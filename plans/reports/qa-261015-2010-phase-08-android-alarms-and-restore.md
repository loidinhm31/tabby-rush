# QA Test Execution Report: Phase 08 Android Alarms, Private Notifications & Restoration

**Date**: 2026-10-15  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Android SDK (`/home/loidinh/Android/Sdk`) | Gradle 8.11.1 | Node.js v24.16.0  
**Target Phase**: `Phase 08 — Android alarms, private notifications and restoration`  
**Working Directory**: `/home/loidinh/WS/doggo`  
**Artifact**: `plans/reports/qa-261015-2010-phase-08-android-alarms-and-restore.md`

---

## 1. Test Results Overview

| Index | Command / Suite | Scope | Category | Total | Pass | Fail | Skip | Status |
|:---:|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `JAVA_HOME=... ANDROID_HOME=... ./gradlew :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off` | Android Unit Tests | Unit / Safety | 11 | 11 | 0 | 0 | PASS |
| 2 | `./gradlew :androidApp:assembleDebug --dependency-verification=off` | Android Debug APK | Assembly Check | 1 | 1 | 0 | 0 | PASS (up-to-date) |
| 3 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | Shared KMP JVM & SQLite | Unit / Integration | 182 | 182 | 0 | 0 | PASS |
| 4 | `JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks` | Desktop App Tests | Unit / Platform | 32 | 32 | 0 | 0 | PASS |
| 5 | `node scripts/strict-v2-vector-runner.mjs` | Canonical wire vectors & schema fingerprints | Contract Vectors | 23 | 23 | 0 | 0 | PASS |
| 6 | `node scripts/hub-live-smoke.mjs --fixture-test` | Hub offline fixture smoke harness | Protocol Fixture | 18 | 18 | 0 | 0 | PASS |
| 7 | `node scripts/release-security-check.mjs --diagnose-source` | Static manifest, export, origin, secret rules | Security Diagnostic | 6 | 6 | 0 | 0 | PASS |
| 8 | `node scripts/hub-strict-upgrade.test.mjs` | Hub strict upgrade CLI suite | Integration CLI | 9 | 9 | 0 | 0 | PASS |

**Test Execution Summary**:
- **Total Tests / Diagnostic Checks**: 281
  - **JVM Unit / Integration Tests**: 225 (11 Android + 182 Shared + 32 Desktop)
  - **Contract Wire Vectors & Schema Fingerprints**: 23
  - **Hub Offline Fixture Smoke Checks**: 18
  - **Static Security Diagnostic Rules**: 6
  - **Hub Strict Upgrade CLI Tests**: 9
- **Passed**: 281 (100%)
- **Failed**: 0
- **Skipped / Cancelled**: 0
- **APK Assembly**: Verified valid debug APK target (64 tasks up-to-date, `androidApp-debug.apk` present, 20,349,202 bytes).

---

## 2. Android Unit Test Breakdown (`:androidApp:testDebugUnitTest`)

- Total Test Classes: 4
- Total Tests: 11
- Passed: 11, Failed: 0, Skipped: 0
- Execution: `BUILD SUCCESSFUL in 10s` (45 actionable tasks executed with `--rerun-tasks`)

| Test Class | Scope / Tested Behavior | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.android.AndroidNotificationAdapterTest` | Request code determinism & distribution, capabilities model, tag formatting, registry key serialization, restore actions, intent constants | 6 | 0 | 0 | 0.043s |
| `dev.doggo.android.BuildConfigTest` | DotEnv loading into BuildConfig | 1 | 0 | 0 | 0.001s |
| `dev.doggo.android.CompositeLeaveSchedulerCancelGateTest` | Teardown cancellation simulation (drain gate -> cancel observers -> cancel sync -> cancel alarms), fail-closed on drain timeout simulation | 2 | 0 | 0 | 0.063s |
| `dev.doggo.android.ReminderPermissionControllerTest` | Permission string constant (`POST_NOTIFICATIONS`), state representation types | 2 | 0 | 0 | 0.001s |

### Detailed Test Cases:
1. `AndroidNotificationAdapterTest.testAlarmRequestCodeDeterminismAndDistribution`: PASS (0.021s) - Verifies non-negative request codes, determinism across repeats, and uniqueness across occurrences and task IDs.
2. `AndroidNotificationAdapterTest.testCapabilitiesModel`: PASS (0.001s) - Verifies capabilities model data class properties.
3. `AndroidNotificationAdapterTest.testAccountNotificationTagFormatting`: PASS (0.000s) - Verifies formatting of account notification tags (`accountKey:taskId`).
4. `AndroidNotificationAdapterTest.testAlarmRegistryKeySerialization`: PASS (0.009s) - Verifies encoding and decoding of composite alarm keys (`generation:taskId:scheduleToken:occurrenceIndex`).
5. `AndroidNotificationAdapterTest.testReminderRestoreReceiverSupportedActions`: PASS (0.011s) - Validates static action list matching `BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
6. `AndroidNotificationAdapterTest.testIntentFactoryConstants`: PASS (0.000s) - Verifies channel ID (`doggo_reminders`), actions, and intent extras constants.
7. `BuildConfigTest.testBuildConfigValuesLoadedFromDotEnv`: PASS (0.001s) - Verifies environment property injection.
8. `CompositeLeaveSchedulerCancelGateTest.testGateFailsClosedWhenDrainFails`: PASS (0.061s) - Simulates delivery gate admission timeout throwing `IllegalStateException` under simulated cancel gate.
9. `CompositeLeaveSchedulerCancelGateTest.testGateDrainsAndCancelsInOrder`: PASS (0.002s) - Verifies ordered execution pattern in test mock: gate drained -> observer cancelled -> sync scheduler cancelled -> reminders cancelled.
10. `ReminderPermissionControllerTest.testPermissionConstants`: PASS (0.000s) - Verifies `POST_NOTIFICATIONS` permission string constant.
11. `ReminderPermissionControllerTest.testPermissionStateTypes`: PASS (0.001s) - Verifies sealed state hierarchy types: `Exact`, `BestEffort`, `NotificationDenied`, `ChannelDisabled`.

---

## 3. Android Debug APK Assembly (`:androidApp:assembleDebug`)

- Command: `./gradlew :androidApp:assembleDebug --dependency-verification=off`
- Result: `BUILD SUCCESSFUL in 762ms` (64 actionable tasks, 64 up-to-date)
- Target Artifact: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Artifact Size: 20,349,202 bytes (~20.3 MB)
- Status: Build tree and packaging artifacts intact and up-to-date.

---

## 4. Shared Multiplatform JVM & SQLite Breakdown (`:shared:jvmTest`)

- Total Test Classes: 26
- Total Tests: 182
- Passed: 182, Failed: 0, Skipped: 0
- Execution: `BUILD SUCCESSFUL in 8s` (15 actionable tasks executed with `--rerun-tasks`)

| Test Class | Scope / Target | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.shared.auth.HubAuthClientTest` | Registration, login, token refresh, access request | 9 | 0 | 0 | 0.248s |
| `dev.doggo.shared.auth.HubAuthSessionTest` | Auth state machine, local logout, 401 guard | 8 | 0 | 0 | 0.096s |
| `dev.doggo.shared.auth.SingleFlightRefreshTest` | Refresh deduplication, concurrency, safe error | 3 | 0 | 0 | 0.005s |
| `dev.doggo.shared.contract.AccountKeyTest` | Account key framing, determinism, validation | 5 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.EndpointPolicyTest` | Origin sanitization, HTTPS enforcement, debug rules | 8 | 0 | 0 | 0.002s |
| `dev.doggo.shared.contract.PersistedScheduleProjectionTest` | Draft-free projection, completion/delete barriers, intent overlay | 5 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.ReminderScheduleCalculatorTest` | Checked 64-bit arithmetic, 1..10 bounds, 5-min grace window | 9 | 0 | 0 | 0.009s |
| `dev.doggo.shared.contract.StrictDigestTest` | SHA-256 vectors, deterministic canonical payload | 5 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.StrictTimeZoneResolverTest` | Standard time, DST overlap/gap, 23/25h boundary | 7 | 0 | 0 | 0.019s |
| `dev.doggo.shared.contract.StrictV2DigestExtendedTest` | Negative epoch, unicode title/zone, max i64 rev | 6 | 0 | 0 | 0.004s |
| `dev.doggo.shared.contract.StrictV2DigestVectorTest` | Date-only, repeating count, null defaults, DST | 5 | 0 | 0 | 0.003s |
| `dev.doggo.shared.contract.TaskDeadlineReminderModelsTest` | Valid date-only/timed, last reminder bounds, tokens | 6 | 0 | 0 | 0.001s |
| `dev.doggo.shared.contract.TitlePolicyTest` | Length bounds, surrogate validation, astral emoji | 6 | 0 | 0 | 0.003s |
| `dev.doggo.shared.data.ReminderOccurrenceStoreTest` | Ledger persistence, claim-before-post, skipped marking, idempotent sync | 4 | 0 | 0 | 0.012s |
| `dev.doggo.shared.data.ReminderSchedulingCoordinatorSqliteSmokeTest` | Real BundledSQLite integration, alarm scheduling, draft isolation | 2 | 0 | 0 | 0.098s |
| `dev.doggo.shared.data.ReminderTeardownBarrierSqliteTest` | Teardown awaits in-flight delivery before closing Room database | 1 | 0 | 0 | 0.010s |
| `dev.doggo.shared.data.RoomAccountStoreTest` | BundledSQLite persistence, non-destructive rebasing, schedule token rotation | 22 | 0 | 0 | 0.114s |
| `dev.doggo.shared.data.RoomMigrationV1ToV2Test` | Explicit v1->v2 Room schema migration | 1 | 0 | 0 | 0.015s |
| `dev.doggo.shared.sync.AccountLeaveProtocolTest` | Teardown sequence, scheduler cancel gate, fail-closed wipe | 7 | 0 | 0 | 0.009s |
| `dev.doggo.shared.sync.AccountSyncCoordinatorTest` | Upgrade detection, quiescence, receipt rebase, v2 push | 9 | 0 | 0 | 0.035s |
| `dev.doggo.shared.sync.ContractTransitionTest` | Transition coordinator, phase resume, superseded blocks | 4 | 0 | 0 | 0.018s |
| `dev.doggo.shared.sync.HubSyncClientTest` | Bootstrap v2, receipt fetch, push/pull envelopes | 12 | 0 | 0 | 0.030s |
| `dev.doggo.shared.sync.OutboxReconciliationTest` | Optimistic lock, FIFO batch, receipt match, CAS ack | 12 | 0 | 0 | 0.007s |
| `dev.doggo.shared.sync.ReminderDeliveryGateTest` | Mutation/delivery admission, drain signal, generation mismatch guard | 4 | 0 | 0 | 0.004s |
| `dev.doggo.shared.sync.StagedFullScanTest` | Multi-page full scan, monotonic promotion, cursor loop | 14 | 0 | 0 | 0.045s |
| `dev.doggo.shared.ui.TodoSemanticsTest` | Semantics, accessibility contrast, conflict warning copy | 8 | 0 | 0 | 0.019s |

---

## 5. Desktop App Test Breakdown (`:desktopApp:test`)

- Total Test Classes: 6
- Total Tests: 32
- Passed: 32, Failed: 0, Skipped: 0
- Execution: `BUILD SUCCESSFUL in 7s` (19 actionable tasks executed with `--rerun-tasks`)

| Test Class | Scope / Target | Passed | Failed | Skipped | Time |
|---|---|:---:|:---:|:---:|:---:|
| `dev.doggo.desktop.DesktopControllerEnvTest` | Environment bootstrap, hub config parsing | 4 | 0 | 0 | 0.130s |
| `dev.doggo.desktop.DesktopLifecycleTest` | App startup, shutdown hooks, controller dispatch | 8 | 0 | 0 | 0.032s |
| `dev.doggo.desktop.DotEnvLoaderTest` | Local env parsing, comments, precedence | 5 | 0 | 0 | 0.003s |
| `dev.doggo.desktop.PlatformVaultDispatcherTest` | Vault resolution across Linux/macOS/Windows | 5 | 0 | 0 | 0.004s |
| `dev.doggo.desktop.linux.LinuxNotificationAdapterTest` | DBus freedesktop notification interface | 4 | 0 | 0 | 0.010s |
| `dev.doggo.desktop.linux.LinuxSecretServiceVaultTest` | Linux libsecret / secret-service integration | 6 | 0 | 0 | 0.020s |

---

## 6. Contract Vectors & Schema Fingerprints (`strict-v2-vector-runner.mjs`)

- Schema Fingerprints: 2/2 PASS
  - v1 SHA-256: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - v2 SHA-256: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- Valid Vectors: 10/10 PASS
  - Minimal date-only deadline, timed DST overlap, repeating count 10, clear deadline, zero-data delete, migrated null defaults, disabled rule with deadline, unicode title/zone, max i64 rev, negative epoch instant.
- Rejection Vectors: 11/11 PASS
  - Revision out of range, payload on delete, negative created timestamp, negative lead minutes, repetition count out of bounds, interval not null when count=1, interval < 15 min, deadline parameters present without deadlineDate, missing required fields when deadlineDate present, invalid reminder anchor on timed deadlines, last reminder after anchor.

---

## 7. Hub Live Smoke Fixture (`hub-live-smoke.mjs --fixture-test`)

- Suite 1: Health & App Admission (2/2 PASS)
- Suite 2: Security & Negative Route Isolation (3/3 PASS)
- Suite 3: Authentication & Access Session (4/4 PASS)
- Suite 4: Strict Strong Sync Operations (7/7 PASS)
- Suite 5: Tombstone Deletion & Invariants (2/2 PASS)
- Total: 18/18 PASS against offline fixture mock server.

---

## 8. Release Security & Diagnostic Audit (`release-security-check.mjs --diagnose-source`)

- Prohibit cleartext traffic (`usesCleartextTraffic="false"`): PASS
- Restrict backup (`allowBackup="false"`): PASS
- Exported components: Only `MainActivity` exported; `DoggoAlarmReceiver` and `ReminderRestoreReceiver` explicitly configured with `android:exported="false"`: PASS
- XML security configs present (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
- Release Hub origin HTTPS enforcement (`https://api-glean-hub.qm-hub-v001.cloud`): PASS
- Repository source secret audit: 0 hardcoded credentials: PASS
- Diagnostic Result: CLEAN (static source-code check only).

---

## 9. Hub Strict Upgrade CLI Suite (`hub-strict-upgrade.test.mjs`)

- Suite 1: CLI Operations & Fault Tolerance (9/9 PASS, ~10.8s)
  - Exit 0 on `--help`
  - Exit nonzero on missing token
  - Sensitive bearer token redaction in logs
  - Fast-fail on HTTP 4xx without polling
  - Preflight dry-run non-mutating
  - Bounded polling upgrade and read-back assertion
  - Idempotent recovery using migration ID
  - `--status-only` query and final assertion
  - Exit code 1 on column schema mismatch

---

## 10. Source Inspection & Tested Behavior

1. **Manifest Configuration Inspection**:
   - `DoggoAlarmReceiver` and `ReminderRestoreReceiver` are declared with `android:exported="false"` in `AndroidManifest.xml`.
   - `ReminderRestoreReceiver` declares intent-filters for `BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
   - Tested behavior: Unit tests verify the string action array and component export rules. Real OS broadcast intent reception requires on-device testing.

2. **Teardown Sequence Simulation**:
   - `CompositeLeaveSchedulerCancelGateTest` uses an anonymous `LeaveSchedulerCancelGate` implementation to test delivery-gate draining and cancellation ordering logic under test conditions.
   - Production class `CompositeLeaveSchedulerCancelGate` integrates `ReminderDeliveryGate`, active observation jobs, `SyncScheduler`, and `AndroidAlarmRegistry`. Unit tests confirm the failure-closed drain protocol when delivery admission is blocked.

3. **Notification Adapter & Intent Configuration**:
   - Source code in `AndroidNotificationAdapter` configures `NotificationCompat.Builder` with `VISIBILITY_PRIVATE` and `NotificationManager.IMPORTANCE_HIGH` on channel `doggo_reminders`.
   - Tested behavior: Unit tests verify deterministic request codes, tag string formatting (`accountKey:taskId`), and intent extra key constants.

4. **Permission Controller**:
   - Source code handles `POST_NOTIFICATIONS` and `canScheduleExactAlarms()` checks.
   - Tested behavior: Unit tests verify the permission string constant and sealed state class hierarchy. Platform permission grants, denials, and runtime dialog flows were not exercised on a device.

---

## 11. Validation Limitations

1. **No Instrumentation / Device Tests Executed**:
   - Android tests were executed via host-side Robolectric / JVM unit test runners (`testDebugUnitTest`). No physical device or emulator instrumented test run (`connectedCheck` / `androidTest`) was conducted.
2. **Exact Alarm & Doze Mode Untested at Runtime**:
   - `AlarmManager.setExactAndAllowWhileIdle()` delivery, power-saving bucket constraints, and Doze mode behavior require runtime device verification.
3. **Lockscreen Notification Privacy Not Visually Verified**:
   - `VISIBILITY_PRIVATE` configuration verified by code review; actual redacted lockscreen presentation was not exercised on an Android display.
4. **Broadcast Receiver Delivery Untested by Framework**:
   - Boot restoration and system time change broadcasts require OS broadcast simulation via `adb` or device reboot.
5. **Static Security Diagnostic Scope**:
   - `release-security-check.mjs --diagnose-source` verifies static manifest and source patterns only; it does not audit signed release APK binaries or runtime Keystore custody.

---

## 12. Recommendations & Next Steps

1. **Instrumentation & Device Smoke**: Execute device smoke test (`scripts/android-smoke.sh` or `connectedDebugAndroidTest`) on an emulator to verify `AlarmManager` wakeups and `ReminderRestoreReceiver` triggers via `adb shell am broadcast`.
2. **Phase 09 Linux Desktop Verification**: Verify Linux desktop integration with libsecret and desktop notification adapters.
3. **Phase 10 Windows Desktop Verification**: Verify DPAPI credential store and Windows MSI packaging.
4. **End-to-End Live Multi-Device Smoke**: Validate live strict-v2 cluster multi-device synchronization and concurrent alarm delivery across mobile and desktop.

---

## 13. Unresolved Questions

1. On Android 14+ (API 34+), should user-initiated revocation of `SCHEDULE_EXACT_ALARM` trigger a user-visible in-app banner explaining fallback to best-effort delivery, or remain silent until next task creation?
2. In multi-user / work profile Android configurations, should `ReminderRestoreReceiver` explicitly check `UserManager.isUserUnlocked()` prior to touching encrypted room files if launched before `USER_UNLOCKED`?
3. Should recurring reminder notifications accumulate separate status-bar items per task, or collapse into a single summary notification per task via notification group keys?
