#!/usr/bin/env node
/**
 * scripts/admin-announcements-smoke.mjs
 *
 * Standalone Node.js HTTP/SSE/Install-Fencing Smoke & Acceptance Test Runner
 * for Server Admin Announcements (contract-v1.json and report03).
 *
 * Zero external npm dependencies.
 *
 * Modes:
 *   --self-test: Spin up built-in in-memory mock server, execute full vector & API test suite,
 *                including negative fixtures, golden vectors, and SSE framing tests, then exit.
 *   --live:      Explicit live-mode flag required for destructive/intake tests against external host.
 *   --url <url>: Target base URL (default: http://127.0.0.1:8080).
 *   --config <p>: Path to JSON config file with tokens/credentials.
 *   --admin-token <jwt>: Admin bearer JWT.
 *   --user-token <jwt>: User bearer JWT.
 *   --other-token <jwt>: Unauthorized/non-admin bearer JWT.
 */

import http from "node:http";
import https from "node:https";
import crypto from "node:crypto";
import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = resolve(__dirname, "..");

// --- Utilities & Redaction ---

function redact(str) {
  if (typeof str !== "string") return str;
  return str.replace(/(Bearer\s+)[A-Za-z0-9._-]+/gi, "$1[REDACTED]")
    .replace(/(X-Tabby-Install-Proof:\s*)[A-Za-z0-9_-]+/gi, "$1[REDACTED]")
    .replace(/("token":\s*")[^"]+(")/gi, '$1[REDACTED]$2');
}

function parseArgs(args) {
  const flags = {
    selfTest: false,
    live: false,
    url: "http://127.0.0.1:8080",
    config: null,
    adminToken: null,
    userToken: null,
    otherToken: null,
    verbose: false,
  };

  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a === "--self-test") flags.selfTest = true;
    else if (a === "--live") flags.live = true;
    else if (a === "--verbose") flags.verbose = true;
    else if (a === "--url" && i + 1 < args.length) flags.url = args[++i];
    else if (a === "--config" && i + 1 < args.length) flags.config = args[++i];
    else if (a === "--admin-token" && i + 1 < args.length) flags.adminToken = args[++i];
    else if (a === "--user-token" && i + 1 < args.length) flags.userToken = args[++i];
    else if (a === "--other-token" && i + 1 < args.length) flags.otherToken = args[++i];
  }
  return flags;
}

// --- Golden Vector Checks ---

function runVectorAssertions() {
  const vectorsPath = resolve(REPO_ROOT, "integration/announcements/vectors-v1.json");
  if (!existsSync(vectorsPath)) {
    throw new Error(`Golden vectors not found at ${vectorsPath}`);
  }
  const vectors = JSON.parse(readFileSync(vectorsPath, "utf8"));
  let passed = 0;

  // 1. Digest Vectors
  for (const v of vectors.digestVectors) {
    const hash = crypto.createHash("sha256").update(v.canonicalJson, "utf8").digest("hex");
    const formatted = v.expectedDigest.startsWith("sha256:") ? `sha256:${hash}` : hash;
    if (formatted !== v.expectedDigest) {
      throw new Error(`Vector mismatch for ${v.name}: expected ${v.expectedDigest}, got ${formatted}`);
    }
    passed++;
  }

  // 2. Cursor Vector
  for (const cv of vectors.cursorVectors) {
    const key = crypto.createHmac("sha256", cv.jwtSecret).update("tabbyrush-announcement-cursor-v1", "utf8").digest();
    const payloadB64 = Buffer.from(cv.payloadJson, "utf8").toString("base64url");
    const sigB64 = crypto.createHmac("sha256", key).update(cv.payloadJson, "utf8").digest("base64url");
    const wireCursor = `${payloadB64}.${sigB64}`;
    if (wireCursor !== cv.expectedWireCursor) {
      throw new Error(`Cursor vector mismatch for ${cv.name}: expected ${cv.expectedWireCursor}, got ${wireCursor}`);
    }
    passed++;
  }

  // 3. Normalization Vectors
  for (const nv of vectors.normalizationVectors) {
    if (nv.name.startsWith("title_")) {
      const norm = nv.rawInput.trim();
      if (norm !== nv.expectedNormalized) {
        throw new Error(`Normalization mismatch for ${nv.name}: expected '${nv.expectedNormalized}', got '${norm}'`);
      }
      passed++;
    } else if (nv.name.startsWith("body_")) {
      const norm = nv.rawInput.replace(/\r\n/g, "\n").trim();
      if (norm !== nv.expectedNormalized) {
        throw new Error(`Normalization mismatch for ${nv.name}: expected '${nv.expectedNormalized}', got '${norm}'`);
      }
      passed++;
    }
  }

  return passed;
}

// --- HTTP Client Helper with Safe Redirect Rejection ---

function makeRequest(baseUrl, options) {
  return new Promise((resolve, reject) => {
    const u = new URL(options.path, baseUrl);
    const isHttps = u.protocol === "https:";
    const transport = isHttps ? https : http;

    const reqOpts = {
      method: options.method || "GET",
      hostname: u.hostname,
      port: u.port || (isHttps ? 443 : 80),
      path: u.pathname + u.search,
      headers: {
        ...(options.headers || {}),
      },
      timeout: options.timeout || 5000,
    };

    const req = transport.request(reqOpts, (res) => {
      // Security rule: Reject redirects for authenticated API requests
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        return reject(new Error(`Redirect rejected for API route: ${res.statusCode} -> ${res.headers.location}`));
      }

      if (options.sse) {
        resolve({
          status: res.statusCode,
          headers: res.headers,
          stream: res,
        });
        return;
      }

      const chunks = [];
      res.on("data", (chunk) => chunks.push(chunk));
      res.on("end", () => {
        const rawBody = Buffer.concat(chunks).toString("utf8");
        let json = null;
        try {
          if (rawBody.trim().length > 0) {
            json = JSON.parse(rawBody);
          }
        } catch {
          // not json
        }
        resolve({
          status: res.statusCode,
          headers: res.headers,
          body: rawBody,
          json,
        });
      });
    });

    req.on("error", reject);
    req.on("timeout", () => {
      req.destroy();
      reject(new Error(`Request timed out after ${reqOpts.timeout}ms`));
    });

    if (options.body) {
      const bodyStr = typeof options.body === "string" ? options.body : JSON.stringify(options.body);
      req.setHeader("Content-Length", Buffer.byteLength(bodyStr));
      req.write(bodyStr);
    }
    req.end();
  });
}

// --- Built-in Mock Server for Harness Self-Test ---

function createMockServer() {
  const adminToken = "mock-admin-token-eyJhbGciOi";
  const userToken = "mock-user-token-eyJhbGciOi";
  const otherToken = "mock-other-token-eyJhbGciOi";
  const installProof = "dGVzdC1pbnN0YWxsLXByb29mLXZhbGlkLXJhbmRvbS0zMmJ5dGVz";

  const storage = {
    previews: new Map(),
    announcements: new Map(),
    byKey: new Map(), // idempotencyKey -> id
    inbox: new Map(), // `${userId}:${messageId}` -> item
    readTimestamps: new Map(), // `${userId}:${messageId}` -> readAt
    installs: new Map(), // installId -> { currentGeneration, state, secretHash, ownerId, lastOperationOwnerId, token }
  };

  const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const authHeader = req.headers.authorization || "";
    const installProofHeader = req.headers["x-tabby-install-proof"] || "";

    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => {
      const bodyRaw = Buffer.concat(chunks).toString("utf8");
      let bodyJson = null;
      try {
        if (bodyRaw) bodyJson = JSON.parse(bodyRaw);
      } catch {
        // malformed
      }

      function sendJson(status, data) {
        const s = JSON.stringify(data);
        res.writeHead(status, {
          "Content-Type": "application/json",
          "Cache-Control": "no-store",
          "Content-Length": Buffer.byteLength(s),
        });
        res.end(s);
      }

      function sendError(status, code, message, details = {}) {
        sendJson(status, {
          error: {
            code,
            message,
            retryable: false,
            requestId: crypto.randomUUID(),
            details,
          },
        });
      }

      const pathname = url.pathname;

      // 1. POST /api/v1/admin/apps/tabby-rush/announcements/audience-preview
      if (req.method === "POST" && pathname === "/api/v1/admin/apps/tabby-rush/announcements/audience-preview") {
        if (!authHeader.includes(adminToken)) {
          return sendError(403, "adminRequired", "Admin privileges required");
        }
        if (!bodyJson || !bodyJson.audience) {
          return sendError(400, "invalidRequest", "Missing audience spec");
        }
        const { mode, userIds } = bodyJson.audience;
        if (mode === "selected" && (!userIds || userIds.length === 0)) {
          return sendError(422, "emptyAudience", "Selected mode requires non-empty userIds");
        }
        if (mode === "selected" && userIds.length > 500) {
          return sendError(413, "audienceTooLarge", "Audience exceeds 500 recipient maximum", { limit: 500 });
        }

        const previewId = crypto.randomUUID();
        const recipientIds = mode === "selected" ? [...new Set(userIds)] : ["507f1f77bcf86cd799439011"];
        const audienceCanonical = mode === "selected" ? { mode, userIds: recipientIds } : { mode };
        const digestInput = JSON.stringify({
          appId: "tabby-rush",
          audience: audienceCanonical,
          recipientIds,
        });
        const audienceDigest = `sha256:${crypto.createHash("sha256").update(digestInput).digest("hex")}`;
        const expiresAt = new Date(Date.now() + 300 * 1000).toISOString();

        const preview = {
          previewId,
          appId: "tabby-rush",
          audience: audienceCanonical,
          recipientIds,
          recipientCount: recipientIds.length,
          excludedCount: 0,
          deliveryAvailability: {
            inbox: "ready",
            desktopLive: "enabled",
            androidPush: "enabled",
          },
          audienceDigest,
          expiresAt,
        };
        storage.previews.set(previewId, preview);
        return sendJson(200, preview);
      }

      // 2. POST /api/v1/admin/apps/tabby-rush/announcements
      if (req.method === "POST" && pathname === "/api/v1/admin/apps/tabby-rush/announcements") {
        if (!authHeader.includes(adminToken)) {
          return sendError(403, "adminRequired", "Admin privileges required");
        }
        const idempotencyKey = req.headers["idempotency-key"];
        if (!idempotencyKey) {
          return sendError(400, "invalidRequest", "Missing Idempotency-Key header");
        }

        if (!bodyJson || !bodyJson.previewId || !bodyJson.title || !bodyJson.body) {
          return sendError(400, "invalidRequest", "Missing required announcement fields");
        }

        // Title/body limits validation
        if (bodyJson.title.length > 200 || Buffer.byteLength(bodyJson.title, "utf8") > 800) {
          return sendError(422, "invalidContent", "Title exceeds 200 scalar / 800 byte limit");
        }
        if (bodyJson.body.includes("\0")) {
          return sendError(422, "invalidContent", "Body contains NUL byte");
        }

        // Calculate request digest: sha256 of canonical {previewId, audienceDigest, title, body}
        const normTitle = bodyJson.title.trim();
        const normBody = bodyJson.body.replace(/\r\n/g, "\n").trim();
        const reqDigest = "sha256:" + crypto.createHash("sha256").update(JSON.stringify({
          previewId: bodyJson.previewId,
          audienceDigest: bodyJson.audienceDigest,
          title: normTitle,
          body: normBody,
        })).digest("hex");

        // Check idempotency replay
        if (storage.byKey.has(idempotencyKey)) {
          const existingId = storage.byKey.get(idempotencyKey);
          const existing = storage.announcements.get(existingId);
          if (existing.requestDigest !== reqDigest) {
            return sendError(409, "idempotencyConflict", "Idempotency key reused with different payload");
          }
          return sendJson(202, { ...existing.acceptance, replayed: true });
        }

        const preview = storage.previews.get(bodyJson.previewId);
        if (!preview) {
          return sendError(404, "previewNotFound", "Preview expired or not found");
        }

        const id = crypto.randomUUID();
        const now = new Date().toISOString();
        const expiresAt = new Date(Date.now() + 90 * 24 * 3600 * 1000).toISOString();
        const acceptance = {
          id,
          appId: "tabby-rush",
          senderId: "507f1f77bcf86cd799439001",
          createdAt: now,
          expiresAt,
          recipientCount: preview.recipientCount,
          materializationState: "pending",
          dispatchSummary: {
            complete: false,
            fcm: { pending: preview.recipientCount, processing: 0, retryable: 0, providerAccepted: 0, failed: 0, skipped: 0, expired: 0, ambiguousAttempts: 0 },
            sse: { pending: preview.recipientCount, processing: 0, hintPublished: 0, failed: 0, skipped: 0, expired: 0 },
          },
          replayed: false,
        };

        storage.announcements.set(id, {
          acceptance,
          requestDigest: reqDigest,
          title: normTitle,
          body: normBody,
          audience: preview.audience,
          recipientIds: preview.recipientIds,
        });
        storage.byKey.set(idempotencyKey, id);

        // Populate recipient inbox
        for (const recipientId of preview.recipientIds) {
          storage.inbox.set(`${recipientId}:${id}`, {
            id,
            appId: "tabby-rush",
            title: bodyJson.title.trim(),
            body: bodyJson.body.replace(/\r\n/g, "\n").trim(),
            createdAt: now,
            expiresAt,
            readAt: null,
          });
        }

        return sendJson(202, acceptance);
      }

      // 3. GET /api/v1/admin/apps/tabby-rush/announcements
      if (req.method === "GET" && pathname === "/api/v1/admin/apps/tabby-rush/announcements") {
        if (!authHeader.includes(adminToken)) {
          return sendError(403, "adminRequired", "Admin privileges required");
        }
        const items = Array.from(storage.announcements.values()).map((a) => ({
          ...a.acceptance,
          title: a.title,
          audience: a.audience,
        }));
        return sendJson(200, {
          items,
          nextCursor: null,
          serverNow: new Date().toISOString(),
        });
      }

      // 4. GET /api/v1/admin/apps/tabby-rush/announcements/by-idempotency/{key}
      if (req.method === "GET" && pathname.startsWith("/api/v1/admin/apps/tabby-rush/announcements/by-idempotency/")) {
        if (!authHeader.includes(adminToken)) {
          return sendError(403, "adminRequired", "Admin privileges required");
        }
        const key = pathname.slice("/api/v1/admin/apps/tabby-rush/announcements/by-idempotency/".length);
        if (storage.byKey.has(key)) {
          const ann = storage.announcements.get(storage.byKey.get(key));
          return sendJson(200, ann.acceptance);
        }
        return sendError(404, "notFound", "Announcement not found by idempotency key");
      }

      // 5. GET /api/v1/admin/apps/tabby-rush/announcements/{id}
      if (req.method === "GET" && pathname.startsWith("/api/v1/admin/apps/tabby-rush/announcements/")) {
        if (!authHeader.includes(adminToken)) {
          return sendError(403, "adminRequired", "Admin privileges required");
        }
        const id = pathname.slice("/api/v1/admin/apps/tabby-rush/announcements/".length);
        if (storage.announcements.has(id)) {
          const ann = storage.announcements.get(id);
          return sendJson(200, {
            ...ann.acceptance,
            title: ann.title,
            body: ann.body,
            audience: ann.audience,
            recipientIds: ann.recipientIds,
            audienceDigest: ann.requestDigest,
          });
        }
        return sendError(404, "notFound", "Sent announcement not found");
      }

      // 6. GET /api/v1/apps/tabby-rush/announcements/inbox
      if (req.method === "GET" && pathname === "/api/v1/apps/tabby-rush/announcements/inbox") {
        if (!authHeader.includes(userToken)) {
          return sendError(403, "appAccessRevoked", "User app access revoked or unauthenticated");
        }
        const currentUserId = "507f1f77bcf86cd799439011";
        const items = [];
        for (const [key, item] of storage.inbox.entries()) {
          if (key.startsWith(`${currentUserId}:`)) {
            const readAt = storage.readTimestamps.get(key) || null;
            items.push({ ...item, readAt });
          }
        }
        return sendJson(200, {
          items,
          nextCursor: null,
          serverNow: new Date().toISOString(),
        });
      }

      // 7. GET /api/v1/apps/tabby-rush/announcements/unread
      if (req.method === "GET" && pathname === "/api/v1/apps/tabby-rush/announcements/unread") {
        if (!authHeader.includes(userToken)) {
          return sendError(403, "appAccessRevoked", "User app access revoked");
        }
        const currentUserId = "507f1f77bcf86cd799439011";
        let unreadCount = 0;
        for (const [key] of storage.inbox.entries()) {
          if (key.startsWith(`${currentUserId}:`)) {
            if (!storage.readTimestamps.has(key)) unreadCount++;
          }
        }
        return sendJson(200, { unreadCount, serverNow: new Date().toISOString() });
      }

      // 8. GET /api/v1/apps/tabby-rush/announcements/inbox/{id}
      if (req.method === "GET" && pathname.startsWith("/api/v1/apps/tabby-rush/announcements/inbox/")) {
        if (!authHeader.includes(userToken)) {
          return sendError(403, "appAccessRevoked", "User app access revoked");
        }
        const id = pathname.slice("/api/v1/apps/tabby-rush/announcements/inbox/".length);
        const currentUserId = "507f1f77bcf86cd799439011";
        const inboxKey = `${currentUserId}:${id}`;
        if (storage.inbox.has(inboxKey)) {
          const item = storage.inbox.get(inboxKey);
          const readAt = storage.readTimestamps.get(inboxKey) || null;
          return sendJson(200, { ...item, readAt });
        }
        return sendError(404, "notFound", "Inbox announcement not found");
      }

      // 9. PUT /api/v1/apps/tabby-rush/announcements/inbox/{id}/read
      if (req.method === "PUT" && pathname.endsWith("/read") && pathname.startsWith("/api/v1/apps/tabby-rush/announcements/inbox/")) {
        if (!authHeader.includes(userToken)) {
          return sendError(403, "appAccessRevoked", "User app access revoked");
        }
        const parts = pathname.split("/");
        const id = parts[parts.length - 2];
        const currentUserId = "507f1f77bcf86cd799439011";
        const inboxKey = `${currentUserId}:${id}`;
        if (!storage.inbox.has(inboxKey)) {
          return sendError(404, "notFound", "Inbox item not found");
        }

        let readAt = storage.readTimestamps.get(inboxKey);
        if (!readAt) {
          readAt = new Date().toISOString();
          storage.readTimestamps.set(inboxKey, readAt);
        }

        let unreadCount = 0;
        for (const [k] of storage.inbox.entries()) {
          if (k.startsWith(`${currentUserId}:`) && !storage.readTimestamps.has(k)) unreadCount++;
        }

        return sendJson(200, {
          id,
          readAt,
          unreadCount,
          serverNow: new Date().toISOString(),
        });
      }

      // 10. GET /api/v1/apps/tabby-rush/announcements/stream (SSE)
      if (req.method === "GET" && pathname === "/api/v1/apps/tabby-rush/announcements/stream") {
        if (!authHeader.includes(userToken)) {
          return sendError(403, "appAccessRevoked", "User app access revoked");
        }
        res.writeHead(200, {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache, no-store",
          "Connection": "keep-alive",
        });

        // Send handshake frame
        const readyData = JSON.stringify({
          protocol: 1,
          appId: "tabby-rush",
          streamEpoch: crypto.randomUUID(),
          baselineAcceptanceOrdinal: "41",
          baselineHintSequence: "58",
          serverNow: new Date().toISOString(),
          replaySupported: false,
        });

        res.write(`event: ready\nid: e1:58\ndata: ${readyData}\n\n`);
        // Test chunk fragmentation / keepalive
        res.write(": keepalive\n\n");
        // End after brief delay or keep alive
        setTimeout(() => {
          res.end();
        }, 100);
        return;
      }

      // 11. Notification Installs Binding routes
      if (pathname.startsWith("/api/v1/apps/tabby-rush/notification-installs/")) {
        const parts = pathname.split("/").filter(Boolean);
        // parts: ["api", "v1", "apps", "tabby-rush", "notification-installs", installId, action]
        const installId = parts[5];
        const action = parts[6];
        if (!installProofHeader || installProofHeader !== installProof) {
          return sendError(403, "installProofRejected", "Invalid or missing X-Tabby-Install-Proof");
        }

        // PUT binding
        if (req.method === "PUT" && action === "binding") {
          const gen = BigInt(bodyJson.operationGeneration || "0");
          const existing = storage.installs.get(installId);
          if (existing) {
            const currentGen = BigInt(existing.currentGeneration);
            if (gen === currentGen) {
              return sendJson(200, {
                installId,
                operationGeneration: String(gen),
                bindingGeneration: String(gen),
                state: existing.state,
                replayed: true,
              });
            }
            if (gen < currentGen) {
              return sendError(409, "staleInstallOperation", "Operation generation is stale", {
                currentGeneration: String(currentGen),
              });
            }
          }

          const record = {
            currentGeneration: String(gen),
            state: "bound",
            ownerId: "507f1f77bcf86cd799439011",
            lastOperationOwnerId: "507f1f77bcf86cd799439011",
            token: bodyJson.token,
          };
          storage.installs.set(installId, record);
          return sendJson(200, {
            installId,
            operationGeneration: String(gen),
            bindingGeneration: String(gen),
            state: "bound",
            replayed: false,
          });
        }

        // POST unbind
        if (req.method === "POST" && action === "unbind") {
          const gen = BigInt(bodyJson.operationGeneration || "0");
          const existing = storage.installs.get(installId);
          if (!existing) {
            return sendError(404, "notFound", "Install binding not found");
          }
          const currentGen = BigInt(existing.currentGeneration);
          if (gen <= currentGen) {
            return sendError(409, "staleInstallOperation", "Unbind generation is stale");
          }
          existing.currentGeneration = String(gen);
          existing.state = "unbound";
          return sendJson(200, {
            installId,
            operationGeneration: String(gen),
            bindingGeneration: String(gen),
            state: "unbound",
            replayed: false,
          });
        }

        // GET binding
        if (req.method === "GET" && action === "binding") {
          const existing = storage.installs.get(installId);
          if (!existing) {
            return sendError(403, "installProofRejected", "Install not registered");
          }
          return sendJson(200, {
            installId,
            currentGeneration: existing.currentGeneration,
            state: existing.state,
            ownedByCurrentUser: existing.ownerId === "507f1f77bcf86cd799439011",
            lastOperationMatchesCurrentUser: true,
          });
        }
      }

      sendError(404, "notFound", `Route not found: ${req.method} ${pathname}`);
    });
  });

  return {
    server,
    tokens: { adminToken, userToken, otherToken, installProof },
  };
}

// --- Test Suite Execution ---

async function runSmokeSuite(baseUrl, tokens, options = {}) {
  const isMock = options.isMock === true;
  const results = [];

  function record(name, pass, detail = "") {
    results.push({ name, pass, detail: redact(detail) });
    const mark = pass ? "\x1b[32mPASS\x1b[0m" : "\x1b[31mFAIL\x1b[0m";
    console.log(`  [${mark}] ${name}${detail ? ` - ${redact(detail)}` : ""}`);
  }

  console.log(`\n\x1b[1mRunning Smoke Tests against: ${baseUrl}\x1b[0m`);

  // Section 1: Audience Preview
  try {
    // 1.1 Non-admin preview -> 403
    const negAuth = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements/audience-preview",
      headers: {
        "Authorization": `Bearer ${tokens.otherToken}`,
        "Content-Type": "application/json",
      },
      body: { audience: { mode: "all" } },
    });
    record("Preview: Non-admin rejected with 403", negAuth.status === 403);

    // 1.2 Empty audience -> 422
    const negEmpty = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements/audience-preview",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
      },
      body: { audience: { mode: "selected", userIds: [] } },
    });
    record("Preview: Empty selected audience rejected with 422", negEmpty.status === 422);

    // 1.3 Overflow audience (>500) -> 413
    const overflowIds = Array.from({ length: 501 }, (_, i) => `507f1f77bcf86cd799439${String(i).padStart(3, "0")}`);
    const negOverflow = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements/audience-preview",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
      },
      body: { audience: { mode: "selected", userIds: overflowIds } },
    });
    record("Preview: Audience >500 rejected with 413", negOverflow.status === 413);

    // 1.4 Happy path preview
    const previewRes = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements/audience-preview",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
      },
      body: { audience: { mode: "selected", userIds: ["507f1f77bcf86cd799439011"] } },
    });
    const pOk = previewRes.status === 200 &&
      previewRes.json &&
      previewRes.json.appId === "tabby-rush" &&
      previewRes.json.recipientCount === 1 &&
      previewRes.json.audienceDigest.startsWith("sha256:");
    record("Preview: Valid selected audience returned 200 with digest", pOk, `previewId: ${previewRes.json?.previewId}`);

    const previewData = previewRes.json;

    // Section 2: Create Announcement & Idempotency
    const idempotencyKey = crypto.randomUUID();
    const createPayload = {
      previewId: previewData.previewId,
      audienceDigest: previewData.audienceDigest,
      title: "Smoke Test Notice",
      body: "This is a smoke test announcement.",
    };

    // 2.1 First send -> 202 Acceptance
    const createRes = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: createPayload,
    });
    const cOk = createRes.status === 202 &&
      createRes.json &&
      createRes.json.replayed === false &&
      createRes.json.recipientCount === 1;
    record("Create: Announcement accepted with 202 (replayed=false)", cOk, `messageId: ${createRes.json?.id}`);

    const messageId = createRes.json?.id;

    // 2.2 Replay same key + payload -> 202 replayed=true
    const replayRes = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: createPayload,
    });
    record("Create: Replayed key returns 202 with replayed=true", replayRes.status === 202 && replayRes.json?.replayed === true);

    // 2.3 Idempotency conflict (same key, different title) -> 409
    const conflictRes = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: { ...createPayload, title: "Altered Title Notice" },
    });
    record("Create: Altered payload with same key returns 409 idempotencyConflict", conflictRes.status === 409);

    // 2.4 Title limit validation -> 422
    const longTitle = "A".repeat(201);
    const longTitleRes = await makeRequest(baseUrl, {
      method: "POST",
      path: "/api/v1/admin/apps/tabby-rush/announcements",
      headers: {
        "Authorization": `Bearer ${tokens.adminToken}`,
        "Content-Type": "application/json",
        "Idempotency-Key": crypto.randomUUID(),
      },
      body: { ...createPayload, title: longTitle },
    });
    record("Create: Title >200 chars rejected with 422", longTitleRes.status === 422);

    // Section 3: History & by-idempotency
    const histRes = await makeRequest(baseUrl, {
      method: "GET",
      path: "/api/v1/admin/apps/tabby-rush/announcements",
      headers: { "Authorization": `Bearer ${tokens.adminToken}` },
    });
    record("History: GET admin announcements returned 200 SentPage", histRes.status === 200 && Array.isArray(histRes.json?.items));

    const byKeyRes = await makeRequest(baseUrl, {
      method: "GET",
      path: `/api/v1/admin/apps/tabby-rush/announcements/by-idempotency/${idempotencyKey}`,
      headers: { "Authorization": `Bearer ${tokens.adminToken}` },
    });
    record("History: GET by-idempotency returned 200 Acceptance", byKeyRes.status === 200 && byKeyRes.json?.id === messageId);

    const sentDetailRes = await makeRequest(baseUrl, {
      method: "GET",
      path: `/api/v1/admin/apps/tabby-rush/announcements/${messageId}`,
      headers: { "Authorization": `Bearer ${tokens.adminToken}` },
    });
    record("History: GET announcement detail returned 200 SentDetail", sentDetailRes.status === 200 && sentDetailRes.json?.title === createPayload.title);

    // Section 4: User Inbox, Unread & Monotonic Read
    const inboxRes = await makeRequest(baseUrl, {
      method: "GET",
      path: "/api/v1/apps/tabby-rush/announcements/inbox",
      headers: { "Authorization": `Bearer ${tokens.userToken}` },
    });
    record("Inbox: GET user inbox returned 200 InboxPage", inboxRes.status === 200 && inboxRes.json?.items?.length > 0);

    const unreadRes = await makeRequest(baseUrl, {
      method: "GET",
      path: "/api/v1/apps/tabby-rush/announcements/unread",
      headers: { "Authorization": `Bearer ${tokens.userToken}` },
    });
    record("Inbox: GET unread returned 200 with unreadCount", unreadRes.status === 200 && typeof unreadRes.json?.unreadCount === "number");

    // Put read
    const readRes1 = await makeRequest(baseUrl, {
      method: "PUT",
      path: `/api/v1/apps/tabby-rush/announcements/inbox/${messageId}/read`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "Content-Type": "application/json",
      },
      body: {},
    });
    const firstReadAt = readRes1.json?.readAt;
    record("Read: PUT read marks readAt timestamp", readRes1.status === 200 && !!firstReadAt);

    // Monotonic read: re-PUT read preserves identical readAt
    const readRes2 = await makeRequest(baseUrl, {
      method: "PUT",
      path: `/api/v1/apps/tabby-rush/announcements/inbox/${messageId}/read`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "Content-Type": "application/json",
      },
      body: {},
    });
    record("Read: Monotonicity preserved on second PUT (same readAt)", readRes2.status === 200 && readRes2.json?.readAt === firstReadAt);

    // Section 5: SSE Stream Ready Handshake & Framing
    const sseRes = await makeRequest(baseUrl, {
      method: "GET",
      path: "/api/v1/apps/tabby-rush/announcements/stream",
      headers: { "Authorization": `Bearer ${tokens.userToken}` },
      sse: true,
      timeout: 3000,
    });

    let sseHandshakeReceived = false;
    await new Promise((resolveSse) => {
      let buffer = "";
      sseRes.stream.on("data", (chunk) => {
        buffer += chunk.toString("utf8");
        if (buffer.includes("event: ready") && buffer.includes("baselineAcceptanceOrdinal")) {
          sseHandshakeReceived = true;
          sseRes.stream.destroy();
          resolveSse();
        }
      });
      sseRes.stream.on("end", resolveSse);
      sseRes.stream.on("error", () => resolveSse());
    });
    record("SSE: Handshake 'event: ready' with baseline ordinal received", sseHandshakeReceived);

    // Section 6: Notification Install Proof & Fencing
    const installId = crypto.randomUUID();
    const bindPayload = {
      operationGeneration: "1",
      platform: "android",
      token: "fcm-device-token-sample-12345",
      appVersion: "1.0.0",
    };

    // 6.1 Missing proof header -> 403
    const negProof = await makeRequest(baseUrl, {
      method: "PUT",
      path: `/api/v1/apps/tabby-rush/notification-installs/${installId}/binding`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "Content-Type": "application/json",
      },
      body: bindPayload,
    });
    record("Install: Missing install proof header rejected with 403", negProof.status === 403);

    // 6.2 Valid bind
    const bindRes = await makeRequest(baseUrl, {
      method: "PUT",
      path: `/api/v1/apps/tabby-rush/notification-installs/${installId}/binding`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "X-Tabby-Install-Proof": tokens.installProof,
        "Content-Type": "application/json",
      },
      body: bindPayload,
    });
    record("Install: Valid bind accepted with 200 BindingResult", bindRes.status === 200 && bindRes.json?.state === "bound");

    // 6.3 Stale generation -> 409
    const staleBind = await makeRequest(baseUrl, {
      method: "PUT",
      path: `/api/v1/apps/tabby-rush/notification-installs/${installId}/binding`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "X-Tabby-Install-Proof": tokens.installProof,
        "Content-Type": "application/json",
      },
      body: { ...bindPayload, operationGeneration: "0" },
    });
    record("Install: Lower generation rejected with 409 staleInstallOperation", staleBind.status === 409);

    // 6.4 GET Binding status
    const statusRes = await makeRequest(baseUrl, {
      method: "GET",
      path: `/api/v1/apps/tabby-rush/notification-installs/${installId}/binding`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "X-Tabby-Install-Proof": tokens.installProof,
      },
    });
    record("Install: GET binding status returned 200 without exposing token", statusRes.status === 200 && statusRes.json?.token === undefined);

    // 6.5 POST Unbind
    const unbindRes = await makeRequest(baseUrl, {
      method: "POST",
      path: `/api/v1/apps/tabby-rush/notification-installs/${installId}/unbind`,
      headers: {
        "Authorization": `Bearer ${tokens.userToken}`,
        "X-Tabby-Install-Proof": tokens.installProof,
        "Content-Type": "application/json",
      },
      body: { operationGeneration: "2", expectedBindingGeneration: "1" },
    });
    record("Install: POST unbind marks state unbound with 200", unbindRes.status === 200 && unbindRes.json?.state === "unbound");

  } catch (err) {
    record("Suite Execution Exception", false, err.message);
  }

  const failedCount = results.filter((r) => !r.pass).length;
  console.log(`\n\x1b[1mSummary: ${results.length - failedCount}/${results.length} passed (${failedCount} failed)\x1b[0m`);
  return failedCount === 0;
}

// --- Main Runner ---

async function main() {
  const flags = parseArgs(process.argv.slice(2));

  console.log("=== TabbyRush Server Admin Announcements Smoke Runner ===");

  // Step 1: Golden vectors validation
  console.log("\n\x1b[1mChecking Golden Vectors (vectors-v1.json)...\x1b[0m");
  try {
    const vectorCount = runVectorAssertions();
    console.log(`\x1b[32mPASS\x1b[0m: Verified ${vectorCount} golden vectors (digests, cursors, normalizations).`);
  } catch (err) {
    console.error(`\x1b[31mFAIL\x1b[0m: Golden vector assertion failed: ${err.message}`);
    process.exit(1);
  }

  if (flags.selfTest) {
    console.log("\n\x1b[1m[SELF-TEST MODE] Spinning up ephemeral in-memory mock server...\x1b[0m");
    const mock = createMockServer();
    await new Promise((resolve) => mock.server.listen(0, "127.0.0.1", resolve));
    const port = mock.server.address().port;
    const mockUrl = `http://127.0.0.1:${port}`;

    try {
      const allPassed = await runSmokeSuite(mockUrl, mock.tokens, { isMock: true });
      mock.server.close();

      if (!allPassed) {
        console.error("\n\x1b[31mSelf-test harness verification FAILED.\x1b[0m");
        process.exit(1);
      }
      console.log("\n\x1b[32mSelf-test harness verification PASSED successfully.\x1b[0m");
      console.log("(Note: Self-test proves test-runner harness correctness against fixtures; live OS/hardware gates require separate evidence.)");
      process.exit(0);
    } catch (err) {
      mock.server.close();
      console.error(`\x1b[31mSelf-test runner threw unexpected exception: ${err.message}\x1b[0m`);
      process.exit(1);
    }
  }

  // Live execution requires explicit --live flag
  if (!flags.live) {
    console.error("\n\x1b[33mError: External execution requires explicit --live flag to protect against accidental staging mutations.\x1b[0m");
    console.error("Usage: node scripts/admin-announcements-smoke.mjs --self-test");
    console.error("   or: node scripts/admin-announcements-smoke.mjs --live --url <baseUrl> --admin-token <jwt> --user-token <jwt>");
    process.exit(1);
  }

  // Load config file if provided
  let configTokens = {};
  if (flags.config && existsSync(flags.config)) {
    try {
      configTokens = JSON.parse(readFileSync(flags.config, "utf8"));
    } catch (err) {
      console.error(`Failed to parse config file: ${err.message}`);
      process.exit(1);
    }
  }

  const tokens = {
    adminToken: flags.adminToken || configTokens.adminToken,
    userToken: flags.userToken || configTokens.userToken,
    otherToken: flags.otherToken || configTokens.otherToken || "invalid-token",
    installProof: configTokens.installProof || "dGVzdC1pbnN0YWxsLXByb29mLXZhbGlkLXJhbmRvbS0zMmJ5dGVz",
  };

  if (!tokens.adminToken || !tokens.userToken) {
    console.error("\x1b[31mError: Missing required --admin-token and --user-token for live run.\x1b[0m");
    process.exit(1);
  }

  const success = await runSmokeSuite(flags.url, tokens, { isMock: false });
  process.exit(success ? 0 : 1);
}

main().catch((err) => {
  console.error("Unhandled error:", err);
  process.exit(1);
});
