# Native Sync and Access Lifecycle — Research

**Research date:** 2026-10-21 (Asia/Bangkok)  
**Scope:** TabbyRush Android/desktop offline state, Room migration, paginated access reconciliation, task permissions. Planning only; no code, builds, tests, API calls, or runtime validation.

## Evidence boundary

**Observed in brainstorm report** (`plans/reports/brainstorm-261021-1410-tabby-rush-task-assignees.md`): current task state is `todos` with parent linkage; native stores separate confirmed base/outbox/draft and account-fence async work; completed paginated scans can currently mark absent rows deleted; projector/store assume locally present parents; Hub row identity is user/table/row scoped; sync is standard `/push`, `/pull`, `/delta`, not strict sync. Requirements include one direct assignee, inherited parent access, child-only isolation, creator-only structure/assignment/delete, shared task fields, explicit conflicts, unknown-JSON and exact-ack preservation.

Everything below is a **recommendation**, not existing behavior or verified capability.

## Recommendations

### 1. Canonical local identity and mutation fence

- Key every canonical task as structured composite `(accountPartition, appId, tableName="todos", ownerUserId, rowId)`. Do not key by current actor + row ID, or assume row IDs globally unique. Keep `ownerUserId` from Hub's trusted target reference; never accept a client-selected owner as authorization. Use the same key for `todos`, its `taskAssignees` relationship, drafts, outbox, conflicts, reminders, and projections.
- Store assignments as versioned relationship rows in their own base/outbox/draft state, keyed by canonical task plus stable assignment-slot/relationship ID. One slot per task; reassign updates it, unassign removes it through normal versioned sync. Do not duplicate authoritative assignment in task JSON.
- Capture a monotonically changing local `accountGeneration` on every queued mutation and async sync result. Apply result only if account partition/generation still matches. Outbox entry carries canonical key, actor, exact `mutationId`, base version, and observed effective-access fence. Hub resolves canonical row and rechecks current effective permission on every push; fence is stale-context detection, **not** authorization. A permission denial is terminal for that mutation, distinct from version conflict; never retry it as a conflict or replay it after regrant.
- Preserve exact acknowledgements: acknowledge by mutation ID and canonical `(owner, table, rowId, serverVersion)`, not row ID alone. Keep Hub's unknown JSON merge and task-version conflict review unchanged.

### 2. Room migration and staging

- Add explicit sequential Room `Migration` path(s) for assignment, canonical-owner metadata/indexes, and any required visibility/staging metadata. Preserve existing private task rows; migrate them as unassigned/private. No destructive fallback, reset, or inferred assignment. Use Room's migration path rather than schema export auto-migration where relationship semantics or backfill need hand-written logic.
- In each Room migration transaction, add tables/columns/indexes and retain account partition boundaries; test populated prior-version DBs and existing user data on Android. Do not copy remote tasks or synthesize assignee rows during schema migration. Desktop should follow the same logical key and migration invariants in its own store.
- Keep confirmed base, outbox, and draft distinct for both `todos` and `taskAssignees`. Stage paginated full-scan pages in a generation keyed by account + scan/snapshot token; validate page ordering, token/revision consistency, and completion. Publish task and assignment base updates together in one Room transaction only after a valid completed generation. A failed, interrupted, malformed, or mixed-generation scan discards staging and leaves active base/visibility untouched. Never overwrite a newer local draft/outbox with scan data.
- Incremental delta events may commit transactionally page-by-page only when their cursor is contiguous and every visibility transition is explicit. Do not equate absence in a non-snapshot page sequence with deletion or lost permission.

### 3. Access reconciliation handshake on standard sync

Extend standard envelopes and cursor semantics; no separate service or strict-sync protocol:

1. On startup/reconnect, freeze outbox replay. Send `/pull` or `/delta` with last committed data cursor **and visibility/access cursor**. Server returns ordinary canonical task/assignment records plus explicit access events, a monotonic visibility revision/cursor, and either a stable snapshot token for paginated scans or a contiguous event-stream watermark. Bind every page to the same token/revision; reject mixed revisions and restart staging.
2. Server emits distinct `access_granted`, `access_revoked`, and actual task tombstone semantics (names illustrative). Revocation is a server-authored event for a canonical task only when its **effective** permission changes from allowed to denied. Direct grants and inherited parent grants are a union: removing one source causes no loss while another source remains. A parent revocation removes child access only for children without direct grants. A child grant survives loss of parent assignment. Delta cursor must preserve ordering/no-gap between pages; a completed legacy-style scan without snapshot/event watermark proves nothing about current access.
3. Commit contiguous access events and cursor atomically with corresponding `todos`/`taskAssignees` base changes. A scan may backfill grants, but only explicit revocation events (or a server-confirmed complete snapshot tied to a visibility revision and followed through its watermark) may invalidate access. The server must serialize grant/revoke changes with event revision so changes during paging are delivered after that revision rather than lost.
4. After reconciliation, replay queued mutations in original order. Hub re-authorizes each canonical write against current grants, creator status, version and field policy; reject revoked writes explicitly. If rights changed after the client's pull, that push-side check closes the race. On denial, remove mutation from replayable outbox and apply evidence-retention policy below. Then resume normal pull/delta and advance cursors only after durable local commit.

**Tradeoff:** snapshot-bound scans cost server state or event-log retention; explicit ordered access deltas avoid long snapshots but require durable cursor/event semantics. Choose the smallest additive envelope extension that existing standard sync can carry. Do not make a client infer permission from table scan completion. If Hub cannot provide either coherent snapshot/watermark or explicit ordered access events, fail closed: no revocation based on scan absence, no enabling shared assignments.

### 4. Revocation, regrant, and retained evidence

- Model access loss separately from deletion. Revocation removes task from accessible active base/projection; it MUST NOT set the task's `deleted` tombstone or permanently suppress its canonical key. Actual deletion remains a distinct creator-authorized tombstone. Regrant upserts the same canonical owner/task identity and current version; it must not create a recipient-owned copy or require restoring a deleted local row.
- On committed revocation, atomically mark visibility inaccessible, block queued writes and remove task/assignment content from active UI queries; then invalidate reminder scheduling and Android widget/desktop sticky projections. Cancel alarms/reminders for that task; remove or refresh widgets/sticky views. Every widget/sticky action must resolve the current canonical row and check access again before mutation—cancellation/UI hiding alone is not a security check.
- Recommended privacy default: erase revoked server-confirmed content from normal cache/projections and discard unsent shared-task payloads from replayable outbox. Retain only minimal review/audit evidence (canonical opaque key, mutation IDs, timestamps, last base version, denial/revocation status); no titles, bodies, assignee names, reminder data, or remote task snapshot. If product chooses recoverable local drafts instead, quarantine encrypted user-authored deltas outside normal task UI with explicit expiry and explicit user export/review; never auto-replay, and clear on account removal. This favors confidentiality over recovery of offline edits; notify user that unsent changes were not delivered.
- Offline clients cannot be remotely erased until reconnect. Clearly separate that limitation from server enforcement: server denies writes after revocation commit; client applies revocation at first successful reconciliation.

### 5. Projection and edit policy

- Full parent/group access (creator or parent assignee): show parent and currently authorized children; normal parent completion calculation may use the full authoritative child set. Parent-assignee may edit existing task content, due date, completion, and supported reminder fields on parent and live children, but not assignment, ownership, delete/restore, or child creation.
- Child-only assignee: project the assigned child using a partial/missing-parent reference, never fabricate a full parent record, fetch private parent title/content, or reveal siblings. Allow edits to that child's shared content/deadline/completion/reminder only. Do not derive or write parent completion from a visible subset; only creator/full-group projection with complete child coverage may compute it. This also removes the existing requirement that a parent row be locally present to edit a child.
- Creator alone can assign/reassign/unassign, delete/restore, and create children. Enforce at Hub on every write/tombstone as well as hide native controls. Assignment does not grant app membership. Existing editable task fields remain shared; no per-user completion copies.

### 6. Compatibility and fail-closed rollout

- Preserve legacy viewer-only SharingConfig defaults. Require a positively negotiated client capability/contract version before serving collaborative task rows or accepting shared-task edits; unsupported/ambiguous configuration or missing canonical owner reference means deny, not downgrade to ordinary user-owned upsert. Older clients must not receive records they would mis-key, expose incorrectly, or overwrite. Enable assignments only after compatible Android and desktop clients and server policy are available; do not rely on unknown-JSON retention as access control.
- If a client lacks relationship-table or partial-hierarchy support, fail closed for assignment visibility and writes while retaining ordinary private-task behavior. Preserve unknown JSON and exact acknowledgements for capable clients. Existing unrelated app sharing stays viewer-only.

## Acceptance scenarios (Android + desktop)

| Scenario | Required observable result |
|---|---|
| Upgrade populated old Room DB with private tasks, drafts and queued edits | Migration preserves all; all tasks stay unassigned/private; no reset or duplicate canonical rows. |
| Creator assigns parent while recipient has old cached data; pages complete | Task and relation become visible coherently; parent assignee sees existing and future children; same owner/task key on both clients. |
| Child-only assignment, no parent cached | Assigned child opens/edits without parent contents or sibling identifiers; parent completion is untouched. |
| Parent and direct-child grants overlap; revoke either source | Access remains where the other grant still authorizes; only effective loss hides a record. Regrant returns original live row. |
| Offline recipient edits, then reconnects after revoke | Pull/delta reconciliation precedes replay; server rejects stale mutation; no resurrection; evidence policy applied. If revoke races after pull, push authorization still rejects. |
| Pull/full scan interrupted, failed page, mixed token/revision, duplicate/out-of-order page | Staging discarded; no revocation, deletion, projection removal, or outbox replay from incomplete state. |
| Actual task deletion vs access revocation | Deletion tombstone remains distinct; revocation does not become deleted=true; regrant only restores a live task. |
| Child/parent content edit across devices; concurrent version conflict | Shared canonical row converges or explicit conflict review; no recipient-owned duplicate; unknown JSON and exact acknowledgements retained. |
| Account switch while sync/reminder/widget work is pending | Old generation results cannot enter new partition; stale widget/sticky actions fail authorization. |
| Revoked task has reminders/widget/sticky projection | On reconciliation reminders canceled and projections removed/refreshed; direct action paths cannot mutate. |
| Legacy client, malformed mapping, absent owner reference, forged assignment/tombstone | No shared rows/writes or privilege escalation; ordinary viewer-only clients retain old behavior. |

## Sources

- Brainstorm evidence, paths, current behavior and requirements: [TabbyRush task-assignees brainstorm](../../reports/brainstorm-261021-1410-tabby-rush-task-assignees.md) (source inspection stated there; not revalidated here).
- Android Developers, [Migrate your Room database](https://developer.android.com/training/data-storage/room/migrating-db-versions): explicit migration paths and preserving data across schema versions; use as the Room implementation reference.
- Android Developers, [Offline-first data layer](https://developer.android.com/topic/architecture/data-layer/offline-first): local source of truth and queued writes/reconciliation patterns; supports retaining local/base and outbox separation, not permission inference from scan absence.
- OWASP, [Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): enforce authorization server-side, deny by default, and check each request; client UI/cache is not an authorization boundary.

## Unresolved questions

1. Hub contract: exact additive envelope names/shape for canonical owner references, visibility cursor/revision, snapshot/event watermark, and access-revoked acknowledgements; can Hub guarantee ordered access events with no cursor gaps?
2. Rollout: what minimum client capability/version is available in existing sync envelopes, and how will assignments be withheld from older clients until both native clients are compatible?
3. Product retention decision: accept immediate deletion of unsent edit payloads on revocation, or require encrypted, time-bounded local-only draft quarantine and its expiry period?
