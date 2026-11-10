#!/usr/bin/env bash
# Read-only hardware identification for the head unit. Changes nothing on the device.
# Usage: ./identify.sh            (USB, or after `adb connect <ip>:<port>`)
set -u
OUT="$(dirname "$0")/report-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
a() { adb shell "$@" 2>&1; }

adb get-state >/dev/null 2>&1 || { echo "No ADB device. Run 'adb devices' and authorize the unit."; exit 1; }

a getprop                     > "$OUT/getprop.txt"
a cat /proc/cpuinfo           > "$OUT/cpuinfo.txt"
a cat /proc/meminfo           > "$OUT/meminfo.txt"
a df -h                       > "$OUT/df.txt"
a uname -a                    > "$OUT/uname.txt"
a wm size                     > "$OUT/display.txt"; a wm density >> "$OUT/display.txt"
a pm list packages -f         > "$OUT/packages.txt"
a dumpsys activity activities | grep -iE 'mResumed|ResumedActivity' > "$OUT/foreground.txt"
a ls /sys/devices/system/cpu/ > "$OUT/cpus.txt"
a cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq >> "$OUT/cpus.txt"
a settings get global window_animation_scale     > "$OUT/anim_scales.txt"
a settings get global transition_animation_scale >> "$OUT/anim_scales.txt"
a settings get global animator_duration_scale    >> "$OUT/anim_scales.txt"

P="$OUT/getprop.txt"
g() { grep -m1 "\[$1\]" "$P" | sed 's/.*\]: \[\(.*\)\]/\1/'; }
{
echo "== SUMMARY =="
echo "Model / board      : $(g ro.product.model) / $(g ro.product.board) / $(g ro.board.platform)"
echo "Hardware           : $(g ro.hardware) | chipname: $(g ro.hardware.chipname) $(g ro.soc.model) $(g ro.soc.manufacturer)"
echo "Android (claimed)  : $(g ro.build.version.release)"
echo "API level (real)   : $(g ro.build.version.sdk)  (28=9, 29=10, 30=11, 31/32=12, 33=13, 34=14)"
echo "Build              : $(g ro.build.display.id) | $(g ro.build.fingerprint)"
echo "CPU cores          : $(grep -c ^processor "$OUT/cpuinfo.txt")"
echo "CPU id lines       : $(grep -iE 'Hardware|model name|CPU part' "$OUT/cpuinfo.txt" | sort -u | tr '\n' ' ')"
echo "RAM (real)         : $(grep MemTotal "$OUT/meminfo.txt")"
echo "Display            : $(tr '\n' ' ' < "$OUT/display.txt")"
echo "Packages           : $(wc -l < "$OUT/packages.txt")"
echo "FYT hints          : $(grep -ciE 'fyt|syu|uis' "$P" "$OUT/packages.txt" | tr '\n' ' ')"
echo "8227L hints        : $(grep -ciE '8227|ac8227|mt8227' "$P" "$OUT/cpuinfo.txt" | tr '\n' ' ')"
echo "MCU-ish props      : $(grep -iE 'mcu' "$P" | tr '\n' ' ')"
} | tee "$OUT/SUMMARY.txt"
echo; echo "Full report in: $OUT"
