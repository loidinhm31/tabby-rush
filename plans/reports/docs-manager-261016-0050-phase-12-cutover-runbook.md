# Documentation Specialist Report: Phase 12 Coordinated Cutover Runbook & Release Documentation

**Date**: 2026-10-16  
**Phase**: Phase 12 — Coordinated cutover, recovery runbook and release documentation  
**Status**: DOCUMENTATION & REHEARSAL TOOLING AUDIT DELIVERED (Live production cutover Gate G7 formally BLOCKED pending physical platform delivery, cluster staging, and multiplatform live sync)  
**Author**: Phase12DocsManager (Senior Technical Documentation Specialist)  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md`  
**Report File**: `plans/reports/docs-manager-261016-0050-phase-12-cutover-runbook.md`  

---

## 1. Executive Summary & Audit Verdict

Comprehensive documentation audit and code-to-documentation synchronization completed for Phase 12 of the Task Deadlines, Reminders, and Strict-Contract Upgrades initiative.

### 1.1 Core Audit Verdict
- **Documentation & Tooling Delivery**: **100% COMPLETE**. Authoritative operational runbook (`docs/strict-v2-cutover-runbook.md`), rehearsal tooling harness (`scripts/strict-v2-cutover-rehearsal.mjs`), CLI runner security hardenings (`scripts/hub-strict-upgrade.mjs`), disaster recovery script documentation quarantine (`scripts/hub-strict-recovery.js`), and cross-repository operational documentation reconciliation are fully delivered and audited.
- **Production Cutover Verdict**: **RUNBOOK & REHEARSAL DELIVERED / LIVE PRODUCTION CUTOVER STRICTLY BLOCKED**.
- **Gate G7 Boundary Notice**: In-process mock rehearsal and CLI unit tests confirm client-side orchestration, token redaction, and schema assertion logic; they do **NOT** establish live cluster migration or production acceptance. Live production cutover remains formally blocked pending:
  1. **Gate G2 (Platform Hardware & Adapters)**: Connected Android physical device Doze idle wakeups (`setExactAndAllowWhileIdle`), graphical Linux desktop D-Bus notification display, and implementation of the Windows native COM/WinRT toast notification bridge (current candidate stub in `:desktopApp` returns safe `Unavailable`).
  2. **Gate G3 (Cluster Staging & Backend Remediations)**: Live multi-instance MongoDB replica set migration execution (`test_strong_sync_live_mongo_acceptance_gate`), plus remediation of three open Phase 03 Rust backend code findings in `glean-hub-sync` (duplicate permit release counter drift, `permitCount` schema backfill, and batch/checkpoint write atomicity).
  3. **Gate G6 (Three-Device Concurrent Sync)**: Live concurrent round-trip synchronization between Android, Linux, and Windows.
  4. **Supply-Chain Dependency Integrity**: Re-locking Gradle verification metadata (`gradle/verification-metadata.xml`) against trusted upstream keyrings to clear `--dependency-verification=off`.
  5. **Formal Production Operator Authorization**: Operator review of restorable journaled backups and staging drill logs.

---

## 2. Documentation Inventory & Scope Assessment

### 2.1 File Size & Limits Audit (`docs.maxLoc: 800`)
Every markdown file in `docs/` was audited for line count compliance (`wc -l docs/*.md`). All 29 files remain strictly under the 800 LOC ceiling:

| File Path | LOC | Status | Category / Primary Scope |
|---|:---:|:---:|---|
| `docs/project-changelog.md` | 701 | PASS | Chronological project changelog across all phases |
| `docs/system-architecture.md` | 691 | PASS | System architecture, multiplatform design & Phase 12 section |
| `docs/codebase-summary.md` | 606 | PASS | Codebase inventory, Repomix metrics & current invariants |
| `docs/project-roadmap.md` | 566 | PASS | Phase progress roll-up, milestone schedule & changelog |
| `docs/local-storage-and-sync.md` | 506 | PASS | Room KMP entities, SQLite persistence & sync contracts |
| `docs/project-overview-pdr.md` | 357 | PASS | Product requirements, traceability & Phase 01–12 boundaries |
| `docs/deployment-guide.md` | 334 | PASS | Hub, mobile, desktop operations & strict-v2 cutover sequence |
| `docs/code-standards.md` | 256 | PASS | Coding, shell, testing, temporal & cutover conventions |
| `docs/hub-schema-integration.md` | 240 | PASS | Hub schema admission, registration & upgrade tooling |
| `docs/strict-v2-cutover-runbook.md` | 237 | PASS | Phase 12 authoritative operator cutover and recovery runbook |
| `docs/headless-android-setup.md` | 232 | PASS | Headless Xpra, virtual display & Android environment runbook |
| `docs/api-contract.md` | 204 | PASS | REST endpoints, route classification & strict wire protocol |
| `docs/android-acceptance.md` | 203 | PASS | Android native adapters, receivers, permissions & Gate G2 hold |
| `docs/release-acceptance.md` | 146 | PASS | Verification matrix, test target inventory & Gate G7 status |
| `docs/security-and-recovery.md` | 146 | PASS | Platform credential custody, DPAPI limits & recovery boundaries |
| `docs/windows-desktop-guide.md` | 140 | PASS | Windows build, packaging, DPAPI vault & operations guide |
| `docs/design-guidelines.md` | 134 | PASS | Visual design tokens, WCAG 2.1 AA palette & UI standards |
| `docs/linux-desktop-guide.md` | 133 | PASS | Linux desktop build, Secret Service & packaging runbook |
| `docs/windows-acceptance.md` | 114 | PASS | Windows acceptance criteria, DPAPI custody & toast stub notice |
| `docs/linux-acceptance.md` | 93 | PASS | Linux D-Bus acceptance criteria, packaging & session hold |
| `docs/host-tooling-evidence.md` | 87 | PASS | Host virtualization, OpenJDK, Node & Xpra capabilities |
| `docs/authentication-and-credentials.md` | 84 | PASS | Auth state machine, route policies & credential lifecycle |
| `docs/sync-acceptance.md` | 66 | PASS | Strict-v1 and strict-v2 sync acceptance test matrix |
| `docs/toolchain-versions.md` | 64 | PASS | Pinned toolchain versions (AGP, Room, Kotlin, Compose, JVM) |
| `docs/auth-acceptance.md` | 60 | PASS | Auth client, token refresh & vault acceptance matrix |
| `docs/foundation-evidence.md` | 55 | PASS | Phase 02 cryptographic digests & wrapper SHA-256 evidence |
| `docs/hub-compatibility-acceptance.md` | 54 | PASS | Hub architectural acceptance criteria & isolation invariants |
| `docs/font-provenance.md` | 53 | PASS | Font provenance audit and system font delegation decision |
| `docs/development-roadmap.md` | 30 | PASS | Architectural roadmap and module trajectory |
| **Total Documentation Size** | **6,592** | **PASS** | **29 files checked; 0 files exceed 800 LOC threshold** |

### 2.2 Link Hygiene & Validator Audit
Execution of the documentation validator (`node ~/.omp/agent/evcrate/scripts/validate-docs.cjs docs/`) confirms:
- **Internal Cross-References**: **194 working internal markdown links, 0 broken links**. All inter-document anchors and relative paths resolve cleanly.
- **Validator Scanner Nuances**:
  - *Code References (1,422 potential issues flagged)*: The validator’s regex heuristic searches `src/`, whereas Doggo’s Kotlin Multiplatform code resides in module-specific directories (`shared/src/commonMain/`, `androidApp/src/main/`, `desktopApp/src/`). All primary classes documented (`DoggoApplication`, `RoomAccountStore`, `ContractTransitionCoordinator`, `PersistedScheduleProjector`, `ReminderOccurrenceStore`, etc.) were verified directly in repository source files.
  - *Config Keys (543 potential issues flagged)*: The validator compares uppercase tokens against `.env.example`. These tokens represent Android intent actions (`ACTION_BOOT_COMPLETED`), notification constants (`IMPORTANCE_HIGH`), visibility flags (`VISIBILITY_PRIVATE`), and Gradle signing properties (`DOGGO_KEYSTORE`). Legitimate environment variables (`APPROVED_HUB_ORIGIN`, `GLEAN_ADMIN_TOKEN`, `MONGO_URI`) are verified in runbooks.

---

## 3. Repomix Compaction & Codebase Summary

### 3.1 Repomix Compaction Artifact (`repomix-output.xml`)
The codebase was compacted using `repomix` (v1.18.0) into `repomix-output.xml`:
- **Compaction Date**: 2026-10-16
- **Total Files Packed**: 418 files
- **Total Tokens**: 962,265 tokens
- **Total Characters**: 4,163,193 characters
- **Top Files by Token Count**:
  1. `docs/project-roadmap.md` (30,931 tokens, 133,991 chars, 3.2%)
  2. `docs/codebase-summary.md` (23,810 tokens, 104,202 chars, 2.5%)
  3. `docs/system-architecture.md` (23,175 tokens, 103,366 chars, 2.4%)
  4. `docs/project-changelog.md` (20,180 tokens, 88,805 chars, 2.1%)
  5. `plans/261015-0752-task-deadlines-reminders-strict-upgrades/plan.md` (14,407 tokens, 62,295 chars, 1.5%)

### 3.2 Security Scanner Heuristic Exclusions
Repomix flagged 5 suspicious files that were excluded from the output by its default heuristic filters:
1. `docs/security-and-recovery.md` (flagged on placeholder URI pattern: `mongodb+srv://<db_user>:<db_password>@...`)
2. `plans/reports/code-reviewer-261014-1624-phase-08-release-operations-cycle2.md` (mock test token strings)
3. `plans/reports/code-reviewer-261014-1638-phase-08-release-operations-cycle3.md` (mock test token strings)
4. `plans/reports/docs-manager-261014-1643-phase-08-docs-verification.md` (mock test token strings)
5. `shared/src/commonTest/kotlin/dev/doggo/shared/contract/endpoint-policy-test.kt` (test URI patterns)

*Direct Inspection Finding*: Each flagged file was inspected directly as a primary source. Zero real production secrets, passwords, or private keys exist; all flagged patterns are non-functional documentation templates or unit test fixtures. `docs/security-and-recovery.md` was audited independently to verify its operational contents.

### 3.3 Codebase Summary Updates (`docs/codebase-summary.md`)
`docs/codebase-summary.md` was updated to incorporate:
- Refreshed Repomix compaction metrics dated 2026-10-16, explicitly labeling it a dated partial snapshot with 5 scanner exclusions.
- Updated high-level directory structure reflecting `scripts/strict-v2-cutover-rehearsal.mjs`, `scripts/hub-strict-upgrade.mjs`, and `docs/strict-v2-cutover-runbook.md`.
- New item 50 in Section 4 (Current State & Invariants) detailing Phase 12 cutover runbook architecture, rehearsal harness, CLI hardenings, script quarantine, and Gate G7 blocker boundaries.

---

## 4. Detailed Audit of Phase 12 Changed Files

A line-by-line audit was conducted across all 18 modified files, 4 untracked files in Doggo, and the sibling documentation file in Glean Hub:

### 4.1 Authoritative Cutover Runbook (`docs/strict-v2-cutover-runbook.md`)
- **Structure**: 7 comprehensive sections covering Executive Summary, Operator Prerequisites & Authorization, Pre-Cutover Backup & Verification, Coordinated Deployment Sequence, Post-Cutover Verification Checklist, Recovery & Incident Decision Matrix, and Archive & Sign-Off.
- **Invariants Enforced**: Zero data loss (in-place `app_doggo` transformation), strict epoch invariance (preserving `strictEpoch` UUID without rotation), monotonic revision and tombstone preservation (`data: {}`), request permit draining, and atomic schema CAS.
- **Credential Custody Precision**: Warns operators against passing tokens on command-line `argv` (visible in `/proc/<pid>/cmdline` and `ps`). Recommends reading from `$GLEAN_ADMIN_TOKEN` or passing via curl configuration files (`-K`). *Precision Note*: Environment variables prevent process-listing snooping, but do not protect against same-user inspection via `/proc/<pid>/environ`; shell tracing (`set -x`) and unredacted logging are explicitly forbidden.
- **Backup Consistency Requirement**: Explicitly notes that sequential, uncoordinated collection dumps during live traffic do not guarantee a point-in-time consistent cross-collection snapshot. Recommends maintenance fence or replica-set oplog dump (`--oplog`).
- **Incident Recovery Matrix**: Explicit procedures across 6 states: (1) Pre-fence rejection (clean v1, 0 mutation), (2) Fencing stall / orphan permits (keep fenced, escalate, never clear via script), (3) Interrupted batch transform (idempotent resume via same migration ID), (4) Lost publish response (read-back verify), (5) Preactivation rollback under fence (strip 12 added fields only, restore `ready-v1`), (6) Post-v2 publication failure (**forward repair only**; rollback or restoring v1 backups is strictly forbidden).

### 4.2 Rehearsal & Tooling Self-Test Harness (`scripts/strict-v2-cutover-rehearsal.mjs`)
- **Real CLI Execution**: Confirmed that `runUpgradeCli()` directly executes `node scripts/hub-strict-upgrade.mjs` via `child_process.spawn`. It does **NOT** duplicate CLI logic in mock stubs.
- **Execution Evidence**: Self-test suite (`--self-test`) executed cleanly: **7/7 PASS** in 10.84s wall.
- **Verified Branches**:
  1. CLI help output and parameter documentation.
  2. Missing token fail-closed exit with code 1.
  3. Sensitive token redaction across stdout and stderr.
  4. Non-mutating preflight dry-run verification.
  5. Interrupted batch recovery using identical migration UUID.
  6. Status inspection (`--status-only`) asserting status without initiating mutation.
  7. Final schema read-back column mismatch detection and fail-closed exit.
- **Hardened Child Invocation**: Passes admin token via child process `env: { GLEAN_ADMIN_TOKEN }` rather than command-line arguments.

### 4.3 CLI Upgrade Runner Hardenings (`scripts/hub-strict-upgrade.mjs`)
- **Token Redaction**: `sanitizeLog()` strips `Bearer <token>` patterns and literal token strings. Wrapped around `s.error` logs to prevent backend-echoed authorization headers or tokens leaking to output streams.
- **Epoch Invariance Assertion**: `assertFinalContract()` enhanced with `expectedEpoch` check: `if (expectedEpoch && verified.epoch && verified.epoch !== expectedEpoch) process.exit(1)`.
- **Already-Target Validation**: If server contract already reports target version/fingerprint, runs full `assertFinalContract()` verification before exit 0 instead of blind short-circuiting.
- **Test Execution**: CLI test suite (`node scripts/hub-strict-upgrade.test.mjs`) passed **9/9 tests** in 10.84s.

### 4.4 Emergency Recovery Script Quarantine (`scripts/hub-strict-recovery.js`)
- **Quarantine Classification**: Documented as **documentation-only operational quarantine**. The script contains warning comments and checks `CONFIRM_MAINTENANCE_QUIESCENCE=true`, but does not contain an automated technical block preventing execution against a v2 collection.
- **Safety Documentation**: `docs/strict-v2-cutover-runbook.md:93` and `docs/security-and-recovery.md:46` clearly instruct operators that `hub-strict-recovery.js` hardcodes a 4-column whitelist, drops/recreates collections, and allocates a new UUID epoch; executing it against `strict-v2` destroys temporal metadata and causes irreversible client conflicts.

### 4.5 Deployment Guide (`docs/deployment-guide.md`)
- Version bumped to 1.4.0 (Phase 12).
- Section 9 added: "Strict-v2 Protocol Migration & Cutover Operations", documenting the 6-stage deployment sequence, CLI execution examples using environment variables, and cross-references to the authoritative runbook.

### 4.6 Security & Recovery Architecture (`docs/security-and-recovery.md`)
- Version bumped to 1.3.0 (Phase 12).
- Section 3 retitled to emphasize "Strict-v1 Fresh-Epoch Recovery (Emergency Cold Restore Only)" with explicit quarantine notice.
- Section 4 added: "Strict-v2 Feature Upgrade & Incident Recovery Boundaries", articulating the architectural distinction between in-place non-destructive feature evolution (preserving epoch and collection) versus catastrophic cold recovery.

### 4.7 Release Acceptance Specification (`docs/release-acceptance.md`)
- Version bumped to 2.1.0 (Phase 12).
- Gate G7 documented with status: **RUNBOOK DELIVERED / CUTOVER BLOCKED**.
- Verification Table updated: Added Group 15 (`scripts/strict-v2-cutover-rehearsal.mjs --self-test`, 7/7 PASS), bringing the reported target inventory to **404 targets across 15 groups** (400 automated test/contract/rehearsal checks: 399 passed, 0 failures, 1 ignored live Mongo gate; plus 4 build, lint, and packaging targets).

### 4.8 System Architecture (`docs/system-architecture.md`)
- Header metadata updated to Phase 12.
- Section 6 retitled to "Deadlines, Reminders & Strict Upgrade Design (Phases 01–12 Status)".
- Section 6.13 added: "Coordinated Cutover, Recovery Runbook & Release Documentation (Phase 12 Delivered)", summarizing deployment ordering, recovery matrix, rehearsal tooling, and honest Gate G7 release boundaries.

### 4.9 Schema Integration & Sync Acceptance (`docs/hub-schema-integration.md`, `docs/sync-acceptance.md`)
- `docs/hub-schema-integration.md`: Documented Phase 12 cutover runbook and rehearsal tooling.
- `docs/sync-acceptance.md`: Section 4 added detailing strict-v2 migration, intent rebasing, and replay acceptance.

### 4.10 Platform Acceptance Docs (`docs/android-acceptance.md`, `docs/linux-acceptance.md`, `docs/windows-acceptance.md`)
- Maintained historical provenance: Document versions bumped to 1.3.0 (Android/Linux) and 1.2.0 (Windows) reflecting Phase 12 release documentation updates while preserving originating phase details (Phase 08 Android, Phase 09 Linux, Phase 10 Windows).
- Explicitly integrated cutover sequence behavior (client Room v2 database upgrade, UI deadline submission disabled while server is strict-v1).
- Maintained honest Gate G2 status: Disclosed that Android physical Doze timing, desktop Linux visual D-Bus bubble display, and Windows native COM/WinRT toast bridge (candidate stub returns `Unavailable`) remain held pending physical hardware.

### 4.11 Roadmap & Changelog (`docs/project-roadmap.md`, `docs/project-changelog.md`)
- `docs/project-roadmap.md`: Executive summary and Section 2.2 table updated to reflect Phase 12 delivery and 236h / 236h (100%) planned effort roll-up, with comprehensive footnote 80. Added Phase 12 to Milestone 5 and Section 4 changelog.
- `docs/project-changelog.md`: Full chronological entry added for `[Phase 12] - 2026-10-16: Coordinated Cutover Runbook, Rehearsal Tooling & Release Documentation`.

### 4.12 Readme (`README.md`)
- Updated architecture directory tree with `hub-strict-upgrade.mjs` and `strict-v2-cutover-rehearsal.mjs`.
- Added command section: "5. Run Strict-v2 Cutover Rehearsal & Tooling Self-Test (`node scripts/strict-v2-cutover-rehearsal.mjs --self-test`)".
- Added `[Strict-v2 Cutover Runbook](docs/strict-v2-cutover-runbook.md)` to documentation index.

### 4.13 Sibling Glean Hub Documentation (`/home/loidinh/WS/glean-hub/docs/doggo-strict-sync.md`)
- Updated CLI command examples: corrected `--status-only` syntax note (inspects status without resuming execution).
- Added section: "Phase 12 Coordinated Cutover Runbook & Rehearsal Tooling", cross-referencing Doggo runbooks and rehearsal scripts, and explicitly documenting Gate G7 blocker boundaries.

### 4.14 Project Overview PDR & Code Standards (`docs/project-overview-pdr.md`, `docs/code-standards.md`)
- `docs/project-overview-pdr.md`: Updated Section 14 to replace stale "phases 02–12 pending" text with an initiative-specific breakdown across all 12 phases, detailing Gate G1–G7 delivery and blocker boundaries.
- `docs/code-standards.md`: Updated header and added Section 9 covering checked 64-bit integer arithmetic, strict DST/timezone resolution, claim-before-post reminder ledger lifecycle, composite leave quiescence, and cutover token custody on argv/env.

### 4.15 Planning Artifacts (`plans/261015-0752-task-deadlines-reminders-strict-upgrades/`)
- `plan.md`: Preserved `status: in-progress`. Updated Phase 12 entry in implementation table to `**DONE** (2026-10-16) / 100% (12h credited; cutover runbook and rehearsal tooling delivered; Gate G7 live cutover held pending G2/G3/G6)`. Fixed obsolete markdown link anchor to `system-architecture.md`.
- `execution-map.md`: Updated line 3 and dependency nodes reflecting Phase 12 delivery with Gate G7 cutover held.
- `phase-12-coordinated-cutover-runbook-and-release.md`: Updated status to DELIVERED / DONE 100% (12h credited), documenting delivered artifacts, rehearsal results, and Gate G7 blocker boundaries.

---

## 5. Verification Execution Evidence

The following verification commands were directly executed and observed during this documentation audit turn:

| # | Command / Test Suite | Scope | Result | Observed Evidence |
|:---:|---|---|:---:|---|
| 1 | `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | Operator Tooling Rehearsal | **7/7 PASS** | 10.84s; help text, missing token, token redaction, dry-run, same-ID retry, status-only, schema divergence verified |
| 2 | `node scripts/hub-strict-upgrade.test.mjs` | Upgrade CLI Unit Suite | **9/9 PASS** | 10.84s; error log sanitization, same-ID resume, terminal state handling, epoch assertion verified |
| 3 | `node ~/.omp/agent/evcrate/scripts/validate-docs.cjs docs/` | Internal Link & Reference Audit | **PASS** | 194 working internal links verified, 0 broken links |
| 4 | `wc -l docs/*.md` | Documentation Size Limits | **PASS** | All 29 markdown files verified <= 800 LOC (range: 30–701 LOC) |
| 5 | `git diff --check docs/` | Whitespace & Formatting Audit | **PASS** | Clean; 0 whitespace errors or malformed lines |
| 6 | `repomix` | Compaction Generation | **PASS** | `repomix-output.xml` generated (418 files, 962,265 tokens, 4,163,193 chars) |

---

## 6. Honest Gate Accounting & Blocker Summary

All documentation adheres to strict gate accounting honesty:

| Gate | Title / Focus | Status | Hard Operational & Engineering Blockers |
|:---:|---|:---:|---|
| **G1** | Contract Freeze & Transition Design | **PASSED** | Canonical v1/v2 schemas, tri-stack binary digests, and 23 wire vectors frozen on 2026-10-15. |
| **G2** | Physical Native Platform Delivery | **BLOCKED** | 1. Android physical Doze timing (`setExactAndAllowWhileIdle`) and reboot broadcast persistence unverified on physical hardware.<br>2. Desktop Linux graphical D-Bus notification display unverified in headless session.<br>3. Windows native COM/WinRT toast notification bridge is unimplemented (candidate adapter stub in `:desktopApp` returns safe `Unavailable`). |
| **G3** | Hub Cluster Staging & Remediations | **BLOCKED** | 1. Live multi-instance MongoDB replica set migration unexecuted (`test_strong_sync_live_mongo_acceptance_gate` unrun due to absent `MONGO_TEST_URI`).<br>2. Distributed admission fence unverified under multi-node cluster traffic.<br>3. Three open Phase 03 backend code findings unaddressed in `glean-hub-sync`: permit release counter drift filter, `permitCount` legacy document backfill, and batch/checkpoint write atomicity. |
| **G4** | Client Version Transition & Safe Replay | **PASSED** | Room v1→v2 migration, `ContractTransitionCoordinator`, intent-masked rebasing, and legacy receipt reconciliation verified. |
| **G5** | Shared Scheduling & Account Fences | **PASSED** | `ReminderScheduleCalculator`, `PersistedScheduleProjector`, `ReminderOccurrenceStore` claim-before-post, and `CompositeLeaveSchedulerCancelGate` verified. |
| **G6** | Adversarial Acceptance & 3-Device Proof | **HELD** | Automated regressions pass (394 passes across 395 cases/targets); live 3-device concurrent sync between Android, Linux, and Windows held pending physical hardware testbeds. |
| **G7** | Coordinated Cutover & Release Runbook | **RUNBOOK DELIVERED / CUTOVER BLOCKED** | Authoritative runbook and rehearsal tooling delivered; live production cutover strictly blocked pending prerequisite gates G2, G3, G6, and formal operator authorization. |

---

## 7. Actionable Recommendations & Prioritized Next Steps

### 7.1 Actionable Recommendations for Main Agent
1. **Implement Native Windows Toast Bridge**: Complete the native WinRT/COM toast notification bridge in `:desktopApp` (`windowsMain`) to replace the fail-closed `Unavailable` stub prior to public Windows release.
2. **Remediate Phase 03 Backend Code Findings (`glean-hub-sync`)**:
   - Patch `PermitService::release_permit` to include `activePermits.<permitId>: { $exists: true }` filter.
   - Add `permitCount` schema backfill during migration preflight.
   - Enforce atomic write transaction combining batch mutation and checkpoint update.
3. **Provision Multi-Node Replica Set Staging**: Deploy a disposable multi-node MongoDB replica set to execute Gate G3 cluster staging validation (`test_strong_sync_live_mongo_acceptance_gate`).
4. **Regenerate Gradle Verification Metadata**: Re-lock `gradle/verification-metadata.xml` against trusted upstream repository keyrings to restore `--dependency-verification=strict`.
5. **Execute Disposable Staging Rehearsal**: Run `node scripts/strict-v2-cutover-rehearsal.mjs --staging-origin <URL> --confirm-disposable-staging` against the staging cluster prior to scheduling the production cutover window.

---

## 8. Unresolved Questions

1. **Windows Native Toast Dispatch Binding**: Will the Windows native toast notification bridge in `:desktopApp` be implemented via JNA direct COM/WinRT bindings in Kotlin, or via a bundled native helper binary in the MSI installer?
2. **Staging Cluster Provisioning Schedule**: What is the scheduled timeline for provisioning the multi-node MongoDB replica set and multi-instance Hub staging environment to execute Gate G3 validation?
3. **Upstream Keyring Trust for Gradle**: Which public keyrings will be designated as trusted root signing keys when regenerating `gradle/verification-metadata.xml` to clear `--dependency-verification=off`?
