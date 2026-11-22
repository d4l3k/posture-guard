#!/usr/bin/env bash
# Builds a signed release APK at build/posture-guard.apk.
#   KEYSTORE=path/to.jks KEYSTORE_PASSWORD_FILE=path/to.password ./build.sh
# Without KEYSTORE it signs with the Android debug key.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
GRADLE="${GRADLE:-gradle}"
args=(-p "$HERE" --console=plain -q assembleRelease)
args+=(-PversionCode="$(git -C "$HERE" rev-list --count HEAD 2>/dev/null || echo 1)")
if [ -n "${KEYSTORE:-}" ]; then
  args+=(-Pkeystore="$KEYSTORE" -PkeystorePasswordFile="$KEYSTORE_PASSWORD_FILE")
fi
"$GRADLE" "${args[@]}"
cp "$HERE/build/gradle/outputs/apk/release/"*-release.apk "$HERE/build/posture-guard.apk"
echo "Built $HERE/build/posture-guard.apk"
