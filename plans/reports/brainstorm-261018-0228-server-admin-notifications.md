# Brainstorm: Glean Hub admin notifications to Tabby Rush

Date: 2026-10-18. Status: user-agreed architecture; implementation not started.
Repositories: `/home/loidinh/WS/glean-hub` and `/home/loidinh/WS/tabby-rush`.
Artifact: decision/report only. Not an implementation plan or runtime acceptance result.

## Problem and agreed requirements

Admins compose notifications in `glean-hub-app`; `glean-hub-server` stores and dispatches them. Tabby Rush is the receiving client—not a new Glean Hub native shell.

- Audience modes: selected users, all users, admins only. Every audience intersects current Tabby Rush access.
- Manual send with title/body, recipient confirmation and admin sent history.
- Tabby Rush inbox, unread/read state, notification-to-inbox navigation.
- Android: native background delivery through FCM on Google-enabled devices. Generic system alert only; no message title/body in push.
- Desktop: Linux and Windows, while process is running with window open or in tray. No live delivery after full quit.
- Small internal deployment, single server process. No strict push-delivery SLA. No hard deadline supplied; assume correctness over a date commitment.
- History retention: 90 days from message creation; reading does not extend retention.
- User explicitly accepts a late content-free Android notification after logout. It must never grant access to old-account content.
- Existing local task reminders, sticky surfaces and task sync retain their behavior.

## Existing implementation: evidence and gaps

Static inspection only; two read-only scouts covered Hub backend and UI. Parent inspected Tabby Rush notification transports and account teardown. No application launched, builds or tests run; no push sent.

| Surface | Existing evidence | Gap |
|---|---|---|
| Hub admin UI | `glean-hub-app/src/pages/admin/notification-management.tsx:14-78`: user list and notification-permission toggle | No composer, audiences or sent history |
| Hub routes | `glean-hub-server/src/routes/notifications.rs:63-100,252-262`: authenticated subscribe/preferences; devices return 501 | Device binding, inbox/read endpoints, admin send |
| Push channel | `glean-hub-notification/src/channels/push.rs:1-75`: explicit stub and legacy server-key config | Real FCM HTTP v1 authentication and sending |
| Delivery service | `glean-hub-notification/src/service.rs`; `channels/sse.rs`: shared channel service, process-local live broadcast | Durable notification acceptance/history and recoverable dispatch |
| Existing deduplication | `glean-hub-server/src/services/notification_idempotency.rs`: Mongo reservation ledger | Not an inbox or complete send-job lifecycle; do not mistake reservations for delivered messages |
| Hub producer precedent | `glean-hub-plugin/glean-hub-app-notification-events`: persisted event processing and leases | Reuse patterns; do not route manual announcements through task sync |
| Android display | `androidApp/src/main/kotlin/dev/tabbyrush/android/android-notification-adapter.kt:71-135` | FCM integration, remote-message channel and inbox navigation |
| Linux display | `desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-notification-adapter.kt:21-139`: D-Bus notification transport | Remote controller and action listener; current transport disconnects after each call |
| Windows display | `desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-notification-adapter.kt:27-75`: toast display/removal | Remote controller and activation routing; display alone does not prove clickable navigation |
| Account safety | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt:33-46,91-156`: generation, wipe marker, drain/cancel, local deletion | Remote work/streams/cache must join existing lifecycle |

Hub's existing `notificationsAllowed` projection maps to an email-oriented gate. Do not silently reinterpret it as a global push permission or change existing producers. Preserve its semantics; remote notification policy and OS permission must be explicit in UI/API.

## Alternatives evaluated

| Approach | Pros | Cons | Decision |
|---|---|---|---|
| FCM Android + SSE desktop + Mongo inbox | Reuses Hub SSE; responsive running desktop; standard Android background delivery | Two transports; reconnect and lifecycle safety | Selected |
| FCM Android + desktop polling + Mongo inbox | Simple desktop request lifecycle | Poll latency and idle requests | Viable, not selected |
| Managed notification platform | Delivery tooling and provider dashboard | Additional vendor, pricing/privacy assessment; duplicates Hub responsibilities | Not justified for internal scope |

SSE-only is not a substitute for reliable Android background wake-up. A broker adds no value to the agreed single-process deployment. No Redis/Kafka or separate worker service required.

## Recommended design

### Authority and delivery

MongoDB stores canonical message content, recipient membership, read state and recoverable dispatch intent. FCM and SSE are hints/alerts, not the history store.

1. Admin submits authenticated send request with stable idempotency key.
2. Server verifies admin privileges and resolves an explicit recipient snapshot intersected with Tabby Rush access. Snapshot excludes users joining after acceptance.
3. Persist accepted message, recipient snapshot and pending dispatch in a recoverable structure before acknowledging acceptance. No acceptance-to-dispatch crash gap.
4. In-process worker fans out recipient inbox state and sends generic FCM alerts plus recipient-scoped SSE events.
5. Tabby Rush fetches canonical, paginated inbox through authenticated APIs. Opening an item marks read; other devices reconcile server state.
6. Expired items are excluded immediately by API rules; Mongo TTL cleanup can be asynchronous.

Implementation planning must choose an atomic Mongo structure appropriate for the actual deployment. Do not assume standalone Mongo supports multi-document transactions. A bounded immutable parent message/recipient snapshot with pending work, followed by idempotent recipient materialization, is one viable design. Enforce a supported audience/payload bound rather than unbounded embedded arrays. If using transactions instead, explicitly provision/test the required Mongo topology.

Keep dispatch durability within the existing server process: claim/lease pending work, retry transient failure with backoff, stop on permanent failure, retire invalid device tokens, resume after restart. Unique constraints prevent duplicate logical messages/recipient rows. Provider timeouts can leave send outcome ambiguous; at-least-once retries can duplicate alerts. Never promise exactly-once OS presentation.

### Authorization and device lifecycle

- Server derives sender/device owner from authentication, never a caller-provided user identity.
- Device registration binds install, app, authenticated user and a binding version; delayed old registrations/removals cannot overwrite a new account binding.
- Rotate/re-register FCM tokens; remove invalid tokens. No service-account credential in client bundles, admin UI or repository.
- Resolve audiences server-side. At inbox read, detail fetch and dispatch, enforce current Tabby Rush authorization. Access revocation blocks future content access even if recipient membership was snapshotted earlier.
- Opaque message IDs are identifiers, not authorization. Notification clicks must authenticate and reauthorize.
- Logout cancels remote controllers, fences callbacks/responses using account generation, clears account-owned cached content and posted alerts, and attempts server-side unbinding. Offline unbinding cannot be guaranteed and must not prevent local account cleanup.
- Never put task contents, message bodies, user-identifying details or auth tokens in FCM payloads. Use fixed generic title/body and minimal opaque routing metadata.

### Android

- Native Firebase Messaging SDK; server uses FCM HTTP v1 and short-lived OAuth credentials, not legacy server keys.
- Background: notification payload lets Android display generic alert. Foreground: Firebase handler posts the generic alert locally.
- A single OS-alert path: Android does not also turn SSE/inbox reconciliation into OS alerts for the same message.
- Separate remote-notification channel/group from reminders and sticky surfaces. Respect Android permission and channel disablement.
- Handle tap with app cold or running; select authenticated inbox context without exposing another account's cached message.
- Generic auto-rendered pushes can arrive after logout/account switch; user accepted this residual behavior. Local cleanup cannot retract a future already-queued FCM message.
- Force-stop, permission denial, OEM battery restrictions, network loss and FCM behavior prevent guaranteed delivery. Durable inbox recovers content when app reconnects.

### Linux and Windows

- Authenticated SSE lifecycle belongs to signed-in application process, not a visible Compose screen. Hiding to tray must not stop it.
- Reuse lower-level D-Bus/Windows toast transports. Do not fake task IDs or route admin messages through `NativeReminderAdapter` scheduling contracts.
- Separate remote notification identifiers/groups; integrate cancellation with account leave.
- SSE is a hint. Durable inbox cursor plus subscribe/reconcile ordering closes connection races; reconnect and bounded periodic reconciliation recover missed hints.
- Historical catch-up updates inbox/unread state without replaying old OS alerts. De-duplicate live hints by notification ID per account/device.
- Implement click activation: persistent D-Bus signal handling where supported, Windows activation appropriate to the packaged application. Existing display code alone is insufficient.
- No tray support: keep app accessible in a window and surface capability limits; do not hide it irretrievably. Full quit stops receiving; reopen catches up inbox.

### UI and contract boundaries

Admin UI extends existing notification-management area without removing permission management. Composer: plain-text title/body, audience selection, resolved recipient count, confirmation, submission state. Sent history distinguishes accepted/pending/provider-accepted/failed; do not label provider acceptance as device receipt.

Tabby Rush shared Compose UI: inbox list/detail, unread count, empty/loading/error states, read state and notification entry navigation. Authenticated API contract covers admin create/list, user inbox/detail/read, and device register/unregister. Exact routes, DTOs, indexes and Kotlin storage migration belong in the implementation plan, not a parallel ad hoc protocol.

Use a separate remote inbox cache if durable offline viewing is implemented; fence and clear it with account-owned storage. Do not change strict task-sync schemas or synchronize remote announcements as tasks. Read state can reconcile on connectivity; do not claim offline server acknowledgement.

## Scope exclusions

Automatic domain alerts, scheduled campaigns, attachments, arbitrary external links, email delivery changes, new Glean Hub native shell, iOS/macOS, non-Google Android support, desktop receive after quit, multi-replica routing, task-sync protocol changes and guaranteed/exactly-once push delivery.

## Cost and operational dependencies

Official Firebase pricing checked during this session: **Cloud Messaging (FCM) is no-cost**. Spark requires no payment method. No per-notification FCM fee; quotas and delivery restrictions still apply.

Direct Rust-server FCM sending does not require Firebase Hosting, Firestore, Cloud Functions or migration of Hub authentication. Existing server/MongoDB storage, networking and operational costs remain. Other optional Firebase/Google Cloud products have independent pricing; do not enable them merely for this feature.

Dependencies: Firebase project/app registration matching Tabby Rush Android application ID; secure server credential provisioning; outbound HTTPS; deployment of updated Hub and client; Google-enabled Android device; supported Linux desktop notification/tray environment; packaged Windows acceptance environment. No credentials inspected or provisioned in this brainstorm.

## Acceptance and validation

These are future implementation checks, not passed results.

| Check | Observable acceptance |
|---|---|
| Admin and audience authorization | Non-admin send rejected; selected/all/admin audience intersects Tabby Rush access; excluded users cannot list/fetch content |
| Durable acceptance | Crash after acceptance, restart and dispatch recovery leave one logical message and one inbox entry per intended recipient |
| Send idempotency | Repeated submission with same key creates no duplicate logical message; conflicting payload rejected |
| Provider ambiguity | Simulated accepted-send/lost-response does not corrupt history; possible duplicate OS alert documented, not mislabeled exactly-once |
| Android foreground/background | Generic alert on permitted Google-enabled device; no title/body leaked; tap works cold and warm |
| Android denial/offline | No permission bypass; inbox persists and is available after reconnect |
| Device lifecycle | Token rotation and invalid-token removal work; stale binding operations cannot steal/remove a newer binding |
| Logout/account switch | Old responses/streams cannot populate new account; no old-account content on click; accepted late generic alert caveat verified |
| Linux/Windows runtime | Live notification open and tray; click opens intended inbox; full quit stops live delivery |
| SSE race/recovery | Events during disconnect or initial subscribe/reconcile are recovered through inbox; catch-up does not create old-toast storm |
| Read state | Open item becomes read and converges across signed-in devices; delivery alone does not mark read |
| Retention | At 90 days, content/read history no longer accessible; associated dispatch data cleaned; expired messages not sent |
| Regression | Local reminders, sticky surfaces, account teardown and strict task sync remain unchanged |

Targeted permanent tests should cover authorization, persistence/crash boundaries, idempotency, lifecycle races and retention. Real runtime smoke must cover admin -> server -> actual Android and Linux/Windows surfaces. Existing repository docs note native release/desktop acceptance holds; this report does not clear them. Firebase credentials and hardware proof are required before declaring delivery implemented.

## Next steps and handoff

1. User decides whether to request a detailed cross-repository implementation plan.
2. If requested, plan API/storage contracts first, then Hub backend/admin and Tabby Rush shared/platform work, deployment/configuration, and runtime acceptance. Plan must start `status: pending`; no implementation authorized by this report alone.
3. Update affected architecture/deployment/security/acceptance docs with implementation; do not mark roadmap delivery now.

Research tools: repository reads/scouts plus official Firebase pages. Docs-seeker helper could not resolve this Firebase query through Context7; primary documentation used instead. Advisor reviewed candidate architecture and highlighted atomic acceptance, stale generic pushes, device-binding races, SSE reconciliation and native activation gaps; incorporated above.

## Sources

- [Firebase pricing](https://firebase.google.com/pricing)
- [FCM Android receive behavior](https://firebase.google.com/docs/cloud-messaging/android/receive-messages)
- [FCM HTTP v1 and server authentication](https://firebase.google.com/docs/cloud-messaging/send/v1-api)
- Local source evidence listed above; repository READMEs for stack and native release caveats.

## Unresolved questions / prerequisites

No unresolved product choice blocks this agreed brainstorm. Implementation planning must verify Mongo deployment topology and audience bounds before choosing atomic storage; determine native activation integration and exact DTO/cache schema; establish Firebase project credentials and device/OS acceptance environments. These are not claims of completed feasibility smoke. Whether to create the detailed implementation plan remains the next user decision.
