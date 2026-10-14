# Code Review Cycle 3: Phase 07 — Android composition & runtime integration

Date: 2026-10-14 | Reviewer: ReviewerPhase07Cycle3 | Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md`

## Verdict: 4/10 — NOT ACCEPTED. Source improved vs cycle 2 (3/10); Android compile + device gates still unrun; teardown correctness gaps remain.
Phase 07 stays open. Not accepted ≠ rejected-for-design: cycle-2 compile blockers landed; remaining blockers = unverifiable (no SDK) + teardown/rollback defects + overclaiming docs.

## Cycle 2 → Cycle 3 fix verification (source-inspected, not compiled)
| Cycle 2 item | Status | Evidence |
|---|---|---|
| C1 kotlin plugin | FIXED | `androidApp/build.gradle.kts:3` `kotlin("android")`; `:androidApp:tasks --all` lists `compileDebugKotlin` + `compileDebugAndroidTestKotlin` |
| C2 SafeError/OutboxEntity/CredentialBundle symbols | FIXED (static) | `SafeError.Internal/Validation` exist (`contract/safe-errors.kt`); `OutboxEntity.deliveryState`; `CredentialBundle(a,r)` + `vault.store(key,bundle)`; all 19 `TodoAction` variants covered in `when`; `ServerBaseEntity` fields match |
| C3 ACCESS_PENDING logout | FIXED | `session-view-model.kt` `requestLeave()` + `AuthSubmitAction.Logout` fall through to `authSession.logout()`+`clearActiveAccount()` when no runtime |
| H1 wipe recovery | PARTIAL | delete result now honoured; vault/marker/active file cleared only if deleted; active account cleared only if key matches. Still: `runBlocking` on main in `Application.onCreate`; own loop instead of shared `AccountLeaveProtocol.completePendingWipe` (DRY) |
| H2 cancelAndAwait fail-open | FIXED | throws `IllegalStateException` on work-not-finished and on mutex timeout (`android-sync-scheduler.kt:84-119`); protocol rolls back on exception. VM calls `runtime.leaveProtocol` directly so no self-deadlock |
| H6 isolation fixture | FIXED | key derived via `AccountKey.create(hubConfig.canonicalOrigin, userId)` |
| Medium clock skew | FIXED | `maxOf(now, createdAt)` in edit+toggle |
| Medium atomic active_account | FIXED | tmp + `ATOMIC_MOVE` (exceptions still swallowed) |
| Sanitized VM errors | FIXED | fixed strings; no `e.message` in VM (still logged in Application/Worker) |
| Docs honesty | PARTIAL | pending device/SDK stated; see W9 |

## Critical (acceptance blockers)
1. **Kotlin never compiled by anything.** `:androidApp:compileDebugKotlin` → `SDK location not found` (task dependency resolution; no source compiled). Further compile errors are plausible once SDK exists (e.g. uninspected `jvmTarget`, see W7). Not claimable as "builds".
2. **Required gates unmet** (plan Success Criteria): `assembleDebug`, `connectedDebugAndroidTest`, merged-manifest inspection, real Hub/device scenarios, lint, release gate all NOT RUN; `gradle.lockfile` is stub `empty=` (plan: "never placeholder"); 7 PNGs are design mockups, not device captures. Host has no SDK/adb/emulator (`find` found none).
3. **Docs overclaim** (`docs/android-acceptance.md:5` "Implementation Complete"; `:41` quiescence "under `coordinatorMutex.withLock`" — real impl is tryLock polling + WorkInfo poll; `android-test-summary.md:12-16,"UI interaction and CRUD assertions"` — tests are ViewModel/store-level, no UI nodes; summary §1 says "all critical issues … remediated" though cycle 2 listed more). Plan status says ~60%/not build-verified; docs must match.

## Warnings
- **W1 Failed-leave rollback leaves broken background state.** `AccountLeaveProtocol.performGuardedTeardown` persists `gen+1` before `cancelAndAwait`; on gate failure it clears marker + unfreezes but NOT generation. Android side already cancelled all tagged work (periodic included) and nothing re-enqueues. Result: old-generation runtime works foreground, but every new one-shot/periodic request carries stale generation and the worker returns `failure()`; periodic safety net gone until relogin/restart. Fix: restore previous generation on rollback (Phase 05 owner) and `schedulePeriodic` after failed leave (VM).
- **W2 Cancellation swallowed in teardown** (shared protocol): `catch (_: Exception)` around `cancelAndAwait` catches `CancellationException` → returns false instead of propagating; `checkReviewWork()` exception after `isFrozen=true` leaves store frozen. Phase 05 owner.
- **W3 `AccountSyncCoordinator.requestLeave()` self-deadlocks if wired to the Android gate** (holds `coordinatorMutex`, gate then waits on same mutex ≥5 s → throws). Unused today (VM bypasses); API is a trap — remove or document.
- **W4 [INFERENCE] Login/register error lost.** Failure flips `AUTHENTICATING→UNAUTHENTICATED`; VM collector then `onUnauthenticated()` → `_uiState.value = TodoUiState()` after `handleAuth` already set `safeError` (collector resumes after current Main coroutine). Wrong-password message likely never renders. Reset only account-bound fields.
- **W5 Leave failures silent** (`requestLeave` executeLeave==false, `ConfirmLeave` false): no `SafeError`, guard stays. Store may be left frozen (delete-failed path) with no user feedback.
- **W6 Release config:** `HUB_API_KEY="doggo-dev-api-key"` and placeholder `https://hub.glean.dev` in `defaultConfig` ship in release; needs per-variant fields (Phase 08 gate). `work-runtime-ktx`, androidx.test coords hard-coded (not in catalog). Release gate matches any task name containing "Release" and reads `android` ext in `whenReady` (works only without config-cache; undocumented).
- **W7 No explicit Kotlin `jvmTarget`** in androidApp while `compileOptions`=17, `:shared` pins 17, host JDK 21 [INFERENCE: possible JVM-target mismatch error]. Add `kotlin { compilerOptions { jvmTarget.set(JVM_17) } }`.
- **W8 `getOrCreateAccountRuntime` falls back to `userId = "default"`** when identity null (silent mis-partition); `TodoDeviceTest.testAccountRuntimeCreationAndStoreAccess` depends on it. Throw instead; seed identity in test.
- **W9 Test quality:** `BackgroundIsolationTest` leaves singleton `authSession` AUTHENTICATED (tearDown clears vault files only) → `TodoDeviceTest.testInitialUiState` order-dependent; `restoreSession` result unasserted; `delay(50)` assertions racy vs init collector; plan-required scenarios (foreground+worker overlap, switch A→B, in-flight cancel, restart re-enqueue, force-stop) absent; no test for mutex-timeout fail-closed (QA cycle 3 asked for one).
- **W10 Startup:** `runBlocking(Dispatchers.IO){vault.clear}` + file scan in `Application.onCreate` (main thread; ANR/StrictMode). Worker/Application log exception messages + `AccountKey` (pseudonymous id).
- **W11 Residual worker TOCTOU:** worker checks generation (line 54) then `getOrCreateAccountRuntime`; if cancellation not yet delivered and leave completes between, it could open an empty DB for a logged-out key and set it active. Mitigated by tag cancel + frozen check; needs a race test, not just timeout test.

## Suggestions
- Per-action coroutine in `onAction` is unordered (rapid toggles); serialize via Channel.
- Every `progress` emission → full `loadTodos()` (+`getAllOutbox()` if conflicts); debounce / query by state. Conflict never cleared when count→0.
- Document KEEP/re-enqueue policy in scheduler KDoc (plan step 4); safe in practice (round drains outbox; foreground triggers).
- `active_account.dat`: plaintext email/username, main-thread I/O, swallowed errors; add `<exclude domain="file" path="."/>` to backup rules (backup already off).
- Drop `ACCESS_NETWORK_STATE` permission (unused, YAGNI); drop redundant `Surface` in `MainActivity` (`DoggoApp` already themes).
- Proxy: forward `url.pathname+url.search` (allowlist validates normalized pathname but upstream gets raw `clientReq.url` → validation/route disagreement; upstream is loopback Hub only, so Low); add `clientRes.on('error')`.
- `android-smoke.sh`: `--reverse` moot (debug origin fixed 10.0.2.2); `--capture` overwrites `android-debug-empty.png` with whatever is on screen; help text says emulator-5554 default but code auto-detects; never runs instrumented tests/Hub smoke.
- "Load more", UnknownSuperseded/protocol/reset UI paths have no contract action (Phase 06 owner decision).

## Positive
- Manifest: single exported launcher, cleartext off, backup off, debug-only NSC with exact hosts, no subdomains/custom CA; extraction rules cover cloud+transfer.
- Account-tag cancellation; worker check order (wipe → auth → account → generation → runtime gen); cancellation rethrown; retry before cold-start restore.
- Leave protocol bound per-runtime (no cross-account capture); fail-closed gate now throws; marker kept on delete failure.
- Fault proxy loopback-only, 2xx-gated single-shot drop, no payload/header logging; signing gate fails closed with no debug-key fallback.
- Docs now state device/SDK gates pending.

## Reviewed files (25/25 + shared cross-reads)
androidApp: `build.gradle.kts`, `gradle.lockfile`, `src/main/AndroidManifest.xml`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `src/debug/AndroidManifest.xml`, `src/debug/res/xml/network_security_config.xml`, `main-activity.kt`, `doggo-application.kt`, `session-view-model.kt`, `android-sync-scheduler.kt`, `doggo-sync-worker.kt`, `todo-device-test.kt`, `background-isolation-test.kt`; `scripts/android-smoke.sh`, `scripts/debug-fault-proxy.mjs`; `docs/android-acceptance.md`, `docs/evidence/android-test-summary.md`; 7 PNGs (cycle 2 type/size check; not re-viewed). Cross-read: `account-leave-protocol.kt`, `account-sync-coordinator.kt`, `hub-auth-session.kt`, `safe-errors.kt`, `todo-action.kt`, `todo-models.kt`, `todo-ui-state`, `android-account-files.kt`, `android-database-builder.kt`, `gradle/libs.versions.toml`, root/settings/shared build.

## Validation (JAVA_HOME=$HOME/.local/share/jvm/jdk-21, `--dependency-verification=off --offline`)
| Command | Result |
|---|---|
| `:androidApp:tasks --all` | BUILD SUCCESSFUL; `compileDebugKotlin`, `compileDebugAndroidTestKotlin` present |
| `:androidApp:compileDebugKotlin` | FAIL (exit 1): `SDK location not found` — no Kotlin compiled |
| `:shared:jvmTest --rerun-tasks` | BUILD SUCCESSFUL, 15 tasks executed, exit 0 (96/96 count per QA cycle 3; not re-parsed here) |
| `bash -n scripts/android-smoke.sh` | pass |
| `node --check scripts/debug-fault-proxy.mjs` | pass |
| NOT RUN (no SDK/adb/emulator) | `assembleDebug`, lint, `connectedDebugAndroidTest`, merged manifest, device/Hub scenarios, screenshots, release gate |

## Recommended actions (order)
1. Provision SDK 35 + platform-tools (+ emulator, Xpra :100) → `:androidApp:compileDebugKotlin`, fix compiler errors, `--write-locks`, `assembleDebug`.
2. Fix W1/W2 in Phase 05 protocol (restore generation, propagate `CancellationException`, unfreeze on exception); VM re-schedules periodic after failed leave; surface leave errors (W5).
3. Fix W4 (preserve `safeError` on unauth), W8, W7, W9 (reset authSession in tearDown; add plan scenarios + mutex-timeout test).
4. Per-variant release `HUB_API_KEY`/origin (W6).
5. Correct docs headers/claims (Critical 3); recapture real PNGs after device run.

## Metrics
- Kotlin type coverage: n/a (androidApp never compiled).
- Tests: JVM shared green; Android instrumented 0 executed (8 written; plan-required scenarios missing).
- Lint: not run.

## Unresolved questions
1. Who provisions Android SDK/emulator so build/device gates can run (blocks acceptance)?
2. Should rollback of a failed leave restore the persisted generation (Phase 05 change) or stay fail-closed and force re-login?
3. Production `HUB_API_KEY`/origin source for release (secret provider vs per-variant `buildConfigField`)?
4. Add LoadMore/UnknownSuperseded/protocol/reset actions to Phase 06 contract or drop from Phase 07 scope?
5. Is `file-ownership.md` amended for shared Gradle/catalog edits (none needed now; Kotlin plugin resolves via the KMP plugin on root classpath)?
