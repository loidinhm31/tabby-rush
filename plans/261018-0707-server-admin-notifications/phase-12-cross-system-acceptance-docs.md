# Phase 12 — Cross-system live acceptance, evidence and permanent docs

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Root guidance: [README](../../README.md) and `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current files before modifying. Edit authority is limited to the exact owned paths below, including permanent docs assigned here.

## Parallelization Info
- Group **D**; implementation start: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md), [P04](phase-04-react-admin-composer.md), [P05](phase-05-shared-remote-core.md), [P06](phase-06-shared-inbox-ui.md), [P07](phase-07-linux-native-adapter.md), [P08](phase-08-windows-native-packaging.md), [P09](phase-09-android-runtime-integration.md), [P10](phase-10-desktop-runtime-integration.md), [P11](phase-11-operations-config-deployment.md).
- Additional runtime/acceptance dependencies: all implementation prerequisites remain. Pilot gate requires Mongo/Firebase/browser/Android/Linux environments and evidence; Windows acceptance may remain held. Full-delivery gate additionally requires installed Windows runtime proof. No fake/no-op component or skipped code/build prerequisite.
- Exclusive owner **P12**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: tooling-delivered; review: approved; status: in-progress; progress: 50%; effort: **28h** (14h credited for acceptance tooling and permanent docs; 14h pending live cross-system acceptance).
- Description: Acceptance tooling, smoke runner (34 assertions pass), browser test suite, evidence matrix and permanent documentation delivered. Live physical Android/browser/native gates, operator cohort approval, and Windows Gate G5 remain pending or held.

## Key Insights
- Source phases own behavior tests; final phase owns only new smoke/evidence/docs.
- Pilot requires actual admin browser+Android+Linux; full delivery additionally requires installed Windows. Unit-only/design-image evidence is never a substitute.

## Requirements
- Map every brainstorm acceptance row to owned tests/live proof and honest pass/fail gates in report05.
- No fabricated results, reduced Windows/Android scope, inherited release gate clearance or source-file overlap.
- Record distinct pilot-ready and full-delivery results; a pilot is not P12 completion. Online details only and maximum500 recipients remain validated scope.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Final driver orchestrates real server/Mongo/provider clients and records redacted artifacts. Browser test uses existing H Playwright tooling through explicit config/fixture URLs; no mocked acceptance backend. Native actions performed on actual OS surfaces and evidence cross-linked.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-smoke.mjs` | create | New real cross-repo HTTP/Mongo crash+SSE acceptance driver, no application-source repair. |
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.spec.ts` | create | Actual admin browser/inbox composer/confirmation/history smoke with real backend. |
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.config.ts` | create | Standalone browser smoke config using existing tooling without changing package manifests. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-acceptance.md` | create | Pending-to-observed matrix, commands/environment/hash/redacted result manifest. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-admin.png` | generate | Actual browser composer/confirmation/history capture only after smoke. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-android.png` | generate | Actual generic Android shade+authorized inbox capture. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-linux.png` | generate | Actual supported Linux native alert/inbox activation capture. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-windows.png` | generate | Actual installed Windows native alert/inbox activation capture. |
| `/home/loidinh/WS/tabby-rush/README.md` | modify | Document feature only to extent observed; link runtime limitations/evidence. |
| `/home/loidinh/WS/tabby-rush/docs/system-architecture.md` | modify | Reconcile proposed section8 to actual implementation and preserve inherited holds. |
| `/home/loidinh/WS/tabby-rush/docs/security-and-recovery.md` | modify | Document install proof/barrier, late generic push, revocation/offline/drain behavior. |
| `/home/loidinh/WS/tabby-rush/docs/deployment-guide.md` | modify | Link rollout/native/client setup and nondestructive rollback runbook. |
| `/home/loidinh/WS/tabby-rush/docs/android-acceptance.md` | modify | Record actual FCM/permission/tap/merged audit gates without clearing unrelated holds. |
| `/home/loidinh/WS/tabby-rush/docs/windows-acceptance.md` | modify | Record installed MSI/AUMID/Activated evidence and full-quit limitations. |
| `/home/loidinh/WS/tabby-rush/docs/windows-desktop-guide.md` | modify | Switch packaging instructions to identity-correct native MSI command and limitations. |
| `/home/loidinh/WS/tabby-rush/docs/project-overview-pdr.md` | modify | Describe agreed announcement behavior, retention and explicit exclusions. |
| `/home/loidinh/WS/tabby-rush/docs/codebase-summary.md` | modify | Add actual remote module/platform ownership after implementation. |
| `/home/loidinh/WS/tabby-rush/docs/project-roadmap.md` | modify | Record completion only after live gates and list retained release holds. |
| `/home/loidinh/WS/glean-hub/docs/system-architecture.md` | modify | Record real durable parent/worker/FCM/admin boundaries without modifying embed plans. |
| `/home/loidinh/WS/glean-hub/docs/CHANGELOG.md` | modify | Cross-repo announcement feature entry with observed limitations/gates. |
| `/home/loidinh/WS/tabby-rush/docs/linux-acceptance.md` | modify | Record actual Linux SSE/action/tray/full-quit acceptance separately from historical reminder holds. |
| `/home/loidinh/WS/tabby-rush/docs/linux-desktop-guide.md` | modify | Explain running-process inbox alerts, actions capability, no-tray recovery and full-quit limits. |
| `/home/loidinh/WS/tabby-rush/docs/project-changelog.md` | modify | Record TabbyRush feature changes and observed limitations, without crediting pending native gates. |
| `/home/loidinh/WS/tabby-rush/docs/code-standards.md` | modify | Reconcile exact remote route/lifecycle and protected Firebase merged-component security policy. |
| `/home/loidinh/WS/tabby-rush/docs/api-contract.md` | modify | Link canonical announcement API/DTO contract and separate it from strict task-sync endpoints. |
| `/home/loidinh/WS/tabby-rush/docs/authentication-and-credentials.md` | modify | Document bearer-only remote allowlist, install proof custody and account-generation fencing. |
| `/home/loidinh/WS/tabby-rush/docs/design-guidelines.md` | modify | Document inbox entry/detail/read/permission/offline UI and native navigation accessibility. |

## File Ownership
- P12 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Collect owner handoffs with code revision, contract version, targeted behavior results and known holds. Code/build dependencies above remain mandatory; classify Windows runtime acceptance as held for the pilot, never passed. Route defects to original source/test/build owner, never edit their files in12.
2. Implement new standalone smoke driver and browser test/config with canonical contract and disposable fixtures; use actual H/glean-hub-app Playwright dependency from its installed toolchain, no new package lock. Credentials via environment/file references not arguments/logs.
3. Execute every report05 row: real standalone kill/restart at each accepted/materialize/send checkpoint, conflicting/replayed keys and uncertain browser submission, changed grant/current admin/audience limits.
4. Operate actual admin browser through selected/all/admin confirmation and history plus Tabby inbox normal navigation/read/unread/error/empty. Capture true UI screenshots, not design baseline images.
5. Exercise Google-enabled Android foreground/background, cold/warm, denied channel/permission, offline/token rotation/switch/stale cleanup/late generic alert. Inspect payload and actual shade for private-content absence.
6. Exercise real Linux actions daemon visible/tray/no-tray/quit/click for pilot. Exercise installed Windows x64 MSI/AUMID/DLL callback for full delivery separately; Windows may remain held during pilot. Both ultimately require no new live receipt after quit, safe stale-toast handling and silent reopen catch-up.
7. Run cross-device read convergence, SSE initial/disconnect/lag/epoch recovery,90day immediate expiry with delayed TTL/orphan cleanup and regression reminders/sticky/task-sync/account teardown. Retain all inherited strict dependency/provenance/G2/G3/G6/G7 holds.
8. Record exact commands/toolchain/versions/provider outcomes/real captures/hashes/pass-fail; sanitize account IDs/tokens/content in artifacts. Distinguish feature acceptance from overall release holds and never mark pending test as passed.
9. Evaluate [pilot/full gates](reports/05-acceptance-matrix.md#pilot-and-full-delivery-gates): record applicable A01..A13 safety evidence on Android/Linux, named operator/cohort approval and Windows limitation before P11 pilot enablement. Keep Windows portions of A09/A13/G5 and full delivery explicitly held. Reconcile docs with observed pilot status; complete P12 and general rollout only after all Windows-inclusive gates pass.

## Todo list
- [x] Read owned files/current upstream changes and confirm frozen contract.
- [x] Implement standalone smoke runner, browser test suite, and config with zero new package dependencies.
- [x] Author comprehensive acceptance matrix and reconcile permanent documentation across TabbyRush and GleanHub.
- [x] Validate smoke runner self-test harness and 13 golden vectors in mock mode.
- [ ] Execute live multi-device acceptance checks with live Mongo/backend and authorized operator credentials.
- [ ] Obtain named operator pilot cohort signoff and verify live physical Android shade/tap.
- [ ] Execute clean-host Windows x64 MSI install/toast checks to close Gate G5 and achieve full P12 completion.
## Success Criteria
- Every row in05 has real owned test result plus required actual-surface evidence; no silent exclusion.
- Actual runtime end-to-end shows one logical inbox/secure read and honest at-least-once provider semantics.
- Permanent docs/changelog match implementation; inherited holds remain unless separately resolved with evidence.
- Pilot-ready requires all shared safety gates and real Android/Linux native proof, operator approval and rollback readiness; it never credits Windows acceptance or full P12 completion.

Future targeted verification (not executed by this plan):
- `At T using H toolchain: pnpm --dir /home/loidinh/WS/glean-hub/glean-hub-app exec playwright test --config /home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.config.ts`
- `node scripts/admin-announcements-smoke.mjs --config <redacted-fixture-config-path>`
- `Main integrator runs final strict Gradle/Rust/frontend/security/regression suites once after all owners land; commands/results recorded, none run during planning.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Hardware/credential prerequisite may hold feature acceptance; report pending gate, not fake pass.
- Evidence can leak content; use synthetic fixtures/redaction and do not commit raw provider logs.

## Security Considerations
- Test accounts/data disposable; no secret tokens in screenshots/logs/URLs.
- Rollout/rollback never deletes retained inbox or install fencing tombstones; current auth checks exercised.

## Next steps
- Consumers/unblocked phases: Final acceptance/release decision; no next source owner.
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
