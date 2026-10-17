#!/usr/bin/env bash
# scripts/start-android-emulator.sh - Launch Android Emulator for TabbyRush
# Complies with Phase 01: checks KVM, selects GPU mode, binds emulator-5554
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source Android SDK environment if emulator is missing from PATH
if ! command -v emulator >/dev/null 2>&1; then
  if [[ -f "${SCRIPT_DIR}/android-sdk-env.sh" ]]; then
    # shellcheck source=scripts/android-sdk-env.sh
    source "${SCRIPT_DIR}/android-sdk-env.sh"
  fi
fi

AVD_NAME="${1:-tabbyrush_android}"
PORT="${EMULATOR_PORT:-5554}"
GPU_MODE="${EMULATOR_GPU:-auto}"
XPRA_DISPLAY_TARGET="${XPRA_DISPLAY:-:100}"
EXTRA_ARGS=()

check_prerequisites() {
  # 1. Validate port (decimal integer without leading zeros, even number between 5554 and 5682)
  if ! [[ "${PORT}" =~ ^[1-9][0-9]{3,4}$ ]]; then
    echo "Error: Invalid EMULATOR_PORT '${PORT}'. Must be an even number between 5554 and 5682." >&2
    exit 1
  fi
  PORT=$((10#${PORT}))
  if [ "$((PORT % 2))" -ne 0 ] || [ "${PORT}" -lt 5554 ] || [ "${PORT}" -gt 5682 ]; then
    echo "Error: Invalid EMULATOR_PORT '${PORT}'. Must be an even number between 5554 and 5682." >&2
    exit 1
  fi

  # 2. Check emulator binary
  if ! command -v emulator >/dev/null 2>&1; then
    echo "Error: 'emulator' command not found." >&2
    echo "Ensure Android SDK emulator package is installed and PATH is configured." >&2
    echo "Run: source ${SCRIPT_DIR}/android-sdk-env.sh" >&2
    exit 1
  fi

  # 3. Check KVM hardware virtualization
  if [[ ! -e /dev/kvm ]]; then
    echo "Warning: /dev/kvm not found. Virtualization acceleration unavailable." >&2
  elif [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
    echo "Warning: /dev/kvm lacks read/write permissions for user $(whoami)." >&2
  else
    echo "KVM acceleration: /dev/kvm accessible (OK)"
  fi

  # 4. Accel check
  echo "Checking emulator hardware acceleration..."
  emulator -accel-check || true

  # 5. Check DISPLAY if running with GUI window
  if [[ -z "${DISPLAY:-}" ]]; then
    echo "Notice: DISPLAY environment variable is unset."
    if command -v xpra >/dev/null 2>&1 && xpra list 2>/dev/null | grep -E "(^|[[:space:]/])${XPRA_DISPLAY_TARGET}([[:space:]]|$)" >/dev/null 2>&1; then
      echo "Detected running Xpra on ${XPRA_DISPLAY_TARGET}. Setting DISPLAY=${XPRA_DISPLAY_TARGET}"
      export DISPLAY="${XPRA_DISPLAY_TARGET}"
    else
      echo "Warning: No graphical DISPLAY found. For headless CI, set EMULATOR_HEADLESS=1"
    fi
  fi
}

check_prerequisites

if [[ "${EMULATOR_HEADLESS:-0}" == "1" ]]; then
  EXTRA_ARGS+=("-no-window" "-no-audio")
fi

echo "Starting emulator '${AVD_NAME}' on port ${PORT} with GPU mode '${GPU_MODE}'..."
echo "To attach or monitor boot:"
echo "  adb -s emulator-${PORT} wait-for-device"
echo "  adb -s emulator-${PORT} shell getprop sys.boot_completed"

# Launch emulator
exec emulator \
  -avd "${AVD_NAME}" \
  -port "${PORT}" \
  -gpu "${GPU_MODE}" \
  "${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}"
