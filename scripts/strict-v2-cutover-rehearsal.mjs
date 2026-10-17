#!/usr/bin/env node
/**
 * scripts/strict-v2-cutover-rehearsal.mjs
 *
 * Operator Tooling Rehearsal and Self-Test Runner for Strict-v2 Cutover.
 *
 * Exercises operator tooling workflows (hub-strict-upgrade.mjs) including:
 * 1. CLI runner help output and parameter validation.
 * 2. Token requirement verification and fail-closed exit.
 * 3. Token redaction across CLI stdout/stderr.
 * 4. Non-mutating preflight dry-run validation.
 * 5. Interrupted batch recovery and same-migration-id idempotent resumption.
 * 6. Status inspection (--status-only) distinct from transformation resumption.
 * 7. Final schema read-back assertion (canonical 16-column fingerprint 5f539551...).
 *
 * IMPORTANT NOTE:
 * This script serves as an operator tooling self-test and staging rehearsal harness.
 * In-process mock tests verify CLI invocation sequencing, error handling, and read-back assertions.
 * They do NOT substitute for live cluster multi-instance Mongo migration gates (Gate G3).
 */

import http from 'node:http';
import { spawn } from 'node:child_process';
import { resolve } from 'node:path';

const CANONICAL_V1_FINGERPRINT =
  '3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec';
const CANONICAL_V2_FINGERPRINT =
  '5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4';

const V1_COLUMNS = [
  { name: 'id', type: 'string', nullable: false },
  { name: 'title', type: 'string', nullable: false },
  { name: 'completed', type: 'boolean', nullable: false },
  { name: 'createdAt', type: 'integer', nullable: false },
];

const V2_COLUMNS = [
  ...V1_COLUMNS,
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

const UPGRADE_CLI_PATH = resolve(process.cwd(), 'scripts/hub-strict-upgrade.mjs');

function runUpgradeCli(args, env = {}) {
  return new Promise((res) => {
    const child = spawn(process.execPath, [UPGRADE_CLI_PATH, ...args], {
      env: { ...process.env, ...env },
    });
    let stdout = '';
    let stderr = '';
    child.on('error', (err) => {
      res({ code: 1, stdout, stderr: `${stderr}\n${err.message}`.trim() });
    });
    child.stdout.on('data', (d) => (stdout += d.toString()));
    child.stderr.on('data', (d) => (stderr += d.toString()));
    child.on('close', (code) => {
      res({ code, stdout, stderr });
    });
  });
}

/**
 * Creates an in-process mock Glean Hub server matching hub-strict-upgrade.mjs expectations.
 */
function createMockHubServer() {
  let syncMode = 'strict-v1';
  let schemaFingerprint = CANONICAL_V1_FINGERPRINT;
  let epoch = '550e8400-e29b-41d4-a716-446655440000';
  let shouldDropNextPost = false;
  let returnCorruptedV2 = false;
  let simulatedState = 'transforming';
  let mockPort = 0;

  const server = http.createServer((req, res) => {
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
        const cols =
          syncMode === 'strict-v2'
            ? returnCorruptedV2
              ? V2_COLUMNS.slice(0, 10)
              : V2_COLUMNS
            : V1_COLUMNS;

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            appId: 'tabby-rush',
            syncMode,
            epoch,
            schemaFingerprint,
            tables: ['todos'],
            schema: {
              todos: {
                columns: cols,
                primaryKey: 'id',
              },
            },
          })
        );
        return;
      }

      // 2. POST /api/v1/apps/:appId/strict-upgrades
      if (req.method === 'POST' && url.pathname.endsWith('/strict-upgrades')) {
        if (shouldDropNextPost) {
          shouldDropNextPost = false;
          req.destroy();
          return;
        }

        if (body?.dryRun) {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(
            JSON.stringify({
              migrationId: body.migrationId,
              appId: 'tabby-rush',
              state: 'preflight-passed',
              sourceVersion: 'strict-v1',
              targetVersion: 'strict-v2',
              recordsTransformed: 42,
              tombstonesPreserved: 5,
            })
          );
          return;
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            migrationId: body.migrationId,
            appId: 'tabby-rush',
            state: simulatedState,
            sourceVersion: 'strict-v1',
            targetVersion: 'strict-v2',
            recordsTransformed: 10,
            tombstonesPreserved: 5,
          })
        );
        return;
      }

      // 3. GET /api/v1/apps/:appId/strict-upgrades/:migrationId
      if (req.method === 'GET' && url.pathname.includes('/strict-upgrades/')) {
        const parts = url.pathname.split('/');
        const migId = parts[parts.length - 1];

        if (simulatedState === 'transforming') {
          simulatedState = 'publishing';
        } else if (simulatedState === 'publishing') {
          simulatedState = 'ready-v2';
          syncMode = 'strict-v2';
          schemaFingerprint = CANONICAL_V2_FINGERPRINT;
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(
          JSON.stringify({
            migrationId: migId,
            appId: 'tabby-rush',
            state: simulatedState,
            sourceVersion: 'strict-v1',
            targetVersion: 'strict-v2',
            recordsTransformed: 42,
            tombstonesPreserved: 5,
          })
        );
        return;
      }

      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'Not found' }));
    });
  });

  return {
    server,
    listen: () =>
      new Promise((resolve) => {
        server.listen(0, '127.0.0.1', () => {
          mockPort = server.address().port;
          resolve({ port: mockPort, origin: `http://127.0.0.1:${mockPort}` });
        });
      }),
    close: () => new Promise((resolve) => server.close(resolve)),
    resetState: () => {
      syncMode = 'strict-v1';
      schemaFingerprint = CANONICAL_V1_FINGERPRINT;
      shouldDropNextPost = false;
      returnCorruptedV2 = false;
      simulatedState = 'transforming';
    },
    setShouldDropNextPost: (val) => {
      shouldDropNextPost = val;
    },
    setReturnCorruptedV2: (val) => {
      returnCorruptedV2 = val;
    },
    setSimulatedState: (state) => {
      simulatedState = state;
    },
    setSyncMode: (mode) => {
      syncMode = mode;
    },
    setSchemaFingerprint: (fp) => {
      schemaFingerprint = fp;
    },
    getSyncMode: () => syncMode,
    getFingerprint: () => schemaFingerprint,
  };
}

async function runSelfTests() {
  console.log('================================================================');
  console.log('TabbyRush Strict-v2 Operator Tooling Rehearsal & Self-Test Suite');
  console.log('Mode: In-Process Tooling Mock Self-Test');
  console.log('================================================================\n');

  const mock = createMockHubServer();
  const { origin } = await mock.listen();

  let passedChecks = 0;
  let totalChecks = 0;

  async function assertCheck(name, fn) {
    totalChecks++;
    process.stdout.write(`  [Rehearsal ${totalChecks}] ${name}... `);
    try {
      await fn();
      console.log('PASS');
      passedChecks++;
    } catch (err) {
      console.log(`FAIL\n    Error: ${err.message}`);
    }
  }

  try {
    // Check 1: CLI --help instructions output cleanly
    await assertCheck('CLI runner outputs comprehensive help text on -h/--help', async () => {
      const { code, stdout } = await runUpgradeCli(['--help']);
      if (code !== 0) throw new Error(`Expected exit code 0, got ${code}`);
      if (!stdout.includes('Usage: node scripts/hub-strict-upgrade.mjs')) {
        throw new Error('Missing usage in --help');
      }
      if (!stdout.includes('--dry-run') || !stdout.includes('--migration-id')) {
        throw new Error('Missing key options in --help');
      }
    });

    // Check 2: Preflight validation with missing or invalid token fails fast
    await assertCheck('CLI fast-fails with exit code 1 when bearer token is absent', async () => {
      const { code, stderr } = await runUpgradeCli(['--origin', origin, '--token', ''], {
        GLEAN_ADMIN_TOKEN: '',
      });
      if (code !== 1) throw new Error(`Expected exit code 1, got ${code}`);
      if (!stderr.includes('Admin token required')) {
        throw new Error(`Unexpected error message: ${stderr}`);
      }
    });

    // Check 3: Bearer token is strictly redacted from error messages and logs
    await assertCheck('CLI redacts sensitive admin bearer token from output logs', async () => {
      const sensitiveToken = 'SECRET_OPERATOR_JWT_xyz123_DO_NOT_LEAK';
      const { code, stdout, stderr } = await runUpgradeCli(
        ['--origin', 'http://127.0.0.1:1', '--token', sensitiveToken, '--timeout', '500'],
        { GLEAN_ADMIN_TOKEN: sensitiveToken }
      );
      if (code === 0) throw new Error('Expected failure on connection refused');
      if (stdout.includes(sensitiveToken) || stderr.includes(sensitiveToken)) {
        throw new Error('Sensitive bearer token leaked in CLI output!');
      }
    });

    // Check 4: Non-mutating preflight dry-run executes cleanly without mutating v1 schema
    await assertCheck('Preflight dry-run verifies preconditions without mutating v1 schema', async () => {
      mock.resetState();
      const { code, stdout } = await runUpgradeCli([
        '--origin',
        origin,
        '--app-id',
        'tabbyrush',
        '--token',
        'valid-admin-token',
        '--dry-run',
      ]);
      if (code !== 0) throw new Error(`Dry-run exited with code ${code}`);
      if (!stdout.includes('Dry run PASSED')) {
        throw new Error(`Expected 'Dry run PASSED', got: ${stdout}`);
      }
      if (mock.getSyncMode() !== 'strict-v1') {
        throw new Error(`Dry run mutated syncMode to ${mock.getSyncMode()}!`);
      }
    });

    // Check 5: Interrupted network POST recovery via same migration ID
    await assertCheck('Interrupted POST recovers status and completes using identical migration ID', async () => {
      mock.resetState();
      mock.setShouldDropNextPost(true);

      const migrationId = '77777777-7777-4777-8777-777777777777';
      const { code, stdout } = await runUpgradeCli([
        '--origin',
        origin,
        '--app-id',
        'tabbyrush',
        '--token',
        'valid-admin-token',
        '--migration-id',
        migrationId,
        '--poll-interval',
        '100',
        '--timeout',
        '5000',
      ]);

      if (code !== 0) throw new Error(`Interrupted upgrade exited with code ${code}`);
      if (!stdout.includes('Initial POST request was interrupted')) {
        throw new Error(`Expected recovery notice for dropped POST, got: ${stdout}`);
      }
      if (mock.getSyncMode() !== 'strict-v2') {
        throw new Error('Failed to reach strict-v2 after resume');
      }
      if (mock.getFingerprint() !== CANONICAL_V2_FINGERPRINT) {
        throw new Error('Schema fingerprint mismatch after recovery');
      }
    });

    // Check 6: Status-only query verifies existing migration without starting new transform
    await assertCheck('Status-only query asserts active status without initiating mutation', async () => {
      mock.resetState();
      mock.setSyncMode('strict-v2');
      mock.setSchemaFingerprint(CANONICAL_V2_FINGERPRINT);
      mock.setSimulatedState('ready-v2');

      const migrationId = '88888888-8888-4888-8888-888888888888';
      const { code, stdout } = await runUpgradeCli([
        '--origin',
        origin,
        '--app-id',
        'tabbyrush',
        '--token',
        'valid-admin-token',
        '--migration-id',
        migrationId,
        '--status-only',
      ]);

      if (code !== 0) throw new Error(`Status-only query exited with code ${code}`);
      if (!stdout.includes('state=ready-v2')) {
        throw new Error(`Expected ready-v2 status output, got: ${stdout}`);
      }
      if (!stdout.includes('VERIFIED')) {
        throw new Error('Missing final read-back assertion in status query');
      }
    });

    // Check 7: Mismatched final schema read-back fails closed
    await assertCheck('CLI fails closed with exit code 1 if final read-back columns diverge', async () => {
      mock.resetState();
      mock.setSyncMode('strict-v2');
      mock.setSchemaFingerprint(CANONICAL_V2_FINGERPRINT);
      mock.setSimulatedState('ready-v2');
      mock.setReturnCorruptedV2(true);

      const migrationId = '99999999-9999-4999-8999-999999999999';
      const { code, stderr } = await runUpgradeCli([
        '--origin',
        origin,
        '--app-id',
        'tabbyrush',
        '--token',
        'valid-admin-token',
        '--migration-id',
        migrationId,
        '--status-only',
      ]);

      if (code !== 1) throw new Error(`Expected exit code 1 on column mismatch, got ${code}`);
      if (!stderr.includes('Read-back schema does not contain 16 columns')) {
        throw new Error(`Unexpected error message: ${stderr}`);
      }
    });

    // Check 8: Staging child invocation passes credentials strictly via environment, keeping argv token-free
    await assertCheck('CLI consumes admin token from GLEAN_ADMIN_TOKEN without requiring --token on argv', async () => {
      mock.resetState();
      const sensitiveEnvToken = 'ENV_ONLY_TOKEN_KEEP_OFF_ARGV_XYZ';
      const { code, stdout } = await runUpgradeCli(
        ['--origin', origin, '--app-id', 'tabbyrush', '--dry-run'],
        { GLEAN_ADMIN_TOKEN: 'valid-admin-token' }
      );
      if (code !== 0) throw new Error(`Dry-run with env token exited with code ${code}`);
      if (!stdout.includes('Dry run PASSED')) {
        throw new Error('Expected dry-run success when token supplied solely via GLEAN_ADMIN_TOKEN');
      }
    });
  } finally {
    await mock.close();
  }

  console.log('\n================================================================');
  console.log(`Rehearsal Self-Test Summary: ${passedChecks}/${totalChecks} checks passed`);
  console.log('================================================================');

  if (passedChecks !== totalChecks) {
    process.exit(1);
  }
}

function printUsage() {
  console.log(`Usage: node scripts/strict-v2-cutover-rehearsal.mjs [options]

Options:
  --self-test                     Run the in-process operator tooling mock self-test (default)
  --staging-origin <URL>          Target disposable staging Hub origin (requires --confirm-disposable-staging)
  --staging-token <JWT>           Operator admin JWT token for staging origin
  --app-id <ID>                   Target application ID on staging (default: tabbyrush)
  --confirm-disposable-staging    Explicit safety confirmation that target is a disposable staging environment
  -h, --help                      Show this help text
`);
}

async function main() {
  const args = process.argv.slice(2);
  let stagingOrigin = '';
  let stagingToken = process.env.GLEAN_ADMIN_TOKEN || '';
  let appId = 'tabby-rush';
  let confirmedStaging = false;

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--help' || args[i] === '-h') {
      printUsage();
      process.exit(0);
    } else if (args[i] === '--staging-origin' && args[i + 1]) {
      stagingOrigin = args[++i];
    } else if (args[i] === '--staging-token' && args[i + 1]) {
      stagingToken = args[++i];
    } else if (args[i] === '--app-id' && args[i + 1]) {
      appId = args[++i];
    } else if (args[i] === '--confirm-disposable-staging') {
      confirmedStaging = true;
    } else if (args[i] === '--self-test') {
      // default behavior
    }
  }

  if (stagingOrigin) {
    if (!confirmedStaging) {
      console.error(
        'ERROR: Live staging rehearsal requires explicit --confirm-disposable-staging flag.'
      );
      console.error(
        'Production cutover is strictly gated. Do NOT run against production origins.'
      );
      process.exit(1);
    }
    if (!stagingToken) {
      console.error('ERROR: Staging rehearsal requires admin token (--staging-token or $GLEAN_ADMIN_TOKEN).');
      process.exit(1);
    }
    console.log(`[Staging Rehearsal] Running preflight check against staging origin: ${stagingOrigin}`);
    const { code, stdout, stderr } = await runUpgradeCli(
      ['--origin', stagingOrigin, '--app-id', appId, '--dry-run'],
      { GLEAN_ADMIN_TOKEN: stagingToken }
    );
    const redact = (s) => (stagingToken ? s.replaceAll(stagingToken, '[REDACTED_TOKEN]') : s);
    console.log(redact(stdout));
    if (stderr) console.error(redact(stderr));
    process.exit(code);
  }

  // Default: run mock self-test suite
  await runSelfTests();
}

main().catch((err) => {
  console.error('Fatal rehearsal error:', err);
  process.exit(1);
});
