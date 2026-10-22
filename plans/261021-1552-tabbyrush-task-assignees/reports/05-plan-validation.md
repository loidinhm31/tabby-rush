# Parent plan validation

Date: 2026-10-21. Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`.
Scope: document validation only. No source implementation, application build/test, migration, Hub API smoke, native UI check or production action performed.

## Verified artifacts
- [Main plan](../plan.md): nine pending phases; YAML frontmatter complete; observed branch retained; total effort 284h equals the phase sum. Estimate only, not a delivery commitment.
- [Command overview](../evc-cmd-plan.md): all phases linked with pending status, progress and parallel group.
- [Ownership manifest](../file-ownership.json): 219 unique case-normalized full file paths; no cross-phase overlap or wildcard ownership. Every existing modify target exists; proposed create paths do not yet exist.
- Nine phase documents: all 15 required sections present in order. Related code files and File Ownership lists match their manifest entries exactly; no missing/extra paths.
- Dependency graph/manifest acyclic. Conservative waves 01 → [02,03,04] → [05,06,07,08] →09; construction dependencies distinguished from runtime integration gates.
- Overview lengths: main plan initially67 lines,79 after interview summary; command overview23 lines, both under80. Research reports100 and84 lines, both under150.
- Local Markdown links checked on disk; initial complete pass resolved151 links with zero errors/warnings. Final pass includes validation/interview artifacts and later clarifications.
- [Acceptance traceability](03-acceptance-traceability.md) contains criteria1–12 exactly once, each with owners, meaningful behavioral/negative checks and required runtime evidence.
- Manual review covered all nine phases, technical design, dependency/ownership boundaries, privacy/compatibility tradeoffs and future real smoke requirements.

## Parent review corrections incorporated
- A server-invalid child parent cannot confer direct OR inherited access. A private parent missing only from Bob's local cache is supported and not a server orphan.
- Final validation-only accessChecks=[] is explicit and distinct from absent field; no default todos filter can turn it into mixed scan mode.
- ACCESS_DENIED indicates effective read loss and permits purge; OPERATION_FORBIDDEN rejects privileged action/field while preserving legitimate read/content access.
- A persisted local accessGeneration fences delayed content/native publication within the same account through revoke/regrant. Late accepted-version metadata cannot resurrect purged payloads/intents.
- Exact acknowledgements retain submitted-version semantics, no mutationId/operation-receipt protocol. Stable slot relies on existing unique owner/table/row index plus rowId=taskId; tombstone cleanup cannot reopen a stale grant.
- Room5 explicitly preserves unresolved LOCAL_ACCOUNT rows until verified account binding; no reversed partition hash or fake owner. Module source/tests remain with their original exclusive phase owner during integration fixes.

## Research and workflow limitations
- Both requested researchers completed and artifacts verified. Native researcher reported5 tool calls. Server researcher used5 top-level invocations but6 underlying tool calls, exceeding the requested five-call ceiling by one; this is a workflow deviation, not hidden research/runtime evidence.
- Fresh codebase summary (generated2026-10-20) satisfied conditional rule: no scout run or scout report fabricated. The reserved scout directory remains empty.
- Published plan-activation script ran but EVCRATE_SESSION_ID was unset; activation did not persist. Explicit directory was passed to every subagent.
- Architecture-first gate updated only the clearly marked proposed design in `docs/system-architecture.md` §2.2. Current implemented Room4/standard-sync baseline and release holds remain intact.

## Policy decisions confirmed by user
1. Privacy-first revocation purges unsent collaborative edit payloads and retains only content-free non-replayable evidence; no encrypted recovery quarantine.
2. App-wide assignment opt-in rejects all old TabbyRush sync clients without capability1. Other apps remain viewer-only/unchanged; no unsafe private-only downgrade.
3. Transaction-capable Mongo replica set is mandatory for opted-in assignment policy; app-scoped fence serialization is a deliberate correctness/throughput tradeoff.

## Remaining gates
Validation interview completed with3 decision questions and1 clarification exchange; [answers recorded](06-validation-interview.md). All3 choices are now propagated into the existing plan/command overview and all9 phase requirements, implementation checklists and acceptance gates. G0 policy validation is confirmed; implementation/source review and G1–G6 remain pending/NOT RUN. The existing directory/file set, ownership manifest, dependencies and284h estimate are retained; no code or new plan created. Future runtime/interactive proof and production authorization remain separate.

## Unresolved questions
None blocking this planning deliverable. Policy choices are confirmed; future runtime prerequisites remain explicit gates. No implementation is started by accepting or reviewing the plan.
