package dev.gus.carhome

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.io.FileOutputStream

/**
 * Runs Car Home on the real Android 10 framework (Robolectric) at the head unit's exact
 * content area: 1280x662 dp, landscape, 160 dpi. No emulator or KVM needed.
 * Screenshots land in app/build/screens/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "w1280dp-h662dp-land-mdpi")
class HomeActivityTest {

    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun resetPrefs() {
        app.getSharedPreferences("carhome", 0).edit().clear().commit()
    }

    private fun launch(): HomeActivity {
        val a = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()   // run view.post{} (tile + wash backgrounds)
        return a
    }

    private fun screenshot(a: HomeActivity, name: String) {
        val root = a.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------------- launch + rendering ----------------

    @Test
    fun launchesInDay_andSavesScreenshot() {
        val a = launch()
        assertFalse(ThemePrefs.isNight(a))
        screenshot(a, "home_day")
    }

    @Test
    @Config(qualifiers = "w1280dp-h662dp-land-night-mdpi")
    fun launchesInNight_andSavesScreenshot() {
        val a = launch()
        assertTrue(ThemePrefs.isNight(a))
        screenshot(a, "home_night")
    }

    @Test
    fun albumArtPlaceholderIsDrawnOnFirstRender() {   // regression test for review bug B1
        val a = launch()
        assertNotNull(a.findViewById<ImageView>(R.id.art).drawable)
        assertEquals("Nothing playing", a.findViewById<TextView>(R.id.track).text.toString())
    }

    @Test
    fun tileAndDockBackgroundsAreThemed() {
        val a = launch()
        assertNotNull("AA tile gradient", a.findViewById<View>(R.id.aa_tile).background)
        assertNotNull("now panel wash", a.findViewById<View>(R.id.now_panel).background)
        listOf(R.id.dock_music, R.id.dock_phone, R.id.dock_apps, R.id.dock_settings, R.id.dock_theme).forEach {
            assertNotNull("dock tile $it", a.findViewById<View>(it).background)
        }
    }

    // ---------------- layout fits the 1280x662 screen ----------------

    @Test
    fun everythingFitsOnScreen() {
        val a = launch()
        val root = a.findViewById<View>(R.id.root)
        assertEquals(1280, root.width)
        assertEquals(662, root.height)

        fun bottomInRoot(id: Int): Int {
            val v = a.findViewById<View>(id)
            val loc = IntArray(2); v.getLocationInWindow(loc)
            val rootLoc = IntArray(2); root.getLocationInWindow(rootLoc)
            return loc[1] - rootLoc[1] + v.height
        }
        listOf(R.id.aa_tile, R.id.now_panel, R.id.dock, R.id.play, R.id.progress_box, R.id.aa_play).forEach {
            assertTrue("view $it overflows the screen", bottomInRoot(it) <= root.height)
        }

        // Tile: two-line title and status must fit inside the tile
        val tile = a.findViewById<View>(R.id.aa_tile)
        val title = a.findViewById<TextView>(R.id.aa_title)
        assertEquals(2, title.lineCount)
        assertTrue("tile title too wide", title.layout.getLineWidth(0) <= tile.width - 80)
        assertTrue("tile content overflows", bottomInRoot(R.id.aa_play) <= bottomInRoot(R.id.aa_tile))

        // Now-playing panel: controls must sit inside the panel
        assertTrue("controls overflow panel", bottomInRoot(R.id.play) <= bottomInRoot(R.id.now_panel))

        // Clock must not be cut off
        val clock = a.findViewById<TextView>(R.id.clock)
        assertEquals(0, clock.layout.getEllipsisCount(0))

        // Touch targets >= 48dp
        listOf(R.id.prev, R.id.play, R.id.next, R.id.dock_music, R.id.dock_phone, R.id.dock_apps,
            R.id.dock_settings, R.id.dock_theme).forEach {
            val v = a.findViewById<View>(it)
            assertTrue("touch target $it too small", v.width >= 48 && v.height >= 48)
        }
    }

    // ---------------- buttons ----------------

    @Test
    fun androidAutoTileOpensZlink() {
        val a = launch()
        a.findViewById<View>(R.id.aa_tile).performClick()
        val started = shadowOf(a).nextStartedActivity
        assertEquals(Apps.ZLINK, started.component?.packageName)
        assertEquals(Apps.ZLINK_MAIN, started.component?.className)
    }

    @Test
    fun dockButtonsTryToOpenTheirApps() {
        val a = launch()
        // Vendor apps aren't installed in the test framework, so each should fail politely, naming its app
        mapOf(R.id.dock_music to Apps.PHONE, R.id.dock_phone to Apps.PHONE, R.id.dock_settings to Apps.CAR_SETTINGS)
            .forEach { (id, pkg) ->
                a.findViewById<View>(id).performClick()
                assertEquals("App not installed: $pkg", ShadowToast.getTextOfLatestToast())
            }
    }

    @Test
    fun musicButton_routesBluetoothAudio_beforeOpeningTheBluetoothApp() {
        val a = launch()
        a.findViewById<View>(R.id.dock_music).performClick()
        assertTrue(shadowOf(app).broadcastIntents.any { it.action == BtSource.ACTION_ACQUIRE })
        assertTrue(BtSource.active)
    }

    @Test
    fun appsButtonOpensTheDrawer() {
        val a = launch()
        a.findViewById<View>(R.id.dock_apps).performClick()
        assertEquals(AppsActivity::class.java.name, shadowOf(a).nextStartedActivity.component?.className)
    }

    @Test
    fun playWithNothingPlaying_acquiresBluetoothAudio() {
        val a = launch()
        a.findViewById<View>(R.id.play).performClick()
        assertTrue(shadowOf(app).broadcastIntents.any { it.action == BtSource.ACTION_ACQUIRE })
    }

    @Test
    fun leavingHome_marksBluetoothSourceLost() {   // regression test for review bug B3
        val c = Robolectric.buildActivity(HomeActivity::class.java).setup()
        c.pause()
        assertFalse(BtSource.active)
    }

    // ---------------- theme mode + theme ----------------

    @Test
    fun themeTileCyclesAutoLightDark() {
        val a = launch()
        assertEquals("Auto", a.findViewById<TextView>(R.id.dock_theme_label).text.toString())
        a.findViewById<View>(R.id.dock_theme).performClick()
        assertEquals(ThemePrefs.LIGHT, ThemePrefs.get(app))
        val b = launch()
        b.findViewById<View>(R.id.dock_theme).performClick()
        assertEquals(ThemePrefs.DARK, ThemePrefs.get(app))
        val c = launch()
        assertEquals("Dark", c.findViewById<TextView>(R.id.dock_theme_label).text.toString())
        assertTrue("forced Dark must render night", ThemePrefs.isNight(c))
        c.findViewById<View>(R.id.dock_theme).performClick()
        assertEquals(ThemePrefs.AUTO, ThemePrefs.get(app))
    }

    @Test
    fun longPressThemeTileNamesTheTheme() {
        val a = launch()
        a.findViewById<View>(R.id.dock_theme).performLongClick()
        assertEquals("Theme: Glacier", ShadowToast.getTextOfLatestToast())
    }

    // ---------------- night sync ----------------

    @Test
    fun forcedModesTellTheUnitToSwitchNightMode() {
        ThemePrefs.set(app, ThemePrefs.DARK)
        NightSync.start(app)
        NightSync.sync(force = true)
        val sent = shadowOf(app).broadcastIntents.last { it.action == "cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED" }
        assertTrue(sent.getBooleanExtra("isNight", false))
        assertEquals("follow_headlamp", sent.getStringExtra("curLaunchMode"))

        ThemePrefs.set(app, ThemePrefs.LIGHT)
        NightSync.sync(force = true)
        val sent2 = shadowOf(app).broadcastIntents.last { it.action == "cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED" }
        assertFalse(sent2.getBooleanExtra("isNight", true))
    }

    @Test
    fun autoModeWithoutHeadlightInput_sendsNothing() {
        // On a PC there is no /sys/class/twctrl/panel_lights, so AUTO must not guess
        ThemePrefs.set(app, ThemePrefs.AUTO)
        val before = shadowOf(app).broadcastIntents.size
        NightSync.start(app)
        NightSync.sync(force = true)
        assertEquals(before, shadowOf(app).broadcastIntents.size)
    }
}
