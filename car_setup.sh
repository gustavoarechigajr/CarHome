#!/usr/bin/env bash
# One-shot Car Home install + setup on the head unit. Every step has a timeout and retries,
# because ADB over the IoT Wi-Fi drops often. Safe to re-run: each step is idempotent.
# Usage: ./car_setup.sh [unit_ip]   (default 10.0.40.20)
set -u
cd "$(dirname "$0")"
UNIT_IP="${1:-10.0.40.20}"
A="adb -s 127.0.0.1:15555"
APK=launcher/app/build/outputs/apk/debug/app-debug.apk
PKG=dev.gus.carhome

ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; }
try()  { # try <tries> <timeout_s> <cmd...>
  local n=$1 t=$2; shift 2
  for i in $(seq 1 "$n"); do timeout "$t" "$@" </dev/null && return 0; sleep 2; done; return 1; }
sh_()  { timeout 15 $A shell "$@" </dev/null 2>/dev/null | tr -d '\r'; }

echo "1. Connect"
./connect.sh "$UNIT_IP" >/dev/null 2>&1
for i in 1 2 3 4; do
  [ "$(sh_ echo alive)" = alive ] && break
  adb disconnect 127.0.0.1:15555 >/dev/null 2>&1; sleep 2; adb connect 127.0.0.1:15555 >/dev/null 2>&1; sleep 3
done
[ "$(sh_ echo alive)" = alive ] && ok "ADB connected" || { bad "ADB not reachable. On the unit: SSH Server app → setprop service.adb.tcp.port 5555; setprop ctl.restart adbd"; exit 1; }

echo "2. Install Car Home"
try 3 180 $A install -r "$APK" >/dev/null && ok "installed $(stat -c %y "$APK" | cut -c1-16)" || { bad "install failed"; exit 1; }

echo "3. Permissions"
sh_ cmd notification allow_listener $PKG/$PKG.MediaListener
sh_ pm grant $PKG android.permission.READ_LOGS
sh_ cmd notification list_listeners 2>/dev/null | grep -q $PKG || true


sleep 2
sh_ dumpsys notification | grep -q "$PKG/$PKG.MediaListener" && ok "media access granted" || bad "media access not confirmed"

echo "4. Open Car Home (not the default home screen yet)"
sh_ am start -n $PKG/.HomeActivity >/dev/null && ok "opened"
sleep 3
timeout 20 $A exec-out screencap -p > car_home_now.png 2>/dev/null && ok "screenshot: car_home_now.png"

echo "5. Quick health"
crash=$(timeout 20 $A logcat -d -t 400 2>/dev/null | grep -E "FATAL EXCEPTION|AndroidRuntime.*$PKG" | head -3)
[ -z "$crash" ] && ok "no crash in log" || { bad "crash:"; echo "$crash"; }
echo "   headlights: $(sh_ cat /sys/class/twctrl/panel_lights)   system night: $(sh_ dumpsys uimode | grep -o 'mNightMode=[0-9] ([a-z]*)')"
echo
echo "Make default home (after testing):  $A shell cmd package set-home-activity $PKG/.HomeActivity"
echo "Revert to stock:                     $A shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher"
