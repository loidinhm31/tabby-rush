# Shared JVM contract regression repair

## Result

Command: `cmd.exe /c "gradlew.bat :shared:jvmTest"`

Final execution: **BUILD SUCCESSFUL in 19s**, exit code 0. Parsed final JUnit XML: **324 tests, 0 failures, 0 errors, 0 skipped**. HTML report: `shared/build/reports/tests/jvmTest/index.html`.

## Initial compilation fixes

All eight assigned files updated:
- `remote-contract-vector-test.kt`: import `assertNotNull`.
- `todo-semantics-test.kt`: migrate eleven editor constructor arguments to nullable/canonical `taskRef`, preserving new-task null identity.
- `adversarial-sync-sqlite-test.kt`: explicit named Room v5 evidence fields; canonical conflict/copy references.
- `native-sync-safety-sqlite-test.kt`: canonical conflict references; invoke coordinator `copyAsNew` without weakening atomic latest-draft safety expectations.
- `reminder-scheduling-coordinator-sqlite-smoke-test.kt`, `task-surface-store-sqlite-test.kt`, `task-surface-sync-sqlite-test.kt`: missing `TaskRef` imports.
- `room-migration-to-v5-test.kt`: required JSON primitive accessor imports.

## Runtime root causes and repairs

The first executable suite exposed real migration regressions, not just missing imports.

### Persistence and conflict actions
- `room-account-store.kt`: private lock-held access lookup prevents public mutex reacquisition from save/delete/completion/assignment mutations.
- `account-sync-coordinator.kt`: reapply reads confirmed canonical rows under its existing mutation lock instead of reacquiring the store mutex. Copy delegates to the store's single-admission atomic helper.
- `room-account-store.kt` / `tabbyrush-dao.kt`: copy latest displayed projection/draft to a fresh actor-owned root; reject unavailable content; atomically create copy and retire represented source revisions by binding/table/row and revision ceiling. SQL-trigger rollback and reopen assertions retained.
- Restore explicit changed-field masks and semantic schedule-token rotation. Preserve last-child completion barrier and use existing pipe-delimited masks.
- Make confirmed tombstones dominate residual drafts/outbox overlays. Reject creation-time/parent-link mutation and grandchild creation using typed `InvalidStructure`; confirmed deletion returns typed `Missing`.
- Exact accepted tuples now advance the confirmed base from the frozen envelope, preserving newer edits and canonical draft isolation. Prefer canonical bound rows; only unresolved actor-private acknowledgments use the local-account binding.
- Schedule snapshots receive the explicit store user for unresolved private display instead of constructing blank-owner `TaskRef` values.

### Coordinated owner repairs
- Phase 01: `persisted-projection-inputs.kt` intent constants now match uppercase persisted masks; fixes silently ignored title/completion/reminder/preservation overlays.
- Phase 05: `staged-full-scan.kt` preserves actual terminal opaque checkpoint before promotion; updates its own access-generation fence after authoritative invalidation.
- Phase 05: `staged-full-scan-test.kt` test DAO removes exact-generation staging consistently. HTTP test bodies use actual `TextContent.text`; fixtures preserve checkpoint ordering and valid frozen envelopes in `hub-sync-client-test.kt`, `staged-full-scan-test.kt`, `collaboration-reconciliation-test.kt`, `outbox-reconciliation-test.kt`.
- Phase 06: `task-assignee-workflow-test.kt` child-only fixtures include real canonical parent references; assertions still require flat privacy-safe projection and full-group-only completion.

### Additional fixture migrations
- `canonical-task-access-test.kt`: timed deadline fixtures now satisfy documented `TaskDeadline` invariants; collision and revoked-access assertions unchanged.
- `room-migration-to-v5-test.kt`: populate required historical SQLite columns using the existing v4 fixture pattern; preserve NULL storage classes; assert the exact seeded legacy evidence identity and original JSON.
- `room-account-store-test.kt`: migrate obsolete exception expectations to explicit typed rejection, additionally asserting no resurrection/reparenting/new grandchild.
- Adversarial/native transport fixtures now implement collaboration response metadata, canonical owner envelopes and access-check responses. Session fixtures explicitly initialize owner binding. Interrupted scan coverage asserts that absence alone is not a tombstone, matching the frozen collaboration contract.
- Add real SQLite regressions for copy isolation when owners share the same rowId and refusal to copy unavailable content.

## Verification history and scope

Compilation repaired first. Initial runtime execution was cancelled on confirmed non-reentrant mutex deadlocks. Subsequent complete suite executions exposed 31, then 8, then 3 failures; all resolved. Final complete execution passed all 324 tests. No test was skipped or disabled.

Warnings remain: Kotlin expect/actual beta, existing constant-condition warnings in UI/test sources, Gradle 9 deprecation notice. No Android/device/Desktop interactive/provider acceptance or production operation performed.

Updated `docs/project-changelog.md` with fixes and observed JVM evidence.

## Unresolved questions

None for this assignment. Broader native acceptance and production authorization remain separate release gates.
