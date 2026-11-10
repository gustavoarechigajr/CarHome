package dev.gus.carhome

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Thin playback progress: track, gradient fill (accent → ink) and a scrubber dot. */
class ProgressLine @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var trackColor = 0
    var fillStart = 0
    var fillEnd = 0

    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 4 * d }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeWidth = 4 * d }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (16 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val r = 7 * d
        val y = height / 2f
        val left = r
        val right = width - r
        val x = left + (right - left) * fraction
        track.color = trackColor
        canvas.drawLine(left, y, right, y, track)
        if (fraction > 0f) {
            fill.shader = LinearGradient(left, 0f, right, 0f, fillStart, fillEnd, Shader.TileMode.CLAMP)
            canvas.drawLine(left, y, x, y, fill)
        }
        dot.color = fillEnd
        canvas.drawCircle(x, y, r, dot)
    }
}
