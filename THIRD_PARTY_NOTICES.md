# 第三方组件声明

本仓库包含或依赖以下第三方作品。它们各自的版权归原作者所有，不适用本仓库的 MIT 许可。

## 1. hmlwsim（JS 解释器核心）

| 项 | 内容 |
| --- | --- |
| 来源 | https://github.com/kqakqakqa/hmlwsim |
| 作者 | 靠墙的钴-60 |
| 基线 commit | `5e2d314` |
| 许可 | **上游仓库未声明任何开源许可** |

涉及路径：

```
app/src/main/assets/www/js/**        （除 js/mobile/ 下的新增文件）
app/src/main/assets/www/css/**       （除 css/mobile.css）
app/src/main/assets/www/index.html
```

本仓库对这些文件做了以下修改（完整说明见 README 第 5 节）：

1. `js/app.js` — 增加模块导出，重写 `imageResolver`
2. `js/runtime/deviceFeature_display.js` — `rotation()` 无参调用容错
3. `js/runtime/apiSim_router.js` — 增加 `pushUrl` / `replaceUrl` 别名
4. `js/runtime/deviceFeature_virtualFS.js` — 增加 `rmdir`、目录 `move`
5. `js/runtime/apiSim_file.js` — 导出 `rmdir`
6. `js/runtime/deviceFeature_appLifecycle.js` — `callOnCreate` 增加 try/catch

**关于许可状态**：上游未附许可文件。本仓库以技术学习与互操作目的引用其代码，
并明确标注来源。若原作者认为此引用方式不妥，请提 Issue，会立即移除相关代码
或按原作者指定的许可重新分发。

## 2. JSZip 3.10.1

- 来源：https://github.com/Stuk/jszip
- 许可：MIT 或 GPLv3（双许可，本项目按 MIT 使用）
- 路径：`app/src/main/assets/www/vendor/jszip.min.js`

## 3. html2canvas 1.4.1

- 来源：https://github.com/niklasvh/html2canvas
- 许可：MIT
- 路径：`app/src/main/assets/www/vendor/html2canvas.min.js`

## 4. qrcode-generator 1.4.4

- 来源：https://github.com/kazuhikoarase/qrcode-generator
- 许可：MIT
- 路径：`app/src/main/assets/www/vendor/qrcode.js`

## 5. AndroidX / Jetpack Compose / Material 3

- 来源：https://developer.android.com/jetpack
- 许可：Apache License 2.0
- 使用方式：Gradle 依赖（未复制源码）

## 6. 应用图标

由本项目自行绘制，几何造型参考上游网页版界面元素，不包含第三方素材。
