# kuikly-code

Kuikly 二维码 / 条形码渲染组件 —— 纯 Kotlin common 实现，一套代码跨 Android / iOS / 鸿蒙 / Web。

基于经典 Kuikly DSL（`com.tencent.kuikly.core`）的 `Canvas` 绘制，内置 QR 编码引擎（qrcode-kotlin 血统，MIT）与
Code 128-B 编码器（编码表对照 zxing / ISO 15417 逐条校验），**完全本地生成，无网络、无原生依赖**。

## 功能特性

### QRCodeView（二维码）

- 纯 Canvas 绘制，四端渲染一致
- 模块形状：方形 / 圆点 / 圆角方形
- 前景支持纯色与线性渐变；定位图案可强制保持方形（保证扫码定位）
- 四纠错级别（L/M/H/Q）与标准静区（默认 4 模块，可配）
- `textProvider` 响应式刷新

### BarcodeView（条形码）

- Code 128-B 子集，支持 ASCII 32~126（数字/字母/常见符号）
- 编码表对照 zxing `Code128Reader.CODE_PATTERNS` 逐条校验，校验和按 ISO 15417 计算
- 标准静区（默认 10 模块，可配）
- 可选人眼可读文本行（字号/颜色/间距可配）
- 超出字符集时安全跳过绘制（不崩溃）

## 平台支持

| 平台 | 状态 | 接入方式 |
|---|---|---|
| Android | ✅ | Maven 依赖（见下） |
| iOS | ✅ | 同一 Maven 坐标，宿主 shared 模块 gradle 引用后随 framework 编译 |
| Web (JS) | ✅ | 同一 Maven 坐标（core 官方产物含 js 变体） |
| 鸿蒙 | ✅ | 纯 common 代码零原生分支；宿主 KuiklyUI 工程以源码模块或 `-ohos` 变体接入（见下） |

## 快速开始

宿主 shared 模块 `build.gradle.kts`：

```kotlin
repositories {
    mavenCentral()
    // Kuikly 官方产物仓库
    maven { url = uri("https://mirrors.tencent.com/repository/maven-tencent/") }
}

dependencies {
    implementation("io.github.zhanghaifei1997:kuiklyCode:0.1.0")
}
```

纯视图组件，**无需注册任何原生模块**，直接在页面中使用：

```kotlin
import com.kuikly.code.BarcodeView
import com.kuikly.code.QRCodeShape
import com.kuikly.code.QRCodeView

// 二维码：圆点风格 + 渐变
QRCodeView {
    attr {
        textProvider = { viewModel.payCode }   // 响应式
        size = 248f
        shape = QRCodeShape.CIRCLE
        useGradient = true
        gradientStartColor = Color(0xFF0052D9)
        gradientEndColor = Color(0xFF7B61FF)
    }
}

// 条形码：Code 128-B + 人眼可读文本
BarcodeView {
    attr {
        textProvider = { viewModel.payCode }
        barcodeWidth = 320f
        barcodeHeight = 90f
        showText = true
    }
}
```

## API

### QRCodeViewAttr

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| textProvider | `(() -> String)?` | null | 编码文本（响应式） |
| size | Float | 248 | 宽=高 |
| foregroundColor / bgColor | Color | 黑/白 | 前景/背景 |
| errorCorrectionLevel | ErrorCorrectionLevel | MEDIUM | L/M/H/VERY_HIGH |
| shape | QRCodeShape | SQUARE | SQUARE / CIRCLE / ROUND_RECT |
| roundRectRadiusRatio | Float | 0.3 | 圆角比例（仅 ROUND_RECT） |
| useGradient + gradientStart/EndColor | Boolean/Color | false | 线性渐变前景 |
| probeSquareStyle | Boolean | true | 定位图案保持方形 |
| quietZoneModules | Int | 4 | 静区模块数（ISO 建议 4） |

### BarcodeViewAttr

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| textProvider | `(() -> String)?` | null | 编码文本（仅 ASCII 32~126） |
| barcodeWidth / barcodeHeight | Float | 650/120 | 条码区域尺寸 |
| foregroundColor / bgColor | Color | 黑/白 | 条/空颜色 |
| quietZoneModules | Int | 10 | 静区模块数（ISO 建议 10） |
| showText | Boolean | false | 显示人眼可读文本 |
| textFontSize / textColor / textMarginTop | - | 24/灰/8 | 文本样式 |

### 编码 API（脱离 UI 直接取矩阵）

```kotlin
// QR：富元数据矩阵（含方块类型：定位/校正/定时/数据，可用于自定义渲染）
val matrix = QRCodeEngine.encode("Hello", ErrorCorrectionLevel.HIGH)
val bools = QRCodeEngine.encodeToBooleanMatrix("Hello")

// Code 128-B：条/空模块序列（true=黑条）
val bars = Code128Encoder.encode("ABC123")
```

## 鸿蒙接入

组件为纯 Kotlin common 代码（零平台分支），两种方式：

1. **源码模块**（推荐）：把 `kuiklyCode` 模块加入宿主 KuiklyUI 工程的鸿蒙构建
   （`settings.ohos.gradle.kts` include + 模块内 `build.ohos.gradle.kts` 已就位）；
2. **`-ohos` 变体**：在配置了 KuiklyBase ohos toolchain 的工程中执行
   `./gradlew -c settings.ohos.gradle.kts :kuiklyCode:publishToMavenLocal`，宿主从 mavenLocal 引用。

组件代码已在鸿蒙 Next 生产应用（五端电商 App）中随业务页运行验证。

## 与 QRose 的差异

官方市场另有 [QRose](https://github.com/Kuikly-contrib/qrose)（alexzhirkevich/qrose 的 Kuikly Compose DSL 适配版）。
本组件面向**经典 Kuikly DSL**（`com.tencent.kuikly.core`）工程：

- 不依赖 Compose 模块，classic DSL 工程零额外引入
- 引擎内联（qrcode-kotlin 移植 + 方块元数据扩展），单依赖体积极小
- Code 128-B 条形码 + 人眼可读文本 + 标准/可配静区

## 演示

`demo/pages/CodeDemoPage.kt`：二维码（方形/圆点/圆角渐变）+ 条形码（带/不带文本）完整示例。

## 测试

```bash
./gradlew :kuiklyCode:iosSimulatorArm64Test   # 或 jsNodeTest
```

覆盖：Code128 编码表 vs zxing 全量对照、校验和独立复算、完整编码向量、QR 矩阵结构不变量
（定位/定时图案、版本尺寸、确定性、纠错级别差异、UTF-8）。

## 许可证

MIT（见 [LICENSE](LICENSE)）。QR 引擎基于 [qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin)（MIT）
移植改造，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
