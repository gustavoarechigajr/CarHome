package dev.gus.carhome

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Faint concentric rings radiating from the tile's play button, with one ring
 * slowly pulsing outward while Car Home waits for the phone.
 * The center sits [centerRightDp]/[centerBottomDp] in from the bottom-right corner.
 */
class RingsView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var color: Int = Color.BLACK
        set(v) { field = v; invalidate() }
    var centerRightDp = 72f
    var centerBottomDp = 68f

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val radiiDp = floatArrayOf(80f, 140f, 200f, 260f)
    private val alphas = floatArrayOf(0.20f, 0.14f, 0.10f, 0.06f)
    private var pulse = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2800
        repeatCount = ValueAnimator.INFINITE
        interpolator = DecelerateInterpolator()
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    /** Respects the system animation setting: if animations are off, rings stay still. */
    fun start() { if (ValueAnimator.areAnimatorsEnabled() && !animator.isStarted) animator.start() }
    fun stop() { animator.cancel(); pulse = 0f; invalidate() }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val cx = width - centerRightDp * density
        val cy = height - centerBottomDp * density
        for (i in radiiDp.indices) {
            paint.color = color
            paint.alpha = (alphas[i] * 255).toInt()
            canvas.drawCircle(cx, cy, radiiDp[i] * density, paint)
        }
        if (animator.isRunning) {
            paint.alpha = (0.30f * (1f - pulse) * 255).toInt()
            canvas.drawCircle(cx, cy, radiiDp[0] * density * (1f + 2.4f * pulse), paint)
        }
    }
}
