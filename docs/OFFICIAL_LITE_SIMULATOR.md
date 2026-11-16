# 官方 Huawei Lite Wearable Simulator —— 获取 / 启用 / 对照验收

> 结论：**不用下载，本机已经有 6 份官方 Lite Simulator**。DevEco Studio 26.0.0.851 自带 2 份，`E:\SDK` 另有 4 份完整版（API 6/7/8）。26.0 的「设备管理器」里看不到 Lite 设备，只是因为设备面板配置把它隐藏了，不影响 SDK 里的仿真器本体。

## 1. 本机清单（已逐个核实）

| 位置 | SDK / API | Previewer 版本 | Simulator.exe | 完整度 |
| --- | --- | --- | --- | --- |
| `E:\SDK\hmscore\2.2.0\previewer\liteWearable` | HarmonyOS API 6 | 2.2.0.3 | 3,459,584 B | 完整：config 有字体 + `default` 设备外观 + `images` + `file_system/app/ace` |
| `E:\SDK\hmscore\3.0.0_7\previewer\liteWearable` | HarmonyOS API 7 | 3.0.0.5 | 4,057,088 B | 完整 |
| `E:\SDK\hmscore\3.0.0_8\previewer\liteWearable` | HarmonyOS API 8 | 3.1.1.4 | 4,057,088 B | 完整（**推荐**：对应 3.1.x 文档集） |
| `E:\SDK\openharmony\8\previewer\liteWearable` | OpenHarmony API 8 | 3.1.13.6 | 4,826,112 B | 完整，附 `NOTICE`（开源许可） |
| `D:\DevEco Studio 26.0.0.851\DevEco Studio\sdk\default\hms\previewer\liteWearable` | API 26 / 26.0.0.105 | 26.0.0.105 | 3,747,328 B | 精简：config 仅字体与断行规则 |
| 同上 `sdk\default\openharmony\previewer\liteWearable` | API 26 | — | 3,732,408 B | 精简 |

官方默认配置（26.0 自带，字段与官方文档一一对应）：
`D:\DevEco Studio 26.0.0.851\DevEco Studio\plugins\openharmony\openharmony-preview-server\deviceConfigJson\liteWearableSettingConfig.json`

你 H 盘各工程的 `.idea\previewer\liteWearable\liteWearableSettingConfig_rd.json` 里还存着每次运行的实际参数（454×454 / 466×466、语言、电量等），说明这条链路一直在用。

## 2. 三条启用路径

### A. DevEco 内（官方推荐）
1. 用 DevEco 26.0 打开任一 **Lite Wearable (JS/HML)** 工程（例如 `H:\LiteTranslate`）。
2. 右上角设备下拉选 **Huawei Lite Wearable Simulator**（官方文档路径）。
3. 点 **Run / Debug** → 弹框里选**设备形状与分辨率** → OK；IDE 会编译、构建、安装，随后应用在 Simulator 中运行。
4. 兼容**签名与未签名 HAP**；点设备上方 **More** 展开全部虚拟硬件面板。
5. 若 26.0 设备下拉里没有 Lite 项：`Emulator26.0\productConfig.json` 中 `Wearable 466×466`、`WearableKid 480×408` 都是 `visible=false`（全表无 `lite` 条目），这是设备面板隐藏，不代表仿真器缺失 —— 走 B 或 C。

### B. 直接调用 SDK 里的完整版（脱离 IDE）
推荐 API 8 那份：
```bat
E:\SDK\hmscore\3.0.0_8\previewer\liteWearable\bin\Simulator.exe -h
```
先 `-h` 打印实际用法，再按需带参启动。以下开关**从该 exe 内字符串提取，属推断，务必以 `-h` 输出为准**：

| 开关 | 含义（二进制内提示原文） |
| --- | --- |
| `-h` / `-v` | Print the usage help / 版本 |
| `-device <type>` | Device type（如 `liteWearable`） |
| `-shape rect\|circle` | Screen shape suported: rect or circle |
| `-or <w> <h>` / `-cr <w> <h>` | Original / Compression resolution |
| `-arp <path>` | appResource 路径（缺失会报 "The configuration appResource path does not exist"） |
| `-p <port>` | Config debug server to listen \<port\> |
| `-lws <port>` | Listening port of WebSocket |
| `-refresh region\|full` | Screen refresh mode |
| `-hs true\|false` | JS heap flag |
| `-url` / `-pages` / `-projectID` / `-card` / `-cm` / `-av` | url / pages / projectID / card / colormode / aceVersion |

启动需要一份 config（含 appResource，指向工程构建产物），因此**实际使用仍建议从 DevEco 里起**；命令行适合脚本化/批量回归。

### C. 旧版官方 IDE（要完整的「设备下拉」体验时）
归档页：<https://developer.huawei.com/consumer/cn/deveco-studio/archive/>

| 包 | 大小 | SHA-256 |
| --- | --- | --- |
| DevEco Studio 3.1.1 Release Windows `devecostudio-windows-3.1.0.501.zip` | 843.6 MB | `fbe79d92017d642ee91b2471b36c3e22ff3c186a0df36f3ae683129cfd445d9c` |
| DevEco Studio 3.1 Release Windows `devecostudio-windows-3.1.0.500.zip` | 813 MB | `c7d07530a0649d04b2dddc3369b3c5f92c2265b958c9fc8980b7026564715677` |

- **注意**：归档页里带签名的 CDN 直链（`HW-CC-Date=20230621...`）现已 **403**，签名是页面按时间生成的，需要从归档页点「下载」重新取，或按论坛说明提交工单要官方直链。
- 装到**独立目录**，不要覆盖 26.0；两版本可共存（3.x 面向 HarmonyOS 3.1/4.0 及以下，26.0 面向 HarmonyOS 7 / API 26）。
- 官方说明：Lite Wearable 与 Smart Vision 使用 **Simulator**；Phone/TV/Wearable 才用 Local/Remote Emulator。

## 3. 官方 Simulator 的全部可配置项

来自本机 `liteWearableSettingConfig.json` 与官方文档 `ide-run-simulator`：

| 分组 | 项 | 本机默认值 |
| --- | --- | --- |
| 屏幕 | Turn screen on / Keep screen on | `KeepScreenOnState: true` |
| 屏幕 | BrightnessMode（Manual / Automatic） | `0` |
| 屏幕 | Brightness | `170` |
| 屏幕 | Resolution（停止运行后才能改） | `454*454` |
| 屏幕 | DeviceType / Screen shape | `liteWearable` / rect·circle |
| 传感器 | WearingState | `true` |
| 传感器 | Barometer | `101325` Pa |
| 传感器 | HeartRate | `100` |
| 传感器 | StepCount | `0` |
| 传感器 | Location（经纬度） | `39.914417, 116.39647` |
| 电池 | ChargeMode（未充电 / 充电 / 无线充电） | `0` |
| 电池 | Power（电量） | `0.1` |
| 设备 | Language / Region（仅 Run 模式） | `en-US`，`1.0.1` 为 `zh-CN` |
| 调试 | Screen coordinate system / Show device mask | 开关 |

## 4. 与 `H:\HmlwRunner`（Android 运行器）对照验收

| 官方 Lite Simulator | 运行器 device 配置页 | 状态 |
| --- | --- | --- |
| 形状 rect/circle、分辨率（运行时不可改） | 圆/方屏 + 宽高输入 + 6 个预设，**可热改** | ✅ 覆盖更全 |
| `liteWearable` 设备类型 | 固定 liteWearable（DeviceInfo 回显） | ✅ |
| 电量 + 三种充电态可调 | 电量 0–100 滑块；充电态只读 | ⚠️ 缺充电三态可调 |
| 亮度模式 / 亮度值 | 仅回显 `getBrightnessMode()` | ⚠️ 只读 |
| 屏幕开关 / 常亮 | — | ❌ 缺 |
| 地理位置（经纬度） | — | ❌ 缺 |
| 语言 / 地区 | 跟随 Android 系统 | ❌ 缺 |
| 屏幕坐标系 / 设备遮罩 | — | ❌ 缺 |
| 气压 / 心率 / 计步 / 佩戴 | 全部有，且可接 Android **真实**传感器 | ✅ 更全 |
| 加速度 / 陀螺 / 磁场 | 有（官方虚拟传感器面板未列） | ➕ 运行器独有 |
| 签名 HAP / 未签名 HAP | signed.bin / APP / 未签名 HAP 三类内容嗅探 | ✅ 覆盖更广 |
| — | WearEngine 消息收发、表冠 Invert Scroll + 灵敏度、截图保存/复制、结构化错误码日志 | ➕ 运行器独有 |

**验收建议**：用同一个 HAP 在官方 Simulator（路径 A）与运行器各跑一遍，按上表逐项打勾；官方 Simulator 是**系统级行为基准**，运行器是**轻量 JS 解释执行**，`.bc` 字节码、ROM、签名校验两边都不做。

## 5. 来源

- 使用仿真器运行轻量级智能穿戴应用：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides/ide-run-simulator>
- 使用模拟器运行应用/服务（Simulator vs Local Emulator）：<https://developer.huawei.com/consumer/cn/doc/harmonyos-guides-V2/run_simulator-0000001053303709-V2/>
- DevEco Studio 历史版本归档：<https://developer.huawei.com/consumer/cn/deveco-studio/archive/>
- 本机实测：`E:\SDK\*\previewer\uni-package.json`、`*\previewer\liteWearable\bin\Simulator.exe`、26.0 自带 `liteWearableSettingConfig.json`、`Emulator26.0\productConfig.json`
