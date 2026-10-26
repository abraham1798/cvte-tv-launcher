#!/usr/bin/env bash
# Press remote keys on the TV, then optionally capture a screenshot.
#   tools/tv.sh [--screenshot shots/NAME.png] [KEY ...]
#   KEY is a KEYCODE_ name without the prefix (DPAD_RIGHT, HOME, BACK, ...).
# TV address: TV_HOST / TV_PORT (default port 5555), or the IP in .tv-host at the repo root.
set -u
export MSYS_NO_PATHCONV=1
HOST="${TV_HOST:-$(cat "$(dirname "$0")/../.tv-host" 2>/dev/null)}"
[ -n "$HOST" ] || { echo "Set TV_HOST=<tv ip> or put the IP in .tv-host" >&2; exit 1; }
DEV="$HOST:${TV_PORT:-5555}"
SHOT=""
if [ "${1:-}" = "--screenshot" ]; then SHOT="$2"; shift 2; fi
for k in "$@"; do
  adb -s "$DEV" shell input keyevent "KEYCODE_$k"
  sleep 0.6
done
if [ -n "$SHOT" ]; then
  sleep 0.8
  mkdir -p "$(dirname "$SHOT")"
  adb -s "$DEV" exec-out screencap -p > "$SHOT"
  if [ ! -s "$SHOT" ]; then
    echo "empty screenshot: Live TV, HDMI and DRM apps can't be captured; check topResumedActivity instead" >&2
    exit 1
  fi
  echo "$SHOT ($(stat -c%s "$SHOT") bytes)"
fi
