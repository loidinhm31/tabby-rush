# Stronger Hub sync — feasibility and decision report
Date:2026-10-13. Requested during plan validation. Architectural feasibility only; no implementation/install/runtime proof. [Design analysis](strong-sync-design-feasibility.md) · [Isolation analysis](strong-sync-isolation-feasibility.md) · [Mongo primary evidence](mongodb-feasibility-evidence.md).
Decision recorded: user selected **A,Doggo-only strict sync**, after confirmation existing embedded apps need no update. Preliminary comparison below is research history; current contracts/phase plan freeze exact implementation scope and148h estimate.

## Recommendation
**Feasible: choose isolated strict per-row sync (A)** if the product needs stale-write rejection, safe immutable retries and no accidental resurrection, while accepting explicit historical-outcome uncertainty. It reuses Hub authentication/schema registration and Mongo; no new backend or required Mongo transaction topology change. Existing apps retain their current protocol/storage. This is a real extension, not an8h patch.
**Do not promise exact historical outcome or snapshot sync under A.** If those are required, choose B; runtime topology and operational capacity must be verified before that design can be deployment-ready. Current-protocol fallback remains available if the selected stronger option fails its prerequisites; never silently downgrade a shipped strict client.

## Material alternatives
| Choice | Consumer-visible guarantees | Remaining limits / operations | Preliminary total effort |
|---|---|---|---|
| A: strict per-row sync (recommended) | Server revisions; exact base CAS; insert-only create; immutable retry at most one logical effect; retained deletion identity prevents implicit resurrection | Last-op history can be replaced: uncertain earlier result becomes explicit UnknownSuperseded review. Full scans eventual, not snapshot. Compact deletion markers retained indefinitely per epoch |136–160h (+36–60h over baseline)|
| B: receipts + ordered feed + snapshot | A plus causal accepted-operation lookup after later writes and consistent snapshot/feed reconciliation | Replica-set transactions, verified Mongo/driver snapshot support, retained receipt/feed storage and more involved recovery; no universal protection from stale backup/storage loss |180–228h (+80–128h), excludes topology migration/data import/operator waits|
| Existing baseline fallback | Current Hub conflict/equality/full-scan behavior plus narrow create fix | Admitted equal-version/purge races, no receipt history or snapshot |100h baseline|
All estimates are [INFERENCE] preliminary engineering ranges, not measured/calendar commitments. Existing uncertainty still applies. Runtime availability cannot be inferred from source or a URI scheme.

## Why A is feasible
Mongo [single-document expected-value updates](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/) support atomic row/revision/last-operation metadata without multi-document transactions. Strong row uses insert-only creation and exact expected server revision for edit/delete, never update-upsert. Tombstone/retired identity stays in that document. Client operation freezes opId/base/kind/payload; new explicit reapply is distinct from retry.
Lost response followed by another accepted edit can erase last-op evidence. Old frozen request still cannot mutate again because its expected revision no longer matches; causal outcome may remain unknown. Preserve intent for review, never silently call it rejected or resubmit against a new base. Replay acknowledgement needs durable confirmation, not an unjournaled visibility read. Replica and standalone policies are specified in design analysis.
Page full state including tombstones by immutable rowId, not syncedAt strings. Complete staged scans remain non-snapshot; retain higher confirmed revisions and late local acknowledgements. Same-epoch missing previously confirmed identity is inconsistency/reset evidence, not permission to delete/recreate. Epoch/reset and backup restore require explicit operator quiescence/new epoch, not restored stale counters.

## Existing-app safety
Use immutable opt-in schema mode, dedicated strong routes under existing sync auth/rate/body gates, and separate collection namespace outside legacy `app*`. Source inventory found legacy push/delta/purge/restore/cleanup and notification writes all reach legacy namespace; isolation prevents those writes changing strict rows/fences. Guard legacy routes for strong apps in new server; old binaries lack strong routes, so Doggo pauses rather than falls back. No Rust/TS client/admin-UI migration or global Mongo-default changes.
Registration must read back actual mode/protocol: older server silently ignores unknown fields. Provision strong indexes before admitting writes. Unknown mode fails closed for that app, not all Hub applications. No sharing/notification-generated rows/implicit restore in the selected personal todo scope.

## Required plan changes if A chosen
- Replace Phase03 narrow legacy patch with opt-in strong-mode/model/routes/service/index/provisioning/regression work; no need to repair legacy upsert solely for Doggo's isolated path.
- Expand03 exclusive external ownership explicitly; phase02 interfaces/epoch/error states, phase05 Room/protocol/reconciliation, phase06 UnknownSuperseded UI, phase07/08 runtime/rollback/restore gates all updated. Auth/logout choices unchanged.
- Keep dependency structure01→02→parallel03/04/05/06→07→08→09→10; coding lanes use frozen new contract. Recalculate exact phase estimates/critical path after choice.
- Require real isolated Mongo deterministic concurrency/retry/delete/epoch/index/legacy-bypass tests and existing-app smoke, then Android/Linux/Windows installed gates. Source analysis is not substitute.

## Verification and limits
Reviewed mutation/purge/restore/app registration/routes source and primary Mongo docs; two independent design/isolation reports. No successful live Hub/Mongo probe exists; previous public502/localrefused not retried. No code, configuration, schema, account or deployment changed.
Investigation exception: one subagent inadvertently inspected a secret-bearing config contrary to explicit instruction. No secret value appears in reports or informs this recommendation; agent removed the incidental topology hint and was instructed not to access it again. No values reproduced here.

## Decision resolved
User adopted A with unchanged embedded clients,permanent compact deletion identity and explicit unknown-outcome review. B not selected. Required runtime feasibility gates remain;failure must be reported before any scope fallback,never silent protocol downgrade. Final contract review refines registration into separate admin `/strict-apps`,intrinsic `_id` identity,per-request active epoch validation and real maintenance recovery script;[current contracts](../contracts.md) override preliminary design details above.
