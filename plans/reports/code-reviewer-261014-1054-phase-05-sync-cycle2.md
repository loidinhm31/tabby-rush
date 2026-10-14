# Code Review Cycle 2 — Phase 05 Durable Local State & Strict Per-Record Sync

Date 2026-10-14 · Reviewer: EvcCodeReviewerPhase05Cycle2 · Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-05-local-sync-state.md`

## Score: **5/10** — NOT accepted. 3 new criticals (N1-N3); C2 + C7 only partially closed.

C1, C3, C4, C5, C6, C8 genuinely fixed. C2 fix (re-freeze at dispatch) correct for PENDING successors but applied to UNCERTAIN retries too → breaks the plan's "identical frozen retry" + UnknownSuperseded invariant. Reproduced on real bundled-SQLite Room via scratch probe (deleted). Fake-DAO coordinator tests + direct-call reconcile tests hide it.

## C1-C8 resolution

| # | Status | Evidence |
|---|---|---|
| C1 transactions | **Resolved (mostly)** | `@Transaction` DAO methods `saveTodoAtomic/deleteTodoAtomic/cancelUnsentCreateAtomic/reconcileAcceptedAtomic/promoteStagingAtomic/replaceOutboxAtomic/discardConflictAtomic` (`doggo-dao.kt:140-212`); store + scan use them. Residual: freeze (`outbox-reconciliation.kt:118`), `markBatchUncertain`, `copyAsNew` (`coordinator:358-364`), `wipeLocalData` still multi-step; no rollback test. |
| C2 freeze at dispatch | **Partial / regressed** | PENDING successor now `update/expected=base` at dispatch (`outbox-reconciliation.kt:68-123`). But same path re-freezes UNCERTAIN (see N1). |
| C3 FIFO head gating | **Resolved** | per-row `minByOrNull{localRevision}` over ALL states; CONFLICT/UNKNOWN_SUPERSEDED/DISPATCHING head blocks (`:35-61`). Test exists. |
| C4 DISPATCHING recovery | **Resolved** | `recoverInterruptedDispatches()` at every round start (`coordinator:102`), `markBatchUncertain` in `NonCancellable` on cancel/exception (`:158-171`). Real-Room test. |
| C5 epoch | **Resolved** (PENDING) | dispatch binds `activeEpoch`+digest; epoch-mismatch round returns ResetRequired before dispatch so no cross-epoch rebinding reachable. |
| C6 order | **Resolved** | ORDER BY monotonic `localRevision` (meta counter in same txn); `reapplyConflict` now uses `allocateNextLocalRevision()` (QA report §6 item 2 is stale/inaccurate). `createdAtEpochMs` now dead column. |
| C7 leave fencing | **Partial** | `requestLeave` takes `coordinatorMutex` (waits in-flight round); `isFrozen` read by store writes + round; cancel-gate failure aborts before delete; `completePendingWipe` exists. Gaps: N3, generation still dead (W-below), `completePendingWipe` has no caller. |
| C8 scan validation | **Resolved** | null-data active row, `data.id==rowId`, strict ascending rowId, duplicate, empty+hasMore, missing cursor, batch overflow all abort + clear staging. Gaps: no UUID/revision upper bound, tombstone-with-data not rejected, page budget still ProtocolIncompatible. |

## Critical issues

**N1. UNCERTAIN retry is re-frozen against current base → never identical; UnknownSuperseded unreachable in real flow.**
`prepareAndFreezeBatch` recomputes kind/expectedRevision/digest for every op in batch, incl. `UNCERTAIN` heads (`outbox-reconciliation.kt:68-123`). Plan §6 / contracts §6-7: "UNCERTAIN recovery retains identical frozen request"; "replay reuses persisted opId/base/digest".
Probe on real Room: op frozen `create exp=0 dig=54629032` → UNCERTAIN → stage-1 scan promotes base rev1 (op had committed) → re-freeze `update exp=1 dig=fa2e48bd`, same opId → server sees same opId + different digest → `opIdReuse` (→CONFLICT) or digest mismatch → UNCERTAIN loop. Exactly the scenario UNCERTAIN exists for; stage-1 scan runs before every dispatch so it is the normal path.
Compounding: `frozenOp.deliveryState = DISPATCHING` is the snapshot passed to `reconcilePushResults`, so `op.deliveryState == UNCERTAIN` (`:239`) is always false → conflict after uncertain delivery becomes `CONFLICT`, never `UNKNOWN_SUPERSEDED` (probe: `batchStateSeenByReconcile=DISPATCHING`). `testUncertainDelivery…` calls reconcile directly with an UNCERTAIN op, bypassing freeze → green.
Fix: freeze only `PENDING`; UNCERTAIN → flip to DISPATCHING without touching kind/expected/digest/epoch; carry pre-dispatch state (`wasUncertain`) into reconcile (or persist before flip / pass original list). Add coordinator-level test: dispatch→timeout→UNCERTAIN→scan advances base→retry→request byte-identical; then REVISION_CONFLICT→UNKNOWN_SUPERSEDED.

**N2. Freeze overwrites concurrent coalesced edit (lost update, draft without op).**
`getEligibleDispatchBatch` snapshots op without store mutex; `prepareAndFreezeBatch` writes `op.copy(...)` over the row (`:118`, no mutex, no re-read, no state precondition). UI `saveTodoLocally` between the two coalesces into the same PENDING opId (new title + new localRevision) and is clobbered. Probe: `outboxTitle=[(v1, DISPATCHING)] draft=v2-edit@3 opRev=[2]` — outbox pushes v1 (localRevision 2), draft v2 (rev 3) stays forever: ack won't clear it (`draft.rev > op.rev`) and no op exists → edit never syncs, never repaired. Silent data-loss class; reintroduces the C1 "draft w/o op" symptom.
Fix: single store-mutex txn: re-read op by opId, require state ∈ {PENDING, UNCERTAIN}, apply targeted `UPDATE outbox SET kind=…,expectedRevision=…,digest=…,epoch=…,deliveryState='DISPATCHING' WHERE opId=? AND deliveryState IN (…)` (or `@Transaction` DAO method), and build wire DTO from the re-read row. Move `getEligible…` + freeze under the same lock.

**N3. Leave: cancel-gate failure leaves wipe marker on disk but unfreezes store.**
`performGuardedTeardown`: marker + generation written (`:102-104`), then gate throws → `isFrozen=false; return false` (`:112-117`) with marker NOT cleared. User is told leave failed and can keep editing; next startup `completePendingWipe` sees marker → deletes DB + clears vault → silent wipe of live edits. Test `testSchedulerCancelFailureAbortsTeardownFailClosed` asserts only `dbDeleted==false`/unfrozen, not marker state. Violates fail-closed + "cancel restores exact prior state".
Fix: either clear marker (+ roll back generation) on any pre-delete abort, or keep frozen + marker and treat leave as committed (retry on startup). Pick one; test marker state in both abort paths.

## High / Warnings

- W-A `checkReviewWork` (counts) runs before `isFrozen=true` (`account-leave-protocol.kt:77-90`) → edit between check and freeze is silently wiped. Freeze first, count, unfreeze on guard hit.
- W-B Generation still dead: coordinator `currentGeneration` only via `setGeneration` (no callers), never bumped by leave; `onReadGeneration`/`onWriteGeneration` default to no-op lambdas (fail-open defaults); read errors swallowed→0. Plan §7 requires fences before credential/DB/network/apply. QA defers to Phase 07 — plan assigns lifecycle/durable generation to 05. Decide owner.
- W-C `completePendingWipe` has no caller (Phase 07 composition?) — document owner; it also doesn't catch `onDeleteDatabaseArtifacts` throw.
- W-D `catch (_: Exception)` in leave (gate, delete, marker write) swallows `CancellationException` (cancel gate!) → coroutine cancel treated as failure, continues. Rethrow cancellation. Same pattern in `coordinator:166`, `HubSyncClient` (prev W4).
- W-E `reconcileAccepted` when base already ahead (`serverRevision < base`) deletes op but leaves draft (`:292-296`) → draft w/o op, shown pending forever. Probe: `draft=s-edit ops=0`. Apply same `clearDraft` rule there.
- W-F `reapplyConflict`: if no conflict head, falls back to `ops.lastOrNull()` and deletes a live PENDING/DISPATCHING op; new op gets highest localRevision → jumps behind existing PENDING successors (FIFO reorder); draft not cleared/retargeted. Guard to CONFLICT/UNKNOWN_SUPERSEDED head only.
- W-G `copyAsNew` non-atomic (delete op, delete draft, then save new): crash between = intent lost. Unconditional `deleteDraft` ignores newer draft; old-row successors remain. Move into one txn.
- W-H No retry cap/backoff: `consecutiveFailures` written, never read; plan "3 failed rounds blocks auto-retry". Combined with N1 hot-loops forever. Network failure → `IDLE`, no schedule. Page budget → ProtocolIncompatible (plan: restart null).
- W-I `isFrozen` plain `var` (not `@Volatile`/atomic), written outside store mutex.
- W-J Promotion does `getServerBase` per staged row (N queries; 10k rows ≈ 10k round-trips) though `preScanConfirmed` map is already loaded; promotion/scan bypass store mutex via public `store.dao` (prev W7).
- W-K Unresolved from cycle 1, unchanged: W3 error mapping (404/405, 400, English text persisted), W6 `OP_ID_REUSE/INVALID_RECORD`→CONFLICT, `RETRYABLE_FAILURE` stale `op.copy` upsert, W10 file perms/umask, W11 non-CSPRNG UUID duplicated ×2, W12 dead columns in v1 schema (`createdAtEpochMs` now also dead), W15 Room alpha pins + lenient dependency verification.

## Suggestions
- S1 One `RoomAccountStore.freezeForDispatch(batchLimit, epoch)` (mutex+txn) returning wire-ready rows + `wasUncertain`; eliminates N1/N2 and DAO leak.
- S2 Add real-Room tests: UNCERTAIN identical retry across base advance; freeze vs concurrent coalesce; rollback preserves base/outbox (inject failing DAO call inside `@Transaction`); stale-ack-base-ahead; 100/101/limit+1; leave marker state per abort path; coordinator end-to-end create-in-flight→edit→ack→successor (QA asked for it).
- S3 Scan: validate UUID shape/revision bound/tombstone-canonical; drop redundant `seenRowIds` (strict ordering already implies uniqueness).
- S4 `kotlin.uuid.Uuid.random()`/single helper; injected clock for 13+ `System.currentTimeMillis()` sites.
- S5 Android instrumented tests remain unexecuted (Phase 07 emulator).

## Positive
- Transactions real and used on every primary mutation path; schema/KSP unchanged and reproducible.
- FIFO head gating + monotonic localRevision clean and tested at unit level.
- Scan validation thorough; every failure path clears staging.
- Cancellation-safe push (`NonCancellable` uncertain-marking); restart recovery both at round start and tested on real SQLite.
- Leave now serialized under coordinator mutex; fail-closed on marker/gate/delete failures; startup wipe completion implemented.
- Exact CAS ack matching unchanged and correct.

## Reviewed files
`shared/src/commonMain/.../data/{room-account-store,doggo-dao,outbox-entity,server-base-entity}.kt`; `.../sync/{outbox-reconciliation,account-sync-coordinator,account-leave-protocol,staged-full-scan}.kt`; `jvmMain/.../jvm-account-files.kt`; `jvmTest/.../room-account-store-test.kt` (head); `commonTest/.../account-leave-protocol-test.kt` (gate-failure + wipe tests); plan phase-05 + contracts §6-10; prior review + QA cycle-2 report. ~2.2k LOC inspected.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks` | 87/87 pass per QA/Main (not rerun by me) |
| Scratch real-Room probe `ZzProbeTest` via `./gradlew --dependency-verification=lenient :shared:jvmTest --tests '*ZzProbeTest*'` | BUILD SUCCESSFUL; reproduced N1 (digest/expected rewritten on UNCERTAIN retry; reconcile sees DISPATCHING), N2 (outbox v1 vs draft v2 rev3), W-E (draft survives, 0 ops). File deleted; tree has no probe. |
| Android instrumented / live Hub / fault injection | not run (Phase 07) |

## Plan status
Phase 05 todo items remain unchecked. Plan status line 18 updated (cycle 2: 5/10, not accepted).

## Recommended actions (priority)
1. N1 freeze only PENDING; propagate pre-dispatch UNCERTAIN into reconcile; end-to-end test.
2. N2 atomic freeze with re-read under store mutex/txn.
3. N3 leave abort-path marker/frozen consistency + test.
4. W-A/W-E/W-F/W-G small atomicity fixes; W-B/W-C decide owner of generation + startup wipe wiring.
5. Retry cap (W-H), perf (W-J), cycle-1 leftovers (W-K).

## Unresolved questions
1. Is generation persistence + startup `completePendingWipe` wiring Phase 05 or Phase 07? (plan text says 05 owns lifecycle/durable generation; QA says deferred.)
2. N3: on cancel-gate failure should leave be rolled back (clear marker) or treated as committed and retried on next start? Plan §8 says "cancel restores exact prior state" → rollback preferred.
3. Does server dedupe replay on (opId,digest) or opId only? Affects whether N1 shows as CONFLICT or UNCERTAIN loop (both wrong).
4. Accept Room alpha pins + lenient verification for this phase gate?
