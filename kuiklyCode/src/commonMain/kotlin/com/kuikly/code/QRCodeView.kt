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
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.timer.postDelayed
import com.tencent.kuikly.core.timer.cancelPostCallback
import com.tencent.kuikly.core.views.Canvas
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.Image
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
    /** 菱形（0.2.0+） */
    DIAMOND,
}

/**
 * 刷新动画类型（0.3.0+）
 */
enum class QRRefreshAnimation {
    /** 直接切换（无动画） */
    NONE,
    /** 渐隐渐现（交叉淡化，默认） */
    CROSS_FADE,
    /** 旧码向上移出，新码从下方移入 */
    SLIDE_VERTICAL,
    /** Loading 转场：等待新内容期间显示加载指示（默认旋转圆弧，可用 loadingView 自定义） */
    LOADING,
}

/** 刷新阶段（内部状态机） */
internal enum class QRRefreshPhase {
    /** 空闲（正常展示 currentText） */
    IDLE,
    /** 已触发刷新，等待 textProvider 返回新内容（LOADING 模式下显示 loading） */
    WAITING,
    /** 已取得新内容，正在播放过渡动画 */
    ANIMATING,
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

    /** 版本下限（1-40，0.2.0+）：固定最小矩阵尺寸，防止文本变短时二维码跳变缩小 */
    var minVersion: Int = 1

    /** 指定掩码模式；null = 自动选择惩罚分最低的最优掩码（0.2.0+ 默认） */
    var maskPattern: MaskPattern? = null

    /** 中心 logo 图片路径（0.2.0+）；叠加 logo 会遮挡部分模块，建议配合 HIGH/VERY_HIGH 纠错 */
    var logoPath: String? = null

    /** logo 尺寸（0 = 自动取 size 的 22%） */
    var logoSize: Float = 0f

    /** logo 白底内边距 */
    var logoPadding: Float = 4f

    /** logo 白底圆角半径（0 = 直角） */
    var logoCornerRadius: Float = 0f

    /** logo 白底颜色 */
    var logoBgColor: Color = Color.WHITE

    // ---------- 动态刷新（0.3.0+） ----------

    /** 自动刷新间隔（秒），0 = 不自动刷新。到点触发 onRefresh 事件，宿主更新数据源后自动过渡到新码 */
    var refreshIntervalSeconds: Int = 0

    /** 刷新过渡动画类型，默认交叉淡化 */
    var refreshAnimation: QRRefreshAnimation = QRRefreshAnimation.CROSS_FADE

    /** 过渡动画时长（毫秒），CROSS_FADE / SLIDE_VERTICAL / LOADING 淡入生效 */
    var refreshAnimationDurationMs: Int = 350

    /** 触发刷新后等待 textProvider 返回新内容的超时（毫秒），超时未变化则放弃本次刷新 */
    var refreshTimeoutMs: Int = 10_000

    /** 自定义 loading 视图（LOADING 模式生效；null = 默认旋转圆弧） */
    var loadingView: (ViewContainer<*, *>.() -> Unit)? = null

    /** loading 遮罩背景色 */
    var loadingMaskColor: Color = Color(0xCCFFFFFF)
}

/**
 * QRCodeView 事件
 */
class QRCodeViewEvent : ComposeEvent() {
    /** 编码失败回调：code 1=文本为空, 2=编码异常（超长等） */
    internal var onError: ((code: Int, message: String) -> Unit)? = null

    fun error(callback: (code: Int, message: String) -> Unit) {
        onError = callback
    }

    /** 刷新间隔到点（或手动 refresh()）：宿主在此回调中更新 textProvider 背后的数据源（可异步）；
     *  组件会轮询 textProvider 直到值变化后自动播放过渡动画 */
    internal var onRefresh: (() -> Unit)? = null

    fun refresh(callback: () -> Unit) {
        onRefresh = callback
    }

    /** 每秒倒计时回调（refreshIntervalSeconds > 0 时），可用于展示「N 秒后自动更新」 */
    internal var onCountdown: ((secondsLeft: Int) -> Unit)? = null

    fun countdown(callback: (secondsLeft: Int) -> Unit) {
        onCountdown = callback
    }
}

/**
 * 二维码视图组件
 *
 * 内置 QR 编码引擎（qrcode-kotlin 血统）+ Kuikly Canvas 绘制，
 * 支持多种模块形状（方形/圆点/圆角/菱形）、颜色（纯色/渐变）、中心 logo、
 * 自动最优掩码与版本下限，完全在本地生成二维码，不依赖网络与原生能力。
 *
 * ## 动态刷新（0.3.0+，付款码轮换场景）
 * ```kotlin
 * QRCodeView {
 *     attr {
 *         textProvider = { vm.payCode }
 *         refreshIntervalSeconds = 60          // 60 秒自动轮换
 *         refreshAnimation = QRRefreshAnimation.LOADING
 *         refreshAnimationDurationMs = 350
 *     }
 *     event {
 *         refresh { vm.fetchNewPayCode() }      // 到点让宿主取新码（可异步）
 *         countdown { s -> vm.countdownText = "${s}s" }
 *         error { code, msg -> vm.fallback() }
 *     }
 * }
 * ```
 *
 * 过渡动画支持：NONE（直接切换）/ CROSS_FADE（交叉淡化）/ SLIDE_VERTICAL（上下推移）/
 * LOADING（转场等待 + 默认旋转圆弧或 [QRCodeViewAttr.loadingView] 自定义）。
 * 也可随时调用视图的 [refresh] 手动触发。
 */
class QRCodeView : ComposeView<QRCodeViewAttr, QRCodeViewEvent>() {

    override fun createAttr() = QRCodeViewAttr()
    override fun createEvent() = QRCodeViewEvent()

    /** 文件级绘制/动画辅助的访问桥（attr/event 为 protected） */
    internal val viewAttr: QRCodeViewAttr get() = attr
    internal val viewEvent: QRCodeViewEvent get() = event

    // ---------- 刷新状态机（observable 驱动重渲染） ----------

    /** 当前展示的文本（过渡期间旧画布内容） */
    internal var currentText: String by observable("")

    /** 过渡期间的新文本（null = 非过渡态） */
    internal var nextText: String? by observable(null)

    /** 状态机阶段 */
    internal var phase: QRRefreshPhase by observable(QRRefreshPhase.IDLE)

    /** 过渡动画启动开关（两步渲染：先静态铺初值，再翻 true 触发 animate） */
    internal var animActive: Boolean by observable(false)

    /** 每次过渡递增，作为动画触发 key */
    internal var animKey: Int by observable(0)

    /** 倒计时秒数（仅事件用，不驱动渲染） */
    private var secondsLeft: Int = 0

    private var countdownTimer: Timer? = null
    private var pollTimer: Timer? = null

    /** 轮询剩余次数（common 无时间 API，用 tick 计数：100ms/次） */
    private var pollTicksLeft: Int = 0
    private var pollOldText: String = ""
    private val pendingTimeouts = mutableListOf<String>()

    override fun body(): ViewBuilder {
        val ctx = this
        startCountdownIfNeeded()
        return {
            View {
                attr {
                    width(ctx.viewAttr.size)
                    height(ctx.viewAttr.size)
                    overflow(true)
                }

                // ===== 当前码画布（绝对定位铺满） =====
                View {
                    attr {
                        positionAbsolute()
                        left(0f); top(0f)
                        width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                        applyOutgoingAnim(ctx)
                    }
                    qrCanvas(ctx, { ctx.currentText })
                }

                // ===== 过渡中的新码画布（仅 ANIMATING 时存在） =====
                vif({ ctx.phase == QRRefreshPhase.ANIMATING && ctx.nextText != null }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            applyIncomingAnim(ctx)
                        }
                        qrCanvas(ctx, { ctx.nextText ?: "" })
                    }
                }

                // ===== LOADING 遮罩（仅 WAITING 且 LOADING 模式） =====
                vif({ ctx.phase == QRRefreshPhase.WAITING && ctx.viewAttr.refreshAnimation == QRRefreshAnimation.LOADING }) {
                    View {
                        attr {
                            positionAbsolute()
                            left(0f); top(0f)
                            width(ctx.viewAttr.size); height(ctx.viewAttr.size)
                            backgroundColor(ctx.viewAttr.loadingMaskColor)
                            justifyContentCenter()
                            alignItemsCenter()
                        }
                        vif({ ctx.viewAttr.loadingView != null }) {
                            View {
                                attr { justifyContentCenter(); alignItemsCenter() }
                                ctx.viewAttr.loadingView?.invoke(this)
                            }
                        }
                        vif({ ctx.viewAttr.loadingView == null }) {
                            defaultLoadingView(ctx)
                        }
                    }
                }

                // ===== 中心 logo 叠加（最上层，过渡期间保持） =====
                vif({ !ctx.viewAttr.logoPath.isNullOrEmpty() }) {
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

    // ---------- 刷新驱动 ----------

    /** 首帧同步展示文本 */
    private fun ensureCurrentTextInitialized() {
        if (currentText.isEmpty() && phase == QRRefreshPhase.IDLE) {
            currentText = attr.text
        }
    }

    private fun startCountdownIfNeeded() {
        ensureCurrentTextInitialized()
        if (attr.refreshIntervalSeconds > 0 && countdownTimer == null) {
            secondsLeft = attr.refreshIntervalSeconds
            countdownTimer = Timer().apply {
                schedule(1000, 1000) {
                    if (phase == QRRefreshPhase.IDLE) {
                        secondsLeft--
                        if (secondsLeft <= 0) {
                            startRefresh()
                        } else {
                            event.onCountdown?.invoke(secondsLeft)
                        }
                    }
                }
            }
        }
    }

    /**
     * 手动触发一次刷新（也可等 refreshIntervalSeconds 自动到点）。
     * 流程：触发 onRefresh → 轮询 textProvider 直到变化/超时 → 播放过渡动画。
     */
    fun refresh() {
        if (phase != QRRefreshPhase.IDLE) return
        startRefresh()
    }

    private fun startRefresh() {
        pollOldText = currentText.ifEmpty { attr.text }
        phase = QRRefreshPhase.WAITING
        event.onRefresh?.invoke()
        pollTicksLeft = (attr.refreshTimeoutMs / 100).coerceAtLeast(1)
        pollTimer?.cancel()
        pollTimer = Timer().apply {
            schedule(100, 100) {
                val latest = attr.text
                if (latest.isNotEmpty() && latest != pollOldText) {
                    cancelPoll()
                    beginTransition(latest)
                } else if (--pollTicksLeft <= 0) {
                    // 超时未变化：放弃本次刷新，重启倒计时
                    cancelPoll()
                    finishRefresh(restartCountdown = true)
                }
            }
        }
    }

    private fun beginTransition(newText: String) {
        if (attr.refreshAnimation == QRRefreshAnimation.NONE) {
            currentText = newText
            finishRefresh(restartCountdown = true)
            return
        }
        animActive = false
        nextText = newText
        phase = QRRefreshPhase.ANIMATING
        animKey += 1
        // 两步渲染：先让新画布以初始态（隐藏/下方）铺好，再激活动画
        pendingTimeouts += postDelayed(30) {
            animActive = true
        }
        pendingTimeouts += postDelayed(attr.refreshAnimationDurationMs + 120) {
            commitTransition()
        }
    }

    private fun commitTransition() {
        nextText?.let { currentText = it }
        nextText = null
        animActive = false
        phase = QRRefreshPhase.IDLE
    }

    private fun finishRefresh(restartCountdown: Boolean) {
        phase = QRRefreshPhase.IDLE
        if (restartCountdown) secondsLeft = attr.refreshIntervalSeconds
    }

    private fun cancelPoll() {
        pollTimer?.cancel()
        pollTimer = null
    }

    override fun didRemoveFromParentView() {
        super.didRemoveFromParentView()
        countdownTimer?.cancel()
        countdownTimer = null
        cancelPoll()
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

// ---------- 文件级绘制/动画辅助（Kotlin 成员扩展在非 inline DSL lambda 中不可用隐式接收者） ----------

/** 渲染一张 QR 画布，内容取自 textProvider（过渡期分别绑定 currentText/nextText） */
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

/** 旧码：动画激活后淡出/上移出 */
private fun com.tencent.kuikly.core.base.Attr.applyOutgoingAnim(ctx: QRCodeView) {
    val animating = ctx.phase == QRRefreshPhase.ANIMATING && ctx.animActive
    val key = ctx.animKey
    val dur = ctx.viewAttr.refreshAnimationDurationMs / 1000f
    when {
        !animating -> opacity(1f)
        ctx.viewAttr.refreshAnimation == QRRefreshAnimation.SLIDE_VERTICAL -> {
            transform(Translate(0f, -1f))
            animate(Animation.easeInOut(dur), key)
        }
        else -> {
            opacity(0f)
            animate(Animation.easeInOut(dur), key)
        }
    }
}

/** 新码：初始隐藏，动画激活后淡入/从下方移入 */
private fun com.tencent.kuikly.core.base.Attr.applyIncomingAnim(ctx: QRCodeView) {
    val active = ctx.animActive
    val key = ctx.animKey
    val dur = ctx.viewAttr.refreshAnimationDurationMs / 1000f
    when {
        !active -> if (ctx.viewAttr.refreshAnimation == QRRefreshAnimation.SLIDE_VERTICAL) {
            transform(Translate(0f, 1f)); opacity(1f)
        } else {
            opacity(0f)
        }
        ctx.viewAttr.refreshAnimation == QRRefreshAnimation.SLIDE_VERTICAL -> {
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

/**
 * 扩展函数：在 ViewContainer 中使用 QRCodeView
 */
fun ViewContainer<*, *>.QRCodeView(init: QRCodeView.() -> Unit) {
    addChild(QRCodeView(), init)
}
