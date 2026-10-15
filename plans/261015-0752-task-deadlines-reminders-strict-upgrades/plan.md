---
title: "Task deadlines, reminders, and strict-contract upgrades"
description: "Data-preserving Hub contract evolution and offline native reminders across Android, Linux, and Windows."
status: in-progress
priority: P2
effort: 236h
branch: main
tags: [feature, backend, database, api, frontend]
created: 2026-10-15
---

# Task deadlines, reminders, and strict-contract upgrades

## Scope and status
Phase 01 contract freeze and transition design marked DONE on 2026-10-15 (100%, Gate G1 passed); Phase 02 logic/contracts DELIVERED on 2026-10-15 (Gate G2 physical native delivery formally BLOCKED on missing host prerequisites); Phase 03 Hub v2 admission, fencing and migration DELIVERED (Contracts/Logic/Tests) on 2026-10-15 (Gate G3 physical multi-instance Mongo cluster execution formally ON HOLD pending live replica set testbed; Code Review: ADMISSION ON HOLD 8.0/10 with 3 open code findings); subsequent implementation phases 04–12 pending. Source: [agreed brainstorm](../reports/brainstorm-261015-0354-task-deadlines-reminders-strict-upgrades.md). Effort is a rough sum of phase estimates, not a release date; recalibrate after design/native proof.

Optional date-only/timed deadline and synced task reminder rules; offline saved changes; Android OS delivery without UI; Linux/Windows delivery while process runs visible/minimized. Coordinated Room and strict Hub upgrade preserves data/revisions/tombstones/receipts. UI and script use one app-authorized server operation.

No recurring tasks/snooze/push/daemon/tray/autostart/macOS/iOS/arbitrary schema migration. Existing platform minimums retained. Five-minute grace; opportunity count1–10; no exactly-once/perfect OS timing claim.

## Implementation phases
| # | Phase | Status / progress | Estimate |
|---|---|---|---|
| 01 | [Freeze temporal/contract/legacy transition](phase-01-freeze-contract-and-transition-design.md) | **DONE** (2026-10-15) / 100% (Gate G1 passed) | 16h |
| 02 | [Prove native notifications and strict DST](phase-02-prove-native-delivery-and-dst.md) | **DELIVERED (Contracts/Logic)** (2026-10-15) / Partial (Gate G2 physical delivery BLOCKED on host prerequisites) | 16h |
| 03 | [Hub v2 admission, fencing and migration](phase-03-hub-versioned-contract-and-migration.md) | **DELIVERED (Contracts/Logic/Tests)** (2026-10-15) / Credited 32h (Gate G3 live cluster execution ON HOLD pending replica set testbed) | 32h |
| 04 | [Hub admin UI and API-only upgrade script](phase-04-hub-admin-ui-and-upgrade-script.md) | Pending / 0% | 12h |
| 05 | [Shared model and explicit Room1→2](phase-05-shared-model-and-room-migration.md) | Pending / 0% | 20h |
| 06 | [Client version transition and safe replay](phase-06-client-version-transition-and-replay.md) | Pending / 0% | 24h |
| 07 | [Saved-state scheduling and account fences](phase-07-shared-scheduling-and-account-safety.md) | Pending / 0% | 20h |
| 08 | [Android alarms, private alerts and restore](phase-08-android-alarms-notifications-and-restore.md) | Pending / 0% | 20h |
| 09 | [Desktop scheduler and native adapters](phase-09-desktop-scheduler-and-native-notifications.md) | Pending / 0% | 24h |
| 10 | [Shared editor, list and local settings](phase-10-compose-deadline-editor-list-and-settings.md) | Pending / 0% | 20h |
| 11 | [Adversarial and platform acceptance](phase-11-adversarial-integration-and-platform-acceptance.md) | Pending / 0% | 20h |
| 12 | [Coordinated cutover, recovery and release](phase-12-coordinated-cutover-runbook-and-release.md) | Pending / 0% | 12h |

## Execution and design access points
- [Dependency graph, gate/ownership rules](execution-map.md): phase numbering is navigation, not a false serial requirement. Begin 01+02; no broad UI until design/native proof. Backend and storage may proceed independently after G1.
- [All twelve acceptance scenarios](acceptance-matrix.md): evidence requirements and exact coverage; all pending.
- Phase 01 contract freeze (Gate G1 passed), Phase 02 strict DST resolution & candidate notification adapters (Gate G2 physical delivery BLOCKED on host prerequisites: Android SDK/device, desktop notification daemon, Windows runtime), and Phase 03 Hub v2 admission, fencing and migration (Gate G3 physical multi-instance Mongo cluster execution ON HOLD pending live replica set testbed) are delivered; runtime Room migration (Phase 05), operator tooling (Phase 04), and physical native execution (Phases 08/09) remain in subsequent phases.
- [Strict upgrade source findings](research/strict-upgrade-research.md) · [Reminder/platform source findings and primary API links](research/reminder-platform-research.md).
- [Proposed architecture section](../../docs/system-architecture.md#6-proposed-deadlines-reminders-and-strict-upgrade-design--pending) is explicitly unimplemented; earlier sections remain current code.

## Critical invariants
- Never rehash/re-ID a frozen uncertain v1 operation. Reconcile original legacy receipt; superseded/unprovable history remains explicit conflict.
- Never translate stale v1 full-row edits by overwriting v2 metadata; rebase known changed intentions over scanned base.
- Migration blocks readers/writers across all nodes, drains admitted requests, transforms in-place and publishes contract last. Preserve collection/epoch/IDs/revisions/tombstones/receipts; no fresh-epoch reset.
- Schedule only persisted submitted intentions, not editor drafts; token independent of title/server revision. Delivery rechecks account/generation/wipe/task/rule under mutation/teardown gate.
- Saved instants fixed; date-only overdue is next captured-zone calendar-day start, not09:00 or +24h. Device defaults never rewrite old tasks.
- Native proof must be separately observed on Android/Linux/installed Windows; API success/build/this Linux host cannot substitute.

## Original planning verification
Source/document inspection and two completed read-only research reports; official Android/Linux/Windows API documentation reviewed. Plan structure/links/coverage checked by executable validation; see [validation report](reports/plan-validation.md).

## Phase 01 Delivery & Verification Evidence
- **Completion Date:** 2026-10-15 (Gate G1 passed).
- **Scope Delivered:** Canonical v1/v2 schema specifications (`doggo-schema-v1.json`, `doggo-schema-v2.json`), 20 cross-stack test vectors (`strict-v2-vectors.json`), shared Kotlin Multiplatform contract & digest models (`StrictSyncV2Digest`), Rust Hub sync v2 models (`strong_sync_v2.rs`), vector runners, and transition design documentation.
- **Validation Reports:**
  - [QA Test Report](../reports/qa-261015-0938-phase-01-freeze-temporal-model.md): 108/108 KMP JVM tests PASS (including 10 Phase 01 vector & extended bounds tests under `--dependency-verification=off`), 20/20 Node vector checks PASS, 37/37 Rust package tests PASS (1 live-Mongo test ignored), 18/18 offline fixture smoke checks PASS.
  - [Code Review Report](../reports/code-review-261015-0951-phase-01-freeze-temporal-model.md): Quality score 9.5/10 APPROVED with zero critical defects.
- **Residual Scope:** Runtime Room migration (Phase 05), live Hub backend migration & permit drain (Phase 03), and native alarm/DST proofs (Phase 02) remain pending subsequent phase execution.

## Phase 02 Delivery & Verification Evidence
- **Delivery Status:** 2026-10-15 (Logic and contract specifications DELIVERED; Gate G2 physical delivery BLOCKED on host prerequisites).
- **Scope Delivered:**
  - Shared strict timezone & DST resolver contract (`StrictTimeZoneResolver`, `StrictZoneOffsetResult` sealed hierarchy: `Single`, `Overlap`, `Gap`) in `:shared` (`commonMain`, `jvmMain`, `androidMain`) using `java.time.zone.ZoneRules`.
  - Android candidate adapter (`AndroidNotificationAdapter`), capabilities checker (`AndroidReminderCapabilitiesChecker`), and non-exported receiver (`DoggoAlarmReceiver`) in `:androidApp`. Hardened manifest with `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`.
  - Linux D-Bus candidate adapter (`DbusLinuxNotificationAdapter`) in `:desktopApp` (`linuxMain`) with safe fallback on missing daemon (`ServiceUnknown` -> `Unavailable`).
  - Windows candidate adapter (`WindowsNotificationAdapter`, `WindowsToastBridge`) in `:desktopApp` (`windowsMain`) with XML escaping, tag/group clearance API, and fail-safe unproven `Unavailable` status with AUMID `dev.doggo.desktop`; native COM/WinRT implementation and installed-runtime proof remain pending Phase 09 Windows testbed.
  - Reconciled Linux dependency locking in `desktopApp/gradle-linux.lockfile`.
  - Acceptance documentation updates in `docs/android-acceptance.md`, `docs/linux-acceptance.md`, and `docs/windows-acceptance.md`.
- **Validation Reports:**
  - [QA Test Report](../reports/qa-261015-1100-phase-02-validation.md): 147/147 Gradle tests PASS under `--dependency-verification=off` (115 :shared:jvmTest incl. 6 strict DST vectors; 32 :desktopApp:test incl. 4 Linux notification tests; 116 JVM tests in clean re-run with 7th DST midnight-gap vector), 18/18 fixture smoke checks PASS, 6/6 source security checks PASS. Windows target tests source-inspected/unexecuted on Linux host due to target isolation.
  - [Code Review Report](../reports/code-review-261015-1105-phase-02-native-delivery-and-dst.md): Quality score 7.5/10 APPROVED (Critical false-positive Windows bridge status remediated to fail-safe `Unavailable`; acceptance matrix statuses updated to avoid conflating static inspection with executed tests).
- **Gate G2 Status:** Formally **BLOCKED** on missing host prerequisites:
  - Android SDK and connected device/emulator absent on host (`ANDROID_HOME` unset; deferred to Phase 08).
  - Linux desktop notification daemon absent from user session bus (`/run/user/1000/bus` returns `ServiceUnknown`; visual bubble delivery deferred to Phase 09).
  - Windows host and MSI packaging tools absent (workstation is Linux Fedora 44; deferred to Phase 09 / Windows testbed).
- **Residual Scope & Next Phases:** Downstream backend (Phase 03 Hub v2 admission & migration) and storage (Phase 05 Shared Room 1→2 migration) proceed independently under Gate G1 clearance. Gate G2 remains formally BLOCKED for broad Compose UI (Phase 10) and final native adapters pending physical execution in Phase 08 (Android device testbed) and Phase 09 (Linux notification daemon and Windows MSI testbed).

## Phase 03 Delivery & Verification Evidence
- **Delivery Status:** 2026-10-15 (Contracts, backend logic, and automated tests DELIVERED; Gate G3 physical multi-instance Mongo cluster execution formally ON HOLD pending live replica set testbed; Code Review: ADMISSION ON HOLD 8.0/10 with 3 open code findings).
- **Scope Delivered:**
  - **Strict-v2 Admission & Guarding:** Exact v1/v2 schema validation enforcing all 16 ordered columns (4 original columns `id`, `title`, `completed`, `createdAt` plus 12 added temporal/reminder fields `deadlineDate` through `scheduleToken`), canonical schema fingerprints (`3361a80f...` and `5f539551...`), typed version-aware request/digest processing in Rust (`strong_sync.rs`, `strong_sync_v2.rs`, `sync_mode_guard.rs`).
  - **Coordination Permit Gate:** Distributed durable permit gate in `permit_service.rs` with atomic `permitCount: { $lt: 1000 }` CAS admission bound, request fencing, and quiescence drain.
  - **In-Place Migration Engine:** Authorized upgrade execution in `strict_upgrade_service.rs` performing chunked in-place transform (`BATCH_SIZE = 500`), incremental `checkpointRowId` persistence, cumulative resumption accounting (`prior_transformed_count` & `preflight_tombstone_count`), majority+journal `_schemas` CAS publication, and invariant verification.
  - **Legacy Receipts Route:** Read-only legacy reconciliation endpoint (`routes/strong_sync.rs`, `StrongSyncService::legacy_receipts_inner`) enforcing `protocol == "strict-v1"`, journaled CAS confirmation, and deterministic v1 digest verification without modifying v2 rows.
  - **App-Scoped Admin Authentication:** Middleware in `dual_auth.rs` verifying target app-scoped admin authorization or global admin grant.
- **Validation Reports:**
  - [QA Test Report](../reports/qa-261015-1215-phase-03-hub-v2-admission.md): 211 passing tests/checks across both repositories (0 failures, 1 ignored live Mongo test). Suite breakdown: 54/54 executed Rust tests PASS (32.34s); 23/23 Node vector and schema fingerprint checks PASS; 18/18 offline Hub fixture smoke checks PASS; 116/116 KMP JVM tests PASS (under `--rerun-tasks --dependency-verification=off`); 6/6 static Android security checks PASS. Rust compiler linter clean (0 warnings, 0 errors via `cargo clippy --locked --all-targets`).
  - [Code Review Report](../reports/code-review-261015-1225-phase-03-hub-v2-admission.md): Cycle 3 final review scored 8.0/10 with status **ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION)** due to 3 open code findings and unexecuted Gate G3 live cluster tests:
    1. Permit release unconditionally decrements `permitCount` without filtering on `activePermits.<permitId>: { $exists: true }`, risking counter drift on duplicate release.
    2. Existing coordination documents lack a `permitCount` backfill for numeric `$lt` predicates.
    3. Batch mutation and checkpoint update are separate writes with ignored checkpoint errors; interruption can undercount transformed rows.
- **Gate G3 Status:** Formally **ON HOLD** pending live replica set testbed. Gate G3 demands physical execution across a live two-node Hub and multi-instance MongoDB replica set covering:
  1. Distributed two-node admission fence race conditions.
  2. Concurrent read/write request permit drain under active migration.
  3. Crash injection during transform batches with resumption from checkpoint.
  4. Publish-response packet loss with idempotent replay.
  5. Physical collection `app_<appId>` and protected-field fingerprint preservation under real DB storage.
- **Residual Scope & Downstream Phases:** Downstream Phase 04 (Hub admin UI & upgrade script) and Phase 05 (Shared Room migration) can proceed on logic and contract foundations under Gate G1/Phase 03 logic delivery (Phase 06 implementation remains dependent on Phase 05), while Gate G3 live cluster execution and 3 open code remediations remain prerequisites for production admission.

## Validation Summary
Validated: 2026-10-15. Three substantive questions; two clarified before final answers.
- **Legacy uncertainty:** block the affected task only; preserve its frozen operation for explicit review, let unrelated tasks transition/sync.
- **Notification crash boundary:** consume before native posting; possible missed alert after a crash accepted, no retry of consumed opportunity.
- **Upgrade availability:** app-scoped maintenance pause accepted; offline local saves continue while server fences/drains/transforms/validates/publishes.
- All answers match the proposed phase design. No phase revisions required; technical proof gates remain pending.
- Interview validates these tradeoffs only, not permission to implement or mutate production.

## Unresolved questions and execution prerequisites
1. Selected Windows binding/native helper and installed AUMID identity remain an open implementation decision pending Phase 09 Windows testbed (candidate stub returns safe `Unavailable`). Physical native delivery across Android API 26 (Phase 08), Linux session notification daemon (Phase 09), and Windows MSI host (Phase 09) remain open prerequisites to clear Gate G2.
2. Prove distributed permit-drain/orphan recovery and Gate G3 two-Hub cluster migration on physical Mongo replica set testbed before production cutover; address open code review findings (permit release filter, permitCount backfill, batch/checkpoint write atomicity).
3. App-scoped admin model, all-node deployment inventory, disposable Mongo, Android device/permissions, Linux notification daemon/session, installed Windows runtime and signing/operator access required before their gates.
Phase 01 (Gate G1 passed), Phase 02 logic/contracts DELIVERED (Gate G2 physical delivery BLOCKED on host prerequisites), and Phase 03 DELIVERED (Contracts/Logic/Tests; Gate G3 physical cluster execution ON HOLD pending live replica set testbed); subsequent phases pending until approved/executed.
