# Code Review Cycle 3 — Phase 05 Durable Local State & Strict Per-Record Sync

Date 2026-10-14 · Reviewer: EvcCodeReviewerPhase05Cycle3 · Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-05-local-sync-state.md`

## Score: **7/10** — NOT yet accepted. N2, N3 resolved. N1 only partly resolved: 1 critical (N1b) remains.

## N1-N3 resolution

| # | Status | Evidence |
|---|---|---|
| N1 UNCERTAIN retry re-frozen | **Partial** | Part 1 fixed: `prepareAndFreezeBatch` UNCERTAIN branch keeps kind/expectedRevision/digest/epoch, flips to DISPATCHING only (`outbox-reconciliation.kt:72-78`). Part 2 (UnknownSuperseded) still wrong in common paths → see N1b. |
| N2 freeze overwrites coalesced edit | **Resolved** | PENDING path uses `freezeOutboxAtomic` (`@Transaction` DAO, re-read by opId, requires `localRevision==expected && state==PENDING`; `doggo-dao.kt:214-226`). Stale → op dropped from batch, not pushed, row untouched. Coalesce only targets PENDING so UNCERTAIN branch has no race. Caveat: CAS only unit-tested on `FakeDoggoDao`; real-Room not exercised (SQLite txn serialization makes it sound by design). |
| N3 leave gate failure leaves marker + unfreezes | **Resolved** | Gate-fail → `onClearWipeMarker` → unfreeze only if clear succeeded; else stays frozen + marker (committed/fail-closed) (`account-leave-protocol.kt:114-130`). **QA cycle-3 report §3/§6 item 1 is stale**: it claims `catch(_){}` then unconditional `isFrozen=false`; current code has `markerCleared` guard. Test asserts marker cleared on happy-abort; marker-clear-failure path has no test. |

## Critical issues

**N1b. `wasUncertain` proxy misses UNCERTAIN ops with `consecutiveFailures==0` → CONFLICT instead of UNKNOWN_SUPERSEDED.**
- Dispatched snapshot is `op.copy(deliveryState=DISPATCHING)` (`:74`), so `op.deliveryState==UNCERTAIN` at `:248` is always false; only `op.consecutiveFailures>0` works.
- `consecutiveFailures` incremented only in `markBatchUncertain` (`:173`) and RETRYABLE_FAILURE (`:271`). NOT incremented by: `recoverInterruptedDispatches` SQL (`doggo-dao.kt:76`, crash/restart — the canonical UNCERTAIN source), response-omitted / rowId-mismatch / digest-mismatch / acceptedRevision-mismatch paths (`updateDeliveryState`, `:193-232`).
- Reproduced (scratch test, deleted): DISPATCHING op → `recoverInterruptedDispatches` → `UNCERTAIN failures=0` → freeze → REVISION_CONFLICT → final state `CONFLICT` (expected `UNKNOWN_SUPERSEDED`). Violates plan §5/§6 ("conflict after previously uncertain delivery becomes UnknownSuperseded"); user gets plain conflict UX/reapply on an op that may have committed.
- Existing N1 test only passes because it seeds `consecutiveFailures=1` (`outbox-reconciliation-test.kt:475`).
- Fix: capture `wasUncertain` before flipping state (return pair/flag from `prepareAndFreezeBatch`, or persist an explicit `hasBeenUncertain`/`attempts` marker); or make recovery + all UNCERTAIN transitions increment `consecutiveFailures`. Add test: crash-recovered (failures=0) → retry → conflict → UNKNOWN_SUPERSEDED; and omitted-result → retry → conflict.

## Warnings (open)
- W1 Cancellation swallowed: `catch (_: Exception)` in leave (gate, read/write generation, marker, delete) and coordinator; cancel gate itself can be cancelled → treated as failure. Rethrow `CancellationException`.
- W2 Marker-write abort path: `onWriteGeneration` succeeded then `onWriteWipeMarker` throws → unfreeze with generation bumped and possibly partial marker, no clear/rollback (`:105-112`). Roll back/clear on this path too.
- W3 Generation still inert: `setGeneration` no callers; `onRead/WriteGeneration` default no-ops; read error → 0. Plan §7 wants fences in 05. `completePendingWipe` has no caller and does not catch delete throw. Decide owner (05 vs 07) — carried from cycle 2 W-B/W-C.
- W4 `reapplyConflict`/`copyAsNew` still fall back to `ops.lastOrNull()` (`coordinator:283,350`) → may act on live PENDING/DISPATCHING op; `copyAsNew` multi-step non-atomic. (W-F/W-G unchanged.)
- W5 Retry cap/backoff still missing: `consecutiveFailures` never read by coordinator; plan "3 failed rounds blocks auto-retry" (W-H unchanged).
- W6 UNCERTAIN branch: `updateDeliveryState(DISPATCHING)` unconditional, no state precondition (low risk; single coordinator mutex).
- W7 `freezeOutboxAtomic` per-op transaction, `getEligibleDispatchBatch` loads `getAllOutbox()` whole table each round; `setOf(...)` allocated per row.
- W8 Cycle-1/2 leftovers unchanged: error mapping W3, OP_ID_REUSE/INVALID_RECORD→CONFLICT, RETRYABLE stale `op.copy` upsert, file perms, non-CSPRNG UUID, dead columns (`createdAtEpochMs`), Room alpha pins + lenient dep verification.
- Resolved since cycle 2: W-A (freeze before count, `executeLeave:76-85`), W-E (stale-ack clears draft, `room-account-store.kt:295-302`), W-I (`@Volatile isFrozen`).

## Suggestions
- S1 Real-Room tests for: freeze CAS vs concurrent `saveTodoLocally`; crash-recovered UNCERTAIN end-to-end through coordinator; rollback-in-txn.
- S2 Test leave marker-clear-failure keeps store frozen; marker-write-failure marker/generation state.
- S3 Store `wasUncertain` explicitly rather than infer from counters.

## Positive
- N2 CAS implemented at DAO transaction level with state+revision precondition; good test.
- N3 fail-closed rollback logic correct and more defensive than QA report describes.
- UNCERTAIN identical-retry now preserves frozen digest/base — core idempotency invariant restored.
- Suite stable: 89 pre-existing tests pass.

## Reviewed files
`shared/src/commonMain/.../sync/{outbox-reconciliation,account-leave-protocol,account-sync-coordinator(grep)}.kt`; `.../data/{doggo-dao,room-account-store}.kt`; `commonTest/.../sync/{outbox-reconciliation-test,account-leave-protocol-test}.kt`; plan phase-05; cycle-2 review + cycle-3 QA reports. ~1.2k LOC read.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks -i` (with temp `ZzProbeTest`) | 90 run, 1 failed = probe only (`PROBE recovered state=UNCERTAIN failures=0`, `PROBE final=CONFLICT`); other 89 pass. Probe deleted afterwards. |
| Android instrumented / live Hub / fault injection | not run (Phase 07) |

## Plan status
Phase 05 todo items remain unchecked; plan line 18 updated (cycle 3: 7/10, not yet accepted).

## Recommended actions
1. Fix N1b (explicit wasUncertain / increment on every UNCERTAIN transition) + regression tests (crash-recovered, omitted result).
2. W1/W2 cancellation + marker-write abort rollback.
3. Decide generation/startup-wipe owner (W3); guard reapply/copyAsNew heads (W4); retry cap (W5).

## Unresolved questions
1. Generation persistence + startup `completePendingWipe` wiring: Phase 05 or 07?
2. Server idempotency: dedupe on (opId,digest) or opId only? (affects how N1b misclassification manifests.)
3. Accept Room alpha pins + lenient dependency verification for phase gate?
4. QA cycle-3 report §3/§6 N3 "defect" appears stale vs current code — was QA run against an older tree?
