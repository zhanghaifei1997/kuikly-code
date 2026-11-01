pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://mirrors.tencent.com/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/repository/maven-tencent/")
        }
    }
}

rootProject.name = "kuikly-code"

include(":kuiklyCode")

// 鸿蒙产物构建用：./gradlew -c settings.ohos.gradle.kts :kuiklyCode:publish
// （需宿主 KuiklyUI 工程提供 KuiklyBase ohos toolchain，见 README「鸿蒙接入」）
