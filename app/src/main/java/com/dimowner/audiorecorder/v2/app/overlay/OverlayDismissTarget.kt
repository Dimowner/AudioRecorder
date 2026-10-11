package com.dimowner.audiorecorder.v2.app.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.dimowner.audiorecorder.R

/** A small, non-intercepting window: the recorder bubble keeps ownership of the entire drag. */
internal class OverlayDismissTarget(
    private val context: Context,
    private val windowManager: WindowManager,
) {
    private var window: FrameLayout? = null
    private var target: ImageView? = null
    private var highlighted = false
    private val bubbleLocation = IntArray(2)
    private val targetLocation = IntArray(2)

    fun show() {
        if (window != null) return
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_round_close)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            contentDescription = context.getString(R.string.btn_dismiss)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(198, 40, 40))
            }
        }
        // Leave room for the highlight's scale animation without enlarging the input-blocking area.
        val container = FrameLayout(context).apply {
            addView(icon, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        }
        val params = WindowManager.LayoutParams(
            dp(96), dp(96), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Without LAYOUT_NO_LIMITS / LAYOUT_IN_SCREEN, Android fits this window above system bars.
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(16)
        }
        windowManager.addView(container, params)
        target = icon
        window = container
    }

    fun contains(bubble: View): Boolean {
        val icon = target ?: return false
        val container = window ?: return false
        if (!icon.isLaidOut || !bubble.isLaidOut) return false
        // Both locations use physical screen coordinates, independent of status bars or gravity.
        bubble.getLocationOnScreen(bubbleLocation)
        // The container does not scale, so entering the target cannot move its own hit area.
        container.getLocationOnScreen(targetLocation)
        return overlaps(
            bubbleLocation[0] + bubble.width / 2f, bubbleLocation[1] + bubble.height / 2f,
            minOf(bubble.width, bubble.height) / 2f,
            targetLocation[0] + container.width / 2f, targetLocation[1] + container.height / 2f,
            minOf(icon.width, icon.height) / 2f,
        )
    }

    fun update(bubble: View) {
        val hit = contains(bubble)
        if (hit == highlighted) return
        highlighted = hit
        target?.animate()?.scaleX(if (hit) 1.15f else 1f)?.scaleY(if (hit) 1.15f else 1f)
            ?.setDuration(100L)?.start()
        if (hit) bubble.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun hide() {
        target?.animate()?.cancel()
        window?.let { windowManager.removeView(it) }
        window = null
        target = null
        highlighted = false
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        internal fun overlaps(
            bubbleX: Float, bubbleY: Float, bubbleRadius: Float,
            targetX: Float, targetY: Float, targetRadius: Float,
        ): Boolean {
            val dx = bubbleX - targetX
            val dy = bubbleY - targetY
            val radius = bubbleRadius + targetRadius
            return dx * dx + dy * dy <= radius * radius
        }
    }
}
