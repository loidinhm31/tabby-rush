# Code Review Cycle 2: Phase 07 — Android composition & runtime integration

Date: 2026-10-14 | Reviewer: ReviewerPhase07Cycle2 | Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md` (status lines updated)

## Verdict: 3/10 — REJECT. Not buildable. Do not mark Phase 07 done.
Cycle 1 API-name fixes landed partially; the same class of blind-edit defects remains (new unresolved symbols). Worse: `:androidApp` has no Kotlin plugin, so none of the Kotlin has ever been compiled by anything. Docs are now honest about unexecuted status (good); they still overstate cancel semantics.

## Critical (blocking)

**C1. No Kotlin Android plugin in `androidApp/build.gradle.kts`.** Plugins = `android.application`, `compose.multiplatform`, `compose.compiler` only. AGP 8.7.3 has no built-in Kotlin. Verified: `./gradlew :androidApp:tasks --all` lists `compileDebugJavaWithJavac` but NO `compileDebugKotlin`. Every `.kt` file (app + androidTest) is ignored; APK would be built without `DoggoApplication`/`MainActivity` classes (crash at launch) — assembleDebug can "pass" green and be wrong. Fix: add `kotlin-android` alias (`org.jetbrains.kotlin.android`, version.ref kotlin) in catalog + root `apply false` + androidApp plugins; re-list tasks and confirm `compileDebugKotlin`.

**C2. Unresolved symbols still in source (compile errors, verified by grep vs `shared/src`):**
| Site | Problem | Real API |
|---|---|---|
| `session-view-model.kt:71,133` `SafeError.Storage(msg)` | no such type | `SafeError.Internal(code, message)` |
| `session-view-model.kt:105,140` `SafeError.ValidationError` + `.reason` | no such type/field | `SafeError.Validation(code, message, field)` → `.message` |
| `session-view-model.kt:~160` `it.state == OutboxEntity.STATE_CONFLICT` | `OutboxEntity` has no `state` | `deliveryState` |
| `background-isolation-test.kt:69` `CredentialBundle(a, r, expiry)` | 3-arg ctor | ctor is `(accessToken, refreshToken)` |
Cycle-1 "C1 fixed" claim is therefore false. Expect more once the Kotlin plugin is added (compiler never ran).

**C3. ACCESS_PENDING logout is a dead end.** `AccessRequestPanel.onLogout` → `AuthSubmitAction.Logout` → VM `onAction(RequestLeave)` → `requestLeave()` returns at `app.getActiveRuntime() ?: return`. A runtime is only created in `onAuthenticated`, so a pending-access user has none → Logout does nothing and no way back to login. Fix: Logout path must call `authSession.logout()` (+clearActiveAccount) when no runtime exists.

**C4. Acceptance gates all unmet** (plan success criteria): no SDK/emulator → `assembleDebug` FAIL, `connectedDebugAndroidTest` not run, no merged-manifest inspection, no real Hub/proxy device run, lockfile is stub `empty=`, 7 PNGs are rendered mockups (docs now say so) not device captures. Phase stays pending; docs label this honestly.

## High
- **H1. Startup wipe recovery fail-open** (`doggo-application.kt:68-90`): return of `deleteDatabaseAndArtifacts` ignored, then vault cleared + marker cleared + active account cleared even if DB files survive. Shared `AccountLeaveProtocol.completePendingWipe(...)` already implements "clear marker only if deleted" — reuse it (DRY + correctness). Also `clearActiveAccount()` runs for ANY wiped account, not just the active one (drops a healthy session). Also `runBlocking` Keystore call on main thread in `Application.onCreate` (ANR/StrictMode). Move to IO barrier before restore.
- **H2. `cancelAndAwait` is not a real quiescence barrier** (`android-sync-scheduler.kt:84-119`): (a) WorkManager marks CANCELLED immediately; `isFinished` poll does not prove the worker coroutine stopped. (b) Coordinator wait uses `tryLock` for 5s then silently proceeds → fail-open; a foreground sync round >5s lets leave close/delete the DB mid-round (isFrozen only fences at checkpoints). (c) Called via `coordinator.requestLeave()` (holds the mutex) it always burns 5s then proceeds. Fix: throw if mutex not acquired (shared leave protocol already rolls back on exception); don't hold the mutex in callers. Docs claim `coordinatorMutex.withLock` — inaccurate. Blocking `.get()` on Default dispatcher; prefer `await()`.
- **H3. Login/register error swallowed (likely)** [INFERENCE, uncompiled]: failure sets state UNAUTHENTICATED; collector then runs `onUnauthenticated()` → `_uiState.value = TodoUiState()` which wipes the `safeError` that `handleAuth` set just before. Wrong-password message may never show. Fix: reset only account-bound fields, preserve safeError/transient form errors.
- **H4. Leave failures silent**: `requestLeave`/`ConfirmLeave` return false (marker/gen write failed, scheduler cancel failed, delete failed) → nothing shown, `leaveGuard` stays up. Surface `SafeError`.
- **H5. Release config**: `HUB_API_KEY="doggo-dev-api-key"` in `defaultConfig` ships in release; `https://hub.glean.dev` unvalidated placeholder. Release-specific `buildConfigField` required (Phase 08 gate), no dev key in release.
- **H6. `testWorkerFailsOnGenerationMismatch` fails for the wrong reason**: `restoreSession` requires `key == AccountKey.create(origin, userId)`; the fixture key `aaaa…` never matches → returns false (unasserted) → state UNAUTHENTICATED → worker returns `retry()`, test expects `failure()` → test fails/never reaches generation check. Derive key via `AccountKey.create(app.hubConfig.canonicalOrigin, userId)` and assert restore true.

## Medium
- Clock skew crash: `Todo.updatedAt = System.currentTimeMillis()` with `require(updatedAt >= createdAt)`; a todo created by another device with a later clock throws on toggle/edit → "Action failed". Use `maxOf(now, todo.createdAt)`.
- `getOrCreateAccountRuntime` falls back to `userId = "default"` when identity is null — silent mis-partition; throw instead.
- Perf: every `progress` emission does full `loadTodos()` + `getAllOutbox()` (whole outbox) when `conflictCount>0`. Debounce / query by state. Conflict never cleared when count returns to 0.
- `onAction` spawns an unordered coroutine per action (rapid toggles interleave). Serialize via a Channel/Mutex.
- `active_account.dat`: non-atomic write (other files use tmp+ATOMIC_MOVE), plaintext email/username, main-thread I/O (`saveActiveAccount` inside collector), not covered by `file` exclude (only `accounts`), swallowed exceptions. Backup is off so impact is low; still add `<exclude domain="file" path="."/>`.
- `DoggoApplication.instance` global lateinit used by VM/Worker/Scheduler; hurts testability; instrumented tests share state (setUp doesn't reset authSession). VM init collector's first emission resets `_uiState`, clobbering early actions (flaky tests: `delay(50)` assertions).
- Scheduler KDoc doesn't document KEEP/re-enqueue policy (plan step 4 requires it). Safe in practice (full outbox drained per run + foreground trigger) but undocumented.
- "Load more" (plan step 7) has no contract (`TodoAction` lacks it) — `loadTodos()` unpaged; UnknownSuperseded/protocol/reset only have generic safeError path. Needs Phase 06 contract owner.
- `android-smoke.sh`: `--reverse` is moot (debug origin hard-coded `10.0.2.2:3000`, USB path 127.0.0.1 unsupported); `--capture` overwrites `android-debug-empty.png` with whatever screen is up; help text says emulator-5554 default but code auto-detects; never runs instrumented tests/Hub smoke.
- `gradle.taskGraph.whenReady` release gate matches any task containing "Release" (lintVitalRelease etc.), reads `android` ext at execution time → requires `--no-configuration-cache` (undocumented in file); `proguard` not configured (minify=false OK until 08). Versions `work-runtime-ktx:2.9.1` and androidx.test coords hard-coded, not in catalog.

## Low
- MainActivity wraps `Surface` outside `DoggoTheme` while `DoggoApp` already does Surface+Theme: redundant, drop.
- Proxy: forwards raw `clientReq.url` (not normalized path) → `/api/v1/auth/me/../../auth/login` passes allowlist (verified; upstream is loopback Hub only). Forward `url.pathname+search`. No `clientRes.on('error')`.
- Placeholder launcher icon; `Log` lines print AccountKey (stable pseudonymous id); `SafeError` text built from raw `e.message`.

## Positive
- Manifest: single exported launcher, cleartext off, debug-only NSC with exact hosts/no subdomains/no custom CA; extraction rules cover cloud+transfer; `fullBackupContent` now linked.
- Account-tag cancellation (`cancelAllWorkByTag`) fixes C3 from cycle 1; worker check order (wipe → auth → account → generation → runtime gen), CancellationException rethrown; worker returns `retry` (not terminal failure) before cold-start restore.
- Cold-start restore + `active_account.dat` now exists; startup wipe runs before restore.
- `leaveProtocolProvider` captures local `leaveProto` (cross-account ref fixed); leave return booleans now honored.
- Fault proxy: loopback-only, correct routes, 2xx-gated single-shot drop — verified functionally (see below). Signing gate fails closed, no debug-key fallback.
- Docs/summary now truthful about unexecuted gates.

## Reviewed files (25/25)
androidApp: `build.gradle.kts`, `gradle.lockfile` (stub), `src/main/AndroidManifest.xml`, `res/xml/{backup_rules,data_extraction_rules}.xml`, `src/debug/AndroidManifest.xml`, `src/debug/res/xml/network_security_config.xml`, `main-activity.kt`, `doggo-application.kt`, `session-view-model.kt`, `android-sync-scheduler.kt`, `doggo-sync-worker.kt`, `todo-device-test.kt`, `background-isolation-test.kt`; `scripts/android-smoke.sh`, `scripts/debug-fault-proxy.mjs`; `docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`; 7 PNGs (type/size checked: 1080x2400 RGB). Cross-read shared contracts/auth/sync/data signatures, `gradle/libs.versions.toml`, settings/root/shared build files.

## Validation
- `bash -n scripts/android-smoke.sh`: pass. `node --check scripts/debug-fault-proxy.mjs`: pass.
- Functional proxy test (dummy upstream, `--scenario drop-push-response`): 1st push → ECONNRESET after upstream 200; 2nd push → 200; pull → 200; old wrong path → 403; traversal path → 200 (Low finding).
- `JAVA_HOME=jdk-21 ./gradlew --dependency-verification=off --offline :androidApp:tasks --all`: works without SDK; no `compileDebugKotlin` task (C1 proof).
- `./gradlew :shared:jvmTest`: success (up-to-date; QA cycle 2 ran `--rerun-tasks` 96/96).
- NOT run (no Android SDK/adb/emulator): `:androidApp:assembleDebug`, lint, `connectedDebugAndroidTest`, merged manifest, real Hub/device scenarios, screenshots, release gate. Not-run ≠ passed.
- Static symbol grep vs `shared/src` established C2.

## Recommended actions (order)
1. Add Kotlin Android plugin; confirm `compileDebugKotlin` exists (C1).
2. Fix 5 unresolved symbols (C2) → provision SDK 35 + platform-tools → `:androidApp:assembleDebug`, fix next compiler errors; write real `gradle.lockfile` (`--write-locks`).
3. Fix ACCESS_PENDING logout (C3), preserve safeError on unauth (H3), surface leave failures (H4).
4. Make wipe recovery use `completePendingWipe`, off main thread; clear active account only if matching (H1).
5. Make `cancelAndAwait` fail-closed on mutex timeout (H2).
6. Fix isolation test fixture (H6); add plan-required scenarios (switch A→B, in-flight cancel, restart re-enqueue) and run on emulator.
7. Release-specific API key/origin (H5); recapture real PNGs; document KEEP policy.

## Metrics
- Type coverage: n/a (Kotlin not compiled; ≥5 unresolved symbols).
- Tests: JVM shared 96/96; Android instrumented 0 executed (8 written, 1 known-wrong, several racy).
- Lint: not run.

## Unresolved questions
1. Where does production `HUB_API_KEY`/origin come from for release (secret provider vs buildConfigField per variant)?
2. Add `LoadMore`/UnknownSuperseded/protocol/reset actions to Phase 06 contract, or drop from Phase 07 scope? (Plan step 7/9 requires UI paths.)
3. Who provisions the Android SDK/emulator (Xpra :100) so the build/device gates can finally run?
4. Is `file-ownership.md` amended for catalog/root-build edits needed for the Kotlin plugin (shared Gradle files are not Phase 07 rows)?
