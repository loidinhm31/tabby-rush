# Phase 04: Admin policy and native provisioning

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave B with02/03; depends on01 policy/wire contract. Exclusive admin TS/pages and native schema/provisioning ownership. Admin construction does not require live02; saved-policy runtime checks and any enablement require02. No deployment/configuration mutation authorized by this plan.

## Overview
- Date: 2026-10-21. Description: Admin policy and native provisioning. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Glean Hub admin unit suite 51/51 ([selected QA](../../reports/qa-261022-1521-selected-test-suite-results.md)); native provisioner Node suite 12/12 in the earlier regression QA ([QA](../../reports/qa-261021-2225-critical-fix-regression-tests.md)).
- Boundary: no live admin browser or real app registration/enablement acceptance is claimed; production actions remain unauthorized.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Use existing registration/edit screens and expectedSchemaVersion flow; no second policy UI. Policy absent preserves legacy viewer apps. Effective policy equality must agree with02 server no-op behavior.
- Native schema/provisioning currently describes only todos; adding a relation table without sending validated SharingConfig would not implement collaboration.

## Requirements
- Add optional assignmentPolicy version1 to admin service types/decoder/help and native provisioning input. Preserve current five SharingConfig fields and unknown JSON retention where existing admin workflow does so.
- TabbyRush schema gains taskAssignees{id,taskId,userId} with ordinary metadata envelope, no todos.assignee field. All policy mapping/allowlist values exactly match frozen design.
- Preserve schema draft on CAS conflict, credential custody, private output checks, no-op version stability and ordinary new-app-only provisioning boundary.
- Admin/provisioning help must communicate the confirmed opt-in cutoff: all legacy TabbyRush online push/pull/delta stops before reads/writes until capability1 upgrade; no private-only downgrade or remote offline-cache erasure, unrelated apps unchanged. Explain required transaction-capable replica set/single app-wide guard and destructive revoked collaborative-edit purge/no recovery replay. Operational rollout authorization remains outstanding, not a new policy-choice review.

## Architecture
- Registration/edit service round-trips policy with version1, hierarchyParentField=parentTaskId and exact editableFields from frozen contracts §2. JSON help explains owner-only relation/deletion/children and inherited child access, not arbitrary roles.
- Native fixture top-level sharing maps taskAssignees/taskId/userId/todos/empty childTables; schema adds relation table with id primary key. Existing nullable parentTaskId stays in todos. Script forwards validated sharing to ordinary registration.
- Admin indicates app-wide older-client cutoff, replica-set prerequisite and refusal of silent toggles with existing assignment data. Controls may be constructed before02 but runtime enablement waits for server/client acceptance and explicit authorization.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/glean-hub/glean-hub-app/src/services/sync-admin-service.ts` — modify; Admin policy decoding, schema CAS/no-op drafts, validation and help.
- `G:/ws/sharing/glean-hub/glean-hub-app/src/services/sync-admin-service.test.ts` — modify; Admin policy decoding, schema CAS/no-op drafts, validation and help.
- `G:/ws/sharing/glean-hub/glean-hub-app/src/pages/admin/app-registration.tsx` — modify; Admin policy decoding, schema CAS/no-op drafts, validation and help.
- `G:/ws/sharing/glean-hub/glean-hub-app/src/pages/admin/edit-app.tsx` — modify; Admin policy decoding, schema CAS/no-op drafts, validation and help.
- `G:/ws/sharing/doggo/integration/glean-hub/tabbyrush-schema.json` — modify; Add relation table/policy to ordinary provisioning without enabling any real app.
- `G:/ws/sharing/doggo/scripts/hub-schema-provision.mjs` — modify; Add relation table/policy to ordinary provisioning without enabling any real app.
- `G:/ws/sharing/doggo/scripts/hub-schema-provision.test.mjs` — create; Behavioral schema-input validation and refusal before registration on invalid policy (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/glean-hub/glean-hub-app/src/services/sync-admin-service.ts` | modify | 04 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-app/src/services/sync-admin-service.test.ts` | modify | 04 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-app/src/pages/admin/app-registration.tsx` | modify | 04 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-app/src/pages/admin/edit-app.tsx` | modify | 04 exclusively |
| `G:/ws/sharing/doggo/integration/glean-hub/tabbyrush-schema.json` | modify | 04 exclusively |
| `G:/ws/sharing/doggo/scripts/hub-schema-provision.mjs` | modify | 04 exclusively |
| `G:/ws/sharing/doggo/scripts/hub-schema-provision.test.mjs` | create (proposed) | 04 exclusively |

## Implementation Steps
1. Use LSP references for SharingConfig and registration/schema request/response consumers; migrate only owned service and pages. Phase01 owns generated sync-client TS,02 owns Rust validation/equality.
2. Extend strict admin decoding/validation for optional policy. Reject unsupported versions, invalid mapping/allowlist values, relation/content overlap and incompatible childTables; do not cast malformed values into apparent success. Preserve omitted policy on legacy configurations.
3. Update registration/edit JSON examples and help to exact frozen shape and confirmed cutoff/privacy/guard requirements: upgrade-required for the whole opted-in app, no private-only fallback, no remote offline erasure, revoked unsent edits purged without recovery/replay, transaction-capable replica set with app-wide serialization. Keep user-entered changed schema/policy after409 so stale drafts can be reviewed, not silently overwritten. Surface topology/compatibility errors actionably without credentials/internal DB errors; help is not permission to enable policy.
4. Extend native schema fixture with taskAssignees string fields and sharing mapping. Keep content solely in todos and parentTaskId nullable; no per-recipient content copies, no redundant authoritative assigneeId.
5. Update hub-schema-provision input validation/registration body to pass sharing. Preserve no destructive existing-app upgrade, no production registration, existing output DACL/symlink checks before POST and protected env credential flow.
6. Add behavior tests for accepted valid mapping, invalid policy refusal before network registration, omitted-policy legacy flow, admin no-op/CAS/policy round-trip and stale draft preservation. Assert displayed cutoff/upgrade, destructive revoked-edit purge and replica-set/app-guard guidance; never offer unsafe private-only downgrade. Test response validation and registration effects, not copied literals or source grep.
7. Hand off schema/config artifact to02 and09. Document in handoff that a fixture change is not app enablement; enabling/disabling with existing assignments requires separately authorized quiescence, never automatic fallback.

## Todo list
- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement confirmed cutoff/privacy/transaction guidance and actionable upgrade/topology feedback in existing admin/provisioning flows; preserve separate enforcement, native acceptance and operational enablement authorization gates.
- [x] Hand off real interfaces/behavior and pending checks to main/integration owner; do not run mid-flight checks without explicit verification assignment.
## Success Criteria
- **Acceptance criteria:** admin service/policy tests and provisioning behavioral tests reject unsupported mappings before POST, retain stale edit drafts, preserve optional legacy omission and round-trip exact policy. **Reported evidence:** Hub admin unit suite 51/51 and provisioner Node suite 12/12 passed; individual results are not enumerated in QA summaries.
- **Live criterion not evidenced:** disposable admin registration/edit API/browser smoke with Phase 02 shows no-op stable version, changed-policy CAS and safe cutoff/topology feedback.
- **Rendered/operational criterion not evidenced:** rendered admin help/feedback accurately describes the whole-app upgrade cutoff, no private downgrade/no remote offline erasure, no revoked-edit recovery and replica-set/app-wide guard prerequisite; no real app enablement or production authorization is claimed.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Client validation and server validation may diverge;02 remains authoritative and both consume01 frozen shape. The older-client cutoff is confirmed, not an outstanding compatibility choice. Actual rollout still needs server/client/runtime acceptance and explicit operational authorization; never infer readiness from schemaVersion or offer private-only downgrade.

## Security Considerations
- No recipient eligibility grants from policy/provisioning. Do not request admin user-list for assignment lookup. Preserve private credential-output checks and never log administrative/API credentials.

## Next steps
Supply disposable registration fixture to09; wait for02 enforcement and both native runtimes before any local opt-in acceptance. Production changes remain separate authorization.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
