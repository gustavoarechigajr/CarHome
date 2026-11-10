package dev.gus.carhome.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/** Small helpers that turn a [Palette] into drawables, colors and fonts. */
object ThemeKit {

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    /** Blend a toward b by t (0..1). */
    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun linear(stops: IntArray, radiusPx: Float = 0f,
               orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR) =
        GradientDrawable(orientation, stops).apply { cornerRadius = radiusPx }

    fun ovalLinear(stops: IntArray) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, stops).apply { shape = GradientDrawable.OVAL }

    fun oval(color: Int, strokePx: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

    /** Radial gradient with its center given as fractions of the view (0..1). */
    fun radial(colors: IntArray, radiusPx: Float, cx: Float, cy: Float, cornerPx: Float) =
        GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            this.colors = colors
            gradientRadius = radiusPx
            setGradientCenter(cx, cy)
            cornerRadius = cornerPx
        }

    /** The Android Auto tile: highlight top-left, accent, deeper toward bottom-right. */
    fun accentTile(theme: CarTheme, radiusPx: Float, w: Int, h: Int): GradientDrawable =
        radial(
            intArrayOf(mix(theme.accent, Color.WHITE, 0.38f), theme.accent, mix(theme.accent, theme.onAccent, 0.28f)),
            radiusPx = 1.15f * maxOf(w, h).coerceAtLeast(1), cx = 0.12f, cy = 0.08f, cornerPx = radiusPx,
        )

    /**
     * Bounded touch ripple on top of any background. Without a mask the ripple is clipped to the
     * content's alpha, so a transparent content needs an explicit [mask] (e.g. an opaque oval).
     */
    fun pressable(content: Drawable, ink: Int, mask: Drawable? = null): Drawable =
        RippleDrawable(ColorStateList.valueOf(withAlpha(ink, 0.14f)), content, mask)

    private val fontCache = HashMap<String, Typeface>()

    /** Variable-font instance: width 62..125 (125 = expanded), weight 100..900. */
    fun font(ctx: Context, asset: String, width: Int, weight: Int): Typeface {
        val key = "$asset/$width/$weight"
        return fontCache.getOrPut(key) {
            try {
                Typeface.Builder(ctx.assets, asset)
                    .setFontVariationSettings("'wdth' $width, 'wght' $weight")
                    .build() ?: Typeface.DEFAULT
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
    }
}
