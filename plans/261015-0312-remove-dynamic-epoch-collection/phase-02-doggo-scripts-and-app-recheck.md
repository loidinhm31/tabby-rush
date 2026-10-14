# Phase 02: Doggo Recovery Tooling & App Recheck

**Context Links**
- Doggo workspace: `/home/loidinh/WS/doggo`
- Files: `scripts/hub-strict-recovery.js`, `shared/src/*`, `androidApp/src/*`

---

## Overview
- **Priority:** High
- **Current Status:** Completed
- **Description:** Update Doggo's disaster recovery script `scripts/hub-strict-recovery.js` to target the fixed collection `app_${CONFIG.appId}` instead of dynamic `_strict_v1_*`. Recheck Doggo shared KMP and Android client modules to verify zero dependencies on MongoDB collection names, and execute full test suites.

---

## Requirements
1. `scripts/hub-strict-recovery.js` allocates the target collection name as `app_${CONFIG.appId}`.
2. Source backup collection guard in `hub-strict-recovery.js` ensures `SOURCE_BACKUP_COLLECTION` is not the target collection itself, not `_schemas`, and not `system.*`.
3. Doggo app code in `shared/` and `androidApp/` verified to consume only HTTP REST strict-v1 protocol contracts with zero collection name leaks.
4. Execute `hub-live-smoke.mjs --fixture-test` (18/18 checks pass).
5. Execute shared JVM tests (`:shared:jvmTest`, 96/96 tests pass).

---

## Related Code Files
- Modify: `scripts/hub-strict-recovery.js`
- Verify: `shared/src/commonMain/kotlin/dev/doggo/shared/sync/hub-sync-client.kt`
- Verify: `scripts/hub-live-smoke.mjs`

---

## Implementation Steps
1. In `scripts/hub-strict-recovery.js`:
   - Change `targetCollectionName` calculation to `'app_' + CONFIG.appId`.
   - Update prerequisite validation so `sourceCollection !== targetCollectionName`.
   - Update header comments and logs reflecting `app_<appId>`.
2. Verify syntax and dry-run: `node --check scripts/hub-strict-recovery.js`.
3. Verify fixture smoke runner: `node scripts/hub-live-smoke.mjs --fixture-test`.
4. Verify shared JVM tests: `./gradlew :shared:jvmTest`.

---

## Todo List
- [x] Update `targetCollectionName` in `scripts/hub-strict-recovery.js`
- [x] Update collision and prerequisite checks in `scripts/hub-strict-recovery.js`
- [x] Verify `node --check scripts/hub-strict-recovery.js`
- [x] Run `hub-live-smoke.mjs --fixture-test`
- [x] Run shared JVM tests `:shared:jvmTest`

---

## Success Criteria
- Recovery script provisions and targets `app_doggo`.
- All client tests and smoke suites pass 100%.
