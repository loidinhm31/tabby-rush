## Code Review Summary

### Scope
- Source-only review; no fixes, tests, builds, formatters, git operations, live services/DB, secrets or verification mirrors accessed.
- H = `G:/ws/sharing/glean-hub`; D = `G:/ws/sharing/doggo`.
- H plugin prefix `glean-hub-plugin/glean-hub-sync/`: `src/models/{app,api,record}.rs`, `src/error.rs`, relevant `src/lib.rs`, `src/routes/apps.rs`, relevant `src/services/{app_service,strict_upgrade_service,sync_service,permit_service}.rs`, `src/jobs/deleted_records_cleanup.rs`, `src/bin/standard-sync-cutover.rs`, all four `src/operator/*.rs`, both new integration suites, `Cargo.toml`.
- H core: `glean-hub-core/glean-hub-sync-engine/src/protocol.rs`.
- H UI prefix `glean-hub-app/src/`: `services/sync-admin-service.ts`, its existing test, `pages/admin/{edit-app,app-registration}.tsx`, relevant `components/organisms/schema-editor.tsx`.
- D: `scripts/{hub-live-smoke,hub-schema-provision,debug-fault-proxy}.mjs`, proxy tests; plan/contracts/phase02+04 reports and relevant requirements/guidance.
- Approximately 3,500 source lines examined; source sections, not a generated diff or coverage measure. Existing strict/fleet work not treated as new feature defects.
- Updated plans: none, intentionally read-only assignment. This report is the sole write.

### Overall Assessment
Ordinary registry CAS/no-op/default/exhaustion handling and UI draft/readback ownership are coherent in the inspected source. Conversion preserves original rows/evidence and validates every converted identity rather than sampling. One high-priority cutover command race remains; two medium safety gaps below. Review completion is not feature or rollout verification.

### Critical Issues
- None identified in reviewed source. No claim of exhaustive runtime safety.

### High Priority Findings

**H1 — Same-migration publish and rollback can leave ordinary registry pointing at strict storage.**
- Exact sources: H plugin `src/operator/mod.rs:255-279`; `src/operator/journal.rs:55-64,100-106`; `src/operator/publication.rs:98-115,141-164`.
- Journal uniqueness excludes another migration ID, not another invocation using the same ID. Journal updates filter only `_id`; no exclusive invocation ownership or conditional transition prevents publish and rollback from executing together.
- Concrete interleave: start from `stageActivated` with original strict registry and converted active collection. Both invocations load that journal. Rollback passes original-registry check at publication lines 145-147, then performs archive/active equivalence reads at 153-157. Concurrent publish finishes its active validation at 98 and changes registry to ordinary at 109. Rollback finishes its earlier checks, renames converted active back to staging at 158 and restores original strict archive at 161. Final snapshot comparison at 164 notices the registry mismatch only after the swap.
- User impact: registry advertises ordinary mode while active rows again have strict string `_id`/`revision` shape; standard record decoding fails and strict access is rejected. Both command invocations can be validly authorized while all Hub/job processes remain stopped. Retained archives limit destruction, but do not prevent broken publication or make the failure self-recovering.
- Minimal recommendation: serialize every mutating invocation for this database/app, including same migration ID, using a durable exclusive command claim/conditional journal state. Hold ownership across validation, both renames and registry publication/rollback. Do not use an expiring lease that can be stolen while a paused former owner can still rename. A registry re-read alone leaves the same check/rename race.
- Coverage gap: `tests/standard_sync_cutover_integration.rs:202-213` exercises rollback sequentially; no overlapping publisher/rollback case. Add a real-Mongo barrier/interleaving regression when coordinator runs checks.

### Medium Priority Improvements

**M1 — Maintenance authorization can expire during validation, before collection/registry mutation.**
- Exact sources: H plugin `src/operator/mod.rs:199-215,269-279`; `src/operator/publication.rs:76-109,145-161`; `src/operator/conversion.rs:113-152`.
- The time is checked before snapshot loading/validation in `authorize`, and publication reauthorization occurs before entering `publish`. Publication then performs full source capture and per-row validation before archive rename, additional per-row validation before activation, archive verification, and another full validation before the actual registry CAS. Rollback likewise checks its authorization only before lengthy validation and renames.
- Concrete reproduction chain: authorize a short remaining window with a sufficiently large snapshot/slow Mongo connection; enter `publish` while valid; the sequential per-record queries span `windowEnd`; archive/activation/registry update still execute because none of those boundaries checks the deadline. No concurrent service or malformed manifest is needed.
- User impact: irreversible publication steps run outside the explicitly authorized maintenance window, contrary to the manifest boundary. The current comment claiming long preparation cannot authorize out-of-window publication does not cover preparation inside `publish` itself.
- Minimal recommendation: carry the validated immutable maintenance deadline into publication/rollback and recheck immediately before each destructive rename and registry CAS, after expensive reads. Expiry must retain isolation/evidence and require renewed authorization, never auto-restore/restart. Cover expiry between validation and publication in the real operator suite.

**M2 — Provisioning writes a one-time API key before ensuring Windows file confidentiality.**
- Exact source: D `scripts/hub-schema-provision.mjs:36-38,48-55,69`.
- `openSync(path, 'wx', 0o600)` prevents overwrite, but POSIX mode 0600 does not install a restrictive Windows DACL. On Windows, choosing an output directory with inherited `Users`/`Authenticated Users` read access creates an output file readable by those principals; the returned one-time API key is written there before the script merely advises the operator to protect ACLs.
- User impact: another local user can read the app credential during/after provisioning. Explicit origin/app confirmation and not printing the key do not protect that on-disk disclosure.
- Minimal recommendation: before sending registration POST, require/verify an output location/file whose actual Windows DACL permits only intended custody principals, or create it with an appropriately restrictive DACL. Fail before requesting the one-time key if confidentiality cannot be established; keep the Unix 0600 path. Do not rely on a post-write warning.

### Low Priority Suggestions
- None; no style/file-length findings.

### Positive Observations
- `app_service.rs:638-684`: invalid stored/precondition counters fail closed; semantic no-op precedes overflow/index work; missing counter publishes explicitly as 2; CAS loser re-reads without blind overwrite. Omitted/null sharing retains current effective sharing.
- `strict_upgrade_service.rs:259-279,412-426,564-607`: interrupted publication recovery checks request/epoch/target tables/counter journal evidence, rather than mode alone; changed tables/version/timestamp publish together.
- `operator/{conversion,journal,publication}.rs`: converted tombstones retain deletion state with stable string timestamp seeds; raw payload/owner/revision equality, unique logical index and complete scans checked; original source UUID/indexes/rows remain in nonoverwritten archive. No dropTarget or automatic backup deletion/restart.
- Rollback checks original registry and unchanged converted rows, so sequential post-publication/post-write rollback refuses; H1 is specifically the unguarded concurrent interleave.
- `edit-app.tsx:34-37,66-106,108-193,219-259`: keyed route plus generation fencing, synchronous request guard, baseline-derived expectation, preserved conflict drafts, separate latest preview and accepted/current readback. Failed readback pauses editing and retries GET only.
- `sync-admin-service.ts:123-150,347-374`: matching identity/safe integer metadata required; only exact structured conflict becomes typed error; unrelated/malformed conflict never renders raw body.
- Registration key stays component-local; wrapper import uses captured imported app ID. Script targets explicit; no dotenv/default live target, credential printing, destructive purge or snapshot restore in reviewed scripts.
- New real-Mongo suites are explicit feature-gated and refuse missing disposable authorization/allowlist. Their source contains actual row/registry equivalence assertions; execution was not assessed here.

### Recommended Actions
1. Resolve H1 before authorizing conversion; add overlapping same-ID publish/rollback regression.
2. Enforce M1 at destructive boundaries and M2 before registration issuance.
3. Coordinator completes existing real-Mongo/API/UI/native gates in the supplied isolated environment; this review does not duplicate those runs or mark pending plan acceptance complete.

### Metrics
- Findings: 0 critical, 1 high, 2 medium, 0 low.
- Type coverage: not measured. Test coverage: not measured. Lint issues: not measured.
- Verification exercised: source inspection and scoped reference/TODO searches only; no TODO/FIXME/unimplemented markers found in the searched new operator/tests, edited editor page/component and three scripts.
- Tests/build/browser acceptance: not run, explicitly excluded. No pass counts inferred from implementation reports.

### Unresolved Questions
- None required to establish the findings. All reproduction chains above are source-derived analysis, not executed reproductions.
