#!/usr/bin/env bash
# Build launcher/build/home.apk with the plain SDK tools (no Gradle):
# aapt2 -> javac -> d8 -> zipalign -> apksigner (debug key).
# Needs: a JDK on PATH, build-tools 34 and platform android-34 under ANDROID_SDK
# (default: ../android-sdk next to this folder).
set -euo pipefail
cd "$(dirname "$0")"
HERE="$(pwd)"
SDK="${ANDROID_SDK:-}"
if [ -z "$SDK" ]; then
  for s in "$(cd .. && pwd)/android-sdk" "${LOCALAPPDATA:+$(cygpath -u "$LOCALAPPDATA" 2>/dev/null)/cleanyourtv/android-sdk}" "$HOME/Android/Sdk"; do
    [ -n "$s" ] && [ -d "$s/platforms/android-34" ] && { SDK="$s"; break; }
  done
fi
[ -n "$SDK" ] || { echo "Android SDK not found: set ANDROID_SDK (needs build-tools and platforms/android-34)" >&2; exit 1; }
BT="$(ls -d "$SDK"/build-tools/* | tail -1)"
JAR="$SDK/platforms/android-34/android.jar"
MIN_SDK=24      # this TV runs Android 7.0 (SDK 24)
TARGET_SDK=34
SRC="$HERE/app/src/main"
OUT="$HERE/build"

# Windows SDK tools want native absolute paths; elsewhere this is a no-op.
w() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
tool() { if [ -f "$BT/$1.bat" ]; then echo "$BT/$1.bat"; elif [ -f "$BT/$1.exe" ]; then echo "$BT/$1.exe"; else echo "$BT/$1"; fi; }

[ -f "$JAR" ] || { echo "android.jar not found under $SDK" >&2; exit 1; }
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$(tool aapt2)" compile --dir "$(w "$SRC/res")" -o "$(w "$OUT/res.zip")"
# Optional personal overrides (not in git): app/src/main/res-local/values/config.xml etc.
OVERLAY=()
if [ -d "$SRC/res-local" ]; then
  "$(tool aapt2)" compile --dir "$(w "$SRC/res-local")" -o "$(w "$OUT/res-local.zip")"
  OVERLAY=(-R "$(w "$OUT/res-local.zip")")
fi
"$(tool aapt2)" link -o "$(w "$OUT/unaligned.apk")" -I "$(w "$JAR")" \
  --manifest "$(w "$SRC/AndroidManifest.xml")" \
  --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
  --java "$(w "$OUT/gen")" "$(w "$OUT/res.zip")" "${OVERLAY[@]}"

javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -classpath "$(w "$JAR")" -d "$(w "$OUT/classes")" \
  $(cd "$HERE" && find app/src/main/java build/gen -name '*.java')
(cd "$OUT/classes" && jar cf ../classes.jar .)
"$(tool d8)" --release --min-api $MIN_SDK --lib "$(w "$JAR")" --output "$(w "$OUT/dex")" "$(w "$OUT/classes.jar")"
(cd "$OUT/dex" && "$(tool aapt)" add "$(w "$OUT/unaligned.apk")" classes.dex >/dev/null)
# Assets are added here rather than with aapt2 -A: on Windows aapt2 stores them
# as "assets/fonts\x.ttf" and Android can't find them. They're staged in a short
# temp folder because aapt can't open relative paths under a very long one.
STAGE="$(mktemp -d)"
cp -r "$SRC/assets" "$STAGE/"
(cd "$STAGE" && "$(tool aapt)" add "$(w "$OUT/unaligned.apk")" $(find assets -type f | sort) >/dev/null)
rm -rf "$STAGE"

"$(tool zipalign)" -p -f 4 "$(w "$OUT/unaligned.apk")" "$(w "$OUT/aligned.apk")"
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -keystore "$(w "$KS")" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi
"$(tool apksigner)" sign --ks "$(w "$KS")" --ks-pass pass:android --key-pass pass:android \
  --out "$(w "$OUT/home.apk")" "$(w "$OUT/aligned.apk")"
echo "built $OUT/home.apk ($(wc -c < "$OUT/home.apk") bytes)"
