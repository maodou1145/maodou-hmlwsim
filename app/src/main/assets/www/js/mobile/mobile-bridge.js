/**
 * MobileBridge
 * ----------------------------------------------------------------------------
 * Android WebView 适配层。原则：**不改动 hmlwsim 的解释执行语义**。
 *  - 不重写 Sandbox / DeviceFeature* / ApiSim*；
 *  - 原生 M3E 配置页通过「驱动原网页 DOM 控件 + 派发原事件」复用全部交互行为；
 *  - 只用 polyfill 把 Android 硬件（电池/传感器/震动/剪贴板/表冠）接到
 *    原网页原本调用的浏览器 API 上；
 *  - 解包交给 MobileUnpacker 归一化后仍走原版 Unpacker.unpack()。
 *
 * 唯一对上游源码的改动：js/app.js 末尾把 loadAppFile/restartApp/resetApp
 * 加入返回对象（新增导出，不改任何逻辑），便于原生端触发运行。
 */
(() => {
  'use strict';

  const TAG = '[MobileBridge]';
  const $ = (id) => document.getElementById(id);
  // hmlwsim 的模块都是全局 const/let，不会挂到 window 上；只能用 typeof 探测
  // 全局词法绑定，不能用 window[name]。
  function has(name) {
    try { return typeof eval(name) !== 'undefined'; } catch (e) { return false; }
  }

  function post(type, obj) {
    try {
      const b = window.HmlwBridge;
      if (b && typeof b.post === 'function') b.post(type, JSON.stringify(obj || {}));
    } catch (e) {
      // 桥不可用时退化为控制台输出，不影响解释器本身
      console.warn(TAG, 'post failed', type, e);
    }
  }

  // 大 payload（截图 dataURL 可能数 MB）分片发送，避免 JS->Java 单次调用超限。
  function postLarge(type, obj) {
    let str;
    try { str = JSON.stringify(obj); } catch (e) { post('error', { code: 'BIG_PAYLOAD', message: '事件序列化失败' }); return; }
    const CHUNK = 180000;
    if (str.length <= CHUNK) { post(type, obj); return; }
    const bridge = window.HmlwBridge;
    if (!bridge) { post('error', { code: 'NO_BRIDGE', message: '大事件无法分片发送' }); return; }
    const id = 'c' + Date.now() + '_' + Math.floor(Math.random() * 100000);
    const total = Math.ceil(str.length / CHUNK);
    for (let i = 0; i < total; i++) {
      const part = JSON.stringify({
        id: id, type: type, index: i, total: total,
        data: str.slice(i * CHUNK, (i + 1) * CHUNK),
      });
      try { bridge.post('chunk', part); } catch (e) {
        post('error', { code: 'CHUNK_FAILED', message: '分片发送失败：' + e.message });
        return;
      }
    }
  }

  function fmt(args) {
    return Array.prototype.map.call(args, (a) => {
      if (a instanceof Error) return a.message + (a.stack ? '\n' + a.stack : '');
      if (typeof a === 'string') return a;
      try { return JSON.stringify(a); } catch (e) { return String(a); }
    }).join(' ');
  }

  function describeError(e) {
    if (!e) return { code: 'UNKNOWN', message: '未知错误', detail: '', stack: '' };
    return {
      code: e.code || 'RUNTIME',
      message: e.message || String(e),
      detail: e.detail || '',
      stack: e.stack || '',
    };
  }

  // ---------------------------------------------------------------- 错误通道
  const nativeError = console.error;
  const nativeWarn = console.warn;
  console.error = function () {
    nativeError.apply(console, arguments);
    const d = describeError(arguments[0] instanceof Error ? arguments[0] : null);
    post('error', {
      // 上游常用 console.error('xxx:', e)，首参是字符串；必须优先完整格式化，
      // 否则会被 describeError(null) 的「未知错误」覆盖，丢掉真实报错内容
      code: d.code, message: fmt(arguments) || d.message,
      detail: d.detail, stack: d.stack || '', source: 'console.error',
    });
  };
  console.warn = function () {
    nativeWarn.apply(console, arguments);
    post('log', { level: 'warn', text: fmt(arguments) });
  };

  window.addEventListener('error', (e) => {
    post('error', {
      code: 'WINDOW_ERROR', message: e.message || '未捕获的脚本异常',
      detail: (e.filename || '') + ':' + (e.lineno || 0),
      stack: e.error && e.error.stack ? e.error.stack : '', source: 'window.onerror',
    });
  });
  window.addEventListener('unhandledrejection', (e) => {
    const r = e.reason;
    post('error', {
      code: 'UNHANDLED_REJECTION',
      message: r && r.message ? r.message : String(r),
      detail: '异步 Promise 未捕获', stack: r && r.stack ? r.stack : '',
      source: 'unhandledrejection',
    });
  });

  // ---------------------------------------------------------------- 日志通道
  function installLogObserver() {
    const panel = $('log-panel');
    if (!panel || typeof MutationObserver === 'undefined') return;
    const flush = (records) => {
      records.forEach((r) => {
        r.addedNodes.forEach((n) => {
          if (n.nodeType !== 1) return;
          const level = (n.className || '').indexOf('error') >= 0 ? 'error'
            : (n.className || '').indexOf('warn') >= 0 ? 'warn' : 'info';
          post('log', { level: level, text: n.textContent || '' });
        });
      });
    };
    new MutationObserver(flush).observe(panel, { childList: true });
  }

  // ------------------------------------------------------- 系统电池 polyfill
  // SensorPanel.startSystemBattery() 依赖 navigator.getBattery()；Android WebView
  // 没有该 API。这里补一个由原生 BatteryManager 驱动的同构实现，原逻辑一行不改。
  function installBatteryPolyfill() {
    // 始终安装：部分 Chromium 版本虽暴露 getBattery 但永远不 resolve。
    const listeners = {};
    let level = 0.85;
    let charging = false;
    const battery = {
      get level() { return level; },
      get charging() { return charging; },
      addEventListener(t, cb) { (listeners[t] = listeners[t] || []).push(cb); },
      removeEventListener(t, cb) {
        listeners[t] = (listeners[t] || []).filter((f) => f !== cb);
      },
    };
    try {
      Object.defineProperty(navigator, 'getBattery', {
        configurable: true,
        value: () => Promise.resolve(battery),
      });
    } catch (e) {
      window.getBattery = () => Promise.resolve(battery);
    }
    window.__hmlwSetBattery = (l, c) => {
      const nl = Math.max(0, Math.min(1, Number(l) || 0));
      const nc = !!c;
      const levelChanged = nl !== level;
      const chargingChanged = nc !== charging;
      level = nl; charging = nc;
      if (levelChanged) (listeners.levelchange || []).forEach((f) => { try { f(); } catch (_) {} });
      if (chargingChanged) (listeners.chargingchange || []).forEach((f) => { try { f(); } catch (_) {} });
    };
  }

  // --------------------------------------------------------- 传感器 polyfills
  // DeviceFeatureSensor 用 DeviceMotionEvent / Magnetometer / PressureSensor。
  // WebView 无真实数据源，这里把原生 SensorManager 的数据转成同名浏览器事件。
  function installSensorPolyfills() {
    const active = [];

    class FakeSensor {
      constructor(type) {
        this.type = type;
        this.x = 0; this.y = 0; this.z = 0; this.pressure = 0;
        this._listeners = [];
        this._started = false;
        active.push(this);
      }
      addEventListener(t, cb) { if (t === 'reading') this._listeners.push(cb); }
      removeEventListener(t, cb) { this._listeners = this._listeners.filter((f) => f !== cb); }
      start() { this._started = true; }
      stop() { this._started = false; }
      emit(values) {
        if (!this._started) return;
        this.x = values[0] || 0; this.y = values[1] || 0; this.z = values[2] || 0;
        this.pressure = values[0] || 0;
        this._listeners.forEach((f) => { try { f(); } catch (_) {} });
      }
    }

    if (!window.Magnetometer) {
      window.Magnetometer = class Magnetometer extends FakeSensor {
        constructor() { super('compass'); }
      };
    }
    if (!window.PressureSensor) {
      window.PressureSensor = class PressureSensor extends FakeSensor {
        constructor() { super('barometer'); }
      };
    }
    if (!window.DeviceMotionEvent) {
      window.DeviceMotionEvent = function DeviceMotionEvent() {};
    }

    window.__hmlwPushSensor = (type, a, b, c) => {
      const v = [Number(a) || 0, Number(b) || 0, Number(c) || 0];
      if (type === 'accelerometer' || type === 'gyroscope') {
        const ev = new Event('devicemotion');
        Object.defineProperty(ev, 'acceleration', {
          value: type === 'accelerometer' ? { x: v[0], y: v[1], z: v[2] } : null,
        });
        Object.defineProperty(ev, 'rotationRate', {
          value: type === 'gyroscope' ? { alpha: v[0], beta: v[1], gamma: v[2] } : null,
        });
        window.dispatchEvent(ev);
      } else {
        const key = type === 'compass' ? 'Magnetometer'
          : type === 'barometer' ? 'PressureSensor' : '';
        if (!key) return;
        active.forEach((s) => { if (s.type === (type === 'compass' ? 'compass' : 'barometer')) s.emit(v); });
      }
    };
  }

  // ------------------------------------------------------------- 震动 / 消息
  function installFeatureHooks() {
    if (has('DeviceFeatureVibrator')) {
      const orig = DeviceFeatureVibrator.vibrate;
      DeviceFeatureVibrator.vibrate = function () {
        const r = orig.apply(this, arguments);
        // 官方 @system.vibrator.vibrate({mode}) 会把模式传进来；网页版自身调用无参时默认 long。
        const mode = arguments[0] === 'short' ? 'short' : 'long';
        window.__hmlwVibratePosts = (window.__hmlwVibratePosts || 0) + 1;
        post('vibrate', {
          mode: mode,
          durationMs: mode === 'short' ? 35 : 400,
          source: '@system.vibrator',
        });
        return r;
      };
    }
    if (has('DeviceFeaturePhoneLink')) {
      const send = DeviceFeaturePhoneLink.sendToPhone;
      const receive = DeviceFeaturePhoneLink.receiveFromPhone;
      const fileIn = DeviceFeaturePhoneLink.receiveFileFromPhone;
      DeviceFeaturePhoneLink.sendToPhone = function (msg) {
        post('wearLog', { direction: 'sent', text: String(msg) });
        return send.apply(this, arguments);
      };
      DeviceFeaturePhoneLink.receiveFromPhone = function (msg) {
        post('wearLog', { direction: 'received', text: String(msg) });
        return receive.apply(this, arguments);
      };
      DeviceFeaturePhoneLink.receiveFileFromPhone = function (name) {
        post('wearLog', { direction: 'received', text: '[文件] ' + String(name) });
        return fileIn.apply(this, arguments);
      };
    }
  }

  // ------------------------------------------------------------ 截图行为接管
  // 网页版是「下载」/「写浏览器剪贴板」；移动端改为把 PNG dataURL 交给原生
  // 保存相册 / 写入 Android 剪贴板(content uri)。按钮与语义保持不变。
  async function captureScreenshot() {
    if (typeof html2canvas === 'undefined') throw new Error('截图库 html2canvas 未就绪');
    const frame = $('watch-frame');
    if (!frame) throw new Error('当前没有可截图的表盘');
    const canvas = await html2canvas(frame, {
      scale: 2, backgroundColor: null, useCORS: true, logging: false,
    });
    return canvas.toDataURL('image/png');
  }

  async function screenshot(action) {
    try {
      const url = await captureScreenshot();
      postLarge('screenshot', { action: action, dataUrl: url, error: '' });
    } catch (e) {
      // 通知原生改用 WebView.draw() 兜底
      postLarge('screenshot', { action: action, dataUrl: '', error: e.message || String(e) });
    }
  }

  function installScreenshotHooks() {
    document.addEventListener('click', (e) => {
      const t = e.target && e.target.closest
        ? e.target.closest('#screenshot-save-btn, #screenshot-clipboard-btn') : null;
      if (!t) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      screenshot(t.id === 'screenshot-save-btn' ? 'save' : 'clipboard');
    }, true);
  }

  // ------------------------------------------------------------- 表盘缩放
  // 原网页按 466px 固定布局，手机上按视口等比缩小到可完整显示。
  // DeviceFeatureTouch 用 getBoundingClientRect 与 windowWidth 做比例换算，
  // transform 缩放后比例自动成立，触摸坐标仍保持设备像素语义。
  function viewportSize() {
    const vv = window.visualViewport;
    return {
      w: window.innerWidth || (vv && vv.width) || document.documentElement.clientWidth || 466,
      h: window.innerHeight || (vv && vv.height) || document.documentElement.clientHeight || 466,
    };
  }

  function updateScale() {
    const shell = $('watch-shell');
    if (!shell) return;
    const w = parseInt(($('width-input') || {}).value, 10) || 466;
    const h = parseInt(($('height-input') || {}).value, 10) || 466;
    const v = viewportSize();
    // 正常情况由 mobile.css 的 fixed/inset 撑满视口；只有容器真的塌陷时才写像素，
    // 避免首帧 innerHeight 为 0 把容器写成 0 高（旧版就是这样黑屏的）。
    const app = $('app');
    const area = $('watch-area');
    if (app && app.getBoundingClientRect().height < 1) app.style.height = v.h + 'px';
    if (area && area.getBoundingClientRect().height < 1) area.style.height = v.h + 'px';
    const s = Math.min(v.w * 0.94 / w, v.h * 0.94 / h);
    shell.style.transform = 'scale(' + Math.max(0.18, Math.min(2, s)).toFixed(4) + ')';
  }

  // 首次布局可能晚于 init；多时机 + 前 2 秒低频校正，视口一就绪就重算缩放。
  window.addEventListener('resize', updateScale);
  window.addEventListener('orientationchange', updateScale);
  window.addEventListener('load', updateScale);
  window.addEventListener('pageshow', updateScale);
  if (window.visualViewport && window.visualViewport.addEventListener) {
    window.visualViewport.addEventListener('resize', updateScale);
  }
  let calibrateLeft = 8;
  const calibrate = () => {
    if (calibrateLeft-- <= 0) return;
    updateScale();
    setTimeout(calibrate, 250);
  };
  setTimeout(calibrate, 100);

  // ------------------------------------------------------- 解包入口（包装）
  function installUnpackerHook() {
    if (!has('Unpacker')) {
      post('error', { code: 'NO_UNPACKER', message: 'Unpacker 模块未加载' });
      return;
    }
    const origLoadFromFile = Unpacker.loadFromFile;
    Unpacker.loadFromFile = async function (file) {
      window.__hmlwLastAppData = null;
      window.__hmlwLastPackageError = null;
      try {
        const buffer = await file.arrayBuffer();
        const out = await MobileUnpacker.normalize(buffer, file.name);
        window.__hmlwLastPackageMeta = out;
        post('progress', {
          phase: 'UNPACKED',
          message: '识别为 ' + out.kind + (out.signed ? '（含签名信息）' : '') + '，共 ' + out.entryCount + ' 个条目',
        });
        const normalized = new File([out.buffer], 'normalized.app');
        const data = await origLoadFromFile.call(Unpacker, normalized);
        window.__hmlwLastAppData = data;
        return data;
      } catch (e) {
        const d = describeError(e);
        window.__hmlwLastPackageError = d;
        post('packageResult', {
          ok: false, stage: 'UNPACK', code: d.code,
          message: '解析失败：' + d.message, detail: d.detail, stack: d.stack,
        });
        throw e;
      }
    };
  }

  // -------------------------------------------------------------- 控件驱动
  function setCheckbox(id, value) {
    const el = $(id);
    if (!el) return;
    const v = !!value;
    if (el.checked === v) return;
    el.checked = v;
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function setValue(id, value, eventName) {
    const el = $(id);
    if (!el) return;
    const v = String(value);
    if (el.value === v) return;
    el.value = v;
    el.dispatchEvent(new Event(eventName || 'input', { bubbles: true }));
  }

  function applyConfig(raw) {
    const c = typeof raw === 'string' ? JSON.parse(raw) : (raw || {});
    setValue('shape-select', c.screenShape || 'circle', 'change');
    setValue('width-input', c.width || 466, 'change');
    setValue('height-input', c.height || 466, 'change');

    // 系统电池开关决定是否启用 navigator.getBattery 轮询（原网页行为）
    setCheckbox('use-system-battery', c.useSystemBattery);
    setValue('battery-slider', c.batteryPercent, 'input');

    const real = c.sensorReal || {};
    ['accelerometer', 'gyroscope', 'compass', 'barometer'].forEach((k) => {
      setCheckbox('sensor-' + k, !!real[k]);
    });
    setValue('sensor-step-count', c.stepCount, 'input');
    setCheckbox('sensor-heart-rate-invalid', c.heartRateInvalid);
    setValue('sensor-heart-rate', c.heartRate, 'input');
    setCheckbox('sensor-on-body', c.onBody);
    setCheckbox('invert-scroll-toggle', c.invertScroll);

    if (has('DeviceFeatureCrown')) {
      DeviceFeatureCrown.setSensitivity(Number(c.crownSensitivity) || 1);
    }
    updateScale();
    post('state', readState());
  }

  function clickPreset(name) {
    const btn = document.querySelector('.preset-btn[data-preset="' + name + '"]');
    if (!btn) {
      post('error', { code: 'BAD_PRESET', message: '未知设备预设：' + name });
      return;
    }
    btn.click();
    updateScale();
    post('state', readState());
  }

  // -------------------------------------------------------------- 状态回读
  function readState() {
    const dom = {
      shape: ($('shape-select') || {}).value || 'circle',
      width: parseInt(($('width-input') || {}).value, 10) || 466,
      height: parseInt(($('height-input') || {}).value, 10) || 466,
      batteryPercent: parseInt(($('battery-slider') || {}).value, 10) || 0,
      useSystemBattery: !!($('use-system-battery') || {}).checked,
      stepCount: parseInt(($('sensor-step-count') || {}).value, 10) || 0,
      heartRate: parseInt(($('sensor-heart-rate') || {}).value, 10) || 72,
      heartRateInvalid: !!($('sensor-heart-rate-invalid') || {}).checked,
      onBody: !!($('sensor-on-body') || {}).checked,
      invertScroll: !!($('invert-scroll-toggle') || {}).checked,
      sensorReal: {
        accelerometer: !!($('sensor-accelerometer') || {}).checked,
        gyroscope: !!($('sensor-gyroscope') || {}).checked,
        compass: !!($('sensor-compass') || {}).checked,
        barometer: !!($('sensor-barometer') || {}).checked,
      },
    };
    const sensorTypes = ['accelerometer', 'gyroscope', 'compass', 'barometer',
      'stepCounter', 'heartRate', 'onBodyState'];
    const sensors = {};
    if (has('DeviceFeatureSensor')) {
      sensorTypes.forEach((t) => { sensors[t] = DeviceFeatureSensor.getSensorValue(t); });
    }
    const data = window.__hmlwLastAppData;
    return {
      time: Date.now(),
      dom: dom,
      device: has('DeviceFeatureDisplay') ? DeviceFeatureDisplay.getDeviceInfo() : null,
      brightnessMode: has('DeviceFeatureDisplay') ? DeviceFeatureDisplay.getBrightnessMode() : null,
      battery: has('DeviceFeatureBattery') ? DeviceFeatureBattery.getStatus() : null,
      sensors: sensors,
      phoneLink: has('DeviceFeaturePhoneLink') ? {
        connected: DeviceFeaturePhoneLink.isConnected(),
        peer: DeviceFeaturePhoneLink.getPeerDevice(),
      } : null,
      page: has('DeviceFeaturePageNav')
        ? ((DeviceFeaturePageNav.getCurrentPage() || {}).uri || null) : null,
      app: has('DeviceFeatureAppLifecycle') ? DeviceFeatureAppLifecycle.getAppInfo() : null,
      vfs: has('DeviceFeatureVirtualFS') ? {
        bundleName: DeviceFeatureVirtualFS.getBundleName(),
        fileCount: data && data.hapFiles ? Object.keys(data.hapFiles).length : 0,
      } : null,
      libs: {
        jszip: typeof JSZip !== 'undefined',
        html2canvas: typeof html2canvas !== 'undefined',
        qrcode: typeof qrcode !== 'undefined',
      },
      audio: has('HmlwAudio') ? HmlwAudio.snapshot() : null,
    };
  }

  // -------------------------------------------------------------- 运行入口
  function packageInfo() {
    const data = window.__hmlwLastAppData;
    const meta = window.__hmlwLastPackageMeta || {};
    if (!data) return null;
    const jsModules = Object.keys(data.modules || {}).filter((k) => k.endsWith('.js'));
    return {
      kind: meta.kind || 'APP',
      signed: !!meta.signed,
      layers: meta.layers || [],
      entryCount: meta.entryCount || 0,
      bundleName: data.bundleName || 'unknown',
      version: data.version || '1.0.0',
      versionCode: data.versionCode || 1,
      deviceType: data.deviceType || 'liteWearable',
      appName: (data.manifest && data.manifest.appName) || '',
      pages: data.pages || [],
      hasAppJs: !!data.appJs,
      jsModuleCount: jsModules.length,
      jsModules: jsModules.slice(0, 40),
      hapFileCount: data.hapFiles ? Object.keys(data.hapFiles).length : 0,
      currentPage: has('DeviceFeaturePageNav')
        ? ((DeviceFeaturePageNav.getCurrentPage() || {}).uri || null) : null,
    };
  }

  async function loadPackage(p) {
    if (!has('App') || typeof App.loadAppFile !== 'function') {
      post('packageResult', {
        ok: false, stage: 'BRIDGE', code: 'NO_APP_BRIDGE',
        message: '运行入口不可用：App.loadAppFile 未导出', detail: '请检查 assets/www/js/app.js 是否被替换',
      });
      return;
    }
    try {
      post('progress', { phase: 'FETCH', message: '读取目标包…' });
      const resp = await fetch(p.url);
      if (!resp.ok) throw new Error('包文件读取失败，HTTP ' + resp.status);
      const buffer = await resp.arrayBuffer();
      if (!buffer || buffer.byteLength === 0) throw new Error('包文件为空');
      post('progress', { phase: 'PARSE', message: '归一化包结构并解包…' });

      const file = new File([buffer], p.fileName || 'package.app');
      window.__hmlwLastAppData = null;
      window.__hmlwLastPackageError = null;
      window.__hmlwLastPackageMeta = null;
      await App.loadAppFile(file);

      const data = window.__hmlwLastAppData;
      const unpackError = window.__hmlwLastPackageError;
      if (!data || unpackError) {
        // 解包错误已由 installUnpackerHook 精确上报，这里不重复发一条笼统错误
        return;
      }
      const sandboxErr = has('Sandbox') && Sandbox.getLastError ? Sandbox.getLastError() : null;
      const info = packageInfo();
      // 入口页判定：PageNav 记录了 uri 属正常情形；个别 bundle 在 onCreate 里自渲染，
      // PageNav 没记录但 Shadow DOM 已有内容 —— 也是成功，不该误报 NO_ENTRY_PAGE。
      const wc = $('watch-content');
      const shadowLen = wc && wc.shadowRoot ? wc.shadowRoot.innerHTML.length : 0;
      if (!info.currentPage && shadowLen < 100) {
        post('packageResult', {
          ok: false, stage: 'LAUNCH', code: 'NO_ENTRY_PAGE',
          message: '运行失败：未找到并加载入口页（默认 pages/index/index）',
          detail: '已注册页面：' + (info.pages || []).join(', '),
          info: info,
        });
        return;
      }
      post('packageResult', {
        ok: true, stage: 'RUNNING', code: 'OK',
        message: '运行成功：' + info.bundleName,
        info: info,
        sandboxError: sandboxErr ? String(sandboxErr.message || sandboxErr) : '',
      });
      post('state', readState());
    } catch (e) {
      const d = describeError(e);
      post('packageResult', {
        ok: false, stage: 'LOAD', code: d.code,
        message: '运行异常：' + d.message, detail: d.detail, stack: d.stack,
      });
    }
  }

  // -------------------------------------------------------------- 命令分发
  function crown(delta) {
    const el = $('crown');
    if (!el) return;
    el.dispatchEvent(new WheelEvent('wheel', {
      deltaY: delta > 0 ? 120 : -120, bubbles: true, cancelable: true,
    }));
  }

  function runAction(p) {
    switch (p.name) {
      case 'restart':
        App.restartApp();
        post('log', { level: 'info', text: '[原生] Restart app' });
        break;
      case 'reload':
        App.resetApp();
        post('log', { level: 'info', text: '[原生] Reload app' });
        break;
      case 'screenshot_save': screenshot('save'); break;
      case 'screenshot_clipboard': screenshot('clipboard'); break;
      case 'crown': crown(Number(p.delta) || 1); break;
      case 'preset': clickPreset(p.value); break;
      case 'wear_send': {
        const input = $('we-input');
        const btn = $('we-send-btn');
        if (input && btn) { input.value = p.text || ''; btn.click(); }
        break;
      }
      case 'wear_send_file':
        if (has('DeviceFeaturePhoneLink')) DeviceFeaturePhoneLink.receiveFileFromPhone(p.text || 'file.bin');
        break;
      case 'clear_log': { const el = $('log-panel'); if (el) el.innerHTML = ''; break; }
      case 'clear_wear_log': { const el = $('we-log'); if (el) el.innerHTML = ''; break; }
      case 'read_state': post('state', readState()); break;
      case 'update_scale': updateScale(); break;
      default:
        post('error', { code: 'UNKNOWN_CMD', message: '未知命令：' + p.name });
    }
  }

  function dispatch(cmd, raw) {
    let p = {};
    try { p = raw ? JSON.parse(raw) : {}; } catch (e) {
      post('error', { code: 'BAD_PAYLOAD', message: '命令参数不是合法 JSON：' + cmd });
      return;
    }
    try {
      switch (cmd) {
        case 'applyConfig': applyConfig(p); break;
        case 'loadPackage': loadPackage(p); break;
        case 'runAction': runAction(p); break;
        case 'pushBattery':
          if (window.__hmlwSetBattery) window.__hmlwSetBattery(p.level, p.charging);
          break;
        case 'pushSensor':
          if (window.__hmlwPushSensor) window.__hmlwPushSensor(p.type, p.x, p.y, p.z);
          break;
        case 'audioConfig':
          if (has('HmlwAudio')) HmlwAudio.setConfig(p);
          break;
        case 'readState': post('state', readState()); break;
        case 'ping': post('pong', { ok: true }); break;
        default: post('error', { code: 'UNKNOWN_CMD', message: '未知命令：' + cmd });
      }
    } catch (e) {
      const d = describeError(e);
      post('error', { code: d.code, message: '命令 ' + cmd + ' 执行失败：' + d.message, stack: d.stack });
    }
  }

  // -------------------------------------------------------------- 初始化
  function init() {
    installBatteryPolyfill();
    installSensorPolyfills();
    installFeatureHooks();
    if (has('HmlwAudioInstaller')) {
      try { HmlwAudioInstaller.install(post); }
      catch (e) { post('error', { code: 'AUDIO_INSTALL', message: '@system.audio 注入失败：' + (e && e.message || e) }); }
    }
    installScreenshotHooks();
    installUnpackerHook();
    installLogObserver();
    updateScale();
    window.HmlwMobile = { dispatch, readState, updateScale, post };
    post('ready', {
      libs: {
        jszip: typeof JSZip !== 'undefined',
        html2canvas: typeof html2canvas !== 'undefined',
        qrcode: typeof qrcode !== 'undefined',
      },
      appExports: has('App') ? Object.keys(App) : [],
      userAgent: navigator.userAgent,
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      // App.init() 也挂在 DOMContentLoaded 上，且注册更早；这里延后一拍确保控件已绑定
      setTimeout(init, 0);
    });
  } else {
    setTimeout(init, 0);
  }
})();
