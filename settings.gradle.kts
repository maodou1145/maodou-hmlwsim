// 国内下载源：镜像优先、官方源兜底。镜像缺制品时 Gradle 会自动继续往下找。
pluginManagement {
    repositories {
        // 阿里云：Gradle 插件门户 / Google Maven / Maven Central + JCenter
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 官方兜底
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 阿里云异常时可打开华为云 Maven 作为备用：
        // maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        // 官方兜底
        google()
        mavenCentral()
    }
}

rootProject.name = "HmlwRunner"
include(":app")
