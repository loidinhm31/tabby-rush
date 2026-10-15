# Phase 07 — Shared scheduling, persisted eligibility and account safety

## Context links
[Parent](plan.md) · [Rule/time contract](phase-01-freeze-contract-and-transition-design.md) · [Storage](phase-05-shared-model-and-room-migration.md) · [Sync transitions](phase-06-client-version-transition-and-replay.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DONE** (2026-10-15) / 100% progress (20h credited). Code review approved: 9.2/10. Automated tests: QA reports 270/270 PASS across 6 test suites. Estimate 20h. Owner: shared lifecycle lead. Depends on phase 05 and phase 01; final sync/conflict integration depends on 06. Gate G5 passed: persisted scheduler state, delivery gate, durable occurrence ledger, and fail-closed teardown barriers exercised and fully verified before native adapters enabled.

## Key Insights
`loadTodos()` includes drafts and is unsafe. UI reload/coordinator progress does not cover all storage changes. Posting and ledger commit cannot be atomic; exactly-once visible delivery is impossible.

## Requirements
- One shared checked calculator/preview; draft-free reactive persisted schedule projection and durable occurrence state.
- Local saved changes schedule offline; unsaved editor/cancel never schedule/cancel.
- Account wipe/mutation delivery serialization and stale callbacks fail closed.
- Grace exactly five minutes: post at most latest eligible opportunity per task, older opportunities skipped, future retained; no burst after resume/clock jump.

## Architecture
- DAO/store Flow recomputes atomic base+submitted outbox snapshot after committed local save/delete/ack/scan/conflict/transition. Never read editor_drafts for eligibility. Observe ledger/settings changes separately to avoid feedback loops.
- Apply latest applicable saved intentions in localRevision order. Pending/dispatching/uncertain are submitted intentions; conflict/unknown heads suppress unresolved schedule, never expose unreviewed conflict drafts. Saved completion/delete/disable remain cancellation barriers even when another rule is conflicted. Resolving/discarding uses a new committed projection.
- Keep OS handles out of domain rule calculation. Adapter contract reconcileSchedule/cancelAccount/postOwned/removeOwned/queryCapability; one scheduling coordinator owns per-account lifecycle.
- Occurrence identity accountKey+persisted generation+rowId+scheduleToken+index. Token only changes on schedule semantics; title update refreshes notification content without duplicating opportunity. Completion→reopen retains prior consumed opportunities, not restart count.
- For now n: choose largest i with due(i)<=n and n-due(i)<=300000ms and not consumed; older due entries marked skipped. If latest eligible already consumed, do not fall back to an older one. Keep all due>n. Exactly 300000ms included; 300001ms skipped. Tasks saved in past are valid but missed opportunities do not burst.
- Durable claim-before-post policy: serialize concurrent callbacks, re-read eligibility/current content/capability, mark occurrence consumed/attempted transactionally, then native post. Crash after claim may lose alert; never replay consumed claim on restart. Posting failure recorded as blocked/failed attempt; no retry beyond rule/grace designed here. Count means opportunities, not visible notifications.
- Shared delivery gate covers task saved mutations, posting and teardown. Recheck wipe marker/persisted generation before opening DB and immediately before native post; do not hold database transaction across slow OS/network calls. Close delivery admission first during leave; pending callbacks stop. Gate drains before Room close; cancel OS handles/remove posted notifications, clear ledger with account wipe. No task text serialized into stale handles.
- Android local identity restore and desktop runtime use same persisted projection; delivery doesn't require valid network credentials or foreground auth refresh. Revoked local account/wipe still blocks.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify shared `contract/account-store.kt`, `data/{doggo-dao,room-account-store}.kt`, `sync/{account-sync-coordinator,outbox-reconciliation,staged-full-scan,account-leave-protocol}.kt`; existing `contract/sync-scheduler.kt` only for combined leave cancellation contract, not repurposing network scheduler for alarms.
Create focused `contract/reminder-scheduler.kt`, `contract/reminder-capability.kt`, `data/reminder-occurrence-store.kt`, shared schedule coordinator/calculator at existing package boundary. Extend shared account-leave/storage/sync behavior tests. Android/desktop runtime integration owned phases 08/09.

## Implementation Steps
1. Implement checked calculator and deterministic clock/strict resolver input; verify arithmetic/boundary vectors, no platform API in commonMain.
2. Implement draft-free consistent observable projection and explicit conflict/cancellation precedence; include every storage mutation path in DAO invalidation rather than UI callbacks.
3. Implement stable identity/durable claim/skip/platform ownership handling and reconcile delta (cancel obsolete, keep unchanged, schedule future). Restart rebuilds handles from current state.
4. Integrate delivery-vs-save/complete/delete/disable/teardown gate with documented lock ordering: lifecycle/delivery gate→store mutex→DB transaction; never inverse, never wait network under gate.
5. Extend leave cancellation gate to both sync and reminders, startup pending-wipe cleanup and posted-notification removal where supported. Failed cancellation retains wipe marker.
6. Exercise throwaway actual SQLite scheduling scenario: local saved create/unsaved edit/title change/complete/reopen/delete/conflict/remote scan, clock jumps/resume, duplicate callback and wipe race.
7. Update storage/lifecycle design with observed state transitions and explicit crash-boundary loss tradeoff.

## Todo list
- [x] Shared arithmetic/strict time handling and preview contract.
- [x] Persisted projection invalidation/conflict precedence.
- [x] Ledger identity/grace/reconcile/claim lifecycle.
- [x] Saved-mutation and fail-closed wipe race proof.

## Delivery & Verification Evidence
- **Completion Date:** 2026-10-15 (100% progress / 20h credited; Gate G5 passed).
- **Scope Delivered:**
  - **Shared Schedule Calculator & Preview:** `ReminderScheduleCalculator` (`shared/src/commonMain/kotlin/dev/doggo/shared/contract/reminder-schedule-calculator.kt`) with checked 64-bit integer arithmetic (`safeMultiply`, `safeSubtract`, `safeAdd`), repetition limits (`1..10`), anchor boundary checks, strict timezone/DST offset resolution (`StrictTimeZoneResolver`), 5-minute grace window (`due <= now && now - due <= 300_000ms`), and preview interval derivation.
  - **Draft-Free Reactive Persisted Projection:** `PersistedScheduleProjector` (`shared/src/commonMain/kotlin/dev/doggo/shared/contract/persisted-schedule-projector.kt`) observing committed Room base and submitted outbox intentions (`STATE_PENDING`, `STATE_DISPATCHING`, `STATE_UNCERTAIN`), strictly isolating uncommitted drafts (`editor_drafts`), applying intent overlay in `localRevision` order, and enforcing cancellation barriers for saved completion, deletion, or rule disabling.
  - **Durable Occurrence Ledger Store:** `ReminderOccurrenceStore` (`shared/src/commonMain/kotlin/dev/doggo/shared/data/reminder-occurrence-store.kt`) backed by `reminder_delivery_ledger` entity, implementing durable claim-before-post semantics, generation-fenced occurrence keys (`accountKey:generation:rowId:scheduleToken:index`), atomic skipping of missed opportunities, and ledger cleanup (`clearPostedNotificationId`) upon obsolete notification removal.
  - **Shared Delivery Gate & Coordination:** `ReminderDeliveryGate` (`shared/src/commonMain/kotlin/dev/doggo/shared/sync/reminder-delivery-gate.kt`) and `ReminderSchedulingCoordinator` (`shared/src/commonMain/kotlin/dev/doggo/shared/sync/reminder-scheduling-coordinator.kt`) enforcing the documented lock hierarchy (`DeliveryGate -> Store Mutex -> SQLite Room Transaction`), pre-post eligibility re-checking under gate admission, and non-blocking native obsolete cleanup with retry retention on adapter failure.
  - **Fail-Closed Account Leave Protocol:** `AccountLeaveProtocol` (`shared/src/commonMain/kotlin/dev/doggo/shared/sync/account-leave-protocol.kt`) coordinating sync scheduler and reminder coordinator teardown, retaining `store.isFrozen = true` across all abort/failure paths after `closeAndDrain()`, generation overflow protection (`Long.MAX_VALUE`), and barrier synchronization draining in-flight callbacks before wipe marker persistence, Room closure, and database file deletion.

### QA Test Execution Summary
- **Validation Date:** 2026-10-15 ([QA Test Report](../reports/qa-261015-1918-phase-07-shared-scheduling-and-account-safety.md)).
- **Reported Results:** 270/270 checks PASS across 6 test suites (0 failures, 0 skips):
  - Shared KMP JVM test suite (`JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`): 182 JVM unit/integration tests PASS across 26 test classes (including `ReminderScheduleCalculatorTest`, `PersistedScheduleProjectionTest`, `ReminderOccurrenceStoreTest`, `ReminderSchedulingCoordinatorSqliteSmokeTest`, `ReminderTeardownBarrierSqliteTest`, `AccountLeaveProtocolTest`, `ReminderDeliveryGateTest`, `RoomAccountStoreTest`, `ContractTransitionTest`, `StagedFullScanTest`).
  - Desktop App test suite (`JAVA_HOME=... ./gradlew :desktopApp:test --dependency-verification=off --rerun-tasks`): 32/32 tests PASS across 6 test classes.
  - Wire vector runner (`node scripts/strict-v2-vector-runner.mjs`): 23/23 checks PASS (2 schema fingerprints, 10 valid vectors, 11 rejection vectors).
  - Offline fixture smoke (`node scripts/hub-live-smoke.mjs --fixture-test`): 18/18 checks PASS across 5 test suites.
  - Diagnostic source security audit (`node scripts/release-security-check.mjs --diagnose-source`): 6/6 static security checks PASS (manifest, backup rules, single exported activity, HTTPS origin, zero hardcoded secrets).
  - Hub strict upgrade CLI suite (`node scripts/hub-strict-upgrade.test.mjs`): 9/9 test suites PASS.

### Code Review Summary
- **Review Date:** 2026-10-15 ([Code Review Report](../reports/code-review-261015-1944-phase-07-remediations-rereview.md)).
- **Overall Score:** 9.2 / 10 (Approved by Senior Software Engineer; Gate G5 unlocked for Phase 08/09 adapter integration).
- **Key Remediations Verified:**
  - *Un-gated Outbox Error State Writes*: All outbox error state writes wrapped inside `store.withMutationLock`, re-reading current state under lock in `RETRYABLE_FAILURE` to prevent resurrection.
  - *Inconsistent Store Unfreezing on Teardown Abort*: Fail-closed safety verified; once `closeAndDrain()` is invoked, abort/failure paths never unfreeze `RoomAccountStore`.
  - *Perpetual Obsolete Notification Removals*: `clearPostedNotificationId()` in `DoggoDao` and `ReminderOccurrenceStore` clears posted IDs after successful native removal.
  - *Wipe Race & Teardown Barrier Proof*: Fresh unconsumed task assertions verified in `ReminderSchedulingCoordinatorSqliteSmokeTest`; dedicated authoritative `ReminderTeardownBarrierSqliteTest` proves in-flight callbacks drain before wipe marker persistence, Room closure, and database file deletion.
  - *Staged Scan Promotion Interleaving*: `promoteStaging` fully wrapped inside `store.withMutationLock`.

### Validation Boundaries & Environment Gates
1. **Desktop JVM/BundledSQLite vs Physical Native Adapters:** Shared scheduling, draft isolation, claim-before-post lifecycle, and delivery gate concurrency verified on JVM and BundledSQLite. Native OS delivery across Android API 26 (Exact Alarm / WorkManager) and Linux session D-Bus / Windows toast adapters proceeds in Phases 08 and 09.
2. **Static Security vs Binary Runtime Vaults:** Static source audit clean; hardware Keystore cryptographic binding and release APK signing remain deferred to Phase 08 release operations.
3. **Dependency Verification:** Test execution scoped under `--dependency-verification=off` pending regeneration of `gradle/verification-metadata.xml`.

## Success Criteria
Focused permanent regressions for grace boundary, count1/10 and rejects, title-edit identity, conflicting rule suppression, duplicate callbacks and wipe-vs-post interleavings. Smoke actual SQLite with local save and simulated adapter observes exact intended due/skip/cancel state. Tests alone are insufficient; native proof follows phases 08/09/11.

## Risk Assessment
Holding gate during OS call can delay user action: keep bounded local/native calls, explicit timeout failure; never let timeout reopen wipe admission. Single per-account coordinator avoids duplicate observers.

## Security Considerations
At delivery fetch current task text only after account/generation eligibility. Account-specific posted IDs prevent removing another account's notifications. Native removal limitations recorded, not bypassed.

## Next steps
Gate G5 passed (shared prerequisites delivered; native proof and runtime delivery remain gated by G2). Downstream Phase 08 (`phase-08-android-alarms-notifications-and-restore.md`) and Phase 09 (`phase-09-desktop-scheduler-and-native-notifications.md`) proceed with adapter implementation under their native testbed prerequisites.

## Unresolved questions
1. Runtime validation of AC09 Android exact alarm and notification capability matrix: verify on target devices (API 26 baseline compatibility, API 31+ `SCHEDULE_EXACT_ALARM` special access revocation operating under `bestEffort` WorkManager delivery, and API 33+ runtime `POST_NOTIFICATIONS` denial accurately marking capabilities as `blocked` without repeated prompts). (Owned by Phase 08).
2. What is the scheduled purge policy for historical ledger entries marked `CANCELLED` or `SKIPPED`? (Can be addressed in maintenance/pruning pass).
