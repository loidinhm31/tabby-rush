#!/usr/bin/env node
/**
 * scripts/hub-strict-upgrade.test.mjs
 *
 * Automated test suite for hub-strict-upgrade.mjs CLI runner.
 * Tests preconditions, secret redaction, dry-run, same-ID recovery, polling, and final assertion.
 */

import assert from 'node:assert/strict';
import { describe, it, before, after } from 'node:test';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const CANONICAL_V1_FINGERPRINT =
  '3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec';
const CANONICAL_V2_FINGERPRINT =
  '5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4';
const V2_COLUMNS = [
  { name: 'id', type: 'string', nullable: false },
  { name: 'title', type: 'string', nullable: false },
  { name: 'completed', type: 'boolean', nullable: false },
  { name: 'createdAt', type: 'integer', nullable: false },
  { name: 'deadlineDate', type: 'string', nullable: true },
  { name: 'deadlineTime', type: 'string', nullable: true },
  { name: 'deadlineZone', type: 'string', nullable: true },
  { name: 'deadlineOffsetSeconds', type: 'integer', nullable: true },
  { name: 'reminderAnchorTime', type: 'string', nullable: true },
  { name: 'deadlineAnchorEpochMs', type: 'integer', nullable: true },
  { name: 'deadlineOverdueEpochMs', type: 'integer', nullable: true },
  { name: 'reminderEnabled', type: 'boolean', nullable: false },
  { name: 'reminderLeadMinutes', type: 'integer', nullable: false },
  { name: 'reminderIntervalMinutes', type: 'integer', nullable: true },
  { name: 'reminderCount', type: 'integer', nullable: false },
  { name: 'scheduleToken', type: 'string', nullable: true },
];

const scriptPath = resolve(process.cwd(), 'scripts/hub-strict-upgrade.mjs');

function runScript(args, env = {}) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [scriptPath, ...args], {
      env: { ...process.env, ...env },
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (d) => (stdout += d.toString()));
    child.stderr.on('data', (d) => (stderr += d.toString()));
    child.on('close', (code) => {
      resolve({ code, stdout, stderr });
    });
  });
}

describe('hub-strict-upgrade.mjs CLI tests', () => {
  let mockServer;
  let mockPort;
  let serverOrigin;

  let currentSyncMode = 'strict-v1';
  let currentFingerprint = CANONICAL_V1_FINGERPRINT;
  let currentEpoch = '550e8400-e29b-41d4-a716-446655440000';
  let shouldDropInitialPost = false;
  let rejectWith400 = false;
  let returnCorruptedV2Contract = false;
  let simulatedMigrationState = 'transforming';
  let simulatedError = null;
  let receivedMigrationIds = [];
  before(async () => {
    mockServer = http.createServer((req, res) => {
      const url = new URL(req.url, `http://127.0.0.1:${mockPort}`);
      let bodyStr = '';
      req.on('data', (c) => (bodyStr += c.toString()));
      req.on('end', () => {
        let body = null;
        try {
          body = bodyStr ? JSON.parse(bodyStr) : null;
        } catch {}

        // 1. GET /api/v1/apps/:appId/strict-contract
        if (req.method === 'GET' && url.pathname.endsWith('/strict-contract')) {
          const v2Cols = returnCorruptedV2Contract ? V2_COLUMNS.slice(0, 10) : V2_COLUMNS;
          const v1Cols = [
            { name: 'id', type: 'string', nullable: false },
            { name: 'title', type: 'string', nullable: false },
            { name: 'completed', type: 'boolean', nullable: false },
            { name: 'createdAt', type: 'integer', nullable: false },
          ];

          return res.end(
            JSON.stringify({
              appId: 'tabby-rush',
              syncMode: currentSyncMode,
              epoch: currentEpoch,
              schemaFingerprint: currentFingerprint,
              tables: ['todos'],
              schema: {
                todos: {
                  columns: currentSyncMode === 'strict-v2' ? v2Cols : v1Cols,
                  primaryKey: 'id',
                },
              },
            }),
          );
        }

        // 2. POST /api/v1/apps/:appId/strict-upgrades
        if (req.method === 'POST' && url.pathname.endsWith('/strict-upgrades')) {
          if (body?.migrationId) {
            receivedMigrationIds.push(body.migrationId);
          }
          if (rejectWith400) {
            res.writeHead(400, { 'Content-Type': 'application/json' });
            return res.end(JSON.stringify({ error: 'Schema conflict or invalid parameters' }));
          }
          if (shouldDropInitialPost) {
            shouldDropInitialPost = false;
            return req.destroy();
          }

          if (body?.dryRun) {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end(
              JSON.stringify({
                migrationId: body.migrationId,
                appId: 'tabby-rush',
                state: 'preflight-passed',
                sourceVersion: 'strict-v1',
                targetVersion: 'strict-v2',
                recordsTransformed: 50,
                tombstonesPreserved: 5,
              }),
            );
          }

          res.writeHead(200, { 'Content-Type': 'application/json' });
          return res.end(
            JSON.stringify({
              migrationId: body.migrationId,
              appId: 'tabby-rush',
              state: simulatedMigrationState,
              sourceVersion: 'strict-v1',
              targetVersion: 'strict-v2',
              recordsTransformed: 10,
              tombstonesPreserved: 5,
            }),
          );
        }

        // 3. GET /api/v1/apps/:appId/strict-upgrades/:migrationId
        if (req.method === 'GET' && url.pathname.includes('/strict-upgrades/')) {
          const parts = url.pathname.split('/');
          const migId = parts[parts.length - 1];

          // Simulate progression
          if (simulatedMigrationState === 'transforming') {
            simulatedMigrationState = 'publishing';
          } else if (simulatedMigrationState === 'publishing') {
            simulatedMigrationState = 'ready-v2';
            currentSyncMode = 'strict-v2';
            currentFingerprint = CANONICAL_V2_FINGERPRINT;
          }

          res.writeHead(200, { 'Content-Type': 'application/json' });
          return res.end(
            JSON.stringify({
              migrationId: migId,
              appId: 'tabby-rush',
              state: simulatedMigrationState,
              sourceVersion: 'strict-v1',
              targetVersion: 'strict-v2',
              recordsTransformed: 50,
              tombstonesPreserved: 5,
              error: simulatedError,
            }),
          );
        }

        res.writeHead(404);
        res.end();
      });
    });

    await new Promise((resolve) => {
      mockServer.listen(0, '127.0.0.1', () => {
        mockPort = mockServer.address().port;
        serverOrigin = `http://127.0.0.1:${mockPort}`;
        resolve();
      });
    });
  });

  after(async () => {
    if (mockServer) {
      await new Promise((resolve) => mockServer.close(resolve));
    }
  });

  it('exits 0 with usage instructions on --help', async () => {
    const { code, stdout } = await runScript(['--help']);
    assert.equal(code, 0);
    assert.ok(stdout.includes('Usage: node scripts/hub-strict-upgrade.mjs'));
    assert.ok(stdout.includes('--dry-run'));
    assert.ok(stdout.includes('--status-only'));
  });

  it('exits nonzero and errors when token is missing', async () => {
    const { code, stderr } = await runScript(['--origin', serverOrigin, '--token', ''], {
      GLEAN_ADMIN_TOKEN: '',
    });
    assert.equal(code, 1);
    assert.ok(stderr.includes('Admin token required'));
  });

  it('redacts sensitive bearer tokens and bare token strings in logs upon error', async () => {
    const sensitiveToken = 'super-secret-admin-token-12345';
    const { stdout, stderr } = await runScript(
      ['--origin', 'http://127.0.0.1:1', '--token', sensitiveToken, '--timeout', '1000'],
      { GLEAN_ADMIN_TOKEN: sensitiveToken },
    );
    assert.ok(!stderr.includes(sensitiveToken), 'Secret token must not appear in stderr');
    assert.ok(!stdout.includes(sensitiveToken), 'Secret token must not appear in stdout');
  });

  it('fast-fails immediately without polling when server returns HTTP 4xx rejection', async () => {
    currentSyncMode = 'strict-v1';
    currentFingerprint = CANONICAL_V1_FINGERPRINT;
    rejectWith400 = true;

    const { code, stderr, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--poll-interval',
      '100',
      '--timeout',
      '5000',
    ]);

    rejectWith400 = false;
    assert.equal(code, 1);
    assert.ok(stderr.includes('Upgrade request rejected by server (400)'));
    assert.ok(!stdout.includes('Polling migration'), 'Must not enter polling loop on 4xx rejection');
  });

  it('executes preflight dry-run without mutating schema version', async () => {
    currentSyncMode = 'strict-v1';
    currentFingerprint = CANONICAL_V1_FINGERPRINT;
    const { code, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--dry-run',
    ]);
    assert.equal(code, 0);
    assert.ok(stdout.includes('Dry run PASSED'));
    assert.ok(stdout.includes('eligible live records=50'));
    assert.ok(stdout.includes('tombstones=5'));
    assert.equal(currentSyncMode, 'strict-v1');
  });

  it('executes full live upgrade with bounded polling and final read-back assertion', async () => {
    currentSyncMode = 'strict-v1';
    currentFingerprint = CANONICAL_V1_FINGERPRINT;
    simulatedMigrationState = 'transforming';
    receivedMigrationIds = [];

    const { code, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--poll-interval',
      '100',
      '--timeout',
      '5000',
    ]);

    assert.equal(code, 0);
    assert.ok(stdout.includes('Initiating upgrade with Migration ID:'));
    assert.ok(stdout.includes('ready-v2'));
    assert.ok(stdout.includes('Performing mandatory final contract read-back assertion...'));
    assert.ok(stdout.includes('VERIFIED: Application \'tabbyrush\' successfully upgraded to strict-v2'));
    assert.equal(currentSyncMode, 'strict-v2');
  });

  it('handles interrupted POST by recovering status using the same migration ID', async () => {
    currentSyncMode = 'strict-v1';
    currentFingerprint = CANONICAL_V1_FINGERPRINT;
    simulatedMigrationState = 'transforming';
    shouldDropInitialPost = true;
    receivedMigrationIds = [];

    const explicitMigId = '77777777-7777-4777-8777-777777777777';

    const { code, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--migration-id',
      explicitMigId,
      '--poll-interval',
      '100',
      '--timeout',
      '5000',
    ]);

    assert.equal(code, 0);
    assert.ok(stdout.includes('Initial POST request was interrupted'));
    assert.ok(stdout.includes(`Re-using Migration ID '${explicitMigId}' to poll for status recovery`));
    assert.ok(stdout.includes('VERIFIED'));
  });

  it('handles --status-only query for an existing migration and runs final assertion', async () => {
    currentSyncMode = 'strict-v2';
    currentFingerprint = CANONICAL_V2_FINGERPRINT;
    simulatedMigrationState = 'ready-v2';

    const { code, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--migration-id',
      '88888888-8888-4888-8888-888888888888',
      '--status-only',
    ]);

    assert.equal(code, 0);
    assert.ok(stdout.includes('Status: state=ready-v2'));
    assert.ok(stdout.includes('validating final contract read-back'));
    assert.ok(stdout.includes('VERIFIED'));
  });

  it('fails with exit code 1 if final read-back schema does not match canonical columns', async () => {
    currentSyncMode = 'strict-v2';
    currentFingerprint = CANONICAL_V2_FINGERPRINT;
    simulatedMigrationState = 'ready-v2';
    returnCorruptedV2Contract = true;

    const { code, stderr } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--migration-id',
      '99999999-9999-4999-8999-999999999999',
      '--status-only',
    ]);

    returnCorruptedV2Contract = false;
    assert.equal(code, 1);
    assert.ok(stderr.includes('CRITICAL: Read-back schema does not contain 16 columns'));
  });

  it('fails with exit code 1 if epoch invariant is violated on read-back', async () => {
    currentSyncMode = 'strict-v2';
    currentFingerprint = CANONICAL_V2_FINGERPRINT;
    simulatedMigrationState = 'ready-v2';
    currentEpoch = '99999999-0000-4000-8000-000000000000'; // Altered epoch!

    const { code, stderr } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
      '--expected-epoch',
      '550e8400-e29b-41d4-a716-446655440000',
      '--migration-id',
      'epoch-test-migration-id',
      '--status-only',
    ]);

    currentEpoch = '550e8400-e29b-41d4-a716-446655440000'; // Reset
    assert.equal(code, 1);
    assert.ok(stderr.includes('Epoch invariant violated'));
  });

  it('validates columns when already on target version and fails if corrupt', async () => {
    currentSyncMode = 'strict-v2';
    currentFingerprint = CANONICAL_V2_FINGERPRINT;
    returnCorruptedV2Contract = true;

    const { code, stderr, stdout } = await runScript([
      '--origin',
      serverOrigin,
      '--token',
      'valid-test-token',
    ]);

    returnCorruptedV2Contract = false; // Reset
    assert.equal(code, 1);
    assert.ok(stdout.includes('reports target version'));
    assert.ok(stderr.includes('CRITICAL: Read-back schema does not contain 16 columns'));
  });

  it('redacts sensitive tokens in server-returned migration errors', async () => {
    const sensitiveToken = 'super-secret-admin-token-12345';
    simulatedMigrationState = 'failed';
    simulatedError = `Internal error with Bearer ${sensitiveToken}`;

    const { stdout, stderr } = await runScript(
      [
        '--origin',
        serverOrigin,
        '--token',
        sensitiveToken,
        '--migration-id',
        'error-redaction-test',
        '--status-only',
      ],
      { GLEAN_ADMIN_TOKEN: sensitiveToken },
    );

    simulatedMigrationState = 'transforming'; // Reset
    simulatedError = null;
    assert.ok(!stdout.includes(sensitiveToken), 'Secret token must not appear in stdout');
    assert.ok(!stderr.includes(sensitiveToken), 'Secret token must not appear in stderr');
  });
});
