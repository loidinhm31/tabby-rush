# Contracts, Ownership & Acceptance

Status: proposed implementation contract, 2026-10-20. No code delivered.
Roots: `D = G:/ws/sharing/doggo`; `H = G:/ws/sharing/glean-hub`.
Architecture: [design](../../docs/standard-sync-cutover-design.md). Evidence: [scouts](reports/scout-findings.md).

## Frozen API decisions

| Surface | Contract |
|---|---|
| Registry | `schemaVersion`: server-owned positive exact JSON integer, initial/missing baseline 1. Never a record column or strict protocol label. |
| Registration | Normal and strict registrations initialize 1; response includes 1. Unrelated normal apps keep their mode. |
| Admin read | Existing `GET /api/v1/apps/:app_id` adds schemaVersion; no write on GET. |
| Admin save | Existing `PUT /api/v1/apps/:app_id/schema`: existing schema/sharing body plus required `expectedSchemaVersion`. Success returns authoritative appId/schema/schemaVersion and existing sharing projection, not only a message. |
| No-op/retry | Same effective schema/sharing as current returns current state, without timestamp/counter mutation, even if expected version is stale. Invalid payload/precondition type still rejected. |
| Changed save | Conditional update covers app identity, ordinary mode and current version. Legacy absent-version+expected-1 branch publishes 2. Stale different content returns 409 with currentSchemaVersion; preserve caller draft. |
| Schema writer scope | Ordinary schema API and strict publication paths increment when actual table definition changes. Registration, conversion and direct maintenance scripts must not bypass counter rules. |
| Sync read/write | PushResponse and PullResponse gain schemaVersion from their schema snapshot; DeltaResponse carries existing nested responses with counters. Export TS types from Rust; update every impacted struct literal/consumer. |
| Native observation | `hubSchemaVersion`, no compiled exact match. Independent Room version, checkpoint and per-row version. A validation failure preserves local intent and displays actionable sync error. |

Semantic equality ignores map key order, retains column array order. Sharing updates retain existing omitted/null behavior; do not fix unrelated clearing semantics here. If sharing changes in a PUT it participates in the registry version as the same document contract. No-op comparisons include effective sharing. Prepare required indexes before publishing metadata where feasible; fail without claiming success if index setup fails. Detect safe-integer exhaustion before changing data.

## Standard task protocol

- Use existing push/pull/delta JSON shapes from `H/glean-hub-core/glean-hub-sync-engine/src/protocol.rs`; delta is not atomic.
- Native round: finish initial/resumption scan; submit one eligible operation per row; apply only explicit acknowledgements; drain pull pages. Ordinary incremental rounds may combine push/pull via delta after reconciliation.
- Per-row outgoing version is greater than confirmed standard version and remains frozen across a dispatch attempt. Always send assumedServerVersion; handle partial failure and concurrent local edits without blanket queue clearing.
- No application-level exact-revision or digest guarantees added to the server. Ambiguous acceptance requires convergence/review, not receipt claims.
- Preserve all 17 current task fields; schema file `D/integration/glean-hub/tabbyrush-schema-v3.json` is source for initial field names/types, not a compiled future-version allowlist.
- Unknown fields survive raw JSON persistence, local known-field edits, retry and conflict reapply. Adding a nullable field does not auto-render a native editor; adding required/type-breaking fields needs client/data evolution.
- Default retention horizon is currently 60 days. Before replay after a horizon-length gap, scan fully from null checkpoint; never recreate missing confirmed/uncertain rows. If deployment cannot provide a trustworthy horizon, use full scan before reconnect replay rather than guess. Do not interpret a partial scan as authoritative absence.

## Cutover decisions

- Maintenance isolates target app on all Hub nodes, including strict routes, standard routes, admin writes and background cleanup. Process-local lock or strict permit fence alone is insufficient for generic background jobs.
- Operator binary prepares a separate collection from a verified backup under an explicit database/app selector. Convert `_id` to ObjectId; retain `(userId,tableName,rowId)`, task data, deletion state and strict revision as standard version. Seed canonical string timestamps as expected by standard filters.
- Snapshot original registry/coordination/strict evidence; journal preparation and publication using one migration ID. Reject existing mixed or duplicate logical identities. Validate counts, payload equivalence, unique/checkpoint indexes and ownership before publication.
- Maintenance remains during collection swap and registry mode publication; they are multiple operations, not an atomic transaction. Clear mode using absence (not null), remove obsolete active strict metadata, preserve credentials/grants and schemaVersion rules. Journal current stage and prove restart can determine the active collection.
- No strict fallback transport. Upgraded offline clients retain old outbox evidence. Provably unsent intents rebase onto fresh standard state; old uncertain/superseded work is reviewable unless proven before conversion.
- Pre-publication rollback under isolation; after standard writes, forward repair or explicitly authorized reverse conversion, never blind snapshot restore.

## Exclusive ownership matrix

Each existing or new file has exactly one implementation owner. A newly discovered caller belongs to its root/layer owner; update this manifest before starting edits, never share a file across simultaneous agents.

| Phase | Exclusive write scope | Reads/dependencies |
|---|---|---|
| 01 Contract freeze | This plan directory except phase reports explicitly assigned below; no runtime code | Current architecture and scouts |
| 02 Hub backend | All affected H Rust sources/tests: registry/API/strict writer, core protocol/client/export, standard service/errors, new cutover binary and Mongo rehearsal tests; generated H sync-client-types index | Frozen contracts; no H admin UI edits |
| 03 Native migration | All affected D shared/androidApp/desktopApp runtime sources/resources/tests, Room schema export 4.json, integration schema artifacts/build config as necessary | Core protocol and frozen contracts; no D scripts/docs edits |
| 04 Admin UI | Affected H/glean-hub-app/src service/editor/pages/types/tests, UI-only package config if required | Frozen API and regenerated types; no Rust/generated index edits |
| 05 Verification/rollout/docs | D/scripts, D/tests script harnesses, D/docs, D/README.md; H/docs and H/README.md if present; rollout/evidence files in this plan | Completed 02/03/04; code corrections routed back to owning phase |

Phase report ownership: each owner may create only `reports/phase-NN-*.md` for its own phase; coordinator owns overview/status. Generated core TS index belongs only to 02, generated Room schemas only to 03. Preserve historical migration source schema exports 1–3. Strict runtime artifacts made obsolete in D are removed by 03 or 05 by scope; H strict engine remains for other apps.

## Acceptance matrix

| Concern | Required proof |
|---|---|
| Schema lifecycle | Baseline 1; changed→2; equivalent/no-op/retry unchanged; concurrent different saves one wins/one 409; no counter mutation on invalid/unauthorized saves. |
| UI/API | Real admin JSON paste/read-back displays version; stale editor retains draft with conflict; normal glean-hub app and converted tabby-rush use same editor. |
| Wire/storage | Existing generic consumers decode additive fields; explicit per-row ack only; correct checkpoint pagination, including ties, empty page and restart. |
| Native migration | Room 1/2/3→4 preserves tasks/drafts/ledger; unsent rebase preserves remote fields; uncertain legacy payloads never auto-dispatched. |
| Data conversion | Isolated Mongo clone, per-user same-row IDs, live/deleted records, original payloads/revisions, indexes, interrupted preparation/swap and repeat-run journal recovery. |
| Native UX | Android↔Desktop create/edit/complete/delete, hierarchy, last-child preservation, own deadlines, reminders, widget/sticky projection, cold restart and account switching. |
| Offline safety | Dropped push response, concurrent edits, partial ack/failure, unknown fields and reconnect after tombstone retention horizon. |
| Release | Real browser plus native UI evidence; authorized live target only after backup restore rehearsal; no accidental live DB use in checks. |

## Side-effect review checklist

- [ ] Exact target origin/database/app and maintenance scope authorized at live execution time.
- [ ] No secrets/logged tokens, copied live credentials or production `.env` test configuration.
- [ ] Backup restore actually rehearsed; journal handles every publication boundary.
- [ ] No concurrent old-node, old-client, admin or cleanup writes during conversion.
- [ ] No wipe, reinstall or destructive Room migration; unresolved work remains recoverable.
- [ ] No exact schemaVersion admission lock; no resurrection/blanket acknowledgement.
- [ ] Required/type-breaking schema edits documented as client/backfill changes.
- [ ] Existing strict apps and unrelated embed apps retain their behavior.

## Unresolved questions

None for design. Execution requires an isolated MongoDB URI/database and explicit live-target authorization; these are never inferred from the existing server environment.
