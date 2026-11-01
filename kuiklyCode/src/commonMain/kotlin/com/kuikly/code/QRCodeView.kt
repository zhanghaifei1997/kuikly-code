package com.kuikly.code

import com.kuikly.code.engine.QRCodeEngine
import com.kuikly.code.engine.internals.QRCodeSquareType
import com.kuikly.code.engine.raw.ErrorCorrectionLevel
import com.kuikly.code.engine.raw.MaskPattern
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.Rotate
import com.tencent.kuikly.core.base.Translate
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.timer.postDelayed
import com.tencent.kuikly.core.timer.cancelPostCallback
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
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
    /** 菱形 */
    DIAMOND,
}

/**
 * 组件展示状态（0.4.0+）：由宿主业务驱动，组件按状态渲染对应视图。
 * 宿主的取数、重试、轮换定时器均属业务逻辑，组件只负责展示。
 */
enum class QRDisplayState {
    /** 正常展示二维码（textProvider 数据有效） */
    SUCCESS,
    /** 加载中：显示 loading 动效（默认旋转圆弧，可用 loadingView 自定义） */
    LOADING,
    /** 空数据：显示空态占位（默认图形+文字，可用 emptyView 自定义） */
    EMPTY,
    /** 异常：显示错误占位（默认图形+文字，可用 errorView 自定义） */
    ERROR,
}

/**
 * 数据更新过渡动画（0.4.0+）：SUCCESS 态下 textProvider 返回值变化时播放
 */
enum class QRTransition {
    /** 直接切换（无动画） */
    NONE,
    /** 渐入渐出（交叉淡化，默认） */
    CROSS_FADE,
    /** 旧码向上移出，新码自下方移入 */
    SLIDE_VERTICAL,
}

/**
 * QRCodeView 属性
 */
class QRCodeViewAttr : ComposeAttr() {
    /** 要编码的文本（响应式：数据源变化自动更新展示，SUCCESS 态数据变化可播过渡动画） */
    var textProvider: (() -> String)? = null

    /** 获取要编码的文本 */
    val text: String
        get() = textProvider?.invoke() ?: ""

    /** 展示状态 Provider（响应式，0.4.0+）：宿主业务状态机驱动（取数中/空/失败/成功）；
     *  null = 恒为 SUCCESS（纯展示用法） */
    var stateProvider: (() -> QRDisplayState)? = null

    /** 当前展示状态 */
    val displayState: QRDisplayState
        get() = stateProvider?.invoke() ?: QRDisplayState.SUCCESS

    /** 二维码尺寸（宽=高） */
    var size: Float = 248f

    /** 前景色（深色模块颜色；loading 圆弧与占位图形默认色） */
    var foregroundColor: Color = Color.BLACK

    /** 背景色（浅色模块颜色；非 SUCCESS 态背景同色） */
    var bgColor: Color = Color.WHITE

    /** 纠错级别（叠加 logo 时建议 HIGH 及以上） */
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

    /** 版本下限（1-40）：固定最小矩阵尺寸，防止文本变短时二维码跳变缩小 */
    var minVersion: Int = 1

    /** 指定掩码模式；null = 自动选择惩罚分最低的最优掩码 */
    var maskPattern: MaskPattern? = null

    /** 中心 logo 图片路径；叠加 logo 会遮挡部分模块，建议配合 HIGH/VERY_HIGH 纠错 */
    var logoPath: String? = null

    /** logo 尺寸（0 = 自动取 size 的 22%） */
    var logoSize: Float = 0f

    /** logo 白底内边距 */
    var logoPadding: Float = 4f

    /** logo 白底圆角半径（0 = 直角） */
    var logoCornerRadius: Float = 0f

    /** logo 白底颜色 */
    var logoBgColor: Color = Color.WHITE

    // ---------- 数据更新过渡（0.4.0+） ----------

    /** 过渡动画类型，默认交叉淡化 */
    var transition: QRTransition = QRTransition.CROSS_FADE

    /** 过渡动画时长（毫秒） */
    var transitionDurationMs: Int = 350

    // ---------- 状态占位（0.4.0+） ----------

    /** 自定义 loading 视图（LOADING 态生效；null = 默认旋转圆弧） */
    var loadingView: (ViewContainer<*, *>.() -> Unit)? = null

    /** 自定义空态视图（EMPTY 态生效；null = 默认虚线框 + 文案） */
    var emptyView: (ViewContainer<*, *>.() -> Unit)? = null

    /** 自定义异常视图（ERROR 态生效；null = 默认感叹号 + 文案；可在注入视图中自行放重试按钮） */
    var errorView: (ViewContainer<*, *>.() -> Unit)? = null

    /** 空态默认文案 */
    var emptyText: String = "暂无内容"

    /** 异常态默认文案 */
    var errorText: String = "加载失败"

    /** 占位图形/文字颜色 */
    var placeholderColor: Color = Color(0xFF999999)
}

/**
 * QRCodeView 事件
 */
class QRCodeViewEvent : ComposeEvent() {
    /** 编码失败回调：code 2=编码异常（超长等） */
    internal var onError: ((code: Int, message: String) -> Unit)? = null

    fun error(callback: (code: Int, message: String) -> Unit) {
        onError = callback
    }
}

/**
 * 二维码视图组件
 *
 * 内置 QR 编码引擎（qrcode-kotlin 血统）+ Kuikly Canvas 绘制，
 * 纯本地生成、零原生依赖。**组件只负责展示**：取数/重试/轮换定时器由宿主业务实现，
 * 通过响应式 Provider 把「数据 + 状态」传进来。
 *
 * ```kotlin
 * // 宿主 viewmodel 自管状态机（loading/empty/error/success）与轮换定时器
 * QRCodeView {
 *     attr {
 *         textProvider = { vm.payCode }
 *         stateProvider = { vm.state }              // SUCCESS/LOADING/EMPTY/ERROR
 *         transition = QRTransition.CROSS_FADE     // SUCCESS 数据变化时的过渡动画
 *         errorCorrectionLevel = ErrorCorrectionLevel.HIGH
 *     }
 *     event {
 *         error { code, msg -> vm.fallback() }
 *     }
 * }
 * ```
 *
 * - 数据更新：`textProvider` 返回值变化 → 自动重绘；SUCCESS 态且配置过渡动画时，
 *   旧码与新码双画布交叉过渡（渐入渐出 / 上下推移），由响应式检测驱动，无轮询
 * - LOADING：默认旋转圆弧（声明式动画），`loadingView` 可注入任意自定义效果
 * - EMPTY / ERROR：默认占位图形+文案，`emptyView` / `errorView` 可注入（重试按钮等自行实现）
 */
class QRCodeView : ComposeView<QRCodeViewAttr, QRCodeViewEvent>() {

    override fun createAttr() = QRCodeViewAttr()
    override fun createEvent() = QRCodeViewEvent()

    /** 文件级绘制/动画辅助的访问桥（attr/event 为 protected） */
    internal val viewAttr: QRCodeViewAttr get() = attr
    internal val viewEvent: QRCodeViewEvent get() = event

    /** 已提交展示的文本（过渡期间旧画布内容；NONE 模式直接跟随 provider） */
    internal var currentText: String by observable("")

    /** 过渡动画激活开关（两步渲染：先静态铺初值，再翻 true 触发 animate） */
    internal var animActive: Boolean by observable(false)

    /** 每次过渡递增，作为动画触发 key */
    internal var animKey: Int by observable(0)

    /** 过渡层插入序号（防止 vif 快速重建时旧定时器提交新过渡） */
    private var transitionSeq: Int = 0
    private val pendingTimeouts = mutableListOf<String>()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            View {
                attr {
                    width(ctx.viewAttr.size)
                    height(ctx.viewAttr.size)
                    overflow(true)
                }

                // ===== SUCCESS：当前码画布（NONE 模式直接跟随 provider） =====
                vif({ ctx.viewAttr.displayState == QRDisplayState.SUCCESS }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            applyOutgoingAnim(ctx)
                        }
                        qrCanvas(ctx) { if (ctx.viewAttr.transition == QRTransition.NONE) ctx.viewAttr.text else ctx.currentText }
                    }

                    // ===== 过渡层：检测到 provider 数据变化时插入新码画布（响应式，无轮询） =====
                    vif({
                        ctx.viewAttr.transition != QRTransition.NONE &&
                            ctx.viewAttr.text.isNotEmpty() && ctx.viewAttr.text != ctx.currentText
                    }) {
                        addChild(QRTransitionLayer(ctx)) {}
                    }
                }

                // ===== LOADING =====
                vif({ ctx.viewAttr.displayState == QRDisplayState.LOADING }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            backgroundColor(ctx.viewAttr.bgColor)
                            justifyContentCenter()
                            alignItemsCenter()
                        }
                        vif({ ctx.viewAttr.loadingView != null }) {
                            ctx.viewAttr.loadingView?.invoke(this)
                        }
                        vif({ ctx.viewAttr.loadingView == null }) {
                            defaultLoadingView(ctx)
                        }
                    }
                }

                // ===== EMPTY =====
                vif({ ctx.viewAttr.displayState == QRDisplayState.EMPTY }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            backgroundColor(ctx.viewAttr.bgColor)
                            justifyContentCenter()
                            alignItemsCenter()
                        }
                        vif({ ctx.viewAttr.emptyView != null }) {
                            ctx.viewAttr.emptyView?.invoke(this)
                        }
                        vif({ ctx.viewAttr.emptyView == null }) {
                            defaultPlaceholder(ctx, isEmpty = true)
                        }
                    }
                }

                // ===== ERROR =====
                vif({ ctx.viewAttr.displayState == QRDisplayState.ERROR }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            backgroundColor(ctx.viewAttr.bgColor)
                            justifyContentCenter()
                            alignItemsCenter()
                        }
                        vif({ ctx.viewAttr.errorView != null }) {
                            ctx.viewAttr.errorView?.invoke(this)
                        }
                        vif({ ctx.viewAttr.errorView == null }) {
                            defaultPlaceholder(ctx, isEmpty = false)
                        }
                    }
                }

                // ===== 中心 logo 叠加（仅 SUCCESS，最上层） =====
                vif({ ctx.viewAttr.displayState == QRDisplayState.SUCCESS && !ctx.viewAttr.logoPath.isNullOrEmpty() }) {
                    val logoSize = if (ctx.viewAttr.logoSize > 0f) ctx.viewAttr.logoSize else ctx.viewAttr.size * 0.22f
                    val box = logoSize + ctx.viewAttr.logoPadding * 2f
                    val offset = (ctx.viewAttr.size - box) / 2f
                    View {
                        attr {
                            positionAbsolute()
                            left(offset)
                            top(offset)
                            width(box)
                            height(box)
                            backgroundColor(ctx.viewAttr.logoBgColor)
                            borderRadius(ctx.viewAttr.logoCornerRadius)
                        }
                        Image {
                            attr {
                                marginLeft(ctx.viewAttr.logoPadding)
                                marginTop(ctx.viewAttr.logoPadding)
                                width(logoSize)
                                height(logoSize)
                                src(ctx.viewAttr.logoPath ?: "")
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 过渡调度（由 QRTransitionLayer 插入时触发） ----------

    internal fun onTransitionLayerAdded() {
        val seq = ++transitionSeq
        animActive = false
        animKey += 1
        // 两步渲染：先让新画布以初始态（隐藏/下方）铺好，再激活动画
        pendingTimeouts += postDelayed(30) {
            if (seq == transitionSeq) animActive = true
        }
        pendingTimeouts += postDelayed(attr.transitionDurationMs + 120) {
            if (seq == transitionSeq) commitTransition()
        }
    }

    private fun commitTransition() {
        currentText = attr.text
        animActive = false
    }

    override fun didRemoveFromParentView() {
        super.didRemoveFromParentView()
        transitionSeq++ // 使未决定时器全部失效
        pendingTimeouts.forEach { cancelPostCallback(it) }
        pendingTimeouts.clear()
    }

    companion object {
        /** 错误码：文本为空 */
        const val ERROR_EMPTY_TEXT = 1

        /** 错误码：编码异常（文本超长等） */
        const val ERROR_ENCODE = 2

        /** 绘制背景 */
        internal fun drawBackground(
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
        internal fun drawModule(
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
                QRCodeShape.DIAMOND -> {
                    // 菱形：四点钻石，半对角线略缩避免粘连
                    val r = size / 2f * 0.92f
                    val cx = x + size / 2f
                    val cy = y + size / 2f
                    context.moveTo(cx, cy - r)
                    context.lineTo(cx + r, cy)
                    context.lineTo(cx, cy + r)
                    context.lineTo(cx - r, cy)
                    context.closePath()
                }
            }
        }

        /** 用 arc + lineTo 绘制圆角矩形路径 */
        internal fun drawRoundedRect(
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
 * 过渡层容器：插入视图树时通知宿主组件启动过渡调度
 * （Kuikly vif 重建会重新走 didMoveToParentView，天然去重）
 */
internal class QRTransitionLayer(
    private val owner: QRCodeView,
) : ComposeView<ComposeAttr, ComposeEvent>() {
    override fun createAttr() = ComposeAttr()
    override fun createEvent() = ComposeEvent()

    override fun didMoveToParentView() {
        super.didMoveToParentView()
        if (parent != null) {
            owner.onTransitionLayerAdded()
        }
    }

    override fun body(): ViewBuilder {
        val ctx = owner
        return {
            View {
                attr {
                    positionAbsolute()
                    left(0f); top(0f)
                    width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                    applyIncomingAnim(ctx)
                }
                qrCanvas(ctx) { ctx.viewAttr.text }
            }
        }
    }
}

// ---------- 文件级绘制/动画辅助（Kotlin 成员扩展在非 inline DSL lambda 中不可用隐式接收者） ----------

/** 渲染一张 QR 画布，内容取自 textProvider */
private fun ViewContainer<*, *>.qrCanvas(
    ctx: QRCodeView,
    textProvider: () -> String,
) {
    Canvas({
        attr {
            width(ctx.viewAttr.size)
            height(ctx.viewAttr.size)
        }
    }) { context, width, height ->
        val text = textProvider()
        if (text.isEmpty()) {
            QRCodeView.drawBackground(context, width, height, ctx.viewAttr.bgColor)
            return@Canvas
        }
        try {
            drawQrContent(ctx, context, width, height, text)
        } catch (e: Exception) {
            ctx.viewEvent.onError?.invoke(QRCodeView.ERROR_ENCODE, e.message ?: "编码失败")
        }
    }
}

private fun drawQrContent(
    ctx: QRCodeView,
    context: CanvasContext,
    width: Float,
    height: Float,
    text: String,
) {
    val rawData = QRCodeEngine.encode(
        text, ctx.viewAttr.errorCorrectionLevel,
        ctx.viewAttr.maskPattern, ctx.viewAttr.minVersion,
    )
    val moduleCount = rawData.size
    val quietZone = ctx.viewAttr.quietZoneModules
    val moduleSize = width / (moduleCount + 2f * quietZone)
    val offset = quietZone * moduleSize

    QRCodeView.drawBackground(context, width, height, ctx.viewAttr.bgColor)

    if (ctx.viewAttr.useGradient) {
        val gradient = context.createLinearGradient(0f, 0f, width, height)
        gradient.addColorStop(0f, ctx.viewAttr.gradientStartColor)
        gradient.addColorStop(1f, ctx.viewAttr.gradientEndColor)
        context.fillStyle(gradient)
    } else {
        context.fillStyle(ctx.viewAttr.foregroundColor)
    }

    context.beginPath()
    for (row in rawData.indices) {
        for (col in rawData[row].indices) {
            val square = rawData[row][col]
            if (!square.dark) continue

            val x = offset + col * moduleSize
            val y = offset + row * moduleSize
            val useShape = if (ctx.viewAttr.probeSquareStyle &&
                square.squareInfo.type == QRCodeSquareType.POSITION_PROBE
            ) {
                QRCodeShape.SQUARE
            } else {
                ctx.viewAttr.shape
            }
            QRCodeView.drawModule(context, x, y, moduleSize, useShape, ctx.viewAttr.roundRectRadiusRatio)
        }
    }
    context.fill()
}

/** 旧码：过渡激活后淡出/上移出 */
private fun com.tencent.kuikly.core.base.Attr.applyOutgoingAnim(ctx: QRCodeView) {
    val animating = ctx.animActive
    val key = ctx.animKey
    val dur = ctx.viewAttr.transitionDurationMs / 1000f
    when {
        !animating -> opacity(1f)
        ctx.viewAttr.transition == QRTransition.SLIDE_VERTICAL -> {
            transform(Translate(0f, -1f))
            animate(Animation.easeInOut(dur), key)
        }
        else -> {
            opacity(0f)
            animate(Animation.easeInOut(dur), key)
        }
    }
}

/** 新码：初始隐藏，过渡激活后淡入/自下方移入 */
private fun com.tencent.kuikly.core.base.Attr.applyIncomingAnim(ctx: QRCodeView) {
    val active = ctx.animActive
    val key = ctx.animKey
    val dur = ctx.viewAttr.transitionDurationMs / 1000f
    when {
        !active -> if (ctx.viewAttr.transition == QRTransition.SLIDE_VERTICAL) {
            transform(Translate(0f, 1f)); opacity(1f)
        } else {
            opacity(0f)
        }
        ctx.viewAttr.transition == QRTransition.SLIDE_VERTICAL -> {
            transform(Translate(0f, 0f))
            animate(Animation.easeInOut(dur), key)
        }
        else -> {
            opacity(1f)
            animate(Animation.easeInOut(dur), key)
        }
    }
}

/** 默认 loading：3/4 圆弧 + Rotate(360°) 无限旋转（声明式动画，零定时器） */
private fun ViewContainer<*, *>.defaultLoadingView(ctx: QRCodeView) {
    val spinnerSize = ctx.viewAttr.size * 0.28f
    View {
        attr {
            width(spinnerSize)
            height(spinnerSize)
            transform(rotate = Rotate(360f))
            animate(Animation.linear(0.8f).repeatForever(true), true)
        }
        Canvas({
            attr {
                width(spinnerSize)
                height(spinnerSize)
            }
        }) { context, width, height ->
            context.lineWidth(spinnerSize * 0.12f)
            context.lineCapRound()
            context.strokeStyle(ctx.viewAttr.foregroundColor)
            context.beginPath()
            context.arc(
                width / 2f, height / 2f, spinnerSize * 0.38f,
                0f, PI.toFloat() * 1.5f, false,
            )
            context.stroke()
        }
    }
}

/** 默认空态/错误占位：Canvas 图形（虚线框 / 感叹号圆圈）+ 文案 */
private fun ViewContainer<*, *>.defaultPlaceholder(ctx: QRCodeView, isEmpty: Boolean) {
    val inner = ctx.viewAttr.size * 0.62f
    View {
        attr { justifyContentCenter(); alignItemsCenter() }
        Canvas({
            attr {
                width(inner)
                height(inner)
                marginBottom(12f)
            }
        }) { context, width, height ->
            context.strokeStyle(ctx.viewAttr.placeholderColor)
            context.lineWidth(3f)
            if (isEmpty) {
                // 空态：虚线圆角框
                context.setLineDash(listOf(8f, 6f))
                context.beginPath()
                val r = width * 0.12f
                context.moveTo(r, 0f)
                context.lineTo(width - r, 0f)
                context.arc(width - r, r, r, PI.toFloat() * 1.5f, PI.toFloat() * 2f, false)
                context.lineTo(width, height - r)
                context.arc(width - r, height - r, r, 0f, PI.toFloat() * 0.5f, false)
                context.lineTo(r, height)
                context.arc(r, height - r, r, PI.toFloat() * 0.5f, PI.toFloat(), false)
                context.lineTo(0f, r)
                context.arc(r, r, r, PI.toFloat(), PI.toFloat() * 1.5f, false)
                context.closePath()
                context.stroke()
            } else {
                // 错误态：圆圈 + 感叹号
                val cx = width / 2f
                val cy = height / 2f
                val r = width * 0.4f
                context.beginPath()
                context.arc(cx, cy, r, 0f, PI.toFloat() * 2f, false)
                context.stroke()
                context.beginPath()
                context.lineCapRound()
                context.lineWidth(5f)
                context.moveTo(cx, cy - r * 0.45f)
                context.lineTo(cx, cy + r * 0.1f)
                context.moveTo(cx, cy + r * 0.45f)
                context.lineTo(cx, cy + r * 0.42f)
                context.stroke()
            }
        }
        Text {
            attr {
                text(if (isEmpty) ctx.viewAttr.emptyText else ctx.viewAttr.errorText)
                fontSize(ctx.viewAttr.size * 0.075f)
                color(ctx.viewAttr.placeholderColor)
            }
        }
    }
}

/**
 * 扩展函数：在 ViewContainer 中使用 QRCodeView
 */
fun ViewContainer<*, *>.QRCodeView(init: QRCodeView.() -> Unit) {
    addChild(QRCodeView(), init)
}
