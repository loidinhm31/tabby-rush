# Phase 03 — FCM HTTP v1 transport and legacy push cutover

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P09](phase-09-android-runtime-integration.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P03**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **16h**.
- Description: FCM HTTP v1 transport and legacy push cutover implemented; token validation and mock transport verified across 35 Rust notification tests (1 ignored doc-test).

## Key Insights
- HTTP v1 has no idempotency key; SDK background duplicates cannot be guaranteed away.
- INVALID_ARGUMENT is not blanket invalid-token proof.

## Requirements
- Short-lived OAuth firebase.messaging scope, secret-file/ADC custody and bounded timeout.
- Fixed generic notification+exact data keys; precise error classification and matching invalidation result.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
PushChannel implements P01 AnnouncementPushTransport. PushConfig becomes projectId+credential source, timeout and optional disabled configuration; no legacy/APNS aliases. Existing NotificationService legacy content push path returns explicit unsupported/not-configured, never forwards private content.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/channels/push.rs` | modify | Replace legacy server-key/APNS stub with real OAuth HTTP v1 PushChannel and typed outcomes. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/service.rs` | modify | Migrate push construction/config callers; do not enable content-bearing legacy push producers. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/models.rs` | modify | Remove obsolete APNS-specific push documentation/fields only when no remaining caller; preserve old SSE DTO. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/tests/fcm_transport.rs` | create | OAuth/cache/concurrency/payload/error/timeout behavior tests. |

## File Ownership
- P03 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Implement ADC/read-only service-account-file loading, project consistency validation and single-flight cached OAuth token refresh five minutes before expiry. No JSON credential env injection or token disk persistence.
2. Implement fixed payload from03 and remaining-lifetime TTL; validate payload locally before network. HTTPS fixed Google endpoint; redact target/header/provider error details.
3. Enforce5s connect/15s total timeout, bounded worker-injected target requests. Refresh OAuth once on401 then return configuration failure; no hidden unbounded transport retries (worker owns attempts).
4. Map FCM success/UNREGISTERED/token-specific INVALID_ARGUMENT/general400/429 Retry-After/500/503/SENDER_ID_MISMATCH exactly to typed outcomes. Network-after-send ambiguity returned explicitly.
5. Delete old server-key/APNS fields and placeholder success path. Update all PushChannel constructors in owned service file; keep channel disabled for old content producers. Ask01 for export/dependency adjustments instead of editing its files.
6. Test credentials via test token issuer/HTTP server only in unit tests; real authenticated request and generic shade proof require02/09/11 then final12 evidence.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Payload contains fixed generic copy and only type/appId/messageId data.
- Real provider acceptance recorded distinctly; ambiguity and token-specific invalidation correctly typed.
- No fake success, APNS config or credential leaks remain.

Future targeted verification (not executed by this plan):
- `At H: cargo test -p glean-hub-notification --test fcm_transport --locked`
- `Live: send one permitted generic payload to test Google-enabled Android, record provider ID redacted and actual shade capture.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Wrong project/package causes permanent failures; surface config hold, not token deletion loop.
- Provider quota/outage cannot block local logout or corrupt durable inbox.

## Security Considerations
- Service account remains server-only, read-only mount/ADC, least permission and redacted logs.
- No arbitrary endpoint override in production or notification content in push.

## Next steps
- Consumers/unblocked phases: [P09](phase-09-android-runtime-integration.md), [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
