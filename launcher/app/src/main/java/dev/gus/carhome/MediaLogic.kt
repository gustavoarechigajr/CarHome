package dev.gus.carhome

/**
 * Every decision Car Home makes about media, with no Android calls, so it can be tested
 * exhaustively on a PC. HomeActivity turns the returned decisions into real calls.
 *
 * Platform facts (verified on the Topway unit):
 *  - The phone's Bluetooth music appears as sessions from com.tw.bt (several at once), and they
 *    declare no transport actions (actions = 0): only media KEY events work on them.
 *  - The unit's MCU keeps Bluetooth audio muted until com.tw.bt's music tab has focus
 *    ("switching the source"), and we can't read back the current source.
 */
object MediaLogic {

    /** What Car Home knows about one media session. [actions] = PlaybackState.getActions(). */
    data class Session(
        val pkg: String,
        val playing: Boolean,
        val hasMetadata: Boolean,
        val actions: Long,
        /** PlaybackState.getLastPositionUpdateTime(): when this session last reported anything. */
        val updated: Long = 0,
    )

    // PlaybackState action bits (copied so this file has no Android dependency)
    const val ACTION_PAUSE = 1L shl 1
    const val ACTION_PLAY = 1L shl 2
    const val ACTION_SKIP_TO_PREVIOUS = 1L shl 4
    const val ACTION_SKIP_TO_NEXT = 1L shl 5

    /**
     * [btMuted]: true = the unit's log shows Bluetooth audio muted right now, false = it isn't,
     * null = unknown (no log access): then fall back to our own record of switching the source.
     */
    fun onPlayPressed(s: Session?, btSourceActive: Boolean, btMuted: Boolean?): Play =
        onPlayPressed(s, btMuted?.let { !it } ?: btSourceActive)

    /**
     * Bring Bluetooth audio back by itself? Only when the phone is actually playing AND the unit's log
     * shows it muting that audio (e.g. after the car's USB player or radio took the source).
     * Unknown mute state (no log access) never triggers it.
     */
    fun shouldReacquireBt(phonePlaying: Boolean, btMuted: Boolean?): Boolean = phonePlaying && btMuted == true

    enum class Play {
        /** Phone is streaming but the unit hasn't routed Bluetooth: switch the source, don't pause. */
        SWITCH_TO_BT,
        PAUSE_TRANSPORT,
        PAUSE_KEY,
        /** Bluetooth (or nothing yet): send PLAY key, then make sure the unit routes Bluetooth. */
        PLAY_KEY_AND_SWITCH_TO_BT,
        PLAY_TRANSPORT,
        PLAY_KEY,
    }

    fun onPlayPressed(s: Session?, btSourceActive: Boolean): Play = when {
        s != null && s.playing && s.pkg == Apps.PHONE && !btSourceActive -> Play.SWITCH_TO_BT
        s != null && s.playing -> if (s.actions and ACTION_PAUSE != 0L) Play.PAUSE_TRANSPORT else Play.PAUSE_KEY
        s == null || s.pkg == Apps.PHONE -> Play.PLAY_KEY_AND_SWITCH_TO_BT
        s.actions and ACTION_PLAY != 0L -> Play.PLAY_TRANSPORT
        else -> Play.PLAY_KEY
    }

    /** true = use the session's transport control; false = send the media key. */
    fun skipUsesTransport(s: Session?, next: Boolean): Boolean {
        val bit = if (next) ACTION_SKIP_TO_NEXT else ACTION_SKIP_TO_PREVIOUS
        return s != null && (s.actions and bit) != 0L
    }

    /**
     * Which session the Now-playing card follows: whatever is playing; else the phone's
     * Bluetooth session that has track info; else any Bluetooth session; else the first. -1 if none.
     */
    fun pick(all: List<Session>): Int {
        // com.tw.bt creates a new session each time it opens and never releases the old ones; stale ones
        // keep old tracks and stay "playing". The system lists the live one first (verified on the unit).
        val freshestBt = all.indexOfFirst { it.pkg == Apps.PHONE }.takeIf { it >= 0 }
        val sessions = all.mapIndexed { i, s -> if (s.pkg == Apps.PHONE && i != freshestBt) null else s }
        val playing = sessions.indexOfFirst { it?.playing == true }
        if (playing >= 0) return playing
        if (freshestBt != null) return freshestBt
        return sessions.indexOfFirst { it != null }
    }

    fun phonePresent(sessions: List<Session>) = sessions.any { it.pkg == Apps.PHONE }

    /** Current playback position, extrapolated while playing, clamped to the track. */
    fun position(reported: Long, lastUpdate: Long, now: Long, speed: Float, playing: Boolean, duration: Long): Long {
        var p = reported
        // Bluetooth (AVRCP) bridges often report speed 0 while PLAYING; treat that as normal speed
        val sp = if (playing && speed == 0f) 1f else speed
        if (playing && lastUpdate > 0) p += ((now - lastUpdate) * sp).toLong()
        return p.coerceIn(0, duration.coerceAtLeast(0))
    }

    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
        else "%d:%02d".format(s / 60, s % 60)
    }

    /** Title/artist lines for the card. [label] = app name of the session's package. */
    /** com.tw.bt fills missing track info with placeholder words instead of leaving it empty. */
    private val PLACEHOLDERS = setOf("not provided", "unknown", "<unknown>", "null")
    private fun real(s: String?): String? = s?.trim()?.takeUnless { it.isEmpty() || it.lowercase() in PLACEHOLDERS }

    fun lines(hasSession: Boolean, rawTitle: String?, rawArtist: String?, playing: Boolean, label: String): Pair<String, String> {
        val title = real(rawTitle)
        val artist = real(rawArtist)
        return linesClean(hasSession, title, artist, playing, label)
    }

    private fun linesClean(hasSession: Boolean, title: String?, artist: String?, playing: Boolean, label: String): Pair<String, String> = when {
        !hasSession -> "Nothing playing" to "Play music from your phone or the radio"
        title.isNullOrBlank() -> (if (playing) "Playing" else "Nothing playing") to "$label is ready"
        artist.isNullOrBlank() -> title to label
        else -> title to "$artist, from $label"
    }
}
