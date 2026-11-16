import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 正式签名配置从 keystore.properties 读取（文件缺失时 release 退回未签名，不影响 debug 构建）。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.maodou.hmlwrunner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.maodou.hmlwrunner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // 解释器需要的 WebView（系统组件，随 ROM 更新），无需任何 native/so 依赖。
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // M3 Expressive 在 material3 1.4.0 中已稳定，但个别组件/主题仍带实验标记；
        // 统一 opt-in，避免不同 Studio 版本下出现 opt-in 编译报错。
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        }
    }

    lint {
        // lifecycle 的 NonNullableMutableLiveDataDetector 与本工程 Kotlin analysis API 版本
        // 不兼容，lintVitalAnalyzeRelease 会抛 IncompatibleClassChangeError（lint 自身 bug）。
        // 该 detector 与本工程无关（无 LiveData 用法），禁用即可。
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // Compose BOM 2025.09.00（compose ui/foundation/runtime 1.9.1）。
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))

    // 显式锁定 material3 1.4.0：BOM 自带 1.3.2，1.4.0 才是含 M3 Expressive
    // (MaterialExpressiveTheme / expressive shapes / motion scheme) 的稳定版。
    // 其 POM 依赖 runtime 1.9.0 / ui 1.8.2 / foundation 1.8.1，与上面的 BOM 兼容。
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.webkit:webkit:1.14.0")
}
