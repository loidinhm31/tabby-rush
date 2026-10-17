# Code Review — Phase 09 Linux Desktop + RPM

Date 2026-10-14. Reviewer Phase09CodeReviewer. Plan: `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-09-linux-distribution.md`.

## Score: 3/10 — NOT acceptable as phase-complete

Compiles, 19 unit tests green, target gate + lock + SPI mechanism work. But: second-instance guard ineffective (data-loss race), evidence docs fabricated/overclaim, RPM + installed + live Secret Service + native screenshot gates not done, vault impl deviates from plan, release config hardcoded dev key.

## Critical

1. **Single-instance guard does not guard** (`main.kt:65-95,367-374`). `DesktopController` ctor acquires guard (null if held) but continues: creates vault, `init{}` runs `completePendingWipesOnStartup()` (deletes DB files + clears vault) and launches session restore/Room open. `launchDesktop` checks `instanceGuard == null` only AFTER controller built. Second launch can delete first instance's DB when wipe marker present, race token refresh, open DB. Violates plan step 3 ("second launch exits without vault/DB open") + test claim. Fix: acquire guard in `launchDesktop` before constructing controller; pass guard in; no vault/DB code in ctor path when absent.
2. **Fabricated evidence.** `docs/evidence/linux-*.png` = synthetic mockups (fake title bar "TabbyRush — Linux Desktop (Workspace)", missing-glyph tofu in badge, hardcoded sample todos "2 mins ago", "Strict-v1 Synced"). `linux-test-summary.md` itself says "Visual mockups generated". Plan step 11 requires actual sanitized native Xpra screenshots. No display (`DISPLAY` unset, no D-Bus per QA diag) → app never ran. Delete or recapture; do not ship as evidence.
3. **Test summary/acceptance claim things not run or not existing.** `linux-test-summary.md` lists `testResolveProviderSingleMatch`, `testNeverFallsBackToPlaintext`, `testSyncSchedulerImmediateAndPeriodic`, `testStatusTransitions` — not in tree. Claims "Package Audit: `--dist` verified zero Windows DLLs", "Launcher ELF", "Verified without --write-locks", "Verification Passed" — QA report states packaged distribution not validated; no RPM exists; `rpmbuild` absent. Acceptance status line "Verification Passed" contradicts own table (RPM/installed pending). Violates no-fabrication contract.
4. **`testResolveProviderMultipleMatchesFailsClosed` has zero assertions** (`platform-vault-dispatcher-test.kt:52-84`). Builds p1/p2/customLoader then ends with a comment. Passes trivially; QA report counts it as "Ambiguous providers fail closed PASSED". Multiple-provider policy is untested (dispatcher code looks right). Also unused imports; deprecated `URL` ctor. Also plan wants wrong-host + load-failure cases: not present.
5. **Phase gates unmet**: no `packageRpm` (rpmbuild missing), no installed RPM run, no real Secret Service/D-Bus proof, no runDebug vs Hub, no fault-proxy run, no HTTPS release run, no strict repeat. Todo list all unchecked, plan still "pending 0%". Roadmap not updated. OK to be blocked, but must be reported as blocked, not "Passed".

## High / Warnings

6. **Vault = `secret-tool` subprocess, not D-Bus binding** (`linux-secret-service-vault.kt:34-118`). Plan/Arch: supported D-Bus binding. `dbus-java` is in build + lockfile but 0 usages → dead dependency (YAGNI) AND plan deviation. Consequences:
   - RPM declares no `Requires: libsecret` (secret-tool). Installed app silently UNAVAILABLE where missing. No `rpmRequires` set.
   - `checkStatus`: exit 0/1 → READY, and `else -> READY` for ANY other exit code/message (fail-open-ish; locked collection w/o prompt hangs → 2s → UNAVAILABLE, ok, but unknown errors → READY). `exitCode==1` also = generic secret-tool failure. Search on `service tabbyrush-probe` doesn't verify read/write ability. Status spawns a process on every `.status` read (session calls it on restore, per-property get).
   - `lookupSecret` returns null for any non-zero exit (locked/unavailable/crash) = indistinguishable from "no credential" → `restoreSession` false → user treated as logged-out instead of actionable "keyring locked". Should throw / map to LOCKED/UNAVAILABLE.
   - `clearSecret` ignores exit code + timeout, never destroys process. `completePendingWipesOnStartup` then clears wipe marker → orphaned secret persists silently after "wipe". Must fail closed (keep marker) if clear fails.
   - `lookupSecret` reads stdout fully BEFORE `waitFor(5s)` → timeout ineffective (blocks on `readText` if process hangs). `storeSecret`/`lookup` never drain stderr when exit 0; `store` stderr read after wait ok. Env passthrough fine. `secret-tool` resolved from PATH (PATH hijack, low).
   - Tokens go via stdin (not argv) — good. Attribute `account=<accountKey>` ok. "Atomic replacement": secret-tool store replaces same-attribute item; fine, but untested against real daemon. Plan wants identity/account attributes + explicit deletion; only service/account used.
   - Fake-client tests only; vault never exercised against real daemon.
7. **Release config hardcoded** (`main.kt:68-73`): `"tabbyrush-dev-api-key"` dev key + unvalidated `https://hub.glean.dev` literal in main source, shipped in RPM. Plan step 4: immutable approved origin/key external owner-controlled input; contracts: no dev key in release. Same open defect as Android W6/H5. Also `DevelopmentLauncher` (DEBUG, http loopback) ships in release jar; plan says registered only by `runDebug`. Move to a debug-only source set or exclude.
8. **Dependency verification fails** for any build without `--dependency-verification=off` (`Dependency verification failed for configuration 'classpath'`; reproduced; `:shared:jvmTest` fails same → pre-existing from Phase 02 `gradle/verification-metadata.xml`, buildscript classpath unlisted). QA + `linux-desktop-guide.md:65` normalise `--dependency-verification=off`. Supply-chain control disabled in documented flow; strict repeat gate unprovable. Guide must not recommend it; fix metadata via 02 owner. Also `compose.desktop` native/skiko linux artifacts checksum not demonstrated.
9. **Unowned stray file** `settings-gradle.lockfile` (content `empty=incomingCatalogForLibs0`) generated by lock-all; not in 09 registry. Remove or register.
10. **Package metadata guessed**: `vendor = "TabbyRush Team"`, `copyright`, no `rpmLicenseType`, no maintainer, no approved values; plan: "actual approved values, not guessed". `includeAllModules=true` plus redundant `modules(...)` list (modules ignored/ dead). No size tradeoff recorded. `upgradeUuid` is a valid v4 but no doc records it as frozen.
11. **`runBlocking` in controller init path** (`completePendingWipesOnStartup`, `main.kt:104`) on Swing EDT; 5s subprocess × N accounts blocks UI. Move to IO coroutine before runtime creation.
12. **`getOrCreateAccountRuntime` `?: "default"`** userId fallback (`main.kt:151`) = silent mis-partition (same as Android W8). Throw.
13. **Silent exception swallowing**: `catch (_: Exception) {}` in wipe completion, session restore (hides vault LOCKED/corrupt → no user-visible actionable error, violates plan "locked/unavailable/corrupt actionable"), `saveActiveAccount` (failure → no cold-restart restore), `reloadTodos`, leave failures (no SafeError when `discardAndLeave()/executeLeave()` false). Scheduler swallows all `Exception` incl. `CancellationException` inside `catch (_: Exception)` in periodic loop (`desktop-sync-scheduler.kt:53`) → cancellation swallowed during `syncRunner`; loop then `isActive` false so exits, but rethrow anyway.
14. **Scheduler lifecycle gaps**: `schedulePeriodic` calls `cancel()` that also cancels the immediate job (unintended). `triggerImmediate` check-then-put race (non-atomic on ConcurrentHashMap). `immediateJobs.remove(key)` in finally may remove a newer job. `cancelAndAwait` has a window where a concurrent `triggerImmediate` re-launches. Periodic first tick after 15min, no initial/foreground-resume sync other than explicit. `dispose()` doesn't cancel `swingScope` (collector coroutine leaks; process exits anyway) and does not await in-flight sync before `database.close()`. "Foreground-only" = window-lifetime only; no pause when minimized — acceptable? (question).
15. **Single-instance guard**: `catch (_: Exception) → null` conflates "lock held" with I/O failure/permissions → prints "another instance running" wrongly. `OverlappingFileLockException` only same-JVM. Lock file/dir created with default umask (shared `JvmAccountFiles` mkdirs no 0700, plan: OS-private DB paths; owned by 05, flag). Diagnostic write of pid ok. `lockFile` field unused.
16. Smoke script: `rpm -qlp | grep -q "/bin/tabbyrush"` + `--dist` assumes `bin/tabbyrush`; plan says discover launcher from desktop metadata, not guessed. Missing providers only WARNING not failure (dist `FOUND_LINUX_PROVIDER=false` still passes; `runtime/bin/java` missing = WARNING). Windows check `grep -iE 'windows'` on file paths may false-positive; `grep` prints matches then errors fine. Does not prompt install / verify hash vs build provenance / version / arch per plan step 9. `unzip -l` per jar for provider class: descriptor resource not checked.
17. Tests weak overall: lifecycle tests cover guard, scheduler, `EndpointPolicy` (shared code, not desktop). `DesktopController` (all real logic, ~300 LOC) untested. No test for locked/corrupt wipe interplay, profile lock + controller, generation cancellation, atomic refresh replacement across restart (plan step 7). `FakeSecretServiceClient` in Linux tests is the only vault coverage.

## Suggestions

- main.kt 406 LOC single file with `DesktopController` ~300 LOC: split (controller / runtime holder / launcher), per kebab-case modularization rule. Duplicated reload/trigger patterns copy Android VM; consider shared extraction later (DRY), not in this phase.
- `triggerSync()` calls `coordinator.triggerSync()` and `scheduler.triggerImmediate()` (which calls `coordinator.triggerSync()` again) = double sync per action.
- `StoredCredentialPayload` has no schema version; add `v` field for future migrations.
- Use `Json` instance with `explicitNulls`/strict mode; `ignoreUnknownKeys=true` on a security payload is lenient; ok but record.
- Replace `process` spawns with dbus-java (already locked) or drop dbus dep and document `libsecret` Requires.
- Add `-Xexpect-actual-classes` suppression in shared (QA note); `URI.create(...).toURL()` in test.
- `detectedHost` duplicates `PlatformVaultDispatcher.detectHostTarget` logic (build vs runtime; acceptable).
- Docs: guide hardcodes guessed RPM filename `tabbyrush-1.0.0.rpm`; `sudo dnf install libsecret gnome-keyring` ok. Guide says "via secret-tool / D-Bus".
- Acceptance doc should state "pending/blocked" status, not "Verification Passed".

## Positive

- Target selection + mismatch gate works before resolution (verified: `-PdesktopTarget=windows` → "does not match detected host OS 'linux'").
- Explicit per-target source/resource dirs, no recursive globs; Linux builds with no Windows files; `gradle-linux.lockfile` 161 entries, custom lockFile per target.
- SPI dispatcher: exact-count policy, fail-closed, clear messages; descriptor FQN correct; no static Windows reference.
- Tokens sent to `secret-tool` on stdin, not argv; bundle stored as single JSON item; corrupt → exception; never plaintext.
- Guard keeps lock file (no inode race); scheduler implements `LeaveSchedulerCancelGate` with cancel+join.
- Release mode literal in `main`, debug only via `runDebug` task; `HubConfig.toString` redacts key.
- Windows upgradeUuid present from outset; MSI/RPM both configured.

## Reviewed files

desktopApp/build.gradle.kts, gradle-linux.lockfile, main.kt, credential-vault-provider.kt, platform-vault-dispatcher.kt, single-instance-guard.kt, desktop-sync-scheduler.kt, linux-secret-service-vault.kt, META-INF service descriptor, 3 test files, scripts/linux-package-smoke.sh, docs/linux-desktop-guide.md, docs/linux-acceptance.md, docs/evidence/linux-test-summary.md, linux-populated.png (viewed; others same generator), shared contracts/jvm-account-files/hub-auth-session (context), QA report, settings.gradle.kts, settings-gradle.lockfile. ~1.2k LOC code + docs.

## Validation

| Command | Result |
|---|---|
| `JAVA_HOME=jdk-21 ./gradlew -PdesktopTarget=linux :desktopApp:test --offline` | FAIL: Dependency verification failed for configuration 'classpath' (pre-existing; `:shared:jvmTest` same) |
| same + `--dependency-verification=off` | BUILD SUCCESSFUL (19 tests per QA report; I did not read XML results—build dir blocked by tool policy) |
| `-PdesktopTarget=windows :desktopApp:compileKotlin --dependency-verification=off` | Fails early as designed: "Requested target 'windows' does not match detected host OS 'linux'" |
| `bash scripts/linux-package-smoke.sh --diagnose` | rpmbuild NOT installed; secret-tool present; no D-Bus, no DISPLAY; default java = 25 |
| `:desktopApp:packageRpm`, `createDistributable`, `--rpm`, `--dist` | NOT run (no rpmbuild / could not inspect build dir). Claims in evidence unverifiable |
| Live Secret Service, Xpra run, screenshots, fault proxy | NOT possible/performed |

## Recommended actions (priority)

1. Fix guard ordering (C1) + add controller-level test.
2. Remove/replace fabricated screenshots; correct `linux-test-summary.md` + `linux-acceptance.md` to actual state (blocked on rpmbuild/sudo/D-Bus/display).
3. Add real assertions to multi-provider test; add wrong-host/load-failure cases.
4. Vault: map non-zero exits to LOCKED/UNAVAILABLE/throw, fail on `clear` error (keep wipe marker), drain/timeout correctly, or switch to dbus-java; add `rpmRequires` or drop dbus dep. Add real-daemon integration test (opt-in).
5. Externalise release key/origin (owner-supplied), drop dev key; keep `DevelopmentLauncher` out of release.
6. Fix verification-metadata via 02 owner; remove `--dependency-verification=off` from guide.
7. Obtain approved vendor/license metadata; drop redundant `modules(...)`; remove `settings-gradle.lockfile` or register.
8. Install `rpm-build` (sudo), run `packageRpm`, smoke `--rpm`, install, run in Xpra with unlocked keyring, capture real screenshots, then mark phase todo.

## Unresolved questions

1. Is `secret-tool` CLI an accepted vault mechanism (plan says supported D-Bus binding)? If yes, drop dbus-java and add RPM `Requires`.
2. Approved vendor/maintainer/license metadata and production Hub origin + owner-controlled app key source?
3. Who owns `gradle/verification-metadata.xml` fix (02) and `settings-gradle.lockfile`?
4. Is shared `JvmAccountFiles` 0700 perms (05-owned) in scope for 09 sign-off?
5. Is sudo/rpm-build install + keyring-unlocked Xpra session obtainable, or is Phase 09 accepted as "blocked pending host"?
6. "Foreground-only" scheduling: window-lifetime sufficient, or pause on minimize/unfocus?
