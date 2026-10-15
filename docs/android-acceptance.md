# Android Composition & Runtime Acceptance Specification

**Document Version**: 1.1.0
**Phase**: 07 — Android composition and runtime integration
**Status**: Implementation Complete (Host SDK & Live Device Execution Pending Operator Tooling)
**Related Documents**: [Project Overview PDR](./project-overview-pdr.md) · [System Architecture](./system-architecture.md) · [Authentication & Credentials](./authentication-and-credentials.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [Design Guidelines](./design-guidelines.md)

---

## 1. Overview & Architectural Boundaries

Phase 07 delivers the native Android application module (`:androidApp`), integrating the frozen domain contracts (Phase 02), backend strict protocol client (Phase 03), Keystore credential vault and auth session (Phase 04), Room database and outbox sync coordinator (Phase 05), and accessible Compose UI (Phase 06).

### 1.1 Process & State Invariants
1. **Single Process Container**: `DoggoApplication` manages singletons: `HubConfig` (with `BuildConfig` parameterization for debug loopback vs production HTTPS), `AndroidKeystoreVault`, `AndroidAccountFiles`, `HubAuthSession`, and `AndroidSyncScheduler`.
2. **Account Runtime Isolation**: `RoomAccountStore` and `AccountSyncCoordinator` are bound to a specific `AccountKey` and generation counter. Both foreground UI and background workers obtain the exact same runtime from `DoggoApplication`, sharing the coordinator mutex and single-flight token refresh.
3. **Cold-Start Active Identity Persistence**: Active account key and identity are persisted in app-private storage (`active_account.dat`) upon successful login/register. On cold start, `DoggoApplication` verifies that no `wipe.marker` exists before restoring the session from the vault.
4. **No Credential Leakage**: Passwords exist only transiently in memory during user input and are never stored in `ViewModel` fields, `SavedStateHandle`, Android Intents, or log outputs.
5. **Fail-Closed Wipe Recovery**: Startup recovery (`completePendingWipesOnStartup`) inspects `accounts/*/wipe.marker` files before opening any account resources. If a wipe marker is present, all scheduled WorkManager tasks for that account tag are cancelled, database files are deleted, credentials cleared synchronously, and marker removed fail-closed.

---

## 2. Security & Manifest Configuration

| Item | Requirement | Verification Method | Status |
|---|---|---|---|
| **Exported Components** | Only `MainActivity` is exported (`android:exported="true"`). Zero unauthenticated services, receivers, or providers. | Manifest source audit | Verified (Source) |
| **Backup Exclusion** | `android:allowBackup="false"`, `android:fullBackupContent="@xml/backup_rules"`. `backup_rules.xml` and `data_extraction_rules.xml` exclude databases, shared prefs, and account files. | XML source audit | Verified (Source) |
| **Cleartext Restrictions** | `android:usesCleartextTraffic="false"` in base manifest. Debug manifest overrides with `network_security_config.xml` strictly allowing `127.0.0.1`, `localhost`, and `10.0.2.2`. Global cleartext and custom CAs are rejected. | Source audit (Merged manifest inspection pending SDK build) | Documented / Pending SDK |
| **Release Signing** | Release task fails closed without `DOGGO_KEYSTORE`, `DOGGO_KEY_ALIAS`, `DOGGO_STORE_PASSWORD`, and `DOGGO_KEY_PASSWORD`. Debug builds require no secrets. | Gradle task graph listener | Verified (Source) |

---

## 3. Background Sync & Scheduling

### 3.1 WorkManager Invariants
1. **Account-Tag Cancellation**: All sync requests are tagged with `doggo_sync_account_<accountKey>`, allowing `cancel(accountKey)` and `cancelAndAwait(accountKey)` to cancel and await all work for an account regardless of generation transitions.
2. **One-Shot Sync**: Unique work name formatted as `doggo_sync_oneshot_<accountKey>_<generation>` with `NetworkType.CONNECTED` constraint and `ExistingWorkPolicy.KEEP`.
3. **Periodic Safety Net**: Unique 15-minute periodic work `doggo_sync_periodic_<accountKey>` with `ExistingPeriodicWorkPolicy.UPDATE`.
4. **Worker Generation Fencing**: `DoggoSyncWorker` validates input `generation` against `AndroidAccountFiles.readGeneration(accountKey)` and asserts active auth session matches worker account. Stale workers drop execution immediately.
5. **Cancellation Gate**: `AndroidSyncScheduler.cancelAndAwait` cancels tagged work and awaits completion of active coordinator rounds under `coordinatorMutex.withLock` to guarantee quiescence before teardown.

---

## 4. Test Verification Matrix

### 4.1 Automated Suites
- **Common / JVM Regression**: `./gradlew --dependency-verification=off :shared:jvmTest` (96/96 unit and SQLite integration tests pass).
- **Script Syntax Verification**: `bash -n scripts/android-smoke.sh` (passes, `--help` exits 0), `node --check scripts/debug-fault-proxy.mjs` (passes).
- **Instrumented Device Suites (Source Ready — Unexecuted / Pending Host SDK)**:
  - `todo-device-test.kt`: Statically verifies ViewModel initial state, filter switching, editor opening/cancelling, and direct AccountRuntime database store persistence. (Compose UI semantic nodes, toggle, and deletion tests deferred to Phase 08 device testbed).
  - `background-isolation-test.kt`: Statically verifies WorkManager background worker failure on pending wipe and generation mismatch, startup wipe marker cleanup, and scheduler cancel-and-await quiescence.


### 4.2 Phase 02 — Native Alarm & Notification Proof Status

| Gate | Requirement | Verification Method | Status |
|---|---|---|---|
| **Manifest Declarations** | `SCHEDULE_EXACT_ALARM`, `POST_NOTIFICATIONS`, and non-exported `DoggoAlarmReceiver` (`android:exported="false"`) | Static manifest inspection | PASS (Verified in `androidApp/src/main/AndroidManifest.xml`) |
| **Alarm & Notification Adapter** | `AndroidNotificationAdapter` and `AndroidReminderCapabilitiesChecker` separating notification permission from exact-alarm access | Host unit test `AndroidNotificationAdapterTest` (requires SDK) | SOURCE INSPECTED / UNEXECUTED (Adapter structure and constants defined; scheduling, capability checks, and receiver delivery unexecuted pending Android SDK in Phase 08) |
| **Live Device & SDK Execution** | Build and execute AlarmManager intent scheduling and offline receipt on physical/emulated device | `./gradlew :androidApp:assembleDebug` | BLOCKED (Workstation lacks Android SDK in `/home/loidinh/Android/Sdk` and connected device/emulator; G2 gate prerequisite remaining) |
### 4.3 Fault-Injection & Network Tests
- **Proxy**: `scripts/debug-fault-proxy.mjs` binds strictly to `127.0.0.1:3000` forwarding to upstream `3001` with allowlisted routes (`/api/v1/sync/doggo/strong/*`, `/api/v1/auth/*`, `/api/v1/apps/access-requests*`).
- **Scenarios**:
  - `drop-push-response`: Upstream commits push mutation (2xx verified), proxy severs client connection; client detects uncertainty and recovers via nonce replay on subsequent sync.
  - `drop-refresh-response`: Upstream executes refresh (2xx verified), proxy drops client socket; client marks `REAUTH_REQUIRED` without infinite refresh loop.

---

## 5. UI Accessibility & Acceptance Checklist

| Check | Specification | Evidence / Tracking |
|---|---|---|
| **Touch Targets** | All interactive controls (checkboxes, buttons, filter chips) meet >= 48dp touch target boundaries. | Verified in `TodoItem`, `TodoFilterChips` |
| **Contrast** | Light theme tokens exceed WCAG 2.1 AA (4.5:1 text, 3.0:1 controls). | Verified in Phase 06 design spec |
| **Typography** | Delegates to platform `FontFamily.SansSerif` for system font scaling up to 200%. | Verified in `DoggoTypography` |
| **Screen States** | 7 core UI states documented: Empty, Populated, Completed, Editor, Error, Conflict, Access-Pending. | High-fidelity design baseline renders in `docs/evidence/`; live device framebuffer capture scheduled for Phase 08. |
