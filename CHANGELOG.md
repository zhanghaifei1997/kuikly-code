# Changelog

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
