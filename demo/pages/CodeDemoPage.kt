package com.example.code_demo.pages

import com.kuikly.code.BarcodeFormat
import com.kuikly.code.BarcodeView
import com.kuikly.code.QRCodeShape
import com.kuikly.code.QRCodeView
import com.kuikly.code.QRDisplayState
import com.kuikly.code.QRTransition
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.BasePager
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * kuikly-code 演示页
 * 展示二维码（形状/渐变/logo/状态化/动态刷新）与条形码（Code128 auto-B/C、EAN-13）
 *
 * 动态刷新演示（0.4.0 架构）：轮换定时器与状态机在 Pager（业务侧），
 * 组件只通过 textProvider/stateProvider 接收「数据 + 状态」。
 */
@Page("code_demo")
internal class CodeDemoPage : BasePager() {

    // ── 业务侧状态机（宿主职责，组件不感知） ──
    private var rotatingCode: String by observable("PAY-2026-A1B2C3")
    private var rotatingState: QRDisplayState by observable(QRDisplayState.SUCCESS)
    private var countdownText: String by observable("")

    private var rotateTimer: Timer? = null
    private var countdownTimer: Timer? = null
    private var secondsLeft = 15

    override fun viewDidAppear() {
        super.viewDidAppear()
        startRotation()
    }

    override fun viewWillDisappear() {
        super.viewWillDisappear()
        rotateTimer?.cancel()
        countdownTimer?.cancel()
    }

    /** 轮换节奏业务自管：15 秒一轮，取数 800ms 模拟网络 */
    private fun startRotation() {
        secondsLeft = 15
        countdownTimer?.cancel()
        countdownTimer = Timer().apply {
            schedule(1000, 1000) {
                secondsLeft--
                countdownText = if (secondsLeft > 0) "${secondsLeft}s 后自动更新" else "更新中..."
                if (secondsLeft <= 0) cancel()
            }
        }
        rotateTimer?.cancel()
        rotateTimer = Timer().apply {
            schedule(15_000, 15_000) {
                fetchNewCode()
            }
        }
    }

    private fun fetchNewCode() {
        rotatingState = QRDisplayState.LOADING
        // 模拟异步取码（实际为网络请求）
        com.tencent.kuikly.core.timer.postDelayed(800) {
            rotatingCode = "PAY-2026-" + kotlin.random.Random.nextInt(100000, 999999)
            rotatingState = QRDisplayState.SUCCESS
            secondsLeft = 15
        }
    }

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
                }

                // ===== 二维码：状态化 + 动态刷新（业务侧定时器驱动） =====
                QRCodeView {
                    attr {
                        textProvider = { ctx.rotatingCode }
                        stateProvider = { ctx.rotatingState }
                        size = 200f
                        marginTop(24f)
                        transition = QRTransition.CROSS_FADE
                        // emptyView / errorView / loadingView 均可注入自定义视图
                    }
                    event {
                        error { _, _ -> /* 兜底 UI */ }
                    }
                }
                Text {
                    attr {
                        text(ctx.countdownText)
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
