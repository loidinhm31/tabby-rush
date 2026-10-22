# Desktop contract compilation and shutdown verification

## Result

Requested command completed successfully after final source changes, with no exception-message suppression in transport:

```text
cmd.exe /c "gradlew.bat :desktopApp:test -PdesktopTarget=windows"
BUILD SUCCESSFUL in 14s
20 actionable tasks: 6 executed, 14 up-to-date
```

All desktop test cases passed, including `testShutdownCancelsControllerSyncWaitingForCoordinatorAdmission`, Windows DPAPI, reminder owner-isolation, and sticky collaboration tests. Existing Kotlin/Gradle warnings remain; this is not a warning-free build.

## Root causes and changes

- Five assigned test/smoke files retained pre-cutover contracts: unqualified task identity, old reminder parameter types, `ACCESS_DENIED`/`REVOCATION` enum names, obsolete local mutation methods, and obsolete auth DTO/config/error properties.
- Migrated to current `TaskRef`, owner-aware task factory, `REVOKED`, `AssignmentIntent`, `saveTodoLocally`, `assignTaskLocally`, `appApiKey`, and `ApiResult.Failure.error` contracts. Smoke obtains account identity through `getMe` rather than nonexistent nested login user properties.
- Shared task fixtures incorrectly attempted local creation of another owner's tasks or children without available parents. They now stage authoritative task/access snapshots and promote reconciliation. Read-only completion asserts the actual `Missing` denial result and unchanged stored content.
- Controller tests attempted runtime creation without authenticated identity. They now restore matching credential-backed account sessions inside isolated account directories.
- Runtime testing uncovered shutdown leakage: controller `triggerSync` directly launched a coordinator round, then requested a second scheduler-managed round. Scheduler disposal could not drain the first launch; it could reach `StagedFullScan.clearStagedReconciliation` after the delivery gate closed. Main applied the source fix: route exclusively through `syncScheduler.triggerImmediate`.
- Added deterministic regression holding coordinator admission, requesting controller sync, disposing scheduler, then releasing admission. No escaped request may transition progress from `IDLE`. Original automatic-observer lifecycle fixture remains active on `Dispatchers.Default`.

## Scope and evidence boundary

Changed the five requested desktop test/smoke files and changelog; coordinated Main's `main.kt` source fix. No production transport exception suppression retained. Final exact-command run occurred after its removal.

Standalone disposable Hub smoke was compiled, not executed; live acceptance and production migration remain outside this assignment. No tests skipped or weakened to avoid the observed failures.

## Unresolved questions

None for desktop test compilation and requested suite execution.
