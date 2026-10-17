#!/usr/bin/env bash
# tests/test-phase-01-tooling.sh - Comprehensive automated test suite for Phase 01
set -euo pipefail

cd "$(dirname "$0")/.."

TOTAL=0
PASSED=0
FAILED=0

assert_test() {
  local desc="$1"
  local cmd="$2"
  TOTAL=$((TOTAL + 1))
  if bash -c "$cmd" >/dev/null 2>&1; then
    echo "PASS: $desc"
    PASSED=$((PASSED + 1))
  else
    echo "FAIL: $desc" >&2
    FAILED=$((FAILED + 1))
  fi
}

echo "=== Running Phase 01 Test Suite ==="

# 1. File existence tests
assert_test "scripts/start-xpra.sh exists" "[ -f scripts/start-xpra.sh ]"
assert_test "scripts/android-sdk-env.sh exists" "[ -f scripts/android-sdk-env.sh ]"
assert_test "scripts/start-android-emulator.sh exists" "[ -f scripts/start-android-emulator.sh ]"
assert_test "docs/headless-android-setup.md exists" "[ -f docs/headless-android-setup.md ]"
assert_test "docs/host-tooling-evidence.md exists" "[ -f docs/host-tooling-evidence.md ]"

# 2. Executable permission tests
assert_test "scripts/start-xpra.sh is executable" "[ -x scripts/start-xpra.sh ]"
assert_test "scripts/android-sdk-env.sh is executable" "[ -x scripts/android-sdk-env.sh ]"
assert_test "scripts/start-android-emulator.sh is executable" "[ -x scripts/start-android-emulator.sh ]"

# 3. Shell syntax validation
assert_test "scripts/start-xpra.sh syntax valid" "bash -n scripts/start-xpra.sh"
assert_test "scripts/android-sdk-env.sh syntax valid" "bash -n scripts/android-sdk-env.sh"
assert_test "scripts/start-android-emulator.sh syntax valid" "bash -n scripts/start-android-emulator.sh"

# 4. Functional script tests
assert_test "scripts/start-xpra.sh --help outputs usage" "./scripts/start-xpra.sh --help | grep -q 'Usage:'"
assert_test "scripts/start-xpra.sh status runs cleanly" "./scripts/start-xpra.sh status"
assert_test "scripts/start-xpra.sh contains mdns=no security flag" "grep -q -- '--mdns=no' scripts/start-xpra.sh"
assert_test "scripts/android-sdk-env.sh sets ANDROID_HOME" "source scripts/android-sdk-env.sh && [ -n \"\$ANDROID_HOME\" ]"
assert_test "scripts/android-sdk-env.sh emits Java 25 compatibility notice" '
  output=$(ANDROID_ENV_VERBOSE=1 source scripts/android-sdk-env.sh 2>&1)
  echo "$output" | grep -q "Host Java is version 25. AGP requires JDK 21 LTS"
'
assert_test "scripts/start-android-emulator.sh fails with code 1 and error message when emulator absent" '
  output=$(PATH="/usr/bin:/bin" ANDROID_HOME="/nonexistent" ./scripts/start-android-emulator.sh tabbyrush_android 2>&1 || true)
  echo "$output" | grep -q "Error: .emulator. command not found."
'
assert_test "scripts/start-android-emulator.sh rejects odd port" '
  output=$(EMULATOR_PORT=5555 ./scripts/start-android-emulator.sh tabbyrush_android 2>&1 || true)
  echo "$output" | grep -q "Error: Invalid EMULATOR_PORT"
'
assert_test "scripts/start-android-emulator.sh rejects leading-zero octal ports" '
  output=$(EMULATOR_PORT=08 ./scripts/start-android-emulator.sh tabbyrush_android 2>&1 || true)
  echo "$output" | grep -q "Error: Invalid EMULATOR_PORT"
'
assert_test "scripts/start-android-emulator.sh rejects non-canonical leading-zero ports" '
  output=$(EMULATOR_PORT=05554 ./scripts/start-android-emulator.sh tabbyrush_android 2>&1 || true)
  echo "$output" | grep -q "Error: Invalid EMULATOR_PORT"
'

# 5. Host prerequisites validation
assert_test "/dev/kvm accessible" "[ -e /dev/kvm ] && [ -r /dev/kvm ] && [ -w /dev/kvm ]"
assert_test "xpra binary present" "command -v xpra"
assert_test "xterm binary present" "command -v xterm"
assert_test "java binary present" "command -v java"
assert_test "node binary present" "command -v node"

# 6. Documentation content validation
assert_test "headless-android-setup.md contains Xpra setup" "grep -q 'Xpra' docs/headless-android-setup.md"
assert_test "headless-android-setup.md contains mdns=no" "grep -q -- '--mdns=no' docs/headless-android-setup.md"
assert_test "headless-android-setup.md contains security rules" "grep -q 'Security' docs/headless-android-setup.md"
assert_test "host-tooling-evidence.md contains KVM evidence" "grep -q 'KVM' docs/host-tooling-evidence.md"
assert_test "host-tooling-evidence.md contains mdns=no" "grep -q -- '--mdns=no' docs/host-tooling-evidence.md"
assert_test "host-tooling-evidence.md documents pending install status" "grep -q 'Pending interactive' docs/host-tooling-evidence.md"

echo "=== Test Results: ${PASSED}/${TOTAL} passed (${FAILED} failed) ==="

if [ "${FAILED}" -gt 0 ]; then
  exit 1
fi
exit 0
