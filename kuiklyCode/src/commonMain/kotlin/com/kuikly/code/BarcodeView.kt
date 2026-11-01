package com.kuikly.code

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

/**
 * BarcodeView 属性
 */
class BarcodeViewAttr : ComposeAttr() {
    /** 要编码的文本（支持 Provider 模式实现响应式） */
    var textProvider: (() -> String)? = null

    /** 获取要编码的文本 */
    val text: String
        get() = textProvider?.invoke() ?: ""

    /** 条形码区域宽度（不含人眼可读文本行） */
    var barcodeWidth: Float = 650f

    /** 条形码区域高度 */
    var barcodeHeight: Float = 120f

    /** 前景色（条码颜色） */
    var foregroundColor: Color = Color.BLACK

    /** 背景色 */
    var bgColor: Color = Color.WHITE

    /** 左右静区宽度（模块数）。ISO 15417 建议 10X，设 0 可关闭（不建议） */
    var quietZoneModules: Int = 10

    /** 是否在条码下方绘制人眼可读文本 */
    var showText: Boolean = false

    /** 人眼可读文本字号 */
    var textFontSize: Float = 24f

    /** 人眼可读文本颜色 */
    var textColor: Color = Color(0xFF333333)

    /** 文本与条码的间距 */
    var textMarginTop: Float = 8f
}

/**
 * BarcodeView 事件
 */
class BarcodeViewEvent : ComposeEvent()

/**
 * 条形码视图组件（Code 128-B，纯 Canvas 绘制，跨端一致）
 *
 * - 完全本地生成，无网络、无原生依赖
 * - 内置标准静区（默认 10 模块，可配），保证扫码器识别率
 * - 可选人眼可读文本行
 *
 * ```kotlin
 * BarcodeView {
 *     attr {
 *         textProvider = { "1234567890" }
 *         barcodeWidth = 650f
 *         barcodeHeight = 120f
 *         showText = true
 *     }
 * }
 * ```
 */
class BarcodeView : ComposeView<BarcodeViewAttr, BarcodeViewEvent>() {

    override fun createAttr() = BarcodeViewAttr()
    override fun createEvent() = BarcodeViewEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    flexDirectionColumn()
                    alignItemsCenter()
                }

                Canvas({
                    attr {
                        width(ctx.attr.barcodeWidth)
                        height(ctx.attr.barcodeHeight)
                    }
                }) { context, width, height ->
                    val text = ctx.attr.text
                    if (text.isEmpty()) return@Canvas

                    try {
                        val encoded = Code128Encoder.encode(text)
                        drawBarcode(
                            context, encoded, width, height,
                            ctx.attr.quietZoneModules,
                            ctx.attr.foregroundColor, ctx.attr.bgColor
                        )
                    } catch (_: IllegalArgumentException) {
                        // 含 Code B 不支持的字符时不绘制
                    }
                }

                vif({ ctx.attr.showText }) {
                    Text {
                        attr {
                            text(ctx.attr.text)
                            fontSize(ctx.attr.textFontSize)
                            color(ctx.attr.textColor)
                            marginTop(ctx.attr.textMarginTop)
                            width(ctx.attr.barcodeWidth)
                            textAlignCenter()
                        }
                    }
                }
            }
        }
    }

    companion object {
        /**
         * 绘制条形码：黑条序列 + 标准静区
         * 模块宽度 = 宽度 / (总模块数 + 2×静区)
         */
        private fun drawBarcode(
            context: CanvasContext,
            encoded: BooleanArray,
            width: Float,
            height: Float,
            quietZoneModules: Int,
            foregroundColor: Color,
            bgColor: Color,
        ) {
            // 绘制背景
            context.fillStyle(bgColor)
            context.beginPath()
            context.moveTo(0f, 0f)
            context.lineTo(width, 0f)
            context.lineTo(width, height)
            context.lineTo(0f, height)
            context.closePath()
            context.fill()

            if (encoded.isEmpty()) return

            val moduleWidth = width / (encoded.size + 2f * quietZoneModules)
            val offsetX = quietZoneModules * moduleWidth

            context.fillStyle(foregroundColor)
            context.beginPath()
            for (i in encoded.indices) {
                if (!encoded[i]) continue
                val x = offsetX + i * moduleWidth
                context.moveTo(x, 0f)
                context.lineTo(x + moduleWidth, 0f)
                context.lineTo(x + moduleWidth, height)
                context.lineTo(x, height)
                context.closePath()
            }
            context.fill()
        }
    }
}

/**
 * 扩展函数：在 ViewContainer 中使用 BarcodeView
 */
fun ViewContainer<*, *>.BarcodeView(init: BarcodeView.() -> Unit) {
    addChild(BarcodeView(), init)
}
