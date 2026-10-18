# Phase 02 — Backend storage, audience, device routes and durable worker

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P03](phase-03-fcm-http-v1-transport.md), [P05](phase-05-shared-remote-core.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P02**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **32h**.
- Description: Backend storage, audience, device routes and durable worker implemented; BSON dates, cursors, TTL, and device routes verified across 85 Rust server tests.

## Key Insights
- Parent snapshot is authority; inbox exists before child materialization through parent queries.
- Standalone transactions unavailable by assumption; expired processing must be reclaimed.

## Requirements
- Implement complete canonical routes and worker algorithms in report03, not reservation-only dispatch.
- Preserve old email/preferences and producers; inject real push transport without task-sync coupling.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Parent journaled acceptance -> recipient row with immutable scan cutoff -> bounded immutable device target batches -> deterministic durable children -> fenced worker. Separate app/recipient broker provides ephemeral hints; parent membership/content and joined first readAt are canonical. No per-user device cap or unbounded embedded device snapshot.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/main.rs` | modify | Own server route mount, feature startup/worker and shutdown integration. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/config.rs` | modify | Parse frozen announcement flags/project/credential path with no secret logging. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/models/mod.rs` | modify | Register backend model once. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/models/admin_announcement.rs` | create | Parent/preview/inbox/dispatch/install persistence records. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/mod.rs` | modify | Export new announcement route router. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/notifications.rs` | modify | Remove 501 token URL device stubs only; preserve subscribe/preferences/test email. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/admin_announcements.rs` | create | Admin preview/create/history/idempotency recovery router. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/routes/announcement_inbox.rs` | create | User inbox/detail/read/unread/stream and install bindings. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/mod.rs` | modify | Register announcement services once. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_repository.rs` | create | Indexes, journaled parent insert, canonical queries, child upserts and cleanup. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_audience.rs` | create | Current DB role/grant resolution and preview confirmation. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_devices.rs` | create | Install proof/fencing CAS, token retirement and tombstones. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_worker.rs` | create | Recoverable parent materialization and per-target leased dispatch. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/src/services/announcement_stream.rs` | create | Authenticated app-scoped SSE ready/baseline and hint broker. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/channels/sse.rs` | modify | Add separate minimal recipient/app channel primitive preserving legacy send envelope. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_storage.rs` | create | Standalone real Mongo crash/checkpoint/retention and query contract tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_authorization.rs` | create | Current DB authorization, preview/idempotency and device-race tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/tests/announcement_stream.rs` | create | Ready ordering, revoked JWT/grant, lag and silent recovered-hint tests. |

## File Ownership
- P02 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Preflight live Mongo hello/journaling/write concern/index permissions via11 procedure. Create all report03 indexes before enabling intake; fail closed rather than use unjournaled acceptance.
2. Implement DB-backed current admin/grant guards and full bounded audience preview. Resolve up to501; enforce same recipient IDs/digest and sender-bound five-minute preview.
3. Implement existing-key lookup before preview validation, request digest conflict and single parent insert. Handle write timeout as unknown; by-idempotency recovery returns original snapshot. Never resolve recipient set after acceptance.
4. Implement canonical parent-backed list/detail/unread and deterministic read-row upsert; first readAt only, read does not extend expiry. Sign scoped stable keyset cursors and support refresh-chain reset.
5. Implement install proof constant-time comparison, numeric persisted generation CAS, exact replay and token partial unique index. Retain unbound/invalid tombstones; test old register arriving both before and after switch, delayed unbind, lost response and invalid-old-token retirement.
6. Materialize each recipient with immutable server scan cutoff, deterministic install-ID page50 cursor and frozen bounded target-batch records, preserving early readAt. No per-user device cap. Bindings active before scan cutoff and still current when enumerated are eligible; later registrations never backfill old OS alerts. Persist batch before child upserts and checkpoint only after child verification; reclaim expired parent processing before verifying/finishing. Recheck every contiguous prior batch/child plus SSE on completion, never mark complete with missing work.
7. Implement dispatch leases/reclaim/renew and fence all final writes; bound concurrency/retry/expiry. Reauthorize parent/member/current grant/current install fence immediately before send. Persist provider outcome without calling it delivery.
8. Implement separate recipient/app hint channels, admission epoch/ordinal and locked ready baseline. Buffer registration-before-ready ordering; enforce15s auth/grant checks, JWT expiry, lag reconcile+close and shutdown cancellation. Legacy SSE envelopes unchanged.
9. Wire real PushChannel configuration from03 in main only after03 lands; own route/module registries/config/shutdown. Startup feature readiness requires Mongo checks; FCM health exposed to admin, not silently bypassed.
10. Add deterministic clock/failure-point tests in owned suites plus real standalone kill/restart harness scenarios. Remove test failure injection from production exposure (test-only cfg). Route any new dependency request to01.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- 202 always has journaled immutable snapshot/pending authority; every crash boundary recovers one logical parent/recipient.
- Grant revocation, stale operations, cursor tampering and expired reads/sends fail safely.
- FCM/SSE/no-listener status accurate; legacy email gate and producer contracts unchanged.

Future targeted verification (not executed by this plan):
- `At H: cargo test -p glean-hub-server --test announcement_authorization --test announcement_stream --locked`
- `With disposable standalone Mongo: cargo test -p glean-hub-server --test announcement_storage --locked -- --test-threads=1`
- `Live gate: SIGKILL after202, midway fanout, after provider acceptance; restart and inspect deterministic rows/fences.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Partial Mongo writes are normal; retain parent responsibility until verified, never counter-only completion.
- Worker storage/code size may need local modules; new file ownership amendment before splitting, no uncontrolled directory claim.

## Security Considerations
- Never trust JWT app list/role or userId request. Proof/token/credential headers redacted.
- Install secret and token fields never exposed in history/API/logs; retention excludes install barrier tombstones.

## Next steps
- Consumers/unblocked phases: [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
