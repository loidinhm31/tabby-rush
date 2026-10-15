# Phase 11 — Adversarial integration and separately observed platform acceptance

## Context links
[Parent](plan.md) · [Acceptance scenario matrix](acceptance-matrix.md) · [Sync transition](phase-06-client-version-transition-and-replay.md) · [Android](phase-08-android-alarms-notifications-and-restore.md) · [Desktop](phase-09-desktop-scheduler-and-native-notifications.md) · [UI](phase-10-compose-deadline-editor-list-and-settings.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 20h. Owners: integration QA and individual platform leads. Depends on phases 03–10 and G1–G5. Gate G6: all twelve brainstorm acceptance criteria exercised; separate platform evidence, not one host-wide pass.

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
Doggo root `/home/loidinh/WS/doggo`: extend meaningful existing common/JVM sync/storage/account-leave tests; Android `background-isolation-test.kt` and device tests; desktop lifecycle/platform suites; `scripts/{hub-live-smoke,hub-compatibility-smoke,debug-fault-proxy}.mjs`, `scripts/android-smoke.sh`, existing Linux/Windows package smoke scripts.
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
- [ ] Deterministic uncertain-boundary regressions.
- [ ] Live migration/replay/script/browser failure-injection evidence.
- [ ] Separate real Android/Linux/Windows native evidence.
- [ ] Cross-device/account-wipe/accessibility/security suite and AC coverage review.

## Success Criteria
All AC01–AC12 rows have verifiable outcomes; no missing platform reported as passing. Run documented shared/Android/Linux/Windows commands on respective hosts, Hub crate tests/clippy and admin `pnpm test:unit`/`pnpm build`; new script syntax checks plus actual script migration. Exactly-once/perfect Doze timing expressly not claimed. Closing desktop yields no later process-scheduled posting; wiped-account text cannot post after teardown fence.

## Risk Assessment
Device/Windows/operator availability can block acceptance, not justify scope reduction. Cross-task Android quotas may miss opportunities while rule scheduler remains correct; report both observed delivery and contractual opportunity semantics.

## Security Considerations
Disposable credentials/data; no exported public debug notification endpoints; sanitize screenshots/logs. Test normal token/admin-without-app-scope rejection and stale-generation payload privacy.

## Next steps
G6 evidence is prerequisite for phase 12 production cutover and release. Any missing evidence leaves plan/phase pending or blocked, never completed by build success alone.

## Unresolved questions
None about product scope; platform/operator prerequisites may remain concrete blockers until supplied and exercised.
