# TabbyRush Security Model & Operational Disaster Recovery

**Document Version**: 1.4.0
**Last Updated**: 2026-10-20
**Status**: Standard sync is current. A standalone Windows native-runtime smoke passed against disposable loopback services; this is not interactive UI or connected-Android acceptance. Live production migration and release remain unauthorized.
**Related Documents**: [Standard Sync Design](./standard-sync-cutover-design.md) · [Deployment Guide](./deployment-guide.md) · [Release Acceptance](./release-acceptance.md) · [Local Storage & Sync](./local-storage-and-sync.md) · [System Architecture](./system-architecture.md) · [Windows Desktop Guide](./windows-desktop-guide.md) · [Windows Acceptance](./windows-acceptance.md)

## 1. Security Architecture & Threat Model

### 1.1 Credential & Key Lifecycle
- **Zero Plaintext Credentials in Storage**: Passwords exist in memory only during user submission and are never stored in databases, shared preferences, view models, or process bundles.
- **OS-Protected Platform Credential Custody**:
  - Android: `AndroidKeystoreVault` uses Android KeyStore with AES-256-GCM to encrypt access and refresh tokens.
  - Linux: Freedesktop Secret Service API via D-Bus (Phase 09).
  - Windows: `WindowsDpapiVault` uses JNA `CryptProtectData` / `CryptUnprotectData` in current-user scope; it stores the encrypted account-key/token pair in `credentials.dpapi` using temporary-file and atomic replacement. Machine-wide scope and plaintext fallback are prohibited.
  - **Windows ACL limitation**: `applyRestrictiveUserAcl()` attempts an owner ACL before the atomic move, but returns when the ACL view or owner is absent and suppresses exceptions. Effective owner-only ACL enforcement is therefore best-effort, not guaranteed. The vault also treats a missing/zero-length file as no saved credentials and does not reject an empty serialized account key; these edge cases remain security-hardening concerns.
- **API Key Classification**: The `TABBYRUSH_API_KEY` is an extractable client application identifier, NOT an administrative credential. It identifies the client as authorized for the `tabbyrush` app schema. Human user authentication relies exclusively on short-lived JWT access tokens and rotatable refresh tokens.
- **Single-Flight Token Refresh**: Mutex-coordinated refresh ensures only one in-flight network refresh executes at a time. All concurrent requests wait on the resulting token or fail closed.

### 1.2 Network & Communication Security
- **Strict HTTPS Enforcement**: Release builds reject cleartext HTTP traffic unconditionally (`android:usesCleartextTraffic="false"`).
- **No-Redirect Policy**: The Ktor HTTP engine and tooling scripts reject HTTP 3xx automatic redirects to prevent TLS downgrade and credential forward attacks.
- **Fail-Closed Wipe Protocol**: Account logout or server reset triggers synchronous cancellation of all scheduled sync workers, deletion of Room SQLite files, clearing of vault credentials, and persistence of fail-closed wipe markers.

---
## 2. Current Standard Sync Security Boundaries

TabbyRush uses ordinary Hub registration and the standard `/api/v1/sync/{app_id}/{push,pull,delta}` routes. Requests use a Bearer token, `X-API-Key`, and `X-App-Id`; the current client does not use strict-v1/v2/v3 route trees, epochs, or strict operation receipts.

- Treat `schemaVersion` as informational Hub concurrency metadata, not a native capability gate.
- The client preserves raw unknown JSON and merges only fields named by a valid explicit intent mask.
- Ambiguous legacy intent is not safe to replay automatically. Require user review; do not fall back to reapplying every known field.
- A full scan starts from a null checkpoint every round/reconnect. Stale scan staging is discarded after restart; an incomplete scan is not deletion proof.
- Only an explicit accepted `(tableName,rowId,version)` tuple confirms a submitted version. A matching pull is convergence, not operation receipt evidence.
- Body size is bounded by 256 KiB serialized bytes before freeze. A known individually oversized record is marked actionable/conflicted without sending that record; it is not an uncertain network result.
- Standard sync does not provide strict exact-revision CAS, server-enforced hierarchy, permanent tombstones, or transactional group writes.

Schema editing uses `expectedSchemaVersion`. An effective no-op does not increment the counter; a stale changed edit must not overwrite current state. Admin UI stale-save behavior preserves the draft and exposes current schema read-only. Hub schema updates do not generate native UI, Kotlin fields, or Room migrations.

## 3. Standard Conversion Operator Recovery

This section describes source-built Glean Hub conversion tooling, not a command in the TabbyRush binary. A disposable local MongoDB run is reported 7/7 passed, including concurrency, deadline, and recovery. No production database migration is authorized.

The operator obtains an exclusive durable claim; it has no automatic expiry or takeover. A failed mutating run of any kind requires explicit recovery before another run. Provide `maintenance.executionRecovery` with the exact `expectedExecutionId`, `executorStopped: true` and evidence in `executorStopEvidence`, `mongoOperationsDrained: true` and evidence in `mongoDrainEvidence`, and `authorizedActions` containing `"recoverExecution"`. Evidence must establish both stopped executor and drained initiated Mongo operations. Never clear or steal a claim by timeout or manual database edit.

Renew and verify the authorized maintenance window immediately before every collection rename and registry compare-and-swap. Keep all Hub nodes and background writers/jobs quiesced or isolated as approved. The operator does not automatically restart services, release external isolation, or perform post-publication rollback. Live cutover requires separate explicit approval naming the target, backup/restore scope, isolation plan, and window. Do not treat test results or a recovery authorization as live-cutover approval.

Before publication, any rollback/restoration still requires separately authorized recovery and verified isolation/backup. After publication, do not restore an old snapshot over new standard writes; recovery is forward repair unless a separately approved reverse-data migration is prepared.

## 4. Archived Strict-v1/v2 Recovery Narrative

> **Historical archive (2026-10-14 through 2026-10-16); NOT current guidance.** Strict fresh-epoch restore, strict-v2 upgrade, epoch mismatch recovery, and the commands formerly documented here describe a legacy strict implementation. The current binary does not use that protocol. Scripts named by the old procedures, including `hub-strict-recovery.js` and `hub-strict-upgrade.mjs`, have been removed; do not execute copied examples or use this section for a live incident.

Historical strict guidance distinguished fresh-epoch disaster recovery from in-place feature upgrades, prohibited clearing active fences blindly, and described state-specific rollback/forward-repair behavior. Those are records of the earlier strict operational model only and are not a substitute for the current durable execution-claim recovery requirements in Section 3. See the runbooks' archive notices for additional dated historical context.

---



## 5. Sticky Task Surface Content, Wipe & Receiver Security

### 5.1 OS-Retained Content and Wipe Behavior
- Android launcher widgets and notifications are rendered/stored by OS services and may outlive TabbyRush's process. The widget can retain task text in launcher-managed content; notification details are private, with a generic public title (“Next task deadline”) rather than task content.
- Pending account wipes and leave cleanup request cancellation of reminder alarms, task-surface boundary work, active task-surface notifications, installed widget content (including a signed-out/blank update), Glance sessions, and account-scoped surface preferences before deleting the account database and credentials. The task-surface preference file is cleared; the wipe marker is removed only after account database/artifact deletion and credential clearing succeed.
- **Cleanup is not an OS erasure guarantee.** `TaskWidgetUpdater` catches and logs failures while pushing blank `RemoteViews`, publishing the Glance signed-out state, or closing Glance sessions. Notification-manager operations may also fail. Thus stale launcher or notification content can remain if the OS does not accept cleanup; the Cycle 2 review also found that Android `closeRuntimeInternal()` proceeds after a delivery-gate drain timeout. These unresolved findings are not waived.

### 5.2 Exported Component Audit Invariants
- The release manifest may export only `.MainActivity` and `.TabbyRushTaskWidgetReceiver`. The widget receiver is permitted only with the exact `android.appwidget.action.APPWIDGET_UPDATE` action and exact app-widget provider metadata (`@xml/task_widget_info`); no additional action, category, or data filter is allowed.
- `TabbyRushAlarmReceiver`, `ReminderRestoreReceiver`, `TaskSurfaceActionReceiver`, `TaskSurfaceDismissReceiver`, and `TaskSurfaceTimeReceiver` remain unexported. No service or content provider may be exported.
- `scripts/release-security-check.mjs` enforces the exported-component allowlist and exact widget filter/metadata invariants for source manifests and release APKs. The Node security-auditor suite (`release-security-check.test.mjs`) is verified with 12/12 passing checks, and static source diagnostics report CLEAN.

### 5.3 Release and Acceptance Boundaries

- **Native runtime evidence**: A standalone Windows runtime smoke exercised DPAPI, real HTTP auth, Room persistence/restart, two independent account stores with actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and nullable `customNotes` preservation through a native edit, parent/child sync, and group deletion against disposable loopback services. This does not establish interactive UI or connected-Android acceptance.
- **Native acceptance**: UI provider approval timed out twice; connected-device acceptance was not run. Offline Gradle/package and standalone runtime evidence do not establish full platform acceptance or release readiness.
- **Dependency and production boundary**: Dependency provenance and release holds remain tracked. No live production migration is authorized.
- **Evidence boundary**: Historical phase counts and strict gate tables do not establish current native acceptance. See [Release Acceptance](./release-acceptance.md) for the current verdict.

---

## 6. Server Admin Announcements Security, Install Fencing & Lifecycle

### 6.1 Install Proof Custody and Device Identity Fencing
- **Proof Generation & Storage**: On first launch, each installation generates a cryptographically random 128-bit UUID (`installId`) and a 32-byte random secret (`X-Tabby-Install-Proof`), stored encrypted in private no-backup app storage via Android Keystore.
- **Server-Side Proof Verification**: The Hub server persists only the SHA-256 hash of the proof (`secretHash`) in `notification_install_bindings` and performs constant-time comparisons. Guessed or mismatched proofs return HTTP 403 `installProofRejected`.
- **Generation CAS Fencing**: Device binding operations (`bind`, `unbind`) require a monotonically increasing numeric generation string (`operationGeneration`). The Hub evaluates generations numerically (e.g. "10" > "9", not lexicographically) using atomic Compare-And-Swap. Stale or delayed operations return HTTP 409 `staleInstallOperation`.
- **Account Switch & Logout Fencing**: Local logout invalidates the in-memory runtime and generation immediately without waiting for network unbind completion. When a new account logs in, a strictly newer generation is reserved. Any delayed in-flight unbind from the previous account cannot delete the new binding.

### 6.2 Late Generic Push & Privacy Boundaries
- **Zero Private Content in Transit**: FCM HTTP v1 payloads contain exclusively fixed generic text ("You have a new announcement") and opaque metadata (`type: "admin_announcement"`, `appId: "tabby-rush"`, `messageId: "<UUID>"`). No title, body, user ID, or sensitive tokens are included.
- **Late Generic Alert Handling**: If an FCM alert is delivered after account logout or switch, tapping the notification opens the app and reauthorizes the message ID against the currently active account session. If the current user lacks membership or grant, the app displays a safe empty/unauthorized notice without ever displaying old cached content.
- **FCM Merged Manifest Policy**: `FirebaseMessagingService` is private (`android:exported="false"`) with action `com.google.firebase.MESSAGING_EVENT`. `FirebaseInitProvider` is private (`exported="false"`) with exact authority `dev.tabbyrush.android.firebaseinitprovider`. Exported `FirebaseInstanceIdReceiver` requires exact action `com.google.android.c2dm.intent.RECEIVE` and package category `dev.tabbyrush.android`. `AppMeasurementReceiver` is completely forbidden (no analytics).

### 6.3 Zero Offline Content & In-Memory Isolation
- **Client-Side In-Memory Cache**: On the client, received announcements and read statuses exist exclusively in process memory (sliding window of max 200 items, dedup capacity 2048), while server records in MongoDB are durably retained for 90 days. No Room database tables, offline outboxes, or durable disk queues exist for announcements.
- **Process Teardown & Offline Protection**: Client process termination purges in-memory announcement state. When offline, detail views are unavailable and cannot be opened from stale memory without fresh server authentication. Offline read mutations are not queued.

### 6.4 Nondestructive Rollback & 90-Day Retention
- **Intake Disable Safety**: Setting `ANNOUNCEMENTS_ENABLED=false` rejects new intake requests with HTTP 503 `announcementsDisabled` but preserves canonical read routes (`/inbox`, `/unread`) and idempotent replay.
- **Data Preservation**: Announcement collections (`admin_announcements`, `admin_announcement_inbox`, `notification_install_bindings`) are never dropped during operational rollback. Accepted records expire automatically after 90 days via MongoDB TTL indexes.
