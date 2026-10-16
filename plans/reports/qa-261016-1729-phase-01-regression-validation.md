# Phase 01 Regression Validation

## Test Results Overview

All six requested commands completed successfully.

| Scope | Command | Result |
|---|---|---:|
| Shared JVM | `cmd /c "gradlew.bat :shared:jvmTest --dependency-verification=off"` | **229 passed**, 0 failures shown |
| Desktop | `cmd /c "gradlew.bat :desktopApp:test --dependency-verification=off"` | **64 passed**, 0 failures shown |
| Android app unit tests | `cmd /c "gradlew.bat :androidApp:testDebugUnitTest --dependency-verification=off"` | **12 passed**, 0 failures shown |
| Hub fixture smoke | `node scripts/hub-live-smoke.mjs --fixture-test` | **18/18 checks passed** |
| Source security diagnostics | `node scripts/release-security-check.mjs --diagnose-source` | **6 checks passed; CLEAN** |
| Strict-v2 wire vectors | `node scripts/strict-v2-vector-runner.mjs` | **10/10 valid vectors passed; 11/11 rejection cases verified** |

Gradle totals: **305 test cases passed across the three requested Gradle tasks**. Counts were tallied from the console's individual `PASSED` lines; XML summaries were inaccessible. No failed cases appeared. The console did not report a skipped-case total.

## Coverage Metrics

Not collected: line, branch, and function coverage percentages unavailable. Coverage was not part of the six requested commands.

## Failed Tests

None reported by the executed commands.

## Performance Metrics

Gradle reported task durations of **3s** (shared JVM), **4s** (desktop), and **9s** (Android app unit tests). These are command build/task durations, not dedicated benchmark measurements. The three Node validations had no timing report in their captured output.

## Build Status

All three Gradle tasks reported `BUILD SUCCESSFUL`. The source security diagnostic returned `CLEAN`.

Warnings observed:
- Gradle deprecation notice: deprecated features may be incompatible with Gradle 9.0.
- Android Kotlin `expect`/`actual` classes Beta warnings during `:shared:compileDebugKotlinAndroid`.

## Critical Issues

None identified in the requested checks.

## Scope and Recommendations

- Android coverage was the requested `:androidApp:testDebugUnitTest` task; no instrumentation/device test was run.
- Hub smoke used fixture mode (offline), not a live Hub. Source diagnostics audited source and did not inspect a built release APK.
- No source edits were made during validation. No clean/unchanged working-tree claim is made.
- For broader release evidence, run coverage collection if required by policy and separately validate device/instrumentation, live-service, or built-APK behavior where those gates apply.

## Next Steps

Current six-command regression gate: **pass**. No test-failure remediation is indicated. Track the Gradle 9 deprecation warning separately.

## Unresolved Questions

None.
