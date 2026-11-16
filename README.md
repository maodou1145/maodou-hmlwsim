# Lite Wearable Runner（Android）

在 Android 手机上运行的 **轻量 JS 代码解释器**：复用 [kqakqakqa/hmlwsim](https://github.com/kqakqakqa/hmlwsim) 的 JS 解释器源码，解释执行 lite wearable 应用的 JS/HML/CSS；**不是完整设备模拟器**（不模拟 ROM、不做安装/签名校验、不执行 `.bc` 字节码）。

工程目录：`H:\HmlwRunner`（H 盘根目录），自包含，Android Studio 直接打开即可构建运行。

---

## 1. 构建与运行

| 项 | 值 |
| --- | --- |
| Android Studio | 本机版本（**Gradle 只能用 8.14.6**；JDK 21 / SDK 35+） |
| JDK | 17 或 21（本机 `E:\Android\jdk21` 可直接用） |
| Gradle | **8.14.6**（本机唯一可用版本，wrapper 已锁死） |
| AGP / Kotlin | 8.7.3 / **2.2.20**（KGP 2.2.x 官方完全支持 Gradle 到 8.14；2.1.0 只支持到 8.10，会冲突） |
| compileSdk / minSdk | 35 / 26 |
| 额外构建环境 | **无**。不需要 Node、NDK、CMake、Flutter、React Native、第三方 JS 引擎或本地 npm 依赖 |

- Android Studio：`File > Open` 选中 `HmlwRunner` 根目录 → Sync → Run `app`。
- 命令行（可选）：`./gradlew :app:assembleDebug`（Windows：`gradlew.bat :app:assembleDebug`）。

**国内下载源（已配好）**

| 位置 | 配置 |
| --- | --- |
| `settings.gradle.kts` | `pluginManagement` 与 `dependencyResolutionManagement` 均为**阿里云镜像优先、官方源兜底**：`google` / `public` / `gradle-plugin` 三个仓库 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle **8.14.6** 发行版走 `repo.huaweicloud.com`；备用 `mirrors.cloud.tencent.com` |
| `validateDistributionUrl` | 设为 `false` —— 非 `services.gradle.org` 的镜像地址必须关掉，否则 wrapper 拒绝下载 |
| 清华 TUNA | `/maven/repos/public/gradle-8.14.6-bin.zip` 实测 **404**，不要用 |
| 全局模板 | `gradle/china-mirrors.init.gradle` 已附，按需拷到 `C:\Users\maodou\.gradle\init.d\` 即可对所有项目生效（**未自动安装**） |

**Gradle JVM（必读）**

Gradle 8.14.6 只支持 Java **1.8–24**，本机 PATH 里的 **JDK 25** 会直接报
`Incompatible Gradle JVM version`。工程已在 `gradle.properties` 里用
`org.gradle.java.home=E:/Android/jdk21`（本机 JDK 21.0.12.1）钉死，
`.idea/gradle.xml` 的 `#GRADLE_LOCAL_JAVA_HOME` 会读取这一项。

- 改完在 AS 里执行 `File > Sync Project with Gradle Files`，必要时重启 AS。
- 若仍报 JDK 25：`Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK` 手动选 `E:\Android\jdk21`（或任意 JDK 17–24）。
- 命令行构建：先 `set JAVA_HOME=E:\Android\jdk21`，再跑 `gradlew.bat :app:assembleDebug`。
- 换机器或 JDK 路径变了，改 `gradle.properties` 那一行即可。

- 若 Sync 报 AGP 8.7.3 与 Gradle 8.14.6 不兼容：把根 `build.gradle.kts` 的 AGP 改成 `8.11.1`（官方要求 Gradle ≥ 8.13，KGP 2.2.20 完整支持到 AGP 8.11.1）。
- 工程位于纯英文路径 `H:\HmlwRunner`，无中文/空格路径风险；工程不依赖任何外部相对路径，可整体再拷贝到别处打开。

---

## 2. 架构

```
┌─────────────────────────────────────────────────────────────┐
│ 原生 M3E UI（Kotlin + Jetpack Compose）                       │
│  目标包 / 运行 / 设备配置 / 日志 4 个入口                     │
│  RuntimeController：包识别、配置、日志、异常、硬件            │
└───────────────┬─────────────────────────────────────────────┘
                │ HmlwMobile.dispatch(cmd, json) / HmlwBridge.post(type, json)
┌───────────────▼─────────────────────────────────────────────┐
│ 系统 WebView（离线同源 https://appassets.androidplatform.net） │
│  mobile.html + js/mobile/*（适配层）                          │
│  └─ 原版 hmlwsim：Sandbox / DeviceFeature* / ApiSim* / VFS    │
│     （js/、css/ 整目录原样复制，解释执行语义不变）            │
└───────────────┬─────────────────────────────────────────────┘
                │ 真正的代码执行：new Function / DOM / Shadow DOM / API 模拟
┌───────────────▼─────────────────────────────────────────────┐
│ Android 硬件（电池 / 传感器 / 震动 / 相册 / 剪贴板）         │
│ 通过 polyfill 注入浏览器同名 API，原网页逻辑无需修改          │
└─────────────────────────────────────────────────────────────┘
```

关键设计：

1. **解释器零移植**：`js/runtime/sandbox.js`（`new Function` + 响应式 + 模块解析）和全部 `DeviceFeature*` / `ApiSim*` 直接作为 `<script>` 加载，执行语义与网页版一致。
2. **配置页不重写逻辑**：原生 M3E 控件通过 JS 桥**驱动原网页 DOM 控件并派发同样的 `input`/`change`/`click` 事件**，因此 `SensorPanel`、`App`、`DeviceFeatureCrown` 的既有交互行为被完整复用。左侧 `#controls` 在移动端 `display:none`，但 DOM 与事件绑定全部保留。
3. **离线同源**：`WebViewAssetLoader` 把 `assets/www/` 映射到 `/assets/`、把 `filesDir` 映射到 `/files/`。页面与包文件同源，JS 用 `fetch()` 拿 `ArrayBuffer`，无 CORS、无 CDN、无文件大小限制。
4. **Web 查询/包文件不通网络**：JSZip / html2canvas / qrcode-generator 已本地化到 `assets/www/vendor/`。
5. **WebView 实例复用**：由 `RuntimeController` 持有，切换「运行 / 设备 / 日志」不会重启正在执行的应用。

---

## 3. 三类输入

导入用系统文件选择器（SAF，`*/*`，不限制扩展名），按**内容嗅探**而非扩展名分类：

| 类型 | 判定规则 | 归一化方式 |
| --- | --- | --- |
| **signed.bin** | ZIP 内有 `config.json` + `assets/js/`，且文件名含 `signed` / `.bin` / 检测到签名头或 `HAP signing block` | 跳过自定义前缀、截断 EOCD 之后的数据，取干净 ZIP → 包成只含 `entry.hap` 的伪 APP |
| **APP 包** | 外层 ZIP 含 `.hap`（通常还有 `pack.info`） | 直接交给原版 `Unpacker.unpack()` |
| **未签名 HAP** | ZIP 顶层直接含 `config.json` + `assets/js/` | 与 APP 包同等处理：包一层伪 APP 后走原版解包器 |
| 未知 | 既不是 APP 也不是 HAP | 仍导入并显示「未知格式」，运行时给出明确错误 |

- 解包器 `MobileUnpacker` 只做**格式适配**：递归最多 4 层、容忍 ZIP 前缀/尾部附加数据、忽略 `.png.bin` 图片资源误判；解析出的 `config.json` / `pack.info` / `assets/js` / `resources` / `hapFiles` 全部由原版 `Unpacker` 处理。
- 签名信息只做展示，**不校验、不参与解释执行**。
- 导入后立刻反馈：包名、版本、设备类型、页面列表、条目数、解包层级、签名状态、警告。

### 错误反馈

| 环节 | 错误码示例 | 界面反馈 |
| --- | --- | --- |
| 导入/识别 | `NOT_ZIP` / `BAD_ZIP` / `EMPTY_FILE` / `OPEN_FAILED` / `TOO_DEEP` | 主页错误卡片 + 日志页详情（含前 16 字节 HEX） |
| 解包 | `NO_JS` / `UNKNOWN_LAYOUT` / `BAD_NESTED` / `NO_APP_DATA` / `LIB_MISSING` | 运行页错误卡片 + Snackbar + 结构化 code/message/detail/stack |
| 启动 | `NO_ENTRY_PAGE` | 明确提示入口页未找到，并列出已注册页面 |
| 运行期 | `RUNTIME` / `WINDOW_ERROR` / `UNHANDLED_REJECTION` / `SANDBOX` / `WEBVIEW_*` | `console.error`、`window.onerror`、未捕获 Promise、Sandbox.lastError 全量转发到日志页 |
| 截图 | `SCREENSHOT` | html2canvas 失败自动回退 `WebView.draw()`，仍失败才报错 |

---

## 4. device 配置页覆盖对照

原生页面按源码分区 1:1 覆盖：

| 源码分区 | 配置项 / 交互 | 原生实现 |
| --- | --- | --- |
| Device | Screen Shape（circle/rect）、Resolution 宽高、6 个 Presets（FIT2 / GT3+ / FIT3+ / GT2 42 / GT2 46 / WATCH D） | 筛选芯片 + 数字输入 + 预设按钮（点击原 `.preset-btn`） |
| Sensors | Use system battery、Battery 滑块与数值、4 个 Show real values（线性加速度/角速度/磁场/气压）、Step count、Heart rate（Not detected + 滑块 + 值）、Wearing state | 开关 / 滑块 / 数字输入，禁用态与网页版一致 |
| WearEngine | 消息日志、输入框、Send，以及应用 `sendMsg` 发来的消息 | 消息列表 + 输入发送 + 文件消息模拟 |
| Crown | Invert Scroll、滚轮旋转 | 开关 + 灵敏度滑块 + 上/下旋按钮（派发合成 `wheel` 事件到 `#crown`，反转逻辑仍由源码处理） |
| Tools | 截图并下载、截图并复制到剪贴板 | 保存相册 / 写入 Android 剪贴板 `content://` URI |
| Log | `#log-panel` 日志、Restart app、Reload app | 日志页（过滤/复制/清空）+ 生命周期按钮 |
| 状态展示 | 设备信息、电池、传感器实时值、亮度模式、当前页、应用信息、PhoneLink 连接、VFS 文件数、离线库状态 | 每 2 秒从 `readState()` 回读并展示 |

系统电池用 `BatteryManager` 注入 `navigator.getBattery()` polyfill；加速度/陀螺仪/磁场/气压用 `SensorManager` 注入 `devicemotion` / `Magnetometer` / `PressureSensor` polyfill；`system.vibrator` 附加真实震动。原网页的传感器订阅与 `setMode` 逻辑完全不变。

---

## 5. 与上游源码的关系

| 文件 | 状态 |
| --- | --- |
| `assets/www/css/simulator.css`、`assets/www/index.html`、其余 `assets/www/js/**` | 上游仓库**逐字节原样复制**（commit `5e2d314`），可用 `diff` 核对 |
| `assets/www/js/app.js` | 上游改动 ①：末尾 `return { init }` → `return { init, loadAppFile, restartApp, resetApp }`（仅新增导出，未改逻辑） |
| `assets/www/js/runtime/deviceFeature_display.js` | 上游改动 ②：`el.rotation` 里 `!!opts.focus` → `!!(opts && opts.focus)`（应用无参调用 `rotation()` 时不再抛 TypeError，传参行为完全不变） |
| `assets/www/js/runtime/apiSim_router.js` | 上游改动 ③：新增 `pushUrl` / `replaceUrl` 别名（新 API 命名的兼容，语义与 `push` / `replace` 一致） |
| `assets/www/js/runtime/deviceFeature_virtualFS.js` | 上游改动 ④：新增 `rmdir`（recursive 删目录前缀）、`move` 支持目录搬移、导出 `resolveResourcePath`。修复应用自封装 `moveDir()` 因缺 `rmdir` 抛错导致初始化中断 |
| `assets/www/js/runtime/apiSim_file.js` | 上游改动 ⑤：导出 `rmdir`，对齐 `@system.file` 的 Lite 接口全集 |
| `assets/www/js/app.js` | 上游改动 ⑥（同文件）：重写 `imageResolver` —— 一次性建包内资源索引（ability 前缀 / `common/` / 单字母分类目录 / `png↔png.bin` 全匹配），精确路径优先，basename 仅唯一候选时兜底。修复「图片 A 显示成 B」与把 `.bin` 当 PNG 打包造成的颜色错乱 |
| `assets/www/js/mobile/mobile-audio.js` | 新增：`@system.audio` 官方语义 + 包内 `rawfile/**` 音频精确查找（含 `internal://app/music/x.mp3` → `rawfile/audio/music/x.mp3`），真机发声 |
| `assets/www/mobile.html` | 新增移动端入口：本地 vendor 库、隐藏桌面面板、加载适配层；core modules 的加载顺序与上游完全一致 |
| `assets/www/js/mobile/mobile-unpacker.js` | 新增：三类包归一化 |
| `assets/www/js/mobile/mobile-bridge.js` | 新增：桥接、polyfill、事件转发、控件驱动、截图接管 |
| `assets/www/css/mobile.css` | 新增：表盘等比缩放与视口适配 |
| `assets/www/vendor/*` | JSZip 3.10.1 / html2canvas 1.4.1 / qrcode-generator 1.4.4，离线本地化 |

---

## 6. M3E 界面说明

采用 **Material 3 Expressive 设计语言**，全部使用稳定公开 API：

- 动态取色（Android 12+）、tonal surface 分层、强调排版；
- `ExpressiveShapes` 大圆角形状系统（20/28/36/44dp）、胶囊状态标签；
- `FilterChip` / `AssistChip` / `FilledTonalButton` / `ElevatedCard` 等强调型组件；
- spring 弹簧入场动效（`expressiveReveal`）。

> 注意：`material3 1.4.0`（当前最新稳定版）中 `MaterialExpressiveTheme`、`Shapes(largeIncreased=...)` 与 `MaterialTheme.shapes.extraLargeIncreased` 仍是 **internal**，跨模块调用会直接编译失败。为了「本地工具链一次构建通过」，本工程不调用它们，改用上述公开 API 落地同一套 M3E 视觉规范。若以后升级到这些 API 已公开的版本，只需在 `ui/theme/Theme.kt` 换成 `MaterialExpressiveTheme(...)` 并把 `ExpressiveShapes` 换回 `Shapes(...)`。

---

## 7. 已知边界

- 不模拟设备 ROM、安装流程、签名校验；`.bc` 字节码不执行（与上游一致，只跑 `.js`）。
- 表盘按设备分辨率固定布局后整体 `transform: scale()` 适配手机；触摸坐标由源码按 `getBoundingClientRect` 换算，仍是设备像素语义。
- `system.fetch` 走 WebView 网络栈，受系统网络与 CORS 约束；AndroidManifest 已开启 cleartext。
- html2canvas 对 Shadow DOM 可能截不全，已提供 `WebView.draw()` 兜底。
- 虚拟文件系统沿用网页版的 `localStorage`，WebView 已开启 `domStorageEnabled`。
- **华为签名包（形态 B / `hw signed app`）已支持**：外层 ZIP 只含 `signed.bin` 时，按大端 TLV（magic `0xBE` + `[4B 名长][名][4B 目录长][目录][8B 数据长][数据]`）还原文件表，重组为 HAP ZIP 后复用原版 `Unpacker`。实测螃蟹 Acto 包 170 个条目全部还原，`config.json` / `assets/js/default/pages/index/index.js` 齐全。原生导入嗅探器 `PackageInspector` 同步识别为 `SIGNED_BIN`，不再误报 UNKNOWN。依据：`lite-wearable-hap-reverse` skill 的形态 B 章节。
- `.png.bin` 是 **BGRA** 原始像素（magic `0x100` + packed 宽高 + 像素），`parseBinImage` 的 B↔R 交换经同名 PNG 逐像素比对确认正确；颜色错乱的真实原因是**取错图**（basename 模糊匹配）或把 `.bin` 当 PNG 直接打包，现由 `imageResolver` 的精确索引修复。
- **真机 WebView 视口单位陷阱（已修，黑屏根因）**：实测 vivo V2048A / Android 13 的 WebView 为 Chrome **101.0.4951.74**，在这台 WebView 上 `vh` / `dvh` / `height:100%` **全部算成 0**（`html`/`body` 高度链断裂）。`#app` / `#watch-area` 塌陷成 0 高后，flex 居中把 466px 表盘推到 `y=-169`，整块被裁掉 → 表现为黑屏，但解释器其实已正常运行。修法：`mobile.css` 用 `height:auto` 兜底，`mobile-bridge.js` 的 `updateScale()` 把 `window.innerHeight` 写成像素高度（resize 时同步更新）。CDP 实测修复后容器 431px、表盘 `y=46`、Shadow DOM 37 个可见节点。
- 上游 `css-adapter.js` 的 `:has(> :only-child)` 需要 Chrome 105+，在 101 上会被静默忽略，只影响「space-between 只有一个子元素时居中」这一处视觉细节，不影响运行。未改上游；升级系统 WebView 到 105+ 可完整。

---

## 8. 自检与来源

交付前已做静态自检（未执行任何构建/打包，交由本地 Android Studio 完成）：上游源码 diff、44 个 JS 文件 `node --check`、Kotlin 括号平衡、HTML 46 个本地引用、Manifest 资源引用、图标类存在性、material3 API 签名核对。

| 来源 | 用途 | 许可 |
| --- | --- | --- |
| github.com/kqakqakqa/hmlwsim | JS 解释器源码 | 以仓库声明为准 |
| JSZip 3.10.1 | ZIP 解包 | MIT / GPLv3 |
| html2canvas 1.4.1 | 截图 | MIT |
| qrcode-generator 1.4.4 | 二维码组件 | MIT |
