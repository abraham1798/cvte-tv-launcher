#!/usr/bin/env bash
# Build the launcher, install it on the TV and make it the default Home.
#   bash build-and-install.sh              (TV IP from .tv-host)
#   TV_HOST=1.2.3.4 bash build-and-install.sh
# The stock CVTE launcher stays enabled (CVTE boards can hang at boot without it);
# ours just becomes the preferred Home activity.
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")"
HOST="${TV_HOST:-$(cat .tv-host 2>/dev/null)}"
[ -n "$HOST" ] || { echo "Set TV_HOST=<tv ip> or put the IP in .tv-host" >&2; exit 1; }
DEV="$HOST:${TV_PORT:-5555}"
PKG=com.cleanyourtv.launcher
HOME_ACT=$PKG/.MainActivity

bash launcher/build.sh
adb connect "$DEV" >/dev/null
adb -s "$DEV" push launcher/build/home.apk /data/local/tmp/home.apk >/dev/null
# pm install over the shell: plain `adb install` can hang waiting for an on-screen prompt.
adb -s "$DEV" shell pm install -r /data/local/tmp/home.apk
adb -s "$DEV" shell rm /data/local/tmp/home.apk
adb -s "$DEV" shell pm enable $PKG >/dev/null 2>&1 || true
adb -s "$DEV" shell cmd package set-home-activity $HOME_ACT
adb -s "$DEV" shell input keyevent KEYCODE_HOME
sleep 2
adb -s "$DEV" shell "dumpsys activity activities | grep -E 'mFocusedActivity'"
