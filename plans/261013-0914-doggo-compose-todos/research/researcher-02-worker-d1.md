# Research Report: Cloudflare D1 SQLite & TypeScript Worker API

> REVIEW STATUS: Partially superseded draft. Do not implement the create upsert, deprecated credential storage recommendation, or copy the quota/security guarantees below. UUIDs alone do not make retries safe; the example upsert can overwrite newer data. Use [authoritative corrections](../reports/design-decisions.md) and final API contracts. Retained as research provenance, not approved guidance.

## 1. Minimal CRUD Schema & Prepared Query API
### Schema (`migrations/0001_init.sql`)
```sql
CREATE TABLE IF NOT EXISTS todos (
  id TEXT PRIMARY KEY, -- Client-generated UUIDv4
  title TEXT NOT NULL,
  is_completed INTEGER NOT NULL DEFAULT 0 CHECK (is_completed IN (0, 1)),
  created_at INTEGER NOT NULL, -- Unix timestamp (ms)
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_todos_updated_at ON todos(updated_at DESC);
```

### Endpoints & Contracts
- `GET /api/todos?status={all|active|completed}` -> `200 OK [Todo]`
- `POST /api/todos` -> body `{ id: string, title: string }`. Validation: `title.trim()` length 1..500 chars; reject empty with `400 Bad Request`. Uses idempotent upsert (`INSERT INTO todos (id, title, is_completed, created_at, updated_at) VALUES (?, ?, 0, ?, ?) ON CONFLICT(id) DO UPDATE SET title=excluded.title`) or checks existence.
- `PATCH /api/todos/:id` -> body `{ title?: string, is_completed?: boolean }`. Returns `200 OK Todo`. If ID not found, returns `404 Not Found` (`{ "error": "Todo not found" }`).
- `DELETE /api/todos/:id` -> `200 OK { "deleted": true }`. If row absent, returns `404 Not Found`.

### D1 Prepared Statements & Parameter Binding
Worker strictly uses parameterized bindings to eliminate SQL injection:
```typescript
const stmt = env.DB.prepare('SELECT * FROM todos WHERE id = ?').bind(id);
const todo = await stmt.first<Todo>();
```

## 2. Authentication & Security Architecture
- **Zero Cloudflare Secrets in Native Apps**: Client NEVER embeds Cloudflare API tokens, Account IDs, or Global Keys. Native app connects strictly to the Worker's custom/workers.dev origin.
- **Private Personal App (Recommended Pattern)**:
  - Worker validates `Authorization: Bearer <APP_API_KEY>` against secret bound via `wrangler secret put API_KEY`.
  - Constant-time verification with `crypto.subtle.timingSafeEqual` prevents timing attacks.
  - Native client stores token securely: Android `EncryptedSharedPreferences` / Android KeyStore; Linux/Windows desktop credential storage or interactive prompt on first run.
- **Multi-User Tradeoff & Decision**:
  - Public multi-tenancy requires adding `user_id` column, indexing `(user_id, updated_at)`, and integrating an OIDC/OAuth provider (e.g., Google OAuth, Cloudflare Access, Clerk). Adds substantial client/server auth plumbing.
  - Recommendation: For personal dogfooding, pre-shared token provides strong security with zero maintenance; validate multi-user requirement with user before adding OAuth.

## 3. Concurrency & Network Failures (Online-First)
- **Stale Writes**: Last-Write-Wins (LWW) using `updated_at = Date.now()`. Sufficient for single-user multi-device without sync engine overhead.
- **Uncertain Network / Timeouts**: Client generates UUIDv4 before `POST`. If timeout occurs, retrying the exact request is idempotent and prevents duplicate items.
- **Transient Failures**: Native client retains unsaved draft in UI state upon HTTP failure and displays non-blocking retry banner.

## 4. Migrations & Deployment Workflow
- **Local Development**:
  1. Create migration: `npx wrangler d1 migrations create doggo-db create_todos`
  2. Apply locally: `npx wrangler d1 migrations apply doggo-db --local`
  3. Run local dev server: `npx wrangler dev` (uses local SQLite via Miniflare at `http://127.0.0.1:8787`).
- **Remote Production**:
  1. Create D1 database: `npx wrangler d1 create doggo-db` (copy `database_id` to `wrangler.toml`).
  2. Apply migrations remotely: `npx wrangler d1 migrations apply doggo-db --remote`.
  3. Store auth secret: `npx wrangler secret put API_KEY`.
  4. Deploy worker: `npx wrangler deploy`.

## 5. Quotas, Cost Caveats & Release Gates
- **Cloudflare Free Tier Quotas**:
  - Workers Free: 100,000 requests/day, 10ms CPU time/request.
  - D1 Free: 5,000,000 row reads/day, 100,000 row writes/day, 5 GB storage. Cost: $0.00/mo.
- **Quotas Caveat**: Batch operations limit (128KB per SQL statement, 100 D1 queries per Worker invocation). Adequate for todo operations.
- **Release Security Gate**:
  - Automated CI check preventing `.dev.vars` or hardcoded tokens in APK/binary assets.
  - Rejection of any raw string concatenation in D1 SQL queries.
  - Strict HTTPS enforcement and CORS headers configured for permitted clients.

## 6. Smoke Verification Steps
- **Local Smoke Test**:
  ```bash
  # 1. Start local worker
  npx wrangler dev --port 8787 &
  # 2. Test create
  curl -X POST http://localhost:8787/api/todos -H "Authorization: Bearer test-key" \
       -H "Content-Type: application/json" -d '{"id":"test-1","title":"Buy milk"}'
  # 3. Test list
  curl http://localhost:8787/api/todos -H "Authorization: Bearer test-key"
  # 4. Test delete
  curl -X DELETE http://localhost:8787/api/todos/test-1 -H "Authorization: Bearer test-key"
  ```
- **Remote Smoke Test**:
  ```bash
  curl -X GET "https://doggo-api.<account>.workers.dev/api/todos" \
       -H "Authorization: Bearer $PROD_API_KEY"
  ```

## 7. Primary Citations
- Cloudflare D1 Database Documentation: https://developers.cloudflare.com/d1/
- Cloudflare D1 Client API & Prepared Statements: https://developers.cloudflare.com/d1/build-with-d1/d1-client-api/
- Cloudflare D1 Migrations Reference: https://developers.cloudflare.com/d1/reference/migrations/
- Cloudflare Workers Limits & D1 Pricing: https://developers.cloudflare.com/d1/platform/pricing/
- Cloudflare Workers Secrets & Environment Variables: https://developers.cloudflare.com/workers/configuration/secrets/

## 8. Unresolved Questions
1. Personal API token vs multi-user OAuth: Does the user prefer the simple Bearer token for private personal use, or is public multi-user authentication required?
2. Offline read caching: Should a local SQLite read cache be added for offline inspection, or keep strict online-only network calls?
3. Soft-delete vs Hard-delete: Is hard-deletion preferred, or should an `is_deleted` column with tombstone retention be used?
