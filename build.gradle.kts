// 插件版本统一在根工程声明一次（apply false），子模块只引用不加版本，
// 避免 Kotlin Gradle 插件被多次加载。
// AGP 8.5.2：本机 SDK platform-35 的 resources.arsc 为新格式，
// AGP 7.4.2 自带的 aapt2 无法解析（2026-11-02 实测），须 AGP 8.x 系 aapt2。
plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.android.library") version "8.5.2" apply false
}
