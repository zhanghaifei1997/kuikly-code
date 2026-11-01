package com.kuikly.code

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign

/**
 * 条码制式
 */
enum class BarcodeFormat {
    /** Code 128-B（默认；autoSubset 开启时数字段自动切 Code C） */
    CODE128,
    /** EAN-13（零售商品码，12 位自动补校验位 / 13 位校验） */
    EAN13,
}

/**
 * BarcodeView 属性
 */
class BarcodeViewAttr : ComposeAttr() {
    /** 要编码的文本（支持 Provider 模式实现响应式） */
    var textProvider: (() -> String)? = null

    /** 获取要编码的文本 */
    val text: String
        get() = textProvider?.invoke() ?: ""

    /** 条码制式 */
    var format: BarcodeFormat = BarcodeFormat.CODE128

    /** Code 128：数字段自动切换 Code C（密度翻倍，0.2.0+ 默认开） */
    var autoSubset: Boolean = true

    /** 条码区域宽度 */
    var barcodeWidth: Float = 650f

    /** 条码区域高度（条码主体，不含文本行） */
    var barcodeHeight: Float = 120f

    /** 前景色（条码颜色） */
    var foregroundColor: Color = Color.BLACK

    /** 背景色 */
    var bgColor: Color = Color.WHITE

    /** 左右静区宽度（模块数）。Code128 建议 10X、EAN13 建议 ≥7X，设 0 可关闭（不建议） */
    var quietZoneModules: Int = 10

    /** 是否在条码下方绘制人眼可读文本（Canvas 绘制，EAN13 为经典三段式） */
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
class BarcodeViewEvent : ComposeEvent() {
    /** 编码失败回调：code 1=文本为空, 2=编码异常（非法字符/校验位错误等） */
    internal var onError: ((code: Int, message: String) -> Unit)? = null

    fun error(callback: (code: Int, message: String) -> Unit) {
        onError = callback
    }
}

/**
 * 条形码视图组件（纯 Canvas 绘制，跨端一致，0.2.0 支持 EAN-13 与 Code 128-C 混合）
 *
 * - Code 128：数字段自动切 Code C（可关），完全本地生成
 * - EAN-13：12 位自动补校验位，13 位严格校验，人眼可读文本为经典三段式布局
 * - 内置标准静区（默认 10 模块，可配），保证扫码器识别率
 *
 * ```kotlin
 * BarcodeView {
 *     attr {
 *         textProvider = { "6901028180297" }
 *         format = BarcodeFormat.EAN13
 *         barcodeWidth = 320f
 *         barcodeHeight = 120f
 *         showText = true
 *     }
 *     event {
 *         error { code, msg -> /* 兜底 UI */ }
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
            val textRowHeight =
                if (ctx.attr.showText) ctx.attr.textMarginTop + ctx.attr.textFontSize * 1.2f else 0f

            Canvas({
                attr {
                    width(ctx.attr.barcodeWidth)
                    height(ctx.attr.barcodeHeight + textRowHeight)
                }
            }) { context, width, height ->
                val text = ctx.attr.text
                if (text.isEmpty()) {
                    ctx.event.onError?.invoke(ERROR_EMPTY_TEXT, "文本为空，跳过绘制")
                    return@Canvas
                }

                try {
                    when (ctx.attr.format) {
                        BarcodeFormat.CODE128 -> {
                            val encoded = if (ctx.attr.autoSubset) {
                                Code128Encoder.encodeAuto(text)
                            } else {
                                Code128Encoder.encode(text)
                            }
                            drawBarcode(
                                context, encoded, width,
                                ctx.attr.barcodeHeight, ctx.attr.quietZoneModules,
                                ctx.attr.foregroundColor, ctx.attr.bgColor,
                            )
                            if (ctx.attr.showText) {
                                drawCenteredText(
                                    context, text, width, ctx.attr.barcodeHeight,
                                    ctx.attr.textMarginTop, ctx.attr.textFontSize, ctx.attr.textColor,
                                )
                            }
                        }

                        BarcodeFormat.EAN13 -> {
                            val encoded = EAN13Encoder.encode(text)
                            drawBarcode(
                                context, encoded, width,
                                ctx.attr.barcodeHeight, ctx.attr.quietZoneModules,
                                ctx.attr.foregroundColor, ctx.attr.bgColor,
                            )
                            if (ctx.attr.showText) {
                                drawEan13Text(
                                    context, text, width, ctx.attr.barcodeHeight,
                                    encoded.size + 2f * ctx.attr.quietZoneModules, ctx.attr.quietZoneModules.toFloat(),
                                    ctx.attr.textMarginTop, ctx.attr.textFontSize, ctx.attr.textColor,
                                )
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    ctx.event.onError?.invoke(ERROR_ENCODE, e.message ?: "编码失败")
                }
            }
        }
    }

    companion object {
        /** 错误码：文本为空 */
        const val ERROR_EMPTY_TEXT = 1

        /** 错误码：编码异常（非法字符/校验位错误等） */
        const val ERROR_ENCODE = 2

        /**
         * 绘制条码主体：黑条序列 + 标准静区，条码区域高度为 barsHeight
         * 模块宽度 = 宽度 / (总模块数 + 2×静区)
         */
        private fun drawBarcode(
            context: CanvasContext,
            encoded: BooleanArray,
            width: Float,
            barsHeight: Float,
            quietZoneModules: Int,
            foregroundColor: Color,
            bgColor: Color,
        ) {
            // 绘制背景（覆盖整个条码区域，不覆盖文本行，避免文字压在色块上）
            context.fillStyle(bgColor)
            context.beginPath()
            context.moveTo(0f, 0f)
            context.lineTo(width, 0f)
            context.lineTo(width, barsHeight)
            context.lineTo(0f, barsHeight)
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
                context.lineTo(x + moduleWidth, barsHeight)
                context.lineTo(x, barsHeight)
                context.closePath()
            }
            context.fill()
        }

        /** Code 128 整体居中文本 */
        private fun drawCenteredText(
            context: CanvasContext,
            text: String,
            width: Float,
            barsHeight: Float,
            marginTop: Float,
            fontSize: Float,
            color: Color,
        ) {
            context.fillStyle(color)
            context.font(fontSize)
            context.textAlign(TextAlign.CENTER)
            context.fillText(text, width / 2f, barsHeight + marginTop + fontSize * 0.9f)
        }

        /**
         * EAN-13 经典三段式文本：首位在左静区、左 6 位居左半、右 6 位居右半
         * 段中心与对应模块区间对齐
         */
        private fun drawEan13Text(
            context: CanvasContext,
            text: String,
            width: Float,
            barsHeight: Float,
            totalUnits: Float,
            quietZone: Float,
            marginTop: Float,
            fontSize: Float,
            color: Color,
        ) {
            val digits = if (text.length == 12) text + EAN13Encoder.calcCheckDigit(text) else text
            val moduleWidth = width / totalUnits
            val baseline = barsHeight + marginTop + fontSize * 0.9f

            context.fillStyle(color)
            context.font(fontSize)
            context.textAlign(TextAlign.CENTER)

            // 首位：左静区中央
            context.fillText(digits[0].toString(), quietZone * moduleWidth / 2f, baseline)
            // 左 6 位（d2..d7）：数据区 3..44 模块的中心
            context.fillText(digits.substring(1, 7), (quietZone + 3f + 21f) * moduleWidth, baseline)
            // 右 6 位（d8..d13）：数据区 50..91 模块的中心
            context.fillText(digits.substring(7, 13), (quietZone + 50f + 21f) * moduleWidth, baseline)
        }
    }
}

/**
 * 扩展函数：在 ViewContainer 中使用 BarcodeView
 */
fun ViewContainer<*, *>.BarcodeView(init: BarcodeView.() -> Unit) {
    addChild(BarcodeView(), init)
}
