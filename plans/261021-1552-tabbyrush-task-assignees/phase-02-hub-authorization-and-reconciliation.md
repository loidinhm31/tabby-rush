# Phase 02: Hub authorization and reconciliation

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave B with03/04; depends on01. Exclusive Hub Rust plugin/service/model/route/test ownership; phase01 owns engine/client DTO definitions,04 owns admin TS. Construction can use phase01 types immediately. Runtime Hub+native acceptance additionally needs04/05 and both platforms; never treat parallel source construction as end-to-end readiness.

## Overview
- Date: 2026-10-21. Description: Hub authorization and reconciliation. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: `task_assignment_integration` 6/6 and `schema_version_integration` 4/4 passed against the explicitly authorized disposable Mongo endpoint ([QA](../../reports/qa-261022-1521-selected-test-suite-results.md)); Cycle 3 review reports Hub sync library 31/31.
- Boundary: these targets do not establish completion of the dedicated API smoke, every race/pagination scenario, collision-recovery coverage or measured fence contention.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Current caller-owned upsert and rowId-only parent owner lookup cannot be relaxed safely. Resolve stored owner-qualified tuples before authorization, including tombstones and empty/malformed payloads.
- Existing app collection unique (userId, tableName, rowId) backs stable assignment slots because slot rowId=data.id=taskId. Do not invent a second grant index or separate authority in todos.
- Live same-owner root is required internally for every child grant, including direct assignment; lack of local parent on Bob is a privacy projection, not server orphan permission.

## Requirements
- Enforce additive policy validation/current stored app membership, strict content allowlist, creator-only grant/delete/restore/children/structure and CAS on all standard paths.
- Implement the confirmed transaction-capable Mongo replica-set requirement and one app-scoped authorization guard shared across all opted-in mutations. No unguarded fallback or owner/tree-guard redesign. Revision-bound data scan and bounded validation-only pull; no event service or persistent HTTP snapshot.
- Enforce the confirmed whole-app cutoff: opted-in TabbyRush clients missing/unsupported capability1 receive HTTP426/COLLABORATION_REQUIRED before any push/pull/delta mutation or read, including private records. Require upgrade; no private-only downgrade. Unsupported policy/topology fails closed; unrelated viewer apps remain unchanged. Denial cannot remotely erase offline client caches.

## Architecture
- Implement assignment_authorization helper over canonical stored tuples and current users membership using the existing user storage representation; never stale JWT apps as sole authorization. Membership administration itself is not expanded.
- Internal _sync_assignment_fences has unique appId, accessRevision initially1 and authorizationSerial initially0. Each mutation transaction increments serial first, then reads schema/current membership/target/root/grants, performs exact CAS and commits. Grant/liveness/hierarchy/config changes increment revision under the same guard. Snapshot read concern per data/check page; majority write concern appropriate for durable transaction commit.
- Root direct access and live-root inheritance union; child direct access additionally requires internally verified live same-owner root. Creator orphan evidence may remain readable for repair/deletion, but no assignee rights. Return only relation rows actor is authorized to receive; metadata conveys effective rights without revealing other recipients.
- Pull scans use (syncedAt, stored _id), expectedAccessRevision E and capability/mode validation. accessChecks present, including [], selects validation-only response; no rows, unchanged checkpoint, hasMore=false, one result per exact ref. Max100 keys/256KiB; reject mixed scan/check requests.
- Exact accepted owner/table/row/submittedVersion identifies client version, not operation receipt. ACCESS_DENIED differs from conflict; 409 ACCESS_REVISION_CHANGED and426 COLLABORATION_REQUIRED retain frozen meanings.
- Distinguish ACCESS_DENIED (no effective read rights, generic unavailable target) from OPERATION_FORBIDDEN (read remains allowed but action/field is forbidden). Only the former is an access-loss signal; denial of an assignee delete/assignment attempt must not falsely revoke its legitimate content access.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml` — modify; Register task_assignment_integration with existing mongo-integration-tests required feature; no accidental database access in ordinary tests.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/schema_validator.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/mod.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/app.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/api.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/record.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/mod.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/sync.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/apps.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/dual_auth.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/jobs/deleted_records_cleanup.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/error.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/lib.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/sync_generation_tests.rs` — modify; Enforce policy transactions, canonical authorization, current membership, revision scans and all existing contract literals.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/tests/schema_version_integration.rs` — modify; Behavioral policy/schema CAS/no-op and topology acceptance tests.
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/assignment_authorization.rs` — create; Narrow transaction/access helpers and real replica-set hostile/race tests (proposed new path).
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs` — create; Narrow transaction/access helpers and real replica-set hostile/race tests (proposed new path).
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/assignment_fence.rs` — create; Narrow transaction/access helpers and real replica-set hostile/race tests (proposed new path).
- `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/tests/task_assignment_integration.rs` — create; Narrow transaction/access helpers and real replica-set hostile/race tests (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/schema_validator.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/mod.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/app.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/api.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/record.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/mod.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/sync.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/apps.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/dual_auth.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/jobs/deleted_records_cleanup.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/error.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/lib.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/sync_generation_tests.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/tests/schema_version_integration.rs` | modify | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/assignment_authorization.rs` | create (proposed) | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/assignment_reconciliation.rs` | create (proposed) | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/assignment_fence.rs` | create (proposed) | 02 exclusively |
| `G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/tests/task_assignment_integration.rs` | create (proposed) | 02 exclusively |

## Implementation Steps
1. Read assigned sources and use LSP references for changed plugin models/request literals. Migrate sync_generation_tests and schema-version integration consumers under this owner; request phase01 contract amendment rather than editing its Rust engine files.
2. Validate policy version/mapping against actual schema: distinct relation/content tables; relation string id/taskId/userId, nullable same-table parent linkage, no childTables ambiguity; allowlist excludes id/owner/createdAt/parent/assignment/unknown fields. Include all added policy fields in effective equality and no-op/CAS handling.
3. Check replica-set transaction support before enabling/serving opted-in mode; fail closed without unguarded fallback. Implement the confirmed single app-wide guard, not an owner/tree redesign. Create unique app fence and retain existing canonical row index; initialize once safely. Policy visibility changes update registry+revision transactionally; reject ordinary enable/disable with existing assignment data without separately authorized quiesced compatibility procedure.
4. For each opted-in request validate collaborationVersion=1 for all required parts before any mutation or read, including delta push and pull. Missing/unsupported capability fails the entire request with426/COLLABORATION_REQUIRED, even private records; no private-only retry path. For each record transaction, acquire app guard first and re-read current stored policy/membership. Enforce actor/recipient eligibility independently of lookup response.
5. Resolve exact (app, owner, table, row); no global rowId search/fallback to caller-owned copy. New task/slot owner equals actor; absent target shared write cannot upsert. Check data.id, immutable createdAt/identity/linkage, maximum two levels/no reparent. Assignee modifications compare complete old/new JSON; reject changes/removal of unknown/noneditable keys, tombstones and restore.
6. Implement stable assignment slot CAS: live same-owner target, child live same-owner root, rowId=id=taskId, immutable target, existing eligible recipient. First insert assumed0 with unique collision -> reload/conflict; reassignment/unassign/regrant all use current slot version and strictly larger safe submitted version. Preserve slot tombstones during cleanup in both direct purge and cleanup job.
7. Implement content/relationship transactions per record, deterministic parent-before-child dependency handling, partial batch accepted tuples. Shared app guard orders edit/revoke and parent liveness changes across processes. Retry aborted-before-commit transactions only; unknown commit result returns uncertain outcome, no blind POST reexecution. Owner deletion/restoration paths cannot expose orphan child grants.
8. Implement capable scans and explicit checks in page snapshot transactions reading revision and visibility/current membership together. E mismatch returns409; cursor string _id and mode must validate. At terminal scan client must still make bounded checks plus explicit []; server must not treat absence as revocation or rows as one cross-page content snapshot.
9. Preserve successful delta push acknowledgements even if its pull fails; if push changed visibility, reject/restart stale-E pull rather than attach invalid rows. Whole-app membership denial is authoritative; errors must not disclose target existence, private parent or other assignees.
10. Add real replica-set integration tests using creator/Alice/Bob/outsider and duplicate-owner IDs. Exercise first-create/reassign CAS races, edit/revoke commit ordering, invalid parents/recipients, forged slot/empty/tombstone/restore/delta, stale membership JWT, equal timestamp paging, bounded checks and legacy viewer behavior. Use transaction coordination/barriers across independent actors, not sleeps as race proof. Assert missing/unsupported capability denies every opted-in route before reads/writes and never privately downgrades; collect app-guard contention for09 disposable acceptance.
11. Register `task_assignment_integration` in Cargo.toml with `required-features=["mongo-integration-tests"]`; enforce the disposable URI/host allowlist. Ordinary tests must never connect silently to developer/production databases. **Reported execution:** `task_assignment_integration` 6/6 and `schema_version_integration` 4/4 passed with the authorized disposable Mongo endpoint ([QA](../../reports/qa-261022-1521-selected-test-suite-results.md)); the full hostile/race matrix and guard-contention measurement remain open.
12. Hand off authoritative server error/envelope behavior to05 and validation constraints to04. Any contract drift goes through01; phase09 receives behavioral test names and required disposable smoke probes, not permission to patch these sources.

- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement the confirmed whole-request capability cutoff on all standard routes and the single app-wide transactional authorization guard; deliver owned behavior coverage and hand off remaining integration gates.
- [x] Hand off real interfaces/behavior and pending acceptance checks to main/integration owner.

## Success Criteria
- **Acceptance criterion (not fully evidenced):** actual Mongo tests demonstrate one canonical creator row, one slot across tombstone cleanup/regrant, exact-version conflict, no post-revoke stale commit, no direct OR inherited child grant with invalid server root. **Evidence status:** 6/6 assignment integration tests passed; Cycle 3 review notes the concurrent insert-collision recovery test is missing.
- **Acceptance criterion (not fully evidenced):** hostile push/pull/delta tests, explicit [] check-mode response, >100/mixed-mode rejection, E races between pages/batches/final check, duplicate owner/table/timestamp cursors, stale JWT membership denial, legacy config/no-op equality. **Evidence status:** integration targets passed 6/6 and 4/4; reports do not break out every listed case.
- **Acceptance criterion (not fully evidenced):** missing/unsupported capability requests, including private-only payloads and delta, return upgrade-required before content reads/mutations; no private downgrade exists and unrelated viewer apps behave unchanged. Independent-actor replica-set proof establishes both edit-before-revoke and revoke-before-edit commit outcomes; disposable acceptance records measured guard contention, not an assumed throughput result. These full race/contention outcomes are not recorded.
- Replica-set unavailable is a blocking environment prerequisite, never an excuse to run unguarded writes. Record app-fence contention in integrated smoke.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- The confirmed app-wide guard serializes opted-in writes; measure contention before release, without speculative owner/tree guard redesign or weakened authorization. Membership administration is separate; claim only current stored-membership checks and strict relationship-revoke ordering, not unimplemented cross-service membership transaction fencing.
- Unknown commit cannot be conflated with conflict/accepted. Protect ordinary task cleanup behavior while retaining assignment slot history.

## Security Considerations
- Every route/path is authoritative server enforcement. Reject forged owner/grant/structure/deletion/restore and currently ineligible users; never auto-grant app eligibility. No app-ID literals, broad legacy sharing rewrite, strict receipts or leaking parent metadata.

## Next steps
Deliver server source/test handoff to05;04 may construct admin code concurrently but cannot enable before this gate. Main schedules authorized replica-set checks and later09 canonical/runtime smoke.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
