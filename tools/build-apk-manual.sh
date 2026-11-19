#!/usr/bin/env bash
# Builds PICO-VR-Operation-Zero.apk WITHOUT Gradle/AGP, using only:
#   javac (JDK 17+), aapt, dalvik-exchange (dx) or d8, zipalign, apksigner, keytool
# On Debian/Ubuntu:  sudo apt-get install -y aapt dalvik-exchange zipalign apksigner
# Usage: tools/build-apk-manual.sh /path/to/android.jar (API 26+ platform jar)   [output dir]
# This is the fallback path used by CI if the Gradle build fails, and it is how the APK was
# verified in an environment without access to Google's Maven repository.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${1:?usage: [MODULE=app|pegapega] $0 /path/to/android.jar [outdir]}"
MODULE="${MODULE:-app}"
OUT="${2:-$ROOT/build-manual/$MODULE}"
VERSION_CODE=1
VERSION_NAME=1.0.0
if [ "$MODULE" = "pegapega" ]; then
  PKG=com.joaolimz.pegapega
  APK_NAME=PICO-VR-Pega-Pega.apk
else
  PKG=com.joaolimz.operationzero
  APK_NAME=PICO-VR-Operation-Zero.apk
fi
SRC="$ROOT/$MODULE/src/main"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/gen"

echo "== 1/6 compile Java"
find "$SRC/java" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== 2/6 dex"
if command -v d8 >/dev/null 2>&1; then
  (cd "$OUT/classes" && find . -name '*.class' > ../classlist.txt)
  d8 --release --min-api 24 --lib "$ANDROID_JAR" --output "$OUT" $(cd "$OUT/classes" && find . -name '*.class' | sed "s#^\./#$OUT/classes/#")
else
  DX=$(command -v dalvik-exchange || command -v dx)
  "$DX" --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"
fi

echo "== 3/6 package resources (aapt)"
# AGP takes the package from 'namespace'; aapt1 needs it in the manifest
sed "s#<manifest #<manifest package=\"$PKG\" #" "$SRC/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
ASSETS_ARG=()
if [ -d "$SRC/assets" ]; then ASSETS_ARG=(-A "$SRC/assets"); fi
aapt package -f -M "$OUT/AndroidManifest.xml" -S "$SRC/res" "${ASSETS_ARG[@]}" \
  -I "$ANDROID_JAR" --min-sdk-version 24 --target-sdk-version 29 \
  --version-code $VERSION_CODE --version-name $VERSION_NAME \
  -J "$OUT/gen" -F "$OUT/unsigned.apk"
(cd "$OUT" && aapt add -k unsigned.apk classes.dex >/dev/null)

echo "== 4/6 zipalign"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "== 5/6 sign"
# standard Android debug keystore location (same key across builds -> updates install over the old version)
KS="${KEYSTORE:-$HOME/.android/debug.keystore}"
mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:${KEYSTORE_PASS:-android} --ks-key-alias ${KEY_ALIAS:-androiddebugkey} \
  --min-sdk-version 24 --out "$OUT/$APK_NAME" "$OUT/aligned.apk"

echo "== 6/6 verify"
apksigner verify --min-sdk-version 24 "$OUT/$APK_NAME"
aapt dump badging "$OUT/$APK_NAME" | head -5
ls -la "$OUT/$APK_NAME"
echo "OK: $OUT/$APK_NAME"
