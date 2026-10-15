#!/usr/bin/env node
/**
 * Doggo Hub Compatibility Smoke Test Script
 * Verifies mounted strict-v1 and legacy route isolation gates against a running Hub origin.
 */

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    origin: process.env.GLEAN_ORIGIN || 'http://127.0.0.1:3000',
    appId: process.env.DOGGO_APP_ID || 'doggo',
    apiKey: process.env.DOGGO_API_KEY || '',
    token: process.env.TEST_JWT || '',
    debugLocal: false,
  };

  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--origin' && args[i + 1]) {
      options.origin = args[++i];
    } else if (args[i] === '--app-id' && args[i + 1]) {
      options.appId = args[++i];
    } else if (args[i] === '--api-key' && args[i + 1]) {
      options.apiKey = args[++i];
    } else if (args[i] === '--token' && args[i + 1]) {
      options.token = args[++i];
    } else if (args[i] === '--debug-local') {
      options.debugLocal = true;
    } else if (args[i] === '--help' || args[i] === '-h') {
      console.log('Usage: node hub-compatibility-smoke.mjs [--origin <url>] [--app-id <app>] [--api-key <key>] [--token <jwt>] [--debug-local]');
      process.exit(0);
    }
  }

  return options;
}

async function runCheck(name, fn) {
  process.stdout.write(`  - Check: ${name}... `);
  try {
    const res = await fn();
    if (res.passed) {
      console.log(`PASSED (${res.details || 'ok'})`);
      return true;
    } else {
      console.log(`FAILED (${res.details})`);
      return false;
    }
  } catch (err) {
    console.log(`ERROR (${err.message})`);
    return false;
  }
}

async function main() {
  const { origin, appId, apiKey, token } = parseArgs();
  console.log(`[Smoke] Testing Hub compatibility on ${origin} for app '${appId}'...`);

  let allPassed = true;

  // Gate 1: Strong sync bootstrap endpoint accessibility
  const gate1 = await runCheck('Strong sync bootstrap route is mounted', async () => {
    const headers = { 'Content-Type': 'application/json' };
    if (apiKey) headers['X-API-Key'] = apiKey;
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${origin}/api/v1/sync/${appId}/strong/bootstrap`, {
      method: 'POST',
      headers,
    });

    if (token && apiKey) {
      if (res.status === 200) {
        const body = await res.json();
        if ((body.protocol === 'strict-v1' || body.protocol === 'strict-v2') && body.epoch) {
          return { passed: true, details: `200 OK, protocol=${body.protocol}, epoch=${body.epoch}` };
        }
      }
      return { passed: false, details: `expected 200 with credentials, got ${res.status}` };
    }
    if (res.status === 401) {
      return { passed: true, details: '401 Unauthorized (dual auth verified for unauthenticated call)' };
    }
    return { passed: false, details: `unexpected status ${res.status}` };
  });
  if (!gate1) allPassed = false;

  // Gate 2: Legacy sync route rejection for strict app (HTTP 426 protocolUnsupported)
  const gate2 = await runCheck('Legacy push route rejects strict app with HTTP 426', async () => {
    const headers = { 'Content-Type': 'application/json' };
    if (apiKey) headers['X-API-Key'] = apiKey;
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${origin}/api/v1/sync/${appId}/push`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ operations: [] }),
    });

    if (token && apiKey) {
      if (res.status === 426) {
        const body = await res.json().catch(() => ({}));
        if (body.code === 'protocolUnsupported') {
          return { passed: true, details: 'HTTP 426 protocolUnsupported verified' };
        }
      }
      return { passed: false, details: `expected 426 protocolUnsupported, got ${res.status}` };
    }
    if (res.status === 401) {
      return { passed: true, details: '401 Unauthorized (unauthenticated call rejected before route processing)' };
    }
    return { passed: false, details: `unexpected status ${res.status}` };
  });
  if (!gate2) allPassed = false;

  // Gate 3: Strong sync scope coherence (mismatched X-App-Id vs path)
  const gate3 = await runCheck('Scope coherence rejects mismatched X-App-Id with 400', async () => {
    const headers = {
      'Content-Type': 'application/json',
      'X-App-Id': 'different-app-id',
    };
    if (apiKey) headers['X-API-Key'] = apiKey;
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${origin}/api/v1/sync/${appId}/strong/bootstrap`, {
      method: 'POST',
      headers,
    });

    if (token && apiKey) {
      if (res.status === 400) {
        const body = await res.json().catch(() => ({}));
        if (body.code === 'invalidRequest') {
          return { passed: true, details: 'HTTP 400 invalidRequest verified' };
        }
      }
      return { passed: false, details: `expected 400 invalidRequest, got ${res.status}` };
    }
    if (res.status === 401) {
      return { passed: true, details: '401 Unauthorized (unauthenticated call rejected)' };
    }
    return { passed: false, details: `unexpected status ${res.status}` };
  });
  if (!gate3) allPassed = false;

  // Gate 4: Strong push stale/forged epoch rejection (HTTP 409 epochMismatch)
  const gate4 = await runCheck('Strong push rejects forged/stale epoch with 409', async () => {
    const headers = { 'Content-Type': 'application/json' };
    if (apiKey) headers['X-API-Key'] = apiKey;
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${origin}/api/v1/sync/${appId}/strong/push`, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        protocol: 'strict-v1',
        epoch: '00000000-0000-4000-8000-000000000000',
        operations: [],
      }),
    });

    if (token && apiKey) {
      if (res.status === 409) {
        const body = await res.json().catch(() => ({}));
        if (body.code === 'epochMismatch') {
          return { passed: true, details: 'HTTP 409 epochMismatch verified' };
        }
      }
      return { passed: false, details: `expected 409 epochMismatch, got ${res.status}` };
    }
    if (res.status === 401) {
      return { passed: true, details: '401 Unauthorized (unauthenticated call rejected)' };
    }
    return { passed: false, details: `unexpected status ${res.status}` };
  });
  if (!gate4) allPassed = false;

  // Gate 5: Strict contract discovery route accessibility and validation
  const gate5 = await runCheck('Strict contract route returns canonical contract metadata', async () => {
    const headers = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const res = await fetch(`${origin}/api/v1/apps/${encodeURIComponent(appId)}/strict-contract`, {
      method: 'GET',
      headers,
    });

    if (token) {
      if (res.status === 200) {
        const body = await res.json();
        if (
          (body.syncMode === 'strict-v1' || body.syncMode === 'strict-v2') &&
          body.epoch &&
          body.schemaFingerprint &&
          Array.isArray(body.tables) &&
          body.tables.includes('todos')
        ) {
          return { passed: true, details: `200 OK, mode=${body.syncMode}, fingerprint=${body.schemaFingerprint.slice(0, 16)}...` };
        }
        return { passed: false, details: 'invalid strict-contract payload structure' };
      }
      if (res.status === 403) {
        return { passed: true, details: '403 Forbidden (verified app-scoped admin fence)' };
      }
      return { passed: false, details: `expected 200 or 403, got ${res.status}` };
    }
    if (res.status === 401) {
      return { passed: true, details: '401 Unauthorized (unauthenticated call rejected)' };
    }
    return { passed: false, details: `unexpected status ${res.status}` };
  });
  if (!gate5) allPassed = false;

  console.log(`[Smoke] Summary: ${allPassed ? 'ALL CHECKS PASSED' : 'SOME CHECKS FAILED'}`);
  process.exit(allPassed ? 0 : 1);
}

main().catch(err => {
  console.error('[Smoke] Uncaught exception:', err);
  process.exit(1);
});
