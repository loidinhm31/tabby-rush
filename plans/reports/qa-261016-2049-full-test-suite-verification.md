# QA Report: Full Test Suite Verification

**Date:** 2026-10-16  
**Scope:** Shared JVM, desktop, Android debug unit tests; release security tests and source diagnosis; Hub fixture smoke; strict-v2 rehearsal self-test.

## Test Results Overview

The literal `./gradlew` invocation is not executable on this Windows host (`%1 is not a valid Win32 application`). The equivalent `cmd.exe /c gradlew.bat ...` wrapper was used. The first shared run was `UP-TO-DATE`; it was rerun with `--rerun-tasks` so its tests executed.

| Command / module | Passed | Failed | Skipped | Duration | Result |
|---|---:|---:|---:|---:|---|
| `:shared:jvmTest` | 229 | 0 | 0 | 15.40 s forced rerun; initial command 1.64 s, `UP-TO-DATE` | **PASS** |
| `:desktopApp:test` | 0 | 0 | 0 | 1.33 s | **BLOCKED** before tests |
| `:androidApp:testDebugUnitTest` | 0 | 0 | 0 | 2.25 s | **BLOCKED** before tests |
| `release-security-check.test.mjs` | 12 | 0 | 0 | 0.085 s wall; 11.37 ms test-runner duration | **PASS** |
| `release-security-check.mjs --diagnose-source` | 6 audit checks | 0 | — | 0.113 s | **PASS** |
| `hub-live-smoke.mjs --fixture-test` | 18 | 0 | 0 | 0.143 s | **PASS** |
| `strict-v2-cutover-rehearsal.mjs --self-test` | 8 | 0 | 0 | 11.00 s | **PASS** |
| **Executed test cases total** | **267** | **0** | **0** | — | **Partial suite; two Gradle test tasks blocked** |

Shared count is based on the 229 passing test-case results printed by Gradle. Desktop and Android produced no test-case results; no prior/stale reports were counted. The source diagnostic's six PASS checks are audit checks, not unit tests. No test failures or skips were reported among executed cases, but the full requested suite is **not green** because desktop resource processing and Android compilation fail.

## Failed Test Tasks / Exact Causes

### Desktop

` :desktopApp:test --dependency-verification=off` fails at `:desktopApp:processResources`:

> Entry `task-surface-tray.png` is a duplicate but no duplicate handling strategy has been set.

The resource file exists at `desktopApp/src/main/resources/task-surface-tray.png`. The desktop main resource roots are configured in `desktopApp/build.gradle.kts:31`; Gradle did not print the second input path. No desktop tests ran. The forced rerun and the requested non-forced command both failed on this same resource-processing error.

### Android

` :androidApp:testDebugUnitTest --dependency-verification=off` fails at `:androidApp:compileDebugKotlin`; no Android unit tests ran. Compiler diagnostics:

- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt:86:51` — unresolved `loadPersistedSchedule`.
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt:229:38` — unresolved `loadTodo`.
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-access.kt:230:35` — unresolved `isDeleted` on the unresolved `todo` value.
- `androidApp/src/main/kotlin/dev/tabbyrush/android/task-surface-time-receiver.kt:26:20` — unresolved `Intent.ACTION_TIME_SET`.

The forced rerun and the requested non-forced command both failed with these diagnostics.

## Coverage and Performance

- **Coverage:** No Jacoco/Kover coverage task or report was found in the searched Gradle configuration; line, branch, and function coverage are unavailable.
- **Measured execution:** Durations above are command wall times, except the explicitly labeled Node test-runner duration. The shared JVM forced test task completed successfully in 15.40 s.
- No dedicated benchmark, memory check, or repeatability/flakiness campaign was run.

## Build Status and Warnings

**Build status: FAIL / incomplete.** Shared JVM tests and all three Node commands passed. Desktop stopped on duplicate resource processing; Android stopped on Kotlin compilation, so regression status for those modules is unverified.

Warnings observed:

- Gradle reports deprecated features that will be incompatible with Gradle 9.0.
- Kotlin `expect`/`actual` classes are marked Beta during shared/Android compilation.

All Gradle commands used `--dependency-verification=off` as requested; dependency verification was bypassed, not validated.

## Critical Issues and Recommendations

1. Resolve the duplicate `task-surface-tray.png` resource input (or declare intentional duplicate handling) and rerun `:desktopApp:test`.
2. Align the Android task-surface access calls with APIs available on the runtime store and replace the unresolved time-action reference with a supported Android action; rerun `:androidApp:testDebugUnitTest`.
3. Rerun the complete suite after fixes, using `--rerun-tasks` for Gradle test tasks to avoid reporting cached results as fresh execution.
4. Generate coverage separately if percentage thresholds are required; review Gradle/Kotlin deprecation and Beta warnings before toolchain upgrades.

## Unresolved Questions

None. Desktop and Android blockers remain unresolved and require a fresh full-suite run after fixes.
