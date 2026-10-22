# Phase 09: Integrated acceptance and documentation

## Context links
- Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`; [overview](plan.md), [exhaustive ownership](file-ownership.json), [acceptance traceability](reports/03-acceptance-traceability.md).
- Read-only authority: [frozen contracts](reports/01-frozen-design-contracts.md), [source evidence](reports/00-context-and-source-evidence.md), [brainstorm](../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md).
- Verified inputs: [server research](research/researcher-01-server-identity-authorization.md), [native research](research/researcher-02-native-sync-access-lifecycle.md). Frozen contracts supersede their illustrative alternatives.
- [Proposed architecture §2.2](../../docs/system-architecture.md#22-proposed-task-assignment-collaboration), [code standards](../../docs/code-standards.md). Prerequisite phase links: [02](phase-02-hub-authorization-and-reconciliation.md), [03](phase-03-room-v5-and-access-persistence.md), [04](phase-04-admin-policy-and-native-provisioning.md), [05](phase-05-native-transport-and-sync-lifecycle.md), [06](phase-06-shared-compose-assignee-workflows.md), [07](phase-07-android-actions-and-access-surfaces.md), [08](phase-08-desktop-actions-and-sticky-surfaces.md) (read-only dependencies, not file ownership).
- Completed policy authority: [validation interview, 2026-10-21](reports/06-validation-interview.md). G0 confirms privacy purge, whole-app capability cutoff and the simpler app-wide transaction guard; consume this recorded review, do not re-interview. It is not implementation approval or runtime evidence.

## Parallelization Info
Wave D after02–08;01 is transitive. Sole dedicated end-to-end harness/evidence/docs and smoke Gradle wiring owner. Earlier module tests/sources retain original owners; integration defects are returned to them, not patched here. Main integrates terminal handoffs then runs authorized checks once; no concurrent build storms against half-migrated source.

## Overview
- Date: 2026-10-21. Description: Integrated acceptance and documentation. Priority: P2.
- Implementation status: **COMPLETED 2026-10-22 for harness/documentation and evidence reconciliation**; implementation approved with noted issues per user-provided disposition.
- Reported verification: selected QA run reports 390/390 across shared JVM 329, Hub admin 51, task-assignment Mongo integration 6 and schema-version Mongo integration 4 ([QA](../../reports/qa-261022-1521-selected-test-suite-results.md)); earlier 625/625 regression results are a separate snapshot ([QA](../../reports/qa-261021-2225-critical-fix-regression-tests.md)).
- Boundary: prepared dedicated feature smoke harnesses are not evidence that their commands ran. G2 full smoke/race and G3–G6 runtime/audit acceptance remain pending or unrun; release is blocked.
- Review status: Cycle 3 score 9.5/10, 0 critical issues, 3 warnings and 4 suggestions; no tests were run for this plan reconciliation.

## Key Insights
- All12 brainstorm acceptance criteria require API/storage/security plus real Android/desktop proof. Tests and package generation alone cannot pass native/release gates.
- Existing architecture2.2 is proposed design only, not implementation evidence. Update actual architecture and API/storage docs after source integration, retaining current native/release holds until observed.
- App-wide opt-in cuts older clients off online; privacy purge destroys unsent shared edits. The recorded interview confirms these choices and replica-set transactions with the simpler app-wide guard. Consume G0 as completed policy review; prove implementation independently, without silent rollback/downgrade or reopening those choices. Operational rollout authorization remains separate.

## Requirements
- Dedicated disposable-local replica-set feature smoke using creator/Alice/Bob/outsider and a second creator with duplicate IDs. Inspect stored canonical rows/slots, real coordinator/Room/offline state and actual UI/native surfaces.
- Evidence maps all12 criteria, negative/race cases, commands/versions/artifacts/limitations and release decisions. Secret-free output, loopback-only disposable services; no production registration/database changes.
- Implementation docs/changelog match contracts actually delivered; tests remain with module owners and dedicated harness here does not duplicate their tests as copied source/default assertions.
- Acceptance must operationalize all confirmed choices: inspect purge/content-free evidence and no quarantine/replay after regrant; prove missing/unsupported capability1 rejects whole opted-in push/pull/delta before reads/mutations with upgrade required and no private-only downgrade; prove edit/revoke commit order under the single app-wide replica-set transaction guard and record measured contention. Unrelated apps/private rows remain unaffected; no offline remote-erasure claim.

## Architecture
- **Gate status:** G0 policy confirmed. G1 module suites and two QA snapshots are reported passed; the 625/625 and 390/390 counts are separate, not additive. G2 partial (Mongo targets 6/6 and 4/4 passed; dedicated feature smoke/race/contention incomplete). G3 coordinator/Room runtime, G4 connected Android, G5 interactive desktop and G6 security/release audit remain pending/not run. Cycle 3 review 9.5/10, 0 critical, 3 warnings, 4 suggestions; implementation disposition approved with noted issues.
- task-assignees-live-smoke.mjs creates disposable fixture/users via normal local APIs, uses protected environment credentials and canonical storage inspection via secured local DB configuration. No secrets on argv/logs. API and data inspection agree on one creator-owned todos row and one taskAssignees slot, no recipient copies.
- Dedicated Kotlin runtime smoke launches actual coordinator/Room/client through strict JavaExec task in desktopApp build wiring with mandatory fixture/env flags; absence of credentials/runtime fails or explicitly skips, never success. It is not interactive UI substitute.
- reports/04-integrated-acceptance-evidence.md records report-attributed QA results and the exact remaining gates; no unobserved result is marked passed.

## Related code files
Only this phase's assigned files; read-only prerequisite links above are not owned here. New paths are proposed. No deletions planned; obsolete code inside owned files is removed during clean cutover.
- `G:/ws/sharing/doggo/README.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/system-architecture.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/api-contract.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/local-storage-and-sync.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/hub-schema-integration.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/hub-compatibility-acceptance.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/release-acceptance.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/android-acceptance.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/windows-acceptance.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/linux-acceptance.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/security-and-recovery.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/code-standards.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/project-overview-pdr.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/project-changelog.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/development-roadmap.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/project-roadmap.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/docs/codebase-summary.md` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/doggo/desktopApp/build.gradle.kts` — modify; Implementation documentation/evidence and dedicated smoke JavaExec wiring only.
- `G:/ws/sharing/glean-hub/docs/system-architecture.md` — modify; Hub policy/config/API/replica-set/rollout and evidence documentation.
- `G:/ws/sharing/glean-hub/docs/standard-sync-schema-version.md` — modify; Hub policy/config/API/replica-set/rollout and evidence documentation.
- `G:/ws/sharing/glean-hub/docs/deployment-guide.md` — modify; Hub policy/config/API/replica-set/rollout and evidence documentation.
- `G:/ws/sharing/glean-hub/docs/CHANGELOG.md` — modify; Hub policy/config/API/replica-set/rollout and evidence documentation.
- `G:/ws/sharing/doggo/scripts/task-assignees-live-smoke.mjs` — create; Dedicated real feature smoke and redacted evidence, not module-test/source fixes (proposed new path).
- `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt` — create; Dedicated real feature smoke and redacted evidence, not module-test/source fixes (proposed new path).
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/reports/04-integrated-acceptance-evidence.md` — create; Dedicated real feature smoke and redacted evidence, not module-test/source fixes (proposed new path).

## File Ownership
This is the exhaustive exclusive boundary, including tests/config/docs. Purpose/action defined above and in the manifest. Whole-file ownership, never split by sections.
| Full repo-qualified path | Action | Owner |
|---|---|---|
| `G:/ws/sharing/doggo/README.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/system-architecture.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/api-contract.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/local-storage-and-sync.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/hub-schema-integration.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/hub-compatibility-acceptance.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/release-acceptance.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/android-acceptance.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/windows-acceptance.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/linux-acceptance.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/security-and-recovery.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/code-standards.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/project-overview-pdr.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/project-changelog.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/development-roadmap.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/project-roadmap.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/docs/codebase-summary.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/desktopApp/build.gradle.kts` | modify | 09 exclusively |
| `G:/ws/sharing/glean-hub/docs/system-architecture.md` | modify | 09 exclusively |
| `G:/ws/sharing/glean-hub/docs/standard-sync-schema-version.md` | modify | 09 exclusively |
| `G:/ws/sharing/glean-hub/docs/deployment-guide.md` | modify | 09 exclusively |
| `G:/ws/sharing/glean-hub/docs/CHANGELOG.md` | modify | 09 exclusively |
| `G:/ws/sharing/doggo/scripts/task-assignees-live-smoke.mjs` | create (proposed) | 09 exclusively |
| `G:/ws/sharing/doggo/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/task-assignees-runtime-smoke.kt` | create (proposed) | 09 exclusively |
| `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/reports/04-integrated-acceptance-evidence.md` | create (proposed) | 09 exclusively |

## Implementation Steps
1. Collect02–08 terminal handoffs and verify no missing implementation, aliases/stubs, uncovered callers or ownership drift. Use LSP exported-reference review per owner; route missing callers/defects to original phase and update manifest before introducing a genuinely new owned file. Do not edit module source/tests here.
2. Consume the linked 2026-10-21 interview as the completed G0 policy review; carry its three confirmed choices into the acceptance matrix without reasking them or marking runtime proof passed. Separately obtain implementation/operational approval and safe runtime prerequisites: disposable local single-node Mongo replica set/Hub, development-only app registration and current-eligibility creator/Alice/Bob/outsider plus duplicate-owner creator. No production resources or remote/public service binding. Obtain required interactive provider/device approval before UI actions; G0 grants none.
3. Implement dedicated API smoke using existing hub-live-smoke/provision patterns read-only as reference. Register disposable04 schema+policy only after02 enforcement and compatible clients exist. Create root/two children/old rows and duplicate task IDs across owner+table; inspect stored owner tuples/counts before/after all edits to prove zero recipient content copies.
4. Exercise all canonical workflows: assign Alice root/backfill old and future children, assign Bob one child, shared title/deadline/completion/reopen convergence, full-group automatic parent completion versus Bob subset, owner reassign/unassign and overlapping grants/regrant with same live task/slot.
5. Use direct hostile push/pull/delta for forged slot/owner/unknown-field removal/tombstone/restore/child-create/structure, invalid recipient/current app membership, malformed data and wrong/missing/deleted root. Verify no private parent/sibling/other-recipient exposure, no unauthorized writes and explicit typed failures. Prove missing/unsupported capability1 returns426/COLLABORATION_REQUIRED for the whole opted-in request before mutations or content reads, including private-only payloads and both delta parts; no unsafe private-only downgrade. Viewer-only Cham-lang config/unrelated apps remain unchanged without migrating embedded app. Document upgrade required, not remote erasure of offline caches.
6. Run coordinated first-assignment/reassignment CAS race and edit/revoke race across independent service/client actors using the confirmed transaction-capable replica set and single app-wide authorization guard. Assert one slot and both commit-order outcomes using observable transaction barriers/order, not timing hope. Prove unsupported topology fails closed, with no unguarded fallback or owner/tree redesign. Simulate uncertain commit/response loss, confirm no blind POST replay/exact-ack forgery; record measured app-fence contention and failure outcomes for release review, not assumed performance.
7. Force multi-page scans with equal timestamps and duplicate owner/table rowIds. Change grant/root liveness between pages, check batches and final explicit[]; E change must restart unfinished stage. Exercise101+ cached keys bounded into<=100 batches, max body, omitted/repeated/mixed-owner access results, empty key union, disappeared row and membership denial. Partial scans leave previous base/access intact and dispatch paused; authoritative loss still suppresses after later scan failure.
8. Implement strict JavaExec runtime harness against real disposable Hub: populated previous Room versions→5, verified two-stage binding, real HTTP login/lookup, creator/Alice/Bob independent stores, opaque nested JSON/private field retention, exact-ack owner/version collision, offline restart/queued edit/revoke-before-reconnect, denial purge inspection and regrant/no-replay. Inspect all active/review/staging/frozen DB payloads for absence of revoked content/unsent edits; only canonical identity/opId/versions/timestamps/reason may remain as non-replayable evidence, with no recovery quarantine or auto-replay after fresh regrant. Verify unrelated private rows/archives survive. Register proposed `runTaskAssigneesRuntimeSmoke` in the owned desktop Gradle file using test runtimeClasspath and dependsOn(testClasses), mandatory disposable environment and explicit main class in the dedicated Kotlin file; no fallback/skipped success.
9. Have07 execute real connected Android app/widget/alarm sequence and08 execute actual desktop main/sticky/native reminder sequence, with screenshots/video/action logs tied to account/task refs but without credentials. Prove ChildOnly editing without parent locally, creator-only control denial via stale native actions, offline restart/reconnect, cancel reminders/posted notifications, refresh widgets/sticky and account-switch fencing. Missing approval/device/native support is a hold, not passed/skipped acceptance.
10. **Validation commands and status as of 2026-10-22:** use the plan's protected disposable environment and never treat a command as passed unless its report provides the result.
   - Reported executed: `./gradlew :shared:jvmTest` (two separate QA snapshots); `./gradlew :desktopApp:test -PdesktopTarget=windows` (earlier regression QA); `cargo test -p glean-hub-sync --features mongo-integration-tests --test schema_version_integration --test task_assignment_integration` (selected QA targets 4/4 and 6/6, authorized disposable Mongo); `pnpm --dir glean-hub-app test:unit` (selected QA 51/51); earlier QA also reports 6 sync-engine, 28 sync-library, 60 Android unit, and 12 provisioner Node tests passing.
   - Not recorded as run: `./gradlew :shared:connectedDebugAndroidTest :androidApp:connectedDebugAndroidTest`; `node scripts/release-security-check.mjs` with real required artifacts; full `cargo test -p glean-hub-sync-engine -p glean-hub-sync-client`; `node scripts/task-assignees-live-smoke.mjs`; `./gradlew :desktopApp:runTaskAssigneesRuntimeSmoke -PdesktopTarget=windows` (or Linux on Linux). See [QA 625/625](../../reports/qa-261021-2225-critical-fix-regression-tests.md) and [selected QA 390/390](../../reports/qa-261022-1521-selected-test-suite-results.md). These results do not include a full release-security audit.
11. Update owned README/API/local-storage/schema/architecture/PDR/standards/roadmaps/changelog and platform/release acceptance docs. Clearly separate legacy default wire/ack/cursor behavior from opted-in version1; document live-root checks, fence+revision protocol, Room5 binding, privacy loss, cutoff, no remote offline erasure and separately authorized quiesced policy changes. Update Hub docs consistently, no strict service references as new design.
12. Produce redacted reports/04 evidence mapping every03 traceability row to observed test/API/storage/native artifact or explicit hold. Post-implementation architecture review: intended drift requires coordinated design revision, unintended drift returns to original owner. Release stays blocked until all required gates truly pass; production approval remains a separate explicit hold.

## Todo list
- [x] Deliver the dedicated smoke harness wiring, evidence/documentation artifacts and current report-attributed gate status; do not claim unrun checks.
- [x] Reconcile all 12 criteria to implemented contracts and available module/integration test evidence.
- [ ] Complete the dedicated feature smoke, populated Room/coordinator runtime, connected Android and interactive desktop acceptance, and G6 security/release audit before release; record exact evidence and authorization separately.
- [x] Hand off the complete implementation and the remaining acceptance/release holds to Main.

## Success Criteria
- **G0 CONFIRMED 2026-10-21.** G1 module suites and construction checks are report-attributed as passed; G2 has partial disposable Mongo evidence (6 assignment-integration and 4 schema-version tests), not full hostile/race/pagination/contention acceptance. Cycle 3 review: 9.5/10, 0 critical, 3 warnings and 4 suggestions.
- **Release acceptance not complete:** all 12 criteria require real canonical-row/no-copy evidence, race outcomes, paginated access checks, lossless Room migration, privacy purge, and native child-only/offline/surface/reminder proof. Missing evidence is itemized in [03-acceptance-traceability.md](reports/03-acceptance-traceability.md); G2 is partial, G3–G6 remain open.
- Native tests/headless coordinator harness do not substitute actual Android+desktop UI. G4/G5 fail/hold on provider/device absence. Existing unrelated release holds and production authorization remain independently tracked.
- Docs/changelog accurately describe implemented behavior and observed limitations;09 modifies no earlier phase source/test file.

## Conflict Prevention
- Only files in this phase's manifest may be changed by its owner. Shared large files have one owner; no split-by-section exceptions.
- Contract change discovered: contact01/main before editing; genuine new file: revise ownership manifest and dependency handoff first. Integration bug: original file owner fixes it;09 never takes source/module-test ownership.
- Each owner uses LSP references for exported changes and migrates all assigned callers. Independent construction is not runtime independence. Main schedules authorized checks once after relevant owners finish; no parallel build/lint/test/formatter storms.

## Risk Assessment
- Replica-set or runtime approval may be unavailable; complete runnable harness/docs and label exact remaining proof gate, never downgrade acceptance to tests only. App guard contention must be measured before release; unacceptable results require design revision, not weakened authorization.
- Native OS cancellation may leave pixels; disclose actual outcome. Privacy-first unsent edit loss, whole-app old-client cutoff and the app-wide transaction guard are confirmed policy choices, not outstanding review questions. Actual implementation proof, interactive approval and operational rollout/production authorization remain separate pending gates.

## Security Considerations
- Use only disposable loopback infrastructure and test identities. Store env credentials privately, redact JWT/API/DB secrets and task content where revoked. Do not run production, accept legal/provider approvals automatically, change app eligibility globally or create a new notification/invitation service.

## Next steps
Main reviews evidence and all remaining release holds with user. Production rollout/configuration/migration is never authorized by this plan. Unmet native gates must remain visible; implementation defects return to their exclusive owner.

Unresolved questions: none blocking design; policy choices confirmed by the recorded 2026-10-21 interview. Implementation approval/review, transaction-capable disposable services, native runtime access/interactive approval and operational rollout/production authorization remain separate pending prerequisites.
