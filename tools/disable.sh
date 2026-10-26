#!/usr/bin/env bash
# Disable packages for user 0, one pm call per package, and log them so
# restore.sh can re-enable them.   tools/disable.sh pkg1 [pkg2 ...]
set -u
export MSYS_NO_PATHCONV=1
HOST="${TV_HOST:-$(cat "$(dirname "$0")/../.tv-host" 2>/dev/null)}"
[ -n "$HOST" ] || { echo "Set TV_HOST=<tv ip> or put the IP in .tv-host" >&2; exit 1; }
DEV="$HOST:${TV_PORT:-5555}"
LOG="$(dirname "$0")/../backup/disabled-by-cleanup.txt"

# Android core packages: disabling these can leave the TV unable to boot.
core_re='^(android|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|android\.ext\.(services|shared)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*)$'
providers_ok='^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$'
# Never on this TV: stock CVTE launcher, TV services, fallback home, factory menus.
keep_re='^(com\.cvte\.tv\.launcher\..*|com\.cvte\.tvservice|com\.cvte\.tv\.api\.impl|com\.mediatek\.tv\.agent|com\.example\.user\.myapplication|com\.cvte\.fac\.menu|cn\.cvte\.tv\.factory\.autotest|com\.android\.vending|com\.google\.android\.gms|com\.google\.android\.gsf|com\.android\.inputmethod\.latin|com\.android\.bluetooth|com\.cvte\.tv\.bluetoothguide|com\.android\.inputdevices|com\.cvte\.tv\.androidsetting)$'

for p in "$@"; do
  if [[ "$p" =~ $core_re && ! "$p" =~ $providers_ok ]] || [[ "$p" =~ $keep_re ]]; then
    echo "REFUSED (load-bearing): $p" >&2; exit 2
  fi
done
for p in "$@"; do
  out=$(adb -s "$DEV" shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r')
  echo "$p -> $out"
  grep -qx "$p" "$LOG" 2>/dev/null || echo "$p" >> "$LOG"
done
echo "--- now disabled on TV:"
adb -s "$DEV" shell pm list packages -d | tr -d '\r' | sed 's/^package://' | sort
