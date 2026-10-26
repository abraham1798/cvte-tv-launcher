#!/usr/bin/env bash
# Undo the cleanup: re-enable every package this cleanup disabled, give Home
# back to the stock CVTE launcher and turn Private DNS off again.
#   bash restore.sh            (TV IP from .tv-host)
#   TV_HOST=1.2.3.4 bash restore.sh
# Packages that were already disabled before the cleanup are listed in
# backup/packages-disabled-before.txt and are left as they were.
set -u
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"
HOST="${TV_HOST:-$(cat .tv-host 2>/dev/null)}"
[ -n "$HOST" ] || { echo "Set TV_HOST=<tv ip> or put the IP in .tv-host" >&2; exit 1; }
DEV="$HOST:${TV_PORT:-5555}"
adb connect "$DEV" >/dev/null

if [ -f backup/disabled-by-cleanup.txt ]; then
  while read -r p; do
    [ -z "$p" ] && continue
    echo -n "$p: "; adb -s "$DEV" shell pm enable "$p" < /dev/null | tr -d '\r'
  done < backup/disabled-by-cleanup.txt
fi

adb -s "$DEV" shell cmd package set-home-activity com.cvte.tv.launcher.fall/com.cvte.tv.launcher.NebulaFallActivity < /dev/null
adb -s "$DEV" shell pm disable-user --user 0 com.cleanyourtv.launcher < /dev/null 2>/dev/null
if grep -q '^private_dns' backup/settings-global.txt 2>/dev/null; then
  echo "Private DNS was set before the cleanup; leaving it alone."
else
  adb -s "$DEV" shell settings delete global private_dns_mode < /dev/null >/dev/null 2>&1
  adb -s "$DEV" shell settings delete global private_dns_specifier < /dev/null >/dev/null 2>&1
fi
echo "Done. Press Home on the remote; reboot the TV if anything looks stale."
