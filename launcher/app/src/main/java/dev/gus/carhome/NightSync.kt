package dev.gus.carhome

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Keeps the unit's system-wide day/night mode in sync with the headlights (or the manual
 * Light/Dark choice). On this Topway/Dofun firmware, com.tw.service only switches Android's
 * night mode when it receives DAY_NIGHT_CHANGED, which the stock launcher normally sends.
 * Since we replace that launcher, we send it ourselves. The switch also darkens zlink/Android Auto.
 *
 * Sends when the wanted mode changes, then retries a few times if the system hasn't switched,
 * so it never fights a manual change made elsewhere for longer than that.
 */
object NightSync {
    const val ACTION = "cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED"
    private const val HEADLIGHT_NODE = "/sys/class/twctrl/panel_lights"
    private const val POLL_MS = 1500L
    const val MAX_RETRIES = 3

    private val handler = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var lastSent: Boolean? = null
    private var retries = 0
    private var warnedNoNode = false

    private val tick = object : Runnable {
        override fun run() {
            sync(force = false)
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start(context: Context) {
        app = context.applicationContext
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** true/false from the headlight input, null if the node can't be read. */
    fun headlightsOn(): Boolean? = try {
        File(HEADLIGHT_NODE).readText().trim() == "1"
    } catch (e: Exception) {
        if (!warnedNoNode) { warnedNoNode = true; Log.w("CarHome", "Can't read $HEADLIGHT_NODE: ${e.message}") }
        null
    }

    private fun desiredNight(c: Context): Boolean? = when (ThemePrefs.get(c)) {
        ThemePrefs.DARK -> true
        ThemePrefs.LIGHT -> false
        else -> headlightsOn()
    }

    fun sync(force: Boolean) {
        val c = app ?: return
        val night = desiredNight(c) ?: return
        if (force || night != lastSent) {
            lastSent = night
            retries = 0
            send(c, night)
            return
        }
        val systemNight = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night != systemNight && retries < MAX_RETRIES) {
            retries++
            send(c, night)
        }
    }

    private fun send(c: Context, night: Boolean) {
        c.sendBroadcast(Intent(ACTION)
            .putExtra("isNight", night)
            .putExtra("curThemeSupportsDayNight", true)
            .putExtra("curLaunchMode", "follow_headlamp"))
    }
}
