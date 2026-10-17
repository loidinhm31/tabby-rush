#!/usr/bin/env node
/**
 * scripts/debug-fault-proxy.mjs
 *
 * Dedicated loopback proxy for Android and multiplatform sync fault-injection testing.
 * Invariants:
 * - Binds strictly to 127.0.0.1 (never 0.0.0.0 or public interfaces).
 * - Allowlisted strict auth and strong sync route paths only.
 * - Zero payload, token, or header logging.
 * - Redacted rejected path logging to prevent sensitive text disclosure.
 * - Deterministic fault injection: drop-push-response, drop-refresh-response, drop-legacy-receipts-response.
 * - Robust stream-error and client-abort lifecycle handling.
 */

import http from 'node:http';
import { parseArgs } from 'node:util';
import { fileURLToPath } from 'node:url';

export const ALLOWLISTED_PATHS = new Set([
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/me',
  '/api/v1/apps/access-requests',
  '/api/v1/apps/access-requests/me',
  '/api/v1/sync/tabby-rush/strong/bootstrap',
  '/api/v1/sync/tabby-rush/strong/pull',
  '/api/v1/sync/tabby-rush/strong/push',
  '/api/v1/sync/tabby-rush/strong/legacy-receipts'
]);

export function createFaultProxy({
  upstreamHost = '127.0.0.1',
  upstreamPort = 3001,
  scenario = 'pass-through',
  quiet = false,
  upstreamTimeoutMs = 30000
} = {}) {
  let faultInjected = false;

  const server = http.createServer((clientReq, clientRes) => {
    clientReq.on('error', (err) => {
      if (!quiet) console.error(`[FaultProxy] Client request error: ${err.message}`);
    });

    let pathname;
    try {
      const url = new URL(clientReq.url, `http://${clientReq.headers.host || '127.0.0.1'}`);
      pathname = url.pathname;
    } catch {
      clientRes.writeHead(400, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'BAD_REQUEST' }));
      return;
    }

    // Strict route allowlist check - log only static message, no unverified path reflection
    if (!ALLOWLISTED_PATHS.has(pathname)) {
      if (!quiet) console.warn(`[FaultProxy] REJECTED unlisted path for method ${clientReq.method}`);
      clientRes.writeHead(403, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'FORBIDDEN_PATH' }));
      return;
    }

    const upstreamHeaders = { ...clientReq.headers };
    upstreamHeaders.host = `${upstreamHost}:${upstreamPort}`;

    const upstreamReq = http.request({
      host: upstreamHost,
      port: upstreamPort,
      path: clientReq.url,
      method: clientReq.method,
      headers: upstreamHeaders,
      timeout: upstreamTimeoutMs
    }, (upstreamRes) => {
      const is2xx = upstreamRes.statusCode >= 200 && upstreamRes.statusCode < 300;
      const isPushRoute = pathname === '/api/v1/sync/tabby-rush/strong/push';
      const isRefreshRoute = pathname === '/api/v1/auth/refresh';
      const isLegacyReceiptsRoute = pathname === '/api/v1/sync/tabby-rush/strong/legacy-receipts';

      const shouldDropPush = is2xx && scenario === 'drop-push-response' && isPushRoute && !faultInjected;
      const shouldDropRefresh = is2xx && scenario === 'drop-refresh-response' && isRefreshRoute && !faultInjected;
      const shouldDropReceipts = is2xx && scenario === 'drop-legacy-receipts-response' && isLegacyReceiptsRoute && !faultInjected;

      if (shouldDropPush || shouldDropRefresh || shouldDropReceipts) {
        faultInjected = true;
        if (!quiet) console.log(`[FaultProxy] INJECTING FAULT (${scenario}) for ${clientReq.method} ${pathname} (upstream status: ${upstreamRes.statusCode})`);
        upstreamRes.resume();
        upstreamRes.on('end', () => {
          if (!quiet) console.log(`[FaultProxy] Upstream write completed. Severing client connection.`);
          clientRes.destroy();
        });
        return;
      }

      if (!quiet) console.log(`[FaultProxy] Forwarding ${clientReq.method} ${pathname} -> Upstream ${upstreamRes.statusCode}`);
      upstreamRes.setTimeout(upstreamTimeoutMs, () => {
        if (!quiet) console.error(`[FaultProxy] Upstream response inactivity timeout (${upstreamTimeoutMs}ms)`);
        upstreamRes.destroy();
        if (!clientRes.destroyed) {
          clientRes.destroy();
        }
      });
      upstreamRes.on('error', (err) => {
        if (!quiet) console.error(`[FaultProxy] Upstream response stream error: ${err.message}`);
        if (!clientRes.destroyed) {
          clientRes.destroy(err);
        }
      });
      clientRes.writeHead(upstreamRes.statusCode, upstreamRes.headers);
      upstreamRes.pipe(clientRes);
    });

    upstreamReq.on('error', (err) => {
      if (!quiet) console.error(`[FaultProxy] Upstream connection error: ${err.message}`);
      if (!clientRes.headersSent && !clientRes.destroyed) {
        clientRes.writeHead(502, { 'Content-Type': 'application/json' });
        clientRes.end(JSON.stringify({ error: 'BAD_GATEWAY', message: err.message }));
      }
    });
    upstreamReq.on('timeout', () => {
      if (!quiet) console.error(`[FaultProxy] Upstream socket inactivity timeout (${upstreamTimeoutMs}ms)`);
      upstreamReq.destroy();
      if (!clientRes.headersSent && !clientRes.destroyed) {
        clientRes.writeHead(504, { 'Content-Type': 'application/json' });
        clientRes.end(JSON.stringify({ error: 'GATEWAY_TIMEOUT', message: 'Upstream Hub timed out' }));
      } else if (!clientRes.destroyed) {
        clientRes.destroy();
      }
    });

    // Abort cleanup: if client socket closes before response ends, destroy upstream request
    clientRes.on('close', () => {
      if (!clientRes.writableEnded && !upstreamReq.destroyed) {
        upstreamReq.destroy();
      }
    });

    clientReq.pipe(upstreamReq);
  });

  return {
    server,
    isFaultInjected: () => faultInjected,
    resetFault: () => { faultInjected = false; }
  };
}

// CLI Execution entrypoint
const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  const options = {
    port: { type: 'string', default: '3000' },
    'upstream-port': { type: 'string', default: '3001' },
    'upstream-host': { type: 'string', default: '127.0.0.1' },
    scenario: { type: 'string', default: 'pass-through' },
    help: { type: 'boolean', short: 'h' }
  };

  const { values } = parseArgs({ options, allowPositionals: true });

  if (values.help) {
    console.log(`Usage: node scripts/debug-fault-proxy.mjs [options]

Options:
  --port <PORT>             Proxy listen port (default: 3000, 127.0.0.1 only)
  --upstream-port <PORT>    Upstream Hub port (default: 3001)
  --upstream-host <HOST>    Upstream Hub host (default: 127.0.0.1)
  --scenario <NAME>         Fault scenario:
                              - pass-through (default)
                              - drop-push-response
                              - drop-refresh-response
                              - drop-legacy-receipts-response
  -h, --help                Show this help
`);
    process.exit(0);
  }

  const LISTEN_PORT = parseInt(values.port, 10);
  const proxy = createFaultProxy({
    upstreamHost: values['upstream-host'],
    upstreamPort: parseInt(values['upstream-port'], 10),
    scenario: values.scenario
  });

  proxy.server.listen(LISTEN_PORT, '127.0.0.1', () => {
    console.log(`[FaultProxy] Listening strictly on 127.0.0.1:${LISTEN_PORT}`);
    console.log(`[FaultProxy] Target upstream: http://${values['upstream-host']}:${values['upstream-port']}`);
    console.log(`[FaultProxy] Active scenario: ${values.scenario}`);
  });
}
