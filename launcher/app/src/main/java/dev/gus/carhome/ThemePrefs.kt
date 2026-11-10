package dev.gus.carhome

import android.content.Context
import android.content.res.Configuration
import dev.gus.carhome.theme.CarTheme
import dev.gus.carhome.theme.Themes

/**
 * Two separate choices:
 *  - mode: AUTO follows Android's night mode (switched by the headlights via NightSync), LIGHT/DARK force it.
 *  - theme: which [CarTheme] look is used (see Themes.all).
 */
object ThemePrefs {
    const val AUTO = "auto"
    const val LIGHT = "light"
    const val DARK = "dark"

    private const val FILE = "carhome"
    private const val KEY_MODE = "theme"        // kept from v0.1 so existing choices survive
    private const val KEY_THEME = "theme_id"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(c: Context): String = prefs(c).getString(KEY_MODE, AUTO) ?: AUTO
    fun set(c: Context, mode: String) { prefs(c).edit().putString(KEY_MODE, mode).apply() }

    fun theme(c: Context): CarTheme = Themes.byId(prefs(c).getString(KEY_THEME, null))
    fun setTheme(c: Context, t: CarTheme) { prefs(c).edit().putString(KEY_THEME, t.id).apply() }

    /** Configuration override for Activity.applyOverrideConfiguration, or null for AUTO. */
    fun override(base: Context): Configuration? {
        val mode = get(base)
        if (mode == AUTO) return null
        val night = if (mode == DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val current = base.resources.configuration.uiMode
        return Configuration().apply {
            uiMode = (current and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        }
    }

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
