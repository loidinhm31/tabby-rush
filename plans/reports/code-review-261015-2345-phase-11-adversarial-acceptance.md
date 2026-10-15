# Code Review Summary: Phase 11 — Adversarial Integration and Platform Acceptance

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer (Phase11Reviewer)  
**Target Phase**: `Phase 11 — Adversarial integration and separately observed platform acceptance`  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-11-adversarial-integration-and-platform-acceptance.md`  
**Parent Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`  
**QA Validation Base**: `plans/reports/qa-261015-2330-phase-11-validation.md`  
**Report Artifact**: `plans/reports/code-review-261015-2345-phase-11-adversarial-acceptance.md`  
**Overall Score**: **8.6 / 10** (Automated Regression & Packaging Targets Approved; Gate G6 End-to-End Platform Acceptance Formally Held)

---

### Scope
- **Files Reviewed (6 files)**:
  - `shared/src/commonMain/kotlin/dev/doggo/shared/contract/persisted-schedule-projector.kt` (token assignment and preservation in draft-free projection)
  - `scripts/debug-fault-proxy.mjs` (route allowlist, sensitive URL path redaction in 403 logs, client abort cleanup, fault injection scenarios)
  - `scripts/debug-fault-proxy.test.mjs` (route allowlist, pass-through, sensitive path log redaction, fault drop, and upstream 502 error tests)
  - `shared/src/commonTest/kotlin/dev/doggo/shared/contract/adversarial-scheduler-boundary-test.kt` (repetition bounds, arithmetic overflow, date-only 09:00 morning overdue, grace window 300,000ms vs 300,001ms, draft isolation, cancellation barrier, token preservation)
  - `shared/src/jvmTest/kotlin/dev/doggo/shared/data/adversarial-sync-sqlite-test.kt` (crash recovery, frozen retry invariance, v1 title rebase over v2 deadline, local deadline removal, UNKNOWN_SUPERSEDED conflict lock)
  - `plans/reports/qa-261015-2330-phase-11-validation.md` (399 targets: 398 passed, 0 failed, 1 ignored live Mongo gate)
- **Lines of Code Analyzed**: ~1,120 lines of new and modified code across Kotlin, JavaScript, and Markdown.
- **Review Focus**: Security, Performance, Architecture (DRY, KISS, YAGNI), Token Invariance, SQLite Crash Boundaries, and Honest Platform Gate Accounting.
- **Updated Plans**:
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-11-adversarial-integration-and-platform-acceptance.md`
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`

---

### Overall Assessment
Phase 11 delivers rigorous adversarial boundary regressions, physical SQLite restart/crash invariance suites, debug fault-injection tooling, and verified desktop/Android packaging. In `PersistedScheduleProjector`, a critical scheduler desynchronization bug was fixed where reminder-only edits failed to advance `scheduleToken`, risking missed alarm updates. Security hardening in `debug-fault-proxy.mjs` completely prevents sensitive URL path leakage into logs and guarantees client abort cleanup.
All 399 automated targets across Doggo and Glean Hub completed with 100% pass of executable targets (398 passed, 0 failed, 1 ignored live Mongo gate). However, in accordance with the project's non-negotiable honesty policy regarding native platforms, **Automated adversarial validation delivered; platform/cluster acceptance blocked**. Physical Android Doze timing (`setExactAndAllowWhileIdle`), live Linux D-Bus visual notification bubble display, Windows COM/WinRT toast bridge implementation, and multi-node MongoDB replica set live migration remain held on respective hardware testbeds and backend remediations. Production cutover remains blocked.

---

### Critical Issues (Remediated During Phase 11)

1. **[CRITICAL / SCHEDULER DESYNCHRONIZATION] Reminder-Only Modifications Failed to Update `scheduleToken` in Projection**
   - **File**: `shared/src/commonMain/kotlin/dev/doggo/shared/contract/persisted-schedule-projector.kt:138-140`
   - **Root Cause**: When folding outbox operations under `OutboxEntity.INTENT_REMINDER`, `PersistedScheduleProjector` reconstructed `reminderRule` but never assigned `scheduleToken = op.scheduleToken`.
   - **Impact**: While `RoomAccountStore.saveTodoLocally` rotated `scheduleToken` upon reminder rule modification, the projected schedule emitted the stale base token. `DesktopReminderScheduler` and `AndroidNotificationAdapter` rely on `scheduleToken` comparisons against stored occurrences to detect schedule changes; emitting a stale token caused native alarm reconciliation to skip rescheduling, leaving stale alarms active or dropping new reminder rules.
   - **Remediation Verified**: Lines 138–140 now assign `scheduleToken = op.scheduleToken` when `deadline != null && op.scheduleToken != null`. Tested and verified via `testScheduleTokenAssignmentAndPreservation` in `AdversarialSchedulerBoundaryTest.kt`, asserting that title-only changes preserve the token, reminder modifications advance the token, and deadline clearing resets the token to `null`.

---

### Warnings & High Priority Findings

1. **[HOLD / PLATFORM HARDWARE & ADAPTERS] Gate G2 Acceptance Formally Held on Missing Testbeds and Windows Implementation Gap**
   - **Assessment**:
     - *Android*: Unit tests pass (`:androidApp:testDebugUnitTest`), but live connected device/emulator Doze timing (`setExactAndAllowWhileIdle`), power-off reboot restore broadcasts (`BOOT_COMPLETED`), and system notification center banners require a physical device or emulator testbed.
     - *Linux Desktop*: Unit tests pass (`:desktopApp:test`), but headless workstation environments lack an active Freedesktop D-Bus notification daemon (`org.freedesktop.Notifications`). Visual bubble display and interaction require an interactive desktop session or Xvfb/dunst testbed.
     - *Windows Desktop*: `JnaWindowsToastBridge` is an **unimplemented candidate stub** returning `WindowsNotificationStatus.Unavailable`. Native WinRT/COM toast posting requires direct implementation and validation on a physical Windows testbed.
   - **Impact**: AC08, AC09, and AC10 cannot be marked as passed for production release without physical testbed verification.

2. **[HOLD / BACKEND CLUSTER] Gate G3 Live Replica Set Migration Held with 3 Open Phase 03 Findings**
   - **Assessment**:
     - Backend crate `glean-hub-sync` passed 54/54 tests, with 1 test (`test_strong_sync_live_mongo_acceptance_gate`) explicitly ignored due to absent `MONGO_TEST_URI`.
     - In addition to the missing physical replica set cluster, three open Phase 03 code findings remain unaddressed:
       1. Unconditional permit release counter decrement without filtering on `activePermits.<permitId>: { $exists: true }`
       2. Missing `permitCount` backfill in legacy coordination documents for numeric `$lt` predicates
       3. Batch mutation and checkpoint update non-atomicity with ignored checkpoint errors
   - **Impact**: Live two-node migration fencing, request permit draining, and concurrent write exclusion remain on staging hold.

3. **[BUILD / CONFIGURATION] Strict Dependency Verification Requires `--dependency-verification=off`**
   - **Assessment**: Gradle builds fail with 149 unmatched classpath checksums in `gradle/verification-metadata.xml` (affecting AGP 8.7.3 and Kotlin 2.1.10 tooling jars). All Gradle tasks currently require `--dependency-verification=off`.
   - **Impact**: Release cutover in Phase 12 must regenerate verification metadata (`--write-verification-metadata sha256`) to satisfy strict supply chain security standards.

---

### Medium Priority Improvements

1. **Upstream Request Timeout in `debug-fault-proxy.mjs`**
   - **File**: `scripts/debug-fault-proxy.mjs:66-72`
   - **Issue**: `http.request` does not configure a socket or request timeout. If upstream Hub hangs during fault injection, proxy requests will remain open indefinitely until client aborts.
   - **Recommendation**: Add `{ timeout: 30000 }` to upstream request options and handle the `'timeout'` event by emitting a 504 Gateway Timeout or terminating the upstream request.

2. **Temporal Fixture Standardization Across Test Suites**
   - **Files**: `AdversarialSchedulerBoundaryTest.kt` & `AdversarialSyncSqliteTest.kt`
   - **Issue**: Earlier iterations used `-14400L` (EDT) for `2026-11-01 15:00` before being corrected to `-18000L` (EST) due to the 02:00 DST fallback.
   - **Recommendation**: Ensure shared test helpers expose canonical instant constants (e.g. `TestInstants.NY_FALL_BACK_2026_EST`) to prevent recurring manual offset mismatches in future test authoring.

---

### Low Priority Suggestions

1. **Configurable Fault Drop Delay in Fault Proxy**
   - **File**: `scripts/debug-fault-proxy.mjs:85-89`
   - **Issue**: Fault drop immediately destroys client socket upon upstream response end (`clientRes.destroy()`).
   - **Recommendation**: Add `--drop-delay-ms <ms>` option to simulate slow-drip connection stalls and read timeouts in addition to immediate connection hangups.

---

### Positive Observations
- **Rigorous SQLite Restart Testing**: `AdversarialSyncSqliteTest.kt` verifies disk persistence, frozen retry invariance, and `UNKNOWN_SUPERSEDED` conflict isolation across physical DB close and reopen cycles against real SQLite storage.
- **Sensitive Log Redaction**: `debug-fault-proxy.mjs` completely suppresses unlisted path reflection, logging only the HTTP method. Validated with active `console.warn` inspection in `debug-fault-proxy.test.mjs`.
- **Draft & Cancellation Isolation**: `AdversarialSchedulerBoundaryTest.kt` verifies that uncommitted editor drafts never leak into schedule projections and that completed/deleted/disabled tasks are excluded from scheduling.
- **Checked Arithmetic & 5-Minute Grace**: Boundary tests prove exact compliance with the 5-minute grace window (`<= 300,000ms` eligible, `300,001ms` skipped, no resurrection of consumed opportunities) and checked 64-bit arithmetic overflow safety.

---

### Recommended Actions
1. **Proceed to Phase 12 Runbook Planning**: Author the Phase 12 coordinated cutover runbook and release procedures, but explicitly maintain Gate G6 as a deployment blocker until physical testbeds are provisioned.
2. **Remediate Phase 03 Backend Findings Before Gate G3 Staging**: Address permit release filtering, `permitCount` backfill, and batch/checkpoint write consistency in `glean-hub-sync` before running the live replica set acceptance test.
3. **Regenerate Gradle Verification Metadata**: Execute `./gradlew --write-verification-metadata sha256` in Phase 12 to eliminate the need for `--dependency-verification=off`.
4. **Implement Native Windows Toast Bridge**: Replace `JnaWindowsToastBridge` stub with native WinRT/COM integration before running Phase 12 Windows release verification.

---

### Metrics & Verification Results

```markdown
- Total Checks / Targets: 399 targets (398 passed, 0 failed, 1 ignored)
  - Unit & Integration Test Cases: 384 passed
    - Doggo Shared JVM & SQLite Tests (:shared:jvmTest --rerun-tasks): 202 passed (28 classes)
    - Doggo Desktop App Tests (:desktopApp:test): 49 passed (10 classes)
    - Doggo Android Debug Unit Tests (:androidApp:testDebugUnitTest): 12 passed (4 classes)
    - Doggo Wire Vector Runner (scripts/strict-v2-vector-runner.mjs): 23 passed
    - Doggo Hub Fixture Smoke (scripts/hub-live-smoke.mjs --fixture-test): 18 passed
    - Doggo Hub Upgrade CLI Suite (scripts/hub-strict-upgrade.test.mjs): 9 passed
    - Doggo Fault Proxy Suite (scripts/debug-fault-proxy.test.mjs): 6 passed
    - Glean Hub Backend Crate Tests (cargo test -p glean-hub-sync --locked): 54 passed (1 ignored live Mongo gate)
    - Glean Hub Admin App Unit Tests (pnpm test:unit): 13 passed (2 suites)
  - Packaging Build Targets: 2 passed
    - Desktop Distributable (:desktopApp:createDistributable): PASS
    - Android Debug APK (:androidApp:assembleDebug): PASS (20.68 MB)
  - Static Security & Linter Targets: 9 passed
    - Android Release Security Diagnostic (scripts/release-security-check.mjs): 6 passed
    - Canonical Schema Fingerprints: 2 verified
    - Glean Hub Clippy (cargo clippy -p glean-hub-sync --locked): PASS (0 warnings, 0 errors)
- Execution Flags: --dependency-verification=off (due to 149 tooling classpath checksums in verification-metadata.xml)
- Code Review Score: 8.6 / 10 (Automated targets approved; Gate G6 end-to-end acceptance held)
```


### Follow-Up Review Addendum (Timeout & Stream Error Hardening)
- **Changes Reviewed**:
  - `scripts/debug-fault-proxy.mjs`: Added configurable `upstreamTimeoutMs` (30s default), socket inactivity timeout handling returning 504 Gateway Timeout before headers, response stream inactivity timeout and client abort destruction, and `upstreamRes` stream error propagation.
  - `scripts/debug-fault-proxy.test.mjs`: Added tests for pre-header timeout (504 Gateway Timeout) and mid-stream response stall (`res.flushHeaders()` followed by stall) verifying clean client socket destruction.
- **Validation**: `node scripts/debug-fault-proxy.test.mjs` passes 8/8 tests in 155ms.
- **Status**: Confirmed robust; no sockets left hanging on upstream or downstream stall.
---

### Unresolved Questions
1. For Phase 12 release verification, will `gradle/verification-metadata.xml` be updated via `--write-verification-metadata sha256` or will AGP/Kotlin compiler classpath checksums be verified via trusted keys?
2. For Gate G2 platform acceptance on Linux CI runners, should a headless virtual display with mock D-Bus notification daemon (`dunst` inside `Xvfb`) be provisioned, or is an interactive graphical workstation session required?
3. For Gate G2 Windows acceptance, will Phase 12 deliver the native WinRT/COM implementation for `JnaWindowsToastBridge` directly or require an external PowerShell toast bridge binary?
4. When deploying the staging MongoDB replica set for Gate G3, will the 3 open Phase 03 code findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint atomicity) be patched prior to cluster admission testing?
