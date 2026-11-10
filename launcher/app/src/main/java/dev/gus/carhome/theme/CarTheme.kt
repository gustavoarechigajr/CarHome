package dev.gus.carhome.theme

import android.graphics.Color

/**
 * One light or dark set of colors for a theme.
 * Gradients run top-left to bottom-right unless noted.
 */
data class Palette(
    /** Page background: 3-stop diagonal gradient. */
    val bgStops: IntArray,
    /** Quiet raised surface (empty album-art box). */
    val raised: Int,
    /** Hairlines and the progress track. */
    val track: Int,
    /** Main text and icons. */
    val ink: Int,
    /** Secondary text. */
    val muted: Int,
    /** Dock tiles: 3-stop gradient, plus how far they lift off the page. */
    val chipStops: IntArray,
    val chipElevationDp: Float,
    /** Play button: 2-stop sheen, and the icon color on top of it. */
    val playStops: IntArray,
    val onPlay: Int,
    /** Now-playing panel: the album color fades into this (alpha included). */
    val washBase: Int,
    /** How strongly the album color shows in the panel (0..1). */
    val washStrength: Float,
    /** Optional 1dp edge on dock tiles (0 = none), for dark palettes where shadows don't show. */
    val chipStroke: Int = 0,
)

/**
 * A complete look for Car Home. To add a theme, create another CarTheme
 * and add it to [Themes.all]; nothing else needs to change.
 */
data class CarTheme(
    val id: String,
    val name: String,
    /** The Android Auto tile color; its gradient is derived from it. */
    val accent: Int,
    /** Lettering and play circle on the accent tile. */
    val onAccent: Int,
    val day: Palette,
    val night: Palette,
    /** Album color to use when the track has no cover art. */
    val fallbackArt: Int,
    /** Variable font in assets/ used for all text. */
    val fontAsset: String = "fonts/Archivo.ttf",
) {
    fun palette(night: Boolean) = if (night) this.night else day
}

private fun c(hex: String) = Color.parseColor(hex)

object Themes {

    /** Steel blue on cool grey by day, deep slate at night. */
    val GLACIER = CarTheme(
        id = "glacier",
        name = "Glacier",
        accent = c("#82B1D9"),
        onAccent = c("#0C1E33"),
        day = Palette(
            bgStops = intArrayOf(c("#F0F3F7"), c("#E4E9EE"), c("#D8DFE6")),
            raised = c("#D9DFE5"),
            track = c("#C7CFD7"),
            ink = c("#101820"),
            muted = c("#56606C"),
            chipStops = intArrayOf(c("#FFFFFF"), c("#F1F4F8"), c("#D6DFE8")),
            chipElevationDp = 3f,
            playStops = intArrayOf(c("#2A3A54"), c("#0D1520")),
            onPlay = c("#E8ECF0"),
            washBase = c("#8CD9DFE5"),
            washStrength = 0.22f,
        ),
        night = Palette(
            bgStops = intArrayOf(c("#1A2532"), c("#131B24"), c("#0E151D")),
            raised = c("#1C2631"),
            track = c("#2C3946"),
            ink = c("#E8EEF3"),
            muted = c("#93A1AF"),
            chipStops = intArrayOf(c("#1D2733"), c("#161E28"), c("#111820")),
            chipElevationDp = 6f,
            playStops = intArrayOf(c("#FFFFFF"), c("#C3D2E0")),
            onPlay = c("#131B24"),
            washBase = c("#591C2631"),
            washStrength = 0.45f,
            chipStroke = c("#2A3644"),
        ),
        fallbackArt = c("#2E4A6B"),
    )

    /** Every theme the user can pick, in cycle order. */
    val all: List<CarTheme> = listOf(GLACIER)

    fun byId(id: String?): CarTheme = all.firstOrNull { it.id == id } ?: all.first()

    fun next(after: CarTheme): CarTheme = all[(all.indexOf(after) + 1) % all.size]
}
