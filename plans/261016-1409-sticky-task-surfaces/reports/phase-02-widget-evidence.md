# Phase 02 — Android Glance Widget Leaf Evidence Report

**Status:** DELIVERED / READY FOR INTEGRATION (GATES PENDING PHASE 05/07)  
**Date:** 2026-10-16  
**Author:** Phase 02 Android Widget Leaf Owner  

---

## 1. Executed & Delivered Components

All assigned production, resource, and test artifacts were created strictly within the Phase 02 boundary:

1. **`androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget.kt`**:
   - `TabbyRushTaskWidget : GlanceAppWidget()`:
     - `SizeMode.Responsive` configured with 4 standard launcher grid sizes: `120x120dp` (2x2), `240x120dp` (3x2), `240x240dp` (3x3), `320x360dp` (4x4+).
     - Single scrollable `LazyColumn` partitioned into Incomplete and Completed sections using `partitionSurfaceTodos`.
     - `items` utilizes `itemId = { it.id.hashCode().toLong() }` for stable Glance item identities.
     - Accessible tap targets ≥48dp (task row height 48dp, Add button touch target 48dp).
     - Full WCAG AA accessible contrast via Material 3 `GlanceTheme.colors`.
     - Single-line title truncation (`maxLines = 1`) with contentDescription semantics.
     - Zero task titles leaked during unauthenticated, locked, loading, or unavailable states.
     - Initial hydration routed strictly through `TaskWidgetUpdater.refreshAll(context, host)` to maintain publication fencing.

2. **`androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-receiver.kt`**:
   - `TabbyRushTaskWidgetReceiver : GlanceAppWidgetReceiver()`:
     - Hosts `TabbyRushTaskWidget()`.
     - Resolves `TaskSurfaceHostOwner` from `applicationContext`.
     - Uses `goAsync()` to protect asynchronous broadcast execution lifetime across Android process suspension.
     - Imports `androidx.glance.appwidget.updateAll` and delegates updates directly to `TaskWidgetUpdater.refreshAll(context, host)`.

3. **`androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-actions.kt`**:
   - `ToggleTaskActionCallback : ActionCallback`:
     - Deserializes immutable desired-state parameters: `accountKey`, `generation`, `taskId`, `desiredCompleted`.
     - Dispatches `host.access.setCompleted(token, taskId, desiredCompleted, nowEpochMs)` directly against the atomic mutation lock.
     - Triggers `host.refresh(SurfaceRefreshReason.Widget)` and `TaskWidgetUpdater.refreshAll(context, host)`.
     - Fails closed safely if parameters are missing or host is uninitialized.
   - Explicit intent and immutable `PendingIntent` factories for Open (`ACTION_WIDGET_VIEW_TASK`) and Add (`ACTION_WIDGET_ADD_TASK`) targeting `MainActivity`:
     - Distinct request codes computed deterministically from `(accountKey, generation, taskId, op)`.
     - Distinct data URIs ensuring intent identity separation in Android OS cache.

4. **`androidApp/src/main/kotlin/dev/tabbyrush/android/task-widget-updater.kt`**:
   - `TaskWidgetUpdater`:
     - Monotonically increasing epoch tickets and `isClearing` barrier serializing refresh operations against teardown.
     - Bounded 4s timeouts on `host.access.awaitReadiness` and Glance update handoffs.
     - Active coroutine `Job` tracking in `activeJobs` registered during refresh and cancelled during teardown.
     - `try ... finally { isClearing.set(false) }` to guarantee updates are never permanently blocked on error or cancellation.
     - Direct `RemoteViews` blanking to immediately replace launcher views with the initial non-sensitive layout.
     - Glance update session closure via `TabbyRushTaskWidget().getSessionManager(context)`.
     - `suspend fun awaitQuiescence(timeoutMs: Long)` joins active jobs.
     - `suspend fun clearAll(context: Context)` / `clearAllInstances(context: Context)` guarantees zero sensitive content restored after account logout.

5. **XML Resources**:
   - `androidApp/src/main/res/xml/task_widget_info.xml`: AppWidgetProvider sizing (min 120dp, target 3x3 cells, horizontal|vertical resize, updatePeriodMillis=0, initialLayout="@layout/task_widget_initial"). Renamed to underscore format per Android AAPT2 specification in coordination with Phase 05.
   - `androidApp/src/main/res/layout/task_widget_initial.xml`: Non-sensitive initial loading layout (no title text or private accounts). Renamed to underscore format per Android AAPT2 specification.
   - `androidApp/src/main/res/values/task-widget-strings.xml`: Localized and accessible string resources for widget UI and accessibility descriptions.

6. **Test Suites**:
   - `androidApp/src/test/kotlin/dev/tabbyrush/android/task-widget-model-test.kt`: Pure unit tests verifying partition order, state projection non-leakage, action parameter roundtrip, deterministic request code distribution, display state transitions, and concurrency fencing (clear-during-read neutralization).
   - `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/task-widget-device-test.kt`: Instrumented Android device tests verifying responsive size configuration, receiver delegation, PendingIntent creation, resource string resolution, mock host lifecycle refresh, and fail-closed callback handling.

---

## 2. Integration Handoff & Technical Requirements for Phase 05

1. **`AndroidManifest.xml` Component Registration**:
   - Register `TabbyRushTaskWidgetReceiver`:
     ```xml
     <receiver
         android:name="dev.tabbyrush.android.TabbyRushTaskWidgetReceiver"
         android:exported="true">
         <intent-filter>
             <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
         </intent-filter>
         <meta-data
             android:name="android.appwidget.provider"
             android:resource="@xml/task_widget_info" />
     </receiver>
     ```
   - **Auditor Rationale**: Widget receivers must be `android:exported="true"` on Android to receive system broadcast intents from launcher hosts (`android.appwidget.action.APPWIDGET_UPDATE`). No private exported actions or custom intent filters are exposed.

2. **AAPT2 Resource Naming Coordinated**:
   - Replaced kebab-case with underscore format (`task_widget_info.xml`, `task_widget_initial.xml`) in coordination with Phase 05 peer.

3. **`TabbyRushApplication` Host Wiring**:
   - Phase 05 implements `TaskSurfaceHostOwner` on `TabbyRushApplication.kt`:
     ```kotlin
     class TabbyRushApplication : Application(), TaskSurfaceHostOwner {
         override val taskSurfaces: AndroidTaskSurfaceHost get() = ...
     }
     ```
   - Calls `TaskWidgetUpdater.clearAll(this)` in `AccountLeaveProtocol` and startup wipe-marker recovery before deleting database files.
   - Calls `TaskWidgetUpdater.refreshAll(this, taskSurfaces)` on task mutation, sync completion, and account switch.

---

## 3. Pending Native Verification (Phases 05 & 07)

- **Real Launcher Acceptance**: Physical launcher widget placement, interactive checkbox toggles, and resize snapping on Android 12–15 launchers are unverified until Phase 07 device testing.
- **Wipe & Race Verification**: Delayed update vs leave, account switch race, and post-logout RemoteViews blanking will be proven in Phase 05 with the real SQLite Room database and `AccountRuntime`.
