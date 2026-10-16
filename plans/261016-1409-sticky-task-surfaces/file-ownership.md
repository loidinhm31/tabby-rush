# Exclusive implementation file ownership

Repository root: `G:/ws/sharing/doggo/`. Every path below is repository-relative; one exact path per row. `M` existing modify; `C` proposed create; `R` retained/inspect-only metadata, phase01 alone may modify if dependency resolution actually changes it. **No source implementation is authorized by this planning artifact.** Ownership is exclusive across phases, including later fixes. Read-only dependencies are not ownership. Phase07/08 return common defects to original owner; they never edit another phase's tests/source/docs. An unlisted necessary file requires a manifest/contract update before work, not opportunistic edits.

## Phase01 — contracts, authoritative store and build foundation

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-store.kt` | 01 | M | General observation and desired-completion contract |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-contracts.kt` | 01 | C | Proposed token/readiness/access/result/snapshot and completion types |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/task-surface-projector.kt` | 01 | C | Proposed stable partition, deadline selection and selection key |
| `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt` | 01 | M | Sole discovered AccountStore implementor; locked helpers and invalidation |
| `shared/src/commonTest/kotlin/dev/doggo/shared/contract/task-surface-projector-test.kt` | 01 | C | Proposed ordering, conflicts, deadline/tie/date-only boundaries |
| `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-store-sqlite-test.kt` | 01 | C | Proposed real-SQLite observer/completion/concurrent edit and reopen tests |
| `shared/src/jvmTest/kotlin/dev/doggo/shared/data/task-surface-sync-sqlite-test.kt` | 01 | C | Proposed real-sync promotion, reconciliation and conflict invalidation |
| `shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt` | 01 | M | Existing persistence/outbox/temporal regression coverage |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-host.kt` | 01 | C | Proposed host owner/access and refresh-reason interface only |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-task-model.kt` | 01 | C | Proposed compact state/actions independent of main editor |
| `gradle/libs.versions.toml` | 01 | M | Aligned stable Glance artifacts, test dependency only if needed |
| `androidApp/build.gradle.kts` | 01 | M | Glance/test dependency integration; retain SDK levels |
| `gradle/verification-metadata.xml` | 01 | M | Authenticate newly introduced artifacts only; preserve baseline hold |
| `androidApp/gradle.lockfile` | 01 | M | Exact Android dependency resolution |
| `shared/gradle.lockfile` | 01 | R | Sole owner of any transitive shared change |
| `desktopApp/gradle-windows.lockfile` | 01 | R | Sole owner of Windows resolved metadata changes |
| `desktopApp/gradle-linux.lockfile` | 01 | R | Sole owner; affected resolution needs actual Linux host |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-01-foundation-evidence.md` | 01 | C | Proposed contract/compatibility/provenance evidence, <=150 lines |

No AccountStore delegation or interface test double was found in supplied source search; RoomAccountStore is the sole concrete implementation. Existing callers retain load/save methods. Phase01 repeats reference discovery before edit; any newly discovered implementor/test double belongs to 01 and must be enumerated here before implementation. No generated schema paths are allowed.

## Phase02 — Android widget leaf

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `androidApp/src/main/kotlin/dev/doggo/android/task-widget.kt` | 02 | C | Proposed real Glance responsive sections |
| `androidApp/src/main/kotlin/dev/doggo/android/task-widget-receiver.kt` | 02 | C | Proposed launcher receiver, cold-safe host updates |
| `androidApp/src/main/kotlin/dev/doggo/android/task-widget-actions.kt` | 02 | C | Proposed desired-state callback and immutable Add/Open intents |
| `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt` | 02 | C | Proposed all-ID refresh/clear and session quiescence |
| `androidApp/src/main/res/xml/task-widget-info.xml` | 02 | C | Proposed launcher provider sizing/metadata |
| `androidApp/src/main/res/layout/task-widget-initial.xml` | 02 | C | Proposed non-sensitive initial/loading RemoteViews |
| `androidApp/src/main/res/values/task-widget-strings.xml` | 02 | C | Proposed accessible widget labels/states |
| `androidApp/src/test/kotlin/dev/doggo/android/task-widget-model-test.kt` | 02 | C | Proposed production projection/action payload boundaries |
| `androidApp/src/androidTest/kotlin/dev/doggo/android/task-widget-device-test.kt` | 02 | C | Proposed real widget render/callback/all-ID tests |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-02-widget-evidence.md` | 02 | C | Proposed leaf proof and pending integrated launcher checks |

## Phase03 — Android notification/settings leaf

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-preferences.kt` | 03 | C | Proposed local account opt-in and durable suppression/receipt |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-notification.kt` | 03 | C | Proposed quiet channel/post/cancel coordinator |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-intents.kt` | 03 | C | Proposed Complete/Open/delete identities and parsing |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-action-receiver.kt` | 03 | C | Proposed private admitted Complete callback |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-dismiss-receiver.kt` | 03 | C | Proposed posted-receipt-checked durable dismissal |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-time-receiver.kt` | 03 | C | Proposed bounded clock/date/zone reconciliation |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-refresh-worker.kt` | 03 | C | Proposed inexact no-network-constrained boundary work |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-permission-controller.kt` | 03 | C | Proposed capability snapshots, separate opt-in permission flow support |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-settings.kt` | 03 | C | Proposed standalone settings composable with real callbacks |
| `androidApp/src/main/res/drawable/task-surface-notification-icon.xml` | 03 | C | Proposed monochrome small icon |
| `androidApp/src/main/res/values/task-surface-strings.xml` | 03 | C | Proposed channel/rationale/privacy/unavailable labels |
| `androidApp/src/test/kotlin/dev/doggo/android/task-surface-notification-test.kt` | 03 | C | Proposed production selection/capability/dismissal transition tests |
| `androidApp/src/androidTest/kotlin/dev/doggo/android/task-surface-preferences-device-test.kt` | 03 | C | Proposed actual persistence/receipt/process-reopen tests |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-03-notification-evidence.md` | 03 | C | Proposed leaf evidence; no native acceptance overclaim |

## Phase04 — desktop compact presentation leaf

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-task-content.kt` | 04 | C | Proposed scrollable compact UI and independent quick-add callbacks |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-window-geometry.kt` | 04 | C | Proposed multi-monitor/DPI work-area geometry conversion/clamp |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/sticky-window-preferences.kt` | 04 | C | Proposed atomic presentation-only file |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/sticky-window-geometry-test.kt` | 04 | C | Proposed negative origin/DPI/hotplug/minimum boundary tests |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/sticky-window-preferences-test.kt` | 04 | C | Proposed real temp-file corrupt/restart/atomic replacement tests |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-04-compact-evidence.md` | 04 | C | Proposed leaf compile and geometry proof |

## Phase05 — Android integration single owner

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt` | 05 | M | Real host/readiness/process-owned coordination |
| `androidApp/src/main/kotlin/dev/doggo/android/account-runtime.kt` | 05 | M | Surface observer/admission lifetime |
| `androidApp/src/main/kotlin/dev/doggo/android/account-runtime-factory.kt` | 05 | M | Real coordinator wiring and teardown hooks |
| `androidApp/src/main/kotlin/dev/doggo/android/android-account-state-manager.kt` | 05 | M | Startup marker recovery and OS clearing |
| `androidApp/src/main/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate.kt` | 05 | M | Drain observers/jobs/OS surfaces before Room close |
| `androidApp/src/main/kotlin/dev/doggo/android/session-view-model.kt` | 05 | M | ID completion, fresh navigation, create route, UI-independent observer use |
| `androidApp/src/main/kotlin/dev/doggo/android/main-activity.kt` | 05 | M | ActivityResult permission, Add/Open/settings routes and resume |
| `androidApp/src/main/kotlin/dev/doggo/android/doggo-sync-worker.kt` | 05 | M | Existing sync completion refresh without cadence change |
| `androidApp/src/main/kotlin/dev/doggo/android/android-sync-scheduler.kt` | 05 | M | Existing per-account cancellation composition, preserve cadence |
| `androidApp/src/main/kotlin/dev/doggo/android/reminder-restore-receiver.kt` | 05 | M | Existing startup/restore entry reconciles independent surfaces |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-access.kt` | 05 | C | Proposed real runtime access and serialized publication implementation |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-runtime-coordinator.kt` | 05 | C | Proposed process-owned observers and refresh coalescing |
| `androidApp/src/main/kotlin/dev/doggo/android/task-surface-create-screen.kt` | 05 | C | Proposed compact creation route reusing existing editor/validation |
| `androidApp/src/main/AndroidManifest.xml` | 05 | M | Register completed components with narrow exported policy |
| `scripts/release-security-check.mjs` | 05 | M | Source/merged manifest widget allowlist and invariants |
| `scripts/release-security-check.test.mjs` | 05 | C | Proposed production auditor XML/manifest policy behavior tests |
| `androidApp/src/test/kotlin/dev/doggo/android/composite-leave-scheduler-cancel-gate-test.kt` | 05 | M | Existing cancellation regressions |
| `androidApp/src/androidTest/kotlin/dev/doggo/android/task-surface-account-device-test.kt` | 05 | C | Proposed real runtime/Room readiness, stale generation and wipe races |
| `androidApp/src/androidTest/kotlin/dev/doggo/android/task-surface-navigation-device-test.kt` | 05 | C | Proposed actual ActivityResult/Add/Open account-safe route tests |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-05-android-evidence.md` | 05 | C | Proposed owner integration evidence and security audit |

## Phase06 — desktop integration single owner

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` | 06 | M | Sole main/controller/runtime integration and creation helper |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-task-surfaces.kt` | 06 | C | Proposed real access implementation, singleton window/tray shell and actions |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/desktop-surface-platform.kt` | 06 | C | Proposed complete generic AWT implementation and Linux provider seam |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/composite-desktop-leave-cancel-gate.kt` | 06 | M | Surface observer drain/clear in account teardown |
| `desktopApp/src/main/resources/task-surface-tray.png` | 06 | C | Proposed bundled native tray image, no external asset dependency |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-lifecycle-test.kt` | 06 | M | Close hides versus Quit/profile-lock contract |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-reminder-controller-lifecycle-test.kt` | 06 | M | Process lifetime reminder observation regression |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/composite-desktop-leave-cancel-gate-test.kt` | 06 | M | Surface and existing scheduler teardown ordering |
| `desktopApp/src/test/kotlin/dev/doggo/desktop/desktop-task-surfaces-test.kt` | 06 | C | Proposed real controller/SQLite input-isolation, account and singleton behavior |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-06-desktop-evidence.md` | 06 | C | Proposed owner integration and Windows native smoke record |

No controller extraction into extra files is assumed: keep existing controller in main.kt and split new shell/access code into the declared file. Any extraction needs a manifest amendment; no second owner for main.kt.

## Phase07 — integrated Android/Windows acceptance and affected shared docs

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `README.md` | 07 | M | Feature index, native/shared presentation distinction and close behavior |
| `docs/project-overview-pdr.md` | 07 | M | Surfaces scope and correct 100%-shared-UI claim only where affected |
| `docs/system-architecture.md` | 07 | M | Post-implementation §7 drift and process lifetime, preserve release holds |
| `docs/codebase-summary.md` | 07 | M | New components and actual source structure |
| `docs/code-standards.md` | 07 | M | Admission/desired-state/native-surface boundary conventions |
| `docs/design-guidelines.md` | 07 | M | Compact/widget sections, accessibility, unavailable states |
| `docs/local-storage-and-sync.md` | 07 | M | Observer, completion atomicity, local-only preferences |
| `docs/authentication-and-credentials.md` | 07 | M | Current-account generation admission and bounded restore |
| `docs/security-and-recovery.md` | 07 | M | OS-retained content, wipe recovery, private notification/actions |
| `docs/android-acceptance.md` | 07 | M | Real launcher/shade/permission/dismissal evidence and holds |
| `docs/windows-acceptance.md` | 07 | M | Window/tray/geometry/runtime/profile-lock evidence |
| `docs/windows-desktop-guide.md` | 07 | M | Close-to-hide, explicit Quit, manual relaunch and tray fallback |
| `docs/deployment-guide.md` | 07 | M | Setup and supported surface capability limits; no auto-start |
| `docs/toolchain-versions.md` | 07 | M | Actually resolved Glance version and compatibility evidence |
| `docs/project-changelog.md` | 07 | M | Delivered scope only after evidence |
| `docs/project-roadmap.md` | 07 | M | Android/Windows status separate from pending Linux/release holds |
| `docs/development-roadmap.md` | 07 | M | Keep existing second roadmap consistent for this feature only |
| `docs/evidence/sticky-surfaces-android-summary.md` | 07 | C | Proposed redacted Android run/evidence index |
| `docs/evidence/sticky-surfaces-windows-summary.md` | 07 | C | Proposed redacted Windows run/evidence index |
| `docs/evidence/sticky-surfaces-android-widget.png` | 07 | C | Proposed captured actual responsive launcher state |
| `docs/evidence/sticky-surfaces-android-notification.png` | 07 | C | Proposed captured actual shade/private-public behavior |
| `docs/evidence/sticky-surfaces-windows-window.png` | 07 | C | Proposed captured actual main/sticky/ordinary-window stacking |
| `docs/evidence/sticky-surfaces-windows-tray.png` | 07 | C | Proposed captured actual singleton tray/menu |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-07-acceptance-evidence.md` | 07 | C | Proposed final integrated matrix verdict, <=150 lines |

Screenshots are future real captures, never generated mock UI. Redact test account details; summaries may link timestamped local recordings without adding unowned repository files. Do not revise historic evidence or unrelated release-acceptance/strict-v2/backend documents to manufacture closure.

## Phase08 — later Linux-only implementation/session gate

| Path | Owner | Action | Reason |
| --- | --- | --- | --- |
| `desktopApp/src/linuxMain/kotlin/dev/doggo/desktop/linux/linux-task-surface-platform.kt` | 08 | C | Proposed real Linux session/tray capability adapter using generic seam |
| `desktopApp/src/linuxMain/resources/META-INF/services/dev.doggo.desktop.DesktopSurfacePlatformProvider` | 08 | C | Proposed Linux-only provider registration |
| `desktopApp/src/linuxTest/kotlin/dev/doggo/desktop/linux/linux-task-surface-platform-test.kt` | 08 | C | Proposed real environment/capability/geometry boundary tests |
| `docs/linux-acceptance.md` | 08 | M | Separate real-session surface verdict, do not close native reminder holds |
| `docs/linux-desktop-guide.md` | 08 | M | Session/tray/focus/no-tray/manual restore instructions |
| `docs/evidence/sticky-surfaces-linux-summary.md` | 08 | C | Proposed distro/session/window manager/capability/runtime evidence |
| `docs/evidence/sticky-surfaces-linux-window.png` | 08 | C | Proposed actual target-session window/stacking capture |
| `docs/evidence/sticky-surfaces-linux-recovery.png` | 08 | C | Proposed actual tray or visible no-tray recovery capture |
| `plans/261016-1409-sticky-task-surfaces/reports/phase-08-linux-evidence.md` | 08 | C | Proposed Linux gate verdict; pending until machine available |

## Read-only dependencies and unchanged boundaries

- Shared `todo-models.kt`, `persisted-task-schedule.kt`, `persisted-schedule-projector.kt`, title/deadline helpers, `reminder-delivery-gate.kt`, `account-leave-protocol.kt`, sync coordinator/reconciliation/full-scan/transition source and existing contracts/tests: read for invariants; no duplicate implementation or source edits planned.
- Existing Android reminder adapter/intents/permission controller/alarm receiver, backup rules and data-extraction rules: preserve behavior; task preferences under existing private files and allowBackup=false, no new backup exception.
- Existing desktop single-instance guard, scheduler, platform vault/reminder adapters, desktop build.gradle.kts: read-only. Existing host-selected source sets load Linux provider without build edits. Windows native toast candidate remains unchanged.
- Existing research/reports01/02 and approved brainstorm remain read-only. Planning artifact author owns only new Markdown here; future implementation rows above are assignments, not present changes. [Dependency matrix](dependency-matrix.md) distinguishes runtime prerequisites from disjoint edit paths.
