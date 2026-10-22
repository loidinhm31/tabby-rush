# Planner handoff: collaborative task assignees
Date: 2026-10-21. Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`.
Branch observed from parent evidence: `feature/parent-child-tasks-deadline-surfaces`.
Deliverable: planning artifacts only; no implementation authorization, source/config/runtime changes or runtime proof.

## Inputs and provenance
- Read `G:/ws/sharing/doggo/README.md` first; [brainstorm](../../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md), [00 source evidence](00-context-and-source-evidence.md), [01 frozen contracts](01-frozen-design-contracts.md), and proposed `docs/system-architecture.md` §2.2.
- Read BOTH verified terminal researchers: [server identity/authorization](../research/researcher-01-server-identity-authorization.md) and [native sync/access lifecycle](../research/researcher-02-native-sync-access-lifecycle.md). Their recommendations are not source/runtime proof. Frozen contracts supersede illustrative config names, mutationId receipts, tree fences, event/snapshot services and legacy private-only downgrade alternatives.
- Parent already read required codebase-summary, full code standards, architecture and PDR. Planner additionally read relevant code standards and targeted current source/build/package sections to assign exact exclusive files/commands. Source/evidence report remains read-only.
- Conditional scout skipped: codebase-summary generated2026-10-20, less than3 days old. No scout executed in this planning run; no scout report/path invented. Existing empty scout directory is not evidence.
- Local `.omp` absent and optional `docs/development-rules.md` absent per evidence/glob. Loaded published planning SKILL and organization/output/solution-design/architecture/codebase references, published AGENTS and primary/development/orchestration/documentation workflows. Catalog reviewed; planning and Mermaid skill instructions used directly. No additional researchers/subagents spawned.
- Parent completed architecture-first design gate and updated architecture2.2 as proposed design; planner made no docs edits. Implementation update ownership remains09.
- Parent activation-helper attempt reported EVCRATE_SESSION_ID unset; activation did not persist. Explicit required plan path retained throughout; no guessed new plan folder or repeated setup.

## Deliverable summary
- Nine detailed phases, pending implementation/implementation review,0% progress; policy validation confirmed2026-10-21; total **284h** planning estimate, no fixed deadline.
- Conservative waves01 → [02,03,04] → [05,06,07,08] →09, with true construction and additional runtime dependencies separately listed.
- Exhaustive manifest: **219 exclusive future file assignments** including source/tests/config/docs; every path has action/purpose, proposed new files explicitly marked. Whole files owned once; no source wildcard/split ownership. Room builders/DAO/entities/migrations03; coordinator/network/outbox05; shared UI06; Android07; desktop08; module tests stay with respective owner.
-01 owns actual DTO/shared contract/pure projection files plus Rust client literals/export and generated TypeScript source/client.02 owns plugin Rust literal consumers and feature-gated Mongo integration target config.04 owns Hub admin types/pages plus ordinary native provisioning/schema.
-09 owns dedicated e2e smoke, desktop Gradle smoke task wiring, implementation docs/changelog and future redacted evidence; integration defects return to original owner, never09 takeover of earlier source/tests.
- [Acceptance traceability](03-acceptance-traceability.md) covers all12 numbered brainstorm criteria with permanent behavioral boundaries, real API/storage/native evidence, negative cases and explicit release gates.

## Contract clarifications carried through
1. All assignee child access, including DIRECT grant, and child assignment creation require internally live same-owner ROOT on server. Bob lacks PRIVATE parent locally only; invalid server orphan grants neither direct nor inherited access. Creator may retain orphan evidence for repair/deletion.02/03/05 and traceability reflect this.
2. accessChecks absent selects data scan; present[] selects final validation-only mode. Kotlin/Rust/TS definitions preserve presence; no serializer drops[] as absent. Checks use<=100 keys/256KiB and final[] atE even with empty union. Scan mode/table filters cannot accidentally leak into validation mode defaults.
3. Room two-stage binding: SQL migration preserves LOCAL_ACCOUNT unresolved rows without guessing owner from one-way hash. Initialization verifies restored/authenticated origin/user matches partition, binds dependencies atomically before shared/sync actions. Offline private display survives; unresolved state is local-only, never fake/default TaskRef authority.
4. Existing persisted schedule projector currently imports concrete entities.01 converts input to immutable contract snapshots;03 supplies real entity adapters.05 consumes storage interfaces, not DAO internals. No Room/Ktor leaks in new domain interfaces, compatibility String aliases, temporary no-op bridges or new protocol receipts.
5. Stable assignment slot rowId=data.id=taskId uses existing owner/table/row unique index; preserve tombstones through cleanup. One internal app fence serializes opted-in writes and visibility revision. Membership checks current stored eligibility, not stale JWT claims; no new membership auto-grant/admin subsystem.
6. Unknown commit remains uncertain; exact owner/table/row/submittedVersion confirms frozen local opId version only. Accepted delta push survives failed/stale pull. Content-free access-loss evidence cannot replay after regrant; no new mutationId wire protocol.

## Future verification and implementation prerequisites
All future commands/tests/API/UI actions in phases and acceptance map are **NOT RUN** here.
- Disposable loopback Hub + transaction-capable single-node Mongo replica set, development registration and protected environment credentials. Tests use existing mongo-integration-tests opt-in/allowlist; no default database connection or production access.
- Creator/Alice/Bob/outsider and duplicate-owner identity fixtures; direct canonical Mongo row/owner/count inspection proving no recipient copies; real CAS/edit-revoke/stable-revision pagination races and final explicit[] checks.
- Actual populated SQLite old versions→5 on JVM/Android and verified AccountIdentity restoration; real coordinator/HTTP/offline restart, unknown JSON/exact ack/account switch and revoked payload purge inspection.
- Connected Android and actual supported desktop host/session with live provider approval. ChildOnly editing, offline restart/reconnect, widgets/sticky/reminder/native notification cancellation require actual UI/native evidence, not tests/headless JavaExec alone.
- Planned dedicated task `:desktopApp:runTaskAssigneesRuntimeSmoke`, test runtimeClasspath+testClasses and mandatory disposable env, plus `scripts/task-assignees-live-smoke.mjs`; not created/executed by this planning task.
- Main owns final on-disk document review, implementation integration and authorized verification once owners finish. No build/lint/test/formatter/API/UI action or production change run by planner.

## Confirmed policy tradeoffs and remaining holds
- User confirmed privacy-first purge2026-10-21: revoked remote payloads and unsent shared edits leave active/review storage. Generic notice and identity/opId/versions/timestamps/reason only; no automatic replay/recovery or quarantine. Intentional unsent-content loss is accepted, not awaiting policy review.
- User confirmed whole-app capability cutoff2026-10-21: opted-in requests from older clients fail BEFORE sync mutation/read; upgrade required. No schemaVersion gate, unsafe private-only downgrade or silent disable with live assignments. Operational enablement still needs separate authorization.
- User confirmed the simpler app-wide guard with replica-set transactions2026-10-21. Retain this design; prove edit/revoke ordering and measure contention in future acceptance, not an unapproved owner/tree-guard redesign. [Interview record](06-validation-interview.md).
- Disconnected remote erasure impossible. Existing release/native acceptance holds remain. Production authorization and separately authorized quiesced compatibility procedure are not granted by plan or local proof.

## Outputs — exhaustive new files
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/plan.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/evc-cmd-plan.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-01-canonical-contracts-and-pure-projections.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-02-hub-authorization-and-reconciliation.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-03-room-v5-and-access-persistence.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-04-admin-policy-and-native-provisioning.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-05-native-transport-and-sync-lifecycle.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-06-shared-compose-assignee-workflows.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-07-android-actions-and-access-surfaces.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-08-desktop-actions-and-sticky-surfaces.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/phase-09-integrated-acceptance-and-documentation.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/file-ownership.json`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/reports/02-planner-handoff.md`
- `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees/reports/03-acceptance-traceability.md`

## Document-only validation signal
Document-only structure checks:9 phases;219 unique case-normalized full paths, no duplicate ownership;effort sum284h;overviews under80 lines;required phase sections ordered;dependencies acyclic;all12 criteria present;manifest JSON valid. Current on-disk validation is maintained in [parent evidence](05-plan-validation.md); these checks do not prove implementation behavior.
These are document checks, NOT source compilation, tests, migration/API/UI proof or release acceptance. No implementation/runtime verification run. No missing prerequisite blocks this planning deliverable.

## Unresolved questions
None blocking technical design. Policy choices are confirmed. Disposable transaction-capable services, native runtimes/interactive approvals and separate production authorization remain explicit later gates, not silently deferred implementation choices.
