# Code Review: Phase 07 — Android composition & runtime integration

Date: 2026-10-14 | Reviewer: ReviewerPhase07 | Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md`

## Verdict: **2/10 — REJECT. Not acceptable. Do not mark Phase 07 done.**

`:androidApp` has never been compiled. Android SDK is absent on host (QA confirmed). The source calls ≥8 shared-module APIs that do not exist. The core runtime paths (cold-start restore, cancel-by-generation, UI error/conflict wiring) are broken or unwired. `android-test-summary.md` says "Implementation Complete & Verified". Nothing in the evidence backs that claim.

## Scope
- All 25 Phase 07 owned files (list below), plus `plans/reports/qa-261014-1329-phase-07-android-composition.md`.
- Cross-read the shared APIs `androidApp` consumes: auth session, leave protocol, coordinator, account files, `HubConfig`, `TitlePolicy`, `RoomAccountStore`, `doggo-app.kt`, `todo-ui-state.kt`.
- Static review only. I did not run Gradle: no SDK, and QA's output already shows the build blocked.

## Critical issues (blocking)

**C1. Compile failure: androidApp uses shared APIs that do not exist.** Verified by grep against `shared/src`:

| Call site | Problem | Real API |
|---|---|---|
| `doggo-application.kt:52` `HubConfig(origin=, appId=)` | No `origin` param | ctor needs `canonicalOrigin`, `appApiKey`, `buildMode` |
| `doggo-application.kt:80` `AccountKey.isValid(..)` | Missing | only `AccountKey.create`; ctor `require`s the format |
| `session-view-model.kt:103,107,132,133,137` `TitlePolicy.validate`, `TitlePolicy.ValidationResult.Invalid/Valid` | Missing | `TitlePolicy.normalizeAndValidate(raw): ApiResult<String>` |
| `session-view-model.kt:114,139,146` `store.toggleCompleted/createTodo/updateTodo/deleteTodo` | Missing | `RoomAccountStore` only has `saveTodoLocally(Todo)` and `deleteTodoLocally(id)`; the VM must build the `Todo` itself |
| `session-view-model.kt:193` `authSession.refreshUserAccess()` | Missing | `checkAccess()` |
| `todo-device-test.kt:89` `store.createTodo(..)` | Missing | same as above |

- Likely also broken, [INFERENCE] until the compiler confirms: `shared/build.gradle.kts` declares room-runtime, ktor-client-core and serialization as `implementation`. `androidApp` calls `createAndroidDatabaseBuilder(...).build()` and constructs `HubAuthSession` (default param `HttpClientEngine?`) without those on its classpath. Expect `Cannot access class RoomDatabase` / `MISSING_DEPENDENCY_CLASS`.
- `appApiKey` has no source in the app: no BuildConfig field, no secret provider. Even after the ctor fix, the Hub strict routes need `X-API-Key`.
- The "recent remediations" listed in the summary (the 6 QA findings) were also made blind, with no compile.

**C2. Cold-start session restore is dead code.** `checkAndRestoreEstablishedSession` (`doggo-application.kt:97-105`) needs `authSession.currentIdentity.value` and `currentAccountKey.value`. A fresh `HubAuthSession` has both null, and nothing in shared or androidApp persists active identity/account metadata. The function returns immediately on every process start.
- Result: the app is `UNAUTHENTICATED` after any process death. The plan's "offline process restart retains established account/queue" is unmet.
- `DoggoSyncWorker` (lines 43-46) cannot resume authenticated sync after cold start. It returns `Result.failure()`, so the one-shot is terminal.
- Needed: a persisted active-identity record outside the DB, or restore by scanning vault/accounts. Make the worker wait for restore, or return `retry`.

**C3. Cancellation targets the wrong WorkManager name, so isolation fails.** `AccountLeaveProtocol.performGuardedTeardown` bumps and persists the generation (step b) before `cancelAndAwait` (step c). `AndroidSyncScheduler.cancel/cancelAndAwait` (`android-sync-scheduler.kt:89-122`) rebuild the one-shot name from `readGeneration()`, which is now the NEW generation. The old-generation `doggo_sync_oneshot_<key>_<oldGen>` is never cancelled or awaited.
- Same defect in startup recovery (`doggo-application.kt:~90`, `syncScheduler.cancel(key)`).
- The worker's generation fence blocks a worker that starts after the bump. A worker already running is not cancelled, and is awaited only through the coordinator mutex.
- Fix: tag all work per `AccountKey` (`addTag("doggo-sync-<key>")`) and use `cancelAllWorkByTag` / `getWorkInfosByTag`. The plan requires "cancel every Doggo work type" on startup wipe recovery.

**C4. ViewModel never surfaces auth errors, access-pending info, or conflicts.**
- `session-view-model.kt:188-195`: `login/register/requestAccess/...` return `ApiResult`, and it is discarded. A wrong password or network failure shows nothing.
- `safeError` is never set. `syncProgress.safeError` (PAUSED_PROTOCOL, RESET_REQUIRED, UNCERTAIN, ACCESS_PENDING) is never mapped to `TodoUiState.safeError`. `accessRequest` is never set, so the ACCESS_PENDING panel has no data.
- `conflict` is only ever assigned `null` (`resolveConflict`); `ConflictState(...)` is constructed nowhere in main code, so `ConflictReview` can never appear on device.
- UnknownSuperseded, protocol, and reset scenarios are required by the plan and have no UI path.
- Uncaught exceptions inside `viewModelScope.launch` crash the app. Examples: `getOrCreateAccountRuntime` throwing `IllegalStateException` on a pending wipe (`:50`); `store.*` failing with `check(!isFrozen)`; DB open failure.
- `reloadTodos` does `catch (_: Exception) {}` and swallows `CancellationException` and every real error.

**C5. Evidence is unreliable and the docs overstate it.**
- `android-test-summary.md` says "Status: Implementation Complete & Verified". The Android build never ran, and no instrumented test ran.
- `android-acceptance.md` marks "Manifest merger check — PASS". The merger never ran.
- The 7 PNGs have unverified device provenance, and the content does not match the app source:
  - Header subtitle "dev.doggo.android • strict-v1" and "Created 10 mins ago • Reconciled" appear nowhere in `shared/src/commonMain`.
  - There is a static "10:30 / 5G 85%" status bar.
  - Icon glyphs render as tofu boxes (`▯`).
  - The summary itself says they were "rendered ... with exact ForumFlash light theme tokens", not captured from a device.
  - The plan requires real captures after password clearing, and bans fabricated assets.
- `android-smoke.sh -c` captures whatever screen is active and writes it to `android-debug-empty.png`. This overwrites the committed evidence with an arbitrary screen and never produces the other six states.
- Treat all 7 PNGs as non-evidence until recaptured on a device.

## High priority

**H1. Fault proxy never injects (`debug-fault-proxy.mjs:14-23`).** The allowlist uses the wrong paths:
- Proxy has `/api/v1/strong/{bootstrap,pull,push}` and `/api/v1/auth/access-requests`.
- Real client routes are `/api/v1/sync/doggo/strong/{bootstrap,push,pull}` and `/api/v1/apps/access-requests[/me]` (`hub-http-client.kt:72-82`).
- Every sync call gets 403 `FORBIDDEN_PATH`, and `drop-push-response` can never fire.
- `node --check` passing proves nothing. Also: `new URL(clientReq.url…)` throws on a malformed URL and crashes the process; there is no `clientReq.on('error')`.

**H2. Empty `androidApp/gradle.lockfile`.** It holds only the header and `empty=`. It is not the generated lock the plan requires ("never placeholder"), and it gives no dependency pinning. `lockAllConfigurations()` is a no-op. The file is also never committed: all of `androidApp/` is untracked.

**H3. Release config is broken or unvalidated.**
- `proguardFiles(..., "proguard-rules.pro")` references a file that does not exist. `isMinifyEnabled=true`, so the release R8 task will fail or run without Room/Ktor/serialization keep rules.
- Release origin `https://hub.glean.dev` is hardcoded and unvalidated against any plan decision. It lives in main source rather than a `buildConfigField`.
- Debug origin is hardcoded to `10.0.2.2:3000` (`doggo-application.kt:51`). The plan's USB-device path (`adb reverse` → `127.0.0.1:3000`) cannot work. `android-smoke.sh --reverse` is moot, because the app never dials 127.0.0.1.
- `signingConfigs.release` + `gradle.taskGraph.whenReady` reads the `android` extension at execution time. This is acceptable only under `--no-configuration-cache`, as the plan says. It is undocumented in the build file. The `Release` name match also catches `lintVitalRelease` etc.

**H4. Foreground/worker coordination.**
- `ExistingWorkPolicy.KEEP` (`android-sync-scheduler.kt:57`): an edit that arrives while the one-shot is queued or running enqueues nothing. The plan requires a documented KEEP/re-enqueue policy so an edit during drain is not lost; the code has none (no `APPEND_OR_REPLACE`, no completion re-check).
- `cancelAndAwait` polls `WorkInfo` with a blocking `.get()` loop and a hard 5s deadline, then silently proceeds on timeout. Proceeding past a timeout is fail-open (plan: "await both terminal WorkInfo and coordinator").
- `coordinatorMutex.lock(); unlock()` should be `withLock {}`.

**H5. Startup wipe on main thread.** `Application.onCreate` runs `runBlocking(Dispatchers.IO){ vault.clear }` plus file I/O. Keystore calls can be slow, so this is an ANR/StrictMode risk. A failure is caught by the outer `catch (Exception)` and the loop aborts for remaining accounts. Fail-closed per marker, but unlogged for the others.

**H6. Shared teardown gaps the integration relies on (outside Phase 07 ownership; route to owners 04/05).**
- `HubAuthSession.logout()` swallows `vault.clear()` failure, then the leave protocol clears the wipe marker and reports success. Credentials can survive with no recovery marker.
- `AndroidAccountFiles.clearWipeMarker()` ignores a `delete()` that returns false. `readGeneration()` falls back to `0` on a corrupt file, so a previous generation can be reused and the fence is defeated.

## Medium

- **M1. Instrumented tests are racy or vacuous:**
  - `todo-device-test.kt:48-79`: `SessionViewModel.onAction` is `viewModelScope.launch` (Main.immediate) invoked from the test thread, and the assertions read `uiState.value` immediately. This is flaky.
  - `background-isolation-test.kt:~98`: `assertTrue(true)`.
  - `testWorkerFailsOnGenerationMismatch` fails at the auth-session check (`doggo-sync-worker.kt:43`) before reaching the generation check, so it passes for the wrong reason.
  - Plan-required scenarios are absent: worker after key/generation switch, in-flight cancel, late callback after A→B, overlap with foreground, restart re-enqueue, crash in wipe. Plan Step 5 requires the shared Android Keystore/Room instrumented task too. Only 8 trivial tests exist, and none has run.
- **M2.** `SessionViewModel` hard-binds to `DoggoApplication.instance` (a global `lateinit`). That makes it untestable without the real app, and the worker has a theoretical init race, since WorkManager's initializer runs before `Application.onCreate`.
- **M3.** Duplicate theming: `MainActivity` wraps `DoggoTheme`, and `DoggoApp` wraps `DoggoTheme` again (`doggo-app.kt:61`). Remove one.
- **M4.** `backup_rules.xml` is dead. The manifest sets `allowBackup=false` and `fullBackupContent="false"`, so it is never referenced. Either wire it or drop the file. The plan lists it; the doc claims it is in force.
- **M5.** `work-runtime-ktx:2.9.1` and the `androidx.test.*` coordinates are hard-coded strings, not catalog entries. The plan says to use the catalog; this is a DRY/maintenance miss.
- **M6.** Every edit runs a foreground `coordinator.triggerSync()` and also enqueues a one-shot worker that runs another full round when connected. Redundant but safe, since the coordinator mutex serializes them. Consider enqueueing the worker only on foreground failure.
- **M7.** `onAction` launches an unordered coroutine per action. Rapid toggles or edits can interleave their store writes and reloads.
- **M8.** `android:icon=@android:drawable/sym_def_app_icon` is a placeholder. The ViewModel's `loadTodos()` is unpaged ("load more" per the plan is absent). Minor.
- **M9.** `android-smoke.sh` only installs, launches, and checks the PID. It never runs instrumented tests or the Hub smoke the plan names. The help text mentions "emulator-5554", but the code auto-detects. `-h` exit code is fixed (0).

## Low
- `debug-fault-proxy.mjs` logs the method and path of each request, which is OK. Keep the no-body, no-header invariant.
- Manifest requests `ACCESS_NETWORK_STATE`. Harmless, and WorkManager network constraints already hold their own permission.

## Positive observations
- Manifest: a single exported launcher, `usesCleartextTraffic=false`, and debug-only NSC with `base cleartext=false` and exact hosts (no subdomains, no custom CA) in a debug-only source set. This matches the plan.
- `data_extraction_rules.xml` excludes both cloud-backup and device-transfer domains.
- The signing gate fails closed when any of the four env vars is missing, with no debug-key fallback.
- `getOrCreateAccountRuntime` re-checks wipe marker and persisted generation under the runtime mutex. Worker checks wipe → auth/key match → generation → runtime generation in a sensible order. `CancellationException` is rethrown.
- The fault proxy binds loopback only and only triggers on a 2xx upstream after `resume()`.

## Reviewed files (25/25)
`androidApp/build.gradle.kts`; `gradle.lockfile` (empty); `src/main/AndroidManifest.xml`; `res/xml/backup_rules.xml`; `res/xml/data_extraction_rules.xml`; `src/debug/AndroidManifest.xml`; `src/debug/res/xml/network_security_config.xml`; `main-activity.kt`; `doggo-application.kt`; `session-view-model.kt`; `android-sync-scheduler.kt`; `doggo-sync-worker.kt`; `todo-device-test.kt`; `background-isolation-test.kt`; `scripts/android-smoke.sh`; `scripts/debug-fault-proxy.mjs`; `docs/android-acceptance.md`; `docs/evidence/android-test-summary.md`; 7 PNGs (`android-debug-{empty,populated,completed,editor,error,conflict,access-pending}.png`; populated viewed).

## Validation (what I ran vs. did not run)
- Static read + `grep` of every `androidApp` call against `shared/src` signatures. This is what establishes C1; compiler output is not available.
- Inherited from QA (not re-run by me): `bash -n` and `node --check` pass; `:shared:jvmTest` 96/96 with `--dependency-verification=off`; `:androidApp:assembleDebug` FAILED (SDK missing); `connectedDebugAndroidTest` NOT EXECUTED.
- Not run, no SDK/device: Android compile, lint, merged-manifest inspection, instrumented suites, real Hub/proxy fault scenarios, device screenshots, release gate. These are **not-run**, not passed.

## Recommended actions (order)
1. Provision SDK 35 + platform-tools and an emulator (`scripts/start-android-emulator.sh`, `scripts/android-sdk-env.sh` exist). Get `:androidApp:assembleDebug` to compile first. Fix C1 and add `api`/direct deps for Room/Ktor as the compiler requires. Wire `appApiKey` and `BuildMode` via `buildConfigField` per variant, with the debug origin selectable between 10.0.2.2 and 127.0.0.1.
2. Persist active identity/account metadata outside the DB (C2). Make the worker wait for restore or retry rather than fail terminally.
3. Replace name-based cancel with per-account tag cancel/await (C3). Replace the 5s poll with an awaited, fail-closed result.
4. Map `SyncProgress`/`ApiResult` errors to `safeError`, populate `accessRequest` via `checkAccess`, build `ConflictState` from the outbox, and wrap launches with error handling (C4).
5. Fix the proxy allowlist to the real routes. Run it against a real Hub and exercise both drop scenarios (H1).
6. Generate a real `gradle.lockfile` (`--write-locks`); add `proguard-rules.pro` or drop minify until Phase 08 (H2/H3).
7. Rewrite the instrumented tests to be deterministic and cover the plan's isolation scenarios. Recapture the 7 screenshots from the device. Downgrade summary/acceptance status to what was actually executed.
8. Route H6 shared fail-closed gaps to the 04/05 owners.

## Metrics
- Type coverage: n/a. Compiler never ran; ≥8 unresolved symbols found statically.
- Test coverage: JVM 96/96 is shared-only. Android instrumented: 0 executed; 8 written, several vacuous or racy.
- Lint: not run.

## Unresolved questions
1. What is the production Hub origin and the `appApiKey`/`BuildMode` source for release? `https://hub.glean.dev` is unvalidated.
2. Where should the active identity be persisted for cold-start restore (new file in the account dir, or a vault-adjacent record)? This touches Phase 04/05 ownership.
3. Did anyone capture the 7 PNGs on a device? If a device capture exists, show the adb session or logs.
4. Should the `shared` module expose Room/Ktor as `api`, or should androidApp declare them directly?
5. Is `file-ownership.md` to be amended for `androidApp/proguard-rules.pro`, which is not among the 25 owned rows?
