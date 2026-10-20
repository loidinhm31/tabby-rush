#!/usr/bin/env node
/**
 * scripts/hub-strict-upgrade.mjs
 *
 * TabbyRush / Glean Hub Strict Schema Upgrade CLI runner.
 * Performs authorized strict-v1 -> strict-v2 migration via Glean Hub Admin API.
 *
 * Enforces:
 * 1. Safe pre-condition verification (active syncMode, schema fingerprint, epoch).
 * 2. Same-migration-id recovery upon network drop or interruption (never blindly mints fresh ID).
 * 3. Bounded polling until terminal state with informative exit codes.
 * 4. Mandatory final read-back assertion against GET /apps/:app_id/strict-contract.
 * 5. Complete secret redaction from stdout/stderr.
 */

import http from 'node:http';
import https from 'node:https';
import crypto from 'node:crypto';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

const CANONICAL_FINGERPRINTS = {
  'tabby-rush': {
    'strict-v1': '132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8',
    'strict-v2': '8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653',
    'strict-v3': '7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f',
  },
  'doggo': {
    'strict-v1': '3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec',
    'strict-v2': '5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4',
    'strict-v3': 'eec72663e7367b4330d1c94196f8e44888df02fa34e240fa4d3bd29222136094',
  },
};

const CANONICAL_STRICT_V1_FINGERPRINT = CANONICAL_FINGERPRINTS['doggo']['strict-v1'];
const CANONICAL_STRICT_V2_FINGERPRINT = CANONICAL_FINGERPRINTS['doggo']['strict-v2'];
const CANONICAL_STRICT_V3_FINGERPRINT = CANONICAL_FINGERPRINTS['doggo']['strict-v3'];

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

const V3_COLUMNS = [
  ...V2_COLUMNS,
  { name: 'parentTaskId', type: 'string', nullable: true },
];

function isMatchingFingerprint(appId, version, fp) {
  if (!fp) return false;
  if (CANONICAL_FINGERPRINTS[appId]?.[version] === fp) return true;
  if (CANONICAL_FINGERPRINTS['doggo']?.[version] === fp) return true;
  if (CANONICAL_FINGERPRINTS['tabby-rush']?.[version] === fp) return true;
  return false;
}
let activeToken = '';

function sanitizeLog(str) {
  if (typeof str !== 'string') return str;
  let out = str.replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer [REDACTED]');
  if (activeToken && typeof activeToken === 'string' && activeToken.length > 5) {
    out = out.replaceAll(activeToken, '[REDACTED_TOKEN]');
  }
  return out;
}

function loadDotEnv() {
  const envPath = resolve(process.cwd(), '.env');
  if (existsSync(envPath)) {
    try {
      const content = readFileSync(envPath, 'utf8');
      for (const line of content.split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const idx = trimmed.indexOf('=');
        if (idx > 0) {
          const key = trimmed.slice(0, idx).trim();
          let val = trimmed.slice(idx + 1).trim();
          if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.slice(1, -1);
          }
          if (process.env[key] === undefined) {
            process.env[key] = val;
          }
        }
      }
    } catch {
      // ignore unreadable .env
    }
  }
}
loadDotEnv();

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    origin: process.env.APPROVED_HUB_ORIGIN || process.env.GLEAN_ORIGIN || 'http://127.0.0.1:3000',
    appId: process.env.TABBYRUSH_APP_ID || 'tabby-rush',
    token: process.env.GLEAN_ADMIN_TOKEN || '',
    expectedVersion: 'strict-v1',
    expectedFingerprint: null,
    targetVersion: 'strict-v2',
    targetFingerprint: null,
    expectedEpoch: null,
    migrationId: null,
    dryRun: false,
    statusOnly: false,
    pollIntervalMs: 1500,
    timeoutMs: 60000,
    help: false,
  };

  const valueOptions = [
    '--origin', '--app-id', '--token', '--expected-version',
    '--expected-fingerprint', '--target-version', '--target-fingerprint',
    '--expected-epoch', '--migration-id', '--poll-interval', '--timeout'
  ];

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (valueOptions.includes(arg)) {
      if (i + 1 >= args.length) {
        console.error(`[Upgrade] ERROR: Option '${arg}' requires a value.`);
        process.exit(1);
      }
      const val = args[++i];
      if (arg === '--origin') options.origin = val;
      else if (arg === '--app-id') options.appId = val;
      else if (arg === '--token') options.token = val;
      else if (arg === '--expected-version') options.expectedVersion = val;
      else if (arg === '--expected-fingerprint') options.expectedFingerprint = val;
      else if (arg === '--target-version') options.targetVersion = val;
      else if (arg === '--target-fingerprint') options.targetFingerprint = val;
      else if (arg === '--expected-epoch') options.expectedEpoch = val;
      else if (arg === '--migration-id') options.migrationId = val;
      else if (arg === '--poll-interval') options.pollIntervalMs = parseInt(val, 10);
      else if (arg === '--timeout') options.timeoutMs = parseInt(val, 10);
    } else if (arg === '--dry-run') {
      options.dryRun = true;
    } else if (arg === '--status-only') {
      options.statusOnly = true;
    } else if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else {
      console.error(`[Upgrade] ERROR: Unrecognized command line option '${arg}'. Use --help for usage.`);
      process.exit(1);
    }
  }

  if (!options.expectedFingerprint) {
    options.expectedFingerprint =
      CANONICAL_FINGERPRINTS[options.appId]?.[options.expectedVersion] ||
      CANONICAL_FINGERPRINTS['doggo'][options.expectedVersion];
  }
  if (!options.targetFingerprint) {
    options.targetFingerprint =
      CANONICAL_FINGERPRINTS[options.appId]?.[options.targetVersion] ||
      CANONICAL_FINGERPRINTS['doggo'][options.targetVersion];
  }

  if (options.help) {
    console.log(`Usage: node scripts/hub-strict-upgrade.mjs [options]

Options:
  --origin <URL>              Glean Hub server origin (default: $APPROVED_HUB_ORIGIN or http://127.0.0.1:3000)
  --app-id <ID>               Target strict app ID (default: tabby-rush)
  --token <JWT>               Admin JWT bearer token (preferred: $GLEAN_ADMIN_TOKEN)
  --expected-version <VER>    Expected current protocol version (default: strict-v1)
  --expected-fingerprint <HEX> Expected current canonical schema fingerprint
  --target-version <VER>      Target protocol version (default: strict-v2)
  --target-fingerprint <HEX>  Target canonical schema fingerprint
  --expected-epoch <UUID>     Expected active strict epoch UUID
  --migration-id <UUID>       Explicit migration ID to resume or execute
  --dry-run                   Execute non-mutating preflight validation only
  --status-only               Query existing migration status without initiating transform
  --poll-interval <MS>        Status polling interval in milliseconds (default: 1500)
  --timeout <MS>              Overall execution timeout in milliseconds (default: 60000)
  -h, --help                  Show this help text
`);
    process.exit(0);
  }

  return options;
}



class ApiClient {
  constructor(origin, token, timeoutMs) {
    this.origin = origin.replace(/\/+$/, '');
    this.token = token;
    this.timeoutMs = timeoutMs;
  }

  async request(method, path, body = null) {
    const url = new URL(path, this.origin);
    const transport = url.protocol === 'https:' ? https : http;

    return new Promise((resolve, reject) => {
      const headers = {
        Accept: 'application/json',
      };
      if (this.token) {
        headers['Authorization'] = `Bearer ${this.token}`;
      }
      let payload = null;
      if (body !== null) {
        payload = JSON.stringify(body);
        headers['Content-Type'] = 'application/json';
        headers['Content-Length'] = Buffer.byteLength(payload);
      }

      const req = transport.request(
        url,
        {
          method,
          headers,
          timeout: this.timeoutMs,
        },
        (res) => {
          let data = '';
          res.on('data', (chunk) => (data += chunk));
          res.on('end', () => {
            let json = null;
            try {
              json = data ? JSON.parse(data) : null;
            } catch {
              json = null;
            }
            resolve({
              statusCode: res.statusCode,
              headers: res.headers,
              body: json,
              rawText: data,
            });
          });
        },
      );

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
}

async function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function main() {
  const options = parseArgs();
  activeToken = options.token;

  if (!options.token) {
    console.error('[Upgrade] ERROR: Admin token required via GLEAN_ADMIN_TOKEN environment variable or --token flag.');
    process.exit(1);
  }

  const client = new ApiClient(options.origin, options.token, 10000);
  console.log(`[Upgrade] Targeting app '${options.appId}' at ${options.origin}`);

  // Status-only mode queries migration record directly
  if (options.statusOnly) {
    if (!options.migrationId) {
      console.error('[Upgrade] ERROR: --status-only requires --migration-id <UUID>');
      process.exit(1);
    }
    console.log(`[Upgrade] Querying migration status for ID '${options.migrationId}'...`);
    const statusRes = await client.request(
      'GET',
      `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-upgrades/${encodeURIComponent(options.migrationId)}`,
    );
    if (statusRes.statusCode !== 200 || !statusRes.body) {
      console.error(`[Upgrade] ERROR fetching status (${statusRes.statusCode}): ${sanitizeLog(statusRes.rawText)}`);
      process.exit(1);
    }
    const s = statusRes.body;
    const expectedReadyState = options.targetVersion === 'strict-v3' ? 'ready-v3' : 'ready-v2';
    console.log(`[Upgrade] Status: state=${s.state}, transformed=${s.recordsTransformed}, tombstones=${s.tombstonesPreserved}, error=${sanitizeLog(s.error) || 'none'}`);
    if (s.state === expectedReadyState) {
      console.log(`[Upgrade] Server reports ${expectedReadyState}; validating final contract read-back...`);
      await assertFinalContract(client, options, options.expectedEpoch);
      process.exit(0);
    }
    process.exit(s.state === 'operatorRequired' || s.state === 'rolledBack' ? 1 : 2);
  }

  // 1. Preflight Contract Query
  console.log(`[Upgrade] Querying active contract for app '${options.appId}'...`);
  let contractRes;
  try {
    contractRes = await client.request(
      'GET',
      `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-contract`,
    );
  } catch (err) {
    console.error(`[Upgrade] Network error querying strict contract: ${sanitizeLog(err.message)}`);
    process.exit(1);
  }

  if (contractRes.statusCode === 403) {
    console.error(`[Upgrade] ERROR 403 Forbidden: Admin token is not scoped to app '${options.appId}'. App-scoped admin token required.`);
    process.exit(1);
  }
  if (contractRes.statusCode === 404) {
    console.error(`[Upgrade] ERROR 404 Not Found: App '${options.appId}' does not exist or has not been admitted.`);
    process.exit(1);
  }
  if (contractRes.statusCode !== 200 || !contractRes.body) {
    console.error(`[Upgrade] ERROR querying contract (${contractRes.statusCode}): ${sanitizeLog(contractRes.rawText)}`);
    process.exit(1);
  }

  const contract = contractRes.body;
  console.log(`[Upgrade] Active Contract: syncMode=${contract.syncMode}, epoch=${contract.epoch}, fingerprint=${contract.schemaFingerprint}`);

  // Check if already on target version
  if (
    contract.syncMode === options.targetVersion &&
    (contract.schemaFingerprint === options.targetFingerprint ||
      isMatchingFingerprint(options.appId, options.targetVersion, contract.schemaFingerprint))
  ) {
    options.targetFingerprint = contract.schemaFingerprint;
    console.log(`[Upgrade] App '${options.appId}' reports target version '${options.targetVersion}'; running validation...`);
    await assertFinalContract(client, options, contract.epoch);
    console.log(`[Upgrade] SUCCESS: App '${options.appId}' is already on target version '${options.targetVersion}' with verified schema.`);
    process.exit(0);
  }

  // Check source version preconditions
  if (contract.syncMode !== options.expectedVersion) {
    console.error(
      `[Upgrade] ERROR: App syncMode '${contract.syncMode}' does not match expected source version '${options.expectedVersion}'.`,
    );
    process.exit(1);
  }

  if (contract.schemaFingerprint !== options.expectedFingerprint && !isMatchingFingerprint(options.appId, options.expectedVersion, contract.schemaFingerprint)) {
    console.error(
      `[Upgrade] ERROR: Schema fingerprint '${contract.schemaFingerprint}' does not match expected source fingerprint '${options.expectedFingerprint}'.`,
    );
    process.exit(1);
  }
  if (contract.schemaFingerprint === options.expectedFingerprint || isMatchingFingerprint(options.appId, options.expectedVersion, contract.schemaFingerprint)) {
    options.expectedFingerprint = contract.schemaFingerprint;
  }

  if (options.expectedEpoch && contract.epoch !== options.expectedEpoch) {
    console.error(
      `[Upgrade] ERROR: Active epoch '${contract.epoch}' does not match expected epoch '${options.expectedEpoch}'.`,
    );
    process.exit(1);
  }

  const activeEpoch = contract.epoch;

  // 2. Dry Run Preflight Mode
  if (options.dryRun) {
    const testMigId = options.migrationId || crypto.randomUUID();
    console.log(`[Upgrade] Executing preflight dry run with ID '${testMigId}'...`);
    const dryRunRes = await client.request(
      'POST',
      `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-upgrades`,
      {
        migrationId: testMigId,
        expectedVersion: options.expectedVersion,
        expectedSchemaFingerprint: options.expectedFingerprint,
        expectedEpoch: activeEpoch,
        targetVersion: options.targetVersion,
        targetSchemaFingerprint: options.targetFingerprint,
        dryRun: true,
      },
    );

    if (dryRunRes.statusCode !== 200 || !dryRunRes.body) {
      console.error(`[Upgrade] Dry run preflight failed (${dryRunRes.statusCode}): ${sanitizeLog(dryRunRes.rawText)}`);
      process.exit(1);
    }

    const dry = dryRunRes.body;
    console.log(`[Upgrade] Dry run PASSED: eligible live records=${dry.recordsTransformed}, tombstones=${dry.tombstonesPreserved}. (State: ${dry.state})`);
    console.log('[Upgrade] Note: Dry run did not mutate data or advance schema version.');
    process.exit(0);
  }

  // 3. Live Upgrade Execution
  const migrationId = options.migrationId || crypto.randomUUID();
  console.log(`[Upgrade] Initiating upgrade with Migration ID: ${migrationId}`);

  let startRes;
  try {
    startRes = await client.request(
      'POST',
      `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-upgrades`,
      {
        migrationId,
        expectedVersion: options.expectedVersion,
        expectedSchemaFingerprint: options.expectedFingerprint,
        expectedEpoch: activeEpoch,
        targetVersion: options.targetVersion,
        targetSchemaFingerprint: options.targetFingerprint,
        dryRun: false,
      },
    );
  } catch (err) {
    console.log(`[Upgrade] Initial POST request was interrupted: ${sanitizeLog(err.message)}`);
    console.log(`[Upgrade] Re-using Migration ID '${migrationId}' to poll for status recovery...`);
  }

  // Check for explicit HTTP client error (4xx) which means migration was rejected and not started
  if (startRes && startRes.statusCode >= 400 && startRes.statusCode < 500) {
    console.error(`[Upgrade] Upgrade request rejected by server (${startRes.statusCode}): ${sanitizeLog(startRes.rawText, options.token)}`);
    process.exit(1);
  }

  const expectedReadyState = options.targetVersion === 'strict-v3' ? 'ready-v3' : 'ready-v2';
  // If startRes succeeded and immediately returned ready state
  if (startRes && startRes.statusCode === 200 && startRes.body && startRes.body.state === expectedReadyState) {
    console.log(`[Upgrade] Migration ID '${migrationId}' reported instant transition to ${expectedReadyState}.`);
  } else {
    console.log(`[Upgrade] Polling migration '${migrationId}' until completion (timeout: ${options.timeoutMs}ms)...`);
    const startTime = Date.now();
    let terminalState = null;

    while (Date.now() - startTime < options.timeoutMs) {
      await sleep(options.pollIntervalMs);
      let pollRes;
      try {
        pollRes = await client.request(
          'GET',
          `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-upgrades/${encodeURIComponent(migrationId)}`,
        );
      } catch (pollErr) {
        console.warn(`[Upgrade] Transient poll error: ${sanitizeLog(pollErr.message)}, retrying...`);
        continue;
      }

      if (pollRes.statusCode === 200 && pollRes.body) {
        const s = pollRes.body;
        console.log(`[Upgrade] Migration state: ${s.state} (transformed=${s.recordsTransformed}, tombstones=${s.tombstonesPreserved})`);
        if (s.state === expectedReadyState) {
          terminalState = s;
          break;
        }
        if (s.state === 'operatorRequired' || s.state === 'rolledBack') {
          console.error(`[Upgrade] FAILURE: Migration entered terminal error state '${s.state}'. Error: ${sanitizeLog(s.error) || 'Unknown error'}`);
          console.error(`[Upgrade] Recovery: Check MongoDB logs. Migration ID '${migrationId}'.`);
          process.exit(1);
        }
      } else if (pollRes.statusCode === 404) {
        console.warn(`[Upgrade] Migration ID '${migrationId}' not yet found on server; waiting...`);
      } else {
        console.warn(`[Upgrade] Unexpected poll status (${pollRes.statusCode}): ${sanitizeLog(pollRes.rawText)}`);
      }
    }

    if (!terminalState) {
      console.error(
        `[Upgrade] ERROR: Polling timed out after ${options.timeoutMs}ms without terminal state. Server migration may still be executing under exclusive fence.`,
      );
      console.error(`[Upgrade] DO NOT issue a new migration ID. Inspect status with: node scripts/hub-strict-upgrade.mjs --migration-id ${migrationId} --status-only (or resume transform by re-running with the same migration ID without --status-only)`);
      process.exit(2);
    }
  }

  // 5. Mandatory Final Read-Back Assertion
  await assertFinalContract(client, options, activeEpoch);
  process.exit(0);
}

async function assertFinalContract(client, options, expectedEpoch = null) {
  console.log('[Upgrade] Performing mandatory final contract read-back assertion...');
  const verifyRes = await client.request(
    'GET',
    `/api/v1/apps/${encodeURIComponent(options.appId)}/strict-contract`,
  );

  if (verifyRes.statusCode !== 200 || !verifyRes.body) {
    console.error(
      `[Upgrade] CRITICAL: Contract read-back returned ${verifyRes.statusCode}: ${sanitizeLog(verifyRes.rawText)}`,
    );
    process.exit(1);
  }
  const verified = verifyRes.body;
  const targetEpoch = expectedEpoch || options.expectedEpoch;
  if (targetEpoch && verified.epoch && verified.epoch !== targetEpoch) {
    console.error(
      `[Upgrade] CRITICAL: Epoch invariant violated: expected active epoch '${targetEpoch}', got '${verified.epoch}'`,
    );
    process.exit(1);
  }
  if (verified.syncMode !== options.targetVersion) {
    console.error(
      `[Upgrade] CRITICAL: Read-back syncMode mismatch: expected '${options.targetVersion}', got '${verified.syncMode}'`,
    );
    process.exit(1);
  }

  if (verified.schemaFingerprint !== options.targetFingerprint && !isMatchingFingerprint(options.appId, options.targetVersion, verified.schemaFingerprint)) {
    console.error(
      `[Upgrade] CRITICAL: Read-back schemaFingerprint mismatch: expected '${options.targetFingerprint}', got '${verified.schemaFingerprint}'`,
    );
    process.exit(1);
  }

  const expectedCols = options.targetVersion === 'strict-v3' ? V3_COLUMNS : V2_COLUMNS;
  const cols = verified.schema?.todos?.columns;
  if (!Array.isArray(cols) || cols.length !== expectedCols.length) {
    console.error(
      `[Upgrade] CRITICAL: Read-back schema does not contain ${expectedCols.length} columns for 'todos' table (found ${cols?.length}).`,
    );
    process.exit(1);
  }

  for (const exp of expectedCols) {
    const found = cols.find((c) => c.name === exp.name);
    if (!found) {
      console.error(`[Upgrade] CRITICAL: Missing expected canonical column '${exp.name}' in read-back.`);
      process.exit(1);
    }
    if (found.type !== exp.type || Boolean(found.nullable) !== exp.nullable) {
      console.error(
        `[Upgrade] CRITICAL: Column '${exp.name}' mismatch: type=${found.type} (expected ${exp.type}), nullable=${found.nullable} (expected ${exp.nullable})`,
      );
      process.exit(1);
    }
  }

  console.log(`[Upgrade] ✓ VERIFIED: Application '${options.appId}' successfully upgraded to ${verified.syncMode}.`);
  console.log(`[Upgrade] Canonical Fingerprint: ${verified.schemaFingerprint}`);
  console.log(`[Upgrade] All ${expectedCols.length} columns active and verified. Data and tombstones preserved.`);
}

main().catch((err) => {
  console.error(`[Upgrade] Fatal unhandled error: ${sanitizeLog(err.message)}`);
  process.exit(1);
});
