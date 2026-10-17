# Validation summary — completed decisions
Date:2026-10-13. Planning interview; no implementation authorized. [Current plan](../plan.md) · [Feasibility report](strong-sync-feasibility.md) · [Authoritative decisions](design-decisions.md).

## Interview sequence
1. User opted into brief validation interview.
2. Three product-risk questions:
   - Stronger sync: report feasibility first; retain current behavior if infeasible.
   - Admin account: allow with warning/recommend ordinary account.
   - Logout: local-only wipe accepted, no remote revocation redesign.
3. Parent+two parallel analysis slices assessed per-row strict CAS versus receipt/snapshot design and verified Mongo primary documentation.
4. User asked whether strict option requires embedded client updates. Parent located actual `/home/loidinh/WS/glean-hub/embed-app/`,read shared client endpoint construction and embedded adapter usage. Answer: no for opt-in TabbyRush-only extension; existing appIds/routes/DTOs/core clients unchanged. Their later opt-in would require separate migration,not a flag-only change.
5. User explicitly selected **Adopt TabbyRush-only strict sync**. Compact permanent deletion markers,explicit unknown-outcome review and eventual non-snapshot scans accepted. Receipt/feed/snapshot design not selected.

## Technical refinements, not new product scope
Separate admin `/strict-apps` to fail closed on old server; strict-local typed errors; intrinsic `_id=userId:rowId`; active epoch/schema checks before namespace use;per-app collection admission. Real durable replay confirmation. Recovery script+same-code Rust admission helper,maintenance/fresh epoch/journaled registry CAS and ambiguity reconciliation. No shared auth/client migration/legacy sync rewrite.

## Plan revision requirements
All affected architecture/contracts/phases/ownership/dependencies/acceptance/effort revised to strict A. Baseline100h superseded by148h estimated effort (±25–35%),ideal critical path96h excluding waits/rework. All implementation pending0%;first step Phase01 tooling. Existing embedded apps explicitly excluded from code ownership;compatibility tests remain release gates.

## Limits and remaining prerequisites
No successful live Hub/Mongo authentication/sync/native proof exists. Public health502/localrefused previously observed;no rerun to confirm. Exact Mongo/journal/index/topology/version,operator registration/key/grants/recovery,stable toolchain/Xpra/vault/font/signing and Windows machine remain runtime prerequisites. Required gate failure means report/replan;never silent fallback to weak protocol. No unresolved product choice for selected strict A.
