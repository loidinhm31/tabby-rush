# Server admin notifications — command plan

Date 2026-10-18 · priority P2 · status in-progress · progress 94.9% (262h / 276h credited) · effort 276h · branch main.
[Comprehensive plan](plan.md). Implementation and tooling delivered across all 12 phases P01–P12; 566 passing verifications (548 test executions + 18 vector/static checks); Cycle 2 code review approved 8/10 (0 critical issues); user approved on 2026-10-18. Staged pilot acceptance and operator authorization pending; Windows native execution held at announcement Gate G5.

## Execution strategy
Gate A freezes exact protocol/pure contracts/common build prerequisites. Wave B implements disjoint components and operations assets. Wave C wires Android and desktop hosts against real components. Wave D first proves restricted Android/Linux pilot readiness, then full Windows-inclusive acceptance and permanent docs. Component independence never waives backend/provider/native runtime gates.

## All phases
| # | Phase | Status / progress | Effort | Parallelization group |
|---|---|---|---|---|
| 01 | [Contract, pure interfaces and common build gate](phase-01-contract-build-gate.md) | implemented / 100% | 12h | A |
| 02 | [Backend storage, audience, device routes and durable worker](phase-02-backend-canonical-inbox.md) | implemented / 100% | 32h | B |
| 03 | [FCM HTTP v1 transport and legacy push cutover](phase-03-fcm-http-v1-transport.md) | implemented / 100% | 16h | B |
| 04 | [React admin composer, confirmation and sent history](phase-04-react-admin-composer.md) | implemented / 100% | 16h | B |
| 05 | [Shared remote inbox core and authorized HTTP/SSE](phase-05-shared-remote-core.md) | implemented / 100% | 28h | B |
| 06 | [Shared Compose inbox UI and remote navigation](phase-06-shared-inbox-ui.md) | implemented / 100% | 16h | B |
| 07 | [Linux persistent D-Bus remote alert adapter](phase-07-linux-native-adapter.md) | implemented / 100% | 20h | B |
| 08 | [Windows C++/WinRT bridge, JNA and MSI identity](phase-08-windows-native-packaging.md) | implemented (Gate G5 held) / 100% | 36h | B |
| 09 | [Android Firebase runtime, install operations and inbox integration](phase-09-android-runtime-integration.md) | implemented (Gate G2 held) / 100% | 32h | C |
| 10 | [Desktop process runtime, tray and native inbox activation](phase-10-desktop-runtime-integration.md) | implemented / 100% | 24h | C |
| 11 | [Operations, credentials, feature gates and deployment assets](phase-11-operations-config-deployment.md) | implemented / 100% | 16h | B |
| 12 | [Cross-system live acceptance, evidence and permanent docs](phase-12-cross-system-acceptance-docs.md) | implemented (Tooling/Docs delivered; live acceptance pending) / 50% (14h / 28h) | 28h | D |

## Dependencies
- A:01 first. B:02..08 and11 after01; whole backend host compile additionally waits real03.
- C:09 after01/03/05/06;10 after05/06/07/08. Both runtime gates also wait02/03/11 and actual host environment.
- D:12 after all02..11 implementation dependencies; pilot evidence permits Windows acceptance held, not missing code/build prerequisites. Full delivery requires Windows proof. Source defects return original owner.
- [Canonical implementation vs acceptance dependency matrix](reports/04-execution-ownership.md#dependency-matrix); [exclusive full file manifest](reports/04-execution-ownership.md#full-file-manifest).

## Contracts and evidence
- [Exact API/storage/idempotency/device fencing/SSE/native ABI](reports/03-contract-specification.md).
- [Acceptance matrix and rollout/rollback gates](reports/05-acceptance-matrix.md).
- [Authoritative design corrections](reports/02-design-decisions.md); [fresh source analysis](reports/01-codebase-analysis.md). Scout intentionally skipped, no scout report claimed.
- [Validated decisions](plan.md#validation-summary): online details only, maximum500 recipients, restricted Android/Linux pilot before Windows acceptance; [pilot/full gates](reports/05-acceptance-matrix.md#pilot-and-full-delivery-gates).

## Safety invariants
Snapshot-confirmed selected/all/admin intersect current grant; journaled parent accepted before202; recoverable fenced materialization/dispatch;90day retention; only authenticated canonical inbox contains content. Android fixed generic FCM sole alert path. Desktop process-scoped SSE+real Linux/installed Windows activation; no live receive after quit. Separate in-memory remote domain/UI, no task schema/read outbox. Offline unbind never blocks local cleanup; late generic push accepted. Preserve email gate/reminders/sticky/strict-sync and release holds.

## Remaining prerequisites
All pending and owned in04/05: live Mongo durability; Firebase credential/public-client config and Google-enabled device; Linux actions daemon/session; Windows x64 SDK/compiler/WiX and installed MSI testbed; admin browser/two-device proof. Existing G2/G3/G6/G7/checksum/Glance holds remain. No unresolved product/protocol question. Explicit plan path authoritative; active-plan helper persistence unavailable because EVCRATE_SESSION_ID unset.
