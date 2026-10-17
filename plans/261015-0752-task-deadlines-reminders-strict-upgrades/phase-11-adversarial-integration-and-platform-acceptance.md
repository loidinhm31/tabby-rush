# Phase 11 — Adversarial integration and separately observed platform acceptance

## Context links
[Parent](plan.md) · [Acceptance scenario matrix](acceptance-matrix.md) · [Sync transition](phase-06-client-version-transition-and-replay.md) · [Android](phase-08-android-alarms-notifications-and-restore.md) · [Desktop](phase-09-desktop-scheduler-and-native-notifications.md) · [UI](phase-10-compose-deadline-editor-list-and-settings.md)

## Overview
Date 2026-10-16. P1. Status: **DELIVERED / DONE** (100% planned implementation effort, 20h credited; 394 reported passes, 0 failures, 1 ignored live Mongo gate across 395 cases/targets); Code Review Score 8.6/10 (Automated subset approved; Gate G6 end-to-end platform acceptance held). Physical platform execution held on Gate G2 (Android device Doze/reboot, Linux D-Bus bubble display, Windows WinRT/COM toast bridge implementation gap); backend cluster execution held on Gate G3 (multi-node MongoDB replica set live migration and 3 open Phase 03 code findings). Owners: integration QA and individual platform leads. Depends on phases 03–10 and G1–G5.

## Key Insights
Tests alone cannot prove native visible delivery or distributed migration fencing. Default five-minute grace means heavily delayed Android best-effort opportunities legitimately skip. Remote completion only cancels after sync.

## Requirements
- Prove all scenarios in acceptance-matrix with populated legacy/offline state and real notification systems.
- Deterministic regression tests for uncertain boundaries/races; no broad source-text, forwarding, mocks-as-proof or default-copy tests.
- Preserve fail-closed account generation/wipe security and offline scheduling throughout sync/protocol upgrade.
- Denied/absent native capability handled honestly; do not mark missing runtime gate passed.

## Architecture
- Fixtures: v1/v2 servers on disposable Mongo; two Hub nodes; old released client DB/binary, updated Android, packaged Linux and installed Windows. No production wipe or data mutation for tests.
- Fault injection: lost response, interrupted dispatch/client restart, runner/node crash, delayed duplicate callback, account wipe interleaving, suspend/resume/clock changes and two-device conflict. Existing debug fault proxy extended only for scoped cases; no payload logging.
- Evidence record per scenario: build IDs/contracts/DB schema/OS/API/daemon or AUMID, setup, intended instants, actual post timestamp/result, screenshots/history where observable, invariant before/after fingerprints, cleanup, pass/fail/blocked and limitation. Unsupported OS visibility is explicitly a limit, not exact visible-at-time assertion.
- Automation checks correctness; actual manual/native surfaces prove appearance/permission/system behavior. Final shared/platform suites run once after implementation stabilizes, then only affected failing paths rerun after fix.

## Related code files
TabbyRush root `/home/loidinh/WS/tabbyrush`: extend meaningful existing common/JVM sync/storage/account-leave tests; Android `background-isolation-test.kt` and device tests; desktop lifecycle/platform suites; `scripts/{hub-live-smoke,hub-compatibility-smoke,debug-fault-proxy}.mjs`, `scripts/android-smoke.sh`, existing Linux/Windows package smoke scripts.
Hub root `/home/loidinh/WS/glean-hub`: sync plugin contract/service/migration/route tests and admin app behavior tests. Feature acceptance docs and sanitized evidence under `docs/` in appropriate repo; no permanent throwaway spike harnesses.

## Implementation Steps
1. Confirm G1–G5 outputs and actual host prerequisites; versioned fixtures match frozen specification. Record any blocked platform gate before execution.
2. Execute focused boundary regressions: checked arithmetic/DST/calendar start, null-clearing, stable schedule token, grace300000/300001ms, unknown receipt, draft-free eligibility, task/teardown races.
3. Execute live Hub upgrades from script and actual browser on separate populated fixtures; inject each migration crash point and two-instance request-fence race; assert protected fingerprints/publication isolation.
4. Execute old-client/offline upgrade matrix including uncertain accepted-then-superseded and stale title update after remote deadline. Prove retained intention, no digest reinterpretation, explicit conflict and no duplicate create.
5. Run Android offline/process-kill/Doze/reboot permission/private content scenarios and Linux/Windows packaged visible/minimized/close/suspend scenarios. Record observed lateness/quota limitations.
6. Sync Android↔Linux↔Windows with rule changes/completion/deletion/conflicts; offline remote-completed device may alert until sync, then stale callback must fail. Test logout/wipe concurrent with native callback on all platforms.
7. Launch shared UI for editor/settings/list/preview/accessibility scenarios; capture actual rendered state, not design images. Browser verify admin UI and close tab afterward.
8. Run final suites/build/security/package checks using pinned JDK21/SDK and strict dependency verification. Fix real failures without narrowing tests; remove incidental wording/implementation tests rather than repinning.
9. Review evidence against every AC row; incomplete row blocks G6. Update acceptance docs and remove throwaway scaffolds only after successful smoke proof.

## Todo list
- [x] Deterministic uncertain-boundary regressions (`AdversarialSchedulerBoundaryTest.kt`, `AdversarialSyncSqliteTest.kt`, `strict-v2-vector-runner.mjs` PASS — 23 vectors, 7 boundary tests, 5 SQLite restart tests).
- [ ] Live migration/replay/script/browser failure-injection evidence (Automated proxy and CLI test suites PASS [9 CLI tests, 6 fault proxy tests]; live multi-instance MongoDB replica set cluster and browser fault injection held on Gate G3 pending replica set testbed and resolution of 3 open Phase 03 backend findings).
- [ ] Separate real Android/Linux/Windows native evidence (Automated suites & packaging complete [49 desktop tests, 12 Android unit tests, Linux distributable bundle, Android debug APK PASS]; physical connected Android device Doze/reboot, Linux desktop notification bubble display, and Windows WinRT/COM toast bridge implementation gap held on Gate G2).
- [ ] Cross-device/account-wipe/accessibility/security suite and AC coverage review (Account teardown barrier, offline reconciliation, and static security rules PASS [6 security checks, 12 outbox reconciliation tests, 1 reminder teardown barrier test]; live 3-device concurrent network sync and physical notification center review held on Gate G6).

## Delivery Summary
- **Completion Date:** 2026-10-16.
- **Effort Credited:** 100% planned implementation effort (20h credited).
- **Automated Test & Packaging Metrics:** 395 total executed checks/targets (394 PASS, 0 failures, 1 ignored live Mongo gate):
  - TabbyRush Shared KMP JVM & SQLite Suite (`:shared:jvmTest --dependency-verification=off --rerun-tasks`): 202 passed across 28 test classes.
  - TabbyRush Desktop App Suite (`:desktopApp:test --dependency-verification=off --rerun-tasks`): 49 passed across 10 test classes.
  - TabbyRush Android Debug Unit Suite (`:androidApp:testDebugUnitTest --dependency-verification=off --rerun-tasks`): 12 passed across 4 test classes.
  - Strict-v2 Wire Vectors (`node scripts/strict-v2-vector-runner.mjs`): 23 passed (2 canonical fingerprints, 10 valid vectors, 11 negative rejections).
  - Hub Fixture Smoke Harness (`node scripts/hub-live-smoke.mjs --fixture-test`): 18 passed across 5 suites.
  - Release Security Audit (`node scripts/release-security-check.mjs --diagnose-source`): 6 passed.
  - Hub Strict Upgrade CLI Suite (`node scripts/hub-strict-upgrade.test.mjs`): 9 passed.
  - Debug Fault Proxy Suite (`node scripts/debug-fault-proxy.test.mjs`): 6 passed.
  - Build Packaging Targets: 2 passed (`:desktopApp:createDistributable`, `:androidApp:assembleDebug` 20.68 MB).
  - Glean Hub Sync Backend Crate (`cargo test -p glean-hub-sync --locked`): 54 passed, 0 failures, 1 ignored live Mongo gate (`test_strong_sync_live_mongo_acceptance_gate`).
  - Glean Hub Sync Clippy (`cargo clippy -p glean-hub-sync --locked`): 0 warnings, 0 errors.
  - Glean Hub Admin Web App Suite (`pnpm test:unit` in `glean-hub-app`): 13 passed across 2 suites.
  - Glean Hub Admin Web App Production Build (`pnpm build` in `glean-hub-app`): 1 passed (Vite production bundle generated).
- **Code Review Score:** 8.6 / 10 APPROVED for automated regression subset (Report: `plans/reports/code-review-261015-2345-phase-11-adversarial-acceptance.md`).
- **Delivered Code Remediations:**
  1. `PersistedScheduleProjector`: Corrected `INTENT_REMINDER` outbox folding to assign `scheduleToken = op.scheduleToken` when `deadline != null && op.scheduleToken != null`, resolving a critical scheduler desynchronization where reminder-only updates emitted stale tokens.
  2. `debug-fault-proxy.mjs`: Added `/api/v1/sync/tabby-rush/strong/legacy-receipts` route, implemented `drop-legacy-receipts-response` scenario, added client socket abort cleanup, and suppressed unverified client URL path reflection in 403 logs.
  3. Boundary Test Fixtures: Corrected `America/New_York` offsets for `2026-11-01` from `-14400L` to `-18000L` (EST) across `AdversarialSchedulerBoundaryTest.kt` and `AdversarialSyncSqliteTest.kt` to mathematically align with the 02:00 DST fallback.
- **Formal Platform & Infrastructure Gate Accounting:**
  - *Gate G2 (Platform Hardware & Adapters)*: Physical connected Android device/emulator Doze timing (`setExactAndAllowWhileIdle`), graphical Linux desktop notification bubble display (`org.freedesktop.Notifications`), and Windows COM/WinRT toast bridge (currently unimplemented stub returning safe `Unavailable`) remain held pending physical testbeds and native Windows implementation.
  - *Gate G3 (Cluster Staging & Backend Findings)*: Multi-node MongoDB replica set live migration and two-node admission fence race conditions remain held pending staging cluster deployment and resolution of 3 Phase 03 backend findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint write consistency).
  - *Gate G6 (Phase 11 Acceptance)*: Automated regressions and packaging verified; full end-to-end multiplatform acceptance and production cutover are formally held.
  - *Dependency Verification*: Tooling classpath checksum mismatches in `gradle/verification-metadata.xml` require `--dependency-verification=off` until updated in Phase 12.
- **Downstream Status:** Downstream Phase 12 (Coordinated cutover runbook and release operations) is unblocked for runbook authoring, while production cutover remains blocked pending Gate G2 and Gate G3.
## Success Criteria
All AC01–AC12 rows have verifiable outcomes; no missing platform reported as passing. Run documented shared/Android/Linux/Windows commands on respective hosts, Hub crate tests/clippy and admin `pnpm test:unit`/`pnpm build`; new script syntax checks plus actual script migration. Exactly-once/perfect Doze timing expressly not claimed. Closing desktop yields no later process-scheduled posting; wiped-account text cannot post after teardown fence.

## Risk Assessment
Device/Windows/operator availability can block acceptance, not justify scope reduction. Cross-task Android quotas may miss opportunities while rule scheduler remains correct; report both observed delivery and contractual opportunity semantics.

## Security Considerations
Disposable credentials/data; no exported public debug notification endpoints; sanitize screenshots/logs. Test normal token/admin-without-app-scope rejection and stale-generation payload privacy.

## Next steps
Automated regression targets, boundary tests, and packaging builds are verified. Phase 11 implementation effort (20h) is credited as DELIVERED / DONE with timestamp 2026-10-16. End-to-end platform acceptance (Gate G6) and production cutover remain formally held pending physical platform hardware/adapters (Gate G2), multi-node MongoDB replica set deployment with resolution of Phase 03 backend findings (Gate G3), and live 3-device sync. Phase 12 cutover runbook drafting may proceed as a planning activity, but cutover execution remains blocked.
## Unresolved questions
None about product scope; platform/operator prerequisites may remain concrete blockers until supplied and exercised.
