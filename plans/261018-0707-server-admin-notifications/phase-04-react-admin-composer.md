# Phase 04 — React admin composer, confirmation and sent history

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P04**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **16h**.
- Description: React admin composer, confirmation dialog and sent history implemented; 48 Web Admin unit tests pass and TypeScript check is clean.

## Key Insights
- Existing notification page is permissions-only and must retain that behavior.
- Submission timeout is unknown commit outcome, not permission to generate another key.

## Requirements
- Selected/all/admin audience, recipient count and exact preview confirmation, title/body validation and sent history.
- Accessible responsive plain-text UI, admin guard, no new package dependency/embedded-app changes.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Existing page composes independent permissions/composer/history sections. Service follows getValidAccessToken/getServerUrl conventions; pure composer state freezes key/body and owns sessionStorage recovery keyed by sender+origin.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/notification-management.tsx` | modify | Extend page alongside unchanged email permission management. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer.tsx` | create | Plain-text composition, audience controls, normalized confirmation and uncertain-result recovery. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-history.tsx` | create | Paginated sent history with honest materialization/provider states. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/services/admin-announcement-service.ts` | create | Canonical server URL/bearer API, frozen request and same-key recovery. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/services/admin-announcement-service.test.ts` | create | API DTO/error, request snapshot, replay and recovery tests. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer-state.ts` | create | Small pure composer state transitions, session-owned pending request persistence. |
| `/home/loidinh/WS/glean-hub/glean-hub-app/src/pages/admin/announcement-composer-state.test.ts` | create | Confirmation invalidation, owner change, unknown-send and conflict tests. |

## File Ownership
- P04 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Add typed announcement service for exact admin routes using canonical origin, bearer token and sanitized error DTO. Never use browser EventSource/query tokens for admin submission.
2. Build title/body and selected/all/admin controls using existing directory identities. Preview sends spec; show normalized copy, resolved count, included/excluded identities and expiry. Any content/audience edit invalidates confirmation, not silently reuses old count.
3. Require explicit confirm of content and exact preview digest/count. Generate key once and freeze request; disable edits while sending/unknown and persist bounded owner+origin-scoped pending intent in sessionStorage (no auth tokens).
4. On timeout/lost response show unknown, GET by-idempotency then exact POST/key if absent. Recover after browser reload without reconfirming into another message. Expired/uncommitted preview or changed audience requires fresh preview+explicit new action;409 digest conflict is hard stop.
5. Render accepted/pending/processing/provider-accepted/failed/skipped/expired/ambiguous counts with provider-is-not-receipt copy. Refresh accepted history while materialization pending; pagination resets on refresh and handles retention expiry.
6. Clear pending content/session storage on logout or sender/origin mismatch. Retain existing notificationsAllowed toggles and email test semantics, show Android delivery availability separately.
7. Add pure transition/service tests now; actual admin/nonadmin browser flows with real backend and2 user roles run in12. Do not invent a frontend stub backend or new dependency stack.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Actual admin can compose/preview/confirm/send/history; eligible recipients exactly match confirmation.
- Unknown send recovers same logical message after reload; no duplicate replacement or provider-as-receipt label.
- Existing permission management remains usable.

Future targeted verification (not executed by this plan):
- `At H/glean-hub-app: pnpm test:unit`
- `At H/glean-hub-app: pnpm exec tsc --noEmit`
- `Live12: actual browser selected/all/admin, excluded user, changed audience, dropped create response then reload recovery.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Concurrent users/grant edits invalidate preview; explain reconfirm instead of automatic resend.
- History refresh and unknown state must not lose frozen intent on routine rerender.

## Security Considerations
- No raw server errors/HTML; private content never in browser telemetry or URL.
- Session pending draft owner-scoped and cleared on logout; current DB admin still enforced server-side.

## Next steps
- Consumers/unblocked phases: [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
