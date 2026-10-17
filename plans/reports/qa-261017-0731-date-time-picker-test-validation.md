# QA Validation Report — Date/Time Picker Changes

**Date:** 2026-10-17  
**Environment:** Windows; Gradle dependency verification disabled as requested

## Test results

| Command | Result | Tests/checks |
|---|---|---:|
| `cmd /c gradlew.bat :shared:jvmTest --dependency-verification=off` | **FAILED** | 239 total: 237 passed, 2 failed |
| `cmd /c gradlew.bat :desktopApp:test --dependency-verification=off` | **PASSED** | 99 passed |
| `cmd /c gradlew.bat :androidApp:testDebugUnitTest --dependency-verification=off` | **PASSED** | 34 passed |
| `node scripts/hub-live-smoke.mjs --fixture-test` | **PASSED** | 18/18 checks |
| `node scripts/release-security-check.mjs --diagnose-source` | **PASSED** | 6/6 source audit checks; result CLEAN |
| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | **PASSED** | 8/8 checks |

**Gradle total:** 372 tests; 370 passed, 2 failed. **Node total:** 32 checks; all passed. No skipped tests were reported in the task output.

## Failures

- `DateTimePickersTest.testDateRoundTripUtc` — `java.lang.AssertionError`, `date-time-pickers-test.kt:26`.
- `TodoSemanticsTest.testDeadlinePickerCanonicalFormatsAndOffsetClearing` — `java.lang.AssertionError`, `todo-semantics-test.kt:628`.

The shared JVM task failed only because of these test failures. Desktop and Android unit test tasks completed successfully.

## Build, coverage, and performance

- **Build status:** Desktop and Android test builds succeeded. Shared test compilation completed, but the task failed on the two assertions above. Gradle emitted a deprecation warning: deprecated features make the build incompatible with Gradle 9.0.
- **Coverage:** Not collected; no coverage command was requested or run.
- **Performance:** No benchmarks run. Observed Gradle command wall times: shared 8.63s, desktop 4.13s, Android 3.31s. Strict-v2 rehearsal command wall time: 10.93s.

## Critical issue and next steps

The shared JVM suite is red on two date/time-related assertions. Investigate the expected UTC round-trip and canonical-format/offset-clearing behavior at the cited assertions, then rerun the shared suite and the requested regression commands after any fix.

**Unresolved questions:** The test runs do not establish the root cause or whether either assertion reflects stale expectations versus a behavior defect.
