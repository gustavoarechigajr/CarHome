package dev.gus.carhome

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import dev.gus.carhome.theme.Themes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Media controls, Bluetooth source switching and day/night handling, driven end to end
 * through HomeActivity on the Android 10 framework. A real MediaSession stands in for the
 * Topway Bluetooth player (package com.tw.bt, no transport actions: keys only).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "w1280dp-h662dp-land-mdpi")
class MediaFlowTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val keys = mutableListOf<Int>()
    private val targets = mutableListOf<String?>()
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        app.getSharedPreferences("carhome", 0).edit().clear().commit()
        BtSource.lost()
        TwSource.resetForTest()
        keys.clear(); targets.clear()
        // Robolectric's controller doesn't forward media buttons to the session, so record what
        // HomeActivity sends and to which app via its test hook.
        HomeActivity.onMediaKey = { code, target -> keys.add(code); targets.add(target) }
        session = MediaSession(app, "fake-bt").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(i: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val ev = i.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    return true
                }
            })
            isActive = true
        }
    }

    // ---------- helpers ----------

    /** Publish a fake session as if it came from [pkg]. actions = 0 mimics com.tw.bt. */
    private fun publish(pkg: String, state: Int, actions: Long = 0, title: String? = "Song", artist: String? = "Band"): MediaController {
        val c = session.controller
        shadowOf(c).setPackageName(pkg)
        shadowOf(c).setPlaybackState(PlaybackState.Builder().setState(state, 84_000, 1f).setActions(actions).build())
        if (title != null) shadowOf(c).setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .apply { if (artist != null) putString(MediaMetadata.METADATA_KEY_ARTIST, artist) }
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 232_000).build())
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(c)
        return c
    }


    private fun home(): ActivityController<HomeActivity> {
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        return c
    }

    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun HomeActivity.click(id: Int) { findViewById<View>(id).performClick(); idle() }

    // ---------- Now playing card ----------

    @Test
    fun bluetoothTrackIsShownOnTheCard() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val a = home().get()
        assertEquals("Song", a.findViewById<TextView>(R.id.track).text.toString())
        assertEquals("Band, from Bluetooth", a.findViewById<TextView>(R.id.artist).text.toString())
        assertEquals("1:24", a.findViewById<TextView>(R.id.pos).text.toString())
        assertEquals("3:52", a.findViewById<TextView>(R.id.dur).text.toString())
        assertEquals(View.VISIBLE, a.findViewById<View>(R.id.phone_dot).visibility)
        assertEquals("Phone connected", a.findViewById<TextView>(R.id.aa_status).text.toString())
    }

    @Test
    fun noSessions_showsWaitingForPhone() {
        val a = home().get()
        assertEquals(View.GONE, a.findViewById<View>(R.id.phone_dot).visibility)
        assertEquals("Waiting for phone", a.findViewById<TextView>(R.id.aa_status).text.toString())
        assertEquals(View.INVISIBLE, a.findViewById<View>(R.id.progress_box).visibility)
    }

    // ---------- Play / Pause / Prev / Next ----------

    private fun acquireBroadcasts() =
        shadowOf(app).broadcastIntents.count { it.action == BtSource.ACTION_ACQUIRE && it.`package` == Apps.PHONE }

    @Test
    fun play_onPausedPhone_sendsPlayKey_andAcquiresBluetoothAudio() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val a = home().get()
        a.click(R.id.play)
        assertEquals(listOf(KeyEvent.KEYCODE_MEDIA_PLAY), keys)
        assertEquals(1, acquireBroadcasts())
        assertNull("no Bluetooth screen is opened any more", shadowOf(a).nextStartedActivity)
    }

    @Test
    fun play_onPhonePlayingButMuted_unmutesWithoutPausing() {
        publish(Apps.PHONE, PlaybackState.STATE_PLAYING)
        val a = home().get()
        a.click(R.id.play)            // no log access in tests → unknown mute → trusts "not switched yet"
        assertTrue("must not pause the phone", keys.isEmpty())
        assertEquals(1, acquireBroadcasts())
        assertTrue(BtSource.active)
    }

    @Test
    fun pause_onRoutedPhone_sendsPauseKey() {
        publish(Apps.PHONE, PlaybackState.STATE_PLAYING)
        val a = home().get()
        a.click(R.id.play)            // first tap: routes Bluetooth audio
        keys.clear()
        a.click(R.id.play)            // second tap: a real pause
        assertEquals(listOf(KeyEvent.KEYCODE_MEDIA_PAUSE), keys)
    }

    @Test
    fun prevAndNext_onPhone_useKeys() {
        publish(Apps.PHONE, PlaybackState.STATE_PLAYING)
        val a = home().get()
        a.click(R.id.prev); a.click(R.id.next)
        assertEquals(listOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT), keys)
        assertEquals("keys aimed at the Bluetooth player", listOf(Apps.PHONE, Apps.PHONE), targets)
    }

    @Test
    fun capableLocalPlayer_usesTransportControls_notKeys() {
        val all = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        publish(Apps.MUSIC, PlaybackState.STATE_PAUSED, actions = all)
        val a = home().get()
        a.click(R.id.play); a.click(R.id.next)
        assertTrue("transport controls, not media keys", keys.isEmpty())
        assertEquals("no Bluetooth acquire for a local player", 0, acquireBroadcasts())
    }

    @Test
    fun dayNightRelaunch_keepsBluetoothRouted() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val c = home()
        c.get().click(R.id.play)
        assertTrue(BtSource.active)
        c.recreate()
        assertTrue("a relaunch is not a source change", BtSource.active)
    }

    @Test
    fun openingAnotherApp_marksBluetoothLost() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val c = home()
        c.get().click(R.id.play)
        c.get().click(R.id.aa_tile)
        c.pause()
        assertFalse(BtSource.active)
    }

    @Test
    fun openingOwnAppDrawer_keepsBluetoothRouted() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val c = home()
        c.get().click(R.id.play)
        c.get().click(R.id.dock_apps)
        c.pause(); c.resume()
        assertTrue(BtSource.active)
    }

    // ---------- with the real audio source known (TwSource) ----------

    @Test
    fun play_whileAnotherSourceIsActive_onlySwitchesToBluetooth() {
        publish(Apps.PHONE, PlaybackState.STATE_PAUSED)
        val a = home().get()
        TwSource.report(3)                         // car music app has the source
        a.click(R.id.play)
        assertTrue("no play/pause: the BT app's own toggle starts playback", keys.isEmpty())
        assertEquals(1, acquireBroadcasts())
    }

    @Test
    fun play_whileBluetoothIsTheSource_sendsOnlyPlayPause() {
        publish(Apps.PHONE, PlaybackState.STATE_PLAYING)
        val a = home().get()
        TwSource.report(TwSource.BLUETOOTH)
        a.click(R.id.play)
        assertEquals(listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE), keys)
        assertEquals("no source switch when Bluetooth already has it", 0, acquireBroadcasts())
    }

    // ---------- album-art wash screenshots ----------

    private fun publishWithArt() {
        val art = android.graphics.Bitmap.createBitmap(300, 300, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(art).apply {
            drawColor(android.graphics.Color.rgb(240, 200, 40))
            drawCircle(150f, 150f, 90f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 60, 40) })
        }
        val c = publish(Apps.PHONE, PlaybackState.STATE_PLAYING, title = "Bright cover test", artist = "Contrast check")
        shadowOf(c).setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Bright cover test")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Contrast check")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 232_000)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art).build())
    }

    private fun shot(a: HomeActivity, name: String) {
        val root = a.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        val dir = java.io.File("build/screens").apply { mkdirs() }
        java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun brightCover_day_screenshot() {
        publishWithArt()
        shot(home().get(), "playing_day")
    }

    @Test
    @Config(qualifiers = "w1280dp-h662dp-land-night-mdpi")
    fun brightCover_night_screenshot() {
        publishWithArt()
        shot(home().get(), "playing_night")
    }

    // ---------- day/night ----------

    @Test
    fun headlightsOn_switchesHomeToNightInPlace() {
        val c = home()
        val before = c.get()
        RuntimeEnvironment.setQualifiers("+night")
        c.configurationChange(Configuration(app.resources.configuration))
        idle()
        assertTrue(ThemePrefs.isNight(before))
        val bg = before.findViewById<View>(R.id.root).background as GradientDrawable
        assertArrayEquals(Themes.GLACIER.night.bgStops, bg.colors)
    }

    @Test
    fun nightSync_retriesAFewTimes_thenStops() {   // agent finding #6
        ThemePrefs.set(app, ThemePrefs.DARK)       // system is day in this test → never "applies"
        NightSync.start(app)
        NightSync.sync(force = true)
        val count = { shadowOf(app).broadcastIntents.count { it.action == NightSync.ACTION } }
        val first = count()
        idle(1500L * 20)
        assertEquals(first + NightSync.MAX_RETRIES, count())
    }
}
