# Acceptance traceability: all12 brainstorm criteria
Date: 2026-10-22. Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`.
Status: **Implementation complete and approved with noted issues** per user-provided disposition. Release acceptance remains incomplete; implementation approval does not close runtime or production gates.
Authority: [brainstorm §7](../../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md#7-acceptance-and-validation-criteria), [frozen contracts](01-frozen-design-contracts.md), [manifest](../file-ownership.json), [Phase 09 integrated evidence](04-integrated-acceptance-evidence.md).
All test/source references remain read-only traceability to the exclusive owner in the manifest. Results below are attributed to cited reports; no tests were run during this PM documentation update.

## Release gates and reported evidence
| Gate | Required evidence | Current recorded status |
|---|---|---|
| G0 | Confirm privacy-first revoked-edit purge, whole-app old-client cutoff and simpler replica-set/app-wide guard | **CONFIRMED 2026-10-21**; [policy record](06-validation-interview.md). |
| G1 | Integrated construction and meaningful module behavior/security/migration suites | **Reported automated suites passed:** earlier regression QA 625/625 and later selected QA 390/390 are separate report snapshots, not additive; Cycle 3 review 9.5/10, 0 critical. See [625/625 QA](../../reports/qa-261021-2225-critical-fix-regression-tests.md), [selected QA](../../reports/qa-261022-1521-selected-test-suite-results.md), [Cycle 3 review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md). These do not constitute every platform/runtime gate. |
| G2 | Disposable replica-set Hub hostile API/race/pagination proof and canonical Mongo inspection | **PARTIAL:** 6/6 `task_assignment_integration` and 4/4 `schema_version_integration` tests passed against the authorized disposable integration endpoint ([selected QA](../../reports/qa-261022-1521-selected-test-suite-results.md)). Dedicated feature API smoke, full hostile/race/pagination scenarios and measured guard contention are not evidenced. |
| G3 | Populated SQLite v1→v5, binding, actual coordinator/HTTP/offline restart and purge inspection | **NOT RUN:** dedicated `runTaskAssigneesRuntimeSmoke` / actual coordinator proof remains pending. Reported JVM migration tests do not substitute for this gate. |
| G4 | Actual Android editor/list/Glance/alarms/notifications child-only/offline/revoke/account-switch behavior | **NOT RUN:** connected-device and provider-approved acceptance pending; unit results are not device evidence. |
| G5 | Actual desktop main/sticky/native reminder behavior on supported OS | **NOT RUN:** interactive Windows/Linux host acceptance pending. |
| G6 | Documentation, security and release audit with secret-free evidence and no unobserved gate marked passed | **NOT RUN / pending.** Production authorization and separate existing release holds remain independent. |

Production authorization, native/provider interactive approval and existing release holds remain separate. Disposable development evidence does not authorize production policy toggles, deployment or migration.


## Criterion1 — Root assignment, existing and future children
**Brainstorm:** creator assigns Alice root; Alice receives existing root/children and creator-added future children.
- Owners:01 canonical/coverage projection;02 visibility/slot enforcement;03 relationship persistence;04 fixture;05 null scan/backfill;06 creator UI;07/08 native routing;09 integration proof.
- Permanent behavior:02 `task_assignment_integration.rs` covers computed inherited grant/backfill/future child;03 `collaborative-access-sqlite-test.kt` covers raw relation and atomic visible group;05 `collaboration-reconciliation-test.kt` covers old timestamps/new grant.
- Runtime evidence:09 API smoke creates root+two existing children before grant, assigns Alice, syncs actual independent accounts, adds child afterward. Capture canonical owner/ref/version and Android/desktop visible full group. Inspect no materialized child relationship grants.
- Negatives: outsider sees nothing; assignment does not grant app eligibility; first relation waits for acknowledged owner create; invalid root never grants child access. Missing-capability whole app request rejected before mutation.
- **Reported implementation evidence:** Cycle 3 review reports canonical-access coverage in shared JVM tests (179/179); the selected QA report records 6/6 Hub assignment integration tests. Neither report is a per-scenario record of the dedicated creator/Alice future-child smoke.
- **Runtime acceptance:** independent-account root/backfill/future-child flow and Android/desktop projection remain unproven; G2 is partial and G3–G5 remain open.

## Criterion2 — Shared deadline/completion, single canonical content
**Brainstorm:** Alice and creator change due date and complete/reopen; independent accounts converge on same canonical row without recipient-owned duplicates.
- Owners:01 identities;02 canonical resolution/CAS;03 keying/intent;05 exact ack/merge;06 controls;07/08 actions;09 row inspection.
- Permanent behavior:02 integration suite asserts stored owner+row count after assignee edit;03 SQLite collision/ack tests;05 outbox reconciliation and JSON merge tests preserve explicit masks/unknown JSON and exact tuple acknowledgement.
- Runtime evidence: alternate creator/Alice title/deadline/completed/reopened edits through real clients; read canonical Mongo rows before/after. Assert precisely one creator-owned todos row per canonical task, zero Alice-owned copies, observed versions converge. Include equal rowId under another owner and same rowId relation table.
- Negatives: missing/forged owner cannot select another row, stale CAS is conflict, accepted count/pulled content/wrong-owner tuple cannot acknowledge intent, newer local version not cleared by earlier ack.
- **Reported implementation evidence:** shared JVM canonical-access/ack coverage and the 6/6 Hub assignment integration target are reported passed; see [Cycle 3 review](../../reports/code-review-261022-1649-collaborative-assignees-cycle3.md) and [selected QA](../../reports/qa-261022-1521-selected-test-suite-results.md). Criterion-specific independent-client convergence and database row-count capture are not supplied.
- **Runtime acceptance:** full creator/Alice edits and canonical-row inspection remain unproven; G2 partial, G3–G5 open.

## Criterion3 — Child-only Bob privacy and native edit
**Brainstorm:** Bob edits assigned child without parent/sibling exposure; Alice retains inherited edit; child display/edit works Android+desktop.
- Owners:01 pure partial projection;02 live-root authorization/privacy;03 missing-local-parent edit;05 access metadata;06 UI;07/08 real surfaces;09 evidence.
- Permanent behavior:01 canonical task-access/hierarchy tests render real ChildOnly item;02 integration suite verifies only child content/effective metadata;03 SQLite suite edits with no local parent;07/08 surface suites reject wrong canonical target.
- Runtime evidence: parent+siblings private to creator/Alice, Bob receives one real child, parent absent from Bob Room and response bodies. Edit same child from Bob Android and desktop; Alice still edits inherited child. Capture app and widget/sticky states without synthetic root/context/title.
- Negatives: no hidden parent fetch/title/sibling IDs/counts or other recipients relation rows. Direct child grant requires live same-owner root INTERNALLY; missing/deleted/wrong-owner server parent confers neither direct nor inherited grant. Local absence due privacy is supported, server orphan is not.
- **Reported implementation evidence:** Cycle 3 review reports Android unit 40/40 and desktop unit 95/95; these are automated tests, not observed Bob response bodies or actual native UI.
- **Runtime acceptance:** child-only privacy and edit journeys on Android/desktop remain unproven; G2 partial, G3–G5 open.

## Criterion4 — Full-group-only parent completion
**Brainstorm:** parent completion uses actual group children, never Bob visible subset; existing full-group behavior remains.
- Owners:01 grouping/coverage;03 completion atomic operation;05 reconciled boundary;06 display;07/08 action paths.
- Permanent behavior:01 hierarchy tests distinguish FullGroup/ChildOnly/staged;03 SQLite tests prove Bob child completion never queues parent mutation and creator/Alice full-group behavior remains;05 unstable E cannot publish complete coverage.
- Runtime evidence: Bob completes his only visible child while another private sibling remains incomplete; inspect parent untouched. Creator/Alice complete full group, then reopen child; inspect expected parent behavior. Add future child after boundary and observe next null-scan behavior, without claiming eternal group snapshot.
- Negatives: incomplete/staged pages, unreconciled hierarchy, missing/invalid root, owner-ID collision or partial subset never trigger automatic parent writes.
- **Reported implementation evidence:** shared JVM behavior and Hub assignment-integration suites are reported passed; the reports do not document the full Bob-subset versus creator/Alice complete-group runtime sequence.
- **Runtime acceptance:** full-group-only completion across independent accounts remains unproven; G2 partial, G3–G5 open.

## Criterion5 — Creator-only privileged operations on every path
**Brainstorm:** assignees cannot manage assignments/delete/restore/create children/ownership/parent linkage via API/tombstones/delta/widgets/stale clients.
- Owners:02 authoritative enforcement;03 local mutation admission;05 dispatch/conflict review;06 controls;07 Android actions;08 desktop actions.
- Permanent behavior:02 real integration matrix covers forged relation, assignment deletion, task tombstone/restore, malformed/empty data, changed/removed immutable/unknown JSON, child create/no-reparent and delta.03 tests typed AccessDenied;07/08 stale/direct actions re-resolve rights twice.
- Runtime evidence:09 hostile standard API requests as Alice/Bob/outsider; repeat stale widget/sticky/notification actions and prior role UI states. Verify no stored mutation. Capability-absent entire opted-in push/pull/delta fails426 COLLABORATION_REQUIRED before any record processed; do not attempt unsafe private-only downgrade.
- Negatives: fake owner/current-user fallback, self-grant, restored deleted row, empty-data tombstone bypass, conflict reapply/copy-latest on revoked payload, old intent missing owner all fail closed.
- Error distinction: still-readable assignee privileged-action denial is OPERATION_FORBIDDEN and preserves valid content access; ACCESS_DENIED is reserved for effective read-access loss and triggers purge.
- **Reported implementation evidence:** Hub assignment-integration tests (6/6) and platform unit suites (Android 40/40, desktop 95/95 per Cycle 3 review) are reported passed. Dedicated hostile API and stale native-action smoke has not been evidenced.
- **Runtime acceptance:** complete standard API/tombstone/delta hostile matrix and stale widget/sticky/notification actions remain unproven; G2 partial, G4/G5 open.

## Criterion6 — Explicit conflicts and unique concurrent reassignment
**Brainstorm:** creator/assignee edit conflicts surface; concurrent reassignment leaves at most one direct assignee with no silent escalation.
- Owners:02 exact CAS/stable slot/fence;03 frozen intent/version;05 exact ack/conflict review;06 conflict UI;07/08 controllers.
- Permanent behavior:02 replica-set tests coordinate first-slot insert/reassign/unassign/regrant races using existing unique owner/table/row index;03/05 preserve local opId/submittedVersion and newer local edit;06 meaningful slot/content conflict feedback.
- Runtime evidence: two owner devices concurrently choose Alice/Bob from same slot version; one succeeds/other conflicts, stored slot count1 including tombstones. Creator/Alice edit same content version, conflict review explicitly resolves supported intent. Race edit versus revoke across independent service processes and observe commit order; record app-fence contention.
- Negatives: CAS miss never upserts; cleanup cannot purge slot tombstone and permit stale recreate; aggregate count/pull-match is not receipt; unknown commit remains uncertain with no blind POST replay or new mutationId service.
- **Reported implementation evidence:** Hub integration target passed 6/6; however, Cycle 3 review explicitly retains a warning that concurrent insert-collision recovery lacks an automated integration test. Do not claim this race case verified.
- **Runtime acceptance:** coordinated reassignment/edit-revoke races and measured app-fence contention remain unproven; G2 partial.

## Criterion7 — Backfill, revocation, partial-scan safety and no replay
**Brainstorm:** grant backfills old rows; reconciled revocation removes inaccessible tasks/reminders and queued replay; failed/partial scan not access loss.
- Owners:02 stable revision/check mode;03 staging/promotion/purge/evidence/ledger;05 round state machine;07/08 native cancellation;09 races/runtime proof.
- Permanent behavior:02 tests page snapshot E, current membership and bounded access checks;03 atomic promotion and purge with failed later scan;05 absent-vs[] serialization, exact coverage, account/E/mode rejection and reconcile-before-freeze;07/08 admission/pre-publication races.
- Runtime evidence: >100 identities force multiple check batches, old timestamps backfill from null scan. Revoke while recipient offline with pending content/draft/frozen envelope, restart/reconnect. Inspect active+review+staging DB for absence of revoked JSON/title/deadline/recipient and non-replayable identity/opId/versions/timestamps/reason only. Observe native alarms/notifications canceled, widget/sticky refreshed. Later regrant sends no discarded intent.
- Negatives: change E between pages, between access batches and immediately before final explicit accessChecks=[]; restart staging, leave previous active base/access and outgoing shared pause. Missing/repeated/mixed-owner checks/mixed scan mode/over100/over256KiB rejected. A network failure or disappeared row is not tombstone. Authoritative unavailable/ACCESS_DENIED/membership denial suppresses even if later content scan fails. Empty union still sends explicit[]; never omitted.
- Delay an authorized pull/native publication, reconcile revocation, then deliver the delayed result before and after regrant within the same account. Persisted local accessGeneration must reject the old content; late exact-ack metadata cannot resurrect purged payloads/intents.
- **Reported implementation evidence:** selected shared JVM suite 329/329 and Hub integration target 6/6 are reported passed. Reports do not supply full runtime evidence for revoked-payload inspection, reminder cancellation, restart or regrant/no-replay.
- **Runtime acceptance:** offline revoke/restart/reconnect behavior and native cancellation remain unproven; G2 partial, G3–G5 open.

## Criterion8 — Grant overlap and same-record regrant
**Brainstorm:** removing parent or direct child grant preserves other effective grant; regrant restores same live record.
- Owners:02 access union/slot lifecycle;03 available/unavailable distinction;05 fresh hydration;06/07/08 projection refresh.
- Permanent behavior:02 overlap removal in either order and live-root checks;03 regrant hydrates original owner/task without deleted=true and never replays content-free evidence;05 next null scan discovers regrant.
- Runtime evidence: Alice parent+direct child overlap, remove each source separately; Bob direct child survives another actor parent-revoke. Remove all effective access then regrant same slot/task; compare stored keys/versions and native visible content. Stable slot survives cleanup window and stale owner regrant loses CAS.
- Negatives: effective union only, not whichever grant last processed; no copied recipient row, permanent local deleted marker, stale unsent edit resurrection or direct orphan access after root deletion. Root restoration permits rights only once current server state validates again.
- **Reported implementation evidence:** shared JVM and Hub integration suites are reported passed, but no scenario-level evidence in those reports establishes every overlapping grant and same-record regrant path.
- **Runtime acceptance:** independent-account grant overlap, revoke and fresh regrant remain unproven; G2 partial, G3–G5 open.

## Criterion9 — Forgery/collision/orphan/recipient integrity
**Brainstorm:** forged relations, duplicate-owner IDs, missing/deleted parents and invalid recipients cannot grant access or mutate another owner row.
- Owners:01 canonical identity;02 source-of-truth validation/current eligibility;03 keys;05 response validation;07/08 action identity.
- Permanent behavior:02 integration security matrix includes owner spoof, cross-owner relation target, wrong/missing/deleted/non-root parent, assignee/outsider relationship mutations, recipient nonexistent or not app-eligible, stale JWT membership;01/03/05 owner+table collisions and page/cursor behavior;07/08 structured action/tag collisions.
- Runtime evidence: duplicate IDs intentionally created for two owners and todos/taskAssignees at equal timestamps; inspect queries/stored rows and all-account pulls. Server independently looks up current stored eligibility despite valid lookup/stale JWT. Internally verify live same-owner root for direct AND inherited child grants; retain creator orphan evidence only for repair/deletion.
- Negatives: no global rowId lookup, UUID-probability authorization, ambiguous missing owner fallback, lookup-triggered app access grant, private root disclosure or duplicate check-result substitution.
- **Reported implementation evidence:** the 6/6 Hub assignment integration target passed against a disposable endpoint. The report does not itemize execution of every forged-owner, duplicate-ID, orphan-root and recipient-integrity case in this criterion.
- **Runtime acceptance:** full hostile API/collision inspection remains unproven; G2 partial, G3–G5 open.

## Criterion10 — Lossless upgrade, unknown JSON and account fencing
**Brainstorm:** private tasks survive Room/schema upgrade unassigned; unknown JSON survives supported edits; account switch fences async results.
- Owners:01 migration/identity contract;03 migrations/builders/SQLite;04 additive schema;05 merge/lookup/sync fencing;06/07/08 selection/lifecycle.
- Permanent behavior:03 populated SQLite versions1/2/3/4→5, lossless v3→4 private legacy evidence, base/draft/outbox/ledger preservation, atomic owner binding mismatch/rollback/restart;05 unknown nested/null JSON merge and exact ack;07/08 old-generation action/publication rejection.
- Runtime evidence: existing private old DB offline opens with no assignment; explicit LOCAL_ACCOUNT unresolved binding remains local-only until restored AccountIdentity verifies AccountKey.create(origin,userId). Inspect no guessed/default owner/no hash reversal, then bind all dependencies atomically before outgoing/shared actions. Upgrade real Android and desktop SQLite; switch account mid lookup/sync/reminder/publication; inspect partitions and retained private rows.
- Negatives: old exported schemas1–4 unchanged; no destructive reset, inferred assignee or ownerHash as user ID. Unresolved legacy display is not shared/sync authority. Invalid identity binds nothing; incoming stale results cannot enter new account. Revoked shared purge does not erase unrelated owner-private tasks/private legacy archive.
- **Reported implementation evidence:** selected shared JVM suite 329/329 passed; Cycle 3 review specifically reports shared JVM migration/access coverage. This does not prove actual desktop/Android account-switch or end-to-end restored-identity behavior.
- **Runtime acceptance:** populated legacy account binding and actual runtime fencing remain unproven; G3–G5 open.

## Criterion11 — Other apps remain viewer-only and unchanged
**Brainstorm:** existing Cham-lang config viewer-only; unrelated app sync unchanged.
- Owners:01 optional/omitted DTO/config defaults;02 legacy behavior/equality/cleanup isolation;04 admin omitted policy;05 native private legacy mode;09 compatibility evidence.
- Permanent behavior:02 existing viewer sharing/schema-generation tests plus assignment-policy-absent regressions verify reads and denied edits; policy equality/no-op/CAS and guard untouched for unrelated apps.04 round-trip omitted policy.05 old/non-enabled server mode never converts existing shared cache to private.
- Runtime evidence: disposable app using existing Cham-lang-style viewer mapping plus unrelated private app; standard legacy envelopes/viewer edits behave as before. No embedded Cham-lang model/code/data migration. Compare legacy cursor and optional wire omissions; assignment-policy app alone rejects old clients.
- Negatives: no app-ID branch, schemaVersion gate, global transaction-fence dependency for unrelated apps, speculative new roles or silent policy-disable downgrade with live assignments.
- **Reported implementation evidence:** Hub schema-version integration target passed 4/4 and the admin unit suite passed 51/51. These are not a live unrelated-app compatibility rollout.
- **Runtime acceptance:** external viewer-only app compatibility and whole-app cutoff acceptance remain unproven; G2/G6 incomplete.

## Criterion12 — Real disposable Hub and native UI proof before release
**Brainstorm:** local creator/Alice/Bob/outsider smoke, canonical writes/revoke/conflicts, actual Android+desktop ChildOnly/offline reconnect.
- Owners:09 dedicated live/runtime harness/evidence/docs;02–08 retain module source/test ownership and execute their platform tasks when explicitly assigned. Main integrates and schedules verification.
- Required artifacts: exact service/client versions/commands; disposable replica-set topology; redacted identity/ref/row count assertions; race chronology/access revision pages and check batches; real SQLite migration/purge inspection; Android editor+Glance+reminder captures; desktop main+sticky+native reminder captures; account-generation and offline restart evidence; failures/holds recorded.
- Actual feature smoke, not tests alone: `scripts/task-assignees-live-smoke.mjs` and dedicated Kotlin actual-coordinator JavaExec harness, then provider-approved Android and desktop user journeys. Environment/credentials mandatory; missing prereq is hold/failure, never vacuous success. Package/model unit tests do not establish UI, tray/toast/Glance or connected sync proof.
- Negatives: do not claim source inspection/research as runtime verification; do not fabricate screenshots/outputs, auto-approve providers, expose services publicly, use production data or infer production authorization from local success.
- **Reported implementation evidence:** Phase 09 harness wiring/docs are delivered; the selected QA run includes the 6+4 disposable Mongo integration targets. Dedicated live feature smoke, actual coordinator run, connected Android, interactive desktop and G6 audit are not recorded as run.
- **Runtime acceptance:** criterion 12 remains **NOT PASSED**; release stays blocked pending G2–G6 evidence and separate production authorization.

## Evidence handoff and source boundaries

- Module owners retain source/test ownership. Automated results above are report-attributed and must not be restated as unobserved runtime proof.
- G2 partial integration tests do not close the dedicated API smoke, collision-recovery, full pagination/race or contention acceptance.
- G3/G4/G5/G6 remain open as listed above; no production actions are authorized by this plan.

## Unresolved questions

None blocking implementation approval. Open operational prerequisites: dedicated G2 feature smoke and remaining race/pagination evidence, G3 runtime harness, connected Android/provider approval, interactive supported desktop sessions, G6 security/release audit, separately authorized production rollout, and rotation of the disposable Mongo test credential disclosed in command text per [selected QA](../../reports/qa-261022-1521-selected-test-suite-results.md).
