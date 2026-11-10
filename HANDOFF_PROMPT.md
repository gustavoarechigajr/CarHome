# Handoff prompt: clean front-end for a Chinese Android car head unit

Paste everything below the line into a new Claude Code session. Attach photos of the unit's About/System-info screens if you have them.

---

You're helping me upgrade a cheap Chinese Android car head unit. The goal is a **fast, clean front-end with full Android Auto functionality**: a custom launcher app that starts at boot, has a big "Start Android Auto" button, a Now-playing card and a small dock, does automatic **light/dark mode that follows the headlights** with a manual override, and **debloats** vendor junk. Android Auto itself stays rendered by the phone. On these units the head-unit side is a vendor receiver app (often **zlink** / `com.zjinnova.zlink`, sometimes Carlinkit/AutoKit/TLink). Our launcher wraps it and doesn't replace it.

A previous unit (Youmecity / Topway-Dofun firmware, Allwinner A133, Android 10) was done successfully. The method and lessons are below. **Assume this unit may differ**, so verify every step instead of copying values.

## Hard safety rules (read first)
1. **Never flash firmware** or write to bootloader/misc partitions (`/data/tw/misc`, `sunxi_flash`, `fdt`). Wrong images brick these boards or kill radio/CAN/steering keys.
2. **Factory-reset traps:** on Topway/Dofun units, Settings → System → "Restore factory settings" has a password box where **plain `7890` IS THE RESET PASSWORD** (it wiped the last unit). Hidden debug codes there are `7890`+more digits. Before giving me any code for any box, list **every** value that would trigger a destructive path, and tell me to type the exact full string.
3. Before changing anything: photograph every factory/"Fábrica" settings page, and record the MCU version, build/system version and car model/CAN settings (the restore reference).
4. Only make reversible changes: `pm disable-user` (undo with `pm enable`), setprop of non-persistent properties, installing our own app. Back up APKs and settings first.
5. Don't touch car-core apps or settings: car model/CAN selection, steering-wheel learning, reverse camera, key mapping, MCU/tuner, logo.
6. Spec screens lie. The "6 GB / Android 14" on the last unit came from a vendor config file. Trust only `getprop`, `/proc/meminfo` and `/proc/cpuinfo`.

## Phase 1: identify the hardware
- Ask me for photos of About/System info: platform/CPU name, MCU version, system version, "Android version".
- Common platforms: **Topway/Dofun** (`com.tw.*`, `com.dofun.*` packages, `/system_tw`), **FYT** (`com.syu.*`, UIS7862/8581, big XDA community, has root tools), **MTC/Hal9k** (`com.microntek.*`), **MediaTek 8227L** budget (often "8227L_Demo").
- Real Android version = API level (`ro.build.version.sdk`): 29=10, 30=11, 31/32=12, 33=13, 34=14. "Wireless debugging" missing from Developer options means Android ≤10.

## Phase 2: get ADB (the hard part on locked units)
Try these in order. Stop at the first that works.
1. **Developer options:** About → tap Build number 7×. Vendor password gates are common. Topway: `7890` (or `7890`+current hour, 24h). Factory menus: `123456`, `8888`, `1617`, `3368`, `0000`, `1234`. Turn on USB debugging. If there's a network-ADB / "ADB over Wi-Fi" toggle, use it.
2. **Scan the unit's open ports from the PC** (it must be on the same reachable network). On the last unit adbd listened on a **non-standard TCP port (5100)** as soon as USB debugging was on. Probe each open port with an ADB CNXN handshake: a reply of `AUTH` means it's adbd.
3. **The auth-prompt trap:** the "Allow USB debugging?" dialog may **flash and close instantly**. That's AOSP `UsbDebuggingActivity`: it closes itself when `service.adb.tcp.port`=0 and no USB device-mode connection exists (head-unit USB ports are host-only, so a PC/Mac cable shows nothing). Fix: get `service.adb.tcp.port` set to non-zero, then reconnect. The prompt then stays up; tick "Always allow".
4. **Ways to run `setprop service.adb.tcp.port 5555` on the unit without ADB:**
   - Install an **SSH server app** on the unit (worked: "SSH Server" by Banana Studio, `net.xnano.android.sshserver`; SimpleSSHD is an alternative). Give the user shell access to a readable home folder (e.g. `/sdcard`). SSH in (that server needs an interactive shell: pipe commands into `ssh -tt`), then run `setprop service.adb.tcp.port 5555; setprop ctl.restart adbd`. This only works if **SELinux is permissive** (check `getenforce` or `cat /sys/fs/selinux/enforce`). Cheap Allwinner/Topway builds often are.
   - Topway Engineer Mode (Factory → Engineer Mode → Debug&Log → ADB shell) exists on some firmwares.
   - Topway hidden USB host/device switch: Restore-factory box, code `7890` + DeviceID(hex) + HH + MM. The DeviceID wasn't found on the last unit. **Low priority, and dangerous box (see rule 2).**
5. After authorizing, persist it: `setprop persist.adb.tcp.port 5555`. Test reconnecting without a prompt. Write a `connect.sh`.
6. **Network gotchas:** if the unit is on an isolated/IoT VLAN, routing may be asymmetric. Bind to the interface that works (`curl --interface`, or a `socat` relay to 127.0.0.1 for adb).

Useful no-ADB helpers: **X-plore File Manager** (paid "premium" unlocks root-path browsing over its WiFi server/FTP) to read `/system_tw`, `/sys/class/*`, the vendor APKs and `/sdcard` configs, and **Activity Launcher** to open hidden activities.

## Phase 3: back up and investigate (read-only)
- `pm list packages -f/-d/-3`, `settings list system|secure|global`, `getprop`, `dumpsys meminfo`, `top`, `dumpsys media_session`. Default HOME: `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`.
- Pull APKs: the AA receiver (zlink), the stock launcher, car/CAN service, Bluetooth app, vendor service.
- Decompile with Android SDK `dexdump -d` plus grep (or JADX if available). Stock launchers may be packed ("jiagu"), so use runtime observation instead.

## Phase 4: find the platform hooks the launcher needs
1. **Headlight / day-night signal:** snapshot `settings`, `getprop`, `/sys/class/*` (e.g. `/sys/class/twctrl/*`) and `dumpsys uimode`. Have me turn on the headlights, snapshot again and diff. Watch logcat too.
   - Last unit: `/sys/class/twctrl/panel_lights` 0→1. The vendor service (`com.tw.service`) only sets Android night mode when it receives the broadcast
     `cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED` with extras `isNight` (bool), `curThemeSupportsDayNight=true`, `curLaunchMode="follow_headlamp"`, **normally sent by the stock launcher**. Replacing the launcher breaks auto night mode unless **our app watches the headlight node and sends that broadcast itself**. That switches system night mode, which also darkens Android Auto/zlink.
2. **Bluetooth music (the hardest part last time; check every point on the new unit).** On the Topway unit:
   - **Audio source:** the MCU routes one source at a time. BT audio stays muted (`gocsdk`/blink log `sbc_process_decode mute(1)`) until source 0x08 is requested.
     Working, invisible: broadcast `net.easyconn.a2dp.acquire` (off: `net.easyconn.a2dp.release`), handled by com.tw.bt. Don't flash the BT screen.
   - **Buttons:** com.tw.bt **ignores** keys sent via MediaSession / `AudioManager.dispatchMediaKeyEvent` (delivered, no effect). Only the unit's virtual keys work.
     Working: broadcast `com.unisound.unicar.broadcast.action` with extra `type` = `pp` (toggle) / `next` / `prev`, which com.tw.service turns into the steering-wheel key.
     Test first from adb: `am broadcast -a com.unisound.unicar.broadcast.action --es type pp`. `input keyevent 127` also works (shell only).
   - **Now playing:** com.tw.bt leaks a new "BTModel" MediaSession every time it opens; stale ones keep old tracks/positions, so don't trust sessions.
     Working: send `com.unisound.intent.action.GET_ID3_INFO` (setPackage com.tw.bt), then receive `com.unisound.intent.action.SEND_ID3_INFO` ~1/s
     (extras musicTitle, musicArtist, musicProgress, musicDuration in ms). Backup: READ_LOGS (`pm grant`) + logcat BTModel `RETURN_ID3`/`RETURN_MS`.
   - No seek, no cover art over that BT link.
   - **Current audio source (don't guess from logs):** xt `CommandService` binder callback pushes `Source_recieve`/`SourceValue` on change;
     request with tx 67 `{project:"system", action:"source_request"}`; fallback `getTWSource()` = raw tx 166 on `activity_task`. See `launcher/.../TwSource.kt`.
   - **Vendor BT app traps:** Back in its activity releases the source and pauses the phone (Home doesn't); entering source 8 sends a BLIND
     play/pause toggle. So: one acquire, never a `pp` next to it; send `pp` only when BT already is the source.
   - **How these were found:** dexdump of com.tw.bt / com.tw.service / com.tw.service.xt + framework.jar `android.tw.john.TWUtil`, then each candidate
     proven from adb on the unit before coding it. Other APIs found but unused: xt `CommandService` binder (btPlayMusic=23, btNext=25…),
     TWUtil `write(0x9e11,0xc0,8)` (request BT source), `0x301` source reports, `0x203` volume/mute. Notes: research/vendor/ on the old project.
3. **AA receiver launch intent**, e.g. `com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity`. zlink uses the standard AA wireless
   protocol and holds privileged Wi-Fi/tethering permissions; don't try to replace it.
4. Vendor apps for the dock: music, BT phone, radio, car settings.
5. **Night dimming:** the MCU dims the backlight when the headlight wire is on. The unit's Brightness mode "Day" stops it but also hides the
   headlight signal (`panel_lights` stays 0). Don't touch factory "Backlight current regulation".

## Phase 5: debloat (ask me before applying)
Rank by RSS (`ps -A -o RSS,NAME`). Typical safe candidates: vendor weather/market/voice assistant, OTA updaters (`*fota*`; this also protects our changes), Huawei HiCar trio, bug reporter, preinstalled YouTube/Facebook/Sygic/GPS-test, NFC, screen-mirroring, Chrome. Some vendor apps are protected (SecurityException), which is fine. Keep Google Play services. Use a list file plus `debloat.sh disable|restore`. **Note:** `adb shell` inside a `while read` loop eats stdin, so add `</dev/null`. Warn about side effects, e.g. disabling the default nav app.

## Phase 6: build the launcher — start from Car Home
**Reuse the finished app** from the first unit instead of starting over: `/mnt/8a0d3363-aa14-4b24-ab6a-36c7252187cf/Projects/AndroidAuto/launcher/`
(Kotlin, theme system with the "Glacier" theme, Archivo font, 56 tests). Copy it into the new project, then adapt only what differs:
package names in `Launch.kt` (`Apps.*`), the BT mechanisms in `BtControl.kt` / `BtSource.kt` / `BtLog.kt`, the headlight node and broadcast in
`NightSync.kt`, and the content-area size in the layout + tests. Read its `README.md` and `REVIEW.md` first (bugs already found and fixed).
Testing without the car: Robolectric (`./gradlew testDebugUnitTest`) renders screenshots at the unit's exact size; no emulator/KVM needed.
`car_setup.sh` does install + permissions + screenshot in one run.

General rules it already follows (keep them if writing new code):
- minSdk = the unit's API level, target the same. Plain framework Views (light on weak SoCs). Day/night via `values` / `values-night` colors. Manual override via `applyOverrideConfiguration(uiMode)` in `attachBaseContext`.
- Manifest: HOME + DEFAULT + LAUNCHER filter, `singleTask`, `stateNotNeeded`, landscape. Swallow Back.
- Now playing for non-BT players: `MediaSessionManager.getActiveSessions` with a dummy `NotificationListenerService` (grant via `adb shell cmd notification allow_listener <pkg>/<cls>`). Vendor BT apps fill missing info with words like "Not Provided"; treat those as empty.
- Keep every media decision in a pure, unit-tested class (`MediaLogic`), and verify each vendor quirk on the unit from adb before coding it.
- A small always-on component (foreground service started at boot plus from the launcher) that watches the headlight node and sends the vendor day/night broadcast. Combine it with the manual override.
- Size the layout for the real content area (e.g. 1280×662 dp under the vendor status bar). Touch targets ≥ 60 dp.
- Install with `adb install -r`, test it while it's NOT the default, then `cmd package set-home-activity <pkg>/<activity>`. Keep the stock launcher installed. Document the revert.

## Deliverables
Keep these in the project folder: `UNIT_INFO.md` (verified facts, codes, restore info, dangers), `NEXT_STEPS.md`, `connect.sh`, `identify.sh`, `debloat_list.txt` + `debloat.sh`, `backup-*/`, `launcher/` (the Gradle project), and photos. Save durable facts to memory.

## How I'd like you to work
- Verify before claiming. Report what failed. Keep things reversible. Ask before anything destructive or outward-facing.
- Prefer finding the vendor's real mechanism (decompile, diff state, logcat) over guessing codes.
- When the car must be on (battery), batch the on-car steps. Do research and builds while it's off.
- ADB over the car's Wi-Fi drops often: wrap every adb call in `timeout`, retry connects, and keep commands short.
- Keep pulled vendor APKs and dexdumps in a permanent project folder (`research/vendor/`), not a temp/scratch folder.
