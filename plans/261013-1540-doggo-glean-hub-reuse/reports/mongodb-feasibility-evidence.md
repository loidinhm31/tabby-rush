# MongoDB feasibility evidence
Date:2026-10-13. Parent read current official documentation; no Mongo command/test/install or live connection performed.

## Capabilities documented
- [Atomicity](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/): single-document writes are atomic; include expected current value in filter, and change that value on every accepted mutation. A separate preflight read alone is not CAS.
- [Transaction prerequisites](https://www.mongodb.com/docs/manual/core/transactions-production-consideration/): standalone deployments do not support multi-document transactions. Receipts+row+feed in separate documents need supported transactional deployment, not a default assumption.
- [Write concern](https://www.mongodb.com/docs/manual/reference/write-concern/): explicitly configured majority/journaling controls acknowledgement durability. A write-concern timeout does not undo a write; client must retain uncertain state. Journaled standalone acknowledgement is not replica-set redundancy/failover protection.

## Design consequences, not runtime proof
Per-row exact-revision CAS plus last-operation metadata and permanent compact deletion fence in the SAME document can avoid multi-document transaction requirements. Insert-only creation needs unique identity index; ordinary update/delete must never upsert. Store server-assigned revision and operation identity atomically. Once last-operation metadata is superseded, historical causal acknowledgement is not recoverable from that field alone; report uncertainty rather than invent receipt history.
Separate operation receipts/change feed with atomic row mutation require a larger design and deployment proof. Incrementing a sequence before an unrelated write can leave holes/late-visible writes and is not a safe committed cursor. A timestamp or last-op field alone does not provide snapshot isolation, durable history or exactly-once delivery.
Durable deletion fences remove payload but retain identity/revision for stale-write rejection; they grow with lifetime deletes. Purging them without replica acknowledgements/expiry/reset protocol weakens the guarantee. Stable-key full scans converge after quiescence but are not snapshots.

## Evidence limit
Inspected Hub source can establish extension points, not deployed Mongo version/topology/journal/write concern/indexes. Existing health probes failed and were not rerun. Feasibility must be labeled architectural until isolated Mongo/route/old-app regression gates run during implementation.

## Unresolved prerequisites
Actual approved Hub/Mongo topology/version/write/read concerns, index state and operator recovery/restore process. No secret configuration is needed to state these requirements.
