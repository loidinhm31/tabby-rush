# Code Review Summary: Phase 03 Hub v2 Admission, Fencing, and Migration

**Date**: 2026-10-15  
**Review Target**: Phase 03 Hub versioned contract, admission guard, and data-preserving migration (`phase-03-hub-versioned-contract-and-migration.md`)  
**Repositories Involved**:
- Backend: `/home/loidinh/WS/glean-hub` (`glean-hub-plugin/glean-hub-sync`)
- Client & Test Vectors: `/home/loidinh/WS/tabbyrush`
**Reviewer Role**: evcCodeReviewerCycle3 (Cycle 3 Final Review after atomic permitCount CAS and resumption accounting fixes)

---

## Scope
- **Files reviewed**:
  1. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/strict_upgrade.rs` (502 lines)
  2. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/permit_service.rs` (399 lines)
  3. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs` (714 lines)
  4. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs` (1405 lines)
  5. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strict_upgrades.rs` (50 lines)
  6. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strong_sync.rs` (66 lines)
  7. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/dual_auth.rs` (234 lines)
  8. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_tests.rs` (263 lines)
  9. `/home/loidinh/WS/glean-hub/docs/tabbyrush-strict-sync.md` (115 lines)
  10. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs` (660 lines)
  11. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/strong_sync.rs` (319 lines)
  12. `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/strong_sync_v2.rs` (187 lines)
- **Lines of code analyzed**: ~4,914 lines across Rust backend, integration scripts, and protocol documentation.
- **Review focus**: Security & authorization, admission fencing, distributed concurrency & crash safety, data-preserving in-place transform, legacy reconciliation, and YAGNI/KISS/DRY compliance.
- **Updated plans**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-03-hub-versioned-contract-and-migration.md`.

---

## Overall Assessment
### Cycle 1 Assessment (Baseline: 7.0/10)
Initial review identified 1 Critical release blocker (crash resumption blocked by purity preflight) and 4 Warnings (unbounded permit growth, schema publication durability, missing legacy receipt protocol validation, and monolithic uncheckpointed transform).

### Cycle 2 Assessment (Post-Remediation: 7.8/10)
Code remediations have been inspected and evaluated. While key blockers have been substantially addressed, **production admission remains ON HOLD**:
1. **CRIT-01 (PARTIALLY MITIGATED)**: `StrictUpgradeService::execute_upgrade` now detects in-progress active migrations via `is_resuming` (`Transforming`, `Validating`, or `Publishing`) and bypasses `preflight_check`. However, resumption exhibits **accounting drift**: `tombstones_preserved` is reset to 0 in the response instead of loading from the existing migration record, and `records_transformed` records only the retry slice rather than cumulative totals. Automated crash-interruption regression tests remain unverified.
2. **WARN-01 (OPEN / PARTIALLY MITIGATED)**: `PermitService::acquire_permit` checks `active_permits.len() >= MAX_CONCURRENT_PERMITS` (1,000), but this evaluates against a local `find_one` snapshot. The subsequent `$set` update filter does not enforce capacity atomically. Under concurrent load spikes, concurrent requests can all pass the snapshot check and breach the 1,000 permit ceiling.
3. **WARN-02 (RESOLVED AT SOURCE LEVEL)**: Publication to `_schemas` routes through `get_strict_collection("_schemas")`, enforcing journaled write concern with replica set majority acknowledgment (`w: Majority`, `journal: true`).
4. **WARN-03 (RESOLVED AT SOURCE LEVEL)**: `StrongSyncService::legacy_receipts_inner` explicitly validates `request.protocol == PROTOCOL_V1`, rejecting non-v1 requests with `ProtocolUnsupported`.
5. **WARN-04 (PARTIALLY MITIGATED)**: `transform_collection_in_place` transforms in chunks of `BATCH_SIZE = 500` sorted by `rowId`, selectively matching untransformed records (`data.deadlineDate: { $exists: false }`) and incrementally persisting `checkpointRowId` and `recordsTransformed`. However, cross-restart cumulative count persistence and interruption regression coverage remain incomplete.

### Cycle 3 Final Assessment (Post-Atomic CAS & Accounting Remediations: 8.0/10)
Code remediations for atomic permit count CAS and cumulative resumption accounting have been verified. Key progress:
1. **Atomic Permit Count CAS Implemented**: `PermitService::acquire_permit` now enforces `permitCount: { $lt: 1000 }` in the atomic CAS `update_one` filter alongside `fenced: false`, and increments `permitCount: 1` atomically. `release_permit` decrements `permitCount: -1`.
2. **Cumulative Resumption Accounting Preserved**: `StrictUpgradeService::execute_upgrade` now loads `prior_transformed_count` from `existing_mig.recordsTransformed` and `preflight_tombstone_count` from `existing_mig.tombstonesPreserved`. `transform_collection_in_place` receives `initial_count` and maintains cumulative counts across restarts, eliminating the accounting reset defect.
3. **Material Finding - Permit Counter Corruption on Release**: `PermitService::release_permit` filters solely by `_id: &doc_id`. Releasing an already-released or invalid permit executes `$inc: { permitCount: -1 }` unconditionally, causing counter drift downwards and allowing future admissions to exceed the 1,000 ceiling. Requires `format!("activePermits.{}", permit.permit_id): { "$exists": true }` in the release filter.
4. **Material Finding - Legacy Coordination Docs Missing Counter**: `permitCount` is initialized solely via `$setOnInsert`. Pre-existing coordination documents without `permitCount` do not evaluate correctly against numeric `$lt` predicates, requiring explicit backfill migration.
5. **Material Finding - Non-Atomic Batch Mutation vs Checkpoint Persistence**: In `transform_collection_in_place`, `coll.update_many` and `migrations_coll.update_one` are separate writes, with checkpoint write errors ignored (`let _ = ...`). A crash in this window permanently undercounts transformed rows.
6. **Gate G3 Multi-Instance Live Mongo Gate (MANDATORY RELEASE GATE)**: Still unexecuted in live distributed replica set environment.

**Production Status**: **ADMISSION ON HOLD (NOT APPROVED FOR PRODUCTION — Score: 8.0 / 10)**.
---

## Critical Issues (Release Blockers)

### CRIT-01: False-Positive Preflight Crash-Resumption Blocker
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:298-301, 593-605`
- **Impact**: If a node or runner crashes mid-transformation, retrying the upgrade with the same `migration_id` to resume progress unconditionally fails with `PreconditionFailed`. The app remains permanently fenced (`fenced: true`), halting all reader/writer traffic and requiring manual operator MongoDB document surgery.
- **Mechanism**:
  ```rust
  // In execute_upgrade:
  // 1. Preflight Inspection runs unconditionally before checking active migration state
  let (preflight_live_count, preflight_tombstone_count) =
      self.preflight_check(&target_coll, app_id, &req.migration_id).await?;
  ```
  `preflight_check` checks for unexpected v2 fields on live documents:
  ```rust
  for f in &v2_field_names {
      if data.contains_key(*f) {
          return Err(StrictSyncError::PreconditionFailed(format!(
              "Preflight failed: record '{}' on app '{}' already contains unexpected v2 field '{}'",
              doc.get_str("_id").unwrap_or(""), app_id, f
          )));
      }
  }
  ```
  Because the previous crashed run already set `deadlineDate` on some or all live records, this check triggers immediately on retry.
- **Remediation**:
  Distinguish initial upgrade admission from resuming an existing migration owned by the same `migration_id`. When resuming from `Transforming`, `Validating`, or `Publishing`:
  1. Verify fence ownership, fence generation, and migration record status.
  2. Skip the v1 purity preflight check or scope it exclusively to unprocessed records.
  3. Resume the transform from `checkpoint_row_id` or re-scan with the idempotent `$exists: false` filter.

---

## High Priority Findings

### HIGH-01: Bounded Permit Limit (`MAX_CONCURRENT_PERMITS`) Unenforced
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/permit_service.rs:12, 111-146`
- **Impact**: Violates architectural requirement: *"Bounded admission prevents coordination document growth."* Under sustained request load or connection spikes, `activePermits` map in MongoDB document grows unbounded, risking BSON document 16 MiB overflow and excessive memory footprint during polling.
- **Mechanism**: `pub const MAX_CONCURRENT_PERMITS: usize = 1000;` is defined at line 12 but never referenced anywhere in `permit_service.rs`. `acquire_permit` unconditionally executes `$set: { format!("activePermits.{}", permit_id): ... }`.
- **Remediation**:
  Enforce the bound atomically with permit insertion. Either:
  1. Maintain an atomic counter `permitCount` in the coordination document and include `permitCount: { $lt: 1000 }` in the CAS update filter along with `$inc: { permitCount: 1 }`, or
  2. Maintain a bounded array with `$push` + slice or reject admission when capacity is reached.

### HIGH-02: `_schemas` CAS Publication Durability Risk
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:471-488`
- **Impact**: In a MongoDB replica set with primary failover, publishing the target schema to `_schemas` with default client write concern could result in unacknowledged write rollback after the fence has already been released (`release_fence`), leaving live data transformed to v2 while `_schemas` reverts to v1.
- **Mechanism**: While `get_coordination_collection` and `get_strict_collection` explicitly build collection handles with majority write concern and journal acknowledgment, `self.db.collection::<Document>("_schemas")` relies on unverified inherited defaults.
- **Remediation**:
  Explicitly apply collection options with `WriteConcern::builder().journal(true).w(Majority).build()` to the `_schemas` collection before performing the CAS publication update.

### HIGH-03: `legacy_receipts` Missing Protocol Validation
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs:968-993`
- **Impact**: Clients sending invalid, unsupported, or mismatched protocol strings (e.g., `"strict-v2"`, `"arbitrary-string"`) to `POST /sync/:app_id/strong/legacy-receipts` have their input protocol ignored. The handler unconditionally executes legacy v1 SHA-256 digest recomputation while blindly echoing back whatever protocol string was submitted.
- **Mechanism**:
  ```rust
  let (active_epoch, collection_name, _) = self
      .authorize_strict_access(app_id, Some(&request.epoch))
      .await?;
  // active_sync_mode discarded; request.protocol not checked to equal "strict-v1"
  ```
- **Remediation**:
  Explicitly validate that `request.protocol == PROTOCOL_V1`. Reject non-v1 requests with `StrictSyncError::ProtocolUnsupported`. Document that `legacy-receipts` is strictly a v1-to-v2 reconciliation bridge.

---

## Medium Priority Improvements

### MED-01: Monolithic Collection Update Lacks Checkpoint Resumption
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:20, 613-642`
- **Impact**: `BATCH_SIZE = 500` and `checkpoint_row_id` exist on `MigrationRecord`, but `transform_collection_in_place` executes a single unpaginated `coll.update_many(filter, update)`. On large collections (100k+ records), unbatched updates risk query timeouts, large WiredTiger cache pinning, and lack progressive checkpoint persistence.
- **Remediation**: Chunk the update into batches of `BATCH_SIZE` sorted by `_id`, updating `checkpoint_row_id` on the `_strict_migrations` document after each batch.

### MED-02: Redundant Topology `hello` Query on Every Sync Operation
- **Location**: `glean-hub-plugin/glean-hub-sync/src/services/permit_service.rs:36-40`, `strong_sync_service.rs:46-50`, `strict_upgrade_service.rs:51-55`
- **Impact**: Every call to `acquire_permit`, `release_permit`, and `push/pull` executes `db.run_command(doc! { "hello": 1 })`. This adds 2 to 3 synchronous database network roundtrips per HTTP sync request.
- **Remediation**: Cache topology status (replica set vs standalone) on service initialization or rely on MongoDB driver topology management.

### MED-03: DRY Violation in Topology Inspection Logic
- **Location**: Exact identical 40-line `get_strict_collection` / `get_coordination_collection` functions duplicated across three services (`PermitService`, `StrictUpgradeService`, `StrongSyncService`).
- **Remediation**: Extract collection helper into a shared database utility function within `glean-hub-sync`.

---

## Low Priority Suggestions
- **LOW-01: Schema Definition Duplication**: Column expectations in `AppService::validate_strict_v2_schema` (`app_service.rs:199-216`) duplicate the list from `models::canonical_v2_columns()`. Iterate `canonical_v2_columns()` directly to avoid drift.
- **LOW-02: Stale Read Permit Cleanup**: Read permits from aborted client pulls remain in `activePermits` until migration fence drain times out. Consider adding an operator permit purge utility or tracking read vs write permit intents.

---

## Positive Observations
1. **Mathematical Invariant Rigor**: `StrictV2TodoData::validate` strictly enforces checked 64-bit integer arithmetic (`checked_mul`, `checked_sub`, `checked_add`), preventing overflow wrapping vulnerabilities in interval/lead time calculations.
2. **Deterministic Digest Alignment**: Preimages match frozen test fixtures bit-for-bit across Rust and Kotlin suites. Delete operations enforce 0-byte payload data invariant.
3. **App-Scoped Authorization**: `app_scoped_admin_middleware` prevents global admins from upgrading or reading schema metadata for apps they are not explicitly granted access to.
4. **Idempotent Migration Re-entry**: Migration record validation prevents conflicting parallel migrations, while allowing re-entrant status queries with identical parameters.
5. **Tombstone Preservation**: Live transform and validation passes strictly guarantee tombstones remain empty (`data: {}`) and immutable.

---

## Recommended Actions
1. **[P0] Fix Crash Resumption in `execute_upgrade`**:
   - Check `existing_mig` before executing `preflight_check`.
   - If resuming an active migration owned by the same `migration_id`, bypass v1 purity validation.
2. **[P0] Enforce `MAX_CONCURRENT_PERMITS`**:
   - Add atomic `permitCount` check in `PermitService::acquire_permit`.
3. **[P0] Fix Protocol Validation in `legacy_receipts`**:
   - Reject any request where `request.protocol != "strict-v1"`.
4. **[P1] Secure `_schemas` Publication Write Concern**:
   - Build collection with `WriteConcern::builder().journal(true).w(Majority).build()`.
5. **[P1] Gate G3 Live Cluster Test Execution**:
   - Provision live two-node Glean Hub + MongoDB replica set to validate concurrent fence admission races and failure injection.

---

## Validation Metrics & Test Results

| Command / Check | Environment / Directory | Scope | Executed | Passed | Failed | Status |
|---|---|---|---|---|---|---|
| `cargo test --locked` | `glean-hub-plugin/glean-hub-sync` | Unit, integration & route tests | 55 | 54 | 0 (1 ign) | **PASS** |
| `cargo clippy --locked --all-targets` | `glean-hub-plugin/glean-hub-sync` | Static compiler analysis | N/A | 0 warnings | 0 | **PASS** |
| `node scripts/strict-v2-vector-runner.mjs` | `tabbyrush` | 10 valid + 11 reject + 2 fingerprint checks | 23 | 23 | 0 | **PASS** |
| `node scripts/hub-live-smoke.mjs --fixture-test` | `tabbyrush` | Offline fixture protocol isolation smoke | 18 | 18 | 0 | **PASS** |
| `./gradlew :shared:jvmTest --rerun-tasks` | `tabbyrush` | Shared KMP JVM test suites | 116 | 116 | 0 | **PASS** |
| `node scripts/release-security-check.mjs --diagnose-source` | `tabbyrush` | Android static manifest & security audit | 6 | 6 | 0 | **PASS** |
| **Gate G3 Multi-Instance Live Mongo Gate** | Distributed Topology | Two-node admission fence race & crash recovery | 0 | 0 | 0 | **UNVERIFIED** |

*Note: 1 ignored test in `glean-hub-sync` (`test_strong_sync_live_mongo_acceptance_gate`) explicitly requires live MongoDB via `MONGO_TEST_URI`.*

---

## Unresolved Questions
1. How will operator runbooks handle orphan permits from crashed client requests when triggering a migration fence without manual MongoDB edits?
2. Does the staging environment provision a secondary Glean Hub instance to automate two-node admission fence race tests against a shared MongoDB replica set in CI?
3. When will Gradle dependency checksum metadata be updated to eliminate the `--dependency-verification=off` flag requirement during CI builds?

---

## Cycle 2 Re-Review: Remediation Verification Details

### 1. CRIT-01: False-Positive Preflight Crash-Resumption Blocker — PARTIALLY MITIGATED
- **File**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:298-330, 558-560, 638-642`
- **Verification**:
  - `execute_upgrade` queries `existing_mig` before preflight. `is_resuming` evaluates to `true` when migration state is `Transforming`, `Validating`, or `Publishing`.
  - When `is_resuming` is `true`, `self.preflight_check` is bypassed, preventing spurious `PreconditionFailed` errors on partially transformed live records.
  - `transform_collection_in_place` filters on `"data.deadlineDate": { "$exists": false }`, safely selecting only untransformed records.
  - Calling dryRun on an in-progress migration is explicitly rejected with `StrictSyncError::Conflict`.
- **Remaining Deficiencies**:
  - **Tombstone Accounting Loss**: Resuming an existing migration sets `preflight_tombstone_count = 0` (lines 329, 559) instead of preserving `record.tombstones_preserved`.
  - **Cumulative Transformed Count Loss**: On retry, `transform_collection_in_place` initializes `total_transformed = 0`, overwriting `recordsTransformed` with only the count of rows modified during the resumed attempt.
  - **Missing Interruption Tests**: No automated regression tests exist for mid-transformation crash, crash during validation, or crash between CAS publication and fence release.

### 2. WARN-01: Bounded Permit Limit Enforcement — OPEN / NON-ATOMIC CHECK
- **File**: `glean-hub-plugin/glean-hub-sync/src/services/permit_service.rs:145-165`
- **Verification**:
  - `acquire_permit` inspects `active_permits.len()`. If `>= MAX_CONCURRENT_PERMITS` (1,000), it returns `Err(StrictSyncError::IndeterminateStorage)`.
- **Remaining Deficiencies**:
  - **Check-Then-Write Concurrency Race**: Capacity is checked against an in-memory document snapshot from `find_one`. The subsequent MongoDB update filter is simply `{ "_id": &doc_id, "fenced": false }`. Under concurrent bursts, multiple parallel workers can read `len < 1000` simultaneously and then all execute `$set`, allowing active permits to significantly overshoot 1,000.
  - **Remediation Needed**: Enforce the permit bound atomically in the MongoDB update filter/pipeline (e.g. maintaining an atomic `permitCount: { $lt: 1000 }` field with `$inc: { permitCount: 1 }`, or using an aggregation pipeline update).

### 3. WARN-02: `_schemas` CAS Publication Write Concern — RESOLVED AT SOURCE LEVEL
- **File**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:487-504`
- **Verification**:
  - Schema publication collection handle is obtained via `self.get_strict_collection("_schemas").await?`.
  - `get_strict_collection` verifies topology via `hello` and configures `WriteConcern::builder().journal(true).w(Majority).build()` with `ReadConcern::majority()`.
  - CAS publication update requires affirmative primary and majority journaled persistence before fence release.

### 4. WARN-03: `legacy_receipts` Protocol Validation — RESOLVED AT SOURCE LEVEL
- **File**: `glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs:974-979`
- **Verification**:
  - At entry of `legacy_receipts_inner`, `request.protocol` is validated against `PROTOCOL_V1`.
  - Non-v1 protocols immediately return `Err(StrictSyncError::ProtocolUnsupported)`.
- **Remaining Work**:
  - Existing test `test_legacy_receipts_request_parsing` covers JSON deserialization; add a behavioral test exercising non-v1 rejection through the service/handler.

### 5. WARN-04: Batch-wise Transformation with Incremental Checkpoints — PARTIALLY MITIGATED
- **File**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:630-712`
- **Verification**:
  - `transform_collection_in_place` queries untransformed rows (`data.deadlineDate: { $exists: false }`) in batches limited to `BATCH_SIZE = 500`, ordered by `rowId`.
  - Incrementally updates the `_strict_migrations` document with `checkpointRowId` and `updatedAt`.
- **Remaining Deficiencies**:
  - Cross-restart cumulative count drift (as noted in CRIT-01).
  - Interruption between batch row update and checkpoint persistence has no automated recovery test.

---

## Cycle 2 Validation Summary

| Suite / Command | Environment | Result | Details |
|---|---|---|---|
| `cargo test --locked` | `glean-hub-sync` | **PASS** | 54 passed, 0 failed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`) |
| `cargo clippy --locked --all-targets` | `glean-hub-sync` | **PASS** | 0 warnings, clean compilation |
| `node scripts/strict-v2-vector-runner.mjs` | `tabbyrush` | **PASS** | 23/23 vectors & fingerprints verified |
| `node scripts/hub-live-smoke.mjs --fixture-test` | `tabbyrush` | **PASS (FIXTURE ONLY)** | 18/18 mock fixture checks passed (does not exercise Rust backend or MongoDB) |
| `node scripts/release-security-check.mjs --diagnose-source` | `tabbyrush` | **PASS** | 6/6 static manifest and security checks passed |
| `./gradlew :shared:jvmTest --rerun-tasks` | `tabbyrush` | **BLOCKED** | Failed at Gradle startup: JDK 25 (`25.0.4.1`) incompatible with Kotlin CLI `JavaVersion.parse`; requires JDK 21 LTS |
| **Gate G3 Multi-Instance Live Mongo Gate** | Cluster Topology | **MANDATORY / UNVERIFIED** | Live two-node replica set race, failover, and crash tests unexecuted |

---

## Cycle 2 Recommended Actions
1. **Fix Concurrency Race in `PermitService::acquire_permit` (P0)**:
   - Maintain an atomic integer `permitCount` in the coordination document.
   - Include `"permitCount": { "$lt": MAX_CONCURRENT_PERMITS as i64 }` directly in the `update_one` CAS filter and `$inc: { "permitCount": 1 }`.
2. **Fix Resumption Accounting Drift in `StrictUpgradeService::execute_upgrade` (P0)**:
   - When `is_resuming == true`, initialize `tombstones_preserved` from `existing_mig.tombstones_preserved`.
   - In `transform_collection_in_place`, initialize `total_transformed` from `existing_mig.records_transformed` so that cumulative counts are preserved across crashes.
3. **Provision JDK 21 LTS for Client JVM Test Execution (P1)**:
   - Replace OpenJDK 25 (`25.0.4.1`) with OpenJDK 21 LTS in workstation environment to unblock `./gradlew :shared:jvmTest`.
4. **Execute Gate G3 Distributed Topology Acceptance (P0 Release Gate)**:
   - Provision two-node Glean Hub cluster against shared MongoDB replica set (`MONGO_TEST_URI`) to validate distributed admission races, CAS schema publication failover, and crash recovery.

---

## Cycle 2 Sign-off Status
**ADMISSION ON HOLD (Score: 7.8 / 10)**  
Production admission cannot be signed off until permit atomicity, resumption accounting, and Gate G3 live cluster tests are completed.

---

## Cycle 3 Final Review: Verification Details

### 1. Atomic Permit Count CAS & Bound Enforcement (`permit_service.rs`)
- **Files**: `glean-hub-plugin/glean-hub-sync/src/services/permit_service.rs:98, 154-168, 195-207`, `src/models/strict_upgrade.rs:361-362`
- **Verification**:
  - `ensure_coordination_doc` initializes `"permitCount": 0i64` in `$setOnInsert`.
  - `AppCoordinationDoc` includes `pub permit_count: i64` with `#[serde(default)]` and `camelCase` rename.
  - `acquire_permit` executes atomic CAS `update_one` with filter:
    ```rust
    let filter = doc! {
        "_id": &doc_id,
        "fenced": false,
        "permitCount": { "$lt": MAX_CONCURRENT_PERMITS as i64 },
    };
    ```
    and atomic update:
    ```rust
    let update = doc! {
        "$set": {
            format!("activePermits.{}", permit_id): permit_entry,
            "updatedAt": now,
        },
        "$inc": {
            "permitCount": 1i64,
        }
    };
    ```
  - `release_permit` decrements `"permitCount"` by -1 via `$inc: { "permitCount": -1i64 }`.
- **Material Residual Defect**:
  - `release_permit` filter is only `doc! { "_id": &doc_id }`. If a permit is released more than once or with an unknown permit ID, `$inc: -1` still fires, corrupting the counter downwards. Fix required: add `format!("activePermits.{}", permit.permit_id): { "$exists": true }` to the filter.
  - Error disambiguation in `acquire_permit`: when `matched_count == 0`, if re-read reveals `fenced == false` and `permitCount >= 1000`, returning `MigrationInProgress` misleadingly attributes capacity rejections to fencing.

### 2. Cumulative Resumption Accounting Preservation (`strict_upgrade_service.rs`)
- **Files**: `glean-hub-plugin/glean-hub-sync/src/services/strict_upgrade_service.rs:298-338, 423-428, 637-646, 696-712`
- **Verification**:
  - Resumption detection (`is_resuming`) correctly identifies `Transforming`, `Validating`, or `Publishing` states and bypasses `preflight_check`.
  - `existing_mig` records are read to extract `prior_trans = recordsTransformed` and `prior_tombs = tombstonesPreserved`.
  - `transform_collection_in_place` receives `prior_transformed_count` as `initial_count` (`let mut total_transformed = initial_count;`).
  - Bounded batch updates (`BATCH_SIZE = 500`) increment `total_transformed += res.modified_count` and checkpoint `recordsTransformed` to `_strict_migrations`.
  - Final `StrictUpgradeResponse` and `_strict_migrations` record preserve both cumulative `records_transformed` and `preflight_tombstone_count` (`prior_tombs`).
- **Material Residual Defect**:
  - Checkpoint update error handling: `let _ = migrations_coll.update_one(...).await;` discards database errors during checkpointing. If node crashes after `coll.update_many` but before checkpoint persistence, rows already stamped with `deadlineDate` will not be re-scanned, causing permanent undercount of `recordsTransformed`.

---

## Cycle 3 Final Validation Summary

| Suite / Command | Environment / Scope | Result | Details |
|---|---|---|---|
| `cargo test --locked` | `glean-hub-sync` unit & integration | **PASS** | 54 passed, 0 failed, 1 ignored (`test_strong_sync_live_mongo_acceptance_gate`) |
| `cargo clippy --locked --all-targets` | `glean-hub-sync` static analysis | **PASS** | Clean compilation, 0 warnings |
| `node scripts/strict-v2-vector-runner.mjs` | `tabbyrush` strict-v2 protocol vectors | **PASS** | 23/23 vectors (10 valid, 11 reject, 2 fingerprints) |
| `node scripts/hub-live-smoke.mjs --fixture-test` | `tabbyrush` offline mock fixture | **PASS (FIXTURE ONLY)** | 18/18 checks passed; does not exercise live Rust backend or MongoDB |
| `node scripts/release-security-check.mjs --diagnose-source` | `tabbyrush` release source security audit | **PASS** | 6/6 static manifest and security checks passed |
| `./gradlew :shared:jvmTest --rerun-tasks` | `tabbyrush` shared KMP JVM test suite | **BLOCKED ON WORKSTATION** | Workstation default JDK 25 (`25.0.4.1`) incompatible with Kotlin CLI; requires JDK 21 LTS |
| **Gate G3 Multi-Instance Live Mongo Gate** | Distributed 2-node cluster + replica set | **MANDATORY / UNVERIFIED** | Live multi-instance race, failover CAS, and crash injection unexecuted |

---

## Cycle 3 Recommended Actions
1. **[P0] Guard `release_permit` Against Counter Corruption**:
   Add `format!("activePermits.{}", permit.permit_id): { "$exists": true }` to `release_permit` filter to make releases strictly idempotent.
2. **[P0] Deploy Coordination Document Counter Migration**:
   Provide a migration script or runtime backfill to ensure existing `_coordination` documents have `permitCount` accurately populated from `Object.keys(activePermits).length`.
3. **[P0] Execute Gate G3 Multi-Instance Live Mongo Gate**:
   Spin up 2-node Glean Hub cluster against shared MongoDB replica set to validate concurrent permit races, fence transitions, and failure injection under live load.
4. **[P1] Harden Batch Checkpoint Persistence**:
   Propagate errors from checkpoint persistence in `transform_collection_in_place` rather than silently discarding with `let _ = ...`.
5. **[P1] Disambiguate CAS Rejection in `acquire_permit`**:
   Differentiate between `fenced == true` and `permitCount >= MAX_CONCURRENT_PERMITS` when returning errors from failed CAS.

---

## Cycle 3 Final Sign-off Status

**FINAL EVALUATION SCORE: 8.0 / 10**  
**PRODUCTION ADMISSION: ON HOLD (NOT APPROVED)**  
*Gate G3 distributed cluster acceptance and permit release counter idempotency guard remain prerequisite release blockers.*
