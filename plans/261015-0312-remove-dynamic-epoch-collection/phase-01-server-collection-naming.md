# Phase 01: Backend Collection Resolver & Admission in Glean Hub

**Context Links**
- Glean Hub Server: `/home/loidinh/WS/glean-hub`
- Source files: `glean-hub-plugin/glean-hub-sync/src/services/app_service.rs`, `glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs`, `glean-hub-plugin/glean-hub-sync/src/services/strong_sync_tests.rs`

---

## Overview
- **Priority:** High
- **Current Status:** Completed
- **Description:** Update `AppService::strict_collection_name` to return `format!("app_{}", app_id)` instead of `_strict_v1_<len>_<appId>_<epochNoHyphens>`. Update `register_strict_app` to be idempotent with respect to collection creation, ensure scan index `{ userId: 1, tableName: 1, rowId: 1 }` exists, and preflight validate against `app_<appId>`.

---

## Requirements
1. `AppService::strict_collection_name(app_id, epoch)` returns `format!("app_{}", app_id)`.
2. `register_strict_app` checks whether target collection already exists before calling `create_collection`, creates the compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`, and verifies admission against `app_<appId>`.
3. `validate_strict_admission` validates collection `app_<appId>`: must exist, uncapped, no custom validator, simple collation, scan index present, no TTL indexes. Reconciles `is_writable` topology check to handle both `isWritablePrimary` and `ismaster`.
4. Unit tests in `strong_sync_tests.rs` pass with the updated collection naming.

---

## Related Code Files
- Modify: `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs`
- Modify: `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_tests.rs`
- Modify: `/home/loidinh/WS/glean-hub/docs/tabbyrush-strict-sync.md`
- Modify: `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/AGENTS.md`

---

## Implementation Steps
1. In `app_service.rs`, modify `strict_collection_name` to return `format!("app_{}", app_id)`.
2. In `app_service.rs`, update `register_strict_app` to check if `coll_name` is in `db.list_collection_names()`; if not, create it.
3. In `app_service.rs`, ensure `validate_strict_admission` topology check matches `get_strict_collection` (`is_writable`).
4. In `strong_sync_tests.rs`, update `test_strict_collection_naming` to assert `app_tabbyrush`.
5. Update docs in `glean-hub/docs/tabbyrush-strict-sync.md` and `glean-hub/glean-hub-plugin/glean-hub-sync/AGENTS.md`.
6. Run `cargo test -p glean-hub-sync` and `cargo check --workspace` in `glean-hub`.

---

## Todo List
- [x] Update `strict_collection_name` in `app_service.rs`
- [x] Update `register_strict_app` collection creation idempotency
- [x] Update topology check in `validate_strict_admission`
- [x] Update unit test `test_strict_collection_naming`
- [x] Update `glean-hub` docs
- [x] Verify `cargo test -p glean-hub-sync` passes 100%

---

## Success Criteria
- Collection resolves to `app_<app_id>`.
- Admission validates target collection.
- `cargo test -p glean-hub-sync` all unit tests green.
