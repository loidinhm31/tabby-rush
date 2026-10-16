# Android Sticky Task Surfaces Verification & Acceptance Summary

**Document Version**: 1.0.0  
**Date**: 2026-10-17  
**Phase**: Phase 07 — Integrated Android/Windows Acceptance & Shared Documentation  
**Plan**: `plans/261016-1409-sticky-task-surfaces/plan.md`  
**Host Environment**: Windows 11 Build 26200 (AMD64), OpenJDK 21.0.9 LTS, Android SDK Platform 35, Build-Tools 35.0.0, AGP 8.7.3  
**Status**: Phase 07 documentation and automated verification are recorded; native Android launcher/shade/device acceptance remains conditionally held at Gate G2. Credited 6h / 12h; project progress 118h / 132h (89.4%). Reported automated results do not constitute hardware acceptance.

---

## 1. Executive Summary

Phase 07 synthesizes the native Android sticky task surfaces delivered in Phase 02 (Glance Task Widget), Phase 03 (Quiet Task Surface Deadline Notification), and Phase 05 (Android Lifecycle, Fencing, and Security Integration).

### Delivered Capabilities
1. **Glance Task Widget (`DoggoTaskWidget`)**: Responsive 4x4, 3x3, 3x2, and 2x2 home screen widget rendering unfiltered Incomplete tasks and Completed tasks in stable `createdAt DESC, id DESC` order. Provides atomic local task completion toggle, quick add dialog launch, and deep-link app opening.
2. **Quiet Task Surface Notification (`TaskSurfaceNotificationCoordinator`)**: Low-importance, no-sound, no-vibration notification channel (`doggo_task_surfaces`) displaying the single next overdue or upcoming deadline. Operates with identity-only `PendingIntent` URIs, generic public lock-screen version (`VISIBILITY_PUBLIC`), and durable API 34+ swipe dismissal suppression.
3. **Application Lifecycle & Token Fencing (`DoggoApplication`, `AndroidTaskSurfaceAccess`)**: Bounded 4-second cold-start wait, non-cancellable admission gating, serialized publication mutex, and multi-account generation fencing rejecting stale actions across account switches.
4. **Account Leave/Wipe Cleanup Path (`CompositeLeaveSchedulerCancelGate`)**: Cascading cancellation of surface observer jobs, WorkManager wake, active notifications, and Glance widget instances across all installed widget IDs. The separate runtime-replacement drain-timeout failure is documented below.

---

## 2. Automated Test Execution Evidence

### 2.1 Android Unit Test Suite (`:androidApp:testDebugUnitTest`)
Executed via `./gradlew.bat :androidApp:testDebugUnitTest --rerun-tasks --dependency-verification=off`:
- **Result**: **34 / 34 PASSED (100% Green)** across 6 test classes:
  - `AndroidNotificationAdapterTest`: 6 / 6 passed
  - `BuildConfigTest`: 1 / 1 passed
  - `CompositeLeaveSchedulerCancelGateTest`: 4 / 4 passed (Observer cancellation and quiescence ordering)
  - `ReminderPermissionControllerTest`: 2 / 2 passed
  - `TaskSurfaceNotificationTest`: 10 / 10 passed (Oldest-overdue selection, ties, conflict handling, dismissal suppression)
  - `TaskWidgetModelTest`: 11 / 11 passed (Partitioning, ordering, empty states, zero title leakage in locked states)

### 2.2 Security Auditor Suite (`scripts/release-security-check.test.mjs`)
Executed via `node --test scripts/release-security-check.test.mjs`:
- **Result**: **12 / 12 PASSED (100% Green)**
- Verifies exact canonical identity for `MainActivity` and `DoggoTaskWidgetReceiver`, while rejecting foreign lookalikes, exported services, content providers, unauthorized intent-filter categories/data, and unverified exported receivers.

### 2.3 Diagnostic Source Security Audit (`scripts/release-security-check.mjs --diagnose-source`)
Executed via `node scripts/release-security-check.mjs --diagnose-source`:
- **Result**: **CLEAN (Source meets static security rules)**
- Verified `usesCleartextTraffic="false"`, `allowBackup="false"`, security XML configs (`backup_rules.xml`, `data_extraction_rules.xml`), HTTPS Hub origin, and absence of hardcoded source secrets.
- *Limitation*: Diagnostic source audit verifies source files; merged release APK audit requires signing keystore custody.

### 2.4 Android Debug Compilation & Packaging (`:androidApp:assembleDebug`)
Executed via `./gradlew.bat :androidApp:assembleDebug --dependency-verification=off`:
- **Result**: **BUILD SUCCESSFUL in 30s**
- Packaging verified with bundled SQLite native driver (`libsqliteJni.so`) and AndroidX graphics libraries.

---

## 3. Visual Design Baselines

The following files are visual design baselines/design mockups, not screenshots of a running Android device or OS:

| Artifact | File Path | Dimensions | Description |
|---|---|---|---|
| **Android Glance Widget** | `docs/evidence/sticky-surfaces-android-widget.png` | 1080 × 2160 | Design mockup of a responsive launcher widget with task sections and actions. |
| **Android Quiet Notification** | `docs/evidence/sticky-surfaces-android-notification.png` | 1080 × 2160 | Design mockup of a quiet deadline notification and generic keyguard public view. |

*Note: Native launcher, notification shade, lock-screen, and connected-device evidence remains pending Gate G2.*

---

## 4. Acceptance Criteria Status & Traceability

| ID | Scenario | Verification Method | Status | Notes / Limitations |
|---|---|---|---|---|
| **AC 1** | Real launcher widget scrolling, ordering, toggle | Unit tests (`TaskWidgetModelTest`) | Automated subset recorded; Gate G2 pending | Ordering/model behavior is covered by tests; no native launcher interaction acceptance is claimed. |
| **AC 2** | Offline widget Add & sync reconnection | Contract/unit fixtures and `TaskSurfaceCreateScreen` | Automated subset recorded; Gate G2 pending | Fixture evidence does not establish live launcher or reconnect behavior. |
| **AC 3** | Multi-surface convergence across background sync | Process Flow observers and `DoggoSyncWorker` | Automated subset recorded; G6 pending | No live multi-device convergence acceptance is claimed. |
| **AC 4** | Oldest-overdue notification selection & ties | Unit tests (`TaskSurfaceNotificationTest`) | Automated subset recorded; Gate G2 pending | Selection behavior is covered by 10 unit tests; native notification presentation remains unaccepted. |
| **AC 5** | Duplicate action delivery & stale generation rejection | Token fencing in `AndroidTaskSurfaceAccess` | Automated/source checks recorded; Gate G2 pending | No physical pending-intent delivery acceptance is claimed. |
| **AC 6** | Permission denial, channel off, API 34+ swipe | Unit tests and preference codecs | Automated subset recorded; Gate G2 pending | Native permission and notification-shade behavior remains unaccepted. |
| **AC 7** | Process kill, restart, logout/wipe cleanup | `CompositeLeaveSchedulerCancelGateTest` | Automated subset recorded; Gate G2 pending | Android drain-timeout and Glance cleanup findings remain open; no native wipe acceptance is claimed. |
| **AC 12** | Full regression and security invariants | Reported 364 Gradle tests, 12 Node tests, source audit | Automated results recorded; release holds remain | Commands used dependency verification off; 149 unmatched checksums and separate Glance provenance hold remain. |

---

## 5. Disclosed Risks, Safety Findings & Execution Holds

1. **Root Dependency Provenance Hold**:
   - Running `./gradlew.bat :shared:jvmTest --dependency-verification=strict` fails with 149 unmatched classpath checksums in `gradle/verification-metadata.xml`.
   - Reported tests/builds used `--dependency-verification=off`; both the 149-checksum baseline and the separate Glance artifact provenance hold remain unresolved.
2. **Android Runtime Closure Drain Timeout (Cycle 2 Finding 1)**:
   - File: `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:191-198`
   - **Description**: `closeRuntimeInternal()` catches and logs the drain-timeout exception, then returns before observer-job cancellation and Room database closure. `closeActiveRuntime()` nevertheless clears `activeRuntime`, so coroutine jobs remain and Room is not closed.
   - **Status**: Open safety finding; this is an incomplete teardown, not a successful fail-closed shutdown. Native hardware acceptance remains held at Gate G2.
3. **Glance Widget Cleanup Error Swallowing (Cycle 2 Finding 2)**:
   - File: `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt:222-226, 240-244`
   - Description: RemoteViews blanking and session closure catch `Throwable` non-fatally during leave protocol.
   - Status: Tracked open safety risk; launcher cache behavior requires physical device testing.
4. **Physical Device & Connected Tests (Gate G2 Hold)**:
   - Workstation currently has no attached ADB emulator or physical device (`adb devices` empty).
   - `task-surface-account-device-test.kt` and `task-surface-navigation-device-test.kt` remain compiled but unexecuted on live hardware.
5. **Remaining Shared Release & Platform Holds**:
   - Production release gates G3, G6, and G7 remain open.
   - Linux session/tray acceptance requires a real Linux target and remains pending Phase 08.
