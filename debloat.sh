#!/usr/bin/env bash
# Disable packages in debloat_list.txt (reversible). Usage: ./debloat.sh [disable|restore]
A="adb -s 127.0.0.1:15555"; MODE="${1:-disable}"
grep -vE '^\s*(#|$)' "$(dirname "$0")/debloat_list.txt" | while read -r p; do
  if [ "$MODE" = restore ]; then r=$($A shell pm enable "$p" 2>&1 </dev/null); else r=$($A shell pm disable-user --user 0 "$p" 2>&1 </dev/null); fi
  printf "%-36s %s\n" "$p" "$(echo "$r" | tr -d '\r' | tail -1)"
done
