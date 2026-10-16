# Sticky task surfaces — proposed implementation contracts

Status: pending; architecture design, **not current APIs or verified runtime behavior**. Source evidence: [repository analysis](reports/01-codebase-analysis.md), [Android research](research/researcher-01-android-surfaces.md), [desktop research](research/researcher-02-desktop-surfaces.md), [intended architecture §7](../../docs/system-architecture.md#7-sticky-task-surfaces--intended-design-pending-implementation). Freeze these boundaries in phase01 before parallel leaves. No new Room schema, task database, backend endpoint, protocol, auto-start or activation IPC.

## 1. Shared contract signatures

Proposed Kotlin; names/types below are binding plan contracts, not excerpts from the repository. Existing `AccountKey`, `Todo`, `TaskDeadline`, `PersistedTaskSchedule`, `Flow`, `StateFlow` retain their meanings. Put declarations in the exact files in [ownership](file-ownership.md).

```kotlin
// Add to existing AccountStore; RoomAccountStore is the only discovered implementor.
fun observeTodos(): Flow<List<Todo>>
suspend fun setCompletedLocally(id: String, completed: Boolean, nowEpochMs: Long): CompletionChange

enum class CompletionChange { Changed, Unchanged, Missing }
data class SurfaceAccountToken(val accountKey: AccountKey, val generation: Long)
sealed interface SurfaceReadiness {
    data object Restoring : SurfaceReadiness
    data class Ready(val token: SurfaceAccountToken) : SurfaceReadiness
    data object SignedOut : SurfaceReadiness
    data object Locked : SurfaceReadiness
    data object Unavailable : SurfaceReadiness
}
data class TaskSections(val incomplete: List<Todo>, val completed: List<Todo>)
data class SurfaceSnapshot(
    val token: SurfaceAccountToken,
    val sections: TaskSections,
    val schedules: List<PersistedTaskSchedule>
)
sealed interface SurfaceResult<out T> {
    data class Value<T>(val value: T) : SurfaceResult<T>
    data object Rejected : SurfaceResult<Nothing>
    data object Unavailable : SurfaceResult<Nothing>
}
interface TaskSurfaceAccess {
    val readiness: StateFlow<SurfaceReadiness>
    suspend fun awaitReadiness(timeoutMs: Long = 4_000L): SurfaceReadiness
    suspend fun read(token: SurfaceAccountToken): SurfaceResult<SurfaceSnapshot>
    suspend fun setCompleted(token: SurfaceAccountToken, taskId: String,
        completed: Boolean, nowEpochMs: Long): SurfaceResult<CompletionChange>
    suspend fun publishIfCurrent(token: SurfaceAccountToken, publish: suspend () -> Unit): Boolean
    suspend fun validateNavigation(token: SurfaceAccountToken, taskId: String?): Boolean
}
fun partitionSurfaceTodos(todos: List<Todo>): TaskSections
fun selectNextDeadline(schedules: List<PersistedTaskSchedule>, nowEpochMs: Long): DeadlineSelection?
data class DeadlineSelection(
    val taskId: String, val title: String, val deadline: TaskDeadline,
    val hasUnresolvedConflict: Boolean
)
data class DeadlineSelectionKey(
    val token: SurfaceAccountToken, val taskId: String, val deadline: TaskDeadline
)
```

`SurfaceResult.Rejected`: stale/non-current token or closed admission. `Unavailable`: bounded restoration failure/locked storage/read failure, without task payload/error secrets. `Missing` covers absent/tombstoned tasks; repeated desired state gives `Unchanged`. Public external entry points consume values, not exception text. `SurfaceSnapshot` reads both projections under a single runtime lease; they need not be one combined DB transaction because separate surfaces use separate projections, but must never mix accounts/generations. Reconcile once again after intervening invalidation.

`TaskSurfaceAccess` is implemented by Android phase05 and desktop phase06 against real runtime ownership, not a fallback fake store. Phase01 adds interfaces only; leaves compile against these dependencies but are not installed/registered until integrations. There is no runtime no-op implementation.

## 2. Atomic completion and observation

- `loadTodos()` currently takes `RoomAccountStore.mutex`; save takes existing mutation admission then mutex and calls `dao.saveTodoAtomic`. Extract **private locked helpers** for load/save so `setCompletedLocally` can read fresh state, alter only completion/updatedAt, persist and invalidate inside one existing `withMutationLock` admission. Never call public load/save recursively while holding the mutex.
- ID lookup uses the fresh visible authoritative projection, not a `Todo` copied from RemoteViews/main UI. Preserve concurrent title, deadline, rule, token, revision, FIFO/coalescing, tombstone and strict-v1/v2 guards. `updatedAt = maxOf(nowEpochMs, current.updatedAt, current.createdAt)` only when changed. Existing temporal-token rotation semantics remain unchanged.
- No DAO writes from widget/notification/compact UI. No network under runtime lease/store lock; request existing immediate sync after local commit and lease release. No new periodic sync or network requirement on local mutation.
- Widget sends desired checked state both directions. Notification Complete always sends `true`. Main toggle callers in Android phase05 and desktop phase06 use ID + desired state too; stale copies no longer overwrite concurrent fields. Re-delivery of the same desired state after commit creates no additional mutation/outbox revision.
- Reuse the existing replayed conflated mutation invalidation for `observeTodos`, retaining `observePersistedSchedule` behavior. Rename the private invalidation field to a task-neutral name; remove the now-misleading unused public `notifyScheduleChanged` helper rather than keep an alias. Existing external sync mutation callers already use `withMutationLock`; do not add a second event bus.
- `observeTodos` replays initial local load; successful save/delete/reconcileAccepted/full-scan promotion/conflict resolution/transition mutations emit, failures do not publish success. Distinct snapshots avoid redundant UI refresh; burst events may coalesce, final state cannot be lost. Tests use real SQLite and real sync mutations for these paths.
- General list includes saved local drafts exactly as existing `loadTodos` does; in-memory unsaved editor text is never in it. Notification alone uses existing atomic persisted schedule projection (base + submitted outbox, **no editor_drafts**). Do not silently replace widget list semantics with reminder eligibility.
- Stable partition: remove deleted records, retain original `createdAt DESC, id DESC` order within Incomplete and Completed, one scrollable list with Incomplete first; never inherit main filter.

## 3. Account admission, readiness and publication

- Restoration starts `Restoring`; transition to `Ready(token)` only after pending wipe recovery, unlocked storage, established current identity/key, matching persisted generation, absent wipe marker and runtime installation. No stored active account => `SignedOut`; locked device => `Locked`; storage/restore failure => `Unavailable` (retry only on existing next safe startup/resume/work trigger).
- Cold external work waits at most **4 seconds**, not indefinitely on an authenticated Flow. Timeout returns `Unavailable`, blank/non-sensitive widget state and no task notification. Worker completion may arrange the existing best-effort surface refresh, not spin or invent a network retry subsystem. Cancellation propagates; do not swallow `CancellationException`.
- Every external read, completion, Open/Add navigation and publication carries current-account token. Validate account key, generation, wipe marker, storage unlock, active runtime identity and open admission **inside** admission. Never `getOrCreateAccountRuntime(action.accountKey)`; only normal current-account establishment may open a runtime.
- Use existing delivery admission to count surface operations and mutations for close/drain. A small platform-owned publication/lifecycle mutex serializes final check + OS publication handoff with transition to non-Ready. Release that mutex before waiting for drain. Lock order: lifecycle publication/admission coordination → delivery admission → store mutation mutex → DB transaction; never wait for lifecycle transition/drain while holding store mutex. A nested store mutation admission increments existing counts but must not recursively acquire a held mutex.
- `read` returns immutable token-tagged data; `publishIfCurrent` reacquires a runtime lease, final-validates under publication serialization, then awaits the actual bounded OS update handoff. Account change cannot pass that serialized boundary concurrently. Prepare rendering before final check, do not hold DB lock while calling OS. Check again for long-running generation-sensitive operations; reject old snapshot/action instead of publishing against replacement.
- Android Glance update sessions must be tracked/cancelled/joined through the actual RemoteViews update handoff, not only enqueue time. `provideContent` lifetime is not an admission lifetime: never hold a lease for an indefinitely running composition. Integrator and widget owner use completed Glance update/session cancellation APIs supported by selected version; acceptance races a delayed update against leave to prove final OS state is blank. Session completion/cancellation must be proven before draining succeeds.
- Bounded operation policy: readiness4s; OS update handoff4s total per refresh/clear round; existing delivery drain5s. Timeout cancels/joins owned update work, returns failure and leaves non-Ready/marker intact if quiescence cannot be proven. Never treat a timed-out enqueue as a completed clear. Blank-only teardown publication uses the same serialized lifecycle owner without requiring Ready; it cannot carry a task/token action payload.
- A queued navigation is revalidated when consumed after restoration and just before selecting fresh task by ID. Invalid/deleted/stale target returns harmless current-account app state, not another account's editor. Signed-out Add/Open can open app authentication without task/account payload; account-bound Add rejects stale generation. App intent extras are untrusted.

## 4. Teardown and recovery order

Preserve shared `AccountLeaveProtocol` sequence; integrate through existing platform composite cancel hooks, not new alternate logout logic.

1. Platform marks surfaces non-Ready/clears in-memory task state under publication mutex; block new external admissions. A leave rejected by existing unresolved-work review may restore Ready only if runtime/generation/gate are still valid.
2. Shared protocol freezes store, closes/drains existing delivery admission (including admitted surfaces) **before** existing generation increment + wipe-marker persistence. Do not invert existing sequence or hold publication mutex during drain. Timeout leaves account frozen/unavailable, database open, no success reported.
3. Persist next generation and wipe marker using existing callbacks. Cancel/join task/schedule observers, bounded Glance sessions, account-tagged surface work, existing sync and reminder scheduling; drain admitted callbacks. Both platform integrations own this wiring.
4. Remove task notification programmatically; invalidate any posted-instance dismissal receipt; clear all retained launcher widget IDs to signed-out/empty with no old token actions; clear account-scoped opt-in/suppression preferences. Existing reminder cancellations still run independently. Clear failures retain marker; never call successful wipe complete while old OS content remains scheduled to republish.
5. Only after successful quiescence/OS clearing: existing Room close, DB/WAL/SHM deletion, credential cleanup and marker removal. If OS refuses update, record bounded failure and keep fail-closed marker for next recovery, no infinite boot wait.
6. Startup pending-wipe recovery repeats cancellation and OS-content cleanup before deleting artifacts/removing marker, **without opening doomed Room or restoring its session**. No-marker signed-out startup also blanks widget instances/cancels task notification. Account replacement clears old surfaces before admitting new generation.

Geometry preferences contain no account content and survive logout; desktop in-memory quick-add and sections clear on transition. Android account-scoped surface preferences are deleted on leave, so a replacement account starts opted out.

## 5. Notification decisions

- Default **off**, device-local/account-scoped outside Room. Opt-in independent of reminders; no exact-alarm request. Eligible = persisted `!isDeleted && !completed && deadline != null`; **include unresolved-conflict projections**, expose a concise “Sync conflict” indication, preserve `hasUnresolvedConflict` in selection. Rationale: this is persisted task display, not reminder delivery; suppression would silently hide an incomplete saved deadline. Complete still follows existing conflict/FIFO store invariants, cannot resolve/drop conflict implicitly. Open provides normal conflict review; no new resolution API.
- At `nowEpochMs >= deadline.overdueEpochMs`, task is overdue (equality included). Oldest overdue = minimum `(overdueEpochMs, taskId ASC)`; otherwise upcoming uses the same minimum `(overdueEpochMs, taskId ASC)`. Date-only sorts by **next-day overdue boundary**, not its reminder morning anchor; a timed task genuinely due earlier wins. Persisted zone/offset/boundaries are authoritative; no parsing display strings or shifting saved deadlines when device zone changes.
- Selection uses persisted title/deadline, saved outbox intentions, no unsubmitted editor drafts. Reminder enabled flag/token is not an eligibility condition. No selection => cancel notification and clear suppression. One quiet notification only, separate low-importance/no-sound/no-vibration channel `doggo_task_surface_v1`, notification tag `doggo.task.surface`, ID `4101`; never reuse reminder IDs/channel or create replacement channel to bypass blocks.
- Local state distinguishes `OptedOut`, `PermissionDenied`, `AppDisabled`, `ChannelDisabled`, `Available`; selection/suppression are separate from capability. ActivityResult `RequestPermission(POST_NOTIFICATIONS)` only after explicit opt-in/rationale on API33+; API26–32 no runtime request. Denial retains intent-to-enable preference but displays unavailable and Settings path; no repeated automatic prompt. Resume recomputes app/channel/runtime capabilities. No-op reminder action handlers are not a permission flow.
- Private notification title, generic public lock-screen version. Immutable explicit Complete/Open/delete PendingIntents, identities distinguish account+generation+task+operation+posted instance; never include credentials. Open goes directly to Activity, not a notification-trampoline receiver. Complete receiver sets true after admission.
- Persist dismissed `DeadlineSelectionKey(token, taskId, deadline)` including full saved deadline value/type/zone/offset/anchor/boundary semantics, not title, reminder rule or schedule token. Same key stays suppressed through title edits, sync, time jumps, worker runs and process restart. Clear when selection changes/disappears, opt-out, or deliberate user re-enable (off→on). Time crossing alone does not clear. Reminder-only edits do not revive notification.
- Each actual post has a durable opaque posted-instance receipt bound to selection key. Delete callback accepts only its currently active receipt, persists suppression atomically before refresh. Programmatic removal/replacement first invalidates receipt; delayed delete intent cannot suppress a replacement or masquerade as dismissal. Persist key/receipt before post and fail closed on preference-write failure. No immediate repost on delete; API34+ swipe acceptance required. An OS path delivering no delete callback is documented honestly, not assumed covered.
- Refresh on initial Ready, successful task observation/schedule invalidation, completed existing background sync, app resume/start, widget update, supported system time/date/time-zone changes. Process-owned observers live in Application/runtime, never Activity/ViewModel. One account-tagged unique **one-time inexact** WorkManager next-boundary job may be scheduled with no network constraint; use at least 15-minute delay to prevent catch-up loops, no permanent polling. Schedule only a future effective due boundary, recompute on execution; coalesce bursts. No exact SLA, FGS, new exact permission, changed sync cadence, or force-stop bypass.

## 6. Platform leaf interfaces

Proposed Android phase01 file `task-surface-host.kt` declares:

```kotlin
interface TaskSurfaceHostOwner { val taskSurfaces: AndroidTaskSurfaceHost }
interface AndroidTaskSurfaceHost {
    val access: TaskSurfaceAccess
    suspend fun refresh(reason: SurfaceRefreshReason)
}
enum class SurfaceRefreshReason { Data, Startup, Account, Clock, Capability, Widget }
```

Leaf callbacks resolve `TaskSurfaceHostOwner` from application context; phase05 implements it in real `DoggoApplication`. No reflection, arbitrary global fake or leaf edits to existing roots. Phase02 exports `DoggoTaskWidget`, `DoggoTaskWidgetReceiver`, `TaskWidgetUpdater.refreshAll(context, host)` and `clearAll(context)` (both suspend); phase03 exports `TaskSurfaceNotificationCoordinator.refresh(token, snapshot, nowEpochMs)`, `cancelForAccount(accountKey)`, `TaskSurfacePreferences`, native settings composable and private action/dismiss/time/worker entry points. All receive real host/access dependencies; p05 wires them once.

Proposed desktop phase01 file `sticky-task-model.kt` freezes:

```kotlin
data class StickyQuickAddState(val rawTitle: String = "", val error: String? = null,
    val submitting: Boolean = false)
data class StickyTaskState(val readiness: SurfaceReadiness,
    val sections: TaskSections, val quickAdd: StickyQuickAddState)
sealed interface StickyTaskAction {
    data class ChangeTitle(val token: SurfaceAccountToken, val rawTitle: String) : StickyTaskAction
    data class Submit(val token: SurfaceAccountToken) : StickyTaskAction
    data class SetCompleted(val token: SurfaceAccountToken,
        val taskId: String, val completed: Boolean) : StickyTaskAction
    data object OpenApp : StickyTaskAction
    data object Quit : StickyTaskAction
}
```

Phase04 exports `@Composable StickyTaskContent(state: StickyTaskState, onAction: (StickyTaskAction) -> Unit)`; geometry helper/presentation storage are desktop-local. Phase06 implements actions/controller publication. Quick-add **never** dispatches main `OpenCreate/ChangeDraftTitle/SubmitEditor`: independent controller-owned buffer preserves an open main edit. TitlePolicy and the same creation helper supply normalized title, UUID, timestamps, incomplete status; title-only create has no deadline and disabled reminder, as existing create with `hasDeadline=false`. Extract helper within phase06, use from both creation paths; full editor retains its settings/default behavior. Clear compact buffer only after successful commit or account transition; rejected submission retains error/text.

Remaining proposed cross-phase leaf boundaries are frozen here; implementation-private helpers may differ. Android imports are platform types, not shared dependencies:

```kotlin
enum class TaskSurfaceAvailability { OptedOut, PermissionDenied, AppDisabled, ChannelDisabled, Available }
data class TaskSurfacePreferenceState(
    val enabled: Boolean = false,
    val suppressed: DeadlineSelectionKey? = null,
    val postedKey: DeadlineSelectionKey? = null,
    val postedReceipt: String? = null
)
class TaskSurfacePreferences(context: Context) {
    suspend fun read(accountKey: AccountKey): TaskSurfacePreferenceState
    suspend fun setEnabled(token: SurfaceAccountToken, enabled: Boolean)
    suspend fun reconcileSelection(token: SurfaceAccountToken,
        key: DeadlineSelectionKey?): TaskSurfacePreferenceState
    suspend fun recordPost(key: DeadlineSelectionKey, receipt: String)
    suspend fun suppressIfCurrent(key: DeadlineSelectionKey, receipt: String): Boolean
    suspend fun invalidatePost(accountKey: AccountKey)
    suspend fun clear(accountKey: AccountKey)
}
class TaskSurfaceNotificationCoordinator(
    context: Context, host: AndroidTaskSurfaceHost, preferences: TaskSurfacePreferences
) {
    suspend fun refresh(token: SurfaceAccountToken, snapshot: SurfaceSnapshot, nowEpochMs: Long)
    suspend fun cancelForAccount(accountKey: AccountKey)
}
object TaskWidgetUpdater {
    suspend fun refreshAll(context: Context, host: AndroidTaskSurfaceHost)
    suspend fun clearAll(context: Context)
}
@Composable
fun TaskSurfaceSettings(
    enabled: Boolean, availability: TaskSurfaceAvailability,
    onEnabledChange: (Boolean) -> Unit, onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit, onClose: () -> Unit
)
```

`setEnabled(false)` and deliberate false→true clear suppression; all preference operations are serialized/atomic and admitted against token by coordinator before use. `reconcileSelection` verifies matching token, clears suppression and invalidates posted receipt when selection changes/disappears, and otherwise preserves them atomically. `recordPost` replaces posted key/receipt, preserving same-key suppression; `suppressIfCurrent` checks both before persisting. Coordinator calls these operations, never mutates preference fields externally. `clearAll` first cancels/joins old widget update sessions then sends blank-only updates for all IDs under teardown serialization. Leaf update failure throws to integration cleanup; no success-shaped no-op.

Proposed desktop cross-phase boundaries (phase04 owns geometry/preferences types; phase06 owns platform interfaces/default):

```kotlin
data class StickyWindowGeometry(val xDp: Double, val yDp: Double,
    val widthDp: Double, val heightDp: Double, val savedScaleX: Double, val savedScaleY: Double)
data class MonitorWorkArea(val id: String, val primary: Boolean,
    val xDp: Double, val yDp: Double, val widthDp: Double, val heightDp: Double,
    val scaleX: Double, val scaleY: Double)
data class StickyWindowPresentation(val visible: Boolean, val geometry: StickyWindowGeometry)
fun restoreStickyGeometry(saved: StickyWindowGeometry?, monitors: List<MonitorWorkArea>): StickyWindowGeometry
class StickyWindowPreferences(file: java.io.File) {
    fun load(): StickyWindowPresentation
    fun save(presentation: StickyWindowPresentation)
}
interface DesktopSurfacePlatform {
    fun monitorWorkAreas(): List<MonitorWorkArea>
    fun traySupported(): Boolean
    fun sessionDescription(): String
}
interface DesktopSurfacePlatformProvider { fun create(): DesktopSurfacePlatform }
```

All monitor coordinates are normalized AWT user-space logical units before these calls; save scale metadata supports validation/conversion, never naïve global scale multiplication of negative virtual origins. Empty monitor list returns reachable default geometry and integration keeps main visible until actual bounds exist; native acceptance still requires real displays. Tray registration success/failure is runtime shell state separate from `traySupported()`; no false success if initialization throws. Linux provider reports real session and delegates complete generic AWT mechanics, not a duplicate lifecycle implementation. Compact ChangeTitle/Submit carry token too, so an event queued across account replacement cannot populate/submit old-account input.

## 7. Desktop geometry, tray and process lifetime

- Single guarded process, controller, runtime, main Window, sticky Window and usable Tray instance. `Show` changes retained visibility/raises existing window; never constructs another controller/window. Profile-lock contention continues exit-only, no activation IPC. Sticky uses pinned Compose `alwaysOnTop=true`; not above secure screens/fullscreen guarantees.
- Local atomic presentation file `sticky-window.properties` stores version, sticky visible flag, x/y/width/height in a documented logical coordinate system plus display scale needed for conversion; no task text/account credentials. Default sticky hidden, main visible; default sticky 360×520dp, minimum 280×240dp constrained by smaller work area. Corrupt/missing preferences => reachable defaults, not failure.
- Enumerate all current AWT display configurations, transforms and Toolkit screen insets; convert persisted logical coordinates consistently to per-monitor work-area coordinates. Retain valid negative origins. Clamp size to selected usable work area; keep complete title bar and at least 48dp of content reachable. Choose maximum-intersection display, otherwise primary; hotplug/DPI changes revalidate before Show and on restore. Debounce persistence on move/resize, flush on hide/Quit, do not persist minimized/transient invalid bounds.
- Close hides its own UI only; neither dispose nor sync cancellation. Tray menu Open app, Show/Hide sticky, Quit. Both windows expose explicit Open/Show-other/Quit recovery controls. If tray unsupported **or initialization fails**, refuse hide of last visible window, retain it with explanation and controls. If tray becomes unusable while hidden, show main; startup always main visible even sticky-hidden preference.
- Phase06 owns a platform-neutral `DesktopSurfacePlatform` seam with real AWT default and optional ServiceLoader provider discovered only on Linux. No-provider uses complete generic implementation, not a stub. Phase08 may add Linux provider for tray/session capability/usable-bounds differences only; same common lifecycle logic handles unavailable tray. One provider at most, no new dependencies/build edits anticipated.
- All Quit routes converge on one idempotent suspend teardown: stop admission, persist geometry, cancel/join surface observer + clock jobs, drain sync/reminder/runtime work, close Room, cancel application scope, release profile lock, exit. Failure/timeout cannot be reported as graceful release while live runtime remains; retain visible recovery/error and permit explicit retry. No database teardown on ordinary hide.

## 8. Build and evidence boundaries

Phase01 owns catalog, Android build, all existing module lockfiles and verification metadata; selects stable Glance 1.2.0 candidate only after compatibility proof against Kotlin2.1.10/Compose1.7.3/min26/SDK35. No leaf edits Gradle files. Shared API implementor discovery found only RoomAccountStore and no AccountStore doubles; repeat targeted references before signature change and put any discovered change in phase01 ownership before work starts. New tests need no test framework unless phase01 explicitly adds it.

Exact lock paths: `androidApp/gradle.lockfile`, `shared/gradle.lockfile`, `desktopApp/gradle-windows.lockfile`, `desktopApp/gradle-linux.lockfile`. Cross-host lock resolution occurs on matching host by the same phase01 owner; do not bypass desktop host guard or gratuitously rewrite unaffected locks. Linux metadata resolution, if actually affected, is a phase01 prerequisite on Linux, not phase08 shared-file ownership. No generated Room schemas or broad baseline checksum repair.

All commands in [acceptance matrix](acceptance-matrix.md) are future commands. Strict dependency verification remains required for clean supply-chain proof; existing 149 tooling checksum baseline is an external hold, not a reason to claim `--dependency-verification=off` as acceptance. Native launcher/shade/window/tray evidence is required beyond build proof. Existing G2/G3/G6/G7 release holds and Windows reminder-toast stub are unchanged.

## Unresolved questions

No blocking product questions. Implementation gates: exact stable Glance/toolchain graph and session-update cancellation semantics; Linux target distro/desktop/session and usable tray behavior. Conservative decisions above are explicit interview candidates, not permission to silently narrow scope; any contract change is reviewed before leaves start.
