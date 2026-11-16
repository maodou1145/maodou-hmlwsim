package com.maodou.hmlwrunner.runtime

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * 目标包导入 + 类型识别。
 *
 * 与 JS 侧 MobileUnpacker 采用同一套嗅探规则，目的是「导入后立刻给出明确反馈」：
 *  - APP：外层 ZIP 含 .hap（+ 可选 pack.info）
 *  - signed.bin：签名后的 HAP 单文件（可能有自定义头 / signing block / 尾部数据）
 *  - 未签名 HAP：直接含 config.json + assets/js，按 APP 同等处理
 * 真正的解包仍由 WebView 内的原版 Unpacker 完成，这里只做元信息与错误判定。
 */
object PackageInspector {

    private const val DIR = "packages"
    private const val MAX_DEPTH = 4

    data class Result(
        val info: PackageInfo?,
        val code: String = "OK",
        val message: String = "",
        val detail: String = "",
    )

    private data class Layout(
        val names: List<String>,
        val configText: String?,
        val packInfoText: String?,
        val nested: Map<String, ByteArray>,
        val layers: MutableList<String>,
    )

    private data class Meta(
        val bundleName: String,
        val version: String,
        val deviceType: String,
        val pages: List<String>,
    )

    fun import(context: Context, uri: Uri, suggestedName: String?): Result {
        val name = suggestedName ?: queryDisplayName(context, uri) ?: "package.bin"
        val id = UUID.randomUUID().toString().substring(0, 8)
        val ext = name.substringAfterLast('.', "bin").ifBlank { "bin" }
        val relativePath = "$DIR/$id.$ext"
        val target = File(context.filesDir, relativePath)
        target.parentFile?.mkdirs()

        var size = 0L
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return Result(null, "OPEN_FAILED", "无法打开所选文件", "Uri = $uri")
            input.use { src ->
                FileOutputStream(target).use { dst -> size = src.copyTo(dst) }
            }
        } catch (e: Exception) {
            target.delete()
            return Result(null, "READ_FAILED", "导入失败：${e.message ?: "读取异常"}", "Uri = $uri")
        }
        if (size == 0L) {
            target.delete()
            return Result(null, "EMPTY_FILE", "导入失败：所选文件是空文件")
        }

        return inspectFile(context, relativePath, name, size)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
    } catch (e: Exception) {
        null
    }

    fun inspectFile(context: Context, relativePath: String, fileName: String, size: Long): Result {
        val file = File(context.filesDir, relativePath)
        if (!file.exists()) return Result(null, "MISSING", "导入的包文件已不存在，请重新导入")

        return try {
            val signature = detectSignature(file, fileName)
            val layout = readLayout(file, 0, mutableListOf())
            var hwDetected = layout.layers.any { it.contains("HW_SIGNED") }
            var signedDetected = signature.signed || hwDetected
            var kind = if (hwDetected) PackageKind.SIGNED_BIN
            else classify(fileName, layout.names, signature.signed)

            var meta = layout.configText?.let { parseConfig(it) }
            var effectiveNames = layout.names
            var warnings = mutableListOf<String>()
            val layers = layout.layers.toMutableList()

            if (layout.names.none { it.equals("config.json", true) || it.endsWith("/config.json", true) }) {
                // APP：进入内层 HAP 读取 config.json 元信息
                val hap = layout.nested.entries.firstOrNull { it.key.endsWith(".hap", true) }
                val hw = layout.nested.entries.firstOrNull { isHwSigned(it.value) }
                when {
                    hap != null -> {
                        val inner = readLayoutFromBytes(hap.value, 1, layers)
                        effectiveNames = inner.names
                        meta = inner.configText?.let { parseConfig(it) } ?: meta
                        layers.add("HAP:${inner.names.size}项")
                        if (inner.layers.any { it.contains("HW_SIGNED") }) {
                            hwDetected = true
                            signedDetected = true
                            kind = PackageKind.SIGNED_BIN
                        }
                    }
                    // 外层 ZIP 只含 signed.bin（形态 B），直接读容器内的 config.json
                    hw != null -> {
                        val inner = hwSignedLayout(hw.value, 1, layers)
                        effectiveNames = inner.names
                        meta = inner.configText?.let { parseConfig(it) } ?: meta
                        layers.add("HAP:config.json")
                        hwDetected = true
                        signedDetected = true
                        kind = PackageKind.SIGNED_BIN
                    }
                    else -> warnings.add("外层未找到可解析的内层 .hap / 签名容器")
                }
            } else {
                layers.add("HAP:config.json")
            }

            if (layout.packInfoText == null && kind == PackageKind.APP) {
                warnings.add("缺少 pack.info（不影响运行，包名取自 HAP 内 config.json）")
            }
            if (meta == null) {
                warnings.add("未能读取 config.json 元信息，仍可尝试运行")
            } else if (meta.pages.isNotEmpty() && !meta.pages.any { it.endsWith("index/index") }) {
                warnings.add("config.json 中未见 pages/index/index；运行器仍会按工程首页规则尝试加载")
            }
            if (signature.byMagic || signature.byText || hwDetected) {
                warnings.add("检测到签名块/签名头，签名信息不参与解释执行（仅作展示）")
            }

            val m = meta ?: Meta("unknown", "1.0.0", "liteWearable", emptyList())
            val info = PackageInfo(
                id = file.nameWithoutExtension,
                fileName = fileName,
                relativePath = relativePath,
                sizeBytes = size,
                kind = kind,
                signed = signedDetected,
                bundleName = m.bundleName,
                version = m.version,
                deviceType = m.deviceType,
                pages = m.pages,
                entryCount = effectiveNames.size,
                layers = layers,
                importedAt = System.currentTimeMillis(),
                warning = warnings.joinToString("；"),
            )
            Result(info, "OK", "识别为 ${kind.label}", "")
        } catch (e: InspectionException) {
            Result(null, e.code, "识别失败：${e.message ?: ""}", e.detail)
        } catch (e: Exception) {
            Result(null, "INSPECT_FAILED", "识别失败：${e.message ?: "未知异常"}", e.stackTraceToString().take(600))
        }
    }

    private class InspectionException(
        val code: String,
        message: String,
        val detail: String = "",
    ) : Exception(message)

    private data class Signature(val signed: Boolean, val byText: Boolean, val byMagic: Boolean)

    /** "signed" 必须是独立词；避免 nexusboxcircle-release-unsigned.app 被误判成含签名信息 */
    private fun looksSignedByName(fileName: String): Boolean {
        val n = fileName.lowercase()
        return Regex("(^|[-_.])signed([-_.]|\$)", RegexOption.IGNORE_CASE).containsMatchIn(n) &&
            !n.contains("unsigned")
    }

    private fun detectSignature(file: File, fileName: String): Signature {
        val len = file.length().toInt().coerceAtMost(1 shl 20)
        val head = ByteArray(len)
        FileInputStream(file).use { it.read(head, 0, len) }
        val text = String(head.copyOfRange(0, minOf(4096, len)), Charsets.ISO_8859_1)
        val byName = looksSignedByName(fileName)
        // 收紧：原先的 harmony.?app / provision 会命中普通 APP 包内容
        val byText = Regex("hap.?signing.?block|code.?sign.?block|hw.?signed.?app|signature.?block", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
        // HAP CodeSignBlockHeader：magic 0x10305070 后必须跟 version=3，避免随机字节误报
        var byMagic = false
        var i = 0
        while (i + 8 <= len) {
            if (head[i] == 0x70.toByte() && head[i + 1] == 0x50.toByte() &&
                head[i + 2] == 0x30.toByte() && head[i + 3] == 0x10.toByte() &&
                head[i + 4] == 3.toByte() && head[i + 5] == 0.toByte() &&
                head[i + 6] == 0.toByte() && head[i + 7] == 0.toByte()
            ) {
                byMagic = true
                break
            }
            i++
        }
        return Signature(byName || byText || byMagic, byText, byMagic)
    }

    private fun classify(fileName: String, names: List<String>, signedByContent: Boolean): PackageKind {
        val hasConfig = names.any { it.equals("config.json", true) || it.endsWith("/config.json", true) }
        val hasHap = names.any { it.endsWith(".hap", true) }
        val hasJs = names.any { it.contains("assets/js/") || it.endsWith(".js", true) }
        return when {
            hasHap && !hasConfig -> PackageKind.APP
            hasConfig && hasJs -> {
                val isBin = fileName.substringAfterLast('.', "").equals("bin", true) ||
                    looksSignedByName(fileName)
                if (isBin || signedByContent) PackageKind.SIGNED_BIN else PackageKind.HAP
            }
            hasHap -> PackageKind.APP
            else -> PackageKind.UNKNOWN
        }
    }

    /** 找到 ZIP 起点（跳过 signed.bin 自定义头），用 ZipInputStream 遍历条目 */
    private fun readLayout(file: File, depth: Int, layers: MutableList<String>): Layout {
        if (depth > MAX_DEPTH) {
            throw InspectionException("TOO_DEEP", "包嵌套层级过深", layers.joinToString(" -> "))
        }
        val probeLen = minOf(file.length(), (1 shl 20).toLong()).toInt()
        val probe = ByteArray(probeLen)
        FileInputStream(file).use { it.read(probe, 0, probeLen) }
        // 顶层就是华为签名容器（不是 ZIP）：按 TLV 读文件表
        if (isHwSigned(probe)) {
            return hwSignedLayout(file.readBytes(), depth, layers)
        }
        val start = indexOfPkLocal(probe)
            ?: throw InspectionException(
                "NOT_ZIP",
                "文件不是 ZIP 容器，无法当作 signed.bin / APP / HAP 解析",
                "前 16 字节：" + probe.take(16).joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
            )
        val stream = FileInputStream(file)
        stream.channel.position(start.toLong())
        return readZip(stream, depth, layers)
    }

    private fun readLayoutFromBytes(bytes: ByteArray, depth: Int, layers: MutableList<String>): Layout {
        if (depth > MAX_DEPTH) {
            throw InspectionException("TOO_DEEP", "包嵌套层级过深", layers.joinToString(" -> "))
        }
        if (isHwSigned(bytes)) return hwSignedLayout(bytes, depth, layers)
        val start = indexOfPkLocal(bytes)
            ?: throw InspectionException("BAD_NESTED", "内层 .hap 不是合法 ZIP")
        return readZip(java.io.ByteArrayInputStream(bytes, start, bytes.size - start), depth, layers)
    }

    private fun indexOfPkLocal(bytes: ByteArray): Int? {
        var i = 0
        while (i + 3 < bytes.size) {
            if (bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4b.toByte() &&
                bytes[i + 2] == 0x03.toByte() && bytes[i + 3] == 0x04.toByte()
            ) return i
            i++
        }
        return null
    }

    private fun u32be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xff) shl 24) or
            ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or
            (b[o + 3].toInt() and 0xff)

    /**
     * 华为签名容器（形态 B / `hw signed app`）：不是 ZIP，是大端 TLV。
     * [1B magic 0xBE][4B 包名长度][包名]，
     * 之后顺序条目 [4B 文件名长度][文件名][4B 目录长度][目录][8B 数据长度][数据]。
     * 常见于整个文件就是 signed.bin，或外层 APP/HAP 里嵌了一个 *-signed.bin。
     */
    private fun isHwSigned(b: ByteArray): Boolean {
        if (b.size < 64 || (b[0].toInt() and 0xff) != 0xBE) return false
        val pkgLen = u32be(b, 1)
        if (pkgLen < 1 || pkgLen > 256 || 5 + pkgLen + 16 > b.size) return false
        val p = 5 + pkgLen
        val nameLen = u32be(b, p)
        if (nameLen < 1 || nameLen > 256 || p + 4 + nameLen + 12 > b.size) return false
        val name = String(b, p + 4, nameLen, Charsets.ISO_8859_1)
        return Regex("^[\\w./-]+\\.(json|info|index|js|bc|bin|png|mp3|txt|key)$", RegexOption.IGNORE_CASE)
            .matches(name)
    }

    /** 只解文件表与 config.json / pack.info，不重建 ZIP（真正解包交给 WebView 侧） */
    private fun hwSignedLayout(b: ByteArray, depth: Int, layers: MutableList<String>): Layout {
        if (!isHwSigned(b)) throw InspectionException("BAD_HW_SIGNED", "华为签名容器头部损坏")
        var pos = 1
        val pkgLen = u32be(b, pos)
        pos += 4 + pkgLen
        val names = mutableListOf<String>()
        var configText: String? = null
        var packInfoText: String? = null
        while (pos + 16 <= b.size && names.size < 4096) {
            val nameLen = u32be(b, pos)
            if (nameLen < 1 || nameLen > 512 || pos + 4 + nameLen + 12 > b.size) break
            pos += 4
            val name = String(b, pos, nameLen, Charsets.UTF_8); pos += nameLen
            val dirLen = u32be(b, pos); pos += 4
            if (dirLen < 0 || dirLen > 1024 || pos + dirLen + 8 > b.size) break
            val dir = if (dirLen > 0) String(b, pos, dirLen, Charsets.UTF_8) else ""; pos += dirLen
            val hi = u32be(b, pos)
            val size = u32be(b, pos + 4); pos += 8
            if (hi != 0 || size < 0 || size > b.size - pos) break
            val d = dir.trim('/')
            val n = name.trimStart('/')
            val full = if (d.isNotEmpty() && !n.contains('/')) "$d/$n" else n
            names.add(full)
            if (full.equals("config.json", true)) configText = String(b, pos, size, Charsets.UTF_8)
            if (full.equals("pack.info", true)) packInfoText = String(b, pos, size, Charsets.UTF_8)
            pos += size
        }
        if (names.isEmpty()) {
            throw InspectionException("BAD_HW_SIGNED", "华为签名容器内没有可读取的条目")
        }
        layers.add("L$depth:${names.size}项(HW_SIGNED)")
        return Layout(names, configText, packInfoText, emptyMap(), layers)
    }

    private fun readZip(input: InputStream, depth: Int, layers: MutableList<String>): Layout {
        val names = mutableListOf<String>()
        var configText: String? = null
        var packInfoText: String? = null
        val nested = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(input)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                if (!entry.isDirectory) {
                    names.add(name)
                    val lower = name.lowercase()
                    val isNested = lower.endsWith(".hap") || lower.endsWith(".app") ||
                        (lower.endsWith(".bin") && !lower.endsWith(".png.bin"))
                    when {
                        lower == "config.json" || lower.endsWith("/config.json") ->
                            configText = zis.readBytes().toString(Charsets.UTF_8)
                        lower == "pack.info" ->
                            packInfoText = zis.readBytes().toString(Charsets.UTF_8)
                        isNested && nested.size < 2 && entry.size < 64L * 1024 * 1024 ->
                            nested[name] = zis.readBytes()
                    }
                }
                zis.closeEntry()
            }
        }
        if (names.isEmpty()) {
            throw InspectionException("BAD_ZIP", "ZIP 容器中没有可读取的条目", "起始层级 L$depth")
        }
        layers.add("L$depth:${names.size}项")
        return Layout(names, configText, packInfoText, nested, layers)
    }

    private fun parseConfig(text: String): Meta? = try {
        val cfg = JSONObject(text)
        val app = cfg.optJSONObject("app")
        val module = cfg.optJSONObject("module")
        val version = app?.optJSONObject("version")
        val jsArr = module?.optJSONArray("js")
        val js = jsArr?.optJSONObject(0)
        val pagesArr = js?.optJSONArray("pages")
        val pages = (0 until (pagesArr?.length() ?: 0)).map { pagesArr!!.optString(it) }
        val typeArr = module?.optJSONArray("deviceType")
        Meta(
            bundleName = app?.optString("bundleName", "unknown") ?: "unknown",
            version = version?.optString("name", "1.0.0") ?: "1.0.0",
            deviceType = typeArr?.optString(0, "liteWearable") ?: "liteWearable",
            pages = pages,
        )
    } catch (e: Exception) {
        null
    }
}
