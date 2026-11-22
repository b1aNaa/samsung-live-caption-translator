package com.livesub.translate.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.livesub.translate.pipeline.DisplayState
import com.livesub.translate.settings.AppSettings
import com.livesub.translate.settings.SettingsStore
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The floating subtitle window.
 *
 * Uses `TYPE_ACCESSIBILITY_OVERLAY`, which an accessibility service may show
 * without the "display over other apps" permission. Position is stored as a
 * fraction of the screen so it survives rotation, and the window is centred
 * horizontally so text grows evenly in both directions like real subtitles.
 *
 * All methods must be called on the main thread.
 */
@SuppressLint("ClickableViewAccessibility")
class OverlayController(
    private val service: AccessibilityService,
    private val store: SettingsStore,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private val background = GradientDrawable().apply {
        cornerRadius = dp(16f)
        // A faint light edge keeps the box crisp over dark scenes.
        setStroke(dp(1f).roundToInt().coerceAtLeast(1), Color.argb(28, 255, 255, 255))
    }
    private val textView = TextView(service).apply {
        setTextColor(Color.WHITE)
        setLineSpacing(0f, 1.15f)
        letterSpacing = 0.02f
        setShadowLayer(dp(2f), 0f, dp(1f), Color.argb(200, 0, 0, 0))
        gravity = Gravity.CENTER_HORIZONTAL
        val h = dp(18f).roundToInt()
        val v = dp(10f).roundToInt()
        setPadding(h, v, h, v)
        // Like Live Caption: a fixed number of lines, older text scrolls off the top.
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(16f).roundToInt())
    }
    private val root = FrameLayout(service).apply {
        background = this@OverlayController.background
        addView(textView)
        visibility = View.GONE
    }
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
    }

    private var attached = false
    private var settings: AppSettings = store.current
    private var state = DisplayState()
    private var status: String? = null
    private var dragging = false

    init {
        attachDrag()
        applySettings(store.current)
    }

    fun attach() {
        if (attached) return
        layoutForScreen()
        try {
            wm.addView(root, params)
            attached = true
        } catch (_: Exception) {
            // Service unbound mid-call — nothing to show on.
        }
    }

    fun detach() {
        if (!attached) return
        try { wm.removeView(root) } catch (_: Exception) {}
        attached = false
        state = DisplayState()
        status = null
        root.visibility = View.GONE
    }

    /** Placeholder shown until the first captions arrive; null clears it. */
    fun setStatus(text: String?) {
        status = text
        redraw()
    }

    fun render(newState: DisplayState) {
        state = newState
        if (newState.lines.isNotEmpty()) status = null
        redraw()
    }

    fun applySettings(s: AppSettings) {
        val old = settings
        settings = s
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.fontSp)
        textView.maxLines = s.displayCount * if (s.showSource) 2 else 1
        background.setColor(Color.argb(s.bgAlpha, 0, 0, 0))
        if (!dragging && (old.overlayX != s.overlayX || old.overlayY != s.overlayY)) layoutForScreen()
        val touchFlags = if (s.overlayLocked) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (touchFlags != params.flags) {
            params.flags = touchFlags
            if (attached) wm.updateViewLayout(root, params)
        }
        redraw()
    }

    fun onConfigurationChanged() = layoutForScreen()

    private fun screen(): Rect = try {
        wm.currentWindowMetrics.bounds
    } catch (_: Exception) {
        val dm = service.resources.displayMetrics
        Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun layoutForScreen() {
        val b = screen()
        val landscape = b.width() > b.height()
        textView.maxWidth = (b.width() * if (landscape) 0.72f else 0.94f).roundToInt()
        val fx = settings.overlayX.takeUnless { it.isNaN() } ?: 0f
        val fy = settings.overlayY.takeUnless { it.isNaN() } ?: DEFAULT_Y
        params.x = (fx * b.width()).roundToInt()
        params.y = (fy * b.height()).roundToInt()
        clamp(b)
        if (attached) wm.updateViewLayout(root, params)
    }

    private fun clamp(b: Rect) {
        val w = root.width.takeIf { it > 0 } ?: 0
        val h = root.height.takeIf { it > 0 } ?: dp(64f).roundToInt()
        val maxX = ((b.width() - w) / 2).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxX, maxX)
        params.y = params.y.coerceIn(0, (b.height() - h).coerceAtLeast(0))
    }

    private fun redraw() {
        val text = buildText()
        if (text.isEmpty()) {
            root.visibility = View.GONE
            return
        }
        textView.text = text
        root.visibility = View.VISIBLE
        scrollToTailBeforeDraw()
    }

    /** Once the new text is laid out, scroll so its last lines are the ones visible. */
    private fun scrollToTailBeforeDraw() {
        textView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                textView.viewTreeObserver.removeOnPreDrawListener(this)
                val layout = textView.layout ?: return true
                val hidden = layout.lineCount - textView.maxLines
                val target = if (hidden > 0) layout.getLineTop(hidden) else 0
                if (textView.scrollY != target) textView.scrollTo(0, target)
                return true
            }
        })
    }

    private fun buildText(): CharSequence {
        val sb = SpannableStringBuilder()
        for (line in state.lines) {
            if (sb.isNotEmpty()) sb.append('\n')
            if (settings.showSource) {
                sb.appendStyled(line.source, SOURCE_COLOR, SOURCE_SCALE)
                if (line.translation != null) sb.append('\n')
            }
            when {
                line.translation != null ->
                    sb.appendStyled(line.translation, if (line.live) LIVE_COLOR else Color.WHITE, 1f)
                !settings.showSource -> sb.appendStyled("…", SOURCE_COLOR, 1f)
            }
        }
        val note = state.error?.let { "⚠ $it" } ?: status.takeIf { state.lines.isEmpty() }
        if (note != null) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.appendStyled(note, if (state.error != null) ERROR_COLOR else SOURCE_COLOR, NOTE_SCALE)
        }
        return sb
    }

    private fun SpannableStringBuilder.appendStyled(s: String, color: Int, scale: Float) {
        val start = length
        append(s)
        setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (scale != 1f) setSpan(RelativeSizeSpan(scale), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun attachDrag() {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        clamp(screen())
                        if (attached) wm.updateViewLayout(root, params)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val b = screen()
                        val fx = params.x.toFloat() / b.width()
                        val fy = params.y.toFloat() / b.height()
                        settings = settings.copy(overlayX = fx, overlayY = fy)
                        store.update { it.copy(overlayX = fx, overlayY = fy) }
                    }
                    dragging = false
                }
            }
            true
        }
    }

    private fun dp(v: Float) = v * density

    private companion object {
        const val DEFAULT_Y = 0.70f
        const val SOURCE_SCALE = 0.72f
        const val NOTE_SCALE = 0.78f
        val SOURCE_COLOR = Color.argb(200, 205, 210, 220)
        /** Dimmer: the sentence still being spoken, whose translation may change. */
        val LIVE_COLOR = Color.argb(200, 255, 255, 255)
        val ERROR_COLOR = Color.rgb(255, 196, 92)
    }
}
