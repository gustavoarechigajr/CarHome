package dev.gus.carhome

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Topway units route one audio source at a time through the MCU; Bluetooth audio stays muted until
 * the Bluetooth source (0x08) is requested. The vendor BT app (com.tw.bt) listens for these broadcasts
 * and does the switch itself, with no screen shown (verified on the unit, 2026-11-10):
 *   net.easyconn.a2dp.acquire  → Bluetooth audio on   (blink "mute(1)" lines stop)
 *   net.easyconn.a2dp.release  → Bluetooth audio off  (muted again)
 */
object BtSource {
    const val ACTION_ACQUIRE = "net.easyconn.a2dp.acquire"
    const val ACTION_RELEASE = "net.easyconn.a2dp.release"

    /** We asked for the Bluetooth source and nothing has taken it since (as far as we know). */
    @Volatile var active = false
        private set

    /** Kept for compatibility: the switch is instant now, so Home is never paused for it. */
    val switching: Boolean get() = false

    /** Something else (Android Auto, USB music, radio) may take the source. */
    fun lost() { active = false }

    /** Ask the unit to route Bluetooth audio. Instant, invisible, safe to repeat. */
    fun ensure(ctx: Context, onDone: () -> Unit = {}) {
        ctx.sendBroadcast(Intent(ACTION_ACQUIRE).setPackage(Apps.PHONE))
        active = true
        onDone()
    }

    /** Overload kept so Activity callers compile unchanged. */
    fun ensure(activity: Activity, onDone: () -> Unit = {}) = ensure(activity as Context, onDone)

    fun homeResumed() {}
}
