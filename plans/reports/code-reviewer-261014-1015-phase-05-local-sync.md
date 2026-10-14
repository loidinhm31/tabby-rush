# Code Review — Phase 05 Durable Local State & Strict Per-Record Sync

Date 2026-10-14 · Reviewer: EvcCodeReviewerPhase05 · Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-05-local-sync-state.md`

## Score: **4/10** — NOT accepted. 8 critical blockers.

Schema/KSP generation, CAS-ack field matching, canonical happy-path scan promotion good. But atomicity, FIFO gating, restart recovery, frozen-revision semantics, leave fencing do not meet plan invariants. Green tests (fake DAO) hide this; 5 defects reproduced by scratch probe on real bundled-SQLite Room (probe deleted).

## Scope
- 29 owned paths + `shared/build.gradle.kts` testLogging diff. ~3.6k LOC prod+test (data 7 files, sync 6, platform 4, tests 7, schema, 2 docs).
- Focus: security, perf, arch, YAGNI/KISS/DRY, CAS ack, FIFO dispatch, staged scan, UnknownSuperseded, crash-safe leave, Room KMP schema.

## Critical issues

| # | Issue | Evidence |
|---|---|---|
| C1 | **No transactions anywhere.** `saveTodoLocally` = 3 separate writes (meta counter, draft, outbox); `deleteTodoLocally`, `reconcileAccepted` (base+draft+outbox), `promoteStaging`, conflict resolvers likewise. Zero `useWriterConnection`/`immediateTransaction`/`@Transaction`. Violates "atomic draft/localRevision/outbox before saved-locally", "rollback preserves base/outbox". Crash between draft and outbox = draft w/o op (never repaired); crash in ack = base advanced, op still queued → replay. | `room-account-store.kt:120-200,206-271,284-323`; `staged-full-scan.kt:182-237`; `account-sync-coordinator.kt:232-327` |
| C2 | **Successor `expectedRevision` frozen at enqueue, stale after predecessor ack.** Edit while op1 DISPATCHING → op2 built from current base (kind create, expected 0). After op1 ack base=1, op2 still `create/expected=0` → guaranteed REVISION_CONFLICT on next push. Any edit during in-flight sync yields false conflict. Plan: freeze at **dispatch**. Probe: `PROBE1 successor kind=create expected=0 base=1`. | `room-account-store.kt:147-161`; `markDispatching` only flips state |
| C3 | **FIFO head bypass.** `getPendingOutbox` filters to PENDING/DISPATCHING/UNCERTAIN, so a CONFLICT / UNKNOWN_SUPERSEDED head vanishes and its PENDING successor becomes "earliest" and dispatches. Plan: successors cannot dispatch before prior resolution. Probe: `PROBE2 eligible=[(false, PENDING)]` with CONFLICT head. | `doggo-dao.kt:51`; `outbox-reconciliation.kt:269-286` |
| C4 | **No DISPATCHING→UNCERTAIN recovery on restart/cancel.** Only `markBatchUncertain` on `ApiResult.Failure` (not on `CancellationException`/crash). Stale DISPATCHING head is non-eligible → row blocked permanently. Probe: `PROBE3 eligible=0`. JVM "restart" test only asserts state is still DISPATCHING. Plan §2 explicit. | `outbox-reconciliation.kt:277`; `account-sync-coordinator.kt:133-147` |
| C5 | **Frozen epoch mismatch for pre-bootstrap ops.** Ops created before `activeEpoch` known freeze `epoch=""` + digest over `""`; `toDto` then sends `activeEpoch`, so server digest ≠ stored → result.digest mismatch → UNCERTAIN forever (retry loop never converges). No filter of `op.epoch != activeEpoch` ("old-epoch separate"). Probe: `PROBE4 epoch=''`. | `room-account-store.kt:125`; `outbox-reconciliation.kt:292-313` (`epoch = activeEpoch`) |
| C6 | **FIFO order keyed on caller clock.** `createdAtEpochMs = todo.updatedAt` (UI-supplied) vs `System.currentTimeMillis()` for deletes; ties/backwards time → `ORDER BY createdAtEpochMs` unstable; `loadTodos` `latestOp = lastOrNull()` can pick wrong op. Probe: `PROBE5 order=[create@5000, create@5000, delete@1791947895425]`. Need monotonic seq (use `localRevision`/autoinc). | `room-account-store.kt:197,268`; `doggo-dao.kt:45-51` |
| C7 | **Leave not fenced; freeze/generation are dead code.** `isFrozen` write-only (never read by store/coordinator → edits not frozen). Leave does not take `coordinatorMutex`, does not cancel/await in-flight round; `currentGeneration` only set via manual `setGeneration`, never bumped, never persisted/read (`readGeneration/writeGeneration` unused). Round can write to closed/wiped DB (late callbacks). `schedulerCancelGate` failure swallowed (`catch Exception` also eats CancellationException) then DB deleted → violates fail-closed, "cancelAndAwait then delete". Startup marker recovery documented (`docs/local-storage-and-sync.md:123`) but **no code consumes marker** (only `hasPendingWipe`, no reader of marker content). | `account-leave-protocol.kt:56,89,434-483`; `account-sync-coordinator.kt:43-47`; `*-account-files.kt` |
| C8 | **Scan accepts invalid rows silently / weak validation.** `deleted=false && data==null` → staged as empty title/0 createdAt and promoted (silent corruption). No check `data.id == rowId`, rowId ordering strictly increasing across pages, duplicate rowIds, empty-page+hasMore non-progress (loops to 1000 pages), tombstone canonical-empty, UUID/revision bounds. `seenRowIds` collected, never used (dead). Plan §3/§4: invalid row aborts, reject nonprogress/order. | `staged-full-scan.kt:97-120` |

## Warnings

- W1 `reconcileAccepted(opId, rev)` is unconditional upsert: not monotonic (acks lower revision over higher base), no epoch/account/generation check, no expectedRevision+1 re-check (check lives only in caller). "Old ack advances base monotonically" not enforced at store. Base `updatedAt` from local clock, not server.
- W2 `discardConflict` deletes **all** ops+draft for row w/o state check → destroys PENDING successors and non-conflict rows. `reapplyConflict`: `localRevision = System.currentTimeMillis()` mixes clock with counter → later `draft.localRevision <= op.localRevision` always true (clears newer drafts); emits `update/expected 0` for never-created rows; can target tombstone (no-implicit-undelete); uses local (not live) base. `copyAsNew` ok but non-atomic.
- W3 Sync client error mapping vs plan: 404/405 → Validation (plan: ProtocolIncompatible); 400 → Validation (plan: protocol pause; coordinator maps Validation→IDLE, so invalidRequest silently retries). Server English `error` text passed through as message and stored in DB `errorMessage` (plan: no English-error behavior). Parse-exception messages interpolated (`e.message`) — can echo body; sanitize.
- W4 `catch (e: Exception)` around `response.bodyAsText()` swallows `CancellationException` and turns I/O errors into `ProtocolIncompatible`. Rethrow cancellation.
- W5 Scan/round policy gaps: no backoff/"3 failed rounds blocks auto-retry" (`consecutiveFailures`/`consecutiveFailedRounds` never read); network failure → `SyncStatus.IDLE` with no schedule; `PAGE_BUDGET_EXCEEDED` → ProtocolIncompatible (plan: restart null, not protocol fault; large accounts permanently paused); no response-vs-request validation in client (results unique/known opId, `Bootstrap.appId`, `tables` contain `todos`, epoch non-empty). Duplicate result `opId` silently last-wins.
- W6 `OP_ID_REUSE`/`INVALID_RECORD` mapped to CONFLICT (user-resolvable) — plan: invalid/protocol failures pause. `RETRYABLE_FAILURE` writes stale `op.copy(...)` snapshot over DB row.
- W7 Promotion/scan bypass store mutex (`store.dao` public, used directly by scan/coordinator/leave) → UI edits read base unlocked mid-promotion. Layering leak; make dao internal and expose store methods.
- W8 Staging lacks "scan-start confirmed identities" record in DB (kept in memory only; plan Architecture says staging records them so late acks survive promotion). Acceptable if scan always restarts, but promotion non-atomic (C1) so crash mid-promotion leaves partial base.
- W9 Leave: `nextGeneration = System.currentTimeMillis()` not monotonic (clock back → fence regress); `readGeneration` swallows parse error→0 (fail-open). No dir fsync after `ATOMIC_MOVE`; `clearWipeMarker` ignores delete failure. No sync-then-leave flow (plan §8), no old-epoch review count, no cancel-restores-state test.
- W10 File perms: account dirs/DB created with default umask (0755/0644 on Linux); plan requires restrictive user-data paths. `deleteDatabaseAndArtifacts` skips `-journal`; `getAccountDirectory` creates dir as side effect of `hasPendingWipe`.
- W11 UUID via `kotlin.random.Random` (not CSPRNG) duplicated in store + coordinator. `kotlin.uuid.Uuid.random()` (Kotlin 2.1.10) or one shared helper.
- W12 Unused/YAGNI: `AccountSyncMetaEntity.{lastSyncTime,syncStatus,safeErrorCode,safeErrorMessage,consecutiveFailedRounds,credentialPersistencePending}`, `DoggoDao` clear/delete/get variants (`getActiveServerBase`, `deleteServerBase`, `getStagedRecord`, `getAllServerBase` dupes…). Schema v1 ships dead columns → forever migration burden.
- W13 Tests are weak evidence: sync suites run on `FakeDoggoDao` (no Room); no tests for transactions/rollback, schema validation/downgrade failure, WAL/SHM presence, partition isolation, 100/101/limit+1, out-of-order/duplicate rows, tombstone canonical, CancellationException mid-push, restart DISPATCHING→UNCERTAIN, successor revision, leave-vs-inflight-round. Plan §9/§10 list these explicitly. Existing `testReconcileAcceptedPreservesNewerDraft` never asserts successor op state, so it passed over C2.
- W14 QA report `qa-261014-1009-...md` §2.6 cites tables `todos_draft/todos_outbox/todos_server_base` that do not exist (actual: `editor_drafts/outbox/server_base`) — description inaccurate; treat per-test narrative as unverified (pass/fail totals only). Android 4 instrumented tests not executed.
- W15 Room `2.7.0-alpha12`/sqlite `2.5.0-alpha12` alpha pins in production path; `verification-metadata` still lenient (QA note) — dependency integrity gate open.

## Suggestions

- S1 One `AccountDatabase.transaction { }` wrapper (`useWriterConnection { it.immediateTransaction {} }`) and move every multi-step mutation into `RoomAccountStore`/`@Transaction` DAO methods.
- S2 Outbox schema: add `seq INTEGER` monotonic PK order (or reuse localRevision), `INDEX(rowId, seq)`, `INDEX(deliveryState)`. Eligibility query = per-row min(seq) among **all** non-acked states; dispatch iff head ∈ {PENDING, UNCERTAIN}.
- S3 Freeze at dispatch: `markDispatching` in one txn recomputes `expectedRevision` from current base (successor), digest, kind (create→update), then persists; toDto uses stored `epoch`; refuse ops with `epoch != activeEpoch` (surface as old-epoch review).
- S4 Startup `recoverInterrupted()` DISPATCHING→UNCERTAIN in txn; wrap push in `try/finally(NonCancellable)`.
- S5 DRY: shared base class for Android/JVM account-files (identical ~110 LOC; could live in a `jvmAndAndroid` intermediate or commonMain on `java.io`); single `executeStrict(path, body, parser)` in `HubSyncClient` replacing 3 copy-pasted blocks; one UUID helper.
- S6 Replace `System.currentTimeMillis()` in commonMain (13 sites) with injected clock (`kotlinx-datetime` already a dep) → deterministic tests, non-JVM-ready.
- S7 Leave: take coordinator mutex, set `AtomicBoolean frozen` checked by store write path, persist generation via `writeGeneration` = max(read)+1, fail leave if cancel gate throws, add startup `completePendingWipe()` that reads marker.
- S8 Move to stable Room when available; document pin rationale.

## Positive

- Real KSP-generated schema v1 (`identityHash 6ebd06d2…`), regen byte-identical (sha256 stable across `--rerun-tasks`); no destructive fallback; explicit empty migration catalog; BundledSQLiteDriver on both platforms.
- Exact CAS ack matches opId+rowId+digest+`acceptedRevision==expected+1`; equal payload/omission never acks; UNCERTAIN→UnknownSuperseded rule present and tested at unit level.
- Push client enforces ≤100 unique rowIds and 256 KiB; epoch/protocol verified on every response; strict-v1 only, no legacy path.
- Wipe marker stored outside DB via fsync+atomic move; marker-write failure aborts before deletion (tested); path-traversal canonical check on account dir.
- Staged scan: staging cleared on every failure path; same-epoch pre-scan identity missing → ResetRequired, equal-revision divergence → ProtocolIncompatible.

## Reviewed files
`shared/src/commonMain/.../data/{doggo-database,server-base-entity,outbox-entity,editor-draft-entity,scan-staging-entity,account-sync-meta-entity,doggo-dao,database-migrations,room-account-store}.kt`; `.../sync/{hub-sync-dtos,hub-sync-client,staged-full-scan,outbox-reconciliation,account-sync-coordinator,account-leave-protocol}.kt`; `androidMain/.../data/{android-database-builder,android-account-files}.kt`; `jvmMain/.../data/{jvm-database-builder,jvm-account-files}.kt`; `jvmTest/.../room-account-store-test.kt`; test inventory of 5 commonTest sync suites + android instrumented store test (names/structure only); `shared/schemas/.../1.json`; `shared/build.gradle.kts` diff; `docs/local-storage-and-sync.md` (wipe section); `contract/{strict-sync-contract,safe-errors,account-store,sync-coordinator}.kt`; QA report.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks` | BUILD SUCCESSFUL (all JVM tests pass; fake-DAO + 7 real-SQLite tests) |
| schema sha256 before/after rerun | identical `e6de3566…571b` → KSP schema reproducible |
| scratch probe `ProbeTest` (real in-memory Room/bundled SQLite) | C2, C3, C4, C5, C6 reproduced (output above); file removed, tree clean of probe |
| Android instrumented / `connectedDebugAndroidTest` | not run (needs emulator; Phase 07) |
| Live Hub / crash / fault-injection gates | not run (Phase 07) |

## Plan status
Phase 05 todo items remain **unchecked / not accepted**. Plan file status line updated to reflect review blockers.

## Recommended actions (priority)
1. C1 transactions → 2. C2/S3 freeze at dispatch → 3. C3/C6/S2 outbox seq + head gating → 4. C4 interrupted-dispatch recovery + cancellation-safe push → 5. C5 epoch handling → 6. C7 leave fencing + startup wipe recovery + generation persistence → 7. C8 scan validation → 8. W1/W2 store-level ack/resolution semantics → 9. add real-Room tests listed in W13 → 10. W3-W15 cleanup.

## Unresolved questions
1. Is first-run bootstrap guaranteed before the first local edit (Phase 07 composition)? If yes C5 is narrowed to restore/reset flows; still needs epoch filter.
2. Who owns startup `completePendingWipe` + generation persistence — Phase 05 (docs claim so) or Phase 07? Currently neither implemented.
3. Is hosting leave "sync-then-leave" (plan §8) intended in Phase 05 coordinator or in 07/UI?
4. Does server `retryableFailure` guarantee not-committed? If so UNCERTAIN downgrade is over-conservative; if not, current mapping right.
5. Accept Room alpha pins for phase gate, or require stable tuple (plan Unresolved: "Actual Room/KSP tuple is 02 prerequisite")?
