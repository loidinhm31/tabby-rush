# Phase 05 — Durable local state and strict per-record sync

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Selected strict A](reports/strong-sync-feasibility.md) · [Mutation/scan design](reports/strong-sync-design-feasibility.md) · [Isolation](reports/strong-sync-isolation-feasibility.md) · [Mongo evidence](reports/mongodb-feasibility-evidence.md).
- [Historical research](../261013-1459-doggo-offline-accounts/research/researcher-02-persistence-sync.md) — only protected-store/Room/atomicity evidence reused; OAuth/D1/LWW/insecure storage superseded.

## Parallelization Info
Group C after02, parallel03/04/06; code/tests depend frozen interfaces only. Runtime requires03 strict server and04 auth;07 composes real implementations.02 owns schema export config/public strict contracts,05 exact1.json/data tests/concrete wire mapping.

## Overview
- Date: 2026-10-13
- Description: Durable local state and strict per-record sync.
- Priority: P2
- Implementation status: pending (0%)
- Review status: parent plan review complete; implementation/runtime acceptance pending.
- Effort: 28h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Room is visible truth; server-owned confirmed revision, localRevision and frozen expectedRevision are distinct. Explicit acceptance is not payload equality. LastOp can be superseded: preserve UnknownSuperseded rather than infer causal failure/success. Stable-key full scans include permanent markers, not snapshots; eventual convergence after quiescence only. No receipt ledger/feed/automatic rebase/legacy fallback.

## Requirements
Per-origin/exact-user full SHA256 partition; epoch in base/outbox/staging/meta; durable draft/intent/outbox transaction; immutable FIFO operation; exact explicit acceptance only; staged bounded stable rowId scans with monotonic promotion; server base plus reviewable local intent; crash-safe offline restart/generation/wipe. Real Room/bundled SQLite, explicit migrations/no destructive fallback. Same-epoch missing pre-scan confirmed identity pauses corruption/reset, never implicit deletion/recreation.

## Architecture
DoggoDatabase namespace dev.doggo.shared.data: server_base,outbox,editor_drafts,scan_staging,account_sync_meta. Outbox persists frozen opId/epoch/expectedRevision/kind/payload/digest/captured localRevision/delivery state and successors separately; base includes canonical tombstones. Staging records epoch/scan-start confirmed identities so late acknowledgements survive promotion. Account-files Android/JVM adapters own active-account/session metadata,generation and wipe marker outside deleted DB. Store closes all handles before wipe; tokens excluded. Foreground/worker share coordinator mutex; response account/generation/epoch fenced.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/doggo-database.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/server-base-entity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/outbox-entity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/editor-draft-entity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/scan-staging-entity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/account-sync-meta-entity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/doggo-dao.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/database-migrations.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/hub-sync-dtos.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/hub-sync-client.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/staged-full-scan.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/outbox-reconciliation.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/account-sync-coordinator.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/sync/account-leave-protocol.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidMain/kotlin/dev/doggo/shared/data/android-database-builder.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidMain/kotlin/dev/doggo/shared/data/android-account-files.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/jvmMain/kotlin/dev/doggo/shared/data/jvm-database-builder.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/jvmMain/kotlin/dev/doggo/shared/data/jvm-account-files.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/sync/hub-sync-client-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/sync/staged-full-scan-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/sync/outbox-reconciliation-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/sync/account-sync-coordinator-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/sync/account-leave-protocol-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidInstrumentedTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/schemas/dev.doggo.shared.data.DoggoDatabase/1.json` — create.
- `/home/loidinh/WS/doggo/docs/local-storage-and-sync.md` — create.
- `/home/loidinh/WS/doggo/docs/sync-acceptance.md` — create.

## File Ownership
Exclusive phase05 owner of all 29 exact absolute paths above; those and only those phase05 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Implement entities/DAO/database/store plus platform builders with BundledSQLiteDriver; account directories keyed by full AccountKey. Account-sync-meta owns reconciliation/queue metadata; Android/JVM account-files own session metadata/generation/wipe marker atomic read/write/recovery. Use restrictive Android/XDG/Windows user-data paths and no symlink traversal. Generate real schema1.json using02 KSP; no handwritten/fake schema or destructive migration fallback.
2. Atomic edit stores draft/localRevision/desired row or deletion intent plus outbox before saved-locally success. Coalesce only never-dispatched intents; unsent create-delete cancels locally. Dispatch freezes opId UUID,epoch,expectedRevision,kind,identity,normalized data,digest and captured localRevision permanently; server assigns revisions, create expected0, edit/delete exact confirmed revision. No max+1 client wire version. One unresolved operation per row/FIFO; successors remain durable and cannot dispatch before prior resolution. <=100 unique rows/256KiB. Persist DISPATCHING before socket; restart/cancel becomes UNCERTAIN. Late callbacks cannot reopen closed/wiped DB; local quiescence does not stop remote commit.
3. Concrete strict DTO/adapter uses02 domain+digest and04 authorizedRequest for POST strong/bootstrap,push,pull only,no body user authority/client revision/delta/legacy path. Validate protocol/app/account/epoch,UUIDs,revision bounds,status/digest/acceptance/todos schema;tombstone canonical empty. Pull no lastOp/nonce,content never resolves delivery. Unknown response fields ignored;invalid row/status aborts without cursor advance. Opaque scoped strict-after rowId cursor never timestamp;reject nonprogress/order/wrong epoch/table.503notAdmitted backoff+preserve;409epochMismatch ResetRequired;426/404/405 ProtocolIncompatible;400invalidRequest protocol pause. No refresh on non401 protocol errors.
4. Round validates /me identity/grant,strict bootstrap+epoch,complete null-cursor staged scan ->eligible frozen pushes ->final scan. Server limit+1/cursor last returned/<=100;100/101 proves no skipped lookahead. Partial/interrupted/page-budget scan restarts null preserving base/outbox;epoch change aborts staging before promotion. Atomically retain higher confirmed revisions;same-revision differing logical state ProtocolIncompatible;pending/drafts/uncertain preserved. Canonical tombstone/reconfirmation not logical change. Missing BEFORE-start confirmed identity same epoch pauses corruption/reset;post-start create/ack remains retained for next round. Never infer deletion/ack from absence.
5. Only accepted/replayed matching current account/generation/epoch/opId/row/digest and expectedRevision+1 acknowledges captured localRevision; counts, omissions, current payload, pull rows and equal data never acknowledge. Old ack advances confirmed base monotonically without clearing successor edits. Known current rejection preserves intent/base for explicit conflict review. Apparent conflict/missing/deleted after previously uncertain delivery becomes UnknownSuperseded because lastOp may have changed; not proof original failed. Invalid/protocol failures pause; retryableFailure backs off and after3 failed rounds blocks automatic retries visibly while preserving work. No English-error behavior.
6. UNCERTAIN recovery retains identical frozen request;scan informs state,never equality/absence acknowledgement. Coordinator replay reuses persisted opId/base/digest;03 real journaled confirmation decides acceptance. Superseded lastOp →conservative UnknownSuperseded,no optional revision-based inference,new base/opId/UUID retry or auto-clear. Remote tombstone may show deletion satisfied but remains review/counts leave until explicit discard. Deliberate NEW mutation only user-authorized after current live-base review;copy-as-new UUID never resurrects identity. Changed bootstrap epoch/409 →ResetRequired,old outbox/drafts separate,no old-response promotion/automatic rebase. No universal losslessness/receipt history/snapshot.
7. Coordinator provides launch/resume/network-recovery/debounced edits/Sync now, shared account mutex and generation fences before credential/DB/network/apply. Active established account restarts offline, expired access preserves editing; known denied grant quarantines behind access state, not silent wipe. Durable pending state repairs edit-commit/enqueue gap when07 scheduler relaunches.
8. Leave counts draft/pending/uncertain/conflict/UnknownSuperseded/old-epoch review and restores exact prior state on cancel. Sync-then-leave only after all review/drafts resolved,explicit accepted queue/final scan. Discard warns possible remote commit/local-only loss. Freeze edits/scheduling,persist new generation+wipe-pending marker outside DB,then cancelAndAwait and await owned callbacks/requests,close Room,delete DB/WAL/SHM/staging/cache/drafts,remove credentials/identity,clear marker. Failed persistence aborts before deletion; failed/interrupted wipe stays fail-closed before any next account. Startup cancels all leftover Doggo work before DB/vault open. No timeout/WorkInfo-only completion; no remote rollback promise.
   Exact teardown order: freeze edits/scheduling first, persist generation+wipe marker next, then cancel/await. No later callback may reopen a wiped DB; failed marker persistence aborts leave before deleting anything.
9. Common tests: strict digest/DTO/status/epoch errors,partial/missing exact ack,old ack vs newer edit,restart UNCERTAIN,identical retry,explicit replay acceptance,successor→UnknownSuperseded,equal-payload no ack,100/101/limit+1/nonprogress/behind-cursor insert,invalid/interrupted scans,higher-revision promotion,scan-start missing vs post-start create/ack,canonical tombstones,conflict/no implicit undelete,epoch switch/late response,generation,worker overlap,leave cancel/failed sync/discard/wipe restart. Scheduler fixtures inspect read/write/cancelAndAwait ordering; actual WorkManager tests07.
10. JVM+Android Room tests open actual private temporary databases, execute transactions/DAO queries, restart with frozen outbox, verify schema/partition/isolation, rollback preserves base/outbox, closed-handle wipe/WAL/SHM and fail-closed retry. Version1 creation/reopen/schema validation and unsupported downgrade fail; execute actual migrations when introduced, never claim invented1->2 migration. Common controlled transport tests are protocol fault evidence, not live Hub proof.07 executes all once with real Hub/device gates.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior,not scaffolds.
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence.
- [ ] Resolve failures through original owner and complete parent review; do not mark implementation accepted early.

## Success Criteria
Real Room schema/export/transactions/outbox survive process death; immutable FIFO requests and explicit durable acceptance cannot clear successor; stable staged scans promote monotonically without absence deletion; known conflict/UnknownSuperseded/protocol/reset remain distinct and retain work. Session metadata/wipe marker fences late work and account switching. Common/Room suites plus actual07 strict/offline/background/isolation/crash gates pass; no fake proof.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Non-snapshot scans need later rounds under writes; incomplete budget is not reconciliation. Superseded lastOp cannot prove historical causality. Lifetime tombstones grow with distinct IDs. Topology/journal/index or03 strict admission failure blocks sync without legacy fallback. Epoch recovery cannot restore backup-missing data. File deletion failure preserves fail-closed state; no equal-version/purge-race legacy assumptions on this isolated protocol.

## Security Considerations
No credentials in SQLite/drafts/staging. Account/origin partition and generation checked everywhere. OS-private DB is not SQLCipher/forensic erasure. Wipe marker/generation protected atomic metadata; no accountB beforeA teardown.

## Next steps
07 composes auth04/store05/UI06/scheduler07 and runs durable/local/live tests. Desktop09/10 reuse05 JVM builders unchanged; defects reopen05.

## Unresolved questions
Actual Room/KSP tuple is02 prerequisite; isolated live Hub03/runtime04 and real file deletion/fault-injection environment needed for runtime gates.
