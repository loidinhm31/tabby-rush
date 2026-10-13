# Doggo Hub Compatibility Acceptance Criteria

Date: 2026-10-14  
Phase: Phase 03 Strict Server & Legacy Isolation (Implementation Complete; Runtime Acceptance Pending)  
Related Docs: [Hub Schema Integration](./hub-schema-integration.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Evidence Summary](./evidence/hub-compatibility-summary.md)

---
## Scope and Isolation Policy
Strict-v1 sync establishes absolute isolation between Doggo and other Hub applications:
- **Namespace Isolation**: Strict data resides in dedicated collections prefixed `_strict_v1_`, completely separate from legacy `app*` collections.
- **Legacy Route Rejection**: Legacy routes (`/api/v1/sync/:app_id/{push,pull,delta,purge,restore}`) reject strict-mode apps with HTTP 426 (`protocolUnsupported`).
- **Strong Route Protection**: Strict routes (`/api/v1/sync/:app_id/strong/{bootstrap,push,pull}`) require `syncMode === "strict-v1"` and matching canonical `epoch`. Stale or forged epochs return HTTP 409 (`epochMismatch`).
- **Unchanged Legacy Applications**: Legacy apps with absent `syncMode` continue using legacy routes, storage, and jobs with zero behavioral modification.

## Acceptance Gates
1. **Digest Vectors**:
   - Rust SHA-256 binary serialization must match Kotlin Phase 02 test vectors deterministically for Create, Update, and Delete operations.
2. **Deterministic Mutation Semantics**:
   - Create: expectedRevision = 0, server revision = 1, insert-only.
   - Update: expectedRevision = current, server revision = current + 1, live row predicate.
   - Delete: permanent compact tombstones with empty data, server revision = current + 1.
3. **Replay Confirmation Durability**:
   - Replay of identical `(opId, digest)` triggers conditional write updating `confirmationNonce` with journaled write concern.
   - Successful confirmation requires `matched_count == 1`.
   - Superseded operations return `revisionConflict` without acknowledging obsolete states.
4. **Stable-Key Scan Pagination**:
   - Full pull scans ordered by immutable `rowId` ascending with simple collation.
   - Page size <= 100 with 1 lookahead item to detect `hasMore`.
   - Cursor derived strictly from last returned row and verified against authenticated scope.
5. **Operator Admission Preflight**:
   - `strong_sync_admission` CLI validates candidate namespaces using the identical Rust admission logic before epoch cutover.

---

## Acceptance Gate Status & Verification Matrix

| Gate Category | Verification Scope | Status | Notes |
|---|---|---|---|
| **Cryptographic Parity** | Digest vectors (Create, Update, Delete) | **VERIFIED** | Matches reference vectors byte-for-byte; live Kotlin-Rust integration deferred to Phase 07 Gradle test runner. |
| **Unit & Route Logic** | CAS mutation, replay logic, cursor bounds, route guards | **VERIFIED** | 36 unit and route tests pass in `glean-hub-sync` crate. |
| **Static Analysis** | Clippy linting across all targets | **VERIFIED** | `cargo clippy --locked --all-targets` passes with 0 warnings. |
| **Script Syntax** | Node.js provisioning and smoke scripts | **VERIFIED** | `node --check` passes cleanly for both scripts. |
| **Live Mongo Engine** | Live MongoDB push/pull, CAS, replay, admission | **DEFERRED** | Hard-fails without `MONGO_TEST_URI`; deferred to Phase 07 device sync. |
| **Mounted Live Router** | Full HTTP stack with live Mongo | **DEFERRED** | Deferred to Phase 07 device sync. |
| **Old Binary Isolation** | Rollback binary and legacy client regression | **DEFERRED** | Requires packaged prior Hub binary; deferred to Phase 08 release operations. |

---

## Open Review Findings & Production Blockers (Cycle 4)

The following findings from Code Review Cycle 4 are documented as technical requirements to be addressed during Phase 07/08 prior to production deployment:
1. **Durability Topology Evaluation**: `get_strict_collection()` falls back to standalone (`w: 1, local`) if the `hello` command fails on a replica set. Must be hardened to fail closed (`NotAdmitted` or `InternalError`) or derive topology once during admission.
2. **Corrupt Row Coercions**: `classify_reread` and pull data default unwraps (`unwrap_or`) currently coerce corrupted or non-document rows to default values. Must fail closed for strict consistency.
3. **Script Key Custody & Acceptance**: `hub-schema-provision.mjs` echoes API keys to stdout, and `hub-compatibility-smoke.mjs` accepts HTTP 401 as passing when credentials are not supplied. Scripts must be run with valid credentials for genuine acceptance verification.
