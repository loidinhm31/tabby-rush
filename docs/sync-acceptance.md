# Doggo Sync & Local State Acceptance Criteria

Date: 2026-10-14  
Phase: Phase 05 Durable Local State and Strict Per-Record Sync (Implementation Complete; Runtime Acceptance Pending)  
Plan: `plans/261013-1540-doggo-glean-hub-reuse`  
Related Docs: [Local Storage & Sync](./local-storage-and-sync.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md)

---

## 1. Acceptance Gates

| Gate ID | Area | Acceptance Rule & Specification | Status |
|---|---|---|---|
| **GATE-05-01** | **Room Schema & Export** | Room database compiles with KSP to schema version 1. Schema JSON exported and verified at `shared/schemas/dev.doggo.shared.data.DoggoDatabase/1.json`. Destructive fallback is strictly prohibited. | **VERIFIED** |
| **GATE-05-02** | **Account Partitioning** | Storage directory is isolated strictly by canonical `AccountKey` SHA-256 string. Path traversal attempts (`..` or symlinks) outside canonical base directory trigger `SecurityException`. | **VERIFIED** |
| **GATE-05-03** | **Atomic Local Save** | `saveTodoLocally` and `deleteTodoLocally` write `account_sync_meta`, `editor_drafts`, and `outbox` atomically via Room `@Transaction` methods before returning success to caller. | **VERIFIED** |
| **GATE-05-04** | **Unsent Create-Delete** | Deleting a record with only an unsent `CREATE` operation cancels locally via `cancelUnsentCreateAtomic` without generating network wire traffic. | **VERIFIED** |
| **GATE-05-05** | **FIFO Per-Row Outbox** | Earliest operation by `localRevision` is dispatched per `rowId`. If the head operation is `DISPATCHING`, `CONFLICT`, or `UNKNOWN_SUPERSEDED`, successor operations on that row are blocked. | **VERIFIED** |
| **GATE-05-06** | **Wire Payload Bounds** | Dispatched push batches enforce `<= 100` operations, at most one operation per `rowId`, and `<= 256 KiB` total payload size. | **VERIFIED** |
| **GATE-05-07** | **Staged Full Scan** | Cursor pull pagination stages rows in SQLite `scan_staging`. Repeating cursors or non-progressing cursors abort with `SafeError.ProtocolIncompatible`. Promotes only upon reaching terminal page. | **VERIFIED** |
| **GATE-05-08** | **Pre-Scan Identity Check** | Missing pre-scan confirmed identity within the same epoch triggers `SafeError.ResetRequired("CONSISTENCY_ERROR")` to prevent silent data loss. | **VERIFIED** |
| **GATE-05-09** | **Monotonic Promotion** | Higher server revision promotes to `server_base`. Equal revision checks logical equivalence; divergent data triggers `SafeError.ProtocolIncompatible("DIVERGENT_REVISION")`. | **VERIFIED** |
| **GATE-05-10** | **Exact CAS Ack Matching** | Push acknowledgement requires matching `opId`, `rowId`, `digest`, status in `[accepted, replayed]`, and `acceptedRevision == expectedRevision + 1`. Response omission or mismatch transitions op to `UNCERTAIN`. | **VERIFIED** |
| **GATE-05-11** | **Newer Draft Preservation** | Acknowledging an outbox op does not clear `editor_drafts` if `draft.localRevision > outbox.localRevision`, preserving subsequent user edits. | **VERIFIED** |
| **GATE-05-12** | **UnknownSuperseded** | Server conflict (`revisionConflict`, `missingRecord`, `deletedRecord`) for an operation that had previous uncertain delivery history is marked `UNKNOWN_SUPERSEDED` for explicit user review. | **VERIFIED** |
| **GATE-05-13** | **Bounded Retries** | Operations receiving `retryableFailure` track `consecutiveFailures`. In-flight network failures mark batch `UNCERTAIN` for controlled re-dispatch. | **VERIFIED** |
| **GATE-05-14** | **Guarded Account Leave** | Teardown checks 5 categories of unresolved work (`draftCount`, `pendingCount`, `uncertainCount`, `conflictCount`, `unknownSupersededCount`); aborts unless explicit discard is invoked. | **VERIFIED** |
| **GATE-05-15** | **Fail-Closed Teardown** | Strict teardown sequence: freeze store -> write generation + `wipe.marker` outside DB -> cancel/await schedulers -> close Room -> delete DB/WAL/SHM -> clear vault -> clear marker. Failed marker persistence aborts before deleting data. | **VERIFIED** |
| **GATE-05-16** | **Dispatch Crash Recovery** | `recoverInterruptedDispatches()` resets leftover `DISPATCHING` operations to `UNCERTAIN` on startup. Push coroutine cancellation uses `NonCancellable` to ensure in-flight batches are marked `UNCERTAIN`. | **VERIFIED** |

---

## 2. Test Verification Matrix

| Test Suite Class | File Path | Scope & Assertions | Status |
|---|---|---|---|
| `HubSyncClientTest` | `shared/src/commonTest/kotlin/.../hub-sync-client-test.kt` | Unit tests for bootstrap, push, pull, batch size <= 100 enforcement, payload size <= 256 KiB enforcement, error envelope mapping (400, 401, 404, 409, 426, 503). | **PASSED** (JVM) |
| `StagedFullScanTest` | `shared/src/commonTest/kotlin/.../staged-full-scan-test.kt` | Tests cursor pagination, cursor loops, batch overflow, non-progressing cursors, pre-scan identity check (`CONSISTENCY_ERROR`), monotonic promotion, divergent revision error. | **PASSED** (JVM) |
| `OutboxReconciliationTest` | `shared/src/commonTest/kotlin/.../outbox-reconciliation-test.kt` | Tests FIFO dispatch per row, head-blocking on CONFLICT/UNKNOWN_SUPERSEDED, dispatch freezing, CAS ack verification, newer draft preservation, UnknownSuperseded, bounded retries. | **PASSED** (JVM) |
| `AccountSyncCoordinatorTest` | `shared/src/commonTest/kotlin/.../account-sync-coordinator-test.kt` | Tests 3-stage sync loop (scan -> push -> scan), mutex serialization, generation fencing, conflict actions (`discardConflict`, `reapplyConflict`, `copyAsNew`), cancellation safety. | **PASSED** (JVM) |
| `AccountLeaveProtocolTest` | `shared/src/commonTest/kotlin/.../account-leave-protocol-test.kt` | Tests 5-category review work guard, freeze-before-review, marker write failure abort, scheduler cancellation failure rollback, database deletion fail-closed, `completePendingWipe`. | **PASSED** (JVM) |
| `RoomAccountStoreTest` (JVM) | `shared/src/jvmTest/kotlin/.../room-account-store-test.kt` | Real SQLite tests with `BundledSQLiteDriver` in temporary directory: schema v1, atomic save, unsent create-delete cancel, FIFO outbox, base advancement, recovery of interrupted dispatches, wipe. | **PASSED** (JVM) |
| `RoomAccountStoreTest` (Android) | `shared/src/androidInstrumentedTest/kotlin/.../room-account-store-test.kt` | Android instrumented Room tests with app-private storage, SQLite driver, Keystore integration. | **DEFERRED** (Phase 07) |

---

## 3. Implementation Status & Deferred Runtime Boundaries

1. **JVM Verification**: All 89 common and JVM unit tests pass via `./gradlew --dependency-verification=lenient :shared:jvmTest`.
2. **Review Status**: Code Review Cycle 3 scored 7/10 with N2 (CAS freeze) and N3 (leave marker rollback) resolved. Follow-up reviews track candidate optimizations for UNCERTAIN crash recovery counter semantics and cancellation rethrows.
3. **Deferred to Phase 07 (Device Sync & Live Infrastructure)**:
   - **Android Instrumented Tests**: Execution on real Android emulator/device (`connectedDebugAndroidTest`) requires Android SDK platform tooling, deferred to Phase 07.
   - **Live Hub / MongoDB Integration**: End-to-end sync against running Rust Hub server and MongoDB replica set is deferred to Phase 07 device sync operations. Common tests in Phase 05 utilize deterministic mocked HTTP transport engines.
