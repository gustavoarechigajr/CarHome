# Car Home — launcher for the Topway/Dofun head unit

Kotlin, framework Views, minSdk/targetSdk 29 (unit runs Android 10). Package `dev.gus.carhome`. Only dependency: none at runtime
(Robolectric + JUnit for tests). Design canvas: https://claude.ai/artifact/LgMDYBJca241jNenA2fZzL

## Build, test, install
```bash
export JAVA_HOME=~/android-studio/jbr
./gradlew assembleDebug testDebugUnitTest        # 56 tests; screenshots → app/build/screens/
../car_setup.sh 10.0.40.20                        # install + permissions + screenshot on the unit
adb -s 127.0.0.1:15555 shell cmd package set-home-activity dev.gus.carhome/.HomeActivity   # make default
```
Permissions granted over adb (car_setup.sh does both):
- `cmd notification allow_listener dev.gus.carhome/dev.gus.carhome.MediaListener` (media sessions)
- `pm grant dev.gus.carhome android.permission.READ_LOGS` (BtLog backup reader + mute signal)

## Themes
Current theme **Glacier** (steel blue `#82B1D9`, cool grey day / deep slate night). Font Archivo variable
(`assets/fonts/Archivo.ttf`, SIL OFL), expanded width for the tile title and clock.
Add a theme: new `CarTheme(...)` in `theme/CarTheme.kt` (accent, onAccent, `day` + `night` `Palette`, fallbackArt, fontAsset) → add to `Themes.all`.
On the unit: tap the theme tile = Auto → Light → Dark; long-press = next theme.

## Files
| File | What it does |
|---|---|
| `HomeActivity.kt` | Home screen. `applyTheme()` paints from the theme; handles uiMode changes in place; media card; dock |
| `AppsActivity.kt` | App drawer (long-press → app info) |
| `theme/CarTheme.kt`, `theme/ThemeKit.kt` | Theme data + registry; gradients, ripples, variable-font loading |
| `RingsView.kt`, `ProgressLine.kt` | Pulsing rings on the AA tile; progress line |
| `MediaLogic.kt` | **Pure** decisions: play/pause/skip routing, which session to follow, card text, progress math |
| `BtControl.kt` | Bluetooth buttons → `com.unisound.unicar.broadcast.action` type `pp`/`next`/`prev` |
| `BtSource.kt` | Bluetooth audio on/off → `net.easyconn.a2dp.acquire` / `.release` |
| `TwSource.kt` | Real current audio source from com.tw.service.xt (binder callback, tx 1/67, callback tx 8; fallback getTWSource tx 166). Drives play-button routing and the BT restore on return to Home |
| `BtLog.kt` | Real BT state: listens to `SEND_ID3_INFO` (asks with `GET_ID3_INFO` every 10 s); backup: logcat reader (BTModel RETURN_ID3/RETURN_MS, blink mute(1)) with auto-restart |
| `NightSync.kt` | Headlights (`/sys/class/twctrl/panel_lights`) → `cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED` (3 retries max) |
| `CarHomeApp.kt` | Starts NightSync, TwSource, BtLog; BootReceiver |
| `MediaListener.kt`, `Launch.kt`, `ThemePrefs.kt` | Media-session access; package names + launcher helper; mode/theme prefs |

## Platform quirks this code relies on (all verified on the unit)
- com.tw.bt **ignores** media keys sent via MediaSession/AudioManager (delivered, no effect). Only the unit's virtual
  keys work (input path, or the broadcast above which com.tw.service turns into one). Play/pause is one toggle.
- com.tw.bt leaks a new "BTModel" MediaSession every time it opens; old ones keep stale tracks/positions. Card data comes
  from its broadcast instead; sessions are only used to detect a player.
- BT audio is muted by the MCU until the BT source (0x08) is requested → `net.easyconn.a2dp.acquire`.
- No seek, no cover art over this Bluetooth link.
- com.tw.bt: Back in its screen releases the BT source and pauses the phone; entering source 8 sends a blind play/pause toggle.
  Hence: Music button = acquire + open BT app; return to Home re-acquires once if the phone was playing; play button never mixes `pp` with acquire.
- Content area 1280×662 dp (vendor status bar on top), mdpi.

## Tests (app/src/test)
`MediaLogicTest` (pure decisions) · `BtLogTest` (exact log/broadcast parsing) · `HomeActivityTest` (launch day/night, layout fit,
buttons, theme cycle, night sync, screenshots) · `MediaFlowTest` (fake BT sessions, key routing via `HomeActivity.onMediaKey`
test hook, acquire broadcasts, relaunch behaviour, bright-cover screenshots). Robolectric runs Android 10 on the JVM; no emulator/KVM needed.
**60 tests, all passing.** History of bugs found/fixed: `REVIEW.md`.
