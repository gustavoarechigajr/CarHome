#!/usr/bin/env bash
# Reconnect ADB to the head unit.
# The unit sits on the IoT VLAN (10.0.40.x). It's only reachable from this PC via the Wi-Fi interface,
# because the wired route through 10.0.0.2 doesn't return. So a local socat relay bound to the Wi-Fi IP is used.
# Usage: ./connect.sh [unit_ip]     (default 10.0.40.20)
set -u
UNIT_IP="${1:-10.0.40.20}"
UNIT_PORT=5555
LOCAL_PORT=15555
WIFI_IF="${WIFI_IF:-wlp5s0}"   # override: WIFI_IF=wlan0 ./connect.sh
WIFI_IP=$(ip -4 -o addr show "$WIFI_IF" | awk '{print $4}' | cut -d/ -f1)

[ -z "$WIFI_IP" ] && { echo "No IPv4 on $WIFI_IF (is the PC on the same Wi-Fi as the unit?)"; exit 1; }

if ! pgrep -f "socat TCP-LISTEN:$LOCAL_PORT" >/dev/null; then
  setsid nohup socat TCP-LISTEN:$LOCAL_PORT,bind=127.0.0.1,reuseaddr,fork \
    TCP:"$UNIT_IP":$UNIT_PORT,bind="$WIFI_IP" >/dev/null 2>&1 < /dev/null &
  sleep 1
  echo "relay: 127.0.0.1:$LOCAL_PORT -> $UNIT_IP:$UNIT_PORT (via $WIFI_IF $WIFI_IP)"
fi

adb connect 127.0.0.1:$LOCAL_PORT
sleep 2
adb devices -l
echo
echo "Use: adb -s 127.0.0.1:$LOCAL_PORT shell ..."
echo "If it fails after a unit reboot: open the SSH Server app on the unit, or toggle USB debugging,"
echo "then from SSH run: setprop service.adb.tcp.port 5555; setprop ctl.restart adbd"
