# Codebase analysis — admin announcements

Date: 2026-10-18. Planning/static inspection only; no runtime, build or tests executed.
Plan directory: `/home/loidinh/WS/tabby-rush/plans/261018-0707-server-admin-notifications`.
Roots: `T=/home/loidinh/WS/tabby-rush`; `H=/home/loidinh/WS/glean-hub`.

## Inputs and conditional scout decision
- Agreed requirements: [brainstorm](../../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read T README and `docs/{codebase-summary,code-standards,system-architecture,project-overview-pdr,design-guidelines}.md`.
- Summary generated 2026-10-17; filesystem mtime 2026-10-18 02:26 +07. Under 3 days: **skip conditional scout**; no scout report claimed.
- Loaded published planning skill and all six references; research, docs-seeker, sequential-thinking and mermaidjs-v11 guidance.
- Local `.omp` resources absent; published workflows used. `docs/development-rules.md` absent; published development rules apply.
- Active-plan helper invoked; `EVCRATE_SESSION_ID` absent, so no session persistence. All prompts carry explicit plan directory.
- Docs-seeker detect/fetch executed; Context7 returned documentation-not-found. Primary-source researchers provide fallback; helper not a source of feasibility evidence.

## Verified source anchors
| File | Observation / planning consequence |
|---|---|
| H `glean-hub-server/src/routes/notifications.rs:63-153` | Existing JWT-only `/subscribe`; user-scoped broadcast; keepalive/ping and connected event; broadcast lag silently dropped. Not durable replay. |
| Same `:252-262` | `/devices` POST and `/devices/:token` DELETE return 501. No working legacy client contract to preserve; remove obsolete stub paths when replacing. Do not put device token in URL. |
| Same `:156-249,264-285` | Preferences persist email address/fin-catch mode; `notificationsAllowed` gates test email. Preserve existing semantics. |
| H `glean-hub-server/src/main.rs:87-126,363-384` | Notification service initialized only when enabled; shared email channel; notifications mounted separately from admin user routes. One integration owner for main/config/module registries. |
| H `glean-hub-server/src/middleware/admin_auth.rs:14-29` | Reusable admin JWT guard gives `AdminAccessContext.user_id`. Current DB role/grant checks still needed for announcements rather than trusting stale JWT app lists. |
| H `glean-hub-notification/src/channels/push.rs:12-75` | Stub accepts legacy server key/APNS fields; never sends real pushes. Replace obsolete FCM path, migrate every caller/config use; do not silently enable push for old producers. |
| H `glean-hub-notification/src/channels/sse.rs:52-163` | Recipient send available; no-active-receiver is not delivered. Reservation ledger is suppression only. |
| Same `:165-180` | `broadcast_to_app` iterates every user's channel; **must not use for announcement audience delivery**. |
| H `glean-hub-notification/src/models.rs:207-245` | Existing SSE envelope serializes title/body/user ID. New announcement hint must be minimal and app-scoped; avoid changing payloads of old producers. |
| H `glean-hub-notification/Cargo.toml` | Tokio/serde/chrono/uuid/async-trait; no active FCM dependency. Add OAuth/HTTPS deps intentionally and own workspace Cargo.lock once. |
| H `glean-hub-app/src/pages/admin/notification-management.tsx:1-78` | Permission management only. Extend existing page without replacing email permission controls; separate announcement composer/history files. |
| H `glean-hub-app/src/services/sync-admin-service.ts:51-57,77-86` | Uses `getValidAccessToken`, canonical `getServerUrl`, bearer header, sanitized server errors. Follow these conventions in announcement service. |
| T `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt:72-103,189-201` | Exact allowlist, 20s request timeout, no SSE plugin; non-auth 409 currently mapped to epoch mismatch. Add precise announcement route parsing, separate streaming timeout policy, domain-safe errors. Do not weaken origin/redirect policy. |
| T `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt:437-570` | `authorizedRequest` refreshes but returns TOKEN_REFRESHED instead of replaying; `quiesce/logout` increment generation. Remote coordinator owns safe retries and checks generation after every response. |
| T `androidApp/build.gradle.kts:46-55` | Android Firebase application ID must be `dev.tabbyrush.android`, not Hub app ID. |
| T `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:115-120` | Runtime Hub `appId` is **`tabby-rush`**. Some docs say `tabbyrush`; do not copy documentation spelling into wire contract. Freeze actual configured ID. |
| Same `:79-165,182-225` | Desktop account runtime/controller owns process lifetime; no-tray prevents unrecoverable hide. SSE belongs here, not Compose screen. |
| T `androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt:44-125` | Central composition and composite leave gate. Android phase owns runtime/factory/application/view-model/activity/cancel gate files together. |
| T `androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt:190-210` | Close swallows drain failure; caller clears active runtime anyway. Remote work must not inherit stale runtime publication or close-under-active-work. Fix only lifecycle behavior necessary for remote safety, not unrelated widget cleanup. |
| T `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt:44-64` | Opens/disconnects bus per call; remote actions need persistent signal connection with daemon-owner lifecycle handling. Reuse low-level transport, never fake reminder task IDs. |
| T `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt:27-76` and `windows-toast-bridge.kt:22-35,99-134,245-248` | Wrapper builds toast XML, AUMID and cancel groups; current source implements PowerShell WinRT projection and aliases `JnaWindowsToastBridge` to it. Architecture's unavailable-stub description is historical/stale. Display is implemented in source, but no native activation callback or runtime proof established; plan replaces bridge while preserving reminder behavior. |

## Deployment evidence versus unknowns
- H `k8s/glean-hub-host.yaml:20-51` sets server replicas=1, Mongo URI from runtime Secret. Supports agreed single-process design; rolling deploy may temporarily overlap processes, so lease fencing still matters.
- H `glean-hub-server/docker-compose.yml:14` and `glean-hub-project/docker-compose.yml:14` use external/default Mongo URI, not a provisioned replica set. No actual live Mongo topology verified; no secret values opened.
- Choose bounded single-document acceptance independent of transactions. Implementation preflight must inspect `hello`, journaling/write concern and indexes; no fail-open topology fallback.
- Existing strict-sync release holds G2/G3/G6/G7 and Gradle checksum/Glance provenance holds stay open. This feature cannot clear them by documentation.
- Other work is active in sibling Hub (embed simplification plan, frontend config). Use exclusive files and pre-edit rereads; do not alter embedded apps.

## Parent decomposition recommendation
- First freeze API/storage/stream/platform contracts and shared build files; no worker/UI phase owns those same files later.
- Parallel component work after contract gate: Mongo+routes; FCM transport; React admin; shared remote core; shared inbox UI; Linux native transport; Windows native transport; deployment/runbook files where disjoint.
- Runtime host wiring waits for shared/OS components: Android owns all Android files and device-specific security audit adjustments; desktop owns main/controller/composite gate. Do not assign host files to UI or OS adapter phases.
- Keep unit/behavior tests with owning component phase. Final cross-system acceptance owns only separate smoke/evidence files and release docs, not earlier test files.
- Distinguish compile/design dependency from runtime acceptance dependency. Do not pretend every phase is independent: freeze → parallel components → host wiring → cross-platform acceptance.
- Full enumerated file ownership manifest, no directory catchalls mixed with child entries; one phase per path including locks/build/docs/tests/generated exports.
- Prefer in-memory canonical inbox cache initially (durable offline viewing was optional); avoids task Room schema/migration changes. Must explicitly show offline unavailable, clear/fence on leave and expiry, fetch/reauthorize before opening message. Server inbox remains durable.
- Recipient confirmation needs stable server-resolved snapshot or versioned preview token; never silently send to a changed audience after count confirmation.
- SSE read-state convergence cannot use creation cursor alone; refresh visible pages/unread independently or provide a bounded mutation cursor. Historical hydration must never produce OS alerts.

## Required future verification (not executed)
- Live standalone Mongo acceptance → kill server after response/between materialization/dispatch writes → restart/recover one logical message/recipient; conflict key replay, stale lease, expiry.
- Actual admin browser composer/confirmation/history and non-admin denied; old permission UI preserved.
- Real Firebase Google-enabled Android foreground/background/cold+warm tap/permission denial/offline/logout/switch; inspect generic payload and notification shade.
- Actual Linux D-Bus daemon and installed Windows MSI show + activate intended inbox while visible/tray; no-tray recovery; full quit stops SSE, reopen catches up without historical toast replay.
- Read-state two devices; retention boundary at createdAt+90d; regression local reminders/sticky/sync/account leave; strict dependency verification remains release requirement.

## Unresolved prerequisites
- Live Mongo topology/journaling and exact target grant records; no live environment queried.
- Firebase project credentials/configuration, Google-enabled Android device, real Linux session/actions capability, Windows MSI/native compiler/testbed.
- Native Windows COM bridge details require early implementation proof; not grounds to silently drop Windows.
