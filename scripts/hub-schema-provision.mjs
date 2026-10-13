#!/usr/bin/env node
/**
 * Doggo Hub Schema Provisioning Script
 * Provisions strict-v1 app schema on Glean Hub, performs mandatory read-back verification,
 * and outputs the provisioned credentials with strict mode 0600 key custody.
 */

import { readFileSync, writeFileSync, chmodSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    origin: process.env.GLEAN_ORIGIN || 'http://127.0.0.1:3000',
    token: process.env.GLEAN_ADMIN_TOKEN || '',
    schemaPath: resolve(process.cwd(), 'integration/glean-hub/doggo-schema.json'),
    outPath: null,
  };

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--origin' && args[i + 1]) {
      options.origin = args[++i];
    } else if (args[i] === '--token' && args[i + 1]) {
      options.token = args[++i];
    } else if (args[i] === '--schema' && args[i + 1]) {
      options.schemaPath = resolve(process.cwd(), args[++i]);
    } else if (args[i] === '--out' && args[i + 1]) {
      options.outPath = resolve(process.cwd(), args[++i]);
    } else if (args[i] === '--help' || args[i] === '-h') {
      console.log('Usage: node hub-schema-provision.mjs [--origin <url>] [--token <jwt>] [--schema <path>] [--out <credentials.json>]');
      process.exit(0);
    }
  }

  return options;
}

function validateSchemaColumns(schema) {
  const todos = schema?.todos;
  if (!todos) {
    throw new Error("Missing 'todos' table in schema definition");
  }
  if (todos.primaryKey !== 'id') {
    throw new Error(`Invalid primaryKey '${todos.primaryKey}', expected 'id'`);
  }

  const expectedColumns = [
    { name: 'id', type: 'string', nullable: false },
    { name: 'title', type: 'string', nullable: false },
    { name: 'completed', type: 'boolean', nullable: false },
    { name: 'createdAt', type: 'integer', nullable: false },
  ];

  if (!Array.isArray(todos.columns) || todos.columns.length !== expectedColumns.length) {
    throw new Error(`Expected exactly 4 columns in 'todos', found ${todos.columns?.length}`);
  }

  for (const expected of expectedColumns) {
    const col = todos.columns.find(c => c.name === expected.name);
    if (!col) {
      throw new Error(`Missing expected column '${expected.name}'`);
    }
    if (col.type !== expected.type || Boolean(col.nullable) !== expected.nullable) {
      throw new Error(`Column '${expected.name}' mismatch: type=${col.type}, nullable=${col.nullable}`);
    }
  }
}

async function main() {
  const { origin, token, schemaPath, outPath } = parseArgs();

  if (!token) {
    console.error('Error: Admin token required via GLEAN_ADMIN_TOKEN env variable or --token parameter');
    process.exit(1);
  }

  let schemaPayload;
  try {
    const raw = readFileSync(schemaPath, 'utf8');
    schemaPayload = JSON.parse(raw);
  } catch (err) {
    console.error(`Error reading schema file at ${schemaPath}:`, err.message);
    process.exit(1);
  }

  validateSchemaColumns(schemaPayload.schema);

  console.log(`[Provision] Registering strict app '${schemaPayload.appId}' at ${origin}/api/v1/strict-apps...`);

  let apiKey = null;
  let strictEpoch = null;

  // 1. POST /api/v1/strict-apps
  const regRes = await fetch(`${origin}/api/v1/strict-apps`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify(schemaPayload),
  });

  if (regRes.status === 409) {
    console.log(`[Provision] App '${schemaPayload.appId}' is already registered; verifying existing registration idempotently...`);
  } else if (!regRes.ok) {
    const text = await regRes.text();
    console.error(`[Provision] Registration failed with status ${regRes.status}:`, text);
    process.exit(1);
  } else {
    const regData = await regRes.json();
    apiKey = regData.apiKey;
    strictEpoch = regData.strictEpoch;
    console.log(`[Provision] Registered new app: appId=${regData.appId}, epoch=${strictEpoch}`);

    // Capture and persist key IMMEDIATELY before read-back
    if (apiKey) {
      if (outPath) {
        const creds = {
          appId: regData.appId,
          syncMode: regData.syncMode,
          epoch: strictEpoch,
          apiKey,
        };
        writeFileSync(outPath, JSON.stringify(creds, null, 2), { mode: 0o600 });
        try { chmodSync(outPath, 0o600); } catch (_) {}
        console.log(`[Provision] Credentials written securely to ${outPath} (mode 0600)`);
      } else {
        console.log(`[Provision] Key issued: ${apiKey}`);
      }
    }
  }

  // 2. Mandatory Read-Back: GET /api/v1/apps/:app_id
  console.log(`[Provision] Performing mandatory schema read-back from ${origin}/api/v1/apps/${schemaPayload.appId}...`);
  const readBackRes = await fetch(`${origin}/api/v1/apps/${schemaPayload.appId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!readBackRes.ok) {
    const text = await readBackRes.text();
    console.error(`[Provision] Read-back failed with status ${readBackRes.status}:`, text);
    process.exit(1);
  }

  const readBackData = await readBackRes.json();

  if (readBackData.syncMode !== 'strict-v1') {
    console.error(`[Provision] Read-back validation failure: syncMode is '${readBackData.syncMode}', expected 'strict-v1'`);
    process.exit(1);
  }

  if (!readBackData.strictEpoch) {
    console.error('[Provision] Read-back validation failure: strictEpoch is missing');
    process.exit(1);
  }

  if (strictEpoch && readBackData.strictEpoch !== strictEpoch) {
    console.error(`[Provision] Read-back validation failure: strictEpoch mismatch (${readBackData.strictEpoch} != ${strictEpoch})`);
    process.exit(1);
  }
  strictEpoch = readBackData.strictEpoch;

  validateSchemaColumns(readBackData.schema);

  console.log('[Provision] Read-back verified successfully. App is admitted and ready.');

  if (outPath && !apiKey && existsSync(outPath)) {
    console.log(`[Provision] Preserving existing credentials file at ${outPath}`);
  }
}

main().catch(err => {
  console.error('[Provision] Uncaught exception:', err);
  process.exit(1);
});
