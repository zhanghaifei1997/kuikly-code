package com.kuikly.code

import com.kuikly.code.engine.QRCodeEngine
import com.kuikly.code.engine.internals.QRCodeSquare
import com.kuikly.code.engine.internals.QRCodeSquareType
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import kotlin.math.PI

/**
 * 二维码模块形状
 */
enum class QRCodeShape {
    /** 方形（默认） */
    SQUARE,
    /** 圆形（圆点风格） */
    CIRCLE,
    /** 圆角方形 */
    ROUND_RECT,
}

/**
 * QRCodeView 属性
 */
class QRCodeViewAttr : ComposeAttr() {
    /** 要编码的文本（支持 Provider 模式实现响应式） */
    var textProvider: (() -> String)? = null

    /** 获取要编码的文本 */
    val text: String
        get() = textProvider?.invoke() ?: ""

    /** 二维码尺寸（宽=高） */
    var size: Float = 248f

    /** 前景色（深色模块颜色） */
    var foregroundColor: Color = Color.BLACK

    /** 背景色（浅色模块颜色） */
    var bgColor: Color = Color.WHITE

    /** 纠错级别 */
    var errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.MEDIUM

    /** 数据模块形状 */
    var shape: QRCodeShape = QRCodeShape.SQUARE

    /** 圆角半径比例 (0.0~1.0)，仅 ROUND_RECT 形状时有效 */
    var roundRectRadiusRatio: Float = 0.3f

    /** 是否使用渐变色 */
    var useGradient: Boolean = false

    /** 渐变起始色（useGradient=true 时有效） */
    var gradientStartColor: Color = Color(0xFF000000)

    /** 渐变结束色（useGradient=true 时有效） */
    var gradientEndColor: Color = Color(0xFF333333)

    /** 定位图案是否保持方形（即使数据模块使用其他形状），建议保持默认 */
    var probeSquareStyle: Boolean = true

    /** 四周静区宽度（模块数）。ISO 18004 建议 4X，设 0 可关闭（不建议） */
    var quietZoneModules: Int = 4
}

/**
 * QRCodeView 事件
 */
class QRCodeViewEvent : ComposeEvent()

/**
 * 二维码视图组件
 * 内置 QR 编码引擎（qrcode-kotlin 血统）+ Kuikly Canvas 绘制，
 * 支持多种模块形状（方形/圆点/圆角）和颜色（纯色/渐变），
 * 完全在本地生成二维码，不依赖网络与原生能力。
 *
 * ```kotlin
 * QRCodeView {
 *     attr {
 *         textProvider = { "https://example.com" }
 *         size = 248f
 *         shape = QRCodeShape.CIRCLE
 *         useGradient = true
 *     }
 * }
 * ```
 */
class QRCodeView : ComposeView<QRCodeViewAttr, QRCodeViewEvent>() {

    override fun createAttr() = QRCodeViewAttr()
    override fun createEvent() = QRCodeViewEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            Canvas({
                attr {
                    width(ctx.attr.size)
                    height(ctx.attr.size)
                }
            }) { context, width, height ->
                val text = ctx.attr.text
                if (text.isEmpty()) return@Canvas

                try {
                    val rawData = QRCodeEngine.encode(text, ctx.attr.errorCorrectionLevel)
                    val moduleCount = rawData.size
                    val quietZone = ctx.attr.quietZoneModules
                    val moduleSize = width / (moduleCount + 2f * quietZone)
                    val offset = quietZone * moduleSize

                    // 绘制背景
                    drawBackground(context, width, height, ctx.attr.bgColor)

                    // 设置前景色（纯色或渐变）
                    if (ctx.attr.useGradient) {
                        val gradient = context.createLinearGradient(0f, 0f, width, height)
                        gradient.addColorStop(0f, ctx.attr.gradientStartColor)
                        gradient.addColorStop(1f, ctx.attr.gradientEndColor)
                        context.fillStyle(gradient)
                    } else {
                        context.fillStyle(ctx.attr.foregroundColor)
                    }

                    // 绘制深色模块
                    context.beginPath()
                    for (row in rawData.indices) {
                        for (col in rawData[row].indices) {
                            val square = rawData[row][col]
                            if (!square.dark) continue

                            val x = offset + col * moduleSize
                            val y = offset + row * moduleSize

                            // 定位图案保持方形（保证扫码器快速定位）
                            val useShape = if (ctx.attr.probeSquareStyle &&
                                square.squareInfo.type == QRCodeSquareType.POSITION_PROBE
                            ) {
                                QRCodeShape.SQUARE
                            } else {
                                ctx.attr.shape
                            }

                            drawModule(context, x, y, moduleSize, useShape, ctx.attr.roundRectRadiusRatio)
                        }
                    }
                    context.fill()
                } catch (_: Exception) {
                    // 编码失败（如文本超长）时不绘制
                }
            }
        }
    }

    companion object {
        /** 绘制背景 */
        private fun drawBackground(
            context: CanvasContext,
            width: Float,
            height: Float,
            bgColor: Color,
        ) {
            context.fillStyle(bgColor)
            context.beginPath()
            context.moveTo(0f, 0f)
            context.lineTo(width, 0f)
            context.lineTo(width, height)
            context.lineTo(0f, height)
            context.closePath()
            context.fill()
        }

        /** 绘制单个模块 */
        private fun drawModule(
            context: CanvasContext,
            x: Float,
            y: Float,
            size: Float,
            shape: QRCodeShape,
            radiusRatio: Float,
        ) {
            when (shape) {
                QRCodeShape.SQUARE -> {
                    context.moveTo(x, y)
                    context.lineTo(x + size, y)
                    context.lineTo(x + size, y + size)
                    context.lineTo(x, y + size)
                    context.closePath()
                }
                QRCodeShape.CIRCLE -> {
                    val cx = x + size / 2f
                    val cy = y + size / 2f
                    val r = size / 2f * 0.85f // 稍微缩小避免粘连
                    context.moveTo(cx + r, cy)
                    context.arc(cx, cy, r, 0f, PI.toFloat() * 2f, false)
                    context.closePath()
                }
                QRCodeShape.ROUND_RECT -> {
                    val radius = size * radiusRatio.coerceIn(0f, 0.5f)
                    drawRoundedRect(context, x, y, size, size, radius)
                }
            }
        }

        /** 用 arc + lineTo 绘制圆角矩形路径 */
        private fun drawRoundedRect(
            context: CanvasContext,
            x: Float,
            y: Float,
            w: Float,
            h: Float,
            r: Float,
        ) {
            val pi = PI.toFloat()
            // 从左上圆角开始，顺时针绘制
            context.moveTo(x + r, y)
            context.lineTo(x + w - r, y)
            context.arc(x + w - r, y + r, r, pi * 1.5f, pi * 2f, false)
            context.lineTo(x + w, y + h - r)
            context.arc(x + w - r, y + h - r, r, 0f, pi * 0.5f, false)
            context.lineTo(x + r, y + h)
            context.arc(x + r, y + h - r, r, pi * 0.5f, pi, false)
            context.lineTo(x, y + r)
            context.arc(x + r, y + r, r, pi, pi * 1.5f, false)
            context.closePath()
        }
    }
}

/**
 * 扩展函数：在 ViewContainer 中使用 QRCodeView
 */
fun ViewContainer<*, *>.QRCodeView(init: QRCodeView.() -> Unit) {
    addChild(QRCodeView(), init)
}
