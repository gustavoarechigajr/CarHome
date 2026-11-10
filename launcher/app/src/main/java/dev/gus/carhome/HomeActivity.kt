package dev.gus.carhome

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import dev.gus.carhome.theme.CarTheme
import dev.gus.carhome.theme.Palette
import dev.gus.carhome.theme.ThemeKit
import dev.gus.carhome.theme.Themes

class HomeActivity : Activity() {

    companion object {
        /** Test hook: called with (keyCode, target package or null for the system) for every media key sent. */
        @JvmStatic var onMediaKey: ((Int, String?) -> Unit)? = null
    }

    private lateinit var theme: CarTheme
    private lateinit var pal: Palette
    private var night = false

    private lateinit var rings: RingsView
    private lateinit var aaStatus: TextView
    private lateinit var nowPanel: View
    private lateinit var track: TextView
    private lateinit var artist: TextView
    private lateinit var art: ImageView
    private lateinit var progressBox: View
    private lateinit var progress: ProgressLine
    private lateinit var pos: TextView
    private lateinit var dur: TextView
    private lateinit var play: ImageButton
    private lateinit var phoneDot: View

    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var needsMediaAccess = false
    private var washColor = 0
    private var lastArt: Bitmap? = null
    private var artDrawn = false   // so the placeholder is drawn on the first render too
    private var openingOwnScreen = false   // our own app drawer doesn't change the audio source
    private var btWhenLeft = false         // phone was PLAYING on Bluetooth when Home was left

    private val listenerComponent by lazy { ComponentName(this, MediaListener::class.java) }
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pickController(it) }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = renderMedia()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSessions()
        override fun onSessionDestroyed() = refreshSessions()
    }

    private var tick = 0
    private val ticker = object : Runnable {
        override fun run() {
            // Re-pick every 3 s: com.tw.bt doesn't always announce which of its sessions is live
            if (++tick % 3 == 0) {
                refreshSessions()
                // Phone playing but the car muting it (another source took over): switch back to Bluetooth
                if (TwSource.current < 0 && MediaLogic.shouldReacquireBt(BtLog.playing(), BtLog.muted())) {
                    android.util.Log.i("CarHome", "phone playing but muted: re-acquiring Bluetooth audio")
                    BtSource.ensure(this@HomeActivity)
                }
            } else renderProgress()
            handler.postDelayed(this, 1000)
        }
    }

    // ---- mode override (Light/Dark forced; Auto = follow headlights via system night mode)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        ThemePrefs.override(newBase)?.let { applyOverrideConfiguration(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        BtLog.onChange = { renderMedia() }

        theme = ThemePrefs.theme(this)
        night = ThemePrefs.isNight(this)
        pal = theme.palette(night)
        washColor = theme.fallbackArt

        rings = findViewById(R.id.rings)
        aaStatus = findViewById(R.id.aa_status)
        nowPanel = findViewById(R.id.now_panel)
        track = findViewById(R.id.track)
        artist = findViewById(R.id.artist)
        art = findViewById(R.id.art)
        progressBox = findViewById(R.id.progress_box)
        progress = findViewById(R.id.progress)
        pos = findViewById(R.id.pos)
        dur = findViewById(R.id.dur)
        play = findViewById(R.id.play)
        phoneDot = findViewById(R.id.phone_dot)

        applyTheme()

        findViewById<View>(R.id.aa_tile).setOnClickListener {
            BtSource.lost()   // Android Auto takes over the audio source
            launchPackage(Apps.ZLINK, Apps.ZLINK_MAIN)
        }
        // Music = the phone's music over Bluetooth. Route BT audio first, then open the BT app.
        // (The car's own USB/SD player, com.tw.music, takes the audio source away from Bluetooth;
        // it's still reachable from Apps.)
        findViewById<View>(R.id.dock_music).setOnClickListener {
            BtSource.ensure(this)
            launchPackage(Apps.PHONE)
        }
        findViewById<View>(R.id.dock_phone).setOnClickListener {
            BtSource.lost()   // the BT app may leave its music tab; re-check before the next Play
            launchPackage(Apps.PHONE)
        }
        findViewById<View>(R.id.dock_apps).setOnClickListener {
            openingOwnScreen = true
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<View>(R.id.dock_settings).setOnClickListener { launchPackage(Apps.CAR_SETTINGS) }
        findViewById<View>(R.id.dock_settings).setOnLongClickListener {
            safeStart(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        }
        // Tap: Auto → Light → Dark. Long-press: next theme (Themes.all).
        findViewById<View>(R.id.dock_theme).setOnClickListener { cycleMode() }
        findViewById<View>(R.id.dock_theme).setOnLongClickListener { cycleTheme(); true }

        // Topway's BT player declares no transport actions (actions=0) and only reacts to media
        // KEYS, so use a session's transport controls only when it says it supports them.
        findViewById<View>(R.id.prev).setOnClickListener {
            val c = controller
            if (MediaLogic.skipUsesTransport(c?.let { view(it) }, next = false)) c!!.transportControls.skipToPrevious()
            else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        findViewById<View>(R.id.next).setOnClickListener {
            val c = controller
            if (MediaLogic.skipUsesTransport(c?.let { view(it) }, next = true)) c!!.transportControls.skipToNext()
            else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        play.setOnClickListener { onPlayPressed() }
        findViewById<View>(R.id.now_row).setOnClickListener {
            if (needsMediaAccess) {
                if (!safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
                    Toast.makeText(this, "Grant with: adb shell cmd notification allow_listener dev.gus.carhome/dev.gus.carhome.MediaListener", Toast.LENGTH_LONG).show()
                }
            } else {
                controller?.packageName?.let { launchPackage(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        openingOwnScreen = false
        BtSource.homeResumed()
        BtLog.start(this)         // no-op if already running or not permitted
        TwSource.request()        // re-sync the audio source whenever Home comes back
        // Bluetooth was playing when you left and another app took the source: take it back now
        TwSource.onChange = { new, _ -> if (new != TwSource.BLUETOOTH && new >= 0 && btWhenLeft) restoreBt("source changed to $new") }
        handler.postDelayed({
            if (btWhenLeft && TwSource.current >= 0 && TwSource.current != TwSource.BLUETOOTH) restoreBt("back on Home, source ${TwSource.current}")
        }, 600)
        refreshSessions()
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, handler)
        } catch (e: SecurityException) {
            needsMediaAccess = true
        }
        handler.post(ticker)
        rings.start()
    }

    override fun onPause() {
        super.onPause()
        // Leaving Home for any other app (radio, USB music, dialer, maps...) may change the unit's
        // audio source, which we can't read back. Only our own Bluetooth switch keeps it.
        btWhenLeft = (TwSource.current == TwSource.BLUETOOTH || BtSource.active) && BtLog.playing()
        TwSource.onChange = null
        // (A relaunch for a day/night or theme change, and our own app drawer, don't count.)
        if (!BtSource.switching && !isChangingConfigurations && !openingOwnScreen) BtSource.lost()
        handler.removeCallbacks(ticker)
        rings.stop()
        try { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        BtLog.onChange = null
        controller?.unregisterCallback(mediaCallback)
        super.onDestroy()
    }

    /**
     * Headlights / Light / Dark changes arrive here (configChanges="uiMode") instead of relaunching
     * Home, so there's no flash and the Bluetooth state survives.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val nowNight = ThemePrefs.isNight(this)
        if (nowNight == night) return
        night = nowNight
        pal = theme.palette(night)
        applyTheme()
        artDrawn = false
        renderMedia()
    }

    private fun safeStart(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (e: Exception) {
        Toast.makeText(this, "This screen isn't available on this unit", Toast.LENGTH_SHORT).show(); false
    }

    /** Switch back to Bluetooth ONCE per return; com.tw.bt's toggle then resumes the phone it paused. */
    private fun restoreBt(why: String) {
        if (!btWhenLeft) return
        btWhenLeft = false
        android.util.Log.i("CarHome", "restoring Bluetooth audio: $why")
        BtSource.ensure(this)
    }

    /** A launcher swallows Back. */
    override fun onBackPressed() {}

    // ======================= theme =======================

    private fun px(dp: Float) = ThemeKit.dp(this, dp)

    private fun font(width: Int, weight: Int) = ThemeKit.font(this, theme.fontAsset, width, weight)

    /** Paint every surface, text and icon from the active theme + day/night palette. */
    private fun applyTheme() {
        val root = findViewById<View>(R.id.root)
        root.background = ThemeKit.linear(pal.bgStops)
        window.statusBarColor = pal.bgStops[0]
        window.navigationBarColor = pal.bgStops[2]
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (night) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val regular = font(100, 400)
        val medium = font(100, 500)
        val semibold = font(100, 600)
        val bold = font(100, 700)

        // --- Android Auto tile
        val tile = findViewById<View>(R.id.aa_tile)
        val tileRadius = px(32f)
        tile.clipToOutline = true
        tile.outlineProvider = roundRect(tileRadius)
        tile.background = ThemeKit.pressable(ThemeKit.linear(intArrayOf(theme.accent, theme.accent), tileRadius), theme.onAccent)
        tile.post {
            tile.background = ThemeKit.pressable(ThemeKit.accentTile(theme, tileRadius, tile.width, tile.height), theme.onAccent)
        }
        rings.color = theme.onAccent
        findViewById<ImageView>(R.id.aa_icon).imageTintList = ColorStateList.valueOf(theme.onAccent)
        findViewById<TextView>(R.id.aa_title).apply { typeface = font(125, 800); setTextColor(theme.onAccent) }
        aaStatus.apply { typeface = bold; setTextColor(theme.onAccent) }
        findViewById<TextView>(R.id.aa_hint).apply { typeface = medium; setTextColor(ThemeKit.withAlpha(theme.onAccent, 0.85f)) }
        findViewById<View>(R.id.aa_play).background = ThemeKit.oval(theme.onAccent)
        findViewById<ImageView>(R.id.aa_play_icon).imageTintList = ColorStateList.valueOf(theme.accent)

        // --- clock + now playing
        findViewById<TextView>(R.id.clock).apply { typeface = font(125, 700); setTextColor(pal.ink) }
        findViewById<TextView>(R.id.date).apply { typeface = medium; setTextColor(pal.muted) }
        track.apply { typeface = bold; setTextColor(pal.ink) }
        artist.apply { typeface = regular; setTextColor(pal.muted) }
        pos.apply { typeface = regular; setTextColor(pal.muted) }
        dur.apply { typeface = regular; setTextColor(pal.muted) }
        progress.trackColor = pal.track
        progress.fillStart = theme.accent
        progress.fillEnd = pal.ink

        art.clipToOutline = true
        art.outlineProvider = roundRect(px(20f))
        art.elevation = px(if (night) 10f else 6f)

        listOf(R.id.prev, R.id.next).forEach { id ->
            findViewById<ImageButton>(id).apply {
                background = ThemeKit.pressable(ThemeKit.oval(Color.TRANSPARENT), pal.ink, mask = ThemeKit.oval(Color.WHITE))
                imageTintList = ColorStateList.valueOf(pal.ink)
            }
        }
        findViewById<View>(R.id.now_row).background = ThemeKit.pressable(
            ThemeKit.linear(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), px(20f)), pal.ink,
            mask = ThemeKit.linear(intArrayOf(Color.WHITE, Color.WHITE), px(20f)))
        play.background = ThemeKit.pressable(ThemeKit.ovalLinear(pal.playStops), pal.onPlay)
        play.imageTintList = ColorStateList.valueOf(pal.onPlay)
        updateWash()

        // --- dock
        findViewById<View>(R.id.dock_rule).setBackgroundColor(pal.track)
        val tiles = listOf(
            Triple(R.id.dock_music, R.id.dock_music_icon, R.id.dock_music_label),
            Triple(R.id.dock_phone, R.id.dock_phone_icon, R.id.dock_phone_label),
            Triple(R.id.dock_apps, R.id.dock_apps_icon, R.id.dock_apps_label),
            Triple(R.id.dock_settings, R.id.dock_settings_icon, R.id.dock_settings_label),
            Triple(R.id.dock_theme, R.id.dock_theme_icon, R.id.dock_theme_label),
        )
        tiles.forEach { (tileId, iconId, labelId) ->
            val isTheme = tileId == R.id.dock_theme
            findViewById<View>(tileId).apply {
                background = ThemeKit.pressable(ThemeKit.linear(pal.chipStops, px(18f)).apply {
                    if (pal.chipStroke != 0) setStroke(px(1f).toInt(), pal.chipStroke)
                }, pal.ink)
                elevation = px(pal.chipElevationDp)
                outlineProvider = roundRect(px(18f))
            }
            val tint = if (isTheme) pal.muted else pal.ink
            findViewById<ImageView>(iconId).imageTintList = ColorStateList.valueOf(tint)
            findViewById<TextView>(labelId).apply { typeface = semibold; setTextColor(tint) }
        }
        phoneDot.background = ThemeKit.oval(theme.accent, px(2f).toInt(), pal.chipStops[1])
        renderModeButton()
    }

    private fun roundRect(r: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, r)
    }

    /** The now-playing panel takes on a soft wash of the album's color. */
    private fun updateWash() {
        if (nowPanel.background == null) nowPanel.background = ThemeKit.linear(intArrayOf(pal.washBase, pal.washBase), px(32f))
        nowPanel.post {
            val radius = 1.2f * maxOf(nowPanel.width, nowPanel.height).coerceAtLeast(1)
            nowPanel.background = ThemeKit.radial(
                intArrayOf(ThemeKit.withAlpha(washColor, pal.washStrength), pal.washBase),
                radius, cx = 0.85f, cy = 0.15f, cornerPx = px(32f),
            )
        }
    }

    private fun renderModeButton() {
        val mode = ThemePrefs.get(this)
        val (icon, label) = when (mode) {
            ThemePrefs.LIGHT -> R.drawable.ic_sun to "Light"
            ThemePrefs.DARK -> R.drawable.ic_moon to "Dark"
            else -> R.drawable.ic_auto to "Auto"
        }
        findViewById<ImageView>(R.id.dock_theme_icon).setImageResource(icon)
        findViewById<TextView>(R.id.dock_theme_label).text = label
        findViewById<View>(R.id.dock_theme).contentDescription = "Theme mode $label. Tap to change, hold to switch theme"
    }

    private fun cycleMode() {
        val next = when (ThemePrefs.get(this)) {
            ThemePrefs.AUTO -> ThemePrefs.LIGHT
            ThemePrefs.LIGHT -> ThemePrefs.DARK
            else -> ThemePrefs.AUTO
        }
        ThemePrefs.set(this, next)
        NightSync.sync(force = true)   // switch the whole unit (and Android Auto) too
        recreate()
    }

    private fun cycleTheme() {
        val next = Themes.next(theme)
        Toast.makeText(this, "Theme: ${next.name}", Toast.LENGTH_SHORT).show()
        if (next.id == theme.id) return
        ThemePrefs.setTheme(this, next)
        recreate()
    }

    // ======================= play / pause =======================

    private fun onPlayPressed() {
        val c = controller
        val src = TwSource.current
        val btTarget = c == null || c.packageName == Apps.PHONE
        // Known source (from the Topway system). com.tw.bt sends the phone a play/pause TOGGLE whenever the
        // source becomes Bluetooth, so never combine a source switch with our own play/pause.
        if (btTarget && src >= 0 && src != TwSource.BLUETOOTH) {
            android.util.Log.i("CarHome", "play pressed: source $src -> switching to Bluetooth (its toggle starts playback)")
            BtSource.ensure(this)
            return
        }
        if (btTarget && src == TwSource.BLUETOOTH) {
            android.util.Log.i("CarHome", "play pressed: Bluetooth is the source -> play/pause")
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        // Source unknown: older best-effort logic
        val decision = MediaLogic.onPlayPressed(c?.let { view(it) }, BtSource.active, BtLog.muted())
        android.util.Log.i("CarHome", "play pressed: session=${c?.packageName} playing=${c?.let { isPlaying(it) }} btActive=${BtSource.active} muted=${BtLog.muted()} -> $decision")
        when (decision) {
            MediaLogic.Play.SWITCH_TO_BT -> BtSource.ensure(this)
            MediaLogic.Play.PAUSE_TRANSPORT -> c!!.transportControls.pause()
            MediaLogic.Play.PAUSE_KEY -> mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            MediaLogic.Play.PLAY_KEY_AND_SWITCH_TO_BT -> {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
                if (BtLog.muted() == null) {
                    BtSource.ensure(this)       // can't see the mute state: switch to be safe
                } else {
                    // Give the phone ~2.5 s to start streaming, then switch only if the unit is muting it
                    handler.postDelayed({ if (BtLog.muted() == true) BtSource.ensure(this) }, 2500)
                }
            }
            MediaLogic.Play.PLAY_TRANSPORT -> c!!.transportControls.play()
            MediaLogic.Play.PLAY_KEY -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun view(c: MediaController) = MediaLogic.Session(
        pkg = c.packageName ?: "",
        playing = if (c.packageName == Apps.PHONE && BtLog.hasInfo()) BtLog.playing() else isPlaying(c),
        hasMetadata = (c.packageName == Apps.PHONE && BtLog.track != null) || c.metadata != null,
        actions = c.playbackState?.actions ?: 0L,
        updated = c.playbackState?.lastPositionUpdateTime ?: 0L,
    )

    // ======================= media =======================

    private fun refreshSessions() {
        try {
            pickController(sessionManager.getActiveSessions(listenerComponent))
            needsMediaAccess = false
        } catch (e: SecurityException) {
            needsMediaAccess = true
            setController(null)
        }
    }

    private fun pickController(list: List<MediaController>?) {
        // Prefer whatever is playing; then the phone's Bluetooth session with track info;
        // then any Bluetooth session (com.tw.bt registers several); then anything.
        val l = list.orEmpty()
        val views = l.map { view(it) }
        val pick = MediaLogic.pick(views).let { if (it >= 0) l[it] else null }
        val phoneThere = MediaLogic.phonePresent(views) || BtLog.hasInfo()
        phoneDot.visibility = if (phoneThere) View.VISIBLE else View.GONE
        aaStatus.text = if (phoneThere) "Phone connected" else "Waiting for phone"
        findViewById<View>(R.id.aa_tile).contentDescription = "Start Android Auto. ${aaStatus.text}"
        findViewById<View>(R.id.dock_phone).contentDescription = if (phoneThere) "Phone, connected" else "Phone"
        setController(pick)
    }

    private fun setController(c: MediaController?) {
        if (c != null && c.sessionToken == controller?.sessionToken) {
            renderMedia(); return
        }
        controller?.unregisterCallback(mediaCallback)
        controller = c
        c?.registerCallback(mediaCallback, handler)
        renderMedia()
    }

    private fun isPlaying(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING

    private fun renderMedia() {
        val c = controller
        val md = c?.metadata
        when {
            needsMediaAccess -> {
                track.text = "Allow media access"
                artist.text = "Tap here once so Now playing can show your music"
            }
            else -> {
                val bt = isBluetooth(c)
                val btTrack = if (bt) BtLog.track else null
                val (line1, line2) = MediaLogic.lines(
                    hasSession = (c != null && md != null) || btTrack != null || (bt && BtLog.hasInfo()),
                    rawTitle = btTrack?.title ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    rawArtist = btTrack?.artist ?: md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    playing = isPlayingNow(c),
                    label = if (bt) "Bluetooth" else c?.packageName?.let { appLabel(it) } ?: "",
                )
                track.text = line1
                artist.text = line2
            }
        }
        val bmp: Bitmap? = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (!artDrawn || bmp !== lastArt) {
            artDrawn = true
            lastArt = bmp
            if (bmp != null) {
                art.background = null
                art.imageTintList = null
                art.setPadding(0, 0, 0, 0)
                art.setImageBitmap(bmp)
                washColor = averageColor(bmp)
            } else {
                val p = px(52f).toInt()
                art.setPadding(p, p, p, p)
                art.background = ThemeKit.linear(intArrayOf(pal.raised, ThemeKit.mix(pal.raised, theme.fallbackArt, 0.35f)), px(20f))
                art.setImageResource(R.drawable.ic_music)
                art.imageTintList = ColorStateList.valueOf(pal.muted)
                washColor = theme.fallbackArt
            }
            updateWash()
        }
        val playingNow = isPlayingNow(c)
        play.setImageResource(if (playingNow) R.drawable.ic_pause else R.drawable.ic_play)
        play.contentDescription = if (playingNow) "Pause" else "Play"
        renderProgress()
    }

    /** The cover's overall color (true average of a 16x16 sample), nudged toward saturation. */
    private fun averageColor(bmp: Bitmap): Int = try {
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var r = 0L; var g = 0L; var b = 0L
        for (y in 0 until 16) for (x in 0 until 16) {
            val p = small.getPixel(x, y)
            r += Color.red(p); g += Color.green(p); b += Color.blue(p)
        }
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb((r / 256).toInt(), (g / 256).toInt(), (b / 256).toInt()), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(0.85f)
        // At night keep the wash dark so the date/artist text on it stays readable
        hsv[2] = hsv[2].coerceIn(0.30f, if (night) 0.55f else 0.75f)
        Color.HSVToColor(hsv)
    } catch (e: Exception) { theme.fallbackArt }

    /** Bluetooth = the vendor BT app's session, or no session but the BT log shows music info. */
    private fun isBluetooth(c: MediaController?) =
        c?.packageName == Apps.PHONE || (c == null && BtLog.hasInfo())

    private fun isPlayingNow(c: MediaController?): Boolean =
        if (isBluetooth(c) && BtLog.hasInfo()) BtLog.playing() else c != null && isPlaying(c)

    private fun renderProgress() {
        val c = controller
        if (isBluetooth(c) && BtLog.durationMs > 0) {
            // Real position from the BT app's log (its media sessions report stale positions)
            val d = BtLog.durationMs
            val p = BtLog.position()
            progressBox.visibility = View.VISIBLE
            progress.fraction = p.toFloat() / d
            pos.text = MediaLogic.clock(p)
            dur.text = MediaLogic.clock(d)
            return
        }
        val duration = c?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val state = c?.playbackState
        if (c == null || duration <= 0 || state == null) {
            progressBox.visibility = View.INVISIBLE
            return
        }
        val position = MediaLogic.position(state.position, state.lastPositionUpdateTime,
            SystemClock.elapsedRealtime(), state.playbackSpeed, state.state == PlaybackState.STATE_PLAYING, duration)
        progressBox.visibility = View.VISIBLE
        progress.fraction = position.toFloat() / duration
        pos.text = MediaLogic.clock(position)
        dur.text = MediaLogic.clock(duration)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /**
     * Send a media key. Bluetooth (com.tw.bt) runs ~10 sessions and only its "BTModel" session, the
     * system's media-button receiver, acts on keys (verified on the unit), so Bluetooth keys go through
     * the system route. Other players get the key on their own session, so a paused USB player can't
     * steal a key meant for something else.
     */
    private fun mediaKey(code: Int) {
        // Real timestamps + keyboard source, like a physical button: com.tw.bt ignores keys with
        // eventTime=0 / source=0 (verified on the unit: those were delivered but had no effect).
        val t = SystemClock.uptimeMillis()
        val down = KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD)
        val up = KeyEvent(t, t + 50, KeyEvent.ACTION_UP, code, 0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, android.view.InputDevice.SOURCE_KEYBOARD)
        val c = controller
        onMediaKey?.invoke(code, c?.packageName)
        android.util.Log.i("CarHome", "media key $code -> ${if (c != null && c.packageName != Apps.PHONE) c.packageName else "system broadcast"}")
        if (c != null && c.packageName != Apps.PHONE) {
            c.dispatchMediaButtonEvent(down)
            c.dispatchMediaButtonEvent(up)
        } else {
            // Bluetooth only obeys the unit's own virtual keys; the system service sends them for this broadcast
            if (!BtControl.send(this, code)) {
                val am = getSystemService(AudioManager::class.java)
                am.dispatchMediaKeyEvent(down)
                am.dispatchMediaKeyEvent(up)
            }
        }
    }
}
