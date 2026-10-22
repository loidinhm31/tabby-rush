# Phase 01: Canonical contracts and pure projections

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: none (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave A, single contract owner. No prerequisites. Publishes real types, pure algorithms and signatures; downstream implementations may temporarily not compile until their owners migrate callers. No compatibility overloads. Construction gate: targeted core/pure-contract checks; complete native/Hub compilation waits for owning consumers in later waves.

## Overview
- Date: 2026-10-21. Description: Canonical contracts and pure projections. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:shared:jvmTest` 179/179 and `glean-hub-sync-engine` 6/6; review scope includes canonical access contracts and projections ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md)).
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; this is not release approval.
- Evidence is report-attributed; no tests were run for this plan reconciliation.

## Key Insights
- Canonical row IDs collide legitimately across owners and across task/relation tables. AccountKey supplies partition, not owner identity. SchemaVersion is not capability negotiation.
- Existing persisted schedule projector imports Room entities; convert its input to immutable contract snapshots so phase03 owns entity conversion and phase05 never needs DAO implementation edits.
- Private parent absent locally differs from invalid parent on server. Trusted child-only metadata permits flat projection; it cannot manufacture authority for an orphan.

## Requirements
- Define CanonicalRecordRef(ownerUserId, tableName, rowId) and TaskRef(ownerUserId, rowId); owner must be nonblank, table validated, no String aliases. Keep TaskData JSON id/parentTaskId as wire structural strings within stored owner namespace; public task identities/actions become TaskRef.
- Freeze exact optional wire additions from frozen contracts §§2–6, preserving omission for legacy apps. Pull accessChecks must distinguish absent from explicit empty list. No mutationId or receipt additions.
- Freeze typed access/coverage, assignment/version results, owner-binding state, scan generation and storage operations before downstream parallel work.
- Encode the confirmed privacy choice in access-loss result/evidence contracts: canonical identity/opId/versions/timestamps/reason only, content-free and non-replayable. No revoked JSON, title, deadline, recipient, draft/diff or recovery quarantine; regrant cannot restore discarded intents.
- Define HTTP426/COLLABORATION_REQUIRED as upgrade-required for the entire opted-in TabbyRush push/pull/delta request without capability1, before reads or mutations; no private-only downgrade. Non-opted-in apps retain legacy contracts.

## Architecture
- Contract values: TaskCapabilities(editContent, manageAssignment, deleteTask, createChild), HierarchyCoverage(FullGroup, ChildOnly, Standalone), TaskAccessState(ref, revision, capabilities, coverage), AssignmentIntent(taskRef, desiredRecipientUserId?, expectedSlotVersion).
- AccountStore consumes TaskRef for read/edit/delete/completion and AssignmentIntent for assignment. CollaborationStore exposes initializeOwnerBinding(AccountIdentity), accessFor(TaskRef), stageDataPage, stageAccessResults, promoteReconciliation, invalidateAccess and review-evidence dismissal. SyncStorage exposes frozen outbox preparation/results, confirmed-base reads and conflict operations without DAO/Room types.
- Named results distinguish Missing, AccessDenied, VersionConflict, InvalidStructure, Unchanged and Changed; ownership binding mismatch fails closed. DTOs retain local opId only locally; acknowledgement requires owner/table/row/submittedVersion.
- Freeze a persisted local accessGeneration in reconciliation/publication fence values, distinct from account generation and server revision. ACCESS_DENIED invalidates read access; OPERATION_FORBIDDEN rejects a privileged action while preserving currently accessible content. Local invalidation advances accessGeneration; stale content responses/publications cannot revive purged state.
- Freeze read-only PersistedProjectionInputs for base/outbox/access records; no Room/Ktor imports in new domain contracts. LocalAccount legacy rows stay explicitly unresolved and local-only until verified binding; never build TaskRef from a hash or default user.
- Rust SharingConfig.assignmentPolicy is optional version1 with hierarchyParentField/editableFields. Rust/Kotlin/TS canonical references, access results and safe integers agree. Capable cursor id is unique stored record ID; legacy semantics remain separate.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-action.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-coordinator.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/safe-errors.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-data-contract.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-contracts.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-projector.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-task-schedule.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projector.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-scheduler.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/auth-session.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/app-factories.kt` — modify; Freeze canonical identity, capability/coverage, typed operations and pure projection contracts.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-dtos.kt` — modify; Actual Kotlin standard-sync wire types, owner-qualified results and explicit accessChecks presence.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/canonical-record-ref.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-access-contracts.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/collaboration-store-contracts.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-storage-contracts.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-projection-inputs.kt` — create; Concrete shared value/interface definitions; no implementation stubs (proposed new path).
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-surface-projector-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projection-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/adversarial-scheduler-boundary-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-reminder-models-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-presentation-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/remote-contract-vector-test.kt` — modify; Migrate affected pure-model fixtures and assert identity/coverage behavior.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/canonical-task-access-test.kt` — create; Behavioral duplicate-owner and full-group/child-only contract tests (proposed new path).
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/sharing.rs` — modify; Actual Rust policy/wire/reference definitions, constructors and legacy omission semantics.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/protocol.rs` — modify; Actual Rust policy/wire/reference definitions, constructors and legacy omission semantics.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/conflict.rs` — modify; Actual Rust policy/wire/reference definitions, constructors and legacy omission semantics.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/document.rs` — modify; Actual Rust policy/wire/reference definitions, constructors and legacy omission semantics.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/checkpoint.rs` — modify; Actual Rust policy/wire/reference definitions, constructors and legacy omission semantics.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client/src/client.rs` — modify; Migrate Rust request literals/export registration and generated TypeScript source consumers.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client/src/bin/export_types.rs` — modify; Migrate Rust request literals/export registration and generated TypeScript source consumers.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client-types/src/index.ts` — modify; Migrate Rust request literals/export registration and generated TypeScript source consumers.
- `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client-types/src/client.ts` — modify; Migrate Rust request literals/export registration and generated TypeScript source consumers.

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-action.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-coordinator.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/safe-errors.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-data-contract.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-contracts.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-surface-projector.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-task-schedule.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projector.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-scheduler.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/auth-session.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/app-factories.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-dtos.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/canonical-record-ref.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-access-contracts.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/collaboration-store-contracts.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/assignment-contracts.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-storage-contracts.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-projection-inputs.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-hierarchy-projector-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-surface-projector-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projection-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/adversarial-scheduler-boundary-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-reminder-models-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-presentation-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/remote-contract-vector-test.kt` | modify | 01 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/canonical-task-access-test.kt` | create (proposed) | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/sharing.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/protocol.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/conflict.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/document.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-engine/src/checkpoint.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client/src/client.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client/src/bin/export_types.rs` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client-types/src/index.ts` | modify | 01 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-core/glean-hub-sync-client-types/src/client.ts` | modify | 01 exclusively |

## Implementation Steps
1. Use LSP references for every changed exported type/signature and constructor. Record affected callers against this manifest; request a manifest revision before an unowned edit. Migrate owned Rust client literals, From conversions, ConflictInfo constructors and Kotlin pure-model fixtures; later owners migrate their own source/tests.
2. Add explicit canonical values and add trusted owner/access/coverage to Todo and immutable UI/editor/conflict/schedule models. Public parent/navigation selections use owner-qualified references; preserve raw TaskData.parentTaskId only inside owner namespace. Deterministic sorting ties include owner, not rowId alone.
3. Define real storage/sync/lookup interfaces. Freeze initializeOwnerBinding validation of origin+user/account key; no guessed user. Bind local-only legacy state separately from canonical shared state. Specify state/result payloads sufficient for phase03 and05 without concrete DAO/entity leaks, including atomic revoked-payload purge, content-free non-replayable evidence/dismissal and fresh-reconciliation-only regrant; expose no recovery/replay operation.
4. Implement pure hierarchy grouping keyed by TaskRef, same-owner parent association, valid full-group completion only after reconciled coverage, flat real ChildOnly item with no synthetic parent or sibling counts. Unknown/unreconciled missing parents remain unresolved; trusted server child-only metadata is required.
5. Refactor persisted schedule projector to immutable contract inputs and owner-qualified schedule keys; filter unavailable/relation rows and retain draft-free scheduling. Supply typed adapter contract for phase03, not temporary fake entities.
6. Extend actual Rust protocol/sharing/document/conflict/checkpoint and Kotlin DTO files. Freeze capability1 on each push/pull part; owner on all record/result identities; access revision/results; validation-only mode and optional-field omission. Freeze whole-request426 upgrade-required semantics for opted-in missing/unsupported capability, with no private-only fallback. Use nullable optional list default null, preserve [] during encoding; do not let default todos tables force mixed scan/check mode.
7. Update Rust client request construction and export_types registration, regenerate TypeScript source using established export command only when future implementation authorizes it; migrate client.ts optional/default request consumers. Do not hand-edit ignored dist outputs or migrate embedded Cham-lang.
8. Add meaningful pure behavior tests: same rowId for two owners, relation/task collision, ChildOnly no parent synthesis, FullGroup versus partial completion, revoked schedule exclusion. Serialization behavior belongs wire contract tests, not copied-source/default assertions.
9. Publish frozen interface signatures and example standard envelopes to downstream owners. Require explicit revision agreement if an implementation discovers a missing method; do not create aliases/stubs to make a wave look buildable.

## Todo list
- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement content-free/non-replayable access-loss contracts and whole-request capability1/upgrade-required semantics confirmed by G0; migrate owned contract consumers without recovery or downgrade aliases.
- [x] Hand off real interfaces/behavior and pending checks to main/integration owner; do not run mid-flight checks without explicit verification assignment.

## Success Criteria
- **Acceptance criterion (full evidence not recorded):** pure behavioral suites establish duplicate-owner isolation, no parent content fabrication and no automatic parent mutation for ChildOnly/staged coverage. Rust/Kotlin/TypeScript definitions agree on optional legacy omissions and explicit [] check mode. **Evidence:** Cycle 3 review reports 179 shared JVM and 6 sync-engine tests passed; it does not itemize every criterion case.
- Every exported-change caller has an assigned owner; new interfaces express required real operations without implementations/stubs. Full build is not claimed at this contract-only boundary.
- **Acceptance criterion (full evidence not recorded):** contract behavior/serialization coverage proves access-loss evidence cannot carry revoked payloads or replay discarded intents, and opted-in capability errors express whole-request upgrade-required semantics without changing unrelated apps. G0 is policy requirements only; end-to-end evidence remains open.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Signature churn can block parallel owners. Freeze complete payloads before wave B and require coordinated versioned plan revision, not unilateral additions. Keep actual wire DTO ownership here for entire feature.
- Temporary cross-module compile gaps are expected between commits; never resolve them with String aliases or no-op storage bridges.

## Security Considerations
- Owner selectors never authorize. Keep trusted server access distinct from user content. No parent titles/sibling counts/other-recipient rows in ChildOnly metadata; no default owner or reverse hash mapping.

## Next steps
Release the interface handoff to02/03/04; supply read-only contracts to05/06/07/08. Parent/main runs authorized module checks after downstream callers are integrated.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
