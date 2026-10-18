# Phase 05 — Shared remote inbox core and authorized HTTP/SSE

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P05**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **28h**.
- Description: Shared remote inbox core, SSE client, controller with 200-item sliding cache, and auth integration implemented; passing shared JVM tests under strict dependency verification.

## Key Insights
- Hub authorizedRequest signals TOKEN_REFRESHED, does not replay POST; caller retains intent.
- Creation/keyset cursor cannot capture cross-device read mutations; refresh pages/unread separately.

## Requirements
- Implement report03 controller against pure P01 types; host-injected lifecycle and platform alert policy.
- No task Room migration, durable content viewing or read outbox; clear/fence across leave.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Authorized API client + streaming client -> concrete controller -> immutable RemoteInboxState. Generation checks guard each callback/response/native activation; cache limits and server-time anchor enforce privacy/retention. Existing auth session remains source of tokens and single-flight refresh.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` | modify | Narrow remote route classification, safe domain409 and per-stream timeout without origin weakening. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client.kt` | create | Typed authorized inbox/detail/read/device API client with frozen safe-retry semantics. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-stream-client.kt` | create | Bounded incremental SSE parser and authenticated connection lifecycle. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-controller.kt` | create | Concrete account-generation fenced runtime, bounded cache/read convergence/navigation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-cache.kt` | create | In-memory200-item page chain, expiry/server-time anchor and canonical merge. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client-test.kt` | create | Exact paths/auth/error/replay/timeout tests. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-stream-client-test.kt` | create | Ready/buffer/race/lag/ordinal/reconnect parser tests. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-inbox-controller-test.kt` | create | Crossdevice read refresh, expiry, stale generation, quiescence and cache limits. |

## File Ownership
- P05 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Extend exact allowlist for configured app receiver routes only, with UUID segments strictly parsed; strip query for path classification after approved query validation. BEARER_ONLY and no redirect/origin/API-key weakening. Announcement409 maps remote domain error, not EPOCH_MISMATCH; existing sync handling untouched.
2. Implement typed JSON APIs, bounded content/response/error parsing. Retry GET/idempotent PUT read once after TOKEN_REFRESHED; device operations replay exact immutable command only when host requests same generation/body. Cancel/discard on generation mismatch.
3. Implement raw streaming over existing Ktor core ByteReadChannel with10s connect, disabled request timeout,45s idle, max16KiB frame/256 buffered hints. Support comments/event/id/data framing and split UTF-8; malformed/oversized frame closes and silent reconciles, never interprets as title/body.
4. Implement ready baseline handshake: buffer while page1/unread/detail refresh, silent initial hydration, server epoch+ordinal+liveEligible gating for desktop only. Never compare UUID order/client wall clock. Dedup cap2048 triggers silent reconnect; no silent eviction causing repeat alert.
5. Implement cache max200 items,25/50-page cursor chain and reset-on-refresh; merge canonical readAt monotonically, remove expired/unauthorized rows. Refresh unread and all visible state on hints/reconnect/foreground/60s, not only new IDs.
6. Native Open always fetches authorized detail before publishing content, then PUT read; canonical acknowledgement only. Offline view hides body/detail and reports unavailable/stale; mark-read failures remain unacknowledged without persisted queue.
7. Implement quiesce barrier: advance/fence generation before cancellation, close stream/timers/fetches, clear routing/dedup/cache and posted remote alerts. If local work cannot drain, report failed drain and retain fenced runtime handle; remote unbind HTTP never gates local cleanup.
8. Test initial race, delayed worker hint, old epoch, Last-Event-ID nonreplay, read on second device, network/JWT renewal, grant revocation, expiry at exact boundary, stale response and native callback after switch. Integration acceptance waits real02/03, no simulator counts as live proof.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- No historical toast storm; events during baseline recovered, missed events reconcile silently.
- Read state converges within online reconciliation interval; stale/offline content cannot open.
- No task protocol/schema change, no409-induced task reset, no callback crossing generation.

Future targeted verification (not executed by this plan):
- `./gradlew :shared:jvmTest --tests "*RemoteInbox*" --tests "*RemoteStream*" --dependency-verification=strict`
- `Real02 integration: drop SSE during create/read, reconnect two devices and record silent catch-up/unread convergence.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- SSE long-lived auth block may retain old token; server expiry closes and client renews with safe replay.
- Cancellation can race UI publication; check generation at final publication not just request start.

## Security Considerations
- Only current-auth content rendered; no tokens in SSE URL/log, parser memory bounded.
- Revocation clears state immediately on observed403; server guards each fetch regardless of cached UI.

## Next steps
- Consumers/unblocked phases: [P09](phase-09-android-runtime-integration.md), [P10](phase-10-desktop-runtime-integration.md), [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
