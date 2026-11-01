// 插件版本统一在根工程声明一次（apply false），子模块只引用不加版本，
// 避免 Kotlin Gradle 插件被多次加载（版本与 Kuikly 2.0.21 模板一致）
plugins {
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("native.cocoapods") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.android.library") version "7.4.2" apply false
}
