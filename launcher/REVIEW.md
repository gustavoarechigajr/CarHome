# Code review — Car Home (Glacier build), 2026-11-10

Resume point for an interrupted session: work down the checklist; each item says its state.
Cannot run the app here (no KVM for the emulator, car off) → static review + build + Android lint only.
Runtime checks go to NEXT_STEPS.md (on-car).

## Checklist
- [x] 1. Read every source file (HomeActivity, AppsActivity, BtSource, BtTabClicker, NightSync, CarHomeApp,
        Launch, MediaListener, ThemePrefs, theme/CarTheme, theme/ThemeKit, RingsView, ProgressLine, manifest, layouts)
- [x] 2. Fix confirmed bugs (B1–B3 + agent findings L*/U*)
- [x] 3. Clean build (`./gradlew assembleDebug`): OK
- [x] 4. Lint: 0 errors. To fix: BootReceiver action check, ProgressLine allocs in onDraw, unused v1 drawables/colors
- [x] 5. Layout fit: verified by UI agent arithmetic + HomeActivityTest.everythingFitsOnScreen
- [x] 6. Car-only checklist written to NEXT_STEPS.md step 5

## Findings
| # | File | Bug | Severity | Status |
|---|---|---|---|---|
| B1 | HomeActivity.renderMedia | Album-art placeholder never drawn on first render: `lastArt` starts null and the first bitmap is null, so `bmp !== lastArt` is false and the art box stays empty until a real cover arrives | Visible | fixing |
| B2 | ThemeKit.pressable + prev/next | RippleDrawable with no mask is masked by its content; prev/next content is a TRANSPARENT oval, so the touch ripple is invisible (no press feedback) | Minor | fixing |
| B3 | BtSource.active | Once set, stays true forever. If the source changes elsewhere (radio, USB, steering-wheel MODE key, the BT dialer), Play won't re-switch to Bluetooth → silent playback | Functional | fixing: mark lost whenever Home is left for anything other than our own BT switch |
| B4 | HomeActivity.pickController | "Phone connected" status and Phone dot assume com.tw.bt only has sessions while a phone is connected. Unverified: com.tw.bt may register sessions at boot with no phone | Unverified | on-car check (NEXT_STEPS) |

## Testing (no emulator: KVM off — kvm_amd module not loaded; needs `sudo modprobe kvm_amd`, may conflict with VirtualBox)
Robolectric (Android 10 framework on the JVM) at 1280x662dp mdpi:
- app/src/test/.../HomeActivityTest.kt — launch day/night + screenshots (app/build/screens), layout fit, buttons, theme cycle, night-sync broadcasts, B1/B3 regressions
- app/src/test/.../MediaLogicTest.kt — every Play/Prev/Next/source/session-pick/progress decision (pure logic, MediaLogic.kt)
Run: `./gradlew testDebugUnitTest` → report app/build/reports/tests/testDebugUnitTest/index.html, log build/tests.log
- [x] 7. HomeActivity wired to MediaLogic
- [x] 8. Robolectric media/BT flow tests (MediaFlowTest): fake sessions, dispatched key events, BtSource switch+return timing, BtTabClicker click
- [x] 9. Two review agents: logic (media/BT/night) and UI (theme/layout) — merge findings
- [x] 10. Car-only checklist (sound actually unmutes, steering keys, zlink) → NEXT_STEPS.md

## Agent findings (2026-11-10)
### Logic reviewer — FIXED in code + covered by MediaFlowTest
- L1 recreate/night relaunch marked BT lost → configChanges="uiMode" + onConfigurationChanged; onPause ignores isChangingConfigurations and our own app drawer
- L2 BtSource hold timer not cancellable → cancellable; homeResumed() stops the pending return
- L3 no-helper path never set active → set active (user is in BT app)
- L4 Settings intents could crash → safeStart()
- L5 media keys went to the system media-button app, not the shown session → controller.dispatchMediaButtonEvent
- L6 NightSync retried forever → send on change + MAX_RETRIES=3
- L7 double recreate → in-place theme switch on uiMode change
- L8 session re-picked on playback change → refreshSessions()
- L9 active set even if tab never tapped → BtTabClicker reports tabClicked; active = clicked
- L12 tab may be missed during window transition → search all windows + 300 ms retry loop
- L15 BT speed 0 while playing froze progress → treated as 1x (MediaLogic.position)
### UI reviewer — layout fits (tile 284/425dp used, panel 377/461, dock 254dp tiles, all touch targets ≥48dp)
- U1 bg_app_item ripple invisible (no mask) · U2 Apps back button lost ripple · U3 art/play shadows clipped (clipChildren)
- U4 dock shadows clipped · U5 night wash too strong → muted text unreadable on bright covers · U6 averageColor samples centre only
- U7 tile/wash missing on first frame (post) · U8 night dock tiles edgeless · U10 phone-dot ring colour · U11 now_row no ripple
- U12 TalkBack never reads phone status · U13 drawer labels not themed · font noCompress
- Not changed (design calls for the user): U9 night accent tile full brightness; RingsView redraw cost → check smoothness on the A133
Status: L* and U* (except U9 design choice) applied; final test run pending
