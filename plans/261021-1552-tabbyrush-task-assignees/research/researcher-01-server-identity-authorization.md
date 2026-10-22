# Server identity, assignment integrity, and compatibility

**Research timestamp:** 2026-10-21 15:54 Asia/Bangkok  
**Scope:** TabbyRush/Hub, based on the supplied brainstorm and MongoDB/authorization references.  
**Evidence boundary:** Brainstorm source inspection is summarized as reported evidence below; I did not inspect implementation sources or execute runtime checks. All design choices are recommendations, not source/runtime proof.

## Recommendation

Extend Hub's existing `SharingConfig` and `/push`, `/pull`, `/delta`; no app-ID branches, separate API, or general role engine. Add opt-in, validated policy describing owner-scoped canonical references, one stable task-assignment slot, task-child inheritance, and creator-only relationship/structural mutations. Absent policy must retain current viewer-only semantics. Assignment becomes usable only for clients that declare the needed capabilities; legacy clients continue ordinary private-task sync and never receive cross-owner task rows.

The critical correctness boundary is server authorization against canonical stored ownership and current access, not client-supplied owner identity or UI state. Serialize shared edits with relevant grant changes. For revocation, use the existing standard reconciliation routes to provide an authoritative visibility boundary; never interpret an incomplete/unstable scan as a revocation or task deletion.

## Evidence from supplied brainstorm

- Brainstorm records standard `/push`, `/pull`, `/delta`; current upsert uses caller `userId`; current identity is user/table/row scoped; SharingConfig already maps relationship table/recipient/target/child tables. Config alone does not enable editing. [brainstorm lines 38–47]
- Task content is `todos`; `parentTaskId` is hierarchy linkage. One assignee, parent-to-child access, creator-only assignment/structural changes, child-only privacy, and viewer-only behavior in other apps are established requirements. [lines 18–30]
- The brainstorm identifies owner-scoped ID collisions, same-table inheritance, stale-client/outbox handling, revocation-vs-deletion, and the absence of a coherent multi-page permission snapshot as unresolved design risks. It flags a possible self-grant path as inference only, not runtime-proven. [lines 44–49, 92–113]
- These are report facts, not independently re-inspected source claims. No API/UI/runtime behavior is asserted here.

## Concrete contract to plan

### Canonical row identity

Use a composite identity for every shared task: **`(ownerUserId, tableName, rowId)`**. Add `ownerUserId` as the minimal canonical-owner field to cross-owner task references in existing sync request/response DTOs. Hub derives and returns it from the stored row; on push it is only a selector, never authority. The route must resolve the exact stored owner/table/id tuple, verify app membership and current grant, then write that stored row. Reject missing/ambiguous owners for cross-owner operations; never fall back to the caller's row with the same ID. Keep creator ownership immutable and do not create recipient-owned content copies. Use the same composite key in native persistence and account partitions.

Alternative: opaque server-issued canonical row key avoids exposing owner IDs, but adds mapping/state. Prefer explicit `ownerUserId` unless IDs are sensitive or existing transport cannot represent it. Random UUID uniqueness is not an authorization boundary.

### One assignment slot, regrant, concurrent reassignment

Keep assignment as a separate ordinary versioned relationship row (`id`, canonical `taskId`, recipient `userId`, existing sync/deletion metadata); do not duplicate authority in `todos.assigneeId`. One durable slot per canonical task: creator changes the slot's recipient; unassignment tombstones the slot; regrant restores/updates the same slot. Do not physically remove it as part of normal unassignment.

Add a unique compound index for the configured relation collection over **`(appId, ownerUserId, taskId)`** (include relation discriminator/table if those share a collection). Uniqueness spans tombstones so duplicate historical rows cannot reopen a second grant. Validate `taskId` against a live task owned by that `ownerUserId`; validate recipient's app eligibility. If slot creation races, the unique index chooses one insert; reload and require the caller's expected version before updating, otherwise return a conflict. For an existing slot, conditional update filters on canonical owner, slot ID, expected version, and live state, then increments version. One concurrent CAS succeeds; stale attempts conflict, never last-write-wins access escalation.

MongoDB single-document writes are atomic; including the expected value in the update filter is its documented concurrency pattern. A unique compound index is the database backstop, not just application validation. On a sharded collection, check that the shard key permits the intended uniqueness scope before rollout; MongoDB unique-index restrictions can constrain the key shape. [MongoDB: atomicity; unique indexes]

### Config shape and safe defaults

Preserve existing relation/recipient/target mapping fields. Proposed additive JSON (names illustrative but exact semantics required):

```json
{
  "sharedContentWrite": false,
  "assignmentPolicy": {
    "targetIdField": "taskId",
    "cardinality": "one-per-target",
    "slotLifecycle": "stable-soft-delete",
    "relationshipMutation": "target-owner-only",
    "hierarchy": {
      "table": "todos",
      "parentField": "parentTaskId",
      "inheritance": "parent-to-children"
    },
    "ownerOnly": ["assign", "unassign", "reassign", "delete", "restore", "create-child"]
  }
}
```

`sharedContentWrite` absent/false must preserve today's viewer-only behavior. Assignment policy absent keeps legacy sharing behavior; do not infer edit rights from a relation row. Reject invalid/unsupported policies (including same-table hierarchy without explicit parent mapping) at registration/update time. Model only these declarative capabilities; do not add arbitrary role expressions or `if app_id == tabby-rush` logic. Update admin types, validation/help, and provisioning contract with this extension. Keep TabbyRush's policy opt-in; unchanged Cham-lang/other app registrations remain unchanged.

### Effective authorization and mutation ordering

For a task, allow read/edit when actor is canonical creator, directly assigned, or assigned its live parent; parent grant flows only downward. Union direct and inherited grants: revoking one must not remove the other. Child-only actors see/edit only the child; never fetch private parent/siblings to construct a synthetic parent. Authorize each row in push, pull, delta, tombstone/restore, and relationship writes. Creator-only operations include changing grants, delete/restore, create-child, ownership, and `parentTaskId`; reject attempts even if fields arrive in otherwise permitted content JSON.

A permission read followed by an independent content write is insufficient: revocation can commit between them. Recommended strict serialization: execute shared-content edit and every relevant grant/revocation mutation in a MongoDB transaction, updating a shared per-task-tree authorization fence/version in both transactions. The content transaction reads/revalidates direct+parent grants and writes the fence plus canonical task; the grant transaction changes the relationship and the same fence. On write conflict, retry transaction then reauthorize; if revoke won, deny stale edit; if edit won, it is ordered before revocation. For a child task, parent-level grant changes and child direct-grant changes must share that child's effective-access fence (tree-level fence is simplest, but serializes edits in that tree). Transactions need a transaction-capable MongoDB deployment (replica set/sharded cluster configuration and suitable read/write concerns); absent that capability, do not enable shared writes. Measure fence contention before widening scope; correctness first.

MongoDB documents transactions for atomic work across documents and collections; single-document atomicity alone cannot make a grant check plus separate task update indivisible. Transactional read/write fences are a design recommendation, not a MongoDB-provided authorization primitive. [MongoDB: transactions; atomicity]

### Reconciliation within standard routes

Define the server's standard `/pull` reconciliation as the authorization boundary: every page belongs to one server-defined snapshot/watermark, where both task visibility and relationship state are evaluated consistently. A completed reconciliation means “visible as of this boundary,” not a client's inference from arbitrary pages. Use existing cursor/scan metadata if available; extend its metadata rather than inventing a second sync protocol. `/delta` must report access-loss as an authorization/visibility transition distinct from content deletion; the client must not tombstone the canonical task as owner-deleted. Partial scans do not evict access. Every push independently rechecks live authorization, so a scan boundary is never a write credential. Preserve standard routes and existing version/conflict behavior. If Hub cannot provide a stable snapshot boundary with its current paging semantics, choose an explicit route-compatible cursor/watermark extension before enabling revocation-based local eviction; do not claim a multi-page scan is coherent by default.

### Older native clients

Unknown-field retention is not capability proof: old clients do not know composite identity, assignment rows, child-only projections, or access-loss semantics. Add a per-request capability declaration on existing routes (e.g. `owner-scoped-task-identity`, `task-assignment-v1`, `access-reconciliation-v1`). Server exposes cross-owner rows and accepts shared writes only when both feature policy is enabled and request capabilities satisfy it. Missing capability means legacy private-row behavior; never send shared rows to legacy clients, since they can mis-key or overwrite them. Capabilities are compatibility gates, not authorization grants. Roll out clients first, verify supported-client coverage, then enable the app's opt-in policy. Existing non-assignment apps remain viewer-only without requiring new client capabilities. If request-level capability negotiation is impossible, use a server-enforced minimum TabbyRush client version and accept that older TabbyRush sync must be denied for the feature; do not silently permit unsafe mixed-version operation.

## Must-prove security and integrity cases

1. Two creators have same `todos.id`; Alice's cross-owner edit resolves only the explicitly authorized creator tuple; absent, forged, or mismatched `ownerUserId` cannot read/write either row.
2. Recipient cannot self-grant by forging a relationship row; relationship create/update/delete and recipient changes require stored target creator authority; invalid/non-app user denied.
3. Concurrent first assignment and concurrent reassignment produce one slot/one direct recipient; stale version returns conflict; unassign/regrant restores the same task and slot.
4. Concurrent edit/revoke ordered by the fence: edit before revoke may commit; revoke before edit causes retry/denial. No stale authorized check may commit after revocation commit.
5. Direct child grant without parent access exposes no parent/sibling fields; parent grant reaches current and future children; direct and inherited grants compose independently under either revocation order.
6. Assignee cannot mutate identity, owner, assignment, `parentTaskId`, delete/restore, or create child via content payload, tombstone, or any standard route.
7. Partial/paginated scan cannot revoke; completed stable reconciliation removes only inaccessible projections/reminders, not owner-deleted rows; queued edits after revocation fail server-side; regrant does not look permanently deleted.
8. Capability-absent/old client receives only legacy-safe records and cannot overwrite shared canonical records; other apps retain viewer-only semantics.

## References

- Supplied brainstorm, `plans/reports/brainstorm-261021-1410-tabby-rush-task-assignees.md`, especially §§2–5, 7–9.
- MongoDB Manual, [Atomicity and Transactions](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/) — single-document atomicity; conditional update filters for concurrent changes.
- MongoDB Manual, [Unique Indexes](https://www.mongodb.com/docs/manual/core/index-unique/) — compound uniqueness; confirm sharded index constraints for deployment topology.
- MongoDB Manual, [Transactions](https://www.mongodb.com/docs/manual/core/transactions/) — multi-document atomic operation support and deployment considerations.
- OWASP, [Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) — deny by default, check authorization on every request, least privilege.

## Unresolved questions

- What is Hub's actual MongoDB topology, shard key, transaction configuration, and retry policy? Must validate unique-index/fence feasibility before implementation.
- What cursor/snapshot/watermark semantics do existing `/pull` and `/delta` provide? Source inspection must settle whether existing metadata suffices or needs a route-compatible extension.
- Should canonical owner ID be exposed in DTOs, or does an existing opaque owner-scoped key fit better? Decide against actual DTO constraints.
- Which minimum native releases will declare all required capabilities, and what server policy will prevent premature feature enablement?
