# Compose Desktop sticky surfaces — research

Research date: 2026-10-16. Planning evidence only; no build or native acceptance performed.

## Findings

- **Pinned API evidence:** Compose Multiplatform core tag `v1.7.3` defines `Window(..., visible, alwaysOnTop, state, onCloseRequest)`; `alwaysOnTop` is documented as above other windows. `visible=false` preserves internal state and native resources; removing the `Window` from composition disposes its native window. `WindowState` observes user size/position changes. See [pinned `Window.desktop.kt`](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.7.3/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/Window.desktop.kt).
- **Application lifetime:** Keep one Compose `application` and controller/runtime; render one main and one sticky `Window`, each controlled by retained visibility state. Close callbacks only hide their own window. `Tray` can expose Open app, Show/Hide sticky and Quit; only explicit Quit calls application exit and controller disposal. JetBrains docs show multiple windows, close interception, tray restoration, and `exitApplication`; docs are current, so signatures beyond the pinned `Window` API need compile-time confirmation against 1.7.3: [Top-level windows management](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html).
- **One instance/one sticky:** Repo `single-instance-guard.kt` documents and implements one process per profile/directory using an OS file lock, acquired before vault/database initialization; it does not create a second app for the sticky. Keep sticky as one boolean/window in that process—no window-per-show, new process, DB or controller. Do not assume lock contention forwards activation to an existing window; repo guard evidence only establishes refusal to acquire.
- **Disposal boundary:** `DesktopController.disposeSuspend()` at `desktopApp/.../main.kt:766+` tears down scheduler/runtime. Move its invocation from ordinary window close to the single Quit path. Hiding/removing UI must not dispose controller, sync/runtime or release profile lock. Ensure every Quit route converges on idempotent teardown, then exits.
- **Persistence/geometry:** Persist only local presentation preferences: sticky visible flag, size, position (and only add other fields if needed). Restore on manual launch; no auto-start. Use `WindowState` changes for capture, but clamp/reposition at restore using current usable display bounds and the product's minimum size. Preserve legitimate negative virtual-screen origins; do not clamp coordinates to primary-screen origin. If saved monitor disappeared, move enough of the window onto a current display so title bar/content can be reached. DPI changes can alter apparent dimensions: validate saved bounds after conversion on the active display rather than assuming old coordinates remain valid. JDK exposes current display configurations/bounds and screen insets: [GraphicsEnvironment](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/GraphicsEnvironment.html), [GraphicsConfiguration](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/GraphicsConfiguration.html). Compose 1.7.3 source translates native AWT x/y/size into `WindowState` values; mixed-DPI and work-area restoration behavior is not guaranteed by the API docs.
- **Tray fallback:** Tray API example uses a native tray icon to restore hidden UI. Do not hide every recovery/quit route when native tray is unavailable: when unsupported, keep at least one app window visible (e.g. refuse to hide the last visible window) or provide an equivalent visible recovery/quit route. Verify support on the target host/session, not merely that Compose exposes `Tray`. Never rely on tray for initial/launch recovery.
- **Windows:** `alwaysOnTop=true` is the available request, not proof of universal stacking. Manually check it stays above ordinary windows, not secure/system screens or every exclusive-fullscreen app. Repeated Show must reuse same window. Test manual relaunch restores sticky visibility/bounds; single-instance lock does not itself imply activation forwarding.
- **Linux:** Native tray and always-on-top/focus behavior depend on desktop/session/window manager; do not infer support from Windows or API presence. Run acceptance on an actual Linux machine and record distro, desktop/session, tray availability, focus/stacking, and geometry restoration. Linux remains unverified until then.

## Implementation split

- **One owner:** existing monolithic `desktopApp/.../main.kt` / controller integration: application/window composition, single visibility state, tray actions, Quit ordering, controller lifetime and stable state/action contracts. Keep the existing `SingleInstanceGuard` ownership/profile lock; no second runtime.
- **Separate owner:** compact sticky UI plus small local presentation-preference and geometry helper, against contracts agreed with the integration owner. UI consumes existing controller/task state and actions; preferences contain only visibility/geometry, not task/account state. Avoid parallel edits to `main.kt`.

## Runtime checks / risks

1. Windows: launch; assert one process/profile, one sticky and one tray; Show repeatedly; Close each window independently; tasks/sync still work while main hidden; tray restores either; Quit exits, disposes runtime and releases profile lock.
2. Restart: test visible and hidden sticky preference, resize/move, negative virtual-screen coordinates, monitor unplug/replug, and per-monitor DPI changes. Confirm minimum size and title bar remain reachable; relaunch manually, no autostart registration.
3. Stacking: verify above ordinary apps and document limits for fullscreen/system windows; test focus/activation after Show.
4. Linux: repeat applicable checks on real target session; record actual tray/stacking/focus behavior. Windows results cannot certify Linux.
5. **Not tested:** no build or runtime verification performed; current docs are not pinned evidence for all Tray/window signatures or platform support. Verify exact Compose 1.7.3 APIs and supported native session behavior during implementation.

## Primary citations

- [Compose Desktop top-level windows docs](https://kotlinlang.org/docs/multiplatform/compose-desktop-top-level-windows-management.html) — window visibility/lifetime, state, tray example.
- [Compose Multiplatform core `v1.7.3` Window source](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.7.3/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/Window.desktop.kt) — pinned signatures and lifecycle semantics.
- [Java `GraphicsEnvironment`](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/GraphicsEnvironment.html), [Java `GraphicsConfiguration`](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/GraphicsConfiguration.html) — runtime display enumeration/bounds primitives.
- Repo evidence: `desktopApp/src/main/kotlin/dev/doggo/desktop/single-instance-guard.kt`; `desktopApp/src/main/kotlin/dev/doggo/desktop/main.kt` (controller disposal at line 766 onward); approved brainstorm `plans/reports/brainstorm-261016-1302-sticky-task-surfaces.md`.

## Unresolved questions

- Which Linux distro/desktop session is the implementation's acceptance target?
- Does Compose Desktop 1.7.3 `Tray` degrade cleanly on each intended Linux session, or must fallback behavior be selected per environment?
- What is the current startup behavior when the profile lock is already held (exit only versus activation/focus handoff)? The guard alone does not answer this.
