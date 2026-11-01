# Changelog

## 0.4.0 (2026-11-02)

架构修正版本（**破坏性变更**，含迁移表）：组件回归纯展示职责，
取数/重试/轮换定时器交还宿主业务，通过响应式 Provider 传入「数据 + 状态」。

**新增**
- `stateProvider: (() -> QRDisplayState)?`：四态展示（`SUCCESS` / `LOADING` / `EMPTY` / `ERROR`），
  null = 恒 SUCCESS（纯展示用法完全兼容）
- `emptyView` / `errorView` 注入：空态/异常态自定义视图（可在其中自行放重试按钮）；
  默认占位 = Canvas 图形（虚线框 / 感叹号圆圈）+ 文案（`emptyText` / `errorText` / `placeholderColor` 可配）
- 数据更新过渡改为**纯响应式驱动**：SUCCESS 态 `textProvider` 返回值变化时，
  双画布交叉过渡由 `vif(text != currentText)` 检测触发（无轮询、无定时器），
  过渡层经 `didMoveToParentView` 钩子调度提交

**重命名 / 移除（0.3.0 → 0.4.0 迁移表）**

| 0.3.0 | 0.4.0 | 说明 |
|---|---|---|
| `refreshIntervalSeconds` / `onRefresh` / `onCountdown` / `refresh()` | 宿主自管 Timer + 状态机 | 轮换节奏是业务职责：vm 里 setTimeout 到点取数即可 |
| `QRRefreshAnimation.LOADING` | `stateProvider = { LOADING }` | loading 从「动画模式」改为「展示状态」 |
| `QRRefreshAnimation` | `QRTransition`（NONE/CROSS_FADE/SLIDE_VERTICAL） | 枚举重命名 |
| `refreshAnimation` / `refreshAnimationDurationMs` | `transition` / `transitionDurationMs` | 属性重命名 |
| `refreshTimeoutMs` | 移除 | 随轮询机制一并移除 |
| `loadingMaskColor` | 移除 | LOADING 态整区渲染，背景即 `bgColor` |
| `loadingView` | 保留 | 语义不变（LOADING 态注入） |

**宿主用法（付款码轮换）**

```kotlin
// ViewModel：状态机 + 轮换定时器（业务自管）
// state: QRDisplayState；payCode: String（observable）
QRCodeView {
    attr {
        textProvider = { vm.payCode }
        stateProvider = { vm.state }
        transition = QRTransition.CROSS_FADE
    }
    event { error { code, msg -> vm.fallback() } }
}
```

## 0.3.0 (2026-11-02)

动态刷新版本（付款码轮换场景，API 向后兼容）。

**QRCodeView 动态刷新**
- `refreshIntervalSeconds` 定时自动刷新：到点触发 `onRefresh` 事件，宿主更新数据源（可异步），
  组件轮询 `textProvider` 直到值变化（`refreshTimeoutMs` 超时自动放弃并重启倒计时）
- `onCountdown(secondsLeft)` 每秒倒计时回调（可做「N 秒后自动更新」UI）
- `refresh()` 手动触发（公开方法，任意时刻可调）
- 四种过渡动画 `QRRefreshAnimation`：
  - `NONE` 直接切换
  - `CROSS_FADE` 交叉淡化（默认，时长可配 `refreshAnimationDurationMs`）
  - `SLIDE_VERTICAL` 旧码上移出、新码自下方入（overflow 裁剪）
  - `LOADING` 转场等待 + 加载指示：默认 3/4 圆弧无限旋转（`Rotate(360°)+repeatForever` 声明式动画，零定时器），
    `loadingView` 可注入任意自定义 loading 视图；遮罩色可配
- 实现：observable 状态机（IDLE/WAITING/ANIMATING）+ 双画布绝对定位交叉过渡 + 两步渲染触发 `animate()`
- 视图移除时自动清理 Timer/postDelayed

## 0.2.0 (2026-11-02)

功能增强版本（API 向后兼容）。

**QRCodeView**
- 中心 logo 叠加（`logoPath/logoSize/logoPadding/logoCornerRadius/logoBgColor`），建议配合 HIGH 级纠错
- 掩码自动最优选择：默认按 ISO 18004 惩罚分规则在 8 种掩码中选最优（`maskPattern` 可指定覆盖）
- `minVersion` 版本下限：固定最小矩阵尺寸，防止文本变短时二维码跳变缩小
- 新增 DIAMOND（菱形）模块形状
- `onError` 事件（code 1=空文本, 2=编码异常）

**BarcodeView**
- 新增 EAN-13 制式（`format = BarcodeFormat.EAN13`）：12 位自动补校验位 / 13 位严格校验，
  编码表对照 zxing `UPCEANReader`/`EAN13Reader` 逐条校验，人眼可读文本为经典三段式布局
- Code 128 数字段自动切换 Code C（`autoSubset`，默认开）：纯数字密度约翻倍；
  切换策略与 zxing Code128Writer 一致（≥4 位连续数字切 C，奇数位首数字留 B）
- 人眼可读文本改为 Canvas 绘制（`fillText`），与条码共用一块画布
- `onError` 事件

**引擎/API**
- `QRCodeEngine.encode(text, ecl, maskPattern, minVersion)` 与 `getModuleCount(text, ecl, minVersion)` 重载
- `Code128Encoder.encodeAuto(text)` + `buildAutoValues`（internal）
- `EAN13Encoder`（encode/calcCheckDigit/isValid，纯逻辑可脱离 UI 使用）
- 测试 17 → 43：auto-B/C 手算向量、EAN13 表形/双源对照/校验位向量/结构布局、掩码惩罚分与最优性

## 0.1.0 (2026-11-02)

首个版本。

- `QRCodeView`：二维码视图，方形/圆点/圆角模块形状、纯色/渐变前景、定位图案保方、
  四档纠错级别、标准静区（默认 4 模块，可配）、`textProvider` 响应式
- `BarcodeView`：Code 128-B 条形码视图，编码表对照 zxing（ISO 15417）逐条校验、
  标准静区（默认 10 模块，可配）、可选人眼可读文本行、非法字符安全跳过
- `QRCodeEngine`：QR 编码门面（qrcode-kotlin MIT 移植，扩展方块元数据）
- `Code128Encoder`：Code 128-B 纯逻辑编码器（`IllegalArgumentException` 严格校验）
- 平台：Android / iOS / Web(JS) 经 Maven Central；鸿蒙源码模块或 `-ohos` 变体接入
- 测试：编码表 vs zxing 全量对照、校验和独立复算、完整编码向量、QR 结构不变量
