# Debloat plan (draft): nothing applied yet

These are based on folder names in /system_tw/priv-app. Package names still need to be confirmed with `pm list packages` once ADB works.
Method: `pm disable-user --user 0 <pkg>`. Reverse it with `pm enable <pkg>`. Nothing gets uninstalled.

## KEEP: car core (disabling can break radio, buttons, reverse camera, MCU, sound)
CarSetting, Settings, SystemUI, TWCore, com.tw.service, com.tw.uart, com.tw.xtservice,
com.tw.keypad_xt, com.tw.radio, com.tw.bt, Bluetooth, com.tw.reverse, com.tw.rightview,
com.tw.carchoose, com.tw.net, com.tw.eq, com.tw.ba, com.tw.color, DeviceFan, DocumentsUI,
zlink (CarPlay/Android Auto), Launcher2 (until a replacement launcher is set as default)

## LIKELY SAFE TO DISABLE (confirm one by one)
| Folder | Probably is | Note |
|---|---|---|
| DoFunPlay | vendor app store / ads | |
| DoFunWeather | weather widget | |
| HiViewLite, Hisight | Huawei HiCar-related | keep if you use HiCar (unlikely) |
| HwNearby, HwDMSDP | Huawei device discovery | background services |
| TDService | vendor telemetry/service | check what it is first |
| BugReport | bug reporter | |
| GooglePartnerSetup | Google partner setup | |
| Fota, Fota_app | firmware OTA updater | disabling stops surprise updates that could undo tweaks |
| CarMate | vendor companion app | |

## ONLY IF UNUSED
| Folder | Feature |
|---|---|
| com.tw.tv | analog/digital TV |
| com.tw.video, com.tw.music | stock players (keep if you play USB media) |
| com.tw.auxin | AUX input |
| DVR | dashcam app (keep if a DVR camera is connected) |
| Zt360 | 360° camera system (keep if installed) |
| MS912X | probably screen-mirroring chip support |
| TWFileExplore | stock file manager |
| SygicInCar, Facebook, YouTube (user apps) | uninstall normally if not wanted |
