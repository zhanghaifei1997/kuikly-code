# 发布指引

本组件为**纯 Kotlin common 视图组件**（无原生模块），公开分发只需 Maven Central 一个通道；
鸿蒙 `-ohos` 变体按官方约定仅在宿主工程本地构建（mavenLocal），无需公网发布。

## 0. 环境要求

- JDK 17（本机 `~/.gradle/gradle.properties` 已全局固定 `org.gradle.java.home`）
- 本仓库可独立构建：`settings.gradle.kts` + Gradle wrapper（7.6.3），`local.properties` 指向 Android SDK（不提交）
- Maven Central 账号/命名空间/GPG 与 kuikly-app-icon 共用（见 kuikly-app-icon/docs/PUBLISH.md）

## 1. 本地快速验证（mavenLocal）

```bash
./gradlew :kuiklyCode:publishToMavenLocal
# 宿主工程 mavenLocal() 引用 io.github.zhanghaifei1997:kuiklyCode:0.1.0 验证

# 鸿蒙 -ohos 变体（需宿主 KuiklyBase ohos toolchain 环境）
./gradlew -c settings.ohos.gradle.kts :kuiklyCode:publishToMavenLocal
```

## 2. Maven Central

`~/.gradle/gradle.properties` 已配置 `signing.keyId`（B2EE9DFD）与 `mavenCentralUsername/Password`。

```bash
# 1. 构建 + 签名到暂存目录
./gradlew :kuiklyCode:publishAllPublicationsToCentralStagingRepository

# 2. 打 bundle zip（⚠️ staging-repo 可能残留旧版本文件，打 zip 前先清空重跑）
rm -rf kuiklyCode/build/staging-repo && ./gradlew :kuiklyCode:publishAllPublicationsToCentralStagingRepository
cd kuiklyCode/build/staging-repo && zip -r ../../bundle.zip . && cd -

# 3. 上传并自动发布（以 repo1 200 为准；status API 偶发 500）
curl -u "$mavenCentralUsername:$mavenCentralPassword" \
  -X POST "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC" \
  -H "Content-Type: multipart/form-data" -F "bundle=@kuiklyCode/build/bundle.zip"
```

## 3. 版本约定

- 首版 `0.1.0`（`gradle.properties` 中 `kuiklyCode.version`，改一处生效）
- 发版前更新 README 平台支持表与 CHANGELOG
