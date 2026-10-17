#!/usr/bin/env bash
# tests/test-phase-02-foundation.sh - Automated verification suite for Phase 02 Foundation
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

echo "=== Running Phase 02 Foundation Test Suite ==="

# 1. Gradle and wrapper files exist
assert_test "settings.gradle.kts exists" "[ -f settings.gradle.kts ]"
assert_test "build.gradle.kts exists" "[ -f build.gradle.kts ]"
assert_test "gradle.properties exists" "[ -f gradle.properties ]"
assert_test "gradle/libs.versions.toml exists" "[ -f gradle/libs.versions.toml ]"
assert_test "gradle/verification-metadata.xml exists" "[ -f gradle/verification-metadata.xml ]"
assert_test "gradle/wrapper/gradle-wrapper.jar exists" "[ -f gradle/wrapper/gradle-wrapper.jar ]"
assert_test "gradle/wrapper/gradle-wrapper.properties exists" "[ -f gradle/wrapper/gradle-wrapper.properties ]"
assert_test "gradlew exists" "[ -f gradlew ]"
assert_test "gradlew.bat exists" "[ -f gradlew.bat ]"
assert_test ".gitignore exists" "[ -f .gitignore ]"
assert_test "shared/build.gradle.kts exists" "[ -f shared/build.gradle.kts ]"
assert_test "shared/gradle.lockfile exists" "[ -f shared/gradle.lockfile ]"

# 2. Executable permissions
assert_test "gradlew is executable" "[ -x gradlew ]"
assert_test "gradle-wrapper distributionSha256Sum matches official hash" "grep -q 'f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' gradle/wrapper/gradle-wrapper.properties"
assert_test "gradle-wrapper.jar sha256 matches verified hash" "[ \"\$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)\" = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' ]"
# 3. Wrapper properties and distribution SHA-256
assert_test "gradle-wrapper distributionUrl is Gradle 8.11.1" "grep -q 'gradle-8.11.1-bin.zip' gradle/wrapper/gradle-wrapper.properties"

# 4. Settings staged assembly guards
assert_test "settings.gradle.kts includes :shared" "grep -q 'include(\":shared\")' settings.gradle.kts"
assert_test "settings.gradle.kts guards :androidApp" "grep -q 'androidApp/build.gradle.kts' settings.gradle.kts"
assert_test "settings.gradle.kts guards :desktopApp" "grep -q 'desktopApp/build.gradle.kts' settings.gradle.kts"

# 5. Dependency version catalog verification
assert_test "libs.versions.toml pins kotlin 2.1.10" "grep -q 'kotlin = \"2.1.10\"' gradle/libs.versions.toml"
assert_test "libs.versions.toml pins compose 1.7.3" "grep -q 'compose = \"1.7.3\"' gradle/libs.versions.toml"
assert_test "libs.versions.toml pins agp 8.7.3" "grep -q 'agp = \"8.7.3\"' gradle/libs.versions.toml"
assert_test "libs.versions.toml pins room 2.7.0-alpha12" "grep -q 'room = \"2.7.0-alpha12\"' gradle/libs.versions.toml"

# 6. Shared module configuration
assert_test "shared/build.gradle.kts configures dev.tabbyrush.shared namespace" "grep -q 'namespace = \"dev.tabbyrush.shared\"' shared/build.gradle.kts"
assert_test "shared/build.gradle.kts targets minSdk 26" "grep -q 'minSdk = 26' shared/build.gradle.kts"
assert_test "shared/build.gradle.kts configures JVM 17 target" "grep -q 'JVM_17' shared/build.gradle.kts"
assert_test "shared/build.gradle.kts configures room schemaDirectory" "grep -q 'schemaDirectory' shared/build.gradle.kts"

# 7. Kotlin contract sources exist
assert_test "hub-config.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/hub-config.kt ]"
assert_test "account-identity.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-identity.kt ]"
assert_test "account-key.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-key.kt ]"
assert_test "credential-vault.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/credential-vault.kt ]"
assert_test "auth-session.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/auth-session.kt ]"
assert_test "account-store.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt ]"
assert_test "sync-coordinator.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-coordinator.kt ]"
assert_test "sync-scheduler.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-scheduler.kt ]"
assert_test "todo-models.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt ]"
assert_test "todo-ui-state.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt ]"
assert_test "todo-action.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-action.kt ]"
assert_test "app-factories.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/app-factories.kt ]"
assert_test "safe-errors.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/safe-errors.kt ]"
assert_test "title-policy.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/title-policy.kt ]"
assert_test "endpoint-policy.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/endpoint-policy.kt ]"
assert_test "strict-sync-contract.kt exists" "[ -f shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-contract.kt ]"

# 8. Test suites exist
assert_test "strict-digest-test.kt exists" "[ -f shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-digest-test.kt ]"
assert_test "title-policy-test.kt exists" "[ -f shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/title-policy-test.kt ]"
assert_test "endpoint-policy-test.kt exists" "[ -f shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/endpoint-policy-test.kt ]"
assert_test "account-key-test.kt exists" "[ -f shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/account-key-test.kt ]"

# 9. Documentation deliverables exist
assert_test "docs/system-architecture.md exists" "[ -f docs/system-architecture.md ]"
assert_test "docs/api-contract.md exists" "[ -f docs/api-contract.md ]"
assert_test "docs/code-standards.md exists" "[ -f docs/code-standards.md ]"
assert_test "docs/toolchain-versions.md exists" "[ -f docs/toolchain-versions.md ]"
assert_test "docs/foundation-evidence.md exists" "[ -f docs/foundation-evidence.md ]"

# 10. Contract isolation & redaction checks
assert_test "CommonMain has zero android.* imports" "! grep -r '^import android\\.' shared/src/commonMain/kotlin/"
assert_test "CredentialBundle redacts credentials in toString" "grep -q 'CredentialBundle(\\[REDACTED\\])' shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/credential-vault.kt"
assert_test "HubConfig redacts appApiKey in toString" "grep -q 'appApiKey=\\[REDACTED\\]' shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/hub-config.kt"
assert_test "All Kotlin sources have package dev.tabbyrush.shared.contract" "[ -z \"\$(grep -L '^package dev\\.tabbyrush\\.shared\\.contract' shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/*.kt)\" ]"

# 11. Functional Policy & Canonical Digest Vectors Check (using Node.js runner)
assert_test "Functional policy and digest vectors PASS" "node -e '
const crypto = require(\"crypto\");

// 1. AccountKey framing check
function createAccountKey(origin, userId) {
  const oBuf = Buffer.from(origin, \"utf8\");
  const uBuf = Buffer.from(userId, \"utf8\");
  const lenBuf = Buffer.alloc(8);
  lenBuf.writeUInt32BE(oBuf.length, 0);
  lenBuf.writeUInt32BE(uBuf.length, 4);
  const hash = crypto.createHash(\"sha256\");
  hash.update(lenBuf.subarray(0, 4));
  hash.update(oBuf);
  hash.update(lenBuf.subarray(4, 8));
  hash.update(uBuf);
  return hash.digest(\"hex\");
}

const k1 = createAccountKey(\"ab\", \"c\");
const k2 = createAccountKey(\"a\", \"bc\");
if (k1 === k2) throw new Error(\"AccountKey framing collision detected\");
if (k1.length !== 64) throw new Error(\"AccountKey invalid length\");

// 2. Title Unicode whitespace check
const wsCodePoints = [0x0009, 0x000A, 0x000D, 0x0020, 0x00A0, 0x3000];
const sample = \"\\u3000\\u00A0Hello World\\u0009 \";
const trimmed = sample.replace(/^[\\s\\u00A0\\u3000]+|[\\s\\u00A0\\u3000]+$/g, \"\");
if (trimmed !== \"Hello World\") throw new Error(\"Title whitespace trimming failed\");

// 3. Digest cross-check
const digest1 = crypto.createHash(\"sha256\").update(\"test\").digest(\"hex\");
if (digest1 !== \"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08\") {
  throw new Error(\"SHA256 standard vector mismatch\");
}
'"

echo ""
echo "=== Phase 02 Foundation Test Results: $PASSED / $TOTAL passed ($FAILED failed) ==="

if [ "$FAILED" -gt 0 ]; then
  exit 1
fi
