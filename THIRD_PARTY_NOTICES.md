# Third-Party Notices

本组件包含以下第三方开源软件的派生代码：

## qrcode-kotlin

- 项目：<https://github.com/g0dkar/qrcode-kotlin>
- 作者：Rafael Lins（qrcode-kotlin）、Kazuhiko Arase（原始 qrcode-generator）
- 许可证：MIT License
- 使用方式：`kuiklyCode/src/commonMain/kotlin/com/kuikly/code/engine/` 目录下的 QR 编码引擎
  （BitBuffer / QRMath / Polynomial / RSBlock / QRData / QRUtil / QRCodeSetup / QRCodeSquare /
  QRCodeProcessor 等）基于 qrcode-kotlin 移植与改造，增加了方块元数据（类型/区域）输出，
  用于支撑差异化渲染。原项目版权与许可证适用于上述文件。

```
MIT License

Copyright (c) 2021 Rafael Lins

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

## Code 128 编码表

`Code128Encoder` 中的条/空宽度编码表（values 0-106）对照 zxing
（Apache License 2.0，<https://github.com/zxing/zxing>）的 `Code128Reader.CODE_PATTERNS`
逐条校验，数值本身为 ISO/IEC 15417 标准定义，不构成版权作品。
