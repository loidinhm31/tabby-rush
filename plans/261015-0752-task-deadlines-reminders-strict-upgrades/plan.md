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
Phase 01 contract freeze and transition design marked DONE on 2026-10-15 (100%, Gate G1 passed); subsequent implementation phases 02–12 pending. Source: [agreed brainstorm](../reports/brainstorm-261015-0354-task-deadlines-reminders-strict-upgrades.md). Effort is a rough sum of phase estimates, not a release date; recalibrate after design/native proof.

Optional date-only/timed deadline and synced task reminder rules; offline saved changes; Android OS delivery without UI; Linux/Windows delivery while process runs visible/minimized. Coordinated Room and strict Hub upgrade preserves data/revisions/tombstones/receipts. UI and script use one app-authorized server operation.

No recurring tasks/snooze/push/daemon/tray/autostart/macOS/iOS/arbitrary schema migration. Existing platform minimums retained. Five-minute grace; opportunity count1–10; no exactly-once/perfect OS timing claim.

## Implementation phases
| # | Phase | Status / progress | Estimate |
|---|---|---|---|
| 01 | [Freeze temporal/contract/legacy transition](phase-01-freeze-contract-and-transition-design.md) | **DONE** (2026-10-15) / 100% (Gate G1 passed) | 16h |
| 02 | [Prove native notifications and strict DST](phase-02-prove-native-delivery-and-dst.md) | Pending / 0% | 16h |
| 03 | [Hub v2 admission, fencing and migration](phase-03-hub-versioned-contract-and-migration.md) | Pending / 0% | 32h |
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
- Phase 01 contract freeze and reference digest models are completed (Gate G1 passed); runtime Room migration, backend admission, and native delivery remain pending in subsequent phases.
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

## Validation Summary
Validated: 2026-10-15. Three substantive questions; two clarified before final answers.
- **Legacy uncertainty:** block the affected task only; preserve its frozen operation for explicit review, let unrelated tasks transition/sync.
- **Notification crash boundary:** consume before native posting; possible missed alert after a crash accepted, no retry of consumed opportunity.
- **Upgrade availability:** app-scoped maintenance pause accepted; offline local saves continue while server fences/drains/transforms/validates/publishes.
- All answers match the proposed phase design. No phase revisions required; technical proof gates remain pending.
- Interview validates these tradeoffs only, not permission to implement or mutate production.

## Unresolved questions and execution prerequisites
1. Prove strict DST offset enumeration and selected Windows binding/installer identity in phase02; exact native choice cannot be accepted from dependency presence.
2. Prove distributed permit-drain/orphan recovery on supported Mongo topology in phase03; lease expiry alone unsafe.
3. App-scoped admin model, all-node deployment inventory, disposable Mongo, Android device/permissions, Linux notification daemon/session, installed Windows runtime and signing/operator access required before their gates.
Phase 01 and Gate G1 marked DONE on 2026-10-15; subsequent phases pending until approved/executed.
