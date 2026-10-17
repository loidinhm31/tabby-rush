# Phase01 — shared contracts, authoritative store and build foundation

## Context links
[Overview](plan.md) · [analysis](reports/01-codebase-analysis.md) · [brief](reports/02-planner-brief.md) · [brainstorm](../reports/brainstorm-261016-1302-sticky-task-surfaces.md) · [Android research](research/researcher-01-android-surfaces.md) · [contracts](architecture-contracts.md) · [ownership](file-ownership.md#phase01--contracts-authoritative-store-and-build-foundation) · [acceptance](acceptance-matrix.md) · [dependencies](dependency-matrix.md).

## Parallelization Info
Group A, sole shared/build owner. Phase 01 is **delivered / 100% of planned scope (24h / 24h); technical acceptance conditional, dependency-provenance gate pending**. Cycle 3 code review scored **9.4/10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)** and authorizes terminal conditional handoff to Phases 02, 03, and 04. The server-tombstone/local-draft-or-outbox finding is resolved and regression-tested; strict dependency provenance remains pending, not waived.

## Overview
- Date: 2026-10-16. Priority: P2. Effort: **24h**.
- Implementation status: **delivered (100% of planned Phase 01 scope; 24h credited)**; terminal conditional handoff issued.
- Technical acceptance: **conditional; dependency-provenance gate pending**. Cycle 3 code review: **9.4/10 (Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending)**. QA reports **307/307 Gradle tests passing**; dependency verification was disabled for the Gradle runs.

## Key Insights
- AccountStore currently has snapshots plus reminder observation, not general task-list Flow or desired completion. Only RoomAccountStore implements it in observed sources; no interface doubles found.
- Existing mutations already flow through a replayed conflated invalidation and gate→mutex→transaction. Reuse it; sync/full-scan/conflict code calls `withMutationLock` already.
- Existing load/save both lock; fresh read+set must use private locked helpers to avoid recursive mutex. Save atomicity, strict-version guards and schedule-token logic are authoritative.
- Glance 1.2.0 is now the selected stable dependency and its Android compilation and locking are reported complete with Kotlin 2.1.10/Compose 1.7.3. Strict artifact provenance remains unproven: Cycle 2 review notes no new verification-metadata entries, while the repository has a pre-existing 149-checksum baseline hold.

## Requirements
1. Add `observeTodos()` and `setCompletedLocally(id, desired, now)` with exact contract/result in architecture-contracts§1–2; migrate every actual implementor/caller/double requiring new declarations.
2. Stable incomplete/completed partition retains createdAt DESC/id DESC and unfiltered state. Selector includes persisted conflict state, reminders-disabled deadlines, no unsaved editor drafts.
3. Completion atomically preserves concurrently changed title/temporal fields and FIFO/outbox invariants; duplicate desired state no-op; missing/tombstoned record no-op.
4. Freeze shared access/readiness/token/result signatures, Android host owner contract and desktop independent compact action/state contract before leaves. No fake implementation.
5. Select aligned stable Glance with SDK levels unchanged; own all listed locks/catalog/verification metadata. Authenticate new artifacts, preserve unrelated baseline hold.

## Architecture
Existing `withMutationLock` → private `loadTodosLocked`/fresh-ID projection → private `saveTodoLocallyLocked` → existing atomic DAO transaction → shared invalidation. Public load/save wrap helpers exactly once. `observeTodos` and existing schedule observer share invalidation but use different projectors. Shared selectors are pure functions; runtime admission remains platform integration responsibility. Timed/date-only ordering both use saved overdueEpochMs then taskId; equality is overdue. Reminder eligibility remains untouched.

## Related code files
**Exclusive changes:** complete exact paths/actions in linked phase01 ownership table, including account-store.kt, room-account-store.kt, proposed task-surface-contracts/projector, SQLite tests, Android host, desktop model, catalog/build/four locks/verification and evidence report. No deletions beyond obsolete in-file helper.
**Read-only:** todo-models.kt, persisted schedule contract/projector, TitlePolicy, existing delivery gate/leave protocol, sync reconciliation/full-scan/conflict/transition, shared build/DAO/schema. Source evidence paths are in analysis; do not invent current APIs.

## File Ownership
Phase01 alone owns every phase01 row in manifest, including shared tests and all target lockfiles even if Linux resolution occurs later. Shared/desktop lock rows are inspect/retain unless actually affected. No generated Room schema allowed. Any discovered AccountStore implementor/double must be concretely added to01 manifest before change. Later findings return to01; no ownership transfer to acceptance or Linux owner.

Explicit owned paths (repository-relative; actions/reasons in central manifest):
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-contracts.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-projector.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-surface-projector-test.kt`
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-store-sqlite-test.kt`
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-sync-sqlite-test.kt`
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-host.kt`
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/sticky-task-model.kt`
- `gradle/libs.versions.toml`
- `androidApp/build.gradle.kts`
- `gradle/verification-metadata.xml`
- `androidApp/gradle.lockfile`
- `shared/gradle.lockfile`
- `desktopApp/gradle-windows.lockfile`
- `desktopApp/gradle-linux.lockfile`
- `plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md`

## Implementation Steps
1. Re-read named source sections and targeted AccountStore references; confirm sole implementor and no missed test doubles. Freeze proposed declaration names/types, no overload aliases or placeholder runtime host.
2. Resolve aligned Glance1.2.0 candidate with current catalog/plugins; inspect Android runtime dependency graph and SDK compatibility. Authenticate provenance of newly added binaries/checksums. A candidate failure must select another compatible **stable** version with evidence and contract review, not downgrade feature scope or SDK.
3. Update catalog/Android build once for Glance and any truly needed test dependency. Resolve exact owned lock configurations on matching hosts; review metadata changes, retain unaffected locks. Baseline checksum failure is disclosed external gate, not arbitrary checksum trust or broad verification disable proof.
4. Extract private locked load/save helpers while preserving temporal/outbox behavior. Implement ID setter under one mutation admission+mutex; fresh lookup, missing/deleted/unchanged early result, monotonic updatedAt on actual change and one atomic persistence.
5. Generalize private invalidation naming, remove unused public schedule-named helper; keep successful mutation emission and cold replay. Implement distinct `observeTodos` over existing load path; no polling or new Flow source.
6. Add stable partition and persisted deadline selector with conflict flag retained. Include conflicted saved deadlines for display, reminder-independent; notification semantics exactly architecture§5.
7. Add shared access contracts, Android host contract and desktop compact state/actions as production interfaces/value types. No integration registration in01.
8. Author pure boundary tests and real SQLite tests. Coordinate actual concurrency for completion vs title/deadline edit, duplicate actions, tombstone/missing, persisted restart and outbox/revision preservation. Exercise actual reconciliation/full-scan promotion/conflict actions and cold replay without Activity.
9. Hand off frozen API/compatibility evidence with future commands in acceptance matrix. Main integrator schedules checks on coherent snapshot; no leaf can claim module runtime proof before real host integration.

## Todo list
- [x] Freeze API/semantic contracts and enumerate actual implementors.
- [x] Implement single-lock desired completion, observation and pure projection.
- [x] Author SQLite/concurrency/sync invalidation foundation tests.
- [x] Select stable Glance 1.2.0, resolve Android dependency locks, and verify compilation.
- [x] Resolve the server-tombstone/local-overlay finding with residual-draft and residual-outbox SQLite regressions.
- [x] Record Cycle 3 review, full-suite QA evidence, and terminal conditional handoff.
- [x] Deliver phase evidence and terminal handoff to Phases 02, 03, and 04.

### Open technical acceptance follow-ups
- [x] Resolve the Cycle 2 server-tombstone/local-draft-or-outbox overlay finding: `setCompletedLocally` returns `Missing` without throwing for authoritative tombstones; both residual-draft and residual-outbox regressions pass (`testSetCompletedLocallyTombstoneWithResidualDraftReturnsMissingWithoutException` and `testSetCompletedLocallyTombstoneWithResidualOutboxReturnsMissingWithoutException`).
- [ ] Establish trusted provenance evidence for the newly introduced Glance artifacts. Gradle runs used `--dependency-verification=off`; the review reports no Glance entries in verification metadata and the pre-existing 149-checksum baseline hold.

### Cycle 3 review recommendations (non-gating)
- Bound initial observer-test awaits and ensure collector cleanup; the reviewer also recommends removing a redundant server-base lookup and aligning one test name with its sequential setup. These are not leaf-handoff blockers.

## Success Criteria

**Reported verification:** [Full-suite QA](../reports/qa-261016-1911-phase-01-full-suite-validation.md) records 307/307 Gradle tests passing: shared JVM 231, desktop 64, Android unit 12, with 0 failures and 0 skips. Separately, it records 18/18 fixture smoke checks, 6/6 source security diagnostics, and 8/8 strict-v2 cutover self-test checks. [Cycle 3 review](../reports/code-review-261016-1911-phase-01-shared-foundation-cycle-3.md) scores 9.4/10 and authorizes terminal conditional handoff; it confirms the tombstone-overlay remediation and its two SQLite regressions. Gradle verification was disabled due to the existing 149-checksum baseline hold; neither strict dependency verification nor leaf native acceptance is established.

## Conflict Prevention
This tracking update changes plan/dependency/roadmap/changelog/report documentation only. No production sources, manifests, Android roots, desktop controllers, or module leaf files are changed. Existing ownership and change-control remain in force: source remediation stays with its owner in the ownership manifest, and signature changes require a reviewed plan/contract amendment before consumers start.

## Risk Assessment
- Recursive mutex/lock inversion: private locked helpers and actual concurrency tests.
- Invalidation coalescing: test final snapshot and initial replay, not event count.
- Metadata expands unexpectedly: exact target review; Linux-host-only metadata resolution remains01, never host-spoof.
- Toolchain/checksum baseline blocks proof: keep pending gate explicit, do not publish fake compile success.

## Security Considerations
No DAO mutation bypass; store frozen/generation checks remain platform+gate boundaries. Shared access never exposes credentials in failures; title display intentionally native widget/private notification. No task text in new preference/schema. Preserve reminder delivery predicate and existing leave sequence.

## Next steps

The Cycle 3 terminal conditional handoff unblocks Phases 02/03/04 against the frozen contracts; their leaf implementation may proceed under existing ownership while dependency provenance remains an explicit, unwaived hold. Start and complete the three leaf plans, preserving separate native-proof gates; then proceed to Phases 05–08 only when their defined prerequisites are met. Main integrator should track trusted Glance artifact provenance and strict verification baseline regeneration. Track non-gating Cycle 3 review recommendations separately; they are not prerequisites to the authorized leaf handoff. The overall plan remains in progress at 24h / 132h (18.2%).

## Unresolved questions

The Cycle 3 reviewer confirms the residual tombstone/draft/outbox finding is resolved. Strict dependency provenance for Glance remains unverified in `verification-metadata.xml`; the existing root verification baseline has 149 unmatched checksums. This external hold does not withdraw the terminal conditional handoff to Phases 02, 03, and 04. Leaf-native behavior remains unproven and is due in downstream acceptance.
