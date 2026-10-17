# TabbyRush command/status access point
Date: 2026-10-13. [Plan](plan.md) · [Architecture](architecture.md) · [Contracts](contracts.md) · [Dependency matrix](dependency-matrix.md) · [Exact ownership](file-ownership.md).
HISTORICAL — use [current Glean Hub plan](../261013-1540-tabbyrush-glean-hub-reuse/evc-cmd-plan.md). This phase table/60h scope and Worker/D1/shared-token/online-only assumptions are superseded; do not execute. No implementation performed.

| Phase | Status | Progress | Parallel group | Effort | Start / gate |
|---|---|---|---|---:|---|
| [01 Headless tooling](phase-01-headless-tooling.md) | completed | 100% | A | 4h | Approved setup; actual Xpra SSH/SDK/AVD visual boot |
| [02 Frozen foundation](phase-02-frozen-foundation.md) | pending | 0% | B | 6h | After01/default approval; disposable tuple proof and freeze |
| [03 D1 migrations](phase-03-d1-migrations.md) | pending | 0% | C | 3h | After02; own SQL code/test ready, executed07 |
| [04 Worker API](phase-04-worker-api.md) | pending | 0% | C | 8h | After02 code; real local D1 tests after03 at07 |
| [05 Shared data/state](phase-05-shared-data-state.md) | pending | 0% | C | 9h | After02 code; permanent behavior/live checks07 |
| [06 Compose UI/theme](phase-06-compose-ui-theme.md) | pending | 0% | C | 9h | After02/font audit; semantic and real render checks07 |
| [07 Android integration](phase-07-android-integration.md) | pending | 0% | D | 6h | All C code ready; tests first, actual debug/local API/UI |
| [08 Deploy/security/release](phase-08-deployment-security-release.md) | pending | 0% | E | 6h | After07; account/signing, remote D1/HTTPS/signed Android |
| [09 Linux distribution](phase-09-linux-distribution.md) | pending | 0% | F | 5h | After08; actual installed self-contained RPM/Xpra |
| [10 Windows distribution](phase-10-windows-distribution.md) | pending | 0% | G | 4h | After09; later Windows/native MSI build/install/UI |

## Future command gates
- 01: `xpra start :100 --start-child=xterm --exit-with-children=no --html=off`; attach SSH verified host, launch Studio/emulator from inherited Xpra terminal. Interactive licenses, no public port/full DE.
- 02: Current-stable wizard Android+Desktop proof outside repo; freeze official-compatible exact tuple and real contract types. No stub app files/final-root build claim yet. Pin npm/Gradle module locks and verification under exact owners at runnable gates.
- 03–06: Exclusive code lanes after freeze; no parallel build/test runs. Standalone SQL test `node --test worker/test/migrations.test.mjs` executed by07 along with other permanent checks; not independent D1/runtime proof.
- 07: `npm --prefix worker ci`; `npm --prefix worker test`; `npm --prefix worker run test:migrations`; `./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` with tuple-proven tasks/active explicit device/local Worker. Run actual CRUD/network/conflict/one-dispatch proxy/lifecycle/accessibility UI and capture evidence.
- Local Worker (worker directory): pinned `npx wrangler d1 migrations apply tabbyrush-db --local`; `npx wrangler dev --ip 127.0.0.1 --port 8787`. Emulator alias10.0.2.2, USB reverse127.0.0.1; HTTP debug-only and token never CLI argument.
- 08: Confirm account/real04 binding, private export/bookmark then `npx wrangler d1 migrations apply tabbyrush-db --remote`; actual fail-closed `npx wrangler deploy`; interactive `npx wrangler secret put APP_SESSION_TOKEN`; live scripts prompt token. `./gradlew --no-daemon --no-configuration-cache :androidApp:assembleRelease`; verify/install actual signed APK and HTTPS UI; release localHTTP rejected. Full recovery/permissions in phase08.
- 09: `./gradlew :desktopApp:test :shared:allTests :desktopApp:createDistributable :desktopApp:packageRpm`; approved RPM install then actual installed launcher from Xpra. Normal/run/package MainKt literal RELEASE; only `:desktopApp:runDebug` chooses distinct DEBUG launcher. Deployed HTTPS/real UI required.
- 10 (Windows): `.\gradlew.bat :desktopApp:test :desktopApp:createDistributable :desktopApp:packageMsi`; actual verified MSI install/UI/uninstall/reinstall. Compatible Windows JDK/WiX/SDK as needed, native task not SKIPPED. No Linux cross-build substitute.

## Handoff rules
Failures block advancement and reopen original file owner; gate rerun after repair.02 metadata/shared lock,07 Android lock,09 desktop lock/config retained through later gates.08 owns general docs throughout, updated with honest Linux/Windows status only after each runtime acceptance.
Screenshots from actual apps, sanitized data/no token form. No fabricated credentials/versions/results; Windows absent means required gate pending. [Parent document review](reports/plan-review.md) completed; implementation reviews remain pending. Prior incomplete report is resolved historical evidence.

## Unresolved questions
Confirmed: separate user accounts, offline editing/sync, reference light theme, manual Windows build. Identity provider, sync conflict/background policy and account-local data lifecycle must be resolved in plan revision; toolchain/fonts/account/signing/Windows access remain implementation prerequisites.
