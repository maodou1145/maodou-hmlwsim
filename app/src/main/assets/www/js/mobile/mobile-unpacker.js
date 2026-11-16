/**
 * MobileUnpacker
 * ----------------------------------------------------------------------------
 * 目标：让 hmlwsim 原版 Unpacker.unpack() 一行不改也能吃下三类输入：
 *   1) signed.bin —— 签名后的 HAP（可能有自定义头 / HAP signing block / 尾部数据）
 *   2) .app       —— 外层 ZIP（含 .hap + pack.info），原版直接支持
 *   3) 未签名 .hap —— 直接就是 HAP ZIP，按 APP 包同等处理
 *
 * 做法：把输入归一化为「一个只含 entry.hap 的伪 APP 容器」，再交给原版
 * Unpacker.unpack()。这样 config.json / pack.info / assets/js / resources /
 * hapFiles 的解析语义与网页版 100% 一致，解包器只做格式适配，不重写解释器。
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

  function readU32BE(bytes, o) {
    return ((bytes[o] << 24) | (bytes[o + 1] << 16) | (bytes[o + 2] << 8) | bytes[o + 3]) >>> 0;
  }

  /**
   * 华为签名容器（形态 B / `hw signed app`）—— 不是 ZIP，不能走 extractZipBytes。
   * 格式（大端，来自 lite-wearable-hap-reverse 的实测结论）：
   *   header = [1B magic 0xBE][4B 包名长度][包名]
   *   entry  = [4B 文件名长度][文件名][4B 目录长度][目录(可为0)][8B 数据长度][数据]
   * 条目顺序排列，直到签名区；尾部剩余字节一律忽略。
   * 常见于：整个包就是 signed.bin，或外层 ZIP/.app 里嵌了一个 *-signed.bin。
   */
  function isHwSigned(bytes) {
    if (bytes.length < 64 || bytes[0] !== 0xbe) return false;
    const pkgLen = readU32BE(bytes, 1);
    if (pkgLen < 1 || pkgLen > 256 || 5 + pkgLen + 16 > bytes.length) return false;
    const p = 5 + pkgLen;
    const nameLen = readU32BE(bytes, p);
    if (nameLen < 1 || nameLen > 256 || p + 4 + nameLen + 12 > bytes.length) return false;
    let name = '';
    for (let i = 0; i < nameLen; i++) name += String.fromCharCode(bytes[p + 4 + i]);
    return /^[\w./-]+\.(json|info|index|js|bc|bin|png|mp3|txt|key)$/i.test(name);
  }

  function parseHwSigned(bytes) {
    const dec = new TextDecoder('utf-8', { fatal: false });
    let pos = 1;
    const pkgLen = readU32BE(bytes, pos); pos += 4;
    const pkg = dec.decode(bytes.subarray(pos, pos + pkgLen)); pos += pkgLen;
    const entries = [];
    while (pos + 16 <= bytes.length) {
      // 读到签名区时长度字段会变成任意字节；任一字段越界即停止
      const nameLen = readU32BE(bytes, pos);
      if (nameLen < 1 || nameLen > 512 || pos + 4 + nameLen + 12 > bytes.length) break;
      pos += 4;
      const name = dec.decode(bytes.subarray(pos, pos + nameLen)); pos += nameLen;
      const dirLen = readU32BE(bytes, pos); pos += 4;
      if (dirLen > 1024 || pos + dirLen + 8 > bytes.length) break;
      const dir = dirLen ? dec.decode(bytes.subarray(pos, pos + dirLen)) : ''; pos += dirLen;
      const hi = readU32BE(bytes, pos);
      const size = readU32BE(bytes, pos + 4); pos += 8;
      if (hi !== 0 || size > bytes.length - pos) break;
      const data = bytes.subarray(pos, pos + size); pos += size;
      const d = dir.replace(/^\/+|\/+$/g, '');
      const n = name.replace(/^\/+/, '');
      const full = d && n.indexOf('/') < 0 ? d + '/' + n : n;
      entries.push({ name: full, data: data });
      if (entries.length > 4096) break;
    }
    if (!entries.some(e => e.name === 'config.json' || e.name.endsWith('/config.json'))) {
      throw makeError('BAD_HW_SIGNED', '华为签名容器内未找到 config.json',
        '解出 ' + entries.length + ' 项：' + entries.slice(0, 8).map(e => e.name).join(', '));
    }
    return { pkgName: pkg, entries: entries };
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
    if (n.endsWith('.bin') && !n.endsWith('.png.bin')) return 'bin';
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

    // ⓪ 华为签名容器（形态 B，不是 ZIP）：还原文件表 → 生成 HAP ZIP → 伪 APP。
    // 常见于整个包就是 signed.bin，或外层 .app/.hap 内嵌 *-signed.bin。
    if (isHwSigned(bytes)) {
      const parsed = parseHwSigned(bytes);
      const hapZip = new JSZip();
      parsed.entries.forEach(function (e) {
        hapZip.file(e.name, e.data, { binary: true, compression: 'STORE' });
      });
      const hapBuf = await hapZip.generateAsync({ type: 'arraybuffer', compression: 'STORE' });
      const wrapped = await wrapAsApp(hapBuf);
      return {
        buffer: wrapped,
        kind: 'SIGNED_BIN',
        signed: true,
        layers: trail.concat([
          'HW_SIGNED:' + parsed.entries.length + '项',
          'HAP:config.json+assets/js',
          'APP:entry.hap',
        ]),
        entryCount: parsed.entries.length,
        hapCount: 1,
      };
    }

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
    const binEntries = names.filter(n => ext(n) === 'bin');

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

    // ② HAP / signed.bin：包一层伪 APP，复用原版解析
    if (hasConfig && hasJs) {
      const wrapped = await wrapAsApp(zipBuf);
      const kind = (ext(fileName || '') === 'bin' || sig.byName) ? 'SIGNED_BIN' : 'HAP';
      return {
        buffer: wrapped,
        kind: kind,
        signed: sig.signed || kind === 'SIGNED_BIN',
        layers: trail.concat([kind + ':config.json+assets/js']),
        entryCount: names.length,
        hapCount: 1,
      };
    }

    // ③ 外层还包了一层 .app / .hap / signed.bin：递归
    const nested = hapEntries.concat(appEntries).concat(binEntries)
      .filter(n => !n.endsWith('.png.bin'));
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
