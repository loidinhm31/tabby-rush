# QA Test Report: Normal-Verification JVM Unit Tests

- **Date:** 2026-10-20
- **Runner:** SafePlatformTests
- **Environment:** Windows x64, Gradle wrapper 8.11.1
- **Scope:** `:shared:jvmTest` and `:androidApp:testDebugUnitTest`, normal dependency verification

## Test Results Overview

| Task | Result | Test outcomes | Duration |
|---|---|---|---|
| `:shared:jvmTest` | PASS (`BUILD SUCCESSFUL`) | Console-listed cases all reported `PASSED`; aggregate count was not printed in captured output | 16s Gradle time |
| `:androidApp:testDebugUnitTest` | BLOCKED (`BUILD FAILED` before test execution) | 0 Android unit tests completed | 2s Gradle time |

The shared task reported 16 actionable tasks (1 executed, 15 up-to-date). Android reported 19 actionable tasks (2 executed, 17 up-to-date). No connected/device tests were run.

## Exact Commands and Results

From repository root, without dependency-verification overrides:

1. `./gradlew.bat :shared:jvmTest` — exit 0; `BUILD SUCCESSFUL in 16s`.
2. `./gradlew.bat :androidApp:testDebugUnitTest` — exit 1; `BUILD FAILED in 2s`.

Android failed during dependency verification, before test execution:

- `:androidApp:checkDebugAarMetadata` failed verification for `firebase-common-22.0.1.aar` (`com.google.firebase:firebase-common:22.0.1`) from Google.
- `:shared:kspDebugKotlinAndroid` failed verification for these 30 Google Maven AARs:
  1. `firebase-common-22.0.1.aar` — `com.google.firebase:firebase-common:22.0.1`
  2. `customview-1.0.0.aar` — `androidx.customview:customview:1.0.0`
  3. `datastore-core-release.aar` — `androidx.datastore:datastore-core-android:1.1.7`
  4. `datastore-preferences-core-release.aar` — `androidx.datastore:datastore-preferences-core-android:1.1.7`
  5. `datastore-preferences-release.aar` — `androidx.datastore:datastore-preferences-android:1.1.7`
  6. `datastore-release.aar` — `androidx.datastore:datastore-android:1.1.7`
  7. `documentfile-1.0.0.aar` — `androidx.documentfile:documentfile:1.0.0`
  8. `firebase-components-19.0.0.aar` — `com.google.firebase:firebase-components:19.0.0`
  9. `firebase-datatransport-18.2.0.aar` — `com.google.firebase:firebase-datatransport:18.2.0`
  10. `firebase-encoders-json-18.0.0.aar` — `com.google.firebase:firebase-encoders-json:18.0.0`
  11. `firebase-iid-interop-17.1.0.aar` — `com.google.firebase:firebase-iid-interop:17.1.0`
  12. `firebase-installations-19.0.1.aar` — `com.google.firebase:firebase-installations:19.0.1`
  13. `firebase-installations-interop-17.1.1.aar` — `com.google.firebase:firebase-installations-interop:17.1.1`
  14. `firebase-measurement-connector-19.0.0.aar` — `com.google.firebase:firebase-measurement-connector:19.0.0`
  15. `firebase-messaging-25.0.1.aar` — `com.google.firebase:firebase-messaging:25.0.1`
  16. `fragment-1.1.0.aar` — `androidx.fragment:fragment:1.1.0`
  17. `legacy-support-core-utils-1.0.0.aar` — `androidx.legacy:legacy-support-core-utils:1.0.0`
  18. `lifecycle-viewmodel-2.8.7.aar` — `androidx.lifecycle:lifecycle-viewmodel:2.8.7`
  19. `loader-1.0.0.aar` — `androidx.loader:loader:1.0.0`
  20. `localbroadcastmanager-1.0.0.aar` — `androidx.localbroadcastmanager:localbroadcastmanager:1.0.0`
  21. `play-services-base-18.1.0.aar` — `com.google.android.gms:play-services-base:18.1.0`
  22. `play-services-basement-18.3.0.aar` — `com.google.android.gms:play-services-basement:18.3.0`
  23. `play-services-cloud-messaging-17.2.0.aar` — `com.google.android.gms:play-services-cloud-messaging:17.2.0`
  24. `play-services-stats-17.0.2.aar` — `com.google.android.gms:play-services-stats:17.0.2`
  25. `play-services-tasks-18.1.0.aar` — `com.google.android.gms:play-services-tasks:18.1.0`
  26. `print-1.0.0.aar` — `androidx.print:print:1.0.0`
  27. `transport-api-3.1.0.aar` — `com.google.android.datatransport:transport-api:3.1.0`
  28. `transport-backend-cct-3.1.9.aar` — `com.google.android.datatransport:transport-backend-cct:3.1.9`
  29. `transport-runtime-3.1.9.aar` — `com.google.android.datatransport:transport-runtime:3.1.9`
  30. `viewpager-1.0.0.aar` — `androidx.viewpager:viewpager:1.0.0`

Gradle referenced the verification report at `build/reports/dependency-verification/at-1792483324434/dependency-verification-report.html`.

## Coverage, Performance, and Build Notes

- Coverage was not measured; the requested Gradle test tasks do not emit coverage metrics in this run.
- Captured shared-task output listed passing cases but did not include aggregate passed/failed/skipped counts. No failure was reported by that task.
- Gradle warned that deprecated features are in use and incompatible with Gradle 9.0; it suggested `--warning-mode all` for details (not run).
- The Android failure is a dependency-verification blocker, not a reported unit-test failure.

## Critical Issue and Recommendation

Android unit tests remain unexecuted until the 30 listed AAR verification failures are resolved through the repository's approved dependency-provenance process. Verification was not disabled and verification metadata was not changed.

## Unresolved Questions

None.
