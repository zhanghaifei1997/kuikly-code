package com.example.code_demo.pages

import com.kuikly.code.BarcodeFormat
import com.kuikly.code.BarcodeView
import com.kuikly.code.QRCodeShape
import com.kuikly.code.QRCodeView
import com.kuikly.code.QRRefreshAnimation
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BasePager
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * kuikly-code 演示页
 * 展示二维码（方形/圆点/圆角/菱形/渐变/logo）与条形码（Code128 auto-B/C、EAN-13）
 */
@Page("code_demo")
internal class CodeDemoPage : BasePager() {

    /** 动态刷新演示：轮换码与倒计时文案（observable 驱动） */
    private var rotatingCode: String by observable("PAY-2026-A1B2C3")
    private var countdownText: String by observable("")

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flex(1f)
                    backgroundColor(Color(0xFFF5F5F5))
                    flexDirectionColumn()
                    alignItemsCenter()
                }

                // ===== 二维码：默认方形（自动最优掩码） =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(40f)
                    }
                }

                // ===== 二维码：圆点 + 品牌色 + 中心 logo（高纠错） =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(24f)
                        shape = QRCodeShape.CIRCLE
                        foregroundColor = Color(0xFF0052D9)
                        errorCorrectionLevel = ErrorCorrectionLevel.HIGH
                        logoPath = "common/images/logo.png"
                    }
                    event {
                        error { code, msg -> /* 兜底 UI */ }
                    }
                }

                // ===== 二维码：菱形 + 渐变 + 版本下限（尺寸稳定） =====
                QRCodeView {
                    attr {
                        textProvider = { "https://kuikly.tds.qq.com" }
                        size = 200f
                        marginTop(24f)
                        shape = QRCodeShape.DIAMOND
                        useGradient = true
                        gradientStartColor = Color(0xFF0052D9)
                        gradientEndColor = Color(0xFF7B61FF)
                        minVersion = 4
                    }
                }

                // ===== 二维码：动态刷新（15 秒轮换 + Loading 转场 + 倒计时） =====
                QRCodeView {
                    attr {
                        textProvider = { ctx.rotatingCode }
                        size = 200f
                        marginTop(24f)
                        refreshIntervalSeconds = 15
                        refreshAnimation = QRRefreshAnimation.LOADING
                    }
                    event {
                        refresh {
                            // 模拟取新码（实际为网络请求）；组件轮询 textProvider 检测变化
                            ctx.rotatingCode = "PAY-2026-" + kotlin.random.Random.nextInt(100000, 999999)
                        }
                        countdown { s -> ctx.countdownText = "${s}s 后自动更新" }
                    }
                }
                Text {
                    attr {
                        text(countdownText)
                        fontSize(24f)
                        color(Color(0xFF999999))
                        marginTop(12f)
                    }
                }

                // ===== 条形码：Code 128（数字段自动切 C，密度更高） =====
                BarcodeView {
                    attr {
                        textProvider = { "MEMBER-2026-0001" }
                        barcodeWidth = 320f
                        barcodeHeight = 90f
                        showText = true
                        marginTop(32f)
                    }
                }

                // ===== 条形码：EAN-13（12 位自动补校验位，三段式文本） =====
                BarcodeView {
                    attr {
                        textProvider = { "690102818029" }
                        format = BarcodeFormat.EAN13
                        barcodeWidth = 320f
                        barcodeHeight = 110f
                        showText = true
                        marginTop(24f)
                    }
                }
            }
        }
    }
}
