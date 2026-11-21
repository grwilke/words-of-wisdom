#!/usr/bin/env bash
# Discovers an Android TV / Google TV target via ADB (explicit host:port or attached device),
# installs the Words of Wisdom APK, and launches MainActivity.
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ADB_BIN:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
[[ -f "$APK" ]] || APK="app/build/outputs/apk/debug/app-debug.apk"
TARGET="${2:-}"

if [[ -n "$TARGET" ]]; then
  "$ADB" connect "$TARGET"
  SERIAL="$TARGET"
else
  SERIAL=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi

if [[ -z "${SERIAL:-}" ]]; then
  echo "No ADB device connected. Enable Developer Options -> Network/Wireless Debugging on your Google TV, then run:" >&2
  echo "  tools/install_tv.sh \"$APK\" <tv-ip>:5555" >&2
  exit 1
fi

echo "Installing $APK onto $SERIAL..."
"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell am start -n com.cubeworks.wordsofwisdom/.MainActivity
echo "Launched Words of Wisdom on $SERIAL!"
