# Phase 05: Native transport and sync lifecycle

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [01](phase-01-canonical-contracts-and-pure-projections.md), [02](phase-02-hub-authorization-and-reconciliation.md), [03](phase-03-room-v5-and-access-persistence.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; it is not implementation approval or runtime evidence.

## Parallelization Info
Wave C with06/07/08; depends on01 interfaces,02 authoritative wire and03 real storage. Exclusive coordinator/network/auth/outbox/scheduling implementation and owned tests. No edits to hub-sync-dtos or RoomAccountStore/DAO; request fixes from01/03. Transport-only construction can consume frozen types, but real sync acceptance needs02/03/04 and platform consumers.

## Overview
- Date: 2026-10-21. Description: Native transport and sync lifecycle. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22**; implementation scope delivered and approved with noted issues per user-provided disposition.
- Reported verification: Cycle 3 review reports `:shared:jvmTest` 179/179 including outbox/reconciliation coverage; selected QA reports the shared JVM suite 329/329 ([review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md), [QA](../../reports/qa-261022-1521-selected-test-suite-results.md)).
- Boundary: dedicated actual coordinator/Room runtime smoke remains unreported; no sync runtime or release gate is inferred from unit results.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- Current coordinator reaches into DAO and assumes task-only/rowId-only scans; replace with01 storage interfaces implemented03. Full scan before writes is useful but not an authority proof without stable E and explicit revalidation.
- No new mutationId/receipt contract: freeze existing local opId and exact submitted owner-qualified tuple. Matching pulled content is convergence only.
- accessChecks absent and [] have different meanings; final empty validation-only request must be serialized explicitly.

## Requirements
- Reuse standard push/pull/delta and JWT-only username lookup. Native capability negotiation must be explicit and fail closed; no schemaVersion inference, auto-POST retry or private reinterpretation of shared cache.
- Reconcile stable revision before shared dispatch/publication. Access-denied is not conflict; purge via03 and cancel via existing projections/lifecycle. Preserve offline cache under last reconciled rights while disconnected.
- Supported assignee edits retain unknown JSON; relationship JSON and task content use separate merge/projection handling; current account generation fences every result.
- Consume the confirmed privacy purge through03 on authoritative denial: discard revoked collaborative content/unsent payloads and retain content-free non-replayable metadata only; no recovery quarantine, copy-latest reconstruction or automatic replay after regrant. Keep OPERATION_FORBIDDEN readable-content handling distinct from ACCESS_DENIED.
- Treat426/COLLABORATION_REQUIRED as upgrade-required for the whole opted-in TabbyRush request, not a cue to retry private-only. Capability disappearance hides/pauses shared state without converting ownership; non-enabled servers still support existing private mode for genuinely private data. Offline caches cannot be remotely erased.

## Architecture
- Round: freeze shared dispatch/publication -> null scan -> bind E+mode+generation -> stage all pages -> enumerate staged/previous accessible/draft/outbox/displayed keys -> bounded checks at E -> final accessChecks=[] at E -> atomic03 promotion -> select/freeze eligible intents -> push -> later null scan. No across-page content snapshot claim.
- Native username lookup uses /api/v1/auth/lookup?username=... with JWT only, URL encoding and existing auth refresh/generation policy; server validates eligibility at assignment commit. LookupRecipient carries userId/username only necessary to choose, not an app-access grant.
- Capable scans request todos+taskAssignees and validate stored-id cursor ordering, trusted owners, exact numeric versions/revision, full result coverage and schema/mode continuity. Check requests omit scan table selection and preserve present empty list.
- Access revision mismatch discards unfinished staging and restarts null; malformed/failed pages leave active base/access intact and dispatch paused. Authoritative unavailable/ACCESS_DENIED/membership denial invalidates affected shared refs even if later scan fails.
- Capture03's local accessGeneration on content requests/publication admission and verify it under the commit lock. ACCESS_DENIED advances the generation/invalidation; OPERATION_FORBIDDEN rejects only that operation, without purging readable content. Ignore delayed pre-revocation content even if account identity is unchanged or regrant has occurred. Add these two distinct-error and delayed-response transitions to behavioral coverage.
- Reminder delivery/scheduling uses TaskRef and current access at admission/pre-post;03 owns ledger/query implementation,07/08 own native adapters. Successful delta push ack commits independently of stale/failed pull.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/task-json-merge.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-scheduling-coordinator.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate.kt` — modify; Transport and reconciliation implementations consume frozen interfaces; canonical sync/reminder lifecycle.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` — modify; JWT-only lookup and account-generation-safe capability/auth transport.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-client.kt` — modify; JWT-only lookup and account-generation-safe capability/auth transport.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-dtos.kt` — modify; JWT-only lookup and account-generation-safe capability/auth transport.
- `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt` — modify; JWT-only lookup and account-generation-safe capability/auth transport.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/hub-sync-client-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/task-json-merge-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/staged-full-scan-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate-test.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/recording-native-reminder-adapter.kt` — modify; Transport/reconciliation behavioral tests and affected exact-identity fixtures.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-client-test.kt` — modify; Lookup policy/generation and restoration integration tests.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-session-test.kt` — modify; Lookup policy/generation and restoration integration tests.
- `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/collaboration-reconciliation-test.kt` — create; Stable-revision, explicit empty accessChecks, denial and exact-ack race behavior (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/task-json-merge.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-scheduling-coordinator.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-client.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-dtos.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/hub-sync-client-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/task-json-merge-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/staged-full-scan-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/reminder-delivery-gate-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/recording-native-reminder-adapter.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-client-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-session-test.kt` | modify | 05 exclusively |
| `G:/ws/sharing/doggo/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/collaboration-reconciliation-test.kt` | create (proposed) | 05 exclusively |

## Implementation Steps
1. Use LSP references for exported coordinator/client/merge/auth methods and migrate owned callers/tests. Replace coordinator/OutboxReconciliation direct store.dao access with frozen SyncStorage/CollaborationStore operations from01; no secondary data store or no-op bridge.
2. Implement mode-aware request construction and response validation with collaborationVersion1 on each applicable push/pull part, mandatory trusted owners for capable records/results, safe exact integer checks, duplicate canonical detection and current account-generation fencing. Handle426/COLLABORATION_REQUIRED as a whole-app upgrade-required stop; never strip capability/shared records and retry private-only. Keep legacy private mode against old/non-enabled servers only for genuinely private data, without reinterpreting previously shared cache/outbox.
3. Implement username lookup via existing HubAuthClient/Session and classify exact lookup route JWT-only in HubHttpClient. URL encode username, no admin token/API-key requirement for lookup, sanitize errors, discard switched-account/obsolete picker results. Do not auto-refresh/replay mutation POSTs.
4. Implement table-aware raw JSON merges/projections. Tasks use explicit editable intent masks preserving unknown nested/null fields; relations validate id=rowId=taskId and immutable target independently. Shared task owner comes only from trusted wire/storage identity, never TaskData JSON or current actor fallback.
5. Implement scan generation state machine and durable03 staging: null start, revision E from first page, same E/mode/account through terminal page, stored-id monotonic cursor including equal timestamps across owners/tables. New shared rows do not publish page-by-page; private unsent owner-created tasks are excluded from missing-remote access checks.
6. Compute exact union of staged/previous accessible/pending/draft/displayed refs. Batch at<=100 and body<=256KiB, with exact one-result-per-ref validation; reject missing/repeated/substituted owner results. Send required explicit [] final validation at E even for empty union. Promotion is one03 transaction only after final success.
7. On409 ACCESS_REVISION_CHANGED discard unfinished generation and restart null with dispatch paused. A partial/network/parse failure never revokes or deletes. Authoritative per-key unavailable/ACCESS_DENIED immediately invokes03's confirmed purge of revoked content and unsent collaborative payloads, retaining only content-free non-replayable evidence; current membership denial invalidates all affected shared access. No recovery quarantine or payload preservation for later replay. Capability disappearance instead hides/pauses shared state and displays upgrade/config error, never resubmits it as caller-owned. OPERATION_FORBIDDEN rejects only the disallowed intent while retaining read access.
8. Only after promotion select eligible outbox heads and freeze complete owner-qualified envelopes/local opId/version. First assignment waits for confirmed task create; dependency failures leave child/relation pending rather than assume batch atomicity. Preserve max100 and256KiB selection; overlarge record remains actionable unsent rather than uncertain network result.
9. Apply exact ack only for frozen owner/table/row/submittedVersion and local opId; wrong-owner/count/pull-match cannot clear intent. Preserve newer edits. Conflict review re-fetches/revalidates before explicit reapply, forbids recreate/reparent and revoked copy-latest payload paths; unknown commit remains uncertain with no blind replay. Regrant does not revive old evidence/intents.
10. Handle sequential delta: persist accepted push even if pull malformed/fails; visibility-changing push invalidates old E and restarts pull. Current access is reauthorized by server even after successful final client check; client expected revision is context only.
11. Update reminder coordinator to canonical keys/current access and suppression, admission and pre-post checks. Consume03 projection invalidation for scheduling/cancellation; retain leave barriers and account generation. Own shared reminder behavior tests, while03 owns real ledger SQLite suites.
12. Add meaningful transport/admission tests for absent versus[], boundaries101/over256KiB, malformed access arrays, E race between pages/checks/final, switched account, unknown JSON, lookup auth/encoding, exact ack collisions, delta partial success, authoritative denial before failed scan, capability loss and regrant/no-replay. Use real SQLite tests in03 and live provider proof09 rather than treating in-memory mocks as persistence proof.

## Todo list
- [x] Implement numbered steps in assigned files, migrating LSP-referenced owned callers without shims.
- [x] Add/update the phase-owned behavioral/security/migration coverage specified above; no copied-source/default tests.
- [x] Implement confirmed authoritative-denial purge/no-replay transitions and whole-app upgrade-required handling; add behavior coverage preventing recovery quarantine, delayed payload resurrection and unsafe private-only retry.
- [x] Hand off real interfaces/behavior and pending checks to main/integration owner; do not run mid-flight checks without explicit verification assignment.

## Success Criteria
- **Acceptance criterion (full evidence not itemized):** transport/state-machine tests demonstrate reconcile-before-dispatch, explicit final [] request, atomic promotion boundary, exact owner-qualified ack and account fencing. Failures cannot turn absence into deletion/revocation. Shared JVM tests are reported passed with sync/outbox coverage, but not a per-case breakdown for every boundary.
- **Runtime criterion not run:** actual coordinator with Phase 02/03 and disposable Phase 04 fixture converges creator/Alice/Bob, backfills old rows and rejects queued revoked changes. This runtime check requires Phase 09.
- **Full scenario not evidenced:** denial/restart/regrant sequences show only content-free evidence survives and fresh hydration never replays discarded intents; stale accessGeneration responses cannot republish payloads. OPERATION_FORBIDDEN preserves readable content. Capability 426 stops opted-in sync without private downgrade, while unrelated/non-enabled private mode remains unchanged.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Revision churn can starve completion; pause and surface actionable retry state, never promote incomplete data or add an unplanned event service. No implicit long-lived lease after final check.
- Avoid clearing push ack because a delta pull failed, or treating membership/capability errors as ordinary conflict retry. Privacy purge cannot later recover discarded unsent payloads.

## Security Considerations
- No credentials/remote task content in logs. Lookup resolves identity only. Server authoritative liveness is required even if ChildOnly has no local parent. Never replay denied/refrozen shared intent after regrant; unknown commit is not permission to retry POST.

## Next steps
Deliver coordinator/action/lookup behavior to06/07/08 via frozen interfaces; route storage defects03, wire definitions01 and server behavior02.09 executes cross-layer real feature smoke.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
