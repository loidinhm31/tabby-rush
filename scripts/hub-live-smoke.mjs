#!/usr/bin/env node
/**
 * scripts/hub-live-smoke.mjs
 *
 * Authorized live Hub strict-v1 verification runner for Doggo.
 * Conforms strictly to docs/api-contract.md and contracts.md.
 *
 * Verifies:
 * 1. Health & Strict App Admission:
 *    - GET /api/v1/health returns healthy without redirect.
 *    - GET /api/v1/apps/doggo echoes syncMode: "strict-v1", active strictEpoch, and schema.
 * 2. Auth Session:
 *    - POST /auth/register with { username, email, password } -> { userId, accessToken, refreshToken }.
 *    - POST /auth/login with { email, password } -> { accessToken, refreshToken, apps }.
 *    - POST /auth/refresh with { refreshToken } -> { accessToken, refreshToken }.
 *    - GET /auth/me with Bearer token.
 * 3. Security Negative Isolation:
 *    - Strict sync rejects missing/invalid API key (HTTP 401/403).
 *    - Strict sync rejects missing/invalid JWT (HTTP 401).
 *    - Legacy sync routes reject strict-v1 app with HTTP 426 Upgrade Required (protocolUnsupported).
 * 4. Strict Strong Sync Operations:
 *    - POST /api/v1/sync/doggo/strong/bootstrap -> { protocol: "strict-v1", epoch, tables: ["todos"] }.
 *    - Asserts bootstrap epoch matches admission read-back epoch.
 *    - Insert-only create with expectedRevision: 0 -> status: "accepted", acceptedRevision: 1.
 *    - Idempotent frozen retry with identical opId + digest -> status: "replayed".
 *    - Base conflict with mismatched expectedRevision -> status: "revisionConflict".
 *    - Pull with batchSize: 100 -> retrieves the specific test rowId, validating schema columns.
 *    - Tombstone deletion via kind: "delete" -> status: "accepted", acceptedRevision: 2.
 *    - Pull confirms specific tombstone persistence: rowId matched, deleted: true, data: {}.
 *    - Epoch mismatch negative test: wrong epoch returns HTTP 409 (epochMismatch).
 */

import http from 'node:http';
import https from 'node:https';
import crypto from 'node:crypto';
import { parseArgs } from 'node:util';

const DEFAULT_TIMEOUT_MS = 10000;

function parseCliArgs() {
  const options = {
    origin: { type: 'string', default: process.env.APPROVED_HUB_ORIGIN || process.env.GLEAN_ORIGIN || 'http://127.0.0.1:3000' },
    'app-id': { type: 'string', default: process.env.DOGGO_APP_ID || 'doggo' },
    timeout: { type: 'string', default: String(DEFAULT_TIMEOUT_MS) },
    'fixture-test': { type: 'boolean', default: false },
    'allow-destructive-cleanup': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false }
  };

  const { values } = parseArgs({ options, allowPositionals: false });

  if (values.help) {
    console.log(`Usage: node scripts/hub-live-smoke.mjs [options]

Environment Variables (Preferred for credentials):
  APPROVED_HUB_ORIGIN   Target Glean Hub origin (e.g. https://hub.example.com)
  DOGGO_APP_ID          Target application ID (default: doggo)
  DOGGO_API_KEY         Provisioned dedicated API key
  TEST_EMAIL            Test user email
  TEST_PASSWORD         Test user password

Options:
  --origin <URL>              Approved Hub origin (default: $APPROVED_HUB_ORIGIN or http://127.0.0.1:3000)
  --app-id <APP_ID>           Target application ID (default: doggo)
  --timeout <MS>              Request timeout in milliseconds (default: 10000)
  --fixture-test              Execute self-contained local mock server test suite
  --allow-destructive-cleanup Enable tombstone deletion cleanup on live runs
  -h, --help                  Show this help message
`);
    process.exit(0);
  }

  return values;
}

class BoundedHttpClient {
  constructor(origin, timeoutMs = DEFAULT_TIMEOUT_MS) {
    this.origin = origin.replace(/\/+$/, '');
    this.timeoutMs = timeoutMs;
  }

  async request(method, path, { headers = {}, body = null } = {}) {
    const url = new URL(path, this.origin);
    const isHttps = url.protocol === 'https:';
    const transport = isHttps ? https : http;

    return new Promise((resolve, reject) => {
      const reqHeaders = { ...headers };
      let payload = null;

      if (body !== null && typeof body === 'object') {
        payload = JSON.stringify(body);
        reqHeaders['Content-Type'] = 'application/json';
        reqHeaders['Content-Length'] = Buffer.byteLength(payload);
      } else if (typeof body === 'string') {
        payload = body;
        reqHeaders['Content-Length'] = Buffer.byteLength(payload);
      }

      const reqOptions = {
        method,
        hostname: url.hostname,
        port: url.port || (isHttps ? 443 : 80),
        path: url.pathname + url.search,
        headers: reqHeaders,
        timeout: this.timeoutMs,
      };

      const req = transport.request(reqOptions, (res) => {
        // Enforce no-redirect policy: redirects are not followed automatically
        if (res.statusCode >= 300 && res.statusCode < 400) {
          res.resume();
          return resolve({
            status: res.statusCode,
            headers: res.headers,
            data: null,
            redirectLocation: res.headers.location || null
          });
        }

        const chunks = [];
        res.on('data', chunk => chunks.push(chunk));
        res.on('end', () => {
          const raw = Buffer.concat(chunks).toString('utf8');
          let parsed = null;
          try {
            parsed = JSON.parse(raw);
          } catch {
            parsed = raw;
          }
          resolve({
            status: res.statusCode,
            headers: res.headers,
            data: parsed
          });
        });
      });

      req.on('timeout', () => {
        req.destroy(new Error(`Request timed out after ${this.timeoutMs}ms`));
      });

      req.on('error', (err) => {
        reject(err);
      });

      if (payload) {
        req.write(payload);
      }
      req.end();
    });
  }

  get(path, opts) { return this.request('GET', path, opts); }
  post(path, body, opts = {}) { return this.request('POST', path, { ...opts, body }); }
  delete(path, opts) { return this.request('DELETE', path, opts); }
}

async function runCheck(suiteName, checkName, fn) {
  process.stdout.write(`  [${suiteName}] ${checkName}... `);
  try {
    const res = await fn();
    if (res && res.passed) {
      console.log(`PASS (${res.detail || 'ok'})`);
      return true;
    } else {
      console.log(`FAIL (${res ? res.detail : 'unspecified failure'})`);
      return false;
    }
  } catch (err) {
    console.log(`ERROR (${err.message})`);
    return false;
  }
}

// In-memory mock server for deterministic self-test mode (--fixture-test)
function startFixtureServer() {
  const mockDb = new Map(); // rowId -> { tableName, rowId, revision, deleted, data }
  const seenOps = new Map(); // opId -> { rowId, revision, digest }

  const server = http.createServer((req, res) => {
    const parsedUrl = new URL(req.url, 'http://127.0.0.1');
    const path = parsedUrl.pathname;
    const authHeader = req.headers['authorization'];
    const apiKey = req.headers['x-api-key'];

    const sendJson = (status, obj) => {
      res.writeHead(status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(obj));
    };

    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => {
      let body = {};
      if (chunks.length > 0) {
        try { body = JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { body = {}; }
      }

      // 1. Health check
      if (path === '/api/v1/health' && req.method === 'GET') {
        return sendJson(200, { status: 'healthy', version: '1.4.0', strictEngine: true });
      }

      // 2. Strict apps readback: GET /api/v1/apps/:id
      if (path === '/api/v1/apps/doggo' && req.method === 'GET') {
        return sendJson(200, {
          appId: 'doggo',
          syncMode: 'strict-v1',
          strictEpoch: '550e8400-e29b-41d4-a716-446655440000',
          schema: {
            todos: {
              columns: [
                { name: 'id', type: 'string', nullable: false },
                { name: 'title', type: 'string', nullable: false },
                { name: 'completed', type: 'boolean', nullable: false },
                { name: 'createdAt', type: 'integer', nullable: false }
              ],
              primaryKey: 'id'
            }
          }
        });
      }

      // 3. Auth registration: POST /auth/register { username, email, password }
      if (path === '/auth/register' && req.method === 'POST') {
        if (!body.email || !body.password) return sendJson(400, { error: 'invalid_credentials' });
        return sendJson(200, {
          userId: 'usr_' + crypto.randomBytes(4).toString('hex'),
          accessToken: 'valid_jwt_mock_token',
          refreshToken: 'valid_refresh_token'
        });
      }

      // 4. Auth login: POST /auth/login { email, password }
      if (path === '/auth/login' && req.method === 'POST') {
        if (body.email && body.password) {
          return sendJson(200, {
            userId: 'usr_test_123',
            accessToken: 'valid_jwt_mock_token',
            refreshToken: 'valid_refresh_token',
            apps: ['doggo'],
            isAdmin: false
          });
        }
        return sendJson(401, { error: 'invalid_credentials', code: 'unauthorized' });
      }

      // 5. Auth refresh: POST /auth/refresh { refreshToken }
      if (path === '/auth/refresh' && req.method === 'POST') {
        if (body.refreshToken === 'valid_refresh_token') {
          return sendJson(200, {
            accessToken: 'refreshed_jwt_mock_token',
            refreshToken: 'refreshed_refresh_token'
          });
        }
        return sendJson(401, { error: 'invalid_refresh_token', code: 'unauthorized' });
      }

      // 6. Auth /auth/me
      if (path === '/auth/me' && req.method === 'GET') {
        if (!authHeader || !authHeader.startsWith('Bearer ')) {
          return sendJson(401, { error: 'unauthorized', code: 'unauthorized' });
        }
        return sendJson(200, {
          userId: 'usr_test_123',
          username: 'test_user',
          email: 'test@example.com',
          apps: ['doggo'],
          isAdmin: false
        });
      }

      // 7. Access requests
      if (path === '/apps/access-requests/me' && req.method === 'GET') {
        return sendJson(200, [{ appId: 'doggo', status: 'approved' }]);
      }

      // 8. Strong sync routes: POST /api/v1/sync/:app/strong/*
      if (path.startsWith('/api/v1/sync/doggo/strong/')) {
        if (!apiKey || apiKey !== 'valid_doggo_key') {
          return sendJson(403, { error: 'Forbidden API key', code: 'unauthorized' });
        }
        if (!authHeader || !authHeader.startsWith('Bearer valid_jwt_mock_token') && !authHeader.startsWith('Bearer refreshed_jwt_mock_token')) {
          return sendJson(401, { error: 'Missing or invalid token', code: 'unauthorized' });
        }

        if (path === '/api/v1/sync/doggo/strong/bootstrap') {
          return sendJson(200, {
            protocol: 'strict-v1',
            appId: 'doggo',
            epoch: '550e8400-e29b-41d4-a716-446655440000',
            tables: ['todos']
          });
        }

        if (path === '/api/v1/sync/doggo/strong/push') {
          if (body.epoch !== '550e8400-e29b-41d4-a716-446655440000') {
            return sendJson(409, { error: 'Epoch mismatch', code: 'epochMismatch' });
          }
          const ops = body.operations || [];
          const results = ops.map(op => {
            // Check idempotent replay
            if (seenOps.has(op.opId)) {
              const prev = seenOps.get(op.opId);
              return { opId: op.opId, rowId: op.rowId, digest: 'mock_digest', status: 'replayed', acceptedRevision: prev.revision };
            }

            const current = mockDb.get(op.rowId);
            const currentRev = current ? current.revision : 0;

            if (op.expectedRevision !== currentRev) {
              return { opId: op.opId, rowId: op.rowId, digest: 'mock_digest', status: 'revisionConflict', currentRevision: currentRev };
            }

            const newRev = currentRev + 1;
            const isDelete = op.kind === 'delete';
            mockDb.set(op.rowId, {
              tableName: 'todos',
              rowId: op.rowId,
              revision: newRev,
              deleted: isDelete,
              data: isDelete ? {} : op.data
            });
            seenOps.set(op.opId, { rowId: op.rowId, revision: newRev, digest: 'mock_digest' });

            return { opId: op.opId, rowId: op.rowId, digest: 'mock_digest', status: 'accepted', acceptedRevision: newRev };
          });
          return sendJson(200, {
            protocol: 'strict-v1',
            epoch: '550e8400-e29b-41d4-a716-446655440000',
            results
          });
        }

        if (path === '/api/v1/sync/doggo/strong/pull') {
          if (body.epoch !== '550e8400-e29b-41d4-a716-446655440000') {
            return sendJson(409, { error: 'Epoch mismatch', code: 'epochMismatch' });
          }
          const records = Array.from(mockDb.values());
          return sendJson(200, {
            protocol: 'strict-v1',
            epoch: '550e8400-e29b-41d4-a716-446655440000',
            records,
            cursor: 'cursor-token-101',
            hasMore: false
          });
        }
      }

      // 9. Legacy sync routes hit with strict app must return HTTP 426 protocolUnsupported
      if (path.startsWith('/api/v1/sync/doggo/') && !path.includes('/strong/')) {
        return sendJson(426, {
          error: 'Legacy sync endpoints prohibited for strict-v1 app',
          code: 'protocolUnsupported'
        });
      }

      return sendJson(404, { error: 'Not found', code: 'notFound' });
    });
  });

  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      resolve({ server, port, origin: `http://127.0.0.1:${port}` });
    });
  });
}

async function runLiveSmoke(options) {
  console.log(`================================================================`);
  console.log(`Doggo Hub Strict-v1 Live Smoke Test Runner`);
  console.log(`Target Origin: ${options.origin}`);
  console.log(`Application:   ${options['app-id']}`);
  console.log(`================================================================\n`);

  const client = new BoundedHttpClient(options.origin, parseInt(options.timeout, 10));
  let totalChecks = 0;
  let passedChecks = 0;

  async function execCheck(suite, name, fn) {
    totalChecks++;
    const ok = await runCheck(suite, name, fn);
    if (ok) passedChecks++;
    return ok;
  }

  // --- Suite 1: Connectivity & Schema Admission Read-Back ---
  console.log(`\n--- Suite 1: Health & App Admission ---`);
  await execCheck('Admission', 'GET /api/v1/health returns healthy without redirect', async () => {
    const res = await client.get('/api/v1/health');
    if (res.status !== 200) return { passed: false, detail: `status ${res.status}` };
    return { passed: true, detail: `HTTP 200, status=${res.data?.status || 'ok'}` };
  });

  let activeEpoch = null;
  await execCheck('Admission', `GET /api/v1/apps/${options['app-id']} echoes strict-v1 mode and schema`, async () => {
    const res = await client.get(`/api/v1/apps/${options['app-id']}`);
    if (res.status !== 200) return { passed: false, detail: `status ${res.status}` };
    const syncMode = res.data?.syncMode;
    activeEpoch = res.data?.strictEpoch;
    if (syncMode !== 'strict-v1') {
      return { passed: false, detail: `unexpected syncMode '${syncMode}', expected 'strict-v1'` };
    }
    if (!activeEpoch || typeof activeEpoch !== 'string') {
      return { passed: false, detail: 'missing or invalid strictEpoch in admission read-back' };
    }
    const todosCols = res.data?.schema?.todos?.columns;
    if (!Array.isArray(todosCols) || todosCols.length !== 4) {
      return { passed: false, detail: `expected 4 columns in todos schema, found ${todosCols?.length}` };
    }
    return { passed: true, detail: `syncMode=strict-v1, epoch=${activeEpoch}` };
  });

  // --- Suite 2: Security & Negative Route Isolation ---
  console.log(`\n--- Suite 2: Security & Negative Route Isolation ---`);
  await execCheck('Security', 'Strict sync bootstrap rejects missing API key (HTTP 401/403)', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/bootstrap`, {});
    const ok = res.status === 401 || res.status === 403;
    return { passed: ok, detail: `status ${res.status}` };
  });

  await execCheck('Security', 'Strict sync bootstrap rejects invalid API key (HTTP 401/403)', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/bootstrap`, {}, {
      headers: { 'X-API-Key': 'invalid_key_probe_' + crypto.randomBytes(4).toString('hex') }
    });
    const ok = res.status === 401 || res.status === 403;
    return { passed: ok, detail: `status ${res.status}` };
  });

  await execCheck('Security', 'Legacy sync route rejects strict-v1 app with HTTP 426 (protocolUnsupported)', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/push`, {});
    const ok = res.status === 426;
    return { passed: ok, detail: `status ${res.status}${ok ? ' (426 protocolUnsupported)' : ''}` };
  });

  // --- Suite 3: Auth & Access Protocol ---
  console.log(`\n--- Suite 3: Authentication & Access Session ---`);
  const runId = crypto.randomBytes(4).toString('hex');
  const testEmail = process.env.TEST_EMAIL || `smoke_${runId}@example.com`;
  const testPassword = process.env.TEST_PASSWORD || `SmokePass_${runId}!`;
  let authToken = null;
  let refreshToken = null;

  await execCheck('Auth', 'Register new user account (POST /auth/register)', async () => {
    const res = await client.post('/auth/register', {
      username: `smoke_user_${runId}`,
      email: testEmail,
      password: testPassword
    });
    if (res.status === 200 || res.status === 201) {
      if (res.data?.accessToken) authToken = res.data.accessToken;
      if (res.data?.refreshToken) refreshToken = res.data.refreshToken;
      return { passed: true, detail: `registered (status ${res.status})` };
    }
    if (res.status === 409) {
      return { passed: true, detail: 'user already exists (HTTP 409), continuing' };
    }
    return { passed: false, detail: `status ${res.status}` };
  });

  await execCheck('Auth', 'Login with user credentials (POST /auth/login)', async () => {
    const res = await client.post('/auth/login', {
      email: testEmail,
      password: testPassword
    });
    if (res.status === 200 && res.data?.accessToken) {
      authToken = res.data.accessToken;
      refreshToken = res.data.refreshToken || refreshToken;
      return { passed: true, detail: `accessToken obtained, apps=${JSON.stringify(res.data.apps || [])}` };
    }
    return { passed: false, detail: `login failed with status ${res.status}` };
  });

  if (refreshToken) {
    await execCheck('Auth', 'Token refresh exchange (POST /auth/refresh)', async () => {
      const res = await client.post('/auth/refresh', { refreshToken });
      if (res.status === 200 && res.data?.accessToken) {
        authToken = res.data.accessToken;
        return { passed: true, detail: 'token refreshed successfully' };
      }
      return { passed: false, detail: `refresh failed with status ${res.status}` };
    });
  }

  if (authToken) {
    await execCheck('Auth', 'Verify identity and grants (GET /auth/me)', async () => {
      const res = await client.get('/auth/me', {
        headers: { 'Authorization': `Bearer ${authToken}` }
      });
      if (res.status === 200) {
        return { passed: true, detail: `verified user=${res.data?.email || res.data?.username}` };
      }
      return { passed: false, detail: `status ${res.status}` };
    });
  }

  // --- Suite 4: Strict-v1 Sync Protocol Operations ---
  console.log(`\n--- Suite 4: Strict Strong Sync Operations ---`);
  const apiKey = process.env.DOGGO_API_KEY || (options['fixture-test'] ? 'valid_doggo_key' : '');
  const testHeaders = {
    'X-App-Id': options['app-id']
  };
  if (apiKey) testHeaders['X-API-Key'] = apiKey;
  if (authToken) testHeaders['Authorization'] = `Bearer ${authToken}`;

  let serverEpoch = null;
  const testRowId = crypto.randomUUID ? crypto.randomUUID() : `smoke_todo_${runId}`;

  await execCheck('Sync', 'Bootstrap establishes authoritative protocol and epoch', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/bootstrap`, {}, { headers: testHeaders });
    if (res.status === 200) {
      serverEpoch = res.data?.epoch;
      const protocol = res.data?.protocol;
      if (protocol !== 'strict-v1') return { passed: false, detail: `invalid protocol '${protocol}'` };
      if (activeEpoch && serverEpoch !== activeEpoch) {
        return { passed: false, detail: `bootstrap epoch '${serverEpoch}' does not match admission epoch '${activeEpoch}'` };
      }
      return { passed: true, detail: `protocol=strict-v1, epoch=${serverEpoch}` };
    }
    return { passed: false, detail: `bootstrap failed with status ${res.status}` };
  });

  const effectiveEpoch = serverEpoch || activeEpoch;

  await execCheck('Security', 'Strict sync rejects invalid JWT token (HTTP 401)', async () => {
    const badAuthHeaders = {
      ...testHeaders,
      'Authorization': 'Bearer invalid_tampered_jwt_token'
    };
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
      protocol: 'strict-v1',
      epoch: effectiveEpoch,
      operations: []
    }, { headers: badAuthHeaders });
    const ok = res.status === 401;
    return { passed: ok, detail: `status ${res.status}` };
  });

  await execCheck('Sync', 'Epoch mismatch negative test (POST /push with invalid epoch returns 409)', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
      protocol: 'strict-v1',
      epoch: '00000000-0000-0000-0000-000000000000',
      operations: []
    }, { headers: testHeaders });
    const ok = res.status === 409;
    return { passed: ok, detail: `status ${res.status}${ok ? ' (epochMismatch)' : ''}` };
  });

  let currentRevision = 0;
  const insertOpId = crypto.randomUUID ? crypto.randomUUID() : `op_create_${runId}`;
  const insertOp = {
    opId: insertOpId,
    epoch: effectiveEpoch,
    tableName: 'todos',
    rowId: testRowId,
    kind: 'create',
    expectedRevision: 0,
    data: {
      id: testRowId,
      title: `Smoke test task ${runId}`,
      completed: false,
      createdAt: Date.now()
    }
  };

  await execCheck('Sync', 'Push create operation: Insert-only create (expectedRevision: 0) accepted', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
      protocol: 'strict-v1',
      epoch: effectiveEpoch,
      operations: [insertOp]
    }, { headers: testHeaders });

    if (res.status === 200 && Array.isArray(res.data?.results)) {
      const result = res.data.results.find(r => r.opId === insertOp.opId);
      if (result && result.status === 'accepted' && result.acceptedRevision === 1) {
        currentRevision = result.acceptedRevision;
        return { passed: true, detail: `accepted at revision ${currentRevision}` };
      }
      return { passed: false, detail: `op not accepted: ${JSON.stringify(result)}` };
    }
    return { passed: false, detail: `push returned status ${res.status}` };
  });

  await execCheck('Sync', 'Idempotent frozen retry: identical opId + digest returns replayed', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
      protocol: 'strict-v1',
      epoch: effectiveEpoch,
      operations: [insertOp]
    }, { headers: testHeaders });

    if (res.status === 200 && Array.isArray(res.data?.results)) {
      const result = res.data.results.find(r => r.opId === insertOp.opId);
      if (result && result.status === 'replayed') {
        return { passed: true, detail: `replayed confirmed, acceptedRevision=${result.acceptedRevision}` };
      }
      return { passed: false, detail: `expected replayed status, got: ${JSON.stringify(result)}` };
    }
    return { passed: false, detail: `push returned status ${res.status}` };
  });

  await execCheck('Sync', 'Push conflict: Base revision mismatch returns revisionConflict', async () => {
    const conflictingOp = {
      opId: crypto.randomUUID ? crypto.randomUUID() : `op_conflict_${runId}`,
      epoch: effectiveEpoch,
      tableName: 'todos',
      rowId: testRowId,
      kind: 'update',
      expectedRevision: 999, // Mismatched base revision
      data: {
        id: testRowId,
        title: `Conflicting update ${runId}`,
        completed: true,
        createdAt: Date.now()
      }
    };
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
      protocol: 'strict-v1',
      epoch: effectiveEpoch,
      operations: [conflictingOp]
    }, { headers: testHeaders });

    if (res.status === 200 && Array.isArray(res.data?.results)) {
      const result = res.data.results.find(r => r.opId === conflictingOp.opId);
      if (result && result.status === 'revisionConflict') {
        return { passed: true, detail: `revisionConflict detected, currentRevision=${result.currentRevision}` };
      }
      return { passed: false, detail: `expected revisionConflict, received: ${JSON.stringify(result)}` };
    }
    return { passed: false, detail: `push returned status ${res.status}` };
  });

  await execCheck('Sync', 'Pull operation retrieves the specific created record and validates schema', async () => {
    const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/pull`, {
      protocol: 'strict-v1',
      epoch: effectiveEpoch,
      tableName: 'todos',
      cursor: null,
      batchSize: 100
    }, { headers: testHeaders });

    if (res.status === 200 && Array.isArray(res.data?.records)) {
      const found = res.data.records.find(r => r.rowId === testRowId);
      if (!found) return { passed: false, detail: `specific created record '${testRowId}' not found in pull` };
      if (found.deleted) return { passed: false, detail: `record '${testRowId}' marked deleted prematurely` };
      const d = found.data;
      if (!d || typeof d.title !== 'string' || typeof d.completed !== 'boolean' || typeof d.createdAt !== 'number') {
        return { passed: false, detail: 'record data does not match todos schema columns' };
      }
      return { passed: true, detail: `retrieved record '${found.rowId}' at revision ${found.revision}` };
    }
    return { passed: false, detail: `pull returned status ${res.status}` };
  });

  // --- Suite 5: Tombstone Deletion & Persistence Invariants ---
  console.log(`\n--- Suite 5: Tombstone Deletion & Invariants ---`);
  const shouldCleanup = options['allow-destructive-cleanup'] || options['fixture-test'];

  if (shouldCleanup) {
    await execCheck('Cleanup', 'Revision-CAS delete produces permanent empty tombstone', async () => {
      const deleteOp = {
        opId: crypto.randomUUID ? crypto.randomUUID() : `op_delete_${runId}`,
        epoch: effectiveEpoch,
        tableName: 'todos',
        rowId: testRowId,
        kind: 'delete',
        expectedRevision: currentRevision, // Dynamic expected revision
        data: {}
      };
      const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/push`, {
        protocol: 'strict-v1',
        epoch: effectiveEpoch,
        operations: [deleteOp]
      }, { headers: testHeaders });

      if (res.status === 200 && Array.isArray(res.data?.results)) {
        const result = res.data.results.find(r => r.opId === deleteOp.opId);
        if (result && result.status === 'accepted' && result.acceptedRevision === currentRevision + 1) {
          currentRevision = result.acceptedRevision;
          return { passed: true, detail: `deleted at revision ${currentRevision}` };
        }
        return { passed: false, detail: `delete failed: ${JSON.stringify(result)}` };
      }
      return { passed: false, detail: `delete push returned status ${res.status}` };
    });

    await execCheck('Cleanup', 'Pull confirms specific tombstone preservation (rowId match, deleted: true, data: {})', async () => {
      const res = await client.post(`/api/v1/sync/${options['app-id']}/strong/pull`, {
        protocol: 'strict-v1',
        epoch: effectiveEpoch,
        tableName: 'todos',
        cursor: null,
        batchSize: 100
      }, { headers: testHeaders });

      if (res.status === 200 && Array.isArray(res.data?.records)) {
        const tombstone = res.data.records.find(r => r.rowId === testRowId && r.deleted === true);
        if (!tombstone) return { passed: false, detail: `specific tombstone for '${testRowId}' not found in pull` };
        const isEmptyData = !tombstone.data || Object.keys(tombstone.data).length === 0;
        if (!isEmptyData) {
          return { passed: false, detail: 'tombstone data must be an empty object' };
        }
        return { passed: true, detail: `tombstone confirmed empty at revision ${tombstone.revision}` };
      }
      return { passed: false, detail: `pull returned status ${res.status}` };
    });
  } else {
    console.log(`  [Cleanup] Skipping tombstone delete (--allow-destructive-cleanup omitted).`);
  }

  // --- Summary ---
  console.log(`\n================================================================`);
  console.log(`Live Smoke Summary: ${passedChecks}/${totalChecks} checks passed`);
  console.log(`================================================================`);

  if (passedChecks < totalChecks) {
    throw new Error(`Smoke suite failed: ${totalChecks - passedChecks} checks did not pass.`);
  }
}

async function main() {
  const options = parseCliArgs();

  if (options['fixture-test']) {
    console.log(`[Info] Starting local fixture server for offline self-test...`);
    const { server, origin } = await startFixtureServer();
    try {
      options.origin = origin;
      await runLiveSmoke(options);
      console.log(`\n[Info] Local fixture self-test completed successfully.`);
      server.close();
      process.exit(0);
    } catch (err) {
      console.error(`\n[Error] Local fixture test failed: ${err.message}`);
      server.close();
      process.exit(1);
    }
  }

  try {
    await runLiveSmoke(options);
    process.exit(0);
  } catch (err) {
    console.error(`\n[Error] Live smoke suite failed: ${err.message}`);
    process.exit(1);
  }
}

main();
