/**
 * MobileUnpacker
 * ----------------------------------------------------------------------------
 * 目标：让 hmlwsim 原版 Unpacker.unpack() 一行不改也能吃下两类输入：
 *   1) .app        —— 外层 ZIP（含 .hap + pack.info），原版直接支持
 *   2) 未签名 .hap  —— 直接就是 HAP ZIP，按 APP 包同等处理
 *
 * 做法：把输入归一化为「一个只含 entry.hap 的伪 APP 容器」，再交给原版
 * Unpacker.unpack()。这样 config.json / pack.info / assets/js / resources /
 * hapFiles 的解析语义与网页版 100% 一致，解包器只做格式适配，不重写解释器。
 *
 * 注：华为签名容器（signed.bin / 形态 B）已按需求移除支持。
 */
const MobileUnpacker = (() => {
  const MAX_DEPTH = 4;

  function makeError(code, message, detail) {
    const e = new Error(message);
    e.code = code;
    e.detail = detail || '';
    return e;
  }

  function toBytes(buffer) {
    if (buffer instanceof Uint8Array) return buffer;
    return new Uint8Array(buffer);
  }

  function isPkLocal(bytes, i) {
    return bytes[i] === 0x50 && bytes[i + 1] === 0x4b &&
      bytes[i + 2] === 0x03 && bytes[i + 3] === 0x04;
  }

  function isEocd(bytes, i) {
    return bytes[i] === 0x50 && bytes[i + 1] === 0x4b &&
      bytes[i + 2] === 0x05 && bytes[i + 3] === 0x06;
  }

  /**
   * 切出干净的 ZIP 字节：
   *  - 从第一个 PK\x03\x04 起（跳过 signed.bin 的自定义签名头）
   *  - 到 EOCD(+comment) 结束（丢掉尾部附加数据）
   * JSZip 依赖 EOCD 中的中央目录偏移，跳过前缀后偏移正好对齐。
   */
  function extractZipBytes(buffer) {
    const bytes = toBytes(buffer);
    let start = -1;
    const scanLimit = Math.min(bytes.length, 1 << 20);
    for (let i = 0; i + 4 <= scanLimit; i++) {
      if (isPkLocal(bytes, i)) { start = i; break; }
    }
    if (start < 0) {
      throw makeError(
        'NOT_ZIP',
        '未找到 ZIP 文件头（PK\\x03\\x04），该文件不是 signed.bin / APP / HAP 容器',
        '前 16 字节：' + hexHead(bytes)
      );
    }

    let end = bytes.length;
    for (let i = bytes.length - 22; i >= 0; i--) {
      if (isEocd(bytes, i)) {
        const commentLen = (bytes[i + 20] | (bytes[i + 21] << 8)) >>> 0;
        end = Math.min(bytes.length, i + 22 + commentLen);
        break;
      }
    }
    const view = bytes.subarray(start, end);
    // 复制成独立 ArrayBuffer，避免 JSZip 持有一个带 offset 的 view
    return view.slice().buffer;
  }

  function hexHead(bytes) {
    let s = '';
    for (let i = 0; i < Math.min(16, bytes.length); i++) {
      s += ('0' + bytes[i].toString(16)).slice(-2) + ' ';
    }
    return s.trim();
  }

  /** 检测 HAP signing block / 自定义签名头，仅用于状态展示，不影响解包 */
  function detectSignature(bytes, fileName) {
    let head = '';
    try {
      head = new TextDecoder('utf-8', { fatal: false })
        .decode(bytes.subarray(0, Math.min(4096, bytes.length)));
    } catch (e) { head = ''; }
    const lowerName = String(fileName || '').toLowerCase();
    const byName = /(^|[-_.])signed([-_.]|$)/.test(lowerName) && !lowerName.includes('unsigned');
    const byText = /hap.?signing.?block|code.?sign.?block|hw.?signed.?app|signature.?block/i.test(head);
    let byMagic = false;
    // CodeSignBlockHeader magic 0x10305070（小端 70 50 30 10）
    const limit = Math.min(bytes.length - 8, 2 << 20);
    for (let i = 0; i < limit; i++) {
      if (bytes[i] === 0x70 && bytes[i + 1] === 0x50 && bytes[i + 2] === 0x30 && bytes[i + 3] === 0x10 &&
          bytes[i + 4] === 3 && bytes[i + 5] === 0 && bytes[i + 6] === 0 && bytes[i + 7] === 0) {
        byMagic = true;
        break;
      }
    }
    return { signed: byName || byText || byMagic, byName, byText, byMagic };
  }

  function ext(name) {
    const n = name.toLowerCase();
    if (n.endsWith('.hap')) return 'hap';
    if (n.endsWith('.app')) return 'app';
    return '';
  }

  /**
   * 分类 + 归一化。
   * @returns {Promise<{buffer:ArrayBuffer, kind:string, signed:boolean, layers:string[], entryCount:number}>}
   *   buffer 可直接传给原版 Unpacker.unpack()
   */
  async function normalize(buffer, fileName, depth, trail) {
    depth = depth || 0;
    trail = trail || [];
    if (depth > MAX_DEPTH) {
      throw makeError('TOO_DEEP', '包嵌套层级超过 ' + MAX_DEPTH + ' 层，已停止递归', trail.join(' -> '));
    }
    if (typeof JSZip === 'undefined') {
      throw makeError('LIB_MISSING', '解包依赖 JSZip 未就绪（离线库加载失败）');
    }

    const bytes = toBytes(buffer);
    const sig = detectSignature(bytes, fileName);

    const zipBuf = extractZipBytes(bytes);

    let zip;
    try {
      zip = await JSZip.loadAsync(zipBuf);
    } catch (e) {
      throw makeError('BAD_ZIP', 'ZIP 容器损坏或签名块导致中央目录不可读', String(e && e.message || e));
    }
    const names = Object.keys(zip.files).filter(n => !zip.files[n].dir);
    const hasConfig = names.some(n => n === 'config.json' || n.endsWith('/config.json'));
    const hasJs = names.some(n => /(^|\/)assets\/js\//.test(n) || n.endsWith('.js'));
    const hapEntries = names.filter(n => n.endsWith('.hap'));
    const appEntries = names.filter(n => n.endsWith('.app'));

    const layerLabel = 'L' + depth + ':' + names.length + '项';
    trail = trail.concat([layerLabel]);

    // ① APP 包（含 .hap）：原版 Unpacker 原生支持，直接透传
    if (hapEntries.length > 0) {
      return {
        buffer: zipBuf,
        kind: 'APP',
        signed: sig.signed,
        layers: trail.concat(['APP:entry.hap']),
        entryCount: names.length,
        hapCount: hapEntries.length,
      };
    }

    // ② HAP：包一层伪 APP，复用原版解析
    if (hasConfig && hasJs) {
      const wrapped = await wrapAsApp(zipBuf);
      return {
        buffer: wrapped,
        kind: 'HAP',
        signed: sig.signed,
        layers: trail.concat(['HAP:config.json+assets/js']),
        entryCount: names.length,
        hapCount: 1,
      };
    }

    // ③ 外层还包了一层 .app / .hap：递归
    const nested = hapEntries.concat(appEntries);
    if (nested.length > 0) {
      const target = nested[0];
      const inner = await zip.files[target].async('arraybuffer');
      try {
        return await normalize(inner, target, depth + 1, trail.concat([target]));
      } catch (e) {
        if (e && e.code) throw e;
        throw makeError('BAD_NESTED', '内层包 ' + target + ' 解析失败', String(e && e.message || e));
      }
    }

    if (hasConfig && !hasJs) {
      throw makeError('NO_JS', 'HAP 内有 config.json，但没有 assets/js 页面代码，可能不是 lite wearable 应用包',
        names.slice(0, 20).join(', '));
    }
    throw makeError('UNKNOWN_LAYOUT',
      '无法识别的包结构：既不是含 .hap 的 APP，也不是含 config.json 的 HAP',
      names.slice(0, 20).join(', '));
  }

  /** 生成一个只含 entry.hap 的 APP 容器（STORE，避免重复压缩大文件） */
  async function wrapAsApp(hapZipBuffer) {
    const wrapper = new JSZip();
    wrapper.file('entry.hap', hapZipBuffer, { binary: true, compression: 'STORE' });
    return wrapper.generateAsync({ type: 'arraybuffer', compression: 'STORE' });
  }

  /** 供 Kotlin 侧结果展示用的轻量元信息（不参与解释执行） */
  async function inspect(buffer, fileName) {
    const out = await normalize(buffer, fileName);
    return {
      kind: out.kind,
      signed: out.signed,
      layers: out.layers,
      entryCount: out.entryCount,
      hapCount: out.hapCount,
    };
  }

  return { normalize, inspect, extractZipBytes, detectSignature };
})();
