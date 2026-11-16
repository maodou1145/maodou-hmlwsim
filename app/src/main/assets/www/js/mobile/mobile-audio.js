/**
 * 移动端补充：Lite Wearable 官方 @system.audio / @system.vibrator 语义 + 真机反馈桥接。
 *
 * 官方接口（developer.huawei.com/consumer/cn/doc/lite-wearable-guides/
 * lite-wearable-entertainment-overview 与 js-apis-system-vibrate）：
 *   audio.play() / pause() / stop() / getPlayState(OBJECT)
 *   属性 src currentTime duration autoplay loop volume muted streamType meta
 *   事件 onplay onpause onstop onloadeddata onended onerror ontimeupdate ondurationchange
 *   vibrator.vibrate({ mode: 'short' | 'long', success, fail, complete })（默认 long）
 *
 * 设计约束：
 *  - 不修改任何上游 js/runtime/* 文件，模块通过 ApiRegistry.getAll() 动态注入；
 *  - 音频用 WebView 原生 HTMLAudioElement 真实播放，HAP 内资源经 VirtualFS 转 Blob；
 *  - 播放/震动状态通过 post() 上报原生，由 Android 提供真机出声、震动与 UI 回读。
 */
(function (global) {
  'use strict';

  const SHORT_MS = 35;
  const LONG_MS = 400;

  function install(post) {
    if (typeof post !== 'function') return null;

    // ------------------------------------------------------------ @system.audio
    const el = new Audio();
    el.preload = 'auto';
    const S = {
      src: '', state: 'stop', autoplay: false, loop: false,
      volume: null, muted: false, streamType: 'music', meta: {},
      systemVolume: 0.6, soundEnabled: true, hapticsEnabled: true,
    };
    const H = {};
    let objectUrl = null;
    let lastReport = 0;

    function fire(name, payload) {
      const fn = H[name];
      if (typeof fn === 'function') {
        try { fn(payload || {}); } catch (e) { /* 应用回调异常不阻断解释器 */ }
      }
    }

    function snapshot() {
      const dur = isFinite(el.duration) && el.duration > 0 ? el.duration : -1;
      const cur = S.state === 'stop' ? -1 : (el.currentTime || 0);
      return {
        state: S.state,
        src: S.src,
        currentTime: cur,
        percent: dur > 0 && cur >= 0 ? Math.max(0, Math.min(100, cur / dur * 100)) : 0,
        autoplay: S.autoplay,
        loop: S.loop,
        volume: S.volume === null ? S.systemVolume : S.volume,
        muted: S.muted,
        duration: dur,
        streamType: S.streamType,
        systemVolume: S.systemVolume,
        soundEnabled: S.soundEnabled,
        hapticsEnabled: S.hapticsEnabled,
      };
    }

    function report(event, force) {
      const now = Date.now();
      if (!force && now - lastReport < 220) return;
      lastReport = now;
      post('audioState', Object.assign({ event: event || '' }, snapshot()));
    }

    function applyVolume() {
      const v = S.volume === null ? S.systemVolume : S.volume;
      const target = S.soundEnabled ? Math.max(0, Math.min(1, v)) : 0;
      try {
        el.volume = S.muted ? 0 : target;
        el.muted = S.muted || !S.soundEnabled;
      } catch (e) { /* 音频设备不可用时忽略 */ }
    }

    function fail(message, code, cb) {
      fire('onerror', { message: message, code: code || -1 });
      if (cb && typeof cb.fail === 'function') {
        try { cb.fail(message, code || -1); } catch (e) {}
      }
      post('error', {
        code: 'AUDIO', message: '音频反馈异常：' + message,
        detail: S.src || '', stack: '', source: '@system.audio',
      });
      report('error', true);
    }

    function fallback(reason) {
      // src 为空或资源无法解析时，让原生给一声短提示，保证「调用有反馈」
      post('audioFeedback', { kind: 'fallback', reason: reason, src: S.src });
    }

    function setSrc(next) {
      const uri = String(next == null ? '' : next);
      S.src = uri;
      if (objectUrl) {
        try { URL.revokeObjectURL(objectUrl); } catch (e) {}
        objectUrl = null;
      }
      if (!uri) {
        el.removeAttribute('src');
        try { el.load(); } catch (e) {}
        report('src', true);
        return;
      }
      if (/^(https?:|blob:|data:)/i.test(uri)) {
        el.src = uri;
        try { el.load(); } catch (e) {}
        report('src', true);
        if (S.autoplay) play();
        return;
      }
      const vfs = DeviceFeatureVirtualFS;
      const toUrl = function (bytes) {
        const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes || 0);
        if (!b.length) { fail('音频资源为空：' + uri, 301); return; }
        try {
          objectUrl = URL.createObjectURL(new Blob([b], { type: audioMime(uri) }));
          el.src = objectUrl;
          el.load();
          report('src', true);
          if (S.autoplay) play();
        } catch (e) {
          fail('音频资源解码失败：' + (e && e.message || e), -2);
        }
      };
      const cb = {
        success: function (d) { toUrl(d && (d.buffer || d.content || d)); },
        fail: function () { tryHapAudio(uri, toUrl); },
      };
      // 先试应用数据区（internal://app/ 下由应用写入的音频）
      try {
        if (/^internal:\/\//i.test(uri)) vfs.readArrayBuffer(uri, cb);
        else vfs.readResourceArrayBuffer(uri, undefined, cb);
      } catch (e) {
        tryHapAudio(uri, toUrl);
      }
    }

    /** 包内 rawfile：/audio/music/x.mp3 -> assets/entry/resources/rawfile/audio/music/x.mp3 */
    function tryHapAudio(uri, toUrl) {
      const data = global.__hmlwLastAppData;
      if (!data) { fail('应用资源未加载：' + uri, 301); return; }
      const norm = String(uri).replace(/\\/g, '/').replace(/^internal:\/\/app\//i, '').replace(/^\/+/, '');
      const pool = [];
      if (data.resources) Object.keys(data.resources).forEach(k => pool.push([k, data.resources[k]]));
      if (data.hapFiles) Object.keys(data.hapFiles).forEach(k => pool.push([k, data.hapFiles[k]]));
      const suffixed = pool.filter(([k]) => {
        const kk = k.replace(/\\/g, '/').replace(/^\/+/, '').toLowerCase();
        const nn = norm.toLowerCase();
        return kk === nn || kk.endsWith('/' + nn) || kk.endsWith('/rawfile/' + nn) ||
          kk.replace(/^assets\/entry\/resources\/(rawfile\/)?/, '') === nn;
      });
      if (suffixed.length) { toUrl(suffixed[0][1]); return; }
      fail('音频资源未找到：' + uri, 301);
    }

    function audioMime(uri) {
      if (/\.wav$/i.test(uri)) return 'audio/wav';
      if (/\.ogg$/i.test(uri)) return 'audio/ogg';
      if (/\.m4a$/i.test(uri)) return 'audio/mp4';
      return 'audio/mpeg';
    }

    function play() {
      if (!S.src) {
        S.state = 'play';
        fire('onplay', {});
        fallback('empty-src');
        report('play', true);
        return;
      }
      try {
        const p = el.play();
        S.state = 'play';
        fire('onplay', {});
        report('play', true);
        if (p && typeof p.catch === 'function') {
          p.catch(function (e) { fail('播放被拒绝：' + (e && e.message || e), -2); });
        }
      } catch (e) {
        fail('播放异常：' + (e && e.message || e), -2);
      }
    }

    function pause() {
      try { el.pause(); } catch (e) {}
      S.state = 'pause';
      fire('onpause', {});
      report('pause', true);
    }

    function stop() {
      try { el.pause(); el.currentTime = 0; } catch (e) {}
      S.state = 'stop';
      fire('onstop', {});
      report('stop', true);
    }

    // 只接管无法由方法本身推导的事件，避免 play/pause 回调重复触发。
    el.addEventListener('loadeddata', function () {
      fire('onloadeddata', {});
      report('loadeddata', true);
    });
    el.addEventListener('durationchange', function () { report('durationchange'); });
    el.addEventListener('timeupdate', function () {
      fire('ontimeupdate', snapshot());
      report('timeupdate');
    });
    el.addEventListener('ended', function () {
      fire('onended', {});
      if (!S.loop) { S.state = 'stop'; report('ended', true); }
    });
    el.addEventListener('error', function () {
      fail('音频加载/解码失败', -2);
    });

    const api = {
      play: play,
      pause: pause,
      stop: stop,
      getPlayState: function (cb) {
        const data = snapshot();
        if (cb && typeof cb.success === 'function') {
          try { cb.success(data); } catch (e) {}
        }
        if (cb && typeof cb.complete === 'function') {
          try { cb.complete(); } catch (e) {}
        }
        report('getPlayState', true);
        return data;
      },
    };

    const props = {
      src: { get: function () { return S.src; }, set: setSrc },
      currentTime: {
        get: function () { return S.state === 'stop' ? -1 : (el.currentTime || 0); },
        set: function (v) { try { el.currentTime = Math.max(0, Number(v) || 0); } catch (e) {} },
      },
      duration: {
        get: function () { return isFinite(el.duration) && el.duration > 0 ? el.duration : NaN; },
      },
      autoplay: {
        get: function () { return S.autoplay; },
        set: function (v) { S.autoplay = !!v; },
      },
      loop: {
        get: function () { return S.loop; },
        set: function (v) { S.loop = !!v; el.loop = !!v; },
      },
      volume: {
        get: function () { return S.volume === null ? S.systemVolume : S.volume; },
        set: function (v) { S.volume = Math.max(0, Math.min(1, Number(v) || 0)); applyVolume(); report('volume', true); },
      },
      muted: {
        get: function () { return S.muted; },
        set: function (v) { S.muted = !!v; applyVolume(); report('muted', true); },
      },
      streamType: { get: function () { return S.streamType; } },
      meta: { get: function () { return S.meta; }, set: function (v) { S.meta = v || {}; } },
    };
    Object.keys(props).forEach(function (k) { Object.defineProperty(api, k, props[k]); });
    ['onplay', 'onpause', 'onstop', 'onloadeddata', 'onended', 'onerror',
      'ontimeupdate', 'ondurationchange'].forEach(function (k) {
      Object.defineProperty(api, k, {
        get: function () { return H[k] || null; },
        set: function (v) { H[k] = typeof v === 'function' ? v : null; },
      });
    });

    // ------------------------------------------------- 注入 ApiRegistry（不改上游）
    if (ApiRegistry && typeof ApiRegistry.getAll === 'function') {
      ApiRegistry.getAll()['system.audio'] = api;
    }

    // --------------------------------------- @system.vibrator 官方方法名与模式
    const vib = ApiSimVibrator;
    if (vib && typeof vib.vibrate !== 'function') {
      vib.vibrate = function (options) {
        const o = options || {};
        const mode = o.mode === 'short' ? 'short' : 'long';
        try {
          // DeviceFeatureVibrator.vibrate 已被 mobile-bridge 包装：会保留视觉反馈
          // 并 post('vibrate', { mode, durationMs })，由原生按模式真机震动。
          DeviceFeatureVibrator.vibrate(mode);
          if (typeof o.success === 'function') o.success();
          post('log', { level: 'info', text: '[vibrator] vibrate mode=' + mode });
        } catch (e) {
          const msg = e && e.message ? e.message : String(e);
          if (typeof o.fail === 'function') o.fail(msg, -1);
          post('error', {
            code: 'VIBRATOR', message: '震动调用失败：' + msg,
            detail: mode, stack: '', source: '@system.vibrator',
          });
        }
        if (typeof o.complete === 'function') o.complete();
      };
      const origStop = vib.stop;
      vib.stop = function () {
        const r = origStop.apply(this, arguments);
        post('vibrateStop', {});
        post('log', { level: 'info', text: '[vibrator] stop' });
        return r;
      };
    }

    // ------------------------------------------------------------ 原生配置下发
    function setConfig(cfg) {
      if (!cfg) return;
      if (typeof cfg.systemVolume === 'number') {
        S.systemVolume = Math.max(0, Math.min(1, cfg.systemVolume));
      }
      if (typeof cfg.soundEnabled === 'boolean') S.soundEnabled = cfg.soundEnabled;
      if (typeof cfg.hapticsEnabled === 'boolean') S.hapticsEnabled = cfg.hapticsEnabled;
      applyVolume();
      report('config', true);
    }

    global.HmlwAudio = {
      api: api,
      setConfig: setConfig,
      snapshot: snapshot,
      modeMs: function (mode) { return mode === 'short' ? SHORT_MS : LONG_MS; },
    };
    post('log', { level: 'info', text: '[@system.audio] 已注册；[@system.vibrator.vibrate] 官方方法名已补齐' });
    return global.HmlwAudio;
  }

  global.HmlwAudioInstaller = { install: install };
})(window);
