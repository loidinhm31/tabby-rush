#!/usr/bin/env node
/**
 * scripts/debug-fault-proxy.test.mjs
 *
 * Automated tests for debug-fault-proxy.mjs.
 * Verifies route allowlist, sensitive path redaction, upstream fault drops, and error handling.
 */

import assert from 'node:assert/strict';
import { describe, it, before, after } from 'node:test';
import http from 'node:http';
import { createFaultProxy, ALLOWLISTED_PATHS } from './debug-fault-proxy.mjs';

describe('debug-fault-proxy.mjs', () => {
  let mockUpstream;
  let upstreamPort;
  let upstreamRequests = [];

  before(async () => {
    await new Promise((resolve) => {
      mockUpstream = http.createServer((req, res) => {
        let body = '';
        req.on('data', (chunk) => { body += chunk; });
        req.on('end', () => {
          upstreamRequests.push({ method: req.method, url: req.url, body });
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ status: 'ok', received: req.url }));
        });
      });
      mockUpstream.listen(0, '127.0.0.1', () => {
        upstreamPort = mockUpstream.address().port;
        resolve();
      });
    });
  });

  after(async () => {
    if (mockUpstream) {
      await new Promise((resolve) => mockUpstream.close(resolve));
    }
  });

  it('verifies allowlist contains required v2 and legacy routes', () => {
    assert.ok(ALLOWLISTED_PATHS.has('/api/v1/sync/doggo/strong/push'));
    assert.ok(ALLOWLISTED_PATHS.has('/api/v1/sync/doggo/strong/pull'));
    assert.ok(ALLOWLISTED_PATHS.has('/api/v1/sync/doggo/strong/legacy-receipts'));
  });

  it('passes through allowlisted route to upstream', async () => {
    const proxy = createFaultProxy({
      upstreamPort,
      scenario: 'pass-through',
      quiet: true
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      upstreamRequests = [];
      const res = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/pull`);
      assert.equal(res.status, 200);
      const json = await res.json();
      assert.equal(json.status, 'ok');
      assert.equal(upstreamRequests.length, 1);
      assert.equal(upstreamRequests[0].url, '/api/v1/sync/doggo/strong/pull');
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
    }
  });

  it('rejects unlisted path with 403 Forbidden without logging sensitive path', async () => {
    const warnings = [];
    const origWarn = console.warn;
    console.warn = (...args) => warnings.push(args.join(' '));

    const proxy = createFaultProxy({
      upstreamPort,
      scenario: 'pass-through',
      quiet: false
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      upstreamRequests = [];
      const res = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/unauthorized/secret-path`);
      assert.equal(res.status, 403);
      const json = await res.json();
      assert.equal(json.error, 'FORBIDDEN_PATH');
      assert.equal(upstreamRequests.length, 0); // never reached upstream

      // Verify log message was emitted but completely redacted secret-path
      assert.ok(warnings.length > 0, 'Expected a warning to be logged');
      assert.ok(warnings.some(w => w.includes('[FaultProxy] REJECTED unlisted path for method GET')));
      assert.ok(!warnings.some(w => w.includes('secret-path')), 'Sensitive path MUST NOT be in logs');
    } finally {
      console.warn = origWarn;
      await new Promise((resolve) => proxy.server.close(resolve));
    }
  });

  it('injects fault: drop-push-response completes upstream but severs client connection', async () => {
    const proxy = createFaultProxy({
      upstreamPort,
      scenario: 'drop-push-response',
      quiet: true
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      upstreamRequests = [];
      assert.equal(proxy.isFaultInjected(), false);

      await assert.rejects(async () => {
        await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/push`, {
          method: 'POST',
          body: JSON.stringify({ operations: [] }),
          headers: { 'Content-Type': 'application/json' }
        });
      }, /fetch failed|socket hang up|ECONNRESET/);

      assert.equal(proxy.isFaultInjected(), true);
      assert.equal(upstreamRequests.length, 1); // upstream write succeeded!

      // Second request passes through since fault was consumed
      const res2 = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/push`, {
        method: 'POST',
        body: JSON.stringify({ operations: [] }),
        headers: { 'Content-Type': 'application/json' }
      });
      assert.equal(res2.status, 200);
      assert.equal(upstreamRequests.length, 2);
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
    }
  });

  it('injects fault: drop-legacy-receipts-response completes upstream but severs client connection', async () => {
    const proxy = createFaultProxy({
      upstreamPort,
      scenario: 'drop-legacy-receipts-response',
      quiet: true
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      upstreamRequests = [];
      assert.equal(proxy.isFaultInjected(), false);

      await assert.rejects(async () => {
        await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/legacy-receipts`, {
          method: 'POST',
          body: JSON.stringify({ operations: [] }),
          headers: { 'Content-Type': 'application/json' }
        });
      }, /fetch failed|socket hang up|ECONNRESET/);

      assert.equal(proxy.isFaultInjected(), true);
      assert.equal(upstreamRequests.length, 1);
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
    }
  });

  it('handles broken upstream returning 502 without crash', async () => {
    const proxy = createFaultProxy({
      upstreamPort: 59999, // unopened port
      scenario: 'pass-through',
      quiet: true
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      const res = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/pull`);
      assert.equal(res.status, 502);
      const json = await res.json();
      assert.equal(json.error, 'BAD_GATEWAY');
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
    }
  });

  it('handles upstream timeout returning 504 Gateway Timeout', async () => {
    // Create an upstream that stalls indefinitely
    const stallingUpstream = http.createServer((req, res) => {
      // Never ends or responds
    });
    await new Promise((resolve) => stallingUpstream.listen(0, '127.0.0.1', resolve));
    const stallPort = stallingUpstream.address().port;

    const proxy = createFaultProxy({
      upstreamPort: stallPort,
      scenario: 'pass-through',
      quiet: true,
      upstreamTimeoutMs: 50 // fast timeout for test
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      const res = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/pull`);
      assert.equal(res.status, 504);
      const json = await res.json();
      assert.equal(json.error, 'GATEWAY_TIMEOUT');
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
      await new Promise((resolve) => stallingUpstream.close(resolve));
    }
  });

  it('handles upstream mid-stream stall after headers sent', async () => {
    // Create an upstream that sends headers but never sends body/ends
    const midStallUpstream = http.createServer((req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.flushHeaders();
      // Never writes body or ends
    });
    await new Promise((resolve) => midStallUpstream.listen(0, '127.0.0.1', resolve));
    const stallPort = midStallUpstream.address().port;

    const proxy = createFaultProxy({
      upstreamPort: stallPort,
      scenario: 'pass-through',
      quiet: true,
      upstreamTimeoutMs: 50 // fast timeout
    });
    await new Promise((resolve) => proxy.server.listen(0, '127.0.0.1', resolve));
    const proxyPort = proxy.server.address().port;

    try {
      await assert.rejects(async () => {
        const res = await fetch(`http://127.0.0.1:${proxyPort}/api/v1/sync/doggo/strong/pull`);
        // Reading body should fail as socket is destroyed by timeout
        await res.text();
      }, /fetch failed|socket hang up|premature close|ECONNRESET/);
    } finally {
      await new Promise((resolve) => proxy.server.close(resolve));
      await new Promise((resolve) => midStallUpstream.close(resolve));
    }
  });
});
