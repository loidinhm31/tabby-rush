#!/usr/bin/env bash
# ==============================================================================
# scripts/android-smoke.sh
# Android device / emulator smoke verification runner for TabbyRush.
# ==============================================================================
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-}"
APK="${APK_PATH:-androidApp/build/outputs/apk/debug/androidApp-debug.apk}"
PORT="${HUB_PORT:-3000}"
USE_REVERSE=0
CAPTURE_DIR="${EVIDENCE_DIR:-docs/evidence}"

usage() {
    local code="${1:-1}"
    cat <<EOF
Usage: $(basename "$0") [options]

Options:
  -s, --serial <SERIAL>   Target ADB serial (default: \$ANDROID_SERIAL or emulator-5554)
  -a, --apk <PATH>        Path to debug APK (default: androidApp/build/outputs/apk/debug/androidApp-debug.apk)
  -p, --port <PORT>       Hub service port to reverse (default: 3000)
  -r, --reverse           Configure adb reverse tcp:<PORT> tcp:<PORT>
  -c, --capture           Capture UI screenshots to docs/evidence/
  -h, --help              Show this help message
EOF
    exit "$code"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--serial) SERIAL="$2"; shift 2 ;;
        -a|--apk) APK="$2"; shift 2 ;;
        -p|--port) PORT="$2"; shift 2 ;;
        -r|--reverse) USE_REVERSE=1; shift ;;
        -c|--capture) CAPTURE=1; shift ;;
        -h|--help) usage 0 ;;
        *) echo "Unknown option: $1" >&2; usage 1 ;;
    esac
done

if ! command -v adb >/dev/null 2>&1; then
    echo "Error: adb binary not found on PATH." >&2
    echo "Ensure Android SDK platform-tools are installed and added to PATH." >&2
    exit 1
fi

if [[ -z "$SERIAL" ]]; then
    # Auto-detect single device if available
    DEVICES=($(adb devices | awk 'NR>1 && $2=="device" {print $1}'))
    if [[ ${#DEVICES[@]} -eq 0 ]]; then
        echo "Error: No connected Android devices or emulators found." >&2
        exit 1
    elif [[ ${#DEVICES[@]} -eq 1 ]]; then
        SERIAL="${DEVICES[0]}"
        echo "Auto-detected device: $SERIAL"
    else
        echo "Error: Multiple devices connected (${DEVICES[*]}). Specify --serial <SERIAL>." >&2
        exit 1
    fi
fi

cleanup() {
    if [[ "$USE_REVERSE" -eq 1 ]]; then
        echo "Removing adb reverse rule for tcp:$PORT..."
        adb -s "$SERIAL" reverse --remove "tcp:$PORT" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "=== TabbyRush Android Smoke Test ==="
echo "Target device: $SERIAL"
echo "Target APK:    $APK"

if [[ ! -f "$APK" ]]; then
    echo "Error: APK not found at $APK" >&2
    echo "Run './gradlew :androidApp:assembleDebug' first." >&2
    exit 1
fi

if [[ "$USE_REVERSE" -eq 1 ]]; then
    echo "Setting up adb reverse tcp:$PORT -> tcp:$PORT..."
    adb -s "$SERIAL" reverse "tcp:$PORT" "tcp:$PORT"
fi

echo "Installing $APK onto $SERIAL..."
adb -s "$SERIAL" install -r "$APK"

echo "Starting MainActivity..."
adb -s "$SERIAL" shell am start -n dev.tabbyrush.android/.MainActivity

echo "Verifying process launch..."
sleep 2
PID=$(adb -s "$SERIAL" shell pidof dev.tabbyrush.android | tr -d '\r\n')
if [[ -z "$PID" ]]; then
    echo "Error: Process dev.tabbyrush.android failed to start." >&2
    exit 1
fi
echo "Process dev.tabbyrush.android running with PID: $PID"

capture_screen() {
    local name="$1"
    local dest="$CAPTURE_DIR/$name.png"
    mkdir -p "$CAPTURE_DIR"
    echo "Capturing $dest..."
    adb -s "$SERIAL" exec-out screencap -p > "$dest"
}

if [[ "${CAPTURE:-0}" -eq 1 ]]; then
    echo "Capturing active screen to $CAPTURE_DIR/android-debug-empty.png..."
    capture_screen "android-debug-empty"
fi

echo "Smoke test initiated successfully on $SERIAL (PID: $PID)."
