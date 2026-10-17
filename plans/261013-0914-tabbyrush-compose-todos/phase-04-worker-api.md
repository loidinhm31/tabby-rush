# Phase 04 — Worker API and local D1 backend

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Planning brief](reports/planning-brief.md)

## Parallelization Info
After02 code; group C with03/05/06. Local D1 tests/runtime require03 SQL. All Worker manifests, lockfile, Wrangler/harness owned04; no SQL edits.

## Overview
- Date: 2026-10-13
- Description: Worker API and local D1 backend.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 8h

## Key Insights
Private high-entropy session token is proposed; all holders share dataset. Research upsert/LWW/deprecated credential advice superseded. Runtime auth primitive must be documented supported, not invented.

## Requirements
Every frozen route/status/error/validation/cursor rule; auth-before-D1 and fail-closed missing secret; conditional version SQL. No retries, public write API, CORS-as-auth or unknown mutation overwrite.

## Architecture
Small modules auth/validation/cursor/store/errors/index; pinned TypeScript/Workers types/Wrangler/Vitest pool and Node24 patch, package scripts test and test:migrations. DB binding uses configured local DB then real08 binding IDs. No credentials in config.

## Related code files
Future create only; no existing source modified or deleted:
- `worker/package.json` — create.
- `worker/package-lock.json` — create.
- `worker/tsconfig.json` — create.
- `worker/wrangler.jsonc` — create.
- `worker/vitest.config.ts` — create.
- `worker/src/index.ts` — create.
- `worker/src/auth.ts` — create.
- `worker/src/validation.ts` — create.
- `worker/src/cursor.ts` — create.
- `worker/src/todo-store.ts` — create.
- `worker/src/http-errors.ts` — create.
- `worker/test/auth.test.ts` — create.
- `worker/test/validation.test.ts` — create.
- `worker/test/crud.test.ts` — create.
- `worker/test/pagination.test.ts` — create.
- `docs/worker-development.md` — create.

## File Ownership
Only exact paths listed above and phase04 rows in registry; exclude every other file. Machine-local/generated artifacts ignored, not tracked. Reopen original owner for out-of-scope changes.

## Implementation Steps
1. Create package.json/package-lock.json/tsconfig and Workers Vitest harness with compatible pinned Wrangler/worker types. Scripts `test` runs Workers tests against real local D1, `test:migrations` runs03 node test. Freeze compatibility in worker-development.md; package installation later `npm --prefix worker ci`. Harness reads03 migration using supported migrations setup API, not duplicated SQL strings.
2. Configure wrangler.jsonc name tabbyrush-api, documented current compatibility_date, binding DB/database_name tabbyrush-db/migrations_dir migrations, workers_dev access as explicitly chosen. At initial local-only stage omit remote database_id if current Wrangler supports local binding; otherwise acquire real DB ID with account prerequisite—never fake ID claiming production-ready. Before08 deployment reopen04 to set real account_id/database_id (identifiers, not secrets). No secret in vars. Local server fixed `npx wrangler dev --ip 127.0.0.1 --port 8787`.
3. Auth middleware all requests first: configured APP_SESSION_TOKEN64 lowercase hex ->32 bytes, malformed config503, missing/invalid bearer401. Confirm exact supported constant-time API/type via current Workers docs and runtime test; no naive string equality/manual timing loop. No body/title/auth/header logs. no-store on all responses; CORS unnecessary for native apps; authenticated unknown routes404.
4. Bounded streaming body<=4096 and strict JSON object/key/content-type validation; exact Unicode scalar title policy, canonical UUID, versions, cursor parser and method/route rules from contracts. Reject JSON null/wrong types/unknown keys. Map exceptions to safe envelope rather than leak SQL.
5. Store uses parameterized INSERT and conditional UPDATE/DELETE RETURNING, server time monotonic clamp; conflict classification lookup has documented race. No upsert. UUID collision409 leaves original untouched. Maxversion PATCH409. GET/list counts/pagination uses frozen predicates, limit+1, global aggregate, terminal cursor null.
6. Permanent tests execute actual Worker fetch + local D1: no/wrong auth yields401 before DB calls (missing DB with bad auth still401), unset secret503, all validation boundaries, CRUD lifecycle, duplicate no overwrite, two concurrent same-version PATCHs exactly one success/one conflict, stale/delete unknown distinctions, 404/405/413/415/envelopes, 100+ page fixtures and filter-bound cursors/counts. Controlled fault injection tests validate failure envelopes, not pretend live proof. No SQL text assertions.
7. Document ignored local `.dev.vars` using `umask 077` (not0600, a file mode) and protected entry of distinct dev token. Runbook from repo root: `umask 077; read -rsp 'Dev token: ' TOKEN`; validate64 lowercase hex without echo; builtin printf writes APP_SESSION_TOKEN to worker/.dev.vars, `chmod 600 worker/.dev.vars`, `unset TOKEN`. Adjust paths if already in worker. No token examples/history/tracing/production reuse; remove file on teardown. Production provisioning08 separate.
8. Defer single final lane test run to07 after03–06 land; pass local API behavior evidence then fix via owners. Raw local D1 file never shipped to app.

## Todo list
- [ ] Complete required owned behavior and permanent tests/runbooks.
- [ ] Record designated gate evidence; resolve failures through original owners.
- [ ] Review frozen contract and exclusive ownership; no runtime placeholders.

## Success Criteria
Local Worker against real migration provides frozen CRUD/auth/pagination/concurrency behavior; exact lock/config/harness pinned, release config real identifiers through04 reopen; no credentials exposed.

## Conflict Prevention
No edits outside listed rows, including catalog/build/lock/docs/tests. New paths require registry amendment first. Integration defects reopen owner, then rerun affected gate.

## Risk Assessment
Wrangler/Vitest API evolution or local-only binding support requires documentation/current typings. Missing Cloudflare access may block remote binding, not justify placeholder. Classification race may return404/409 but never lost write.

## Security Considerations
Scoped deploy credentials outside app; fixed-size constant-time bearer check, prepared bindings, bounded requests, no redirects in client, no title/token logs. Service secret absent fails closed.

## Next steps
07 actual Android/local API gate; reopen04 for08 real binding IDs/config repairs, rerun permanent backend tests.

## Unresolved questions
Actual Cloudflare account/database identifiers during08; exact runtime timingSafeEqual API confirmed in02/04, not asserted verified now.
