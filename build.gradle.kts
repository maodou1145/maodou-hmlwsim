// 顶层构建脚本：只声明插件版本，不引入任何额外构建环境。
// 工具链基线（对齐本机 Android Studio 唯一可用的 Gradle 8.14.6）：
//   Gradle 8.14.6 / AGP 8.7.3 / Kotlin 2.2.20 / JDK 17+（本机 JDK 21）
// Kotlin 2.2.x 官方完全支持 Gradle 到 8.14；原来的 2.1.0 只支持到 8.10，会冲突。
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
}
