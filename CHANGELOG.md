# Changelog

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
