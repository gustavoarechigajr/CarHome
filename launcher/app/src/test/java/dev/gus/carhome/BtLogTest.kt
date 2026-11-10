package dev.gus.carhome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parses the exact log lines captured from the unit's Bluetooth app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BtLogTest {
    @Before fun reset() = BtLog.resetForTest()

    @Test fun trackLine() {
        BtLog.parse("D/BTModel ( 8667): RETURN_ID3:musicTitle:Song Name musicAlbum:Album X musicArtist:Some Artist", now = 1000)
        assertEquals("Song Name", BtLog.track?.title)
        assertEquals("Album X", BtLog.track?.album)
        assertEquals("Some Artist", BtLog.track?.artist)
    }

    @Test fun positionLine_fullWidthColon_andPlayingDetection() {
        BtLog.parse("D/BTModel ( 8667): RETURN_MS:totalTime：304200 currentTime:41035", now = 10_000)
        BtLog.parse("D/BTModel ( 8667): RETURN_MS:totalTime：304200 currentTime:42022", now = 11_000)
        assertEquals(304_200, BtLog.durationMs)
        assertTrue(BtLog.playing(now = 11_500))
        assertEquals(42_522, BtLog.position(now = 11_500))
        // position stops advancing → paused after the window
        BtLog.parse("D/BTModel ( 8667): RETURN_MS:totalTime：304200 currentTime:42022", now = 12_000)
        assertFalse(BtLog.playing(now = 14_000))
        assertEquals(42_022, BtLog.position(now = 14_000))
    }

    @Test fun muteLines() {
        BtLog.parse("E/blink   (17975): sbc_process_decode mute(1) hfpcall(0) hfpConnect(1) a2dpdisconnect(0)", now = 5_000)
        assertEquals(true, BtLog.muted(now = 6_000))
        assertEquals(false, BtLog.muted(now = 10_000))
    }

    @Test fun broadcastFeedsTrackAndPosition() {
        BtLog.onId3("Song B", "Artist B", progressMs = 60_000, durationMs = 200_000, now = 1_000)
        BtLog.onId3("Song B", "Artist B", progressMs = 61_000, durationMs = 200_000, now = 2_000)
        assertEquals("Song B", BtLog.track?.title)
        assertEquals("Artist B", BtLog.track?.artist)
        assertEquals(200_000, BtLog.durationMs)
        assertTrue(BtLog.playing(now = 2_500))
        assertEquals(61_500, BtLog.position(now = 2_500))
        // song change: position jumps back
        BtLog.onId3("Song C", "Artist C", progressMs = 1_000, durationMs = 180_000, now = 3_000)
        assertEquals("Song C", BtLog.track?.title)
        assertEquals(180_000, BtLog.durationMs)
        assertTrue(BtLog.playing(now = 3_500))
    }
}
