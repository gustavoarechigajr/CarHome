# Head unit — factory reference (recorded 2026-11-09)

Restore reference. Keep these before modifying anything.

| Field | Value |
|---|---|
| Platform | T133 |
| CPU | A133 → Allwinner A133/T133 (4× Cortex-A53 @ ~1.6 GHz, PowerVR GE8300). Chip maximum is 4 GB RAM |
| Audio processor | PT2313 |
| FM radio | QN8035 |
| Bluetooth (claimed) | 5.4 |
| Boot version | GB0xLH0_M8_K0909S4936-T08_90_240320 |
| MCU version | T13.1.1-100-10-A8939D-251105(8) |
| App version | V13_20260305.110156_APP-THEME1 |
| System version | V13.2.1_20260305.111143_OS-EN |
| CarSetting version | master.20260304_1631 |
| Resolution | 1280x720 |
| Claimed Android / RAM / storage | 14 / 6 GB / 64 GB (6 GB is impossible on an A133; needs verification) |
| CAN version | Unknown (no CAN box detected) |

Photos: `photos/about-1-hardware.jpg`, `photos/about-2-system.jpg`
Developer/factory password: 7890 (8288 does NOT work)
TODO: photograph every Fábrica (factory settings) page.

## Firmware family: Topway (com.tw.* packages, /data/tw/ themes)
Known Topway codes (community-reported):
- Factory mode: 8888 / Advanced: 123456 / Developer: 7890 + current hour (24h), e.g. 789015
- Android Developer Options: 7890 (confirmed)
ADB: adbd listens on TCP 5100 when USB debugging is on, but the RSA prompt auto-closes because service.adb.tcp.port=0 and USB is host-only.
Fix (Topway guide): Factory (123456) -> Engineer Mode -> Debug&Log -> ADB shell -> `setprop service.adb.tcp.port 5555` -> Start; toggle USB debugging; adb connect <ip>:5555; Always allow.
X-plore: WiFi server :1111 (free = top-level only), FTP :2222 (internal storage only).

## Verified facts (2026-11-09, via X-plore premium filesystem access)
- **Real Android: 10 (API 29)**: /system/etc/llndk.libraries.29.txt, ld.config.29.txt
- **Android Go edition** (product/priv-app/GmsCoreGo) → RAM almost certainly ≤ 2 GB
- SoC: Allwinner A133 (init.recovery.sun50iw10p1.rc), 32-bit userspace (init.zygote32.rc)
- **Fake specs are configured**: /sdcard/.VersionSettings has MEMORY_INFO_TEXT=6GB, PLATFORMDISPLAYSETTINGS_TEXT=14
- Vendor partition: /system_tw (priv-app: CarSetting, Settings, SystemUI, TWCore, com.tw.service_a5a4, Launcher2, Fota, Fota_app, DoFunPlay, DoFunWeather, HiViewLite, Hisight, HwNearby, HwDMSDP, Zt360, DVR, CarMate, MS912X, TDService, ...)
- Autostart app: com.zjinnova.zlink (Tlink5 / wireless CarPlay+AA). Default nav: com.sygic.incar
- /data/tw/misc (bootloader message area) contains `fdt set /soc/car_reverse twconsole <1>`: **DO NOT MODIFY**
- Factory test report (2026-03-27): dram=1, wifi=-50
- Factory settings password: **123456** (confirmed). 7890+hour did NOT open the car dev menu.
- Factory → Backup Apk exports only user-installed apps to /sdcard/.apk

## Hidden debug codes (decoded from CarSetting.apk, com.dofun.carsetting.fragment.SystemFragment.restoreFactorySettings)
Entered in: Settings → System → "Restore factory settings" password box.
Device counts as "T100" platform (system version starts with V13). Device ID = /sys/class/twctrl/mcuoem = 0x100 → "100".
- `7890` + `100` + HH + MM (unit clock, 24h, zero-padded): **USB host/device dialog**. "device" runs otg_role usb_device on usbc0 (physical USB → device mode → PC/Mac ADB)
- `7890100`: "System update now?" dialog. **Cancel it.**
- Any input starting with 7890 and longer than 4 chars never triggers a reset (wrong codes only show a toast)
- **NEVER enter plain `7890` (default RESET password; it triggered a factory reset on 2026-11-09)**, any other reset password (wipes data via `rmdata`), or `11223344` (writes boot0 DRAM flag)
- Developer-options check (checkDeveloperPassword): T100 → `7890`+HH. Plain `7890` otherwise / or custom per-preference password

## ADB access: WORKING (2026-11-09)
- Real specs via ADB: Android 10 / API 29, **4 GB RAM** (MemTotal 4067520 kB), 4× Cortex-A53 (0xd03), A133 "ceres-b6", 1280x720 @160dpi, 183 packages
- Build: Allwinner/ceres_b6/ceres-b6:10/QP1A.191105.004/eng.hy.20260305.110106:user/release-keys (test-keys)
- **SELinux: Permissive** → apps can setprop. ro.adb.secure=1
- How it was unlocked: "SSH Server" app (Banana Studio) on unit, port 2222, user car. Ran
  `setprop service.adb.tcp.port 5555; setprop ctl.restart adbd`, then the auth prompt stayed up → Always allow.
- Persisted: `persist.adb.tcp.port=5555`. This PC's adb key is authorized (reconnects without a prompt).
- Reconnect: `./connect.sh [ip]` (socat relay via wlp5s0, since the IoT VLAN is only reachable over Wi-Fi). Full report: report-20261109-165730/

## Day/night (headlights) signal, verified 2026-11-09
Headlights on → MCU illumination input (twutil2 `__Video_IlluminDet 1`) → TWService (mode `follow_headlamp`) sets:
- Android system night mode: `secure ui_night_mode=2`, `dumpsys uimode mNightMode=2`, every app gets a `night` config change
- `persist.tw.theme.mode=night` (day → `day`), `persist.sys.theme=2`, `/sys/class/twctrl/panel_lights=1`
→ Our launcher's "Auto" = follow the standard Android DayNight (Configuration.UI_MODE_NIGHT_YES). No vendor API needed.
Stock launcher = com.dofun.variety (/system_tw/priv-app/Launcher2), packed with jiagu (not decompilable statically).
zlink launch: com.zjinnova.zlink/com.zjinnova.android.zlink.features.main.MainActivity

## Debloat applied (2026-11-09)
`./debloat.sh disable` with debloat_list.txt: 19 packages disabled (pm disable-user). Undo all: `./debloat.sh restore`, or a single one with `adb shell pm enable <pkg>`.
com.tw.dvr and cn.cardoor.zt360 are protected (SecurityException) and stay enabled (not running anyway).
Note: Sygic was the default "navi" app (/data/tw/navi_name) and is now disabled → the nav key/shortcut may need to point to Google Maps.

## Launcher integration findings (2026-11-09, evening)
- **Auto night mode needs our launcher to send the vendor broadcast.** com.tw.service only calls UiModeManager.setNightMode on
  `cn.cardoor.variety.intent.action.DAY_NIGHT_CHANGED` (extras: isNight bool, curThemeSupportsDayNight=true, curLaunchMode="follow_headlamp"),
  which the stock launcher (com.dofun.variety) normally sends. Verified: `am broadcast` with isNight=true → mNightMode=2.
  Headlight input: /sys/class/twctrl/panel_lights (1=on). Logcat: twutil2 Illumin_IO / __Video_IlluminDet.
- **BT audio is muted until the BT app's music screen is focused**: gocsdk "sbc_process_decode mute(1)". com.tw.bt requests source 0x08 (BT music)
  only while com.tw.bt/.ATBluetoothActivity has window focus (and not while Android Auto is connected). It remembers the last tab.
  The launcher must open/bring up that activity to start BT music. The brief-open-then-return trick is untested (no audio was streaming during the test).

## zlink (com.zjinnova.zlink 5.4.101) analysis — 2026-11-09
APK: backup-20261109-1748/apks/user/com.zjinnova.zlink.apk (sha256 68218254...f0e0b). Java is PACKED (tiny classes.dex + o0oooOO0ooOo.dat), so the Kotlin/Java glue isn't statically readable; the native libs + asset YAMLs are plaintext.
- **Board profile:** assets/cnf_vendor_dingwei.yaml maps platform `ceres` (our ceres-b6) → config `cnf_dingwei-t100`, mfi_bus_num1=7, mfi_bus_num2=1.
- **USB OTG switch (important, non-destructive):**
  host:   `echo "usb_host"   > /sys/devices/platform/soc/usbc0/otg_role`
  device: `echo "usb_device" > /sys/devices/platform/soc/usbc0/otg_role`
  This is the device-mode switch we wanted for Mac↔unit USB ADB / wired CarPlay. Needs write access to that sysfs node (root or the right context; app writes it via a privileged helper).
- **Wireless AA = standard Google AA Wireless protocol** (native libs: `AA/AA-proto/WifiStartRequest.pb-c.c`, strings `[AA]Android Auto rfcomm open success`, `AA_WIFI_INFO_REQUEST/START_RESPONSE/VERSION_RESPONSE/CONNECT_STATUS`, `bt_cmd_rfcomm_start`). i.e. BT RFCOMM handshake → head unit hosts a Wi-Fi AP → phone joins → TCP projection stream. Same protocol AAWireless / open-headunit "Native mode" / the AA dev "head unit server" use.
- **zlink is a privileged/system app:** holds TETHER_PRIVILEGED, NETWORK_SETTINGS, OVERRIDE_WIFI_CONFIG, LOCAL_MAC_ADDRESS, BLUETOOTH_PRIVILEGED, READ_PRIVILEGED_PHONE_STATE, WRITE_SETTINGS. That's how it brings up the SoftAP/hotspot for wireless. A normal (non-privileged) app like open-headunit cannot start that AP itself.
- Also bundles AirPlay/CarPlay (libAirPlay) and Huawei HiCar (libhicar, libdmsdp*, libnearby) stacks. CarPlay uses an MFi path (mfi_bus_num).

### Implication for open-headunit
- open-headunit can't replace zlink's wireless bring-up on its own: it lacks the privileged Wi-Fi-AP/tethering permissions zlink has. Its WIRED USB mode is the clean test (switch the port to device mode with the command above, or use the unit's own port).
- Interop angle: open-headunit's docs list a "ZLink / ZJ socket" transport — reusing zlink's already-established transport rather than re-hosting the AP. Worth testing wired first, then that transport, before investing in wireless.

## Car Home on the unit — verified 2026-11-10
- **Default home screen: Car Home** (`cmd package set-home-activity dev.gus.carhome/.HomeActivity`).
  Revert: `cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher`
- Bluetooth music, all verified on the unit:
  - Buttons: broadcast `com.unisound.unicar.broadcast.action` extra `type`=`pp|next|prev` → com.tw.service sends the unit's own virtual key (same path as steering wheel). MediaSession/AudioManager keys are delivered but IGNORED by com.tw.bt.
  - Audio source: broadcast `net.easyconn.a2dp.acquire` (on) / `net.easyconn.a2dp.release` (off), no UI shown.
  - Now playing: send `com.unisound.intent.action.GET_ID3_INFO` → com.tw.bt broadcasts `com.unisound.intent.action.SEND_ID3_INFO` ~1/s (musicTitle, musicArtist, musicProgress, musicDuration ms). Its MediaSessions are unreliable (a new one leaked per open, stale positions).
  - Log backup (needs `pm grant dev.gus.carhome android.permission.READ_LOGS`): BTModel RETURN_ID3 / RETURN_MS lines, blink `mute(1)` while muted.
  - No seek and no cover art over this Bluetooth link.
- Headlights → dark theme in place: works. Backlight dimming at night is the unit's **Brightness mode** (Auto/Day/Night, settings key ip_brightness_mode): set to Day to stop dimming.
- Android Auto tile → zlink: works.
- Vendor research (permanent): research/vendor/ (com.tw.bt, com.tw.service, com.tw.service.xt, com.dofun.carsetting APKs + dexdump; twutil_dump.txt).

## Audio source & Music button — added 2026-11-10 (late), needs on-car retest
- **Current source, read directly:** bind `com.tw.service.xt.CommandService.Bind` (package com.tw.service.xt, exported, no permission).
  Register a raw Binder callback with tx 1 (token `com.tw.service.xt.aidl.ITWCommandAidl`); callback token `...ITWCommandCallbackAidl`.
  xt pushes callback tx 8 `onExtendedInterface(Bundle{action:"Source_recieve" (sic), SourceValue:int})` on every change.
  Ask for the current value: tx 67 `extendedInterface(Bundle{project:"system", action:"source_request"})`.
  Fallback: `ServiceManager.getService("activity_task")` raw tx 166 = `getTWSource()`. Values: 8 BT music, 3 car music app, 1 radio, 0 none.
  Verified on the unit: Car Home logged `audio source -1 -> 8`. Log line from the system: TWService `0X0301:<new> mSource:<old>`.
- **com.tw.bt behaviour (from its code):**
  - Back inside the BT app ALWAYS releases the BT source (onBackPressed → s(false)) and pauses the phone. Leaving with Home does not.
  - When the source leaves 8 while playing, it pauses the phone; when the source ENTERS 8 it sends a blind play/pause toggle.
    So `acquire` usually resumes a phone it paused, but could pause one that was already playing. Never send `pp` close to an acquire.
  - `acquire` needs no extras and has no guards (two receivers → can produce two writes). SEND_ID3_INFO is not gated on the source.
- **Car Home now:** Music dock button = acquire + open com.tw.bt. On returning Home, if the phone was PLAYING on BT when Home was left
  and the source is no longer 8, it acquires once (once per return). Play button: other source active → acquire only (the toggle starts
  playback); BT is the source → `pp` only; source unknown → old log/mute-based logic. Mute-based re-acquire only runs when the source is unknown.
- Tip: leave the BT music app with **Home**, not Back.
