# Phase 02 — Complete runtime dispatch and receipt compatibility

Priority P1 · Pending · 5h · Depends on phase 01.

## Context and architecture

`P/src/services/strong_sync_service.rs` owns admission, bootstrap, push, row CAS/replay, pull and receipts. All paths use existing permits and schema/admission checks. Preserve this architecture; a protocol allowlist patch alone exposes unsupported mutations.

Frozen relationship contract: roots have null linkage, children refer to a live same-user root when freshly created, links never change, depth is two. Existing migrated roots remain eligible parents. Replay proof precedes current-parent checks; child deletion remains legal after root deletion. Parent deletion does not authorize a hidden server cascade or provide a multi-row atomicity guarantee.

## File changes

| Action | Path | Purpose |
|---|---|---|
| Modify | `P/src/services/strong_sync_service.rs` | V3 admission, payload/digest dispatch, persistence, parent checks, receipt dispatch |
| Modify as needed | `P/src/models/strong_sync.rs` | Preserve `lastOp.contractVersion` in typed document paths; update affected literals/callers if field added |
| Modify as needed | `P/src/models/strict_upgrade.rs` | Correct receipt documentation/version handling; keep existing batch-level `protocol` wire shape |
| Modify | `P/src/services/strong_sync_tests.rs` | Non-network runtime helper/schema tests |
| Modify | `P/src/routes/strong_sync_route_tests.rs` | Protocol error mapping and route-scope regression assertions |
| Documentation-only touch as appropriate | `P/src/services/app_service.rs` | Fix v1/v2-only comments where v3 schema validation already exists |

Do not change upgrade behavior, schema fingerprints, collection names or epoch. `strict_upgrade_service.rs` is read-only context for preserving legacy evidence, not an invitation to remediate unrelated migration findings. No obsolete v1/v2 branches: those remain supported contracts on unmigrated applications.

## Sequenced implementation

### A. Admit only actually implemented versions

1. Import v3 model/digest and existing `PROTOCOL_V3`. Extend `authorize_strict_access` with explicit v3 arm and truthful unsupported-mode message; unknown/legacy modes still reject.
2. Preserve per-request schema validation for active mode, expected epoch checks, collection/index/topology admission and permit acquisition. Do not bypass these to get bootstrap green or cache a guessed v3 mode.
3. Bootstrap returns configured protocol/epoch/todos as today. Push and pull continue requiring request protocol equal active mode. V1/v2 request mutation on v3 app remains `protocolUnsupported`; do not rewrite its request protocol or downgrade the schema.

### B. Parse v3 and preserve the complete payload

4. Add explicit CREATE/UPDATE/DELETE v3 dispatch in `apply_operation` using phase-01 functions. CREATE requires revision 0; UPDATE/DELETE require positive expected revision; envelope maximum/UUID/table/epoch/title/createdAt checks remain enforced. CREATE/UPDATE data ID must equal row ID. DELETE cannot carry a task payload.
5. Serialize all v3 fields to BSON, including explicit null/string `parentTaskId`. Keep legacy bytes and validation paths untouched. Carry parsed parent identity locally for relationship checks; do not repeatedly deserialize an untrusted map or drop the field during BSON conversion.

### C. Replay-first, then fresh relationship admission

6. Preserve the order: validate operation and derive digest → load scoped target row → validate revision/lastOp integrity → exact `(opId,digest)` durable replay confirmation → existing fresh-operation CAS/deleted/missing classification → fresh relationship constraints → write. Same opId with a different digest remains `opIdReuse` under established semantics; never acknowledge changed parent data as a replay.
7. For a new v3 child CREATE, lookup authoritative parent in the same app collection and authenticated user namespace. Require `tableName = todos`, matching parent row identity, `deleted = false`, and live data with explicit `parentTaskId = null`. Include explicit user/table checks rather than trusting a client-supplied identity. Another user's matching rowId must not satisfy the reference. Missing parent, tombstoned parent, child-as-parent, corrupt parent data and non-root parent fail closed; semantic invalidity uses `invalidRecord`, while database/topology failure stays retryable/internal rather than a false missing-parent assertion.
8. For a fresh v3 UPDATE, compare submitted parent identity with authoritative stored live-row identity. Reject root→child, child→root and child→different-parent changes. Preserve existing createdAt immutability and full-replacement semantics for that row's own other fields. Do not require renewed parent liveness for an unchanged-link UPDATE unless the frozen contract changes: specified backend admission is fresh child CREATE plus immutable UPDATE. Existing client dispatch gating is not a stronger server relationship guarantee.
9. Do not run fresh-parent lookup before exact replay. Accepted child CREATE → lost response → root DELETE → exact retry must return durable `replayed`, with original accepted revision. A distinct new CREATE after observed root deletion rejects. Child DELETE remains legal with the parent missing/deleted and leaves the normal empty tombstone.
10. Root/child operations may legally share a batch; do not reject a whole batch merely for containing related distinct rows. Preserve unique-row restriction and independent outcomes. A prior accepted root in an ordered batch may satisfy a later child lookup; no implicit batch dependency, rollback or cross-row transaction is promised. Lookup versus concurrent root-delete races remain possible; client tombstone dominance and acknowledged per-row child cleanup implement convergence.

### D. CAS, durable acknowledgement and pull

11. Stamp `lastOp.contractVersion = strict-v3` on every new accepted v3 CREATE, UPDATE and DELETE. Keep v2 stamps and historical v1 missing-version convention intact. If typed `StrictLastOp` gains an optional version field, migrate all constructors/consumers and preserve absent v1 serialization rather than fabricating a historical version.
12. Keep insert-only CREATE, revision-matching live UPDATE/DELETE, `acceptedRevision = expectedRevision + 1`, monotonic safe revision limits, `lastOp.acceptedRevision == revision`, and confirmationNonce durable matched-write requirement. Do not return acceptance on read-only replay observation, write error or zero matched rows. Review concurrent-write reread/confirmation paths for the same v3 integrity/version evidence as the normal path; do not introduce a weaker fallback.
13. Exact replay/receipt proof uses stored original contract evidence; conflicting explicit versions must not prove acceptance. Missing `contractVersion` remains historical v1 compatibility only, not proof of v2/v3 acceptance. Do not recompute old digests from an enriched current row or stamp old evidence v3 during confirmation.
14. Pull already transports BSON `data` as JSON; ensure the v3 live field survives end-to-end and root null is not omitted. Preserve user/table filtering, simple-collation immutable-row ordering, bounded lookahead, cursor scope/protocol checks and all tombstones. Do not filter orphaned children or hide tombstones server-side; client full scans need those records. Never repair malformed stored v3 payloads by manufacturing parent null or `{}` as a successful live task.

### E. Original-version receipts, not legacy write authorization

15. Extend `legacy_receipts_inner` to accept original-version v1/v2 batches, dispatching each batch by request `protocol`. Keep wire shape homogeneous: `LegacyReceiptItem` currently has no per-item version. V1 uses original v1 DTO/domain/digest; v2 uses original v2 DTO/domain/digest, including all temporal fields. Revalidate kind/revision/data shape, ID/title and digest before proof. Unsupported receipt protocols and mismatched data/version reject; do not parse a v2 receipt as v3 or add parent enrichment to frozen data.
16. Authorize active app/epoch with the common gate, including a v3 active app, independently of original receipt domain. Preserve permits, user scoping and existing outcomes: `provenAccepted`, `provenNotAccepted`, `unknownSuperseded`, `integrityFailure`. Exact matching lastOp proof requires correct version and revision invariant plus durable confirmation; absent/superseded evidence never becomes fabricated acceptance.
17. Receipt processing may update only the established durable confirmationNonce for matching proof; never apply CREATE/UPDATE/DELETE, alter live payload/revision/epoch, or grant old-version push permission. Even receipts are not read-only operations and must not be tried against the live cluster in this task.
18. Preserve v1-on-v1/v2 compatibility; add v1/v2 reconciliation on active v3 without accepting newer-version receipts against an older active contract. A frozen v3 operation is replayed on ordinary v3 push, not a new legacy receipt mode.

## Acceptance and risks

- Fresh semantic rejections cannot be swallowed as successful acknowledgement or broad permissive fallback.
- V1/v2 app behavior, immutable canonical bytes and current receipt status meanings remain intact.
- Cross-account lookup, replay-after-parent-delete, all three version stamps, no-parent DELETE and concurrent CAS paths have targeted tests in phase 03.
- Existing upgrade dry-run/fence/null-validation findings remain release holds; this runtime repair does not establish migration safety or authorize rerunning it.

Next: isolated validation then source-grounded docs. No implementation/validation executed here.

## Unresolved questions

No product decisions. Current client transition code quarantines unresolved v2 operations instead of dispatching v2 receipts; do not expand this server assignment into a client-state-machine rewrite or claim it heals existing client conflicts automatically.
