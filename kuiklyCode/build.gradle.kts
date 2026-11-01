plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
    id("signing")
}

// 发布坐标（io.github.zhanghaifei1997，见 docs/PUBLISH.md）
group = project.findProperty("kuiklyCode.group") as String? ?: "com.kuiklyext.code"
version = project.findProperty("kuiklyCode.version") as String? ?: "0.1.0"

val kuiklyCoreVersion: String by project

android {
    namespace = "com.kuikly.code"
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        publishLibraryVariants("release")
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Web/H5 端：core 官方产物带 js 变体（见 mirrors.tencent.com core .module 元数据），
    // 组件为纯 common 代码，js 目标直接产出 klib 供宿主 shared 模块引用
    js(IR) {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // Kuikly 产物版本为复合格式（Kuikly版本-Kotlin版本），见 gradle.properties
            api("com.tencent.kuikly-open:core:$kuiklyCoreVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    // 本组件为纯 Kotlin common 实现（Canvas 绘制），不含任何平台专属代码：
    // - Android/iOS/JS：本模块 klib/aar 直接可用
    // - 鸿蒙：宿主 KuiklyUI 工程用 settings.ohos.gradle.kts + build.ohos.gradle.kts 构建 -ohos 变体
}

// ---------- Maven Central 发布 ----------

// sources jar 由 KMP 插件自动生成并挂到各 target publication；
// javadoc jar Central 校验必需，KMP 无 javadoc 文档，附空 jar。
// ⚠️ 每个 publication 必须各自生成 javadoc jar（独立目录），Gradle 8 下
// 共享同一个 jar 会让多个签名任务输出相同 .asc 路径而冲突。
publishing {
    publications.withType<MavenPublication>().configureEach {
        val pubName = name
        val javadocJar = tasks.register("${pubName}JavadocJar", Jar::class) {
            archiveClassifier.set("javadoc")
            destinationDirectory.set(layout.buildDirectory.dir("libs-javadoc/$pubName"))
        }
        artifact(javadocJar.get())
        pom {
            name.set("kuikly-code")
            description.set("Kuikly QR code & Code128 barcode rendering views (classic Kuikly DSL, pure Kotlin common)")
            url.set("https://github.com/zhanghaifei1997/kuikly-code")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("zhanghaifei1997")
                    name.set("Zhanghaifei")
                    email.set("2428669833@qq.com")
                }
            }
            scm {
                url.set("https://github.com/zhanghaifei1997/kuikly-code")
                connection.set("scm:git:git://github.com/zhanghaifei1997/kuikly-code.git")
                developerConnection.set("scm:git:ssh://github.com/zhanghaifei1997/kuikly-code.git")
            }
        }
    }
    repositories {
        mavenLocal()
        // Central Portal 暂存目录：publishAllPublicationsToCentralStagingRepository 后
        // 将 staging-repo 打 zip 经 central.sonatype.com API 上传（见 docs/PUBLISH.md）
        maven {
            name = "CentralStaging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

// 本机 ~/.gradle/gradle.properties 配置了 signing.keyId 时启用签名（无密钥环境跳过，便于本地开发）
if (project.hasProperty("signing.keyId")) {
    signing {
        useGpgCmd()
        sign(publishing.publications)
    }
}
