<div align="center">

<img src="docs/assets/icon.png" width="104" alt="maodou-hmlwsim">

# maodou-hmlwsim

**在 Android 手机上跑鸿蒙轻量穿戴（Lite Wearable）应用的 JS 解释器**

[![Platform](https://img.shields.io/badge/Android-8.0%2B(API%2026)-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3%20Expressive-4285F4)](#)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

移植自 [kqakqakqa/hmlwsim](https://github.com/kqakqakqa/hmlwsim) 的 JS 解释器 —— 把网页版模拟器搬进 Android，让手表应用在手机上跑起来。

</div>

> **⚠️ 这不是完整设备模拟器**
> 不模拟 ROM，不校验安装与签名，不执行 `.bc` 字节码，**无法替代真机**。它是开发期的快速验证工具：改完代码，掏手机就能看效果，不必每次都打包装表。

---

## 目录

- [它是什么 / 不是什么](#它是什么--不是什么)
- [功能](#功能)
- [构建与运行](#1-构建与运行)
- [架构](#2-架构)
- [支持的两类包](#3-支持的两类包)
- [device 配置页覆盖对照](#4-device-配置页覆盖对照)
- [与上游源码的关系](#5-与上游源码的关系)
- [界面](#6-界面)
- [已知边界](#7-已知边界)
- [许可与致谢](#8-许可与致谢)

---

## 它是什么 / 不是什么

| ✅ 是 | ❌ 不是 |
| --- | --- |
| 用 `new Function` + DOM + Shadow DOM 解释执行手表的 JS/HML/CSS | 不跑 ROM、不模拟系统镜像 |
| 提供 `@system.*` API 的仿真实现（file / router / vibrator / audio / sensor / fetch …） | 不做签名校验、不模拟安装流程 |
| 用真实 Android 硬件充当传感器、电池、震动、剪贴板 | 不能替代真机做最终验收 |
| 把 APP / HAP 解包后直接运行里面的页面 | 不执行 `.bc` 字节码（只跑 `.js`，与上游一致） |

---

## 功能

- **包导入**：系统文件选择器选 APP / HAP，按内容嗅探而非扩展名识别
- **设备配置页**：屏幕形状、分辨率、6 组预设、电池、四类传感器、表冠、WearEngine 消息，全部 1:1 覆盖上游网页版控件
- **真机硬件桥接**：`BatteryManager`、`SensorManager`（加速度 / 陀螺仪 / 磁场 / 气压）、真实震动、相册保存截图
- **图片资源**：自动识别 `.png` / `.png.bin` / `.bin` 三种形态，`png.bin` 是 BGRA 原始像素，转码后正常显示
- **日志页**：`console.*`、`window.onerror`、未捕获 Promise、Sandbox 错误全量转发，支持过滤 / 复制 / 清空
- **WebView 实例复用**：在「运行 / 设备 / 日志」之间切换不会重启正在跑的应用
- **完全离线**：JSZip / html2canvas / qrcode-generator 全部本地化，无 CDN、无网络请求

---

## 1. 构建与运行

| 项 | 值 |
| --- | --- |
| Android Studio | 最新稳定版即可 |
| JDK | **17 或 21**（Gradle 8.14.6 只支持 Java 1.8–24，JDK 25 会直接报 `Incompatible Gradle JVM version`） |
| Gradle | **8.14.6**（wrapper 已锁死，不要升级） |
| AGP / Kotlin | 8.7.3 / **2.2.20**（KGP 2.2.x 官方支持到 Gradle 8.14；2.1.0 只到 8.10，会冲突） |
| compileSdk / minSdk / targetSdk | 35 / 26 / 35 |
| 额外环境 | **无**。不需要 Node、NDK、CMake、第三方 JS 引擎或 npm 依赖 |

### 三步跑起来

```bash
git clone https://github.com/maodou1145/maodou-hmlwsim.git
# Android Studio：File > Open 选中根目录 → Sync → Run 'app'

# 或命令行
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug
```

### 国内网络

仓库已配好镜像，clone 下来不用再改：

| 位置 | 配置 |
| --- | --- |
| `settings.gradle.kts` | `pluginManagement` / `dependencyResolutionManagement` 均为**阿里云镜像优先、官方源兜底** |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.14.6 走 `repo.huaweicloud.com`，备用腾讯云 |
| `validateDistributionUrl` | `false` —— 非 `services.gradle.org` 的地址必须关掉，否则 wrapper 拒绝下载 |
| `gradle/china-mirrors.init.gradle` | 可选全局模板，拷到 `~/.gradle/init.d/` 即可对所有项目生效（**未自动安装**） |

> 清华 TUNA 的 `gradle-8.14.6-bin.zip` 实测 404，别用。

### 构建报错怎么办

| 症状 | 处理 |
| --- | --- |
| `Incompatible Gradle JVM version` | `gradle.properties` 里把 `org.gradle.java.home` 指向你的 JDK 17–24 |
| AGP 8.7.3 与 Gradle 8.14.6 冲突 | 根 `build.gradle.kts` 的 AGP 换成 `8.11.1`（要求 Gradle ≥ 8.13） |
| release 构建报未签名 | 正常。仓库不含签名材料，见下 |

### 签名

仓库**不包含任何签名材料**（`keystore/*.jks` 与 `keystore.properties` 已在 `.gitignore` 中）。自己出正式包时在根目录建 `keystore.properties`：

```properties
storeFile=keystore/my.jks
storePassword=******
keyAlias=******
keyPassword=******
```

文件不存在时 `app/build.gradle.kts` 会自动跳过签名配置，`release` 输出未签名包，`debug` 不受影响。

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
2. **配置页不重写逻辑**：原生 M3E 控件通过 JS 桥**驱动原网页 DOM 控件并派发同样的 `input` / `change` / `click` 事件**，因此 `SensorPanel`、`App`、`DeviceFeatureCrown` 的既有交互行为被完整复用。左侧 `#controls` 在移动端 `display:none`，但 DOM 与事件绑定全部保留。
3. **离线同源**：`WebViewAssetLoader` 把 `assets/www/` 映射到 `/assets/`、把 `filesDir` 映射到 `/files/`。页面与包文件同源，JS 用 `fetch()` 拿 `ArrayBuffer`，无 CORS、无 CDN、无文件大小限制。
4. **Web 查询/包文件不通网络**：JSZip / html2canvas / qrcode-generator 已本地化到 `assets/www/vendor/`。
5. **WebView 实例复用**：由 `RuntimeController` 持有，切换「运行 / 设备 / 日志」不会重启正在执行的应用。

---

## 3. 支持的两类包

导入用系统文件选择器（SAF，`*/*`，不限制扩展名），按**内容嗅探**而非扩展名分类：

| 类型 | 判定规则 | 归一化方式 |
| --- | --- | --- |
| **APP 包** | 外层 ZIP 含 `.hap`（通常还有 `pack.info`） | 直接交给原版 `Unpacker.unpack()` |
| **HAP** | ZIP 顶层直接含 `config.json` + `assets/js/` | 包一层只含 `entry.hap` 的伪 APP，再走原版解包器 |
| 未知 | 既不是 APP 也不是 HAP | 仍导入并显示「未知格式」，运行时给出明确错误 |

- 解包器 `MobileUnpacker` 只做**格式适配**：递归最多 4 层、容忍 ZIP 前缀 / 尾部附加数据、忽略 `.png.bin` 图片资源误判；解析出的 `config.json` / `pack.info` / `assets/js` / `resources` / `hapFiles` 全部由原版 `Unpacker` 处理。
- **签名信息只做展示，不校验、不参与解释执行。** 华为签名容器（`signed.bin`，形态 B 的 TLV 结构）曾在早期版本支持，现已按需求移除 —— 需要的话请先取出里面的 HAP。
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

上游基线：`kqakqakqa/hmlwsim` commit `5e2d314`。

| 文件 | 状态 |
| --- | --- |
| `assets/www/css/simulator.css`、`assets/www/index.html`、其余 `assets/www/js/**` | 上游仓库**逐字节原样复制**，可用 `diff` 核对 |
| `assets/www/js/app.js` | ① 末尾 `return { init }` → 增加 `loadAppFile, restartApp, resetApp` 导出；② 重写 `imageResolver` |
| `assets/www/js/runtime/deviceFeature_display.js` | `el.rotation` 里 `!!opts.focus` → `!!(opts && opts.focus)`（应用无参调用 `rotation()` 时不再抛 TypeError） |
| `assets/www/js/runtime/apiSim_router.js` | 新增 `pushUrl` / `replaceUrl` 别名（新 API 命名兼容，语义与 `push` / `replace` 一致） |
| `assets/www/js/runtime/deviceFeature_virtualFS.js` | 新增 `rmdir`（recursive 删目录前缀）、`move` 支持目录搬移、导出 `resolveResourcePath` |
| `assets/www/js/runtime/apiSim_file.js` | 导出 `rmdir`，对齐 `@system.file` 的 Lite 接口全集 |
| `assets/www/js/runtime/deviceFeature_appLifecycle.js` | `callOnCreate` 加 try/catch，与 `callOnDestroy` 对称 —— 应用 `onCreate` 抛错不再阻断入口页导航 |
| `assets/www/js/mobile/*` | **新增**：移动端适配层（解包归一化 / 桥接与 polyfill / 音频 / 截图接管） |
| `assets/www/css/mobile.css` | **新增**：表盘等比缩放与视口适配 |
| `assets/www/mobile.html` | **新增**：移动端入口，core modules 加载顺序与上游完全一致 |
| `assets/www/vendor/*` | JSZip 3.10.1 / html2canvas 1.4.1 / qrcode-generator 1.4.4，离线本地化 |

**`imageResolver` 为什么重写**：包里 351 个图片资源，同一个逻辑图片可能以 `.png`（预览用）、`.png.bin`（真机读取的 BGRA 位图）、`.bin` 三种形态并存。旧逻辑按精确路径 + basename 模糊匹配，结果大量落空，还把 `.bin` 当 PNG 直接塞进 `Blob`，`naturalWidth` 恒为 0 → 裂图。现在的做法是先去全部扩展名建索引，按 `.png > .png.bin > .bin` 优先级取值，精确路径优先、同目录同基名次之、全局唯一 basename 兜底。

---

## 6. 界面

采用 Material 3 Expressive 的组件体系与形状/色彩层级：

- 主题跟随系统深浅色；Android 12+ 使用动态取色，旧系统用暖色 fallback；
- 大标题折叠栏、四项底部导航；首页用 `Card` / `ElevatedCard` / `ExtendedFloatingActionButton` / `Badge`；
- 设备配置使用 `SingleChoiceSegmentedButtonRow`、`SegmentedButton`、`FilterChip`、`Slider`、`Switch` 与 `ListItem`；
- 日志使用 `FilterChip`、`ListItem` 与按级别着色的状态图标；
- 系统 SplashScreen 先接管冷启动，Compose 品牌加载页承接应用初始化；导入与启动应用时显示进度遮罩。

形状令牌统一为 20 / 28 / 36 / 44dp 层级。Material 3 1.4.0 中 `MaterialExpressiveTheme` 和增加版 Shapes 参数仍为 internal，因此使用公开稳定 API 建立同一套视觉风格，不调用内部接口。

---

## 7. 已知边界

- 不模拟设备 ROM、安装流程、签名校验；`.bc` 字节码不执行（与上游一致，只跑 `.js`）。
- 表盘按设备分辨率固定布局后整体 `transform: scale()` 适配手机；触摸坐标由源码按 `getBoundingClientRect` 换算，仍是设备像素语义。
- `system.fetch` 走 WebView 网络栈，受系统网络与 CORS 约束；AndroidManifest 已开启 cleartext。
- html2canvas 对 Shadow DOM 可能截不全，已提供 `WebView.draw()` 兜底。
- 虚拟文件系统沿用网页版的 `localStorage`，WebView 已开启 `domStorageEnabled`。
- 上游 `css-adapter.js` 的 `:has(> :only-child)` 需要 Chrome 105+，低于此版本会被静默忽略，只影响「space-between 只有一个子元素时居中」这一处视觉细节。
- **真机 WebView 视口单位陷阱（已修，曾导致黑屏）**：实测 vivo V2048A / Android 13 的 WebView 为 Chrome **101.0.4951.74**，在该版本上 `vh` / `dvh` / `height:100%` 全部算成 0，`html`/`body` 高度链断裂 → `#app` / `#watch-area` 塌陷成 0 高，flex 居中把 466px 表盘推到 `y=-169`，整块被裁掉。修法：`mobile.css` 用 `height:auto` 兜底，`mobile-bridge.js` 的 `updateScale()` 把 `window.innerHeight` 写成像素高度并在 resize 时同步更新。
- **`.png.bin` 是 BGRA 原始像素**（magic `0x100` + packed 宽高 + 像素），`parseBinImage` 的 B↔R 交换经同名 PNG 逐像素比对确认正确。颜色错乱的真实原因是**取错图**，不是转码错了。

---

## 8. 许可与致谢

本项目以 [MIT](LICENSE) 协议开源 —— 适用于 Android 原生层（Kotlin / Compose / Gradle 配置 / `assets/www` 下的移动端适配层）。

**但 `assets/www/js`、`assets/www/css`、`assets/www/index.html` 中来自上游的部分不属于上述授权范围**：上游 [kqakqakqa/hmlwsim](https://github.com/kqakqakqa/hmlwsim) 仓库目前未声明任何开源许可，本仓库仅作技术引用并附带少量兼容性修改。若上游作者对此有异议，会立即调整或移除相关代码。详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

| 来源 | 用途 | 许可 |
| --- | --- | --- |
| [kqakqakqa/hmlwsim](https://github.com/kqakqakqa/hmlwsim) | JS 解释器源码 | 上游未声明 |
| [JSZip 3.10.1](https://stuk.github.io/jszip/) | ZIP 解包 | MIT / GPLv3 双许可 |
| [html2canvas 1.4.1](https://html2canvas.hertzen.com/) | 截图 | MIT |
| [qrcode-generator 1.4.4](https://github.com/kazuhikoarase/qrcode-generator) | 二维码 | MIT |
| Jetpack Compose / Material 3 | UI | Apache-2.0 |

感谢上游作者做出的 JS 解释器 —— 没有它，这个项目无从谈起。
