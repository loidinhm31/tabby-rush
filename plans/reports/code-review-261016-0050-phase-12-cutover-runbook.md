# Code Review Report: Phase 12 Coordinated Cutover Runbook & Release Documentation

**Date**: 2026-10-16  
**Context**: Linux Fedora 44 x86_64 | JDK 21 LTS | Node.js v24.16.0 | Cargo 1.95.0 | pnpm 10.28.2  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md` (Phase 12)  
**Report File**: `plans/reports/code-review-261016-0050-phase-12-cutover-runbook.md`  

---

## Code Review Summary

### Score: 9.2/10

### Scope
- **Files Reviewed**:
  - `docs/strict-v2-cutover-runbook.md` (New authoritative cutover runbook)
  - `scripts/strict-v2-cutover-rehearsal.mjs` (New rehearsal & tooling self-test harness)
  - `scripts/hub-strict-upgrade.mjs` (CLI migration runner)
  - `scripts/hub-strict-recovery.js` (Quarantined strict-v1 emergency cold restore script)
  - `docs/deployment-guide.md`
  - `docs/security-and-recovery.md`
  - `docs/release-acceptance.md`
  - `docs/system-architecture.md`
  - `docs/hub-schema-integration.md`
  - `docs/sync-acceptance.md`
  - `docs/android-acceptance.md`
  - `docs/linux-acceptance.md`
  - `docs/windows-acceptance.md`
  - `docs/project-roadmap.md`
  - `docs/project-changelog.md`
  - `README.md`
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-12-coordinated-cutover-runbook-and-release.md`
  - `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`
  - Glean Hub: `docs/doggo-strict-sync.md`
- **Lines of Code & Docs Analyzed**: ~2,850 LOC/doc lines across Doggo and Glean Hub.
- **Review Focus**: Credential custody, protocol ordering, recovery state matrix, gate accounting honesty, code quality.

---

## 1. Security & Credential Custody Assessment

### 1.1 In-Process & Output Token Redaction
- **`hub-strict-upgrade.mjs`**:
  - `sanitizeLog()` strips `Bearer <token>` patterns and literal string occurrences of `activeToken` (`replaceAll(activeToken, '[REDACTED_TOKEN]')`).
  - Unit tests verify redaction in error messages (`hub-strict-upgrade.test.mjs` Test 3 PASS).
  - Remediation applied during review: `s.error` interpolations in lines 262 and 422 wrapped in `sanitizeLog()` to prevent backend-echoed authorization headers or tokens leaking to stdout/stderr.
- **`strict-v2-cutover-rehearsal.mjs`**:
  - Self-test Check 3 asserts CLI redacts sensitive admin tokens from stdout/stderr on network failure (PASS).
  - Child process spawning now sanitizes stdout/stderr outputs before emission.

### 1.2 Credential Custody & Process Argument Audit (`argv`)
- **Finding (Remediated)**: `scripts/strict-v2-cutover-rehearsal.mjs` previously invoked `runUpgradeCli` with `['--token', stagingToken]` in child arguments when targeting disposable staging origins. This leaked tokens onto `argv`, visible in `/proc/<pid>/cmdline` and `ps`.
  - **Fix Applied**: `runUpgradeCli` refactored to pass `stagingToken` strictly through environment parameter `env: { GLEAN_ADMIN_TOKEN: stagingToken }`, leaving `args` token-free.
- **Documentation Alignment**:
  - `docs/deployment-guide.md:328-333` updated to export `GLEAN_ADMIN_TOKEN` via environment instead of passing `--staging-token "$GLEAN_ADMIN_TOKEN"` on CLI argv.
  - `docs/strict-v2-cutover-runbook.md:46-51` updated with operational notes warning against bare `curl -H "$TOKEN"` on shared shells; recommends protected environment reads or curl config files (`-K`).

---

## 2. Protocol & Architecture Integrity

### 2.1 Chronological Deployment Sequence
Strict, unidirectional deployment ordering verified consistent across `docs/strict-v2-cutover-runbook.md`, `docs/deployment-guide.md`, `docs/system-architecture.md`, `docs/hub-schema-integration.md`, and Glean Hub `docs/doggo-strict-sync.md`:
1. **All-Instance Hub Deployment**: All Glean Hub nodes deployed with fence-aware binary; ingress continues serving `strict-v1`.
2. **Doggo Client Distribution**: Distribute Room v2 client binaries; client UI guards disable deadline/reminder submission with upgrade notice while backend is v1.
3. **Preflight Dry-Run**: `node scripts/hub-strict-upgrade.mjs --dry-run` verifies pre-conditions without mutating data.
4. **Controlled In-Place Upgrade**: `node scripts/hub-strict-upgrade.mjs --migration-id <UUID>` acquires fence in `_coordination`, drains admitted permits, transforms records in batches of 500, preserves tombstones `data: {}`, writes migration checkpoint in `_schemas.activeMigration`, validates 16 columns, atomically updates `_schemas` via CAS, and releases fence.
5. **Mandatory Final Read-Back Assertion**: GET `/api/v1/apps/doggo/strict-contract` asserts `syncMode: "strict-v2"`, canonical fingerprint `5f539551...`, all 16 canonical columns, and unchanged epoch UUID.
6. **Client Replay & Transition**: Connected clients execute `ContractTransitionCoordinator` lifecycle (`ready-v1` -> `upgrade-detected` -> `dispatch-quiesced` -> `legacy-reconciling` -> `v2-base-scanning` -> `safe-intents-translating` -> `ready-v2`), reconcile legacy receipts, and unlock deadline UI.

### 2.2 Canonical Invariants & Fingerprints
- `strict-v1`: 4 columns, canonical fingerprint `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`.
- `strict-v2`: 16 columns, canonical fingerprint `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`.
- **Read-Back Epoch Assertion Hardened**: `assertFinalContract()` in `scripts/hub-strict-upgrade.mjs` enhanced during review to validate `verified.epoch === expectedEpoch`, enforcing the invariant that epoch never rotates during feature migrations.
- **Already-Target Validation Hardened**: If server contract already reports target version/fingerprint, `hub-strict-upgrade.mjs` now runs full `assertFinalContract()` validation before exit 0 instead of blindly short-circuiting.

---

## 3. State-Specific Recovery Decision Matrix & Script Quarantine

### 3.1 Migration Failure State Matrix
The runbook defines exact operator procedures and safety invariants for all 6 migration states:
1. **Pre-Fence Rejection (HTTP 4xx)**: Clean v1; zero mutation; parameter/credential fix only.
2. **Fencing Stall / Orphan Permits**: Stop; keep fenced; escalate to DB operator; never clear permits via TTL or script.
3. **Interrupted Batch Transform**: Idempotent resume using exact same `--migration-id <UUID>`. Engine resumes from `checkpointRowId`. Runbook notes `--status-only` does not resume execution.
4. **Lost Publish Response**: Query status with `--status-only`; if `ready-v2`, verify contract; if pending, resume with same ID.
5. **Preactivation Rollback**: If abort occurs before v2 publish, backend strips 12 migration fields, asserts 4 columns, restores `ready-v1` under fence.
6. **Post-v2 Publish Failure**: **FORWARD REPAIR ONLY**. Regressing schema or restoring v1 backup is strictly prohibited to prevent erasing new user deadlines and corrupting offline client queues.

### 3.2 Legacy Script Quarantine
- `scripts/hub-strict-recovery.js` confirmed quarantined. It hardcodes a 4-column whitelist, drops/reprovisions target collections, and allocates a new external UUID epoch.
- Documentation in `docs/strict-v2-cutover-runbook.md:3.3` and `docs/security-and-recovery.md:3` clearly forbids its use during v2 upgrades or rollback.

### 3.3 Backup Consistency Requirement
- Runbook section 3.2 updated to explicitly note that sequential, uncoordinated collection dumps (`app_doggo`, `_schemas`, `_coordination`) during live traffic do not guarantee a point-in-time consistent cross-collection snapshot. Consistent backup requires maintenance fence or MongoDB replica-set oplog backup (`--oplog`).

---

## 4. Honest Gate Accounting

All release documentation maintains strict integrity regarding gate boundaries:
- **Gate G7 (Production Cutover)**: Formally **RUNBOOK DELIVERED / CUTOVER BLOCKED**. Staging rehearsal tooling exists; live production cutover is withheld.
- **Gate G2 (Platform Hardware)**: Formally **HELD**. Automated unit tests run on host JVM; physical Android device Doze timing (`setExactAndAllowWhileIdle`), desktop Linux visual D-Bus notification display, and native Windows WinRT/COM toast bridge (candidate stub returns `Unavailable`) are clearly disclosed as pending physical hardware.
- **Gate G3 (Cluster Staging & Backend Remediation)**: Formally **BLOCKED**. `test_strong_sync_live_mongo_acceptance_gate` ignored due to absent `MONGO_TEST_URI`; 3 open Phase 03 code findings (permit release duplicate filter, `permitCount` backfill, batch/checkpoint write atomicity) remain open engineering prerequisites.
- **Gate G6 (Three-Device Live Sync)**: Formally **HELD** pending physical multiplatform hardware and live cluster testbed.
- **Supply-Chain Integrity**: Disclosed that Gradle test runs required `--dependency-verification=off` due to 149 unmatched tooling checksums; metadata audit and strict verification enforcement remain a required pre-release gate.

---

## 5. Code Quality & Error Handling

- **YAGNI / KISS**: `scripts/strict-v2-cutover-rehearsal.mjs` uses a clean in-process Node.js HTTP mock without external dependencies.
- **DRY**: Shared canonical column definitions and SHA-256 fingerprints match backend and schema definitions.
- **Fail-Closed Exits**:
  - `hub-strict-upgrade.mjs` exits with code 1 on precondition mismatch, unauthorized tokens, terminal error states, or column divergence; exits code 2 on timeouts.
  - `strict-v2-cutover-rehearsal.mjs` fast-fails with exit code 1 if `--staging-origin` is used without `--confirm-disposable-staging` or without admin token.
  - Added `child.on('error', ...)` handler in `runUpgradeCli` to prevent unhandled promise hangs if executable fails to spawn.

---

## 6. Critical Issues, Warnings & Suggestions

### Critical Issues
*None remaining.* (Credential custody on argv, token redaction in `s.error`, and epoch assertion gaps were remediated during this review).

### Warnings
1. **Windows Native Toast Gap (Gate G2)**: `:desktopApp` candidate adapter currently falls back to `Unavailable` stub. Must implement native WinRT/COM toast dispatch prior to public Windows release.
2. **Three Phase 03 Backend Code Remediations (Gate G3)**: In `glean-hub-sync`, permit release duplicate filter, `permitCount` legacy backfill, and batch/checkpoint write atomicity must be patched before staging cluster validation.
3. **Supply-Chain Checksum Audit**: 149 unmatched checksums in `gradle/verification-metadata.xml` must be verified against upstream repository signatures and re-locked (`--dependency-verification=strict`).

### Suggestions
1. **Curl Config File Helper**: Add a convenience shell wrapper or Makefile target that creates a temporary protected curl config file for manual contract inspections, completely eliminating `$GLEAN_ADMIN_TOKEN` from operator terminal histories.
2. **Dry-Run Staging Metric Verification**: Extend rehearsal tooling to parse and assert record count estimates reported during staging dry-run executions.

---

## 7. Validation Commands & Execution Evidence

| Command / Suite | Category | Results |
|---|---|:---:|
| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | Rehearsal Self-Test | **7/7 PASS** (10.61s) |
| `node scripts/hub-strict-upgrade.test.mjs` | CLI Upgrade Test | **9/9 PASS** (10.87s) |
| `node scripts/strict-v2-vector-runner.mjs` | Wire Vectors | **23/23 PASS** (0.05s) |
| `node scripts/hub-live-smoke.mjs --fixture-test` | Offline Protocol Fixture | **18/18 PASS** (0.52s) |
| `node scripts/release-security-check.mjs --diagnose-source` | Static Security Audit | **6/6 PASS** (0.08s) |
| `node scripts/debug-fault-proxy.test.mjs` | Fault Injection Suite | **8/8 PASS** (0.16s) |

---

## 8. Unresolved Questions

1. **Windows Toast Dispatch Architecture**: Will the native Windows toast implementation in `:desktopApp` be completed via JNA direct COM/WinRT bindings, or via a packaged helper binary?
2. **Staging Multi-Instance Testbed Timeline**: What is the scheduled timeline for provisioning the multi-node MongoDB replica set and multi-instance Hub staging environment to execute Gate G3 validation?
3. **Upstream Dependency Key Audit**: Will Gradle dependency verification metadata be regenerated against upstream repository keyrings before Phase 13 release packaging?
