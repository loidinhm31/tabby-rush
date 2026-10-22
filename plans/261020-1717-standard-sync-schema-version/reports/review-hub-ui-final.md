## Code Review Summary

### Scope
- Follow-up to [review-hub-ui.md](review-hub-ui.md); source-only, genuine safety defects rather than style.
- H = `G:/ws/sharing/glean-hub`; D = `G:/ws/sharing/doggo`.
- H plugin prefix `glean-hub-plugin/glean-hub-sync/`: all `src/operator/{mod,journal,publication}.rs`, relevant `conversion.rs`, `src/bin/standard-sync-cutover.rs`, and `tests/standard_sync_cutover_integration.rs`.
- H UI: relevant `glean-hub-app/src/services/sync-admin-service.ts`, including `SchemaVersionConflictError`; unchanged UI behavior retains prior review, not a fresh full UI audit.
- D: `scripts/hub-schema-provision.mjs`, including final private-directory gate, snapshot `C2B2`.
- Approximately 1,350 source lines inspected, plus prior review and relevant requirements/standards/design sections. No generated diff or coverage measurement.
- Read `code-review` skill and applicable development rules. Updated plans: none; assignment explicitly excludes plan edits. This report is the sole write.

### Overall Assessment
No remaining actionable critical/high/medium/low defect identified in this scoped source review. Prior H1 and M1 are structurally addressed. Prior M2 required an additional retained-read-handle fix during this review; the final private-directory precondition addresses that race for trusted custody paths. Source approval is not native acceptance, deployment authorization, or proof of every runtime failure interleaving.

### Critical Issues
- None identified.

### High Priority Findings
- None remaining in inspected source.
- **Prior H1 — resolved in source:** `journal.rs:9-74` introduces durable, non-expiring, app-scoped execution ownership. `mod.rs:269-301` acquires it before mutable journal reads and releases only after the command and final state read succeed. Same migration ID does not bypass the unique claim.
- Recovery atomically replaces only the exact prior execution token, database/app/migration/origin/snapshot identity. It requires explicit `recoverExecution` authorization plus stopped-executor AND drained-Mongo evidence (`journal.rs:33-46`). Competing recovery manifests cannot both consume one token; ordinary acquisition never treats age/expiry as permission to take over.
- No `Drop` release: errors, cancellation or lost command acknowledgements leave ownership held. A cancelled MongoDB await can outlive its caller, so retaining the claim and requiring external drain evidence is the correct fail-closed boundary, not automatic process-death recovery.

### Medium Priority Improvements
- None remaining in final source.
- **Prior M1 — resolved in source:** `Execution::check` verifies identity/ownership and checks the immutable deadline AFTER its awaited ownership read (`journal.rs:56-65`). Every collection rename passes through the checked helper (`publication.rs:47-54`); registry CAS checks again after full validation (`publication.rs:101-113`). Both rollback renames use the same helper. Strict coordination retirement also rechecks ownership/deadline.
- Window expiry can leave archived/activated collections or a published registry under isolation; it does not release ownership, restart services or silently reverse publication. Renewed authorization and exact-token recovery are required.
- **Prior M2 — resolved in final source:** final provisioner checks an existing non-reparse private output directory, protected DACL, permitted owner and permitted explicit principals BEFORE `openSync` (`hub-schema-provision.mjs:8-28,89-94`). File DACL installation and readback still precede registration POST (`31-56,104`). Failure refuses registration rather than modifying parent-directory permissions.
- The intermediate file-only ACL fix left a real retained-handle race: another inherited-read principal could open the initially empty file before ACL tightening and later read the issued key through that handle. Final private-directory precondition removes that opening for excluded principals; changing a file ACL alone would not.

### Low Priority Suggestions
- None; no style or file-length findings.

### Positive Observations
- Rollback intent survives interruption. `mod.rs:280-287` resumes pending rollback only with rollback authorization instead of accidentally publishing it.
- UUID, snapshot and row-equivalence checks remain in place across interrupted rename recovery; no `dropTarget`, blind restore, backup deletion or automatic restart introduced.
- Rollback still rejects published/drifted registry state and validates converted rows before moving collections. App-scoped ownership closes the prior concurrent publish/rollback check-then-rename interleave.
- Regression source now covers overlapping same-ID publish/resume/rollback, non-expiring claims, missing drain evidence, wrong recovery tokens, competing token recovery and expiry at all three publication and both rollback mutation boundaries (`standard_sync_cutover_integration.rs:265-391`). Assertions inspect actual registry, claim and collection UUID state.
- `SchemaVersionConflictError` now uses an explicit constructor parameter plus assigned field (`sync-admin-service.ts:143-150`); fixed safe message, error name, version metadata and typed-error behavior remain intact. Exact structured 409 classification remains unchanged.
- Provisioner preserves issued credentials before subsequent GET, retains them after readback failure, refuses overwrite, suppresses server/network bodies and never prints the API key. POSIX exclusive-create/0600 path remains unchanged.

### Recommended Actions
1. Preserve the Windows private-directory precondition and pre-POST file-DACL verification; do not weaken custody to a post-write warning or file-only ACL fix.
2. Continue enforcing the external recovery contract: stop the previous executor and establish outstanding MongoDB operations are drained before authorizing exact-token recovery. The program validates attested evidence, not an independent process/server shutdown oracle.
3. Keep native UI smoke held until required interactive computer-control approval and actual execution; packaging, unit tests and this source review do not close that gate. No live cutover authorization inferred.

### Metrics and Evidence
- Remaining findings: 0 critical, 0 high, 0 medium, 0 low in reviewed final source.
- Type coverage / test coverage / lint issue counts: not measured.
- Exercised here: source inspection and scoped reference/unfinished-marker searches only. No TODO/FIXME/unimplemented markers found in the searched operator, operator test, provisioner and conflict-constructor scope.
- No tests, builds, typechecks, linters, formatters, git operations, live DB/services, secrets or UI control used by this reviewer.
- Parent-provided evidence, not rerun here: strict Gradle integrated shared/desktop/Android unit + APK + instrumentation APK + Windows distributable PASS; real-Mongo operator suite 7 PASS; API/UI version/no-op/stale-409 draft retention PASS; prior Windows provisioning/file-DACL PASS.
- Latest parent-provided provisioning smoke PASS: existing private protected-DACL directory allowed creation/registration of a fresh isolated app at version 1; broad/unprotected directory exited 1 before file/POST, output absent and admin GET returned 404 for the fresh app. Disposable Hub/Mongo targets only; not rerun by this reviewer.
- Native UI smoke remains held after two control-approval timeouts. No broader completion claim made.

### Unresolved Questions
- None needed for source disposition. Native UI acceptance remains a verification gate, not an unresolved source defect.
