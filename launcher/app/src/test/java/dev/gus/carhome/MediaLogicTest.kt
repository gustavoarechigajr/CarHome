package dev.gus.carhome

import dev.gus.carhome.MediaLogic.Play
import dev.gus.carhome.MediaLogic.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-logic tests: every media / Bluetooth-source decision, no Android needed. */
class MediaLogicTest {

    private val btPaused = Session(Apps.PHONE, playing = false, hasMetadata = true, actions = 0)
    private val btPlaying = Session(Apps.PHONE, playing = true, hasMetadata = true, actions = 0)
    private val btEmpty = Session(Apps.PHONE, playing = false, hasMetadata = false, actions = 0)
    private val all = MediaLogic.ACTION_PLAY or MediaLogic.ACTION_PAUSE or
        MediaLogic.ACTION_SKIP_TO_NEXT or MediaLogic.ACTION_SKIP_TO_PREVIOUS
    private val usbPlaying = Session(Apps.MUSIC, playing = true, hasMetadata = true, actions = all)
    private val usbPaused = Session(Apps.MUSIC, playing = false, hasMetadata = true, actions = all)
    private val dumbPaused = Session("x.radio", playing = false, hasMetadata = true, actions = 0)
    private val dumbPlaying = Session("x.radio", playing = true, hasMetadata = true, actions = 0)

    // ---- Play button ----
    @Test fun nothingPlaying_playsAndRoutesBluetooth() =
        assertEquals(Play.PLAY_KEY_AND_SWITCH_TO_BT, MediaLogic.onPlayPressed(null, btSourceActive = false))

    @Test fun phonePaused_playsAndRoutesBluetooth() =
        assertEquals(Play.PLAY_KEY_AND_SWITCH_TO_BT, MediaLogic.onPlayPressed(btPaused, btSourceActive = true))

    @Test fun phonePlayingButUnitMuted_switchesSourceInsteadOfPausing() =
        assertEquals(Play.SWITCH_TO_BT, MediaLogic.onPlayPressed(btPlaying, btSourceActive = false))

    @Test fun phonePlayingAndRouted_pausesWithKey() =   // com.tw.bt ignores transport controls
        assertEquals(Play.PAUSE_KEY, MediaLogic.onPlayPressed(btPlaying, btSourceActive = true))

    @Test fun localAppPlaying_pausesWithTransport() =
        assertEquals(Play.PAUSE_TRANSPORT, MediaLogic.onPlayPressed(usbPlaying, btSourceActive = false))

    @Test fun localAppPaused_playsWithTransport() =
        assertEquals(Play.PLAY_TRANSPORT, MediaLogic.onPlayPressed(usbPaused, btSourceActive = false))

    @Test fun appWithoutActions_usesKeys() {
        assertEquals(Play.PLAY_KEY, MediaLogic.onPlayPressed(dumbPaused, btSourceActive = false))
        assertEquals(Play.PAUSE_KEY, MediaLogic.onPlayPressed(dumbPlaying, btSourceActive = false))
    }

    // ---- Prev / Next ----
    @Test fun skipOnBluetooth_usesKeys() {
        assertFalse(MediaLogic.skipUsesTransport(btPlaying, next = true))
        assertFalse(MediaLogic.skipUsesTransport(btPlaying, next = false))
    }

    @Test fun skipOnCapableApp_usesTransport() {
        assertTrue(MediaLogic.skipUsesTransport(usbPlaying, next = true))
        assertTrue(MediaLogic.skipUsesTransport(usbPlaying, next = false))
    }

    @Test fun skipWithNoSession_usesKeys() = assertFalse(MediaLogic.skipUsesTransport(null, next = true))

    // ---- Which session the card follows ----
    @Test fun pick_prefersPlaying() = assertEquals(1, MediaLogic.pick(listOf(btPaused, usbPlaying)))

    @Test fun pick_followsFirstListedBluetoothSession() =
        assertEquals(0, MediaLogic.pick(listOf(btPaused, btEmpty, usbPaused)))

    /** Seen on the unit: stale com.tw.bt sessions behind the live one still claim "playing". */
    @Test fun pick_ignoresStaleBluetoothSessionStuckOnPlaying() {
        val live = btPaused
        val stale = btPlaying
        assertEquals(0, MediaLogic.pick(listOf(live, stale)))
        assertEquals(0, MediaLogic.pick(listOf(live, stale, usbPaused)))
    }

    @Test fun mutedSignal_overridesOurGuess() {
        // log says not muted → a tap pauses, even if we never switched the source ourselves
        assertEquals(Play.PAUSE_KEY, MediaLogic.onPlayPressed(btPlaying, btSourceActive = false, btMuted = false))
        // log says muted → switch source instead of pausing
        assertEquals(Play.SWITCH_TO_BT, MediaLogic.onPlayPressed(btPlaying, btSourceActive = true, btMuted = true))
        // no log access → old behaviour
        assertEquals(Play.SWITCH_TO_BT, MediaLogic.onPlayPressed(btPlaying, btSourceActive = false, btMuted = null))
    }

    @Test fun pick_localPlayerPlaying_beatsPausedBluetooth() =
        assertEquals(1, MediaLogic.pick(listOf(btPaused, usbPlaying)))

    @Test fun pick_thenBluetooth() = assertEquals(1, MediaLogic.pick(listOf(usbPaused, btEmpty)))

    @Test fun pick_thenFirst_orNone() {
        assertEquals(0, MediaLogic.pick(listOf(usbPaused, dumbPaused)))
        assertEquals(-1, MediaLogic.pick(emptyList()))
    }

    @Test fun phonePresence() {
        assertTrue(MediaLogic.phonePresent(listOf(usbPaused, btEmpty)))
        assertFalse(MediaLogic.phonePresent(listOf(usbPaused)))
    }

    // ---- Progress ----
    @Test fun position_extrapolatesWhilePlaying() =
        assertEquals(15_000, MediaLogic.position(10_000, lastUpdate = 1_000, now = 6_000, speed = 1f, playing = true, duration = 200_000))

    @Test fun position_frozenWhenPaused() =
        assertEquals(10_000, MediaLogic.position(10_000, 1_000, 6_000, 1f, playing = false, duration = 200_000))

    @Test fun position_clampedToTrack() {
        assertEquals(200_000, MediaLogic.position(199_000, 1_000, 60_000, 1f, true, 200_000))
        assertEquals(0, MediaLogic.position(-5, 0, 0, 1f, false, 200_000))
    }

    @Test fun clockFormat() {
        assertEquals("0:00", MediaLogic.clock(0))
        assertEquals("1:24", MediaLogic.clock(84_000))
        assertEquals("1:02:03", MediaLogic.clock(3_723_000))
    }

    // ---- Card text ----
    @Test fun lines_cover_every_case() {
        assertEquals("Nothing playing" to "Play music from your phone or the radio",
            MediaLogic.lines(false, null, null, false, "Bluetooth"))
        assertEquals("Playing" to "Bluetooth is ready", MediaLogic.lines(true, "", null, true, "Bluetooth"))
        assertEquals("Nothing playing" to "Bluetooth is ready", MediaLogic.lines(true, null, null, false, "Bluetooth"))
        assertEquals("Song" to "Bluetooth", MediaLogic.lines(true, "Song", null, true, "Bluetooth"))
        assertEquals("Song" to "Band, from Bluetooth", MediaLogic.lines(true, "Song", "Band", true, "Bluetooth"))
        // com.tw.bt sends literal placeholder words when the phone shares no track info (seen on the unit)
        assertEquals("Playing" to "Bluetooth is ready", MediaLogic.lines(true, "Not Provided", "Not Provided", true, "Bluetooth"))
        assertEquals("Song" to "Bluetooth", MediaLogic.lines(true, "Song", "Unknown", true, "Bluetooth"))
    }

    @Test fun bluetoothKeysMapToUnitBroadcast() {
        assertEquals("pp", BtControl.typeFor(126)); assertEquals("pp", BtControl.typeFor(127)); assertEquals("pp", BtControl.typeFor(85))
        assertEquals("next", BtControl.typeFor(87)); assertEquals("prev", BtControl.typeFor(88))
        assertEquals(null, BtControl.typeFor(90))
    }

    @Test fun reacquireOnlyWhenPhonePlayingAndUnitMuting() {
        assertTrue(MediaLogic.shouldReacquireBt(phonePlaying = true, btMuted = true))
        assertFalse(MediaLogic.shouldReacquireBt(phonePlaying = false, btMuted = true))   // phone paused: leave radio/USB alone
        assertFalse(MediaLogic.shouldReacquireBt(phonePlaying = true, btMuted = false))
        assertFalse(MediaLogic.shouldReacquireBt(phonePlaying = true, btMuted = null))    // can't tell: do nothing
    }
}
