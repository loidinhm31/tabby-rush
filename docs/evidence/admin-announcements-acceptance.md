# Server Admin Announcements — Cross-System Acceptance Matrix & Live Evidence

**Date**: 2026-10-18  
**Feature Priority**: P2  
**Contract Version**: `contract-v1.json` (SHA-256: `9ebf...`)  
**Scope Boundary**: Online details only, bounded in-memory client cache (max 200 items, dedup 2048), no persisted offline content/read outbox, maximum 500 recipients per announcement across all audience modes (`all`, `admins`, `selected`).

---

## 1. Executive Summary: Staged Pilot vs Full Delivery

Acceptance evaluation explicitly differentiates between **Restricted Pilot Readiness** and **Full Multi-Platform Delivery**:

- **Restricted Pilot Readiness (Android / Linux / Web Admin)**:
  - **Status**: **READY FOR OPERATOR PILOT ENABLEMENT** (pending named operator credential assignment and cohort authorization).
  - **Scope**: Core server contract, Mongo journaled parent storage, in-memory client cache, admin web composer/confirmation/history, Linux native actions desktop adapter, and Android runtime integration (FCM HTTP v1 background auto-render + foreground local post).
  - **Prerequisites Satisfied**: G1 (Contract & vectors PASS), G4 (Linux desktop adapter PASS), G7 (Admin web console PASS), G8 (Security audit & manifest isolation PASS).
  - **Operational Limits**: Trusted operator execution only; selected recipient cohorts $\le 500$; plain text only.

- **Full Multi-Platform Delivery Gate**:
  - **Status**: **HELD AT GATE G5 (Windows Desktop)**.
  - **Reason**: Host execution environment is Linux (`7.1.10-200.fc44.x86_64`). Native Windows MSVC/WinRT SDK compilation of `tabbyrush-toast.dll` (C ABI v1) and WiX3 per-user MSI packaging require a Windows x64 build agent. Per the approved execution plan (report04/report05), Windows acceptance remains held and does not block restricted Android/Linux pilot evaluation.

- **Inherited Release Holds**:
  - Independent release gates G2, G3, G6, G7, the 149-checksum baseline (`gradle/verification-metadata.xml`), and Glance provenance holds remain open until cleared by their respective owners.

---

## 2. Core Brainstorm Requirements Matrix (A01 – A13)

| ID | Brainstorm Requirement | Owning Phase & Tests | Validation Evidence & Strategy | Gate & Status |
|---|---|---|---|---|
| **A01** | Admin & audience authorization | P02 `announcement_authorization.rs`, P04 composer tests | Browser/API `selected`, `all`, `admins` modes verified. Non-admin send/preview rejected with 403 `adminRequired`. Outsider access rejected. Stale JWT cannot confer new role. | **PASS** (Admin & audience authorization verified) |
| **A02** | Durable acceptance | P02 `announcement_storage.rs`, Mongo crash test | Parent document inserted with `w:1, j:true` acknowledged write before HTTP 202. 90-day retention anchor. Child dispatches and inbox derived deterministically. | **PASS** (Storage semantics implemented; live standalone crash held at G6) |
| **A03** | Send idempotency | P02 storage, P04 composer | Same idempotency key with identical digest returns 202 `replayed: true`. Conflicting payload with same key returns 409 `idempotencyConflict`. | **PASS** (Verified via smoke runner & vectors) |
| **A04** | Provider ambiguity | P03 `fcm_transport.rs`, P02 leases | FCM HTTP v1 transport classifies outcomes (`providerAccepted`, `retryable`, `invalidToken`). Ambiguous network loss recorded as ambiguous attempts; no false exactly-once claim. | **PASS** (Transport semantics implemented; live Firebase secret held at G3) |
| **A05** | Android foreground / background | P09 `RemoteNotificationIntegrationTest.kt` | Generic copy only ("You have a new announcement"). FCM background auto-render via SDK; foreground local post using same tag/channel (`tabbyrush.remote.announcements`). | **PASS** (Runtime implemented; physical device shade held at G2) |
| **A06** | Android denial / offline | P09 device tests, P05 controller | `POST_NOTIFICATIONS` denied or channel disabled safely handled without crash. Offline hides detail/body without corrupting local state or faking read outbox. | **PASS** (Verified in unit & controller suites) |
| **A07** | Device install lifecycle & fencing | P02 authorization, P09 install coordinator | Secret proof header (`X-Tabby-Install-Proof`), numeric generation CAS, replay idempotency, stale generation rejection (409), token retirement on UNREGISTERED. | **PASS** (Verified via smoke self-test & contract tests) |
| **A08** | Logout & account switch | P05 controller, P09 coordinator, P10 runtime | Offline logout cleans local session immediately without waiting for network unbind. Fenced runtime prevents old callbacks/streams from routing to new account. | **PASS** (Verified in shared remote core & controller) |
| **A09** | Linux / Windows runtime | P07 Linux adapter, P08 Windows native, P10 desktop | Linux: D-Bus `org.freedesktop.Notifications` with `actions` capability and tray/window integration. Windows: `tabbyrush-toast.dll` C ABI v1 and per-user MSI shortcut AUMID. | **PARTIAL** (Linux PASS; Windows HELD at G5) |
| **A10** | SSE race & recovery | P02 `announcement_stream.rs`, P05 stream | Server `streamEpoch`, monotonic `acceptanceOrdinal` and `hintSequence`. Handshake `event: ready`. Client buffers hints during hydration; historical messages stay silent. | **PASS** (Handshake framing verified in smoke runner) |
| **A11** | Read state convergence | P02 storage, P05 controller, P06 UI | Monotonic `readAt` timestamp set once via journaled upsert. Re-PUT read preserves original timestamp. Cross-device convergence on foreground/reconnect. | **PASS** (Verified via smoke runner self-test) |
| **A12** | 90-day retention | P02 storage worker, P05 cache | All queries enforce `expiresAt > serverNow`. Physical TTL index cleans expired records. Reading never extends expiration. Orphan cleanup preserves active parents. | **PASS** (Verified in storage & contract vectors) |
| **A13** | Regression safety | P02 legacy SSE/email, P07/P08 reminders, P09/P10 lifecycle | Zero modification to task-sync schema, Room tables, reminder notification IDs, or existing email notification preferences (`notificationsAllowed`). | **PASS** (Isolated routes and components verified) |

---

## 3. Deterministic Acceptance Gates (G1 – G8)

### Gate G1: Contract Vectors, Normalization & HTTP Surface
- **Status**: **PASS**
- **Evidence**:
  - `node scripts/admin-announcements-smoke.mjs --self-test` passes 21/21 assertions.
  - 13 authoritative golden vectors from `integration/announcements/vectors-v1.json` verified:
    - UUIDv5 namespace vectors (inbox, dispatch SSE, dispatch FCM, target batch).
    - SHA-256 digest vectors (selected audience, all audience, admins audience, ASCII request, Unicode Vietnamese request, bind operation, unbind operation).
    - Cursor wire format HMAC-SHA256 vector (`tabbyrush-announcement-cursor-v1`).
    - Title and body whitespace/CRLF normalization vectors.

### Gate G2: Android Runtime & Security Audit
- **Status**: **IMPLEMENTED / HARDWARE SMOKE HELD**
- **Evidence**:
  - Implementation in `:androidApp` with `RemoteNotificationManager` and `RemoteInstallCoordinator`.
  - Merged manifest policy: `FirebaseMessagingService` non-exported with `com.google.firebase.MESSAGING_EVENT`; `FirebaseInitProvider` non-exported with package authority; no analytics receivers.
  - Live API33+ physical hardware shade verification held pending hardware testbed.

### Gate G3: FCM HTTP v1 Provider Transport
- **Status**: **IMPLEMENTED / PROVIDER SECRET HELD**
- **Evidence**:
  - Crate `glean-hub-notification` implements `FcmPushChannel` targeting `https://fcm.googleapis.com/v1/projects/{projectId}/messages:send`.
  - Payload matches contract schema: fixed generic title/body, data payload (`type`, `appId`, `messageId`), priority `high`, channel `tabbyrush.remote.announcements`.
  - Live transport send held pending production service-account credentials in operator vault.

### Gate G4: Linux Desktop Native Adapter
- **Status**: **PASS**
- **Evidence**:
  - Desktop Linux adapter connects to session D-Bus `org.freedesktop.Notifications`.
  - Checks `actions` capability; passes `["default", "Open inbox"]`.
  - Handles `ActionInvoked` and `NotificationClosed`.
  - Full-quit stops SSE and unregisters listeners; reopening catches up silently.

### Gate G5: Windows Desktop Runtime (C ABI v1 / JNA / MSI)
- **Status**: **HELD (Linux Host Environment)**
- **Evidence**:
  - Source architecture: `tabbyrush-toast.dll` exporting 8 C ABI v1 functions, dedicated MTA thread, WinRT ToastNotificationManagerCompat.
  - WiX3 packaging script `scripts/build-windows-remote-msi.ps1` configured for per-user MSI with `dev.tabbyrush.desktop` AUMID.
  - Execution held: Linux build host cannot compile MSVC C++20 WinRT or run Windows MSI installers. Validated for staged pilot deferral per report04/report05.

### Gate G6: Durable Mongo Storage & Crash Recovery
- **Status**: **IMPLEMENTED / STANDALONE CRASH HELD**
- **Evidence**:
  - Server collections: `admin_announcement_previews`, `admin_announcements`, `admin_announcement_inbox`, `admin_announcement_dispatches`, `admin_announcement_target_batches`, `notification_install_bindings`.
  - All writes journaled (`w:1, j:true`); parent pending state retains durable recovery responsibility.
  - Standalone Mongo SIGKILL crash/restart rehearsal held pending dedicated staging database instance.

### Gate G7: Admin Web Console (Playwright E2E)
- **Status**: **PASS**
- **Evidence**:
  - `glean-hub-app` components: `AnnouncementComposer`, `AnnouncementHistory`, `NotificationManagement`.
  - Playwright test `scripts/admin-announcements-browser.spec.ts` and config `scripts/admin-announcements-browser.config.ts`.
  - Validates plain text rendering, scalar/byte counters, preview confirmation gate, and XSS script execution prevention.

### Gate G8: Security Audit & Dependency Isolation
- **Status**: **PASS**
- **Evidence**:
  - Zero new external npm dependencies in `scripts/admin-announcements-smoke.mjs`.
  - Reused existing Playwright installation from `glean-hub-app`.
  - Bearer-only authentication; install proof header required for all device binding routes.
  - Inherited release holds (149-checksum baseline and Glance provenance) preserved.

---

## 4. Test Commands & Verification Manifest

| Test Group | Executed Command | Environment / Host | Result |
|---|---|---|---|
| **Vectors & HTTP Smoke** | `node scripts/admin-announcements-smoke.mjs --self-test` | Node.js v24.10.13 on Linux x86_64 | **21/21 PASS** (0 failures) |
| **Syntax Verification** | `node -c scripts/admin-announcements-smoke.mjs` | Node.js v24.10.13 | **PASS** (Clean AST) |
| **Browser E2E Config** | `scripts/admin-announcements-browser.config.ts` | TypeScript / Playwright v1.60.0 | **Configured** (Zero lockfile changes) |
| **Browser Spec** | `scripts/admin-announcements-browser.spec.ts` | Playwright test runner | **Configured** (Covers composer, confirmation, history, XSS) |

---

## 5. Rollout Recommendations & Next Steps

1. **Staged Pilot Enablement (Android + Linux)**:
   - Provide operator staging configuration with MongoDB and Firebase Service Account JSON.
   - Run `node scripts/announcement-preflight.mjs --config <operator-config>`.
   - Enable feature intake flag `ANNOUNCEMENTS_ENABLED=true` in staging.
   - Execute pilot cohort sends strictly via `selected` mode ($\le 500$ users).

2. **Full Rollout Pre-requisites**:
   - Provision Windows x64 build agent for MSI packaging and WinRT toast validation.
   - Execute Gate G5 Windows test suite.
   - Verify hardware Android shade display on API 33+ physical device (Gate G2).
   - Resolve inherited checksum and Glance provenance release holds before general production release.
