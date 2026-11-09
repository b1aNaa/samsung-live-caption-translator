package com.livesub.translate

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.livesub.translate.Prefs.bgAlpha
import com.livesub.translate.Prefs.fontSp
import com.livesub.translate.Prefs.ovX
import com.livesub.translate.Prefs.ovY
import com.livesub.translate.Prefs.showSrc
import kotlin.math.abs

/** Floating overlay that shows the Chinese translation. Drag to move. */
@SuppressLint("ClickableViewAccessibility")
class OverlayController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val srcView: TextView
    private val textView: TextView
    private val root: LinearLayout
    private val params: WindowManager.LayoutParams
    private val bg: GradientDrawable
    private var attached = false

    init {
        val dm = ctx.resources.displayMetrics
        val maxW = (dm.widthPixels * 0.9f).toInt()
        srcView = TextView(ctx).apply {
            setTextColor(Color.argb(190, 200, 200, 200))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setLineSpacing(0f, 1.1f)
            this.maxWidth = maxW
            setPadding(dp(12), dp(8), dp(12), 0)
        }
        textView = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.15f)
            this.maxWidth = maxW
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        bg = GradientDrawable().apply { cornerRadius = dp(10).toFloat() }
        root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(srcView)
            addView(textView)
            background = bg
        }
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ctx.ovX.takeIf { it >= 0 } ?: dp(12)
            y = ctx.ovY.takeIf { it >= 0 } ?: (dm.heightPixels * 0.68f).toInt()
        }
        applyStyle()
        attachDrag()
    }

    private fun dp(v: Int) =
        (v * ctx.resources.displayMetrics.density).toInt()

    /** Re-read prefs so font/alpha changes apply while running. */
    private fun applyStyle() {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ctx.fontSp)
        bg.setColor(Color.argb(ctx.bgAlpha, 0, 0, 0))
    }

    private fun attachDrag() {
        val dm = ctx.resources.displayMetrics
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        root.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (dm.widthPixels - root.width).coerceAtLeast(0)
                    val maxY = (dm.heightPixels - root.height).coerceAtLeast(0)
                    params.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, maxX)
                    params.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, maxY)
                    ctx.ovX = params.x
                    ctx.ovY = params.y
                    if (attached) wm.updateViewLayout(root, params)
                    true
                }
                else -> abs(e.rawX - downX) > 8 || abs(e.rawY - downY) > 8
            }
        }
    }

    fun show(src: String, text: String) {
        mainHandler.post {
            applyStyle()
            if (!attached) {
                try { wm.addView(root, params) } catch (_: Exception) { return@post }
                attached = true
            }
            srcView.visibility = if (ctx.showSrc) View.VISIBLE else View.GONE
            srcView.text = src
            textView.text = text
        }
    }

    fun show(text: String) = show("", text)

    fun showError(msg: String) {
        show("", "⚠ $msg")
    }

    fun destroy() {
        mainHandler.post {
            if (attached) {
                try { wm.removeView(root) } catch (_: Exception) {}
                attached = false
            }
        }
    }
}
