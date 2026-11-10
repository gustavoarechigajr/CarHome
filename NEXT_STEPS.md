# Status & next steps (updated 2026-11-10)

## Done and verified on the unit
| Area | State |
|---|---|
| ADB | ✅ TCP 5555 (persist.adb.tcp.port), this PC authorized. Reconnect: `./connect.sh 10.0.40.20`. Link over the IoT Wi-Fi drops often; retry |
| Car Home | ✅ **Default home screen.** Glacier theme, Archivo font, day/night |
| Bluetooth buttons ▶⏸⏭⏮ | ✅ via `com.unisound.unicar.broadcast.action` (type pp/next/prev) |
| Now playing (Bluetooth) | ✅ title/artist/time/state from the BT app's SEND_ID3_INFO broadcast (+ log backup) |
| Bluetooth audio unmute | ✅ `net.easyconn.a2dp.acquire` broadcast, invisible |
| Headlights → dark theme | ✅ in place, also darkens zlink/Android Auto |
| Android Auto tile → zlink | ✅ |
| Debloat | ✅ 19 apps disabled (`./debloat.sh restore` to undo) |
| Music dock button | ✅ acquires BT audio + opens the BT music app |
| BT audio restore after leaving Home | 🟡 **Installed, needs on-car retest.** Uses the real source signal (TwSource); re-acquires once on return if the phone was playing |
| Automated tests | ✅ 60 Robolectric/JVM tests (`cd launcher && ./gradlew testDebugUnitTest`) |

## Decided / known limits
- Night backlight dimming: done by the MCU when the headlight wire is on; both stored brightness levels are already max.
  Brightness mode "Day" stops dimming but also hides the headlight signal (panel_lights stays 0) → kept on **Auto**.
  Only other control is factory "Backlight current regulation" (LED current) → deliberately not touched.
- No album art and no seeking over this unit's Bluetooth (the vendor BT app gets neither). Placeholder art stays.
- Progress bar is display-only.

## First thing next session
1. Retest BT restore: play music → Music button → switch tabs → Home (and once with Back). Audio should return within ~1 s.
   Watch: `adb logcat -s CarHome` (lines `audio source A -> B`, `restoring Bluetooth audio`, `play pressed: ...`).
2. If acquire ever PAUSES an already-playing phone (blind toggle): after acquire wait ~1 s, check `BtLog.playing()`, send one `pp` only if needed.
3. Known: Back inside the BT app releases the source and pauses the phone (vendor behaviour). Prefer Home.

## Optional ideas for later
- Cold-start check: car fully off → on; Car Home appears, ADB returns on 5555, music card works immediately.
- Remove the two debug log lines in HomeActivity (play decision / media key route) once stable.
- Album art via online lookup (title+artist) over the phone hotspot; layout idea: clock on the AA tile, art fills the right panel.
- "Auto by sunset" theme mode (only needed if Brightness mode is ever set to Day).
- Dimmer night version of the accent tile (UI review suggestion U9).

## Safety (unchanged)
- **NEVER type plain `7890`** in Settings → System → Restore factory settings (it's the reset password). Never `11223344` there either.
- Don't touch: Select models, CID, Key update, Steering wheel, Reverse mode, Panel/Touch keys, LOGO, Backlight current regulation, /data/tw/misc.
- Revert launcher: `adb shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher`
