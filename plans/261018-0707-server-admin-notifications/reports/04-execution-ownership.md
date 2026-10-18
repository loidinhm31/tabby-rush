# Execution, dependencies and exclusive ownership

Date 2026-10-18 · pending · 0%. Implementation authorization not implied. Roots are fixed absolute paths in manifest. Every listed path belongs to one phase for the entire feature, including sequential integration, tests, build resources and generated handoff artifacts. No directory catchalls. [Contract](03-contract-specification.md) and [acceptance](05-acceptance-matrix.md) are canonical alongside this manifest.

## Dependency matrix
Implementation-start gates concern available pure interfaces/source. Compile/integration and runtime acceptance have additional gates; wave B is not a promise of zero dependencies. A phase may write its source and owner tests before a provider exists, but cannot label missing runtime gates passed.

| Phase | Group | Implementation start after | Source/build constraint | Runtime acceptance after | Status |
|---|---|---|---|---|---|
| [P01](../phase-01-contract-build-gate.md) | A | None | Pure contracts/builds only; no missing future-module exports | Contract checks only; applicable environments below | pending / 0% |
| [P02](../phase-02-backend-canonical-inbox.md) | B | P01 | Repository logic can use P01 trait; main constructor/whole-server compile waits P03 real PushChannel | P03,P05,P11; applicable environments below | pending / 0% |
| [P03](../phase-03-fcm-http-v1-transport.md) | B | P01 | Notification crate real implementation after P01 | P02,P09,P11; applicable environments below | pending / 0% |
| [P04](../phase-04-react-admin-composer.md) | B | P01 | Existing frontend deps; report03 wire DTOs | P02,P03,P11; applicable environments below | pending / 0% |
| [P05](../phase-05-shared-remote-core.md) | B | P01 | Shared core compiles against P01 pure types; no host needed | P02,P03; applicable environments below | pending / 0% |
| [P06](../phase-06-shared-inbox-ui.md) | B | P01 | Shared UI compiles against P01; existing hosts compile after P09/P10 required signature migration | P05,P09,P10; applicable environments below | pending / 0% |
| [P07](../phase-07-linux-native-adapter.md) | B | P01 | Linux source/test compilation against P01; host integration P10 | P05,P10; applicable environments below | pending / 0% |
| [P08](../phase-08-windows-native-packaging.md) | B | P01 | Windows-native + Windows target build; Linux lock adjustments through same owner | P05,P10,P11; applicable environments below | pending / 0% |
| [P09](../phase-09-android-runtime-integration.md) | C | P01,P03,P05,P06 | Real P03/P05/P06 compile interfaces; backend still needed for live | P02,P11; applicable environments below | pending / 0% |
| [P10](../phase-10-desktop-runtime-integration.md) | C | P05,P06,P07,P08 | Real shared UI/core and both target adapters; test each host separately | P02,P03,P11; Linux evidence can satisfy pilot, Windows required for full delivery | pending / 0% |
| [P11](../phase-11-operations-config-deployment.md) | B | P01 | Draft assets/preflight without source; staging readiness waits actual consumers | P02,P03,P09,P10 Linux + P12 pilot proof/operator signoff for pilot; Windows-inclusive P12 proof for full rollout | pending / 0% |
| [P12](../phase-12-cross-system-acceptance-docs.md) | D | P02,P03,P04,P05,P06,P07,P08,P09,P10,P11 | Code/build dependencies unchanged; no application test/source ownership transfer | Pilot: Android/Linux/common safety proof, Windows acceptance may be held; full: every platform proof | pending / 0% |

## Wave gates and integration handoffs
1. **A start:** parent architecture gate and all reports read; P01 sole owner resolves common build/export/protocol prerequisites. **A end:** real pure Kotlin/Rust contracts, reviewed exact wire/schema/vectors, no unresolved design choices, common dependency/export/checksum ownership established. No stub backend/provider or not-yet-created module export. P01 updates its own build files later only on consumer requests; never transfers lock ownership.
2. **B start:** P01 frozen contract available. P02/03/04/05/06/07/08/11 implement disjoint components. P02 whole-server compile waits P03 provider construction; P06 root signature deliberately requires host migrations in C. Source owners may use injected test transports in owned behavior tests, never shipped fake fallbacks. **B component ends:** each publishes real implementation, targeted owner test results and remaining live gates; Windows real native early proof is mandatory, not a substitute design spike.
3. **C Android start:** P01+03+05+06 code/interfaces landed. **C desktop start:** P05+06+07+08 code/interfaces landed. Backend/provider/config readiness P02+03+11 needed for both runtime acceptance; a composed UI alone is not feature completion. **C end:** every host caller migrated, generation/drain/cancellation integrated, actual target build artifacts produced; platform behavior checks belong P09/P10. Any P01/P08 shared build request returns to that owner.
4. **D start:** all P02..11 implementation dependencies and applicable code/build checks landed; Mongo/Firebase/browser/Android/Linux environments available and deployment preflight complete. P12 first records pilot readiness under report05; Windows acceptance/environment evidence may remain held without becoming a fake pass. Main integrator schedules applicable combined checks, not simultaneous owner rebuilds. **D pilot exit:** real Android/Linux and common safety gates plus explicit operator/cohort approval permit restricted P11 enablement, while P12/full delivery remains pending. **D full exit:** actual installed Windows and every remaining report05 gate pass; only then complete P12/general rollout. Inherited release holds stay separately open.
5. Defect handoff uses original owner even sequentially. P12 records a failure and sends minimal reproduction to source owner; owner repairs/tests its files, returns hash/result; main reruns affected final gate. No broad final integration source-edit exception.

Each handoff message contains `phase=PXX; contract=v1; revision=<observed git revision>; files=<exact owned paths>; signatures/routes=<concrete exports>; behavior-checks=<commands and actual results or pending>; artifacts=<path+SHA256>; gates=<remaining prerequisites>`. No credentials/private content in messages. Consumer responds with accepted signatures or explicit mismatch; P01 owns protocol amendment. Handoff is evidence, not auto-passing runtime gates.

## Prerequisite owners and hard gates
| Gate | Owner | Must observe, not infer | Blocking effect |
|---|---|---|---|
| Pure API/build exports and cross-language vectors | P01 | Exact types/routes/error codes/cursor encoding, reviewed resolved dependencies | All B/C implementation starts |
| Mongo durable acceptance | P02 + P11 environment | hello/topology, journaling, w/j acknowledged write/read-back, indexes; real standalone crash/restart | Intake enable and durable acceptance |
| Firebase server custody/readiness | P03 code + P11 operator | project/scope/read-only credential/ADC/outbound TLS/OAuth, actual provider response | FCM integration/runtime acceptance |
| Android public project config/device | P09 + P11 operator | package dev.tabbyrush.android, Firebase app/sender/project, Google-enabled API33+ device and locked SDK manifest | Android build enabled/FCM live acceptance |
| Linux daemon/tray/session | P07 adapter + P10 host | actions-supported daemon, actual signal click; no-tray recovery; Wayland restore limitations | Required supported-Linux native acceptance |
| Windows native/installer environment | P08 + P10 | x64 MSVC/C++20/WinRT SDK, JDK21/WiX3 and installed per-user MSI shortcut identity/callback | Required for Windows/full acceptance; held Windows acceptance does not block restricted pilot |
| Pilot/full proof and release holds | P12 evidence + P11 operator, existing release owners | Android/Linux/common safety pilot proof+cohort approval; installed Windows for full proof; original G2/G3/G6/G7/checksum/Glance evidence separately | Pilot-ready is not full feature acceptance or inherited release clearance |

## Build and generated-artifact policy
- P01 only: H workspace Cargo.toml/Cargo.lock, server/notification Cargo.toml, T catalog/shared build/shared lock/dependency verification metadata. P08 only: desktop build and both target locks/native/packaging/scripts. P09 only: Android build/lock, Firebase components and security audit/tests. P02 only: server main/config/model/route/service registries. P11 only: deployment assets. P12 only: existing permanent release/architecture docs and new smoke evidence. P01 owns new permanent contract reference.
- No new frontend package dependencies: H root and app `pnpm-lock.yaml`, `glean-hub-app/package.json`, `pnpm-workspace.yaml` intentionally unchanged. H `glean-hub-server/Cargo.lock` is not the workspace authoritative lock and stays unchanged. T root build.gradle.kts/settings.gradle.kts/gradle.properties intentionally unchanged (no new Gradle module/plugin). No google-services plugin; P09 initializes FirebaseOptions from external public client config.
- P01 pins Firebase BoM34.4.0 and reviewed Rust OAuth/HTTPS dependencies; actual artifacts/provenance must resolve at Gate A before enabled builds. P09/P08 perform target resolutions but submit exact artifact/checksum requests to P01, not edit global verification file themselves. Existing verification/provenance holds are not waived; no `--dependency-verification=off` release proof.
- P08 `packageRemoteMsi` invokes own PowerShell build script solely for compiler/packaging, **not notification delivery**: Compose Windows app-image task -> native CMake x64 DLL -> app-owned image resource copy -> WiX3 heat fragment -> candle/light per-user MSI with MsiShortcutProperty AUMID. Preserve existing icon/upgrade UUID/signing custody. Plain Windows `packageMsi` must not silently produce an identity-incomplete release. Linux RPM pipeline unchanged. Native artifact load from installed app-owned path; no PATH probe.
- P09 external `TABBYRUSH_FIREBASE_CONFIG_PATH` JSON is provisioned by operator, not created/committed here; only public client options enter generated BuildConfig. No service-account file generated in repository. Explicit generated BuildConfig paths below are handoff ownership, not committed source. Retain no-backup install storage and all existing backup exclusions.
- P09 security policy must handle actual merged Firebase receiver: exact class+SEND permission+RECEIVE action/package category; existing MainActivity/widget allowlist preserved, every other exported component rejected. FirebaseInitProvider must be nonexported with exact package authority. No generic Firebase/provider exemption, Analytics dependency or broad exported permission exception. Negative fixtures required; source-only audit cannot satisfy APK gate.
- Compiler intermediates/caches/general app-image files are untracked implementation outputs, not shared source ownership; explicit native/installer/BuildConfig/evidence handoff outputs are enumerated. Do not commit DLL/MSI/generated source. P12 commits only sanitized evidence artifacts intentionally specified.
- Each owner keeps changed/new code focused and normally under200 lines/file. If splitting becomes necessary, amend this manifest+phase explicit paths before writing new file; never use a blanket directory claim. Permanent docs remain under800 lines; link focused contract/ops/evidence docs rather than inflate architecture pages.

## Full file manifest
Actions are **planned**, not performed. `generate` = build/evidence owner output only; no binary/design image masquerading as observed runtime proof.

| Absolute path | Exclusive owner | Action | Purpose |
|---|---|---|---|
| `/home/loidinh/WS/tabby-rush/integration/announcements/contract-v1.json` | P01 | create | Versioned route/DTO/error/limit/platform contract; machine-readable source for vectors, not generated runtime client. |
| `/home/loidinh/WS/tabby-rush/integration/announcements/vectors-v1.json` | P01 | create | Canonical normalization, UUIDv5, cursor and binding-generation golden vectors. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/remote-inbox-contracts.kt` | P01 | create | Pure remote state/actions/controller and native adapter boundaries; no runtime implementation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/remote-contract-vector-test.kt` | P01 | create | Golden contract serialization, limits and UUID input vectors. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/announcement_contract.rs` | P01 | create | Pure serde DTOs, transport trait and typed outcomes; no placeholder transport. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/lib.rs` | P01 | modify | Export pure announcement contract once; preserve existing producer exports. |
| `/home/loidinh/WS/glean-hub/Cargo.toml` | P01 | modify | Single workspace dependency declaration if required; workspace membership unchanged. |
| `/home/loidinh/WS/glean-hub/Cargo.lock` | P01 | modify | Only owner resolves/records all planned Rust dependencies. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/Cargo.toml` | P01 | modify | Declare backend Mongo/time/test dependencies ahead of components. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/Cargo.toml` | P01 | modify | Pin HTTP/OAuth dependencies for FCM component. |
| `/home/loidinh/WS/tabby-rush/gradle/libs.versions.toml` | P01 | modify | Pin Firebase BoM34.4.0/messaging alias and any common library aliases; no unreviewed version range. |
| `/home/loidinh/WS/tabby-rush/shared/build.gradle.kts` | P01 | modify | Public Compose exports required by hosts and Ktor streaming compile prerequisite; do not change Room schema. |
| `/home/loidinh/WS/tabby-rush/shared/gradle.lockfile` | P01 | modify | Shared dependency resolution owner. |
| `/home/loidinh/WS/tabby-rush/gradle/verification-metadata.xml` | P01 | modify | Only owner adds verified artifacts for shared/desktop/Android dependency requests, never disables strict verification. |
| `/home/loidinh/WS/tabby-rush/docs/announcement-contract.md` | P01 | create | Permanent concise API/invariant reference linking versioned vectors. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/main.rs` | P02 | modify | Own server route mount, feature startup/worker and shutdown integration. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/config.rs` | P02 | modify | Parse frozen announcement flags/project/credential path with no secret logging. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/models/mod.rs` | P02 | modify | Register backend model once. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/models/admin_announcement.rs` | P02 | create | Parent/preview/inbox/dispatch/install persistence records. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/mod.rs` | P02 | modify | Export new announcement route router. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/notifications.rs` | P02 | modify | Remove 501 token URL device stubs only; preserve subscribe/preferences/test email. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/admin_announcements.rs` | P02 | create | Admin preview/create/history/idempotency recovery router. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/announcement_inbox.rs` | P02 | create | User inbox/detail/read/unread/stream and install bindings. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/mod.rs` | P02 | modify | Register announcement services once. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_repository.rs` | P02 | create | Indexes, journaled parent insert, canonical queries, child upserts and cleanup. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_audience.rs` | P02 | create | Current DB role/grant resolution and preview confirmation. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_devices.rs` | P02 | create | Install proof/fencing CAS, token retirement and tombstones. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_worker.rs` | P02 | create | Recoverable parent materialization and per-target leased dispatch. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_stream.rs` | P02 | create | Authenticated app-scoped SSE ready/baseline and hint broker. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/channels/sse.rs` | P02 | modify | Add separate minimal recipient/app channel primitive preserving legacy send envelope. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_storage.rs` | P02 | create | Standalone real Mongo crash/checkpoint/retention and query contract tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_authorization.rs` | P02 | create | Current DB authorization, preview/idempotency and device-race tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_stream.rs` | P02 | create | Ready ordering, revoked JWT/grant, lag and silent recovered-hint tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/channels/push.rs` | P03 | modify | Replace legacy server-key/APNS stub with real OAuth HTTP v1 PushChannel and typed outcomes. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/service.rs` | P03 | modify | Migrate push construction/config callers; do not enable content-bearing legacy push producers. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/models.rs` | P03 | modify | Remove obsolete APNS-specific push documentation/fields only when no remaining caller; preserve old SSE DTO. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/tests/fcm_transport.rs` | P03 | create | OAuth/cache/concurrency/payload/error/timeout behavior tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/notification-management.tsx` | P04 | modify | Extend page alongside unchanged email permission management. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer.tsx` | P04 | create | Plain-text composition, audience controls, normalized confirmation and uncertain-result recovery. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-history.tsx` | P04 | create | Paginated sent history with honest materialization/provider states. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/services/admin-announcement-service.ts` | P04 | create | Canonical server URL/bearer API, frozen request and same-key recovery. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/services/admin-announcement-service.test.ts` | P04 | create | API DTO/error, request snapshot, replay and recovery tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer-state.ts` | P04 | create | Small pure composer state transitions, session-owned pending request persistence. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer-state.test.ts` | P04 | create | Confirmation invalidation, owner change, unknown-send and conflict tests. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` | P05 | modify | Narrow remote route classification, safe domain409 and per-stream timeout without origin weakening. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client.kt` | P05 | create | Typed authorized inbox/detail/read/device API client with frozen safe-retry semantics. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-stream-client.kt` | P05 | create | Bounded incremental SSE parser and authenticated connection lifecycle. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-controller.kt` | P05 | create | Concrete account-generation fenced runtime, bounded cache/read convergence/navigation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-cache.kt` | P05 | create | In-memory200-item page chain, expiry/server-time anchor and canonical merge. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client-test.kt` | P05 | create | Exact paths/auth/error/replay/timeout tests. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-stream-client-test.kt` | P05 | create | Ready/buffer/race/lag/ordinal/reconnect parser tests. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-inbox-controller-test.kt` | P05 | create | Crossdevice read refresh, expiry, stale generation, quiescence and cache limits. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` | P06 | modify | Add separate remote state/action inputs and inbox entry without TodoAction changes. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox.kt` | P06 | create | Accessible list, unread badge, pagination and loading/empty/offline/revoked/error states. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox-detail.kt` | P06 | create | Freshly authorized plain-text detail and canonical read acknowledgement UI. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/remote-inbox-navigation.kt` | P06 | create | Pure inbox route/detail entry state separate from task navigation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/remote-inbox-navigation-test.kt` | P06 | create | Safe native target, unavailable/account-clearing navigation and back behavior tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt` | P07 | modify | Reuse low-level transport and expose persistent lifecycle/signals without reminder ID collision. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-remote-notification-adapter.kt` | P07 | create | Remote capability/action mapping/daemon epoch and scoped cleanup implementation. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-remote-notification-adapter-test.kt` | P07 | create | Signal mapping, daemon restart, missing actions and teardown tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter-test.kt` | P07 | modify | Migrate transport fixtures if needed while preserving existing reminder assertions. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build.gradle.kts` | P08 | modify | Sole desktop build owner: native DLL resources, Windows MSI task and both platform dependency wiring. |
| `/home/loidinh/WS/tabby-rush/desktopApp/gradle-windows.lockfile` | P08 | modify | Windows lock resolution only owner; request checksum additions from01. |
| `/home/loidinh/WS/tabby-rush/desktopApp/gradle-linux.lockfile` | P08 | modify | Linux lock resolution only owner for any build requests from07. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-toast-bridge.kt` | P08 | modify | Replace PowerShell/typealias with real JNA native handle/callback and typed local methods. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt` | P08 | modify | Migrate all local reminder wrapper calls to typed native functions, remove global clearing/dead XML builders. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-remote-notification-adapter.kt` | P08 | create | RemoteNotificationAdapter with generation callback/tag/group/lifetime mapping. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/fake-windows-toast-bridge.kt` | P08 | modify | Migrate fixture to actual typed local bridge contract, no PowerShell executor fiction. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter-test.kt` | P08 | modify | Replace obsolete PowerShell-specific tests with native API boundary/local reminder regression tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-remote-notification-adapter-test.kt` | P08 | create | Callback drain, ABI mismatch, tag collision and scoped clear tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/CMakeLists.txt` | P08 | create | Real x64 Windows SDK/C++20 WinRT DLL build and native test target. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast.h` | P08 | create | Frozen C ABI v1 exported signatures and ownership comments. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast.cpp` | P08 | create | Dedicated MTA WinRT implementation, real Activated handlers and HRESULT boundary. |
| `/home/loidinh/WS/tabby-rush/desktopApp/native/windows/tabbyrush-toast-test.cpp` | P08 | create | Native XML escaping, handler/drain and command-error tests. |
| `/home/loidinh/WS/tabby-rush/desktopApp/packaging/windows/installer.wxs` | P08 | create | Owned per-user MSI WiX3 installer/shortcut AUMID identity preserving upgrade UUID. |
| `/home/loidinh/WS/tabby-rush/scripts/build-windows-remote-msi.ps1` | P08 | create | Build x64 DLL and Compose app image, heat/candle/light MSI using pinned local toolchain. |
| `/home/loidinh/WS/tabby-rush/scripts/windows-package-smoke.ps1` | P08 | modify | Verify installed shortcut/AUMID, DLL ABI/load and native activation without skipped assertions. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/native/windows/Release/tabbyrush-toast.dll` | P08 | generate | Native output handoff; generated, not source-controlled binary. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/windows-installer/application-files.wxs` | P08 | generate | Heat generated app-image file manifest; exclude from source control. |
| `/home/loidinh/WS/tabby-rush/desktopApp/build/compose/binaries/main/msi/TabbyRush-1.0.0.msi` | P08 | generate | Canonical per-user MSI artifact with correct shortcut; not checked in. |
| `/home/loidinh/WS/tabby-rush/androidApp/build.gradle.kts` | P09 | modify | Firebase dependency, external public config validation/generated BuildConfig and Android test wiring. |
| `/home/loidinh/WS/tabby-rush/androidApp/gradle.lockfile` | P09 | modify | Android resolution owner; provenance/checksums coordinated to01. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/AndroidManifest.xml` | P09 | modify | Native Messaging service/default channel/icon and permission; preserve other components. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt` | P09 | modify | Own remote controller lifetime and fenced close/drain. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt` | P09 | modify | Compose remote core/Android adapter/install coordinator and existing leave gates. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` | P09 | modify | Firebase init and retain fenced runtime on failed local drain, not clear active reference under surviving work. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | P09 | modify | Expose remote state/actions and current runtime generation to UI. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | P09 | modify | Bind inbox UI/permission and consume exact cold/warm remote extras safely. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt` | P09 | modify | Join remote local cancellation; best-effort unbind never blocks local leave. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-firebase-messaging-service.kt` | P09 | create | Native token/foreground message/onDeletedMessages entry with fixed generic alert only. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-notification-adapter.kt` | P09 | create | Separate channel/group/tag, capability checks, immutable intents and scoped cancellation. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-install-store.kt` | P09 | create | Keystore-encrypted no-backup install ID/proof/generation and pending install command atomic persistence. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-install-coordinator.kt` | P09 | create | Serialized persist-before-enqueue binding/token/switch/unbind operations and lost-response handling. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-notification-intents.kt` | P09 | create | Shared exact payload-key parsing, consumed-once target and auth-generation routing. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/res/drawable/remote_notification_icon.xml` | P09 | create | Dedicated monochrome system remote icon. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/res/values/remote-notification-strings.xml` | P09 | create | Fixed generic channel and capability copy, no private content resources. |
| `/home/loidinh/WS/tabby-rush/scripts/release-security-check.mjs` | P09 | modify | Narrow merged Firebase receiver allowlist preserving all other exported/service/provider prohibitions. |
| `/home/loidinh/WS/tabby-rush/scripts/release-security-check.test.mjs` | P09 | modify | Positive actual SDK merge and negative permission/action/authority/export fixtures. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/test/kotlin/dev/tabbyrush/android/remote-install-coordinator-test.kt` | P09 | create | Persistent generation races, lost response, logout offline and token rotation tests. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/remote-notification-integration-test.kt` | P09 | create | Cold/warm taps, permission/channel, generation and local drain UI/device tests. |
| `/home/loidinh/WS/tabby-rush/androidApp/build/generated/source/buildConfig/debug/dev/tabbyrush/android/BuildConfig.java` | P09 | generate | Public Firebase debug options from external config, no service credentials; generated only. |
| `/home/loidinh/WS/tabby-rush/androidApp/build/generated/source/buildConfig/release/dev/tabbyrush/android/BuildConfig.java` | P09 | generate | Validated release options matching Android package; generated only. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | P10 | modify | Compose process-owned remote runtime/controller/UI and installed AUMID startup/shutdown. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/composite-desktop-leave-cancel-gate.kt` | P10 | modify | Join remote cancellation/drain without blocking on remote network. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime.kt` | P10 | create | Process/session controller scope, activation dispatch, foreground/periodic and quit lifecycle. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-remote-platform.kt` | P10 | create | Platform-neutral provider interface consuming target-specific factories. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-remote-platform.kt` | P10 | create | Instantiate real Linux adapter for host runtime. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-remote-platform.kt` | P10 | create | Instantiate real DLL/JNA adapter and retain native lifetime for host runtime. |
| `/home/loidinh/WS/tabby-rush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-remote-runtime-test.kt` | P10 | create | Tray/process vs UI lifetime, quiesce, failed drain and stale activation tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/.env.example` | P11 | modify | Document disabled-by-default feature/FCM project/path configuration; no credentials. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/docker-compose.yml` | P11 | modify | Read-only secret-file mount reference and graceful worker shutdown config. |
| `/home/loidinh/WS/glean-hub/glean-hub-project/docker-compose.yml` | P11 | modify | Mirror canonical server feature/credential mount options, no secret literals. |
| `/home/loidinh/WS/glean-hub/k8s/glean-hub-host.yaml` | P11 | modify | Runtime secret file mount, single-process policy, SSE/probe/graceful rollout settings. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/Dockerfile` | P11 | modify | Ensure runtime CA certificates/readable nonroot secret mount and stop handling as needed. |
| `/home/loidinh/WS/glean-hub/scripts/announcement-preflight.mjs` | P11 | create | Non-destructive durability/topology/index/custody/provider preflight using disposable probe record and sanitized output. |
| `/home/loidinh/WS/glean-hub/docs/admin-announcement-operations.md` | P11 | create | Feature enable/rollback/retention/provider incident and credential rotation runbook. |
| `/home/loidinh/WS/tabby-rush/docs/remote-notification-setup.md` | P11 | create | Android public config path and desktop compiler/MSI/daemon operator prerequisites. |
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-smoke.mjs` | P12 | create | New real cross-repo HTTP/Mongo crash+SSE acceptance driver, no application-source repair. |
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.spec.ts` | P12 | create | Actual admin browser/inbox composer/confirmation/history smoke with real backend. |
| `/home/loidinh/WS/tabby-rush/scripts/admin-announcements-browser.config.ts` | P12 | create | Standalone browser smoke config using existing tooling without changing package manifests. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-acceptance.md` | P12 | create | Pending-to-observed matrix, commands/environment/hash/redacted result manifest. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-admin.png` | P12 | generate | Actual browser composer/confirmation/history capture only after smoke. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-android.png` | P12 | generate | Actual generic Android shade+authorized inbox capture. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-linux.png` | P12 | generate | Actual supported Linux native alert/inbox activation capture. |
| `/home/loidinh/WS/tabby-rush/docs/evidence/admin-announcements-windows.png` | P12 | generate | Actual installed Windows native alert/inbox activation capture. |
| `/home/loidinh/WS/tabby-rush/README.md` | P12 | modify | Document feature only to extent observed; link runtime limitations/evidence. |
| `/home/loidinh/WS/tabby-rush/docs/system-architecture.md` | P12 | modify | Reconcile proposed section8 to actual implementation and preserve inherited holds. |
| `/home/loidinh/WS/tabby-rush/docs/security-and-recovery.md` | P12 | modify | Document install proof/barrier, late generic push, revocation/offline/drain behavior. |
| `/home/loidinh/WS/tabby-rush/docs/deployment-guide.md` | P12 | modify | Link rollout/native/client setup and nondestructive rollback runbook. |
| `/home/loidinh/WS/tabby-rush/docs/android-acceptance.md` | P12 | modify | Record actual FCM/permission/tap/merged audit gates without clearing unrelated holds. |
| `/home/loidinh/WS/tabby-rush/docs/windows-acceptance.md` | P12 | modify | Record installed MSI/AUMID/Activated evidence and full-quit limitations. |
| `/home/loidinh/WS/tabby-rush/docs/windows-desktop-guide.md` | P12 | modify | Switch packaging instructions to identity-correct native MSI command and limitations. |
| `/home/loidinh/WS/tabby-rush/docs/project-overview-pdr.md` | P12 | modify | Describe agreed announcement behavior, retention and explicit exclusions. |
| `/home/loidinh/WS/tabby-rush/docs/codebase-summary.md` | P12 | modify | Add actual remote module/platform ownership after implementation. |
| `/home/loidinh/WS/tabby-rush/docs/project-roadmap.md` | P12 | modify | Record completion only after live gates and list retained release holds. |
| `/home/loidinh/WS/glean-hub/docs/system-architecture.md` | P12 | modify | Record real durable parent/worker/FCM/admin boundaries without modifying embed plans. |
| `/home/loidinh/WS/glean-hub/docs/CHANGELOG.md` | P12 | modify | Cross-repo announcement feature entry with observed limitations/gates. |
| `/home/loidinh/WS/tabby-rush/docs/linux-acceptance.md` | P12 | modify | Record actual Linux SSE/action/tray/full-quit acceptance separately from historical reminder holds. |
| `/home/loidinh/WS/tabby-rush/docs/linux-desktop-guide.md` | P12 | modify | Explain running-process inbox alerts, actions capability, no-tray recovery and full-quit limits. |
| `/home/loidinh/WS/tabby-rush/docs/project-changelog.md` | P12 | modify | Record TabbyRush feature changes and observed limitations, without crediting pending native gates. |
| `/home/loidinh/WS/tabby-rush/docs/code-standards.md` | P12 | modify | Reconcile exact remote route/lifecycle and protected Firebase merged-component security policy. |
| `/home/loidinh/WS/tabby-rush/docs/api-contract.md` | P12 | modify | Link canonical announcement API/DTO contract and separate it from strict task-sync endpoints. |
| `/home/loidinh/WS/tabby-rush/docs/authentication-and-credentials.md` | P12 | modify | Document bearer-only remote allowlist, install proof custody and account-generation fencing. |
| `/home/loidinh/WS/tabby-rush/docs/design-guidelines.md` | P12 | modify | Document inbox entry/detail/read/permission/offline UI and native navigation accessibility. |

## Planning artifacts and intentionally unchanged inputs
Planning creates/updates only artifacts under this plan directory and the explicitly proposed architecture section. Validation decisions are propagated through plan/access point, P10/P11/P12 and design/contract/execution/acceptance/review reports. Raw research and brainstorm remain historical inputs. This manifest governs **future** application implementation; no listed application source path is created/modified by planning.

## Checked prerequisites / unresolved questions
- [ ] P01 provenance/dependency/artifact resolution and frozen contract vector gate.
- [ ] P02/P11 Mongo durability/current grant environment read-back.
- [ ] P03/P09/P11 Firebase server credentials/public client config/Google-enabled device.
- [ ] P07/P08/P10 real Linux daemon/tray and Windows x64 SDK/MSI testbed.
- [ ] P12 all observed feature gates; inherited release holds tracked separately.
- No unresolved protocol/product decision; no missing environment is implicitly waived.
