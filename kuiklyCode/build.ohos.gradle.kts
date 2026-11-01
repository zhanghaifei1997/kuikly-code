plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
}

// 鸿蒙产物版本需与 Android/iOS 产物区分（官方指引：追加 -ohos 后缀）
group = project.findProperty("kuiklyCode.group") as String? ?: "com.kuiklyext.code"
version = (project.findProperty("kuiklyCode.version") as String? ?: "0.1.0") + "-ohos"

val kuiklyVersion: String by project

android {
    namespace = "com.kuikly.code.ohos"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    // 鸿蒙 target：由 KuiklyBase 提供的 ohos toolchain 解析（见 KuiklyBase ohos-kn 文档）。
    // 在已配置 KuiklyBase 鸿蒙工具链的工程中取消注释：
    // ohosArm64 {
    //     binaries.sharedLib {
    //     }
    // }

    sourceSets {
        commonMain.dependencies {
            api("com.tencent.kuikly-open:core:$kuiklyVersion-ohos")
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}
