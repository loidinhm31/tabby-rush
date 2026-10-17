# Phase 03 — D1 schema and migrations

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Planning brief](reports/planning-brief.md)

## Parallelization Info
After02; group C with04/05/06. No dependency on04 package/harness for own SQL test. Worker live D1 gate waits03 SQL land.

## Overview
- Date: 2026-10-13
- Description: D1 schema and migrations.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 3h

## Key Insights
D1 is cloud SQLite behind Worker, not client-local DB. Schema/version/index decisions are frozen; upsert would overwrite collision and is forbidden.

## Requirements
Exact single table and two indices from contracts; canonical UUID validation in Worker, scalar title validation in shared/Worker, SQL shape constraints. Append-only migration and reproducible local/remote runbook.

## Architecture
0001-create-todos.sql owns SQL only. node:sqlite test opens isolated temporary SQLite DB, applies real migration, asserts constraints/indices/query order/conditional atomic behavior. D1-specific behavior covered04 local and08 deployed.

## Related code files
Future create only; no existing source modified or deleted:
- `worker/migrations/0001-create-todos.sql` — create.
- `worker/test/migrations.test.mjs` — create.
- `docs/d1-schema-and-migrations.md` — create.

## File Ownership
Only exact paths listed above and phase03 rows in registry; exclude every other file. Machine-local/generated artifacts ignored, not tracked. Reopen original owner for out-of-scope changes.

## Implementation Steps
1. Copy frozen schema SQL into owned migration (no IF NOT EXISTS to hide incompatible schema drift). Validate primary key NOT NULL, completion0/1, integer version range, time monotonic check, title1..200 and two list indices. No user_id or tombstones for private scope.
2. Write permanent Node24 `node:test`/`node:sqlite` test in worker/test/migrations.test.mjs with no npm imports. Apply real SQL to isolated temporary DB; insert valid UUID fixtures, duplicate ID must fail and leave original title/version intact. Assert constraint violations, descending same-time tie order, active/completed page predicates, conditional update increments and stale delete does not remove.
3. Inspect EXPLAIN QUERY PLAN for indexed unfiltered/filtered pagination without overfitting exact explain wording. Prove expected data result; no implementation-text test. Test title scalar edge via JS/Kotlin owner tests, not SQLite alone.
4. Runbook commands executed later from worker directory using04 pinned Wrangler: `npx wrangler d1 migrations list tabbyrush-db --local`; `npx wrangler d1 migrations apply tabbyrush-db --local`. Remote is **separate**, explicit account/env check: `npx wrangler d1 migrations list tabbyrush-db --remote`; backup08 first; `npx wrangler d1 migrations apply tabbyrush-db --remote`. Never assume local application deploys schema or use unspecified default environment.
5. Registry excludes wrangler.jsonc/package manifests;04 declares migrations_dir and test:migrations script invoking owned node test. Node test can run directly `node --test worker/test/migrations.test.mjs` without04. Main07 executes all lane tests once.
6. Document data mapping, cursor index rationale, no snapshot guarantee, migration history/forward-only correction. Later schema change needs new owned path/approved contract, not rewriting applied migration. Backup restoration not equivalent to safe downgrade.

## Todo list
- [ ] Complete required owned behavior and permanent tests/runbooks.
- [ ] Record designated gate evidence; resolve failures through original owners.
- [ ] Review frozen contract and exclusive ownership; no runtime placeholders.

## Success Criteria
Migration exactly matches frozen schema; real SQL constraint/order/conditional tests ready and run at07; Worker migrations path/config remain owned04; local/remote commands explicit.

## Conflict Prevention
No edits outside listed rows, including catalog/build/lock/docs/tests. New paths require registry amendment first. Integration defects reopen owner, then rerun affected gate.

## Risk Assessment
SQLite standalone is not D1 proof; require real Wrangler/D1 tests. Remote migration destructive/irreversible changes must stop until backed-up rehearsal, not roll back by deleting history.

## Security Considerations
Prepared API writes controlled04, not arbitrary client SQL. Migration/backup SQL may contain private titles; secure backup directory and never commit it.

## Next steps
Publish SQL to04;07 runtime gate then08 remote migration/backup gate.

## Unresolved questions
Cloudflare account/DB ID not required for SQL coding; remote access is08 prerequisite.
