# Phase 03 — Deterministic tests, isolated smoke and accurate docs

Priority P1 · Pending · 4h · Depends on phases 01–02. This section defines future checks for the main agent; none were run during planning.

## Files and execution ownership

| Action | Path | Purpose |
|---|---|---|
| Create | `P/src/services/strong_sync_v3_tests.rs` | Isolated service lifecycle, relationships, CAS/replay and receipts |
| Modify | `P/src/services/mod.rs` | Register new test module |
| Modify | `P/src/services/strong_sync_tests.rs` | Extend pure schema/admission helpers without activating live gate |
| Modify | `P/src/routes/strong_sync_route_tests.rs` | Scope guards and protocol/epoch error regressions |
| Modify | `H/docs/strict-sync.md`, `H/docs/CHANGELOG.md` | Runtime version matrix and Unreleased fix entry |
| Modify targeted sections | `H/docs/system-architecture.md`, `H/docs/codebase-summary.md` | New v3 module/runtime dispatch and preserved storage invariants |
| Modify targeted sections | `S/docs/strict-v3-cutover-runbook.md`, `S/docs/hub-schema-integration.md`, `S/docs/system-architecture.md` | Runtime acceptance prerequisite; precise server-versus-client receipt capability |
| Modify targeted sections | `S/docs/project-changelog.md`, `S/docs/project-roadmap.md` | Record repair and actual evidence; retain unrelated holds |

No change to `tabbyrush-app.kt`, app version, active schema fixture or migration scripts. Existing administrative-completion reports remain historical; link fresh evidence and clarify current gate rather than silently turning reported results into observed current-source success.

## 1. Offline deterministic acceptance

Use production parser/validator/preimage/digest functions, not duplicated implementations that trivially agree with themselves.

- Every v3 canonical valid/rejection vector; compare exact preimage, length and digest. Missing fixture fails.
- Compare checked-in Hub v3 fixture bytes with `S/integration/glean-hub/strict-v3-vectors.json`. Existing v1/v2 vectors must pass unchanged; do not silently skip cross-repository parity if S is unavailable.
- Cover serde unknown-field/wrong-type checks, optional versus explicit-null input policy, explicit-null BSON output, malformed/self/noncanonical parent, and full temporal validation/overflow.
- Cover v3 root marker and DELETE domain independently; v2 must still reject `parentTaskId` and produce original hashes.
- Pure schema tests prove v3 exact 17-column schema accepted and wrong type/nullability/extra/missing columns fail. Unknown protocol stays fail-closed.
- Keep digest-only vectors separate from service validity: a helper vector can describe bytes for an operation that would fail CREATE revision/parent admission at runtime.

## 2. Safe isolated service harness — prerequisite gate

The existing `strong_sync_tests.rs::test_strong_sync_live_mongo_acceptance_gate` reads `MONGO_TEST_URI`, connects, registers an app and drops its test database. A random DB name does not make a remote cluster safe. Do not use that test, set/use `MONGO_TEST_URI`, run `--ignored` indiscriminately, or load repository `.env` files.

Future main agent must first obtain authorization to provision a disposable testbed. Required harness design:

1. Newly started loopback-only MongoDB process/container with throwaway storage, random allocated port and no mounted project/host credentials. Prefer a single-node replica set to exercise majority+journaled confirmation. Do not connect the running Hub or reuse its database client/configuration.
2. Connection options are constructed from that harness-owned instance/port only. No production URI fallback, inherited URI use, DNS/SRV endpoint or arbitrary remote endpoint. Refuse to proceed if ownership/isolation is not established; this is a blocked integration gate, not a test pass.
3. Instantiate real `AppService`, `PermitService`, `StrongSyncService` against a uniquely owned synthetic database; seed only synthetic users, schemas, roots/children and historical lastOp evidence. Provision exact normal indexes/admission before runtime calls.
4. Mark service tests explicit opt-in if infrastructure is not available to default unit runs. Select only the new isolated test filter after provisioning. Do not change the ignored live gate to make it execute automatically.
5. Cleanup may remove only the harness-created database/storage/process; never broad database enumeration/drop or service restart. The operator's provisioning authorization must cover that scoped cleanup. No task titles, tokens, URI secrets or account content in reports.
6. Use direct service functions for behavior and persistence; middleware-only routes returning stub `ok` are not v3 runtime evidence. Existing route tests remain useful for scope/error regressions. No production storage abstraction solely for mocking MongoDB.

## 3. Required isolated test matrix

| Area | Cases and exact assertion |
|---|---|
| Admission/bootstrap | Valid published v3 schema returns v3 and unchanged epoch; unknown mode, malformed schema, missing admission/index/topology and permit fence reject rather than bypass |
| Basic CRUD | Root then child CREATE revision 1; UPDATE revision 2; correct digest/opId/rowId/status/acceptedRevision; all 17 persisted fields returned by pull, explicit root null and child parent retained |
| Existing roots | Seed migrated v2 root with explicit-null parent and original lastOp evidence; accept new v3 child without modifying root identity, revision, completed or temporal fields |
| Hierarchy | Reject self/blank/malformed parent, missing parent, tombstoned parent, another user's same rowId, child-as-parent; reject root→child, child→root and reparent UPDATE; valid same-link UPDATE succeeds |
| Persisted integrity | Missing/corrupt persisted parent is not silently treated as valid root/link; wrong explicit lastOp contract version and revision invariant cannot prove acceptance |
| Deletes | Root DELETE creates permanent `{}` tombstone; no automatic child mutation; child DELETE succeeds after root deletion; neither root nor child identity can resurrect |
| Lost response | Accept child CREATE, discard response, delete parent, retry exact frozen child CREATE: `replayed` with original revision and durable nonce write, no child revision increment; distinct fresh child fails |
| CAS/replay | Same opId/different digest or parent fails; stale UPDATE/DELETE conflict; missing row not upserted; duplicate CREATE insert-only; competing updates at one base yield one acceptance; zero-match/error confirmation never reports replay |
| Version evidence | New v3 CREATE/UPDATE/DELETE each stamp v3; v2 stamps unchanged; historical v1 absence preserved; exact retry works across service re-instantiation using persisted evidence |
| Pull | Small-page root/child/tombstone scan includes all rows without duplicates; immutable rowId cursor order; wrong protocol/user/app/table/epoch cursor rejected; no parent-driven filtering or invented tombstone |
| Batching | Duplicate same-row operations rejected; distinct root/child batch has independent results; one invalid child does not produce acknowledgement for rejected data or roll back unrelated accepted row |
| Receipts | Original v1 and v2 frozen receipts on active v3 yield each of the four established outcomes as applicable; exact evidence confirms only nonce, preserving payload/revision/version; wrong digest/data/version gives integrity failure/rejection; superseded evidence remains unknown |
| Protocol fence | V1/v2 push/pull requests on v3 fail; original-version receipt reconciliation does not grant mutation permission; newer receipt version on older active contract rejected |
| Legacy regressions | V1 and v2 apps separately bootstrap/CRUD/pull/exact replay, v1 receipts still work; own temporal data preserved and parent field not accepted by v2 |
| Scope/security | Authenticated user controls composite row namespace; header/path mismatch unchanged; wrong epoch and invalid ObjectId user fail before useful account data access |

For races, use deterministic barriers around relevant reads/writes where the harness permits, not sleeps. A parent-create lookup and root-delete race is not evidence of cross-row atomicity; assert that any accepted late child stays visible to scans and can be deleted normally, with no hidden server cascade.

## 4. Planned commands and evidence discipline

Only the main agent may execute checks after all edits land and safety prerequisites hold. Suggested offline checks, from S:

```text
node scripts/strict-v3-vector-runner.mjs
node scripts/strict-v2-vector-runner.mjs
cargo test --manifest-path ../glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked --lib
```

Before a broad Rust unit command, main agent must inspect test setup so default tests do not make unapproved connections. Use a child environment with inherited DB configuration removed without displaying its values. Run the new isolated suite separately, by its exact filter, only with the harness-owned database; do not append `--ignored` to a broad suite. Follow repository compile/lint/format requirements once on the integrated changes if explicitly authorized; no build/test was part of this planning assignment.

Record exact command, source revision, fixture path/hash comparison, totals and skipped tests. Skipped isolated tests are an open service gate, not runtime support. Node/Kotlin vectors prove canonical bytes, not backend service behavior. Isolated Mongo proof does not prove production topology, deployment or client conflict recovery.

## 5. Smoke exit criteria and deployment boundary

Required disposable-only smoke sequence:
1. Bootstrap valid synthetic v3 app.
2. Create explicit-null root, create linked child, update child's own data; validate exact acknowledgements and persisted values through paginated pull.
3. Simulate lost child response; delete root; replay the frozen accepted operation successfully; reject a distinct new child; delete existing child with ordinary CAS.
4. Reinstantiate services against the same disposable DB; confirm version/replay evidence, empty tombstones, unchanged epoch and cursor behavior.
5. Exercise original v1/v2 receipts on v3 while v1/v2 pushes remain fenced; run legacy-app regression subset.

No live signed-in desktop sync is authorized: even opening sync or requesting receipts may mutate the cluster. After source and isolated gates pass, hand the parent a repair candidate, not a statement that the running app is fixed. Deploying/restarting the connected server, exercising the real account, migrating, dry-running migration, rotating epoch, downgrading schema, deleting data or inspecting credentials all require a separate exact operator-approved action. Keep existing G2/G3/G5/G6/G7 and migration safety holds intact.

## 6. Documentation completion

- Hub changelog: describe runtime rejection root cause, distinct v3 codec/relationships/version stamps, original-version receipt support and legacy compatibility; distinguish code delivery from deployment.
- Hub strict-sync/architecture docs: supported runtime and receipt version matrix, replay-first ordering, immutable two-level relationships, per-row CAS/tombstones, cross-row race limit, testbed isolation instructions.
- Doggo runbook/integration/architecture: published schema is insufficient; require codec + runtime + isolated smoke evidence; clarify client v2 quarantine versus server receipt capability; retain migration dry-run and release holds. Fix touched stale claims only when grounded by implementation/evidence.
- Doggo changelog/roadmap: link this repair/evidence and mark actual acceptance status. Do not mark historical staging/live/desktop gates passed.

## Risks and unresolved questions

Highest risks: accidental remote test connection; allowlist-only false success; v2 bytes altered by DTO reuse; replay intercepted by new parent lookup; old digest overwritten during enrichment; fixtures conditionally skipped. Mitigations are the hard safety gate, complete service matrix and exact canonical comparisons above.

No product questions. Remaining execution prerequisites: approved disposable MongoDB harness; separate deployment/real-account authorization. If those are unavailable, complete source/unit work and report isolated/live gates explicitly unexercised—never substitute mock-only proof.
