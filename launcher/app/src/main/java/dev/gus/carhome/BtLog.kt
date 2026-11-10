package dev.gus.carhome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * The real state of Bluetooth music on this unit, read from the vendor Bluetooth app's own log.
 * Its media sessions can't be trusted (it leaks a new one every time it opens and the old ones keep
 * stale tracks/positions), but it logs, verified on the unit:
 *   BTModel: RETURN_ID3:musicTitle:<t> musicAlbum:<a> musicArtist:<r>     (on every track change)
 *   BTModel: RETURN_MS:totalTime：<ms> currentTime:<ms>                    (every second)
 *   blink:   sbc_process_decode mute(1) ...                                 (every ~1.7 s while muted)
 *
 * Needs READ_LOGS:  adb shell pm grant dev.gus.carhome android.permission.READ_LOGS
 */
object BtLog {
    data class Track(val title: String?, val album: String?, val artist: String?)

    private val MS = Regex("""RETURN_MS:totalTime[:：]\s*(\d+)\s+currentTime[:：]\s*(\d+)""")
    private val ID3 = Regex("""RETURN_ID3:musicTitle:(.*?)\s+musicAlbum:(.*?)\s+musicArtist:(.*)$""")
    private const val MUTE_WINDOW_MS = 4000L
    private const val PLAYING_WINDOW_MS = 2600L

    @Volatile var available = false
        private set
    @Volatile var track: Track? = null
        private set
    @Volatile var durationMs = 0L
        private set
    @Volatile private var positionMs = 0L
    @Volatile private var positionAt = 0L      // elapsedRealtime of the last position line
    @Volatile private var advancedAt = 0L      // elapsedRealtime when the position last moved forward
    @Volatile private var lastMuteAt = 0L

    /** Called on the main thread when the track or the playing state changes. */
    var onChange: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var thread: Thread? = null
    private var lastPlaying = false

    fun start(ctx: Context) {
        if (thread?.isAlive == true) return
        if (ctx.checkSelfPermission("android.permission.READ_LOGS") != PackageManager.PERMISSION_GRANTED) {
            Log.i("CarHome", "BtLog off: READ_LOGS not granted")
            return
        }
        thread = Thread({
            try {
                // 1) Seed the current track from recent history (ignore old mute lines)
                read(arrayOf("logcat", "-d", "-v", "brief", "-t", "400", "BTModel:D", "*:S"), live = false)
                // 2) Then follow live; logcat can exit (buffer rotation, low memory), so restart it
                while (true) {
                    available = true
                    read(arrayOf("logcat", "-v", "brief", "-T", "1", "BTModel:D", "blink:E", "*:S"), live = true)
                    available = false
                    Log.w("CarHome", "BtLog: logcat exited, restarting")
                    Thread.sleep(2000)
                }
            } catch (e: Exception) {
                Log.w("CarHome", "BtLog stopped: ${e.message}")
            } finally {
                available = false
            }
        }, "BtLog").apply { isDaemon = true; start() }
    }

    private fun read(cmd: Array<String>, live: Boolean) {
        val p = Runtime.getRuntime().exec(cmd)
        p.inputStream.bufferedReader().forEachLine { line -> parse(line, live) }
    }

    // ---- Primary source: the BT app's own broadcast (no log access needed) ----
    // After GET_ID3_INFO, com.tw.bt broadcasts SEND_ID3_INFO about once a second with
    // musicTitle, musicArtist (String) and musicProgress, musicDuration (int, ms).
    const val ACTION_GET = "com.unisound.intent.action.GET_ID3_INFO"
    const val ACTION_SEND = "com.unisound.intent.action.SEND_ID3_INFO"
    @Volatile var broadcastSeenAt = 0L
        private set

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            onId3(i.getStringExtra("musicTitle"), i.getStringExtra("musicArtist"),
                i.getIntExtra("musicProgress", -1).toLong(), i.getIntExtra("musicDuration", -1).toLong())
        }
    }
    private val ask = object : Runnable {
        override fun run() {
            appCtx?.sendBroadcast(Intent(ACTION_GET).setPackage(Apps.PHONE))
            main.postDelayed(this, 10_000)   // re-ask in case the BT app restarted
        }
    }
    private var appCtx: Context? = null

    /** Listen for the BT app's track broadcasts. Safe to call more than once. */
    fun listen(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        appCtx!!.registerReceiver(receiver, IntentFilter(ACTION_SEND))
        main.post(ask)
    }

    /** Visible for tests. */
    fun onId3(title: String?, artist: String?, progressMs: Long, durationMs: Long, now: Long = SystemClock.elapsedRealtime()) {
        broadcastSeenAt = now
        val t = Track(title?.let { clean(it) }, track?.album, artist?.let { clean(it) })
        if (title != null && (t.title != track?.title || t.artist != track?.artist)) { track = t; notifyChange() }
        if (durationMs > 0) this.durationMs = durationMs
        if (progressMs >= 0) {
            if (progressMs > positionMs || progressMs < positionMs - 5000) advancedAt = now
            positionMs = progressMs
            positionAt = now
            if (playing(now) != lastPlaying) { lastPlaying = !lastPlaying; notifyChange() }
        }
    }

    /** Visible for tests. */
    fun parse(line: String, live: Boolean = true, now: Long = SystemClock.elapsedRealtime()) {
        ID3.find(line)?.let { m ->
            val t = Track(clean(m.groupValues[1]), clean(m.groupValues[2]), clean(m.groupValues[3]))
            if (t != track) { track = t; notifyChange() }
            return
        }
        MS.find(line)?.let { m ->
            val total = m.groupValues[1].toLong()
            val pos = m.groupValues[2].toLong()
            durationMs = total
            if (live) {
                if (pos > positionMs || pos < positionMs - 5000) advancedAt = now   // moved forward (or a new track)
                positionAt = now
            }
            positionMs = pos
            if (live && playing(now) != lastPlaying) { lastPlaying = !lastPlaying; notifyChange() }
            return
        }
        if (live && line.contains("mute(1)")) lastMuteAt = now
    }

    private fun clean(s: String) = s.trim().takeUnless { it.isEmpty() || it.equals("null", true) }

    private fun notifyChange() { main.post { onChange?.invoke() } }

    /** Phone music is advancing right now. */
    fun playing(now: Long = SystemClock.elapsedRealtime()) =
        advancedAt > 0 && now - advancedAt < PLAYING_WINDOW_MS

    /** Current position, extrapolated between the once-a-second log lines while playing. */
    fun position(now: Long = SystemClock.elapsedRealtime()): Long {
        val p = if (playing(now)) positionMs + (now - positionAt) else positionMs
        return p.coerceIn(0, durationMs.coerceAtLeast(0))
    }

    /** We've seen Bluetooth music info recently, so a phone is connected and streaming/paused. */
    fun hasInfo(now: Long = SystemClock.elapsedRealtime()) =
        track != null || (positionAt > 0 && now - positionAt < 15_000)

    /** true = unit is muting BT audio now, false = not, null = can't tell (no log access). */
    fun muted(now: Long = SystemClock.elapsedRealtime()): Boolean? =
        if (!available) null else now - lastMuteAt < MUTE_WINDOW_MS

    /** Test-only reset. */
    fun resetForTest() {
        available = true; track = null; durationMs = 0; positionMs = 0; positionAt = 0
        advancedAt = 0; lastMuteAt = 0; lastPlaying = false
    }
}
