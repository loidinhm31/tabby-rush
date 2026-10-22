#!/usr/bin/env node
/** Real Hub standard-sync smoke. Creates then soft-deletes one uniquely named test task. */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { parseArgs } from 'node:util';

const { values } = parseArgs({ options: {
  origin: { type: 'string' }, 'app-id': { type: 'string' },
  'confirm-app': { type: 'string' }, help: { type: 'boolean', short: 'h' },
} });
if (values.help) {
  console.log('Usage: node scripts/hub-live-smoke.mjs --origin <approved-origin> --app-id <appId> --confirm-app <same-appId>');
  console.log('Set TABBYRUSH_API_KEY and either GLEAN_TEST_USER_TOKEN or TEST_EMAIL/TEST_PASSWORD. No dotenv loading or default target.');
  console.log('Writes one new task and an update/conflict attempt, then soft-deletes it. Use an isolated test app/account. Does not purge or restore existing data.');
  process.exit(0);
}

async function main() {
  assert.ok(values.origin && values['app-id'] && values['app-id'] === values['confirm-app'], 'Explicit origin/app-id and matching confirm-app required');
  const origin = new URL(values.origin);
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(origin.hostname);
  assert.ok((origin.protocol === 'https:' || (origin.protocol === 'http:' && loopback)) &&
    !origin.username && !origin.password && !origin.search && !origin.hash && origin.pathname === '/', 'Invalid origin');
  const appId = values['app-id'];
  const apiKey = process.env.TABBYRUSH_API_KEY;
  assert.ok(apiKey, 'TABBYRUSH_API_KEY required');
  let token = process.env.GLEAN_TEST_USER_TOKEN;
  async function request(method, path, body, authenticated = true) {
    const response = await fetch(new URL(path, origin), {
      method, redirect: 'error', signal: AbortSignal.timeout(15000),
      headers: { 'Content-Type': 'application/json', 'X-App-Id': appId, 'X-API-Key': apiKey,
        ...(authenticated && token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    assert.ok(response.ok, `HTTP ${response.status} for ${method} ${path}`);
    return response.json();
  }
  const health = await request('GET', '/api/v1/health', undefined, false);
  assert.equal(health.status, 'ok');
  if (!token) {
    assert.ok(process.env.TEST_EMAIL && process.env.TEST_PASSWORD, 'Test token or TEST_EMAIL/TEST_PASSWORD required');
    const login = await request('POST', '/api/v1/auth/login', { email: process.env.TEST_EMAIL, password: process.env.TEST_PASSWORD }, false);
    assert.ok(typeof login.accessToken === 'string' && login.accessToken, 'Login returned no access token');
    token = login.accessToken;
  }
  const prefix = `/api/v1/sync/${encodeURIComponent(appId)}`;
  const checkVersion = (result) => assert.ok(Number.isSafeInteger(result.schemaVersion) && result.schemaVersion >= 1, 'Missing/invalid schemaVersion');
  const id = randomUUID();
  const initial = {
    id, title: `Standard sync smoke ${id}`, completed: false, createdAt: Date.now(),
    deadlineDate: null, deadlineTime: null, deadlineZone: null, deadlineOffsetSeconds: null,
    reminderAnchorTime: null, deadlineAnchorEpochMs: null, deadlineOverdueEpochMs: null,
    reminderEnabled: false, reminderLeadMinutes: 0, reminderIntervalMinutes: null,
    reminderCount: 1, scheduleToken: null, parentTaskId: null,
    smokeExtra: { retained: true, nested: [null, 'opaque'] },
  };
  const row = (data, version, assumedServerVersion, deleted = false) => ({
    tableName: 'todos', rowId: id, data, version, assumedServerVersion, deleted,
  });
  function accepted(result, version) {
    checkVersion(result);
    assert.deepEqual(result.conflicts, []);
    assert.deepEqual(result.failures, []);
    assert.ok(result.syncedRecords.some(record => record.tableName === 'todos' && record.rowId === id && record.version === version), 'Missing exact accepted record');
  }
  async function findRow() {
    let checkpoint = null;
    for (let page = 0; page < 10000; page++) {
      const result = await request('POST', `${prefix}/pull`, { checkpoint, batchSize: 100, tables: ['todos'] });
      checkVersion(result);
      const found = result.records.find(record => record.rowId === id && record.tableName === 'todos');
      if (found) return found;
      if (!result.hasMore) return null;
      assert.notDeepEqual(result.checkpoint, checkpoint, 'Pull checkpoint did not progress');
      checkpoint = result.checkpoint;
    }
    throw new Error('Pull exceeded page bound');
  }
  accepted(await request('POST', `${prefix}/push`, { records: [row(initial, 1, 0)] }), 1);
  const created = await findRow();
  assert.ok(created && !created.deleted);
  assert.deepEqual(created.data, initial);
  const updated = { ...created.data, title: `${initial.title} edited`, completed: true };
  const delta = await request('POST', `${prefix}/delta`, {
    push: { records: [row(updated, 2, 1)] }, pull: { checkpoint: null, batchSize: 100, tables: ['todos'] },
  });
  accepted(delta.push, 2);
  checkVersion(delta.pull);
  const current = await findRow();
  assert.equal(current.version, 2);
  assert.deepEqual(current.data, updated);
  const conflict = await request('POST', `${prefix}/push`, { records: [row(initial, 3, 1)] });
  checkVersion(conflict);
  assert.ok(conflict.conflicts.some(record => record.rowId === id), 'Stale edit was not rejected');
  assert.ok(!conflict.syncedRecords.some(record => record.rowId === id), 'Conflicted edit reported accepted');
  assert.deepEqual((await findRow()).data, updated);
  accepted(await request('POST', `${prefix}/push`, { records: [row({}, 3, 2, true)] }), 3);
  const tombstone = await findRow();
  assert.ok(tombstone && tombstone.deleted);
  assert.equal(tombstone.version, 3);
  assert.deepEqual(tombstone.data, {});
  console.log(`PASS: standard create/pull/delta/update/stale-conflict/delete; unknown JSON retained; schemaVersion observed. appId=${appId}, rowId=${id}`);
}

main().catch(error => {
  // Assertions here never include raw response bodies or credentials.
  console.error(`[Smoke] ${error instanceof assert.AssertionError ? error.message.split('\n')[0] : 'Request or smoke execution failed; inspect the isolated server logs'}`);
  process.exitCode = 1;
});
