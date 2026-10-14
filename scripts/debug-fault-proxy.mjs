#!/usr/bin/env node
/**
 * scripts/debug-fault-proxy.mjs
 *
 * Dedicated loopback proxy for Android sync fault-injection testing.
 * Invariants:
 * - Binds strictly to 127.0.0.1 (never 0.0.0.0 or public interfaces).
 * - Allowlisted strict auth and strong sync route paths only.
 * - Zero payload, token, or header logging.
 * - Deterministic fault injection: drop-push-response, drop-refresh-response.
 */

import http from 'node:http';
import { parseArgs } from 'node:util';

const ALLOWLISTED_PATHS = new Set([
  '/api/v1/auth/register',
  '/api/v1/auth/login',
  '/api/v1/auth/refresh',
  '/api/v1/auth/me',
  '/api/v1/apps/access-requests',
  '/api/v1/apps/access-requests/me',
  '/api/v1/sync/doggo/strong/bootstrap',
  '/api/v1/sync/doggo/strong/pull',
  '/api/v1/sync/doggo/strong/push'
]);

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
  -h, --help                Show this help
`);
  process.exit(0);
}

const LISTEN_PORT = parseInt(values.port, 10);
const UPSTREAM_PORT = parseInt(values['upstream-port'], 10);
const UPSTREAM_HOST = values['upstream-host'];
const SCENARIO = values.scenario;

let faultInjected = false;

const server = http.createServer((clientReq, clientRes) => {
  clientReq.on('error', (err) => {
    console.error(`[FaultProxy] Client request error: ${err.message}`);
  });

  let pathname;
  try {
    const url = new URL(clientReq.url, `http://${clientReq.headers.host || '127.0.0.1'}`);
    pathname = url.pathname;
  } catch (err) {
    clientRes.writeHead(400, { 'Content-Type': 'application/json' });
    clientRes.end(JSON.stringify({ error: 'BAD_REQUEST' }));
    return;
  }

  // Strict route allowlist check
  if (!ALLOWLISTED_PATHS.has(pathname)) {
    console.warn(`[FaultProxy] REJECTED unlisted path: ${clientReq.method} ${pathname}`);
    clientRes.writeHead(403, { 'Content-Type': 'application/json' });
    clientRes.end(JSON.stringify({ error: 'FORBIDDEN_PATH' }));
    return;
  }

  const upstreamHeaders = { ...clientReq.headers };
  upstreamHeaders.host = `${UPSTREAM_HOST}:${UPSTREAM_PORT}`;

  const upstreamReq = http.request({
    host: UPSTREAM_HOST,
    port: UPSTREAM_PORT,
    path: clientReq.url,
    method: clientReq.method,
    headers: upstreamHeaders
  }, (upstreamRes) => {
    const is2xx = upstreamRes.statusCode >= 200 && upstreamRes.statusCode < 300;
    const isPushRoute = pathname === '/api/v1/sync/doggo/strong/push';
    const isRefreshRoute = pathname === '/api/v1/auth/refresh';

    const shouldDropPush = is2xx && SCENARIO === 'drop-push-response' && isPushRoute && !faultInjected;
    const shouldDropRefresh = is2xx && SCENARIO === 'drop-refresh-response' && isRefreshRoute && !faultInjected;

    if (shouldDropPush || shouldDropRefresh) {
      faultInjected = true;
      console.log(`[FaultProxy] INJECTING FAULT (${SCENARIO}) for ${clientReq.method} ${pathname} (upstream status: ${upstreamRes.statusCode})`);
      upstreamRes.resume();
      upstreamRes.on('end', () => {
        console.log(`[FaultProxy] Upstream write completed. Severing client connection.`);
        clientRes.destroy();
      });
      return;
    }

    console.log(`[FaultProxy] Forwarding ${clientReq.method} ${pathname} -> Upstream ${upstreamRes.statusCode}`);
    clientRes.writeHead(upstreamRes.statusCode, upstreamRes.headers);
    upstreamRes.pipe(clientRes);
  });

  upstreamReq.on('error', (err) => {
    console.error(`[FaultProxy] Upstream connection error: ${err.message}`);
    if (!clientRes.headersSent) {
      clientRes.writeHead(502, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'BAD_GATEWAY', message: err.message }));
    }
  });

  clientReq.pipe(upstreamReq);
});

server.listen(LISTEN_PORT, '127.0.0.1', () => {
  console.log(`[FaultProxy] Listening strictly on 127.0.0.1:${LISTEN_PORT}`);
  console.log(`[FaultProxy] Forwarding to ${UPSTREAM_HOST}:${UPSTREAM_PORT}`);
  console.log(`[FaultProxy] Active scenario: ${SCENARIO}`);
});
