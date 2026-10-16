# Sticky surfaces — repository analysis and phase contracts

Date: 2026-10-16. Planning only; no builds, tests, or UI runtime verification performed.
Root: `G:/ws/sharing/doggo`; Windows x64; branch `main` from injected context.

## Inputs and workflow evidence
- Approved scope: [brainstorm](../../reports/brainstorm-261016-1302-sticky-task-surfaces.md), especially requirements and acceptance 1–12.
- Read README, docs/codebase-summary.md, code-standards.md, system-architecture.md, project-overview-pdr.md, design-guidelines.md.
- Summary generated 2026-10-16; conditional `/evc-cmd-scout` correctly skipped. No scout report exists or is required. `scout/` directory reserved, empty.
- Loaded published planning skill and all six planning references, research, mobile-development, docs-seeker, mermaidjs-v11. Local `.omp/` absent; required workflows resolved from published HOME installation.
- docs-seeker scripts ran; Context7 returned no matching documentation; official platform documentation fallback in researcher reports.
- LSP status: no language servers configured. Future implementation must query references if Kotlin LSP becomes available.
- Plan activation utility ran but warned `EVCRATE_SESSION_ID` absent: state not persisted. Always pass this plan directory explicitly; do not claim active-plan persistence.

## Verified source seams
| Files | Observed behavior / planning consequence |
| --- | --- |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-store.kt:5-18` | Snapshot load/save/delete; schedule Flow only. No general task-list observation or explicit idempotent completion setter. |
| `shared/src/commonMain/kotlin/dev/doggo/shared/data/room-account-store.kt:49-98,179-190` | Replayed conflated invalidation after mutations; loadTodos overlays base/drafts/outbox, sorted createdAt DESC then id DESC. Save under gate admission + mutex. Reuse existing invalidation, not polling or a second store. |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/persisted-task-schedule.kt:8-33` | Committed scheduling snapshot; reminder predicate additionally requires enabled rule and conflict-free state. Deadline notification needs separate eligibility; do not change reminder eligibility. |
| `shared/src/commonMain/kotlin/dev/doggo/shared/contract/persisted-schedule-projector.kt:12-184` | Base plus submitted outbox projection; no editor drafts. Reuse deadline projection without computing times from strings. |
| `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:46-121` | Wipe recovery synchronous, session restoration asynchronous. getOrCreateAccountRuntime accepts an explicit key and can close prior runtime. External callers must resolve only current account after readiness, never invoke this with a stale action account. |
| Android `session-view-model.kt:58-111,301-345,437-469` | allTodos separate from filtered uiState; reload tied to ViewModel. Toggle copies potentially stale Todo. Existing quick-add uses full existing editor/validation. Pending notification task routing only account-checks today. |
| Android `main-activity.kt:27-63` | Handles task deep-link, no widget create route/runtime permission request. onResume discards reminder capability result. New surface permission flow must be real, not reuse no-op TodoAction handlers. |
| Android `doggo-sync-worker.kt:29-68` | Auth/generation/wipe checks and reconcilePending; cold auth retries. Integrate surface refresh without new network cadence. |
| Android `account-runtime-factory.kt:48-100` | Runtime holds reminder observer and composite leave gate. One integration owner must wire surface sink, action admission, observers and teardown. |
| Android `android-account-state-manager.kt:57-89` | Startup wipe cleanup cancels existing reminders only. Must clear new notification, jobs, preferences, launcher-retained contents before removing marker. |
| Android `AndroidManifest.xml:21-47` | Single exported MainActivity; alarm/restore receivers private. Widget receiver launch mechanism needs carefully justified platform configuration and merged manifest audit. |
| `scripts/release-security-check.mjs:15,160+` | Existing audit allows only MainActivity exported. Prefer private widget receiver if supported; otherwise narrow audited widget exception, never blanket receiver allowance. |
| `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt:84-128,291-405,592-610,766-835` | Controller/runtime and application windows in one file. Main Close calls dispose/exit. Sole integration phase owns this file. Separate sticky model must not consume filtered uiState.items. |
| `desktopApp/build.gradle.kts:10-35,94-104` | Rejects host/target mismatch; runDebug custom task. Linux acceptance cannot run on Windows. |
| `docs/system-architecture.md:589-600` | Existing process scheduler lifetime is window-bound today. Planned Close hides changes this documented contract. Windows toast bridge remains unrelated unavailable candidate; don't expand scope into fixing it. |

## Recommended exclusive ownership / execution waves
1. Shared foundation + Android dependency setup: account-store.kt, room-account-store.kt, shared surface contracts/projector/selection and behavioral tests, Android Gradle/catalog/verification/lock artifacts. Freeze APIs and select stable Glance with compile proof.
2. Concurrent leaf slices after foundation: Android widget files/resources/tests; Android next-deadline notification/preferences/permission UI files/tests; desktop compact UI/geometry/preferences files/tests. No edits to existing Android composition roots or desktop main.kt.
3. Android integration sole owner: all existing Application/runtime/VM/Activity/worker/restore/wipe/cancel-gate/manifest/auditor touchpoints. Consumes completed widget and notification modules; no stubs committed to fake independence.
4. Desktop integration sole owner: main.kt, any extracted controller/runtime files, lifecycle/tray shell and tests. Can run alongside Android integration; consumes finished desktop compact UI slice.
5. Integrated Android/Windows acceptance + shared docs owner after both integrations; no production fixes in validation phase. Findings return to original owner without transferring file ownership.
6. Linux-specific implementation/acceptance on its own machine; owns Linux-only adaptations/evidence/docs. Missing Linux machine is explicit pending gate, not Android/Windows acceptance blocker or implicit completion.

## Contract details planner must settle explicitly
- Immutable sections preserve loadTodos ordering; common projection partitions incomplete/completed stably. Desktop publishes unfiltered authoritative state separately from main filter.
- Add a store-level completion setter by ID/desired boolean under a single existing mutation admission+mutex; private locked helpers avoid recursive mutex acquisition. Preserve current title/deadline/reminder and tombstones. Repeated Complete=true is no-op; widget passes desired checked state, never blind toggle.
- Do not change exported AccountStore without migrating all concrete implementors/test doubles within its sole foundation owner; enumerate these in ownership manifest during planning.
- Prefer generic task invalidation/observation built atop existing store mutation notifications; reminder-specific projection must not become general widget data source. Cover mutations, sync promotion/conflict actions, startup/account change; avoid duplicate event systems.
- Account/generation token on every external mutation/navigation; wait boundedly for restoration, validate active key, unlocked storage, wipe marker, generation and current runtime before access and before OS publication. Track in-flight surface operations; drain before Room close. Clear old state before rendering replacement. No network under store lock.
- Widget and deadline display use local state even offline; notification committed projection excludes draft-only edits. UI editor in-memory changes never refresh OS display as if saved.
- Deadline selection: define overdue comparator and equality boundary explicitly using persisted overdueEpochMs; date-only anchor is reminder time, not overdue boundary. Document upcoming ordering field; don't introduce reminder-enabled dependency. Task ID deterministic tie.
- Notification opt-in local/account-scoped file/preferences, default off, separate quiet channel/tag and generic public version. Persist dismissal suppression and define reset; no immediate repost on worker/clock/sync. No new exact permission or static FGS.
- Presentation persistence outside Room/schema/task sync; no task text or credentials in geometry files. Clamp against actual monitor work areas including negative coordinates and DPI; preserve close-to-hide and explicit Quit when tray unavailable.
- Existing PDR says 100% shared UI; final docs must distinguish shared app UI from native Glance/platform shell without rewriting historical acceptance.
- Tests owned with module implementation, not a later overlapping omnibus test phase. Runtime acceptance checks exact launcher/shade/window/tray behavior and records real evidence, not only compilation.

## Acceptance coverage required
Map all brainstorm criteria 1–12 to owning phase + verification evidence. Include process-death and wipe interruption, same-account new-generation replay, completion duplicate/concurrent edit, offline add + sync convergence, reminder-disabled deadline, date-only/ties/time movement, permission/channel/dismissal independence, multiple widget IDs, sticky singleton, hidden-main sync, explicit Quit/lock reacquire, off-screen geometry and no-tray recovery, and distinct Linux session proof.

## Unresolved questions
No blocking product questions. Exact stable Glance combination needs compile proof; dismissal reset and upcoming date-only ordering require explicit conservative plan decisions; Linux distribution/session/machine needed only for later Linux gate.
