package dev.gus.carhome

import android.content.Context
import android.content.Intent
import android.view.KeyEvent

/**
 * Bluetooth music buttons on this Topway unit. The vendor BT player ignores keys sent the standard
 * MediaSession/AudioManager way; the system service com.tw.service turns this broadcast into the same
 * virtual key the steering wheel sends and routes it to the current source (verified on the unit:
 * type=pp paused the phone). "pp" toggles play/pause; next/prev skip.
 */
object BtControl {
    const val ACTION = "com.unisound.unicar.broadcast.action"

    /** The broadcast type for a media key code, or null if it has none. */
    fun typeFor(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "pp"
        KeyEvent.KEYCODE_MEDIA_NEXT -> "next"
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "prev"
        else -> null
    }

    /** Returns false if [keyCode] has no broadcast equivalent. */
    fun send(ctx: Context, keyCode: Int): Boolean {
        val type = typeFor(keyCode) ?: return false
        ctx.sendBroadcast(Intent(ACTION).putExtra("type", type))
        return true
    }
}
