# Shared by the scripts below. Connects to the TV over Wi-Fi.
set -euo pipefail
command -v adb >/dev/null || { echo "adb not found. On a Mac: brew install android-platform-tools"; exit 1; }
TV="${1:-}"
if [ -z "$TV" ]; then
  read -r -p "TV IP address (Settings > Network & Internet > your Wi-Fi): " TV
fi
adb connect "$TV:5555" >/dev/null
echo "If the TV asks to allow USB debugging, choose Always allow, then press Enter here."
read -r _
adb -s "$TV:5555" get-state >/dev/null || { echo "Could not reach the TV at $TV."; exit 1; }
A="adb -s $TV:5555"
