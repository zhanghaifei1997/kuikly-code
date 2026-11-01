package com.example.code_demo.pages

import com.kuikly.code.BarcodeView
import com.kuikly.code.QRCodeShape
import com.kuikly.code.QRCodeView
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.BasePager
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.views.View

/**
 * kuikly-code 演示页
 * 展示二维码（方形/圆点/圆角/渐变）与条形码（Code 128-B）的各种形态
 */
@Page("code_demo")
internal class CodeDemoPage : BasePager() {

    override fun body(): ViewBuilder {
        return {
            View {
                attr {
                    flex(1f)
                    backgroundColor(Color(0xFFF5F5F5))
                    flexDirectionColumn()
                    alignItemsCenter()
                }

                // ===== 二维码：默认方形 =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(40f)
                    }
                }

                // ===== 二维码：圆点风格 + 品牌色 =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(24f)
                        shape = QRCodeShape.CIRCLE
                        foregroundColor = Color(0xFF0052D9)
                        bgColor = Color.WHITE
                    }
                }

                // ===== 二维码：圆角 + 渐变 =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(24f)
                        shape = QRCodeShape.ROUND_RECT
                        useGradient = true
                        gradientStartColor = Color(0xFF0052D9)
                        gradientEndColor = Color(0xFF7B61FF)
                    }
                }

                // ===== 条形码：Code 128-B + 人眼可读文本 =====
                BarcodeView {
                    attr {
                        textProvider = { "MEMBER-2026-0001" }
                        barcodeWidth = 320f
                        barcodeHeight = 90f
                        showText = true
                        marginTop(32f)
                    }
                }

                // ===== 条形码：纯条码（自定义颜色） =====
                BarcodeView {
                    attr {
                        textProvider = { "1234567890" }
                        barcodeWidth = 320f
                        barcodeHeight = 70f
                        foregroundColor = Color(0xFF333333)
                        marginTop(24f)
                    }
                }
            }
        }
    }
}
