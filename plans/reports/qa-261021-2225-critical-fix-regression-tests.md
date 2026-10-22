# Critical-Fix Regression Test Verification

**Date:** 2026-10-21 22:25 (Asia/Bangkok)  
**Scope:** Seven requested suites, after the critical fixes. Test verification only; no code changes or patch review.

## Test results

| Suite | Status | Passing tests | Failures observed | Wall runtime |
|---|---:|---:|---:|---:|
| `cmd.exe /c "gradlew.bat :shared:jvmTest"` | PASS | 324 | 0 | 24.71 s |
| `cmd.exe /c "gradlew.bat :desktopApp:test -PdesktopTarget=windows"` | PASS | 145 | 0 | 22.26 s |
| `cmd.exe /c "gradlew.bat :androidApp:testDebugUnitTest"` | PASS | 60 | 0 | 18.30 s |
| `node --test scripts/hub-schema-provision.test.mjs` | PASS | 12 | 0 | 0.259 s |
| `cargo test -p glean-hub-sync-engine` (from `glean-hub`) | PASS | 6 | 0 | 0.391 s |
| `cargo test -p glean-hub-sync --lib` (from `glean-hub`) | PASS | 28 | 0 | 30.389 s |
| `pnpm --dir glean-hub-app test:unit` (from `glean-hub`) | PASS | 50 | 0 | 0.643 s |
| **Total** | **PASS** | **625** | **0** | — |

The three Gradle suites were force-executed with `--rerun-tasks` to ensure fresh test execution and collect counts. The initial Android invocation reported `UP-TO-DATE`; its forced rerun then executed all 60 tests. Cargo sync-lib reports 30.02 s test-process duration; listed wall time includes command overhead.

## Coverage, performance, and build status

- **Coverage:** Not generated; no coverage target was requested or run.
- **Performance:** No benchmark or performance threshold run. Longest command was `cargo test -p glean-hub-sync --lib` at 30.389 s wall time.
- **Build/test status:** All seven requested test commands succeeded. No production/release build was separately run.

## Warnings and issues

- Gradle emitted non-fatal Kotlin compiler warnings (always-true/false conditions, expect/actual beta notices), Compose/Java deprecation warnings, and the Gradle 9 compatibility deprecation notice.
- `pnpm` output included a warning that several API keys were missing and sync would fail with 401; the unit suite nevertheless passed. The output alone does not establish whether this warning is expected for this test environment.
- **Critical test blockers:** None observed.
- These results cover only the named automated suites; they do not establish live multi-user behavior, device UI acceptance, Linux desktop behavior, or production readiness.

## Recommendations and next steps

1. Keep the existing compiler/deprecation warnings visible and triage them separately from this regression verification.
2. Run coverage or platform/runtime acceptance separately if required by release criteria; neither was part of these test targets.

## Unresolved questions

None.
