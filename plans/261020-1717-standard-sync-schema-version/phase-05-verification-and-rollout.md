# Phase 05 — Real Verification, Documentation & Controlled Rollout

## Context links

[Overview](plan.md) · [Acceptance and side-effect checklist](contracts-and-ownership.md) · [Design](../../docs/standard-sync-cutover-design.md) · completed phase02/03/04 reports.

## Parallelization info

The integrated strict-Gradle and real-Mongo operator suites have run; current outcomes are consolidated in the parent-authored [final integration verification report](reports/verification-final.md). Any source remediation returns to its owner and triggers affected checks only. No production migration occurs without separate exact-target authorization.

## Overview

Date: 2026-10-20. Priority P1. Status: **in progress — integrated verification and controlled rollout gates remain open.** Do not mark complete from source delivery or partial smoke.

## Key insights

- Existing server uses an unknown-to-this-plan database; its localhost health success is not test-target approval.
- A connected Android emulator and Windows desktop can establish native proof, but Linux acceptance still needs a Linux session.
- Tests prove logic; actual browser/native behavior and real Mongo cutover rehearsal are separate gates.

## Current status — 2026-10-20

- **Backend/API smoke:** On disposable local Mongo 27191, Hub 3100 and admin 3102, exercised schemaVersion baseline 1, changed save 1→2, identical no-op/retry staying at 2, and stale distinct save returning 409. Standard create/pull/delta/update/delete retained unknown JSON.
- **Admin browser smoke:** Existing editor's JSON-paste path added nullable `customNotes` and moved 1→2; no-op stayed at 2; stale 409 preserved the draft and latest inspection remained read-only. Explicit discard/reload adopted version 2. Builder remains available, but this is not the full browser matrix.
- **Backend/operator results:** Parent reports 83 affected Rust tests passed and 1 pre-existing strict-live-gate ignored (66 plugin, 5 schema Mongo, 7 operator Mongo, 4 client, 1 engine). The 7/7 operator suite covers concurrency, maintenance-deadline and recovery. `cargo check --workspace --all-targets` passed with existing unused warnings.
- **Native results:** Strict Gradle passed: shared JVM 264, Desktop 131, Android unit 50 (445 total); `assembleDebug`, `assembleDebugAndroidTest` and Windows `createDistributable` succeeded. The Android artifact manifest uses `dev.tabbyrush.android.standardsmoke`. Parent reports a standalone Windows runtime smoke PASS using real DPAPI, HubAuthSession HTTP, Room offline restart and two actual stores/coordinators; schemaVersion 2, opaque nested JSON/`customNotes`, parent-child sync and group delete were exercised without mocks or JUnit. Android connected instrumentation was not run; interactive native UI remains held after two computer-control grant timeouts.
- **Dependency/review boundary:** 50 dependency checksum entries are officially validated, including AAPT Windows; strict Gradle passed. Latest [native](reports/review-native-final.md) and [Hub/operator](reports/review-hub-ui-final.md) source-only reviews approved scoped fixes with no actionable findings; neither closes native UI/Android-connected acceptance. Existing Glance provenance and release holds remain open.
- **Safety and authorization:** Private-DACL ordinary provisioning passed in disposable smoke. No live production conversion is authorized. Exact-target approval, external all-node/job isolation, verified backup/restore and ownership/preservation of every user's data remain mandatory. The inherited strict-v3 runbook restore is **not approved for execution** because its MongoDB namespace is wrong and `_schemas`/`_coordination` are omitted; do not use it for Option B.

- The phase05 checklist remains open for the full browser matrix ([named cases](phase-04-admin-ui.md)), Android connected instrumentation, interactive native UI and exact-target rollout. The parent-reported Windows runtime smoke closes only the actual scenarios listed in the [final integration verification](reports/verification-final.md).
- Current outcomes above are parent-reported from 2026-10-20; this documentation pass did not run checks. That final report supersedes earlier pre-fix verification snapshots for the current outcomes.

## Requirements / architecture

Execute every acceptance row in the contract. Use a disposable database with ordinary authenticated test accounts and the actual Hub runtime; create task scenarios through real APIs/native clients. No mocked server, in-memory echo or source-string test counts as end-to-end proof.

## Related code files and exclusive file ownership

- D `scripts/hub-live-smoke.mjs`, `hub-compatibility-smoke.mjs`, `debug-fault-proxy.mjs` and their affected script tests: migrate useful behavior to standard protocol or remove obsolete strict-only scaffolds.
- D `scripts/hub-schema-provision.mjs`: ordinary app provisioning/schema version handling. Remove obsolete strict-upgrade/vector/recovery scripts only after proving they are not needed by migration tooling; never delete user data/backups.
- D `scripts/standard-sync-smoke.mjs` (NEW, if existing harness cannot be cleanly reused): real server exercise, no fake fixture mode as acceptance.
- D `tests/` affected script-level suites; remove obsolete wiring/source-text/strict-only assertions, retain meaningful security checks.
- D `docs/api-contract.md`, `local-storage-and-sync.md`, `hub-schema-integration.md`, `deployment-guide.md`, `security-and-recovery.md`, `system-architecture.md`, `standard-sync-cutover-design.md`, `project-overview-pdr.md`, `code-standards.md`, `codebase-summary.md`, `project-roadmap.md`, `development-roadmap.md`, `project-changelog.md`, `sync-acceptance.md`, `hub-compatibility-acceptance.md`, `release-acceptance.md`, root README and affected platform acceptance docs.
- D `docs/standard-sync-cutover-runbook.md` (NEW) and evidence under docs/evidence; H docs and README contract updates as applicable. Reports `reports/phase-05-*.md` in this plan.
- Backend/native/UI corrections stay with owners 02/03/04; this phase does not edit their runtime sources or generated types.

## Implementation steps

1. Integrate all terminal reports; check changed exported API references and ownership inventory are complete. Ensure no active Tabby runtime strict paths, fingerprint gates, strict feature labels or unconditional schema save callers remain. Keep unrelated Hub strict support.
2. Configure a fresh isolated Hub test database via protected environment/config. Refuse configured live DB targets and do not reuse the server's production `.env`. Start a separate port/service name. No credentials in command arguments/logs/reports.
3. Tester runs Hub Rust workspace suite (or all affected crates plus downstream workspace compilation), admin unit/build suite and native Gradle suites/builds. Record actual commands, failures, skips and dependency verification policy. Fix regressions, never re-pin wording/source-text assertions.
4. Rehearse cutover binary against the disposable strict dataset: dry-run no writes; live/deleted and per-user identical-row cases; mixed-shape rejection; interruption at preparation/swap/registry stages; rerun same journal; verify payload/identity/version/index invariants and restore backup under isolation.
5. Exercise actual schema API: legacy baseline1; changed2; no-op/reordered-map/identical retry same version; invalid/unauthorized no mutation; concurrent editors one success/one409; strict publication for another strict app; no-op does not change updatedAt. Standard sync response counters observed by non-admin user.
6. Open real admin browser UI: paste schema JSON, save, inspect returned version, refresh/readback; second-tab stale save preserves draft and offers reload/manual merge. Verify ordinary glean-hub and converted tabby-rush use normal editor; other strict apps retain their panel. Close test tabs.
7. Launch actual updated Android and Windows Desktop against isolated Hub. Create root/child with deadline/reminder on Android, observe Desktop, edit on Desktop, observe Android, complete/delete group and verify widget/sticky/scheduling behavior. Repeat account switch/cold restart with pending work.
8. Exercise network boundary with real fault proxy: drop accepted push response; retain ambiguous intent; reconcile convergence or explicit conflict review; no duplicate rows, cleared unacknowledged work or resurrection. Test concurrent edits, partial batch failures and an edit arriving while an older ack is in flight.
9. Add a future nullable JSON field through actual schema UI/API, write it, and edit another known field from an older native projection. Verify the future value survives round-trip. Document required-field/type-breaking changes as a client/backfill requirement, not automatic migration.
10. Exercise retention gap recovery and missing parent/confirmed rows against controlled old tombstones; complete full scan before replay; preserve unresolved local intent and prevent missing-row recreation. Do not use an incomplete page set to infer deletion.
11. Use evc-code-reviewer for implemented changes. Fix critical findings, rerun affected proof, obtain terminal approval. After approval, evc-project-manager updates only plan/status/roadmap/changelog while evc-docs-manager updates other docs in parallel, with disjoint ownership assigned for that documentation wave.
12. Replace current strict claims with verified standard contracts. Label retained old runbooks historical; update primary links. Record remaining platform/release holds honestly; planning or unit proof does not close physical acceptance.
13. Present exact live target, app, database, backup scope and maintenance window for authorization. Only then run approved conversion/deploy ordering. No live execution is authorized by this plan. Ask about commit after code acceptance; never stage/commit secrets automatically.

## Verification commands

Run in the indicated root after editing completes; commands below are planned, not executed results.

- H: `cargo test --workspace --locked` with an explicit disposable `MONGO_TEST_URI`; ensure Mongo integration tests execute rather than silently skip. `cargo build --workspace --locked` if tests do not compile all production binaries.
- H: `pnpm --filter glean-hub-app test:unit`; `pnpm --filter glean-hub-app build`.
- D: `.\gradlew.bat :shared:jvmTest :androidApp:testDebugUnitTest -PdesktopTarget=windows :desktopApp:test :androidApp:assembleDebug :androidApp:compileDebugAndroidTestKotlin :desktopApp:createDistributable`.
- Use dependency verification normally. Existing mismatch holds are not silently bypassed; any authorized `--dependency-verification=off` diagnostic run is disclosed and cannot close the release gate.
- D real Android target: `C:/Users/loidi/AppData/Local/Android/Sdk/platform-tools/adb.exe -s emulator-5554 ...`; verify intended package/activity from actual built manifest before launch. Existing Android smoke harness only proves launch/screenshot, not authenticated sync.
- Browser/native interaction uses actual surfaces and fresh screenshots/state. Obtain task-scoped desktop control approval when required; do not substitute static screenshots for runtime proof.

## Acceptance / rollout status
- [x] Integrated strict test/build wave completed; current results and one pre-existing ignored strict-live gate are recorded in the [final integration verification](reports/verification-final.md).
- [ ] **Acceptance hold:** disposable Mongo conversion/restart suite passed, but an independently validated backup/restore rehearsal and authorized maintenance-isolation proof remain open. The inherited strict-v3 restore runbook is not executable (wrong MongoDB namespace; omits `_schemas` and `_coordination`).
- [ ] **Acceptance hold:** actual admin browser smoke covered JSON save/readback 1→2, no-op at 2, stale 409 draft retention/latest read-only and explicit discard/reload only. Remaining browser scenarios are enumerated in [phase04](phase-04-admin-ui.md).
- [ ] **Acceptance hold:** Android connected instrumentation and interactive native UI remain unrun; the Windows smoke validates runtime core only and does not complete Android/Desktop UI, connected-device, reminder or full cross-device acceptance.
- [ ] **Acceptance hold:** unknown JSON retention was exercised; long-offline deletion/recovery acceptance remains unproven.
- [x] Latest scoped source reviews approved with no actionable findings; this plan's owned status, roadmap and changelog entries are reconciled.
- [x] Rollout is explicitly held; no live migration is claimed or authorized.
Unchecked items above are acceptance/release gates, not missing source implementation tasks. Phase05 stays in progress until named acceptance and authorization gates close.

## Success criteria

Every consumer-visible acceptance row has exercised evidence; no tests omitted to manufacture green. Code completion and release/cutover completion are reported separately. Live deployment, if authorized, reads back normal registry mode, unchanged credentials/grants, expected schemaVersion, generic records/indexes and two-native-client convergence.

## Conflict prevention

No edits to source-owner files. Project manager owns plan/roadmap/changelog; docs manager owns other documentation only after review. One owner records final acceptance status. No mid-wave build or shared documentation formatting churn.

## Risk assessment / security

Collection replacement and registry publication are multi-step: keep all-node maintenance until verified. Backup restore before publication; after generic user writes use forward repair or separately approved reverse migration. Reject unintended app/database, preserve account grants/API keys, do not expose copied user content in screenshots/reports. Manual purge/restore is outside native route allowlist and not part of smoke without explicit isolated-target scope.

## Next steps

Publish verified native builds and operational instructions only after gates pass. Existing inherited release blockers remain independently tracked.

## Unresolved questions

Execution-only prerequisites: isolated MongoDB target/credentials, Linux graphical session if Linux acceptance is claimed, and exact live-target authorization. Never infer these from archived credentials.
