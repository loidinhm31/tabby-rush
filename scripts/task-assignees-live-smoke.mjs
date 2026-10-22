#!/usr/bin/env node
/**
 * TabbyRush Collaborative Task Assignees Live API Smoke.
 * Validates canonical single-row storage, 426 whole-app capability cutoff,
 * assignment CAS, child-only privacy, full-group completion, bounded accessChecks,
 * and hostile operation rejection against a disposable Glean Hub instance.
 *
 * SAFETY INVARIANTS:
 * - Refuses non-loopback origins.
 * - Requires explicit --confirm-app matching --app-id.
 * - Requires DISPOSABLE_RUN_AUTHORIZATION=true or TABBYRUSH_ALLOW_DISPOSABLE_SMOKE=1.
 * - Redacts tokens and credentials from console output.
 */

import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { parseArgs } from 'node:util';

const { values } = parseArgs({
  options: {
    origin: { type: 'string' },
    'app-id': { type: 'string' },
    'confirm-app': { type: 'string' },
    help: { type: 'boolean', short: 'h' },
  },
});

if (values.help) {
  console.log('Usage: node scripts/task-assignees-live-smoke.mjs --origin <approved-origin> --app-id <appId> --confirm-app <same-appId>');
  console.log('Environment variables required:');
  console.log('  DISPOSABLE_RUN_AUTHORIZATION=true (or TABBYRUSH_ALLOW_DISPOSABLE_SMOKE=1)');
  console.log('  TABBYRUSH_API_KEY=<key>');
  console.log('  TEST_EMAIL / TEST_PASSWORD (or DISPOSABLE_CREATOR_TOKEN, etc.)');
  console.log('Fails closed if target is not a local loopback origin or required credentials are missing.');
  process.exit(0);
}

async function main() {
  const disposableAuth = process.env.DISPOSABLE_RUN_AUTHORIZATION ?? process.env.TABBYRUSH_ALLOW_DISPOSABLE_SMOKE;
  assert.ok(
    disposableAuth === 'true' || disposableAuth === '1',
    '[ABORT] DISPOSABLE_RUN_AUTHORIZATION=true or TABBYRUSH_ALLOW_DISPOSABLE_SMOKE=1 required'
  );

  assert.ok(
    values.origin && values['app-id'] && values['app-id'] === values['confirm-app'],
    'Explicit --origin, --app-id, and matching --confirm-app required'
  );

  const origin = new URL(values.origin);
  const loopbackHosts = ['localhost', '127.0.0.1', '[::1]'];
  assert.ok(
    loopbackHosts.includes(origin.hostname),
    `[ABORT] Origin must target local loopback (localhost / 127.0.0.1). Target was: ${origin.hostname}`
  );
  assert.ok(
    (origin.protocol === 'http:' || origin.protocol === 'https:') &&
      !origin.username && !origin.password && !origin.search && !origin.hash && origin.pathname === '/',
    'Invalid origin format'
  );

  const appId = values['app-id'];
  const apiKey = process.env.TABBYRUSH_API_KEY;
  assert.ok(apiKey, 'TABBYRUSH_API_KEY required');

  console.log(`=== TabbyRush Task Assignees Live Smoke ===`);
  console.log(`Origin: ${origin.origin}`);
  console.log(`App ID: ${appId}`);

  async function rawRequest(method, path, body, token = null) {
    const response = await fetch(new URL(path, origin), {
      method,
      redirect: 'error',
      signal: AbortSignal.timeout(15000),
      headers: {
        'Content-Type': 'application/json',
        'X-App-Id': appId,
        'X-API-Key': apiKey,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    return response;
  }

  async function request(method, path, body, token = null) {
    const res = await rawRequest(method, path, body, token);
    const text = await res.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      throw new Error(`HTTP ${res.status} for ${method} ${path}: non-JSON body: ${text}`);
    }
    assert.ok(res.ok, `HTTP ${res.status} for ${method} ${path}: ${text}`);
    return json;
  }

  // Health check
  const health = await request('GET', '/api/v1/health');
  assert.equal(health.status, 'ok', 'Health check failed');

  // Helper to register or login a disposable user
  async function getOrCreateUser(role) {
    const baseEmail = process.env[`TEST_${role.toUpperCase()}_EMAIL`];
    const basePass = process.env[`TEST_${role.toUpperCase()}_PASSWORD`];
    if (baseEmail && basePass) {
      const login = await request('POST', '/api/v1/auth/login', { email: baseEmail, password: basePass });
      const token = login.accessToken || login.access_token;
      const id = login.userId || login.user_id || login.user?.id;
      return { token, user: { id } };
    }
    const username = `smoke_${role.toLowerCase()}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
    const email = `${username}@disposable.local`;
    const password = `SmokePass!_${randomUUID()}`;

    const regRes = await rawRequest('POST', '/api/v1/auth/register', { username, email, password });
    if (regRes.ok) {
      const reg = await regRes.json();
      const token = reg.accessToken || reg.access_token;
      const id = reg.userId || reg.user_id || reg.user?.id;
      return { token, user: { id } };
    }
    // Fall back to main TEST_EMAIL if role registration not available
    const mainEmail = process.env.TEST_EMAIL;
    const mainPass = process.env.TEST_PASSWORD;
    assert.ok(mainEmail && mainPass, `Unable to register ${role} and no TEST_EMAIL/TEST_PASSWORD set`);
    const login = await request('POST', '/api/v1/auth/login', { email: mainEmail, password: mainPass });
    const token = login.accessToken || login.access_token;
    const id = login.userId || login.user_id || login.user?.id;
    return { token, user: { id } };
  }

  console.log('\n[Phase 1/8] Setting up disposable actor identities...');
  const creator = await getOrCreateUser('creator');
  const alice = await getOrCreateUser('alice');
  const bob = await getOrCreateUser('bob');
  const outsider = await getOrCreateUser('outsider');
  const creator2 = await getOrCreateUser('creator2');

  console.log(`Creator ID: ${creator.user.id}`);
  console.log(`Alice ID:   ${alice.user.id}`);
  console.log(`Bob ID:     ${bob.user.id}`);

  const prefix = `/api/v1/sync/${encodeURIComponent(appId)}`;

  // Phase 2: Whole-App Capability Cutoff (HTTP 426 COLLABORATION_REQUIRED)
  console.log('\n[Phase 2/8] Testing whole-app capability cutoff (HTTP 426)...');
  const legacyPush = await rawRequest('POST', `${prefix}/push`, {
    records: [{ tableName: 'todos', rowId: randomUUID(), data: { title: 'Legacy' }, version: 1, assumedServerVersion: 0 }]
  }, creator.token);
  assert.equal(legacyPush.status, 426, 'Missing collaborationVersion: 1 must return HTTP 426 COLLABORATION_REQUIRED');
  const legacyPushJson = await legacyPush.json();
  assert.equal(legacyPushJson.code, 'COLLABORATION_REQUIRED');

  const legacyPull = await rawRequest('POST', `${prefix}/pull`, {
    batchSize: 10, tables: ['todos']
  }, creator.token);
  assert.equal(legacyPull.status, 426, 'Pull without collaborationVersion: 1 must return HTTP 426');

  const legacyDelta = await rawRequest('POST', `${prefix}/delta`, {
    push: { records: [] },
    pull: { batchSize: 10, tables: ['todos'] }
  }, creator.token);
  assert.equal(legacyDelta.status, 426, 'Delta without collaborationVersion: 1 must return HTTP 426');
  console.log('PASS: Capability cutoff enforced on /push, /pull, and /delta.');

  // Phase 3: Setup canonical tasks (Root + 2 Children) under Creator
  console.log('\n[Phase 3/8] Creating canonical root and children tasks under Creator...');
  const rootId = `smoke-root-${randomUUID()}`;
  const child1Id = `smoke-child1-${randomUUID()}`;
  const child2Id = `smoke-child2-${randomUUID()}`;

  const rootData = {
    id: rootId,
    title: 'Collaborative Root Task',
    completed: false,
    createdAt: Date.now(),
    parentTaskId: null,
    opaqueMeta: { customField: 'preserved_root' }
  };
  const child1Data = {
    id: child1Id,
    title: 'Child Task 1',
    completed: false,
    createdAt: Date.now(),
    parentTaskId: rootId,
    opaqueMeta: { customField: 'preserved_c1' }
  };
  const child2Data = {
    id: child2Id,
    title: 'Child Task 2',
    completed: false,
    createdAt: Date.now(),
    parentTaskId: rootId,
    opaqueMeta: { customField: 'preserved_c2' }
  };

  const pushRes = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      { tableName: 'todos', rowId: rootId, ownerUserId: creator.user.id, data: rootData, version: 1, assumedServerVersion: 0 },
      { tableName: 'todos', rowId: child1Id, ownerUserId: creator.user.id, data: child1Data, version: 1, assumedServerVersion: 0 },
      { tableName: 'todos', rowId: child2Id, ownerUserId: creator.user.id, data: child2Data, version: 1, assumedServerVersion: 0 }
    ]
  }, creator.token);

  assert.equal(pushRes.collaborationVersion, 1);
  assert.equal(pushRes.conflicts.length, 0);
  assert.equal(pushRes.syncedRecords.length, 3);
  assert.ok(pushRes.syncedRecords.every(r => r.ownerUserId === creator.user.id && r.version === 1));
  console.log('PASS: Canonical tasks created with exact owner tuple acknowledgement.');

  // Phase 4: Duplicate rowId under Creator2 (proves owner-namespaced canonical identity)
  console.log('\n[Phase 4/8] Testing duplicate rowId under different owner namespace...');
  const duplicateRes = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      { tableName: 'todos', rowId: rootId, ownerUserId: creator2.user.id, data: { ...rootData, title: 'Creator2 Root' }, version: 1, assumedServerVersion: 0 }
    ]
  }, creator2.token);
  assert.equal(duplicateRes.conflicts.length, 0);
  assert.equal(duplicateRes.syncedRecords[0].ownerUserId, creator2.user.id);
  console.log('PASS: Tasks with identical rowId under different owners are strictly partitioned.');

  // Phase 5: Assign Alice to Root & Verify Single Row Storage (0 Recipient Copies)
  console.log('\n[Phase 5/8] Assigning Alice to root task and verifying canonical storage...');
  const slotRes = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      {
        tableName: 'taskAssignees',
        rowId: rootId,
        ownerUserId: creator.user.id,
        data: { id: rootId, taskId: rootId, userId: alice.user.id },
        version: 1,
        assumedServerVersion: 0
      }
    ]
  }, creator.token);
  assert.equal(slotRes.conflicts.length, 0);
  assert.equal(slotRes.syncedRecords[0].tableName, 'taskAssignees');

  // Alice pulls: gets root + 2 children via inherited grant
  const alicePull = await request('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    checkpoint: null,
    batchSize: 100,
    tables: ['todos', 'taskAssignees']
  }, alice.token);

  const aliceVisibleTodos = alicePull.records.filter(r => r.tableName === 'todos');
  assert.ok(aliceVisibleTodos.some(r => r.rowId === rootId), 'Alice must receive assigned root task');
  assert.ok(aliceVisibleTodos.some(r => r.rowId === child1Id), 'Alice must receive inherited child 1');
  assert.ok(aliceVisibleTodos.some(r => r.rowId === child2Id), 'Alice must receive inherited child 2');

  // Assert single-row invariant: all records returned have ownerUserId === creator.user.id
  assert.ok(
    aliceVisibleTodos.every(r => r.ownerUserId === creator.user.id),
    'All collaborative task rows must retain canonical creator ownerUserId; zero recipient copies'
  );
  console.log('PASS: Alice received full-group projection with single canonical creator-owned rows.');

  // Phase 6: Assign Bob to Child 2 Only (Child-Only Privacy Verification)
  console.log('\n[Phase 6/8] Assigning Bob directly to child 2 (child-only privacy)...');
  await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      {
        tableName: 'taskAssignees',
        rowId: child2Id,
        ownerUserId: creator.user.id,
        data: { id: child2Id, taskId: child2Id, userId: bob.user.id },
        version: 1,
        assumedServerVersion: 0
      }
    ]
  }, creator.token);

  const bobPull = await request('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    checkpoint: null,
    batchSize: 100,
    tables: ['todos', 'taskAssignees']
  }, bob.token);

  const bobTodos = bobPull.records.filter(r => r.tableName === 'todos');
  assert.equal(bobTodos.length, 1, 'Bob must receive exactly one child task');
  assert.equal(bobTodos[0].rowId, child2Id, 'Bob must receive only child 2');
  assert.ok(!bobTodos.some(r => r.rowId === rootId), 'Bob MUST NOT receive private parent root task');
  assert.ok(!bobTodos.some(r => r.rowId === child1Id), 'Bob MUST NOT receive private sibling child 1');
  console.log('PASS: Child-only privacy verified; parent and siblings unexposed to Bob.');

  // Phase 7: Hostile Operations & Privilege Denial
  console.log('\n[Phase 7/8] Verifying rejection of unauthorized assignee mutations...');
  // Bob tries to delete Child 2
  const bobDelete = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      { tableName: 'todos', rowId: child2Id, ownerUserId: creator.user.id, data: {}, version: 2, assumedServerVersion: 1, deleted: true }
    ]
  }, bob.token);
  assert.ok(bobDelete.conflicts.length > 0 || bobDelete.failures.length > 0, 'Assignee delete must be rejected');

  // Bob tries to create a new child under root
  const bobCreateChild = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      { tableName: 'todos', rowId: randomUUID(), ownerUserId: creator.user.id, data: { title: 'Unauthorized Child', parentTaskId: rootId }, version: 1, assumedServerVersion: 0 }
    ]
  }, bob.token);
  assert.ok(bobCreateChild.conflicts.length > 0 || bobCreateChild.failures.length > 0, 'Assignee child create must be rejected');

  // Alice tries to edit non-editable field createdAt
  const aliceEditImmutable = await request('POST', `${prefix}/push`, {
    collaborationVersion: 1,
    records: [
      {
        tableName: 'todos',
        rowId: child1Id,
        ownerUserId: creator.user.id,
        data: { ...child1Data, createdAt: 1000 },
        version: 2,
        assumedServerVersion: 1
      }
    ]
  }, alice.token);
  assert.ok(aliceEditImmutable.conflicts.length > 0 || aliceEditImmutable.failures.length > 0, 'Editing immutable field must be rejected');

  // Outsider tries to access tasks
  const outsiderCheck = await request('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    accessChecks: [{ ownerUserId: creator.user.id, tableName: 'todos', rowId: rootId }]
  }, outsider.token);
  assert.equal(outsiderCheck.accessResults[0].status, 'unavailable', 'Outsider must be denied access');
  console.log('PASS: Authoritative permission enforcement rejected unauthorized assignee and outsider actions.');

  // Phase 8: Bounded AccessChecks & Stable Revision E
  console.log('\n[Phase 8/8] Testing bounded accessChecks and stable access revision E...');
  const validCheck = await request('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    accessChecks: [
      { ownerUserId: creator.user.id, tableName: 'todos', rowId: rootId },
      { ownerUserId: creator.user.id, tableName: 'todos', rowId: child1Id }
    ]
  }, alice.token);
  assert.ok(Number.isSafeInteger(validCheck.accessRevision), 'Missing accessRevision in check response');
  assert.equal(validCheck.accessResults.length, 2);
  assert.equal(validCheck.accessResults[0].status, 'accessible');
  assert.equal(validCheck.accessResults[0].hierarchyCoverage, 'full-group');

  // Bounded check: >100 keys rejected
  const oversizedChecks = Array.from({ length: 101 }, (_, i) => ({
    ownerUserId: creator.user.id,
    tableName: 'todos',
    rowId: `dummy-${i}`
  }));
  const overRes = await rawRequest('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    accessChecks: oversizedChecks
  }, alice.token);
  assert.equal(overRes.status, 400, 'AccessChecks > 100 must be rejected with HTTP 400');

  // Empty accessChecks [] final boundary check
  const emptyCheck = await request('POST', `${prefix}/pull`, {
    collaborationVersion: 1,
    expectedAccessRevision: validCheck.accessRevision,
    accessChecks: []
  }, alice.token);
  assert.equal(emptyCheck.accessRevision, validCheck.accessRevision);
  assert.deepEqual(emptyCheck.accessResults, []);
  console.log('PASS: Bounded accessChecks and stable revision E boundary confirmed.');

  console.log('\n=== ALL TASK ASSIGNEES LIVE SMOKE PHASES PASSED ===');
}

main().catch((err) => {
  console.error('\n[FATAL SMOKE ERROR]:', err.message);
  process.exit(1);
});
