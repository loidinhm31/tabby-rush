# Phase 06 — Shared Compose inbox UI and remote navigation

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P05](phase-05-shared-remote-core.md), [P09](phase-09-android-runtime-integration.md), [P10](phase-10-desktop-runtime-integration.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P06**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: complete; review: ready; status: complete; progress: 100%; effort: **16h**.
- Description: Shared Compose inbox UI and remote navigation.

## Key Insights
- Shared root currently accepts TodoUiState only; remote state must remain orthogonal.
- Hosts own runtimes; Composable visibility must not start/stop SSE.

## Requirements
- Inbox/list/detail/unread/empty/loading/error/pagination, manual refresh and secure native entry.
- Expose remote availability and OS permission/capability distinctly from email/local reminders.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Render pure RemoteInboxState and emit RemoteInboxAction; root forwards separate required remote inputs. All actual hosts migrate in09/10; intermediate whole-host compilation waits those integrations. Inbox UI has no network/provider ownership and introduces no default/no-op runtime shim.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` | modify | Add separate remote state/action inputs and inbox entry without TodoAction changes. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox.kt` | create | Accessible list, unread badge, pagination and loading/empty/offline/revoked/error states. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox-detail.kt` | create | Freshly authorized plain-text detail and canonical read acknowledgement UI. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox-navigation.kt` | create | Pure inbox route/detail entry state separate from task navigation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/remote-inbox-navigation-test.kt` | create | Safe native target, unavailable/account-clearing navigation and back behavior tests. |

## File Ownership
- P06 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Extend root signature with separate RemoteInboxState and callback, preserve existing task action semantics. Add toolbar/sidebar inbox entry and unread count in compact and expanded layouts.
2. Build paginated list with stable UUID keys, canonical read styling, retention message and refresh/loading/error/empty states. Do not reveal cached bodies when availability offline/revoked/signedOut.
3. Build detail using controller-authorized detail only, plain text and no external-link behavior. Opening emits Open and read acknowledgement is visible only after canonical readAt, not OS delivery.
4. Add safe notification entry routing: unauthorized/expired/wrong-account goes inbox with generic unavailable message; generation change dismisses detail and clears navigation. Back returns list/task workspace predictably.
5. Show Android notification permission/channel and desktop daemon/native capability limitations with in-app recovery. Do not reinterpret notificationsAllowed or add remote preference product scope.
6. Add pure navigation/state tests, and hand off semantics IDs for final actual UI evidence. Host integration09/10 binds flows/callbacks and foreground lifecycle; no UI-owned controller lifecycle.

## Todo list
- [x] Read owned files/current upstream changes and confirm frozen contract.
- [x] Complete all numbered steps with deterministic failure handling.
- [x] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [x] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.
## Success Criteria
- Inbox reachable through normal UI and native tap, with accessible content labels and all failure/empty states.
- Remote UI independent of TodoAction/task editor and no offline private detail.

Future targeted verification (not executed by this plan):
- `./gradlew :shared:jvmTest --tests "*RemoteInboxNavigationTest" --dependency-verification=strict`
- `Actual Android and desktop compact/expanded UI loading/empty/error/offline/read/detail/back screenshots in12.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Large root Composable already exceeds guidance; keep new rendering in small focused files.
- Required remote inputs intentionally require host migrations in09/10; do not add temporary defaults, compatibility overloads or no-op callbacks.

## Security Considerations
- No stale native target auto-open across auth generation; no cached detail after revocation.
- Content plain text with bounded lengths; OS capability not falsely represented as policy permission.

## Next steps
- Consumers/unblocked phases: [P09](phase-09-android-runtime-integration.md), [P10](phase-10-desktop-runtime-integration.md), [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
