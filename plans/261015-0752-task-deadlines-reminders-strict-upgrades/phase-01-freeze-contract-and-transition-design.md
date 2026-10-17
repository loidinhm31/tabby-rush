# Phase 01 — Freeze temporal model and strict transition

## Context links
- [Parent plan](plan.md) · [Brainstorm](../reports/brainstorm-261015-0354-task-deadlines-reminders-strict-upgrades.md)
- [Strict research](research/strict-upgrade-research.md) · [Platform research](research/reminder-platform-research.md)
- [Current/proposed architecture](../../docs/system-architecture.md) · [Storage spec](../../docs/local-storage-and-sync.md)

## Overview
- Date: 2026-10-15. Priority P1. Status: DONE (2026-10-15) / 100%; Gate G1 passed.
- Planning estimate: 16h, not a release commitment. Owner: cross-repository contract/integration lead.
- Dependencies: none. Gate G1: reviewed contract, invariants and failure scenarios before backend/schema changes.

## Key Insights
- Existing v1 digest is immutable; Room migration cannot reinterpret frozen network operations.
- Server retains only current lastOp. Superseded acceptance cannot be reconstructed; do not promise automatic resolution.
- Primitive-field wire shape fits existing Hub schema registry; grouped domain types need not become arbitrary JSON columns.

## Requirements
- Freeze one exact supported `strict-v2`/schema pair; keep v1 admitted only on v1 apps and for dedicated historical reconciliation.
- Same instant across devices; captured date-only label/zone separate from notification anchor and overdue boundary.
- Explicit missing/null/default policy, integer bounds, DST policy, error mapping, version-transition and migration invariants.
- No scope expansion: no recurring task, snooze, push, tray/daemon, general migration framework or full historical receipt service.

## Architecture
### Proposed v2 live task shape — freeze names/types here
Keep existing `id:string`, `title:string`, `completed:boolean`, `createdAt:integer`. All fields present in live v2 payloads; absent fields rejected. Nullable means explicit JSON null, not omission. Full replacement, not patch semantics. Tombstones retain canonical empty data.

| Field | Wire type/default for migrated record | Meaning |
|---|---|---|
| deadlineDate | nullable string / null | ISO calendar date in captured zone; original presentation |
| deadlineTime | nullable string / null | Minute-precision HH:mm; null = date-only |
| deadlineZone | nullable string / null | Captured IANA timezone |
| deadlineOffsetSeconds | nullable integer / null | Chosen offset at resolved anchor; explicit DST overlap choice |
| reminderAnchorTime | nullable string / null | Captured HH:mm for date-only; null for timed/no deadline |
| deadlineAnchorEpochMs | nullable integer / null | Resolved timed deadline or date-only reminder anchor |
| deadlineOverdueEpochMs | nullable integer / null | Timed anchor, or next captured-zone calendar-day start |
| reminderEnabled | boolean / false | Synced per-task rule; independent of local device switch |
| reminderLeadMinutes | integer / 0 | Nonnegative; checked conversion to milliseconds |
| reminderIntervalMinutes | nullable integer / null | null if count=1; >=15 if repeating |
| reminderCount | integer / 1 | 1–10 opportunities including first |
| scheduleToken | nullable string / null | Canonical UUID for deadline-bearing schedule; only schedule edits replace it |

No deadline: all deadline/anchor/token fields null and enabled false. Disabled rule with deadline retains validated rule preferences and resolved anchor. Removing deadline clears deadline/token fields and disables rule; no latent enabled rule. Old tasks remain deadline-free. Local notification switch/capability/delivery ledger never serialized to Hub.

### Time and digest decisions
- Checked signed 64-bit epoch milliseconds and minute arithmetic; reject overflow at conversion/subtraction/multiplication/addition. Rust must enforce same Kotlin range, not wider unsigned ranges.
- `first=anchor-lead`; `time(i)=first+i*interval`; count1 ignores interval and canonicalizes it to null. Reject last>anchor, missing deadline, negative lead, count outside 1–10, repeating interval<15.
- Timed overdue starts at anchor; date-only overdue starts at the next local calendar day's start (earliest valid start for midnight transitions), computed by calendar arithmetic and persisted as instant. Never anchor+24h. Minute time formatting is presentation, not rounding of saved instant.
- Reject nonexistent anchor local times. For overlaps enumerate valid offsets and require an explicit user choice. Date-only anchor follows same policy. Use existing kotlinx-datetime; prove strict resolver API availability, introduce narrow expect/actual resolver if necessary rather than silently using library default offset selection.
- Save resolves local presentation only when deadline/anchor settings actually edited. Title/completion/default/device-zone changes preserve saved instants/token. Date-only display stays captured date; timed list display uses device zone and editor exposes captured zone/offset.
- v2 digest domain `strict-v2`; retain v1 framed identity order. Encode original four data fields, then new fields in table order. Nullable: one byte 0=null, 1=value followed by usual encoding. Strings use u32 byte length + UTF-8; booleans one byte; integers 8-byte big-endian two's complement (validated range). Count1 interval null. Delete has zero data bytes. Do not hash JSON text or schema order.
- Freeze vectors: migrated null/default task, date-only, timed overlap offsets, enabled count1/count10, disabled rule, clearing, Unicode, delete and overflow rejects; Rust/Kotlin/Node reference bytes must agree.

### Version/outbox reconciliation decision table
Persist each frozen operation's original contract version and evidence of dispatch. Never-sent means durable PENDING with no dispatch history, not a failed HTTP response.

| Legacy operation evidence | Transition action |
|---|---|
| Never dispatched PENDING | Scan v2 base; rebase known changed fields; freeze new v2 op with new identity only after safe classification |
| Current v1 lastOp matches original ID/digest | Reconciliation-only endpoint returns durable original acceptedRevision; acknowledge exact v1 operation, preserve newer intention |
| Row missing for CREATE expected0 | Nonacceptance provable only with unchanged epoch/permanent tombstones/no hard deletion; translate saved intention |
| Live row revision equals expectedRevision | Nonacceptance provable under monotonic revisions; safe rebase/normal CAS conflict handling |
| Revision advanced/deleted; no matching receipt | UNKNOWN_SUPERSEDED; retain frozen data and explicit review. Never auto-reapply/copy/re-ID |
| Wrong digest/version/epoch or unverifiable integrity | Fail closed incompatibility/reset guidance; no automatic wipe |

Proposed `POST /api/v1/sync/:app_id/strong/legacy-receipts`: ordinary app-scoped sync authorization, bounded batch, original v1 preimage+digest, per-op provenAccepted/provenNotAccepted/unknownSuperseded/integrityFailure. It may confirm matching receipt durability but cannot apply a legacy mutation. No expiry silently removes reconciliation ability for offline clients.
- Before upgrade, new binary can preserve/sync v1 work; deadline saves on a v1 account are visibly blocked until server upgraded, never silently stripped.
- Upgrade status comes from bootstrap before scan/push. Freeze dispatch, recover DISPATCHING→UNCERTAIN, reconcile v1 frozen operations, scan v2 base, then translate safe pending changes atomically. Resumable checkpoints and per-row FIFO retain unresolved heads.
- Whole-row v1 updates cannot overwrite new remote fields. Record a changed-field mask from old intention vs its observed old base; preserve remote v2 deadline/rule. Where history cannot establish a field change, require conflict review instead of guessing. Create retains its original rowId; copying is explicit user action only.

### Migration/admin contract decisions
Proposed management API: `GET /api/v1/apps/:app_id/strict-contract`, `POST /api/v1/apps/:app_id/strict-upgrades`, `GET /api/v1/apps/:app_id/strict-upgrades/:migrationId`.
- Request: migrationId, expectedVersion, expectedSchemaFingerprint, expectedEpoch, targetVersion and exact target schema fingerprint; server owns canonical schema, accepts no arbitrary schema edits.
- Global admin role plus explicit authorization for target app (prefer reuse app ownership/grant model; define missing app-admin check here). A normal app token/API key is insufficient.
- Durable states ready-v1→fencing→transforming→validating→publishing→ready-v2; operatorRequired remains fenced. Active schema/version switch in one journaled registry CAS. Preserve epoch and physical collection name.
- Readers AND writers acquire server-enforced durable app permits before contract validation/data access. Fence acquisition atomically blocks new permits; migration starts only after admitted requests have definitively drained. Use bounded permit admission in one coordination document, not check-then-write or process mutex. Crashed/uncertain requests remain unresolved; timeout/lease expiry must never clear them automatically.
- Recovery of orphan permits requires stopping old owners and proving Mongo operations complete on the supported topology; if proof unavailable remain fenced. Two-instance race/crash proof is mandatory; phase 03 may replace this primitive only with equally strong reviewed evidence.
- Map errors: unauthorized 401, app scope forbidden 403, invalid/unsupported schema 400, stale precondition/concurrent ID conflict 409, protocolUnsupported 426, migrationInProgress/indeterminateStorage 503. Existing envelope convention `{error,code}` retained; no account/task text in diagnostics.

## Related code files
Roots: TabbyRush `/home/loidinh/WS/tabbyrush`, Hub `/home/loidinh/WS/glean-hub`.
- Modify later: TabbyRush `integration/glean-hub/tabbyrush-schema.json`, `docs/system-architecture.md`, `docs/local-storage-and-sync.md`, `docs/hub-schema-integration.md`; Hub `docs/tabbyrush-strict-sync.md`.
- Reference: TabbyRush `shared/src/commonMain/kotlin/dev/tabbyrush/shared/{contract/strict-sync-contract,data/outbox-entity,sync/outbox-reconciliation}.kt`; Hub `glean-hub-plugin/glean-hub-sync/src/{models/strong_sync,services/strong_sync_service,services/app_service,middleware/dual_auth}.rs`.
- Create during execution: versioned canonical v1/v2 fixtures and vector fixture under existing integration/test conventions; no implementation files in this planning session.

## Implementation Steps
1. Compare current registry types, digest, receipt invariants and auth roles against proposal; freeze exact field/API specification in both repos.
2. Produce shared vectors and timezone cases; explicitly review signed ranges, nulls and unknown field rejection.
3. Write transition sequence/table with crash point before/after each acknowledgement, scan and outbox replacement; include old full-row update preservation.
4. Review distributed permit/fence proof and in-place rollback invariants; inventory running server versions/storage topology.
5. Record G1 review approval; rejected proposal needs replacement design before phases 03/05/06 proceed.

## Todo list
- [x] Freeze schema/digest/time vectors and explicit errors.
- [x] Freeze migration fencing/publication/recovery and app authorization.
- [x] Freeze legacy receipt and intent-rebase algorithm.
- [x] Record cross-repository design approval and residual proof gates.

## Success Criteria
- Every brainstorm requirement maps to a named implementation phase and acceptance row.
- Reviewed cases include lost response then supersession; stale title edit cannot erase a new remote deadline.
- Source/target identifiers, canonical schema, errors and client behavior are concrete, not inferred from payload shape.

## Risk Assessment
Primary: assuming absent receipt means unsent; assuming lock expiry means writer stopped. Fail closed and preserve work.

## Security Considerations
App scope and user identity must cover management/status/reconciliation; bound batches; no arbitrary table/schema input; receipts cannot expose another user's task.

## Next steps
G1 unlocks 03/05/06. Phase 02 native/DST proof can run independently; no broad UI before G1+G2.

## Unresolved questions
- Prove chosen cross-instance drain primitive and strict offset resolver; phase 03/02 respectively own evidence. No unresolved product-scope choice.
