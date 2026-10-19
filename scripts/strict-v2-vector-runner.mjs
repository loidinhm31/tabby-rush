#!/usr/bin/env node
/**
 * scripts/strict-v2-vector-runner.mjs
 *
 * Independent Node.js runner to verify canonical strict-v2 preimages and digests.
 * Validates cross-language agreement against integration/glean-hub/strict-v2-vectors.json.
 */

import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import crypto from 'node:crypto';

function encodePreimage(op) {
  const parts = [];
  const putU32 = (v) => {
    const b = Buffer.alloc(4);
    b.writeUInt32BE(v, 0);
    parts.push(b);
  };
  const putStr = (s) => {
    const b = Buffer.from(s, 'utf8');
    putU32(b.length);
    parts.push(b);
  };
  const putNullableStr = (s) => {
    if (s === null || s === undefined) parts.push(Buffer.from([0x00]));
    else { parts.push(Buffer.from([0x01])); putStr(s); }
  };
  const putBool = (v) => parts.push(Buffer.from([v ? 0x01 : 0x00]));
  const putI64 = (v) => {
    const b = Buffer.alloc(8);
    b.writeBigInt64BE(BigInt(v), 0);
    parts.push(b);
  };
  const putNullableI64 = (v) => {
    if (v === null || v === undefined) parts.push(Buffer.from([0x00]));
    else { parts.push(Buffer.from([0x01])); putI64(v); }
  };

  putStr(op.domain);
  putStr(op.appId);
  putStr(op.userId);
  putStr(op.epoch);
  putStr(op.tableName);
  putStr(op.rowId);
  putStr(op.opId);
  putStr(op.kind);
  putI64(op.expectedRevision);

  if (op.kind !== 'delete' && op.data) {
    const d = op.data;
    putStr(d.id);
    putStr(d.title);
    putBool(d.completed);
    putI64(d.createdAt);
    putNullableStr(d.deadlineDate);
    putNullableStr(d.deadlineTime);
    putNullableStr(d.deadlineZone);
    putNullableI64(d.deadlineOffsetSeconds);
    putNullableStr(d.reminderAnchorTime);
    putNullableI64(d.deadlineAnchorEpochMs);
    putNullableI64(d.deadlineOverdueEpochMs);
    putBool(d.reminderEnabled);
    putI64(d.reminderLeadMinutes);
    putNullableI64(d.reminderIntervalMinutes);
    putI64(d.reminderCount);
    putNullableStr(d.scheduleToken);
  }

  const preimage = Buffer.concat(parts);
  const sha256Hex = crypto.createHash('sha256').update(preimage).digest('hex');
  return { preimageHex: preimage.toString('hex'), sha256Hex, byteLength: preimage.length };
}
const I64_MIN = -9223372036854775808n;
const I64_MAX = 9223372036854775807n;

function checkI64(val, name) {
  if (val < I64_MIN || val > I64_MAX) {
    throw new Error(`${name} overflow: ${val}`);
  }
  return val;
}

function validateOp(op) {
  const expRev = BigInt(op.expectedRevision);
  if (expRev < 0n || expRev > I64_MAX) throw new Error('expectedRevision out of range (0..i64::MAX)');
  if (op.kind === 'delete') {
    if (op.data !== null && op.data !== undefined) throw new Error('delete with data rejected');
    return;
  }
  const d = op.data;
  if (!d) throw new Error('non-delete must have data');
  if (BigInt(d.createdAt) < 0n) throw new Error('createdAt must be non-negative');
  if (BigInt(d.reminderLeadMinutes) < 0n) throw new Error('negative lead');
  const count = BigInt(d.reminderCount);
  if (count < 1n || count > 10n) throw new Error('count out of range');
  if (count === 1n && d.reminderIntervalMinutes !== null && d.reminderIntervalMinutes !== undefined) {
    throw new Error('interval not null when count=1');
  }
  if (count > 1n && (d.reminderIntervalMinutes === null || BigInt(d.reminderIntervalMinutes) < 15n)) {
    throw new Error('interval < 15 for repeating');
  }
  if (!d.deadlineDate) {
    if (d.deadlineTime !== null && d.deadlineTime !== undefined) throw new Error('deadlineTime not null without deadlineDate');
    if (d.deadlineZone !== null && d.deadlineZone !== undefined) throw new Error('deadlineZone not null without deadlineDate');
    if (d.deadlineOffsetSeconds !== null && d.deadlineOffsetSeconds !== undefined) throw new Error('deadlineOffsetSeconds not null without deadlineDate');
    if (d.reminderAnchorTime !== null && d.reminderAnchorTime !== undefined) throw new Error('reminderAnchorTime not null without deadlineDate');
    if (d.deadlineAnchorEpochMs !== null && d.deadlineAnchorEpochMs !== undefined) throw new Error('deadlineAnchorEpochMs not null without deadlineDate');
    if (d.deadlineOverdueEpochMs !== null && d.deadlineOverdueEpochMs !== undefined) throw new Error('deadlineOverdueEpochMs not null without deadlineDate');
    if (d.scheduleToken !== null && d.scheduleToken !== undefined) throw new Error('scheduleToken not null without deadlineDate');
    if (d.reminderEnabled) throw new Error('enabled reminder without deadline');
    if (BigInt(d.reminderLeadMinutes ?? 0) !== 0n) throw new Error('lead not 0 without deadline');
    if (BigInt(d.reminderCount ?? 1) !== 1n) throw new Error('count not 1 without deadline');
    if (d.reminderIntervalMinutes !== null && d.reminderIntervalMinutes !== undefined) {
      throw new Error('interval not null without deadline');
    }
  } else {
    if (d.deadlineZone === null || d.deadlineZone === undefined) throw new Error('deadlineZone required');
    if (d.deadlineOffsetSeconds === null || d.deadlineOffsetSeconds === undefined) throw new Error('deadlineOffsetSeconds required');
    if (d.deadlineAnchorEpochMs === null || d.deadlineAnchorEpochMs === undefined) throw new Error('deadlineAnchorEpochMs required');
    if (d.deadlineOverdueEpochMs === null || d.deadlineOverdueEpochMs === undefined) throw new Error('deadlineOverdueEpochMs required');
    if (d.scheduleToken === null || d.scheduleToken === undefined) throw new Error('scheduleToken required');
    if (!d.deadlineTime) {
      if (d.reminderAnchorTime === null || d.reminderAnchorTime === undefined) throw new Error('reminderAnchorTime required for date-only');
    } else {
      if (d.reminderAnchorTime !== null && d.reminderAnchorTime !== undefined) throw new Error('reminderAnchorTime must be null for timed');
      if (BigInt(d.deadlineOverdueEpochMs) !== BigInt(d.deadlineAnchorEpochMs)) throw new Error('timed overdue must equal anchor');
    }
    const anchor = checkI64(BigInt(d.deadlineAnchorEpochMs), 'anchor');
    const leadMs = checkI64(BigInt(d.reminderLeadMinutes) * 60000n, 'leadMs');
    const first = checkI64(anchor - leadMs, 'firstAnchor');
    const intervalMs = checkI64(BigInt(d.reminderIntervalMinutes ?? 0) * 60000n, 'intervalMs');
    const lastOffset = checkI64((count - 1n) * intervalMs, 'lastOffset');
    const last = checkI64(first + lastOffset, 'lastAnchor');
    if (last > anchor) throw new Error('last after anchor');
  }
}

const fixturePath = resolve(process.cwd(), 'integration/glean-hub/strict-v2-vectors.json');
const fixtureBytes = readFileSync(fixturePath);
const fixtureSha256 = crypto.createHash('sha256').update(fixtureBytes).digest('hex');
const fixture = JSON.parse(fixtureBytes.toString('utf8'));

const hubFixturePath = resolve(process.cwd(), '../glean-hub/glean-hub-plugin/glean-hub-sync/tests/fixtures/strict-v2-vectors.json');
try {
  const hubFixtureBytes = readFileSync(hubFixturePath);
  const hubFixtureSha256 = crypto.createHash('sha256').update(hubFixtureBytes).digest('hex');
  const hubFixture = JSON.parse(hubFixtureBytes.toString('utf8'));
  if (hubFixture.validVectors[0]?.op.appId === fixture.validVectors[0]?.op.appId) {
    if (hubFixtureSha256 !== fixtureSha256) {
      throw new Error(`Cross-repository fixture hash mismatch: TabbyRush (${fixtureSha256}) != Hub (${hubFixtureSha256})`);
    }
  }
} catch (e) {
  if (e.code !== 'ENOENT') throw e;
}
let passed = 0;
for (const v of fixture.validVectors) {
  validateOp(v.op);
  const result = encodePreimage(v.op);
  if (result.byteLength !== v.expectedByteLength) {
    throw new Error(`Vector ${v.name} byteLength mismatch: ${result.byteLength} != ${v.expectedByteLength}`);
  }
  if (result.preimageHex !== v.expectedPreimageHex) {
    throw new Error(`Vector ${v.name} preimageHex mismatch`);
  }
  if (result.sha256Hex !== v.expectedSha256Hex) {
    throw new Error(`Vector ${v.name} sha256Hex mismatch: ${result.sha256Hex} != ${v.expectedSha256Hex}`);
  }
  passed++;
}

let rejectionsPassed = 0;
for (const r of fixture.rejectionVectors) {
  try {
    validateOp(r.op);
    throw new Error(`Expected rejection for ${r.name} did not fail`);
  } catch (err) {
    if (err.message.includes('Expected rejection')) throw err;
    rejectionsPassed++;
  }
}

function canonicalJson(obj) {
  if (obj === null || typeof obj !== 'object') return JSON.stringify(obj);
  if (Array.isArray(obj)) return '[' + obj.map(canonicalJson).join(',') + ']';
  return '{' + Object.keys(obj).sort().map((k) => JSON.stringify(k) + ':' + canonicalJson(obj[k])).join(',') + '}';
}

const v1Schema = JSON.parse(readFileSync(resolve(process.cwd(), 'integration/glean-hub/tabbyrush-schema-v1.json'), 'utf8'));
const v2Schema = JSON.parse(readFileSync(resolve(process.cwd(), 'integration/glean-hub/tabbyrush-schema-v2.json'), 'utf8'));
const FINGERPRINTS = {
  'tabby-rush': {
    v1: '132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8',
    v2: '8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653',
  },
  'doggo': {
    v1: '3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec',
    v2: '5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4',
  },
};
const expectedV1 = FINGERPRINTS[v1Schema.appId]?.v1;
const expectedV2 = FINGERPRINTS[v2Schema.appId]?.v2;
const fpV1 = crypto.createHash('sha256').update(canonicalJson(v1Schema)).digest('hex');
const fpV2 = crypto.createHash('sha256').update(canonicalJson(v2Schema)).digest('hex');
if (fpV1 !== expectedV1) throw new Error(`Schema v1 fingerprint mismatch: ${fpV1} != ${expectedV1}`);
if (fpV2 !== expectedV2) throw new Error(`Schema v2 fingerprint mismatch: ${fpV2} != ${expectedV2}`);
console.log(`schema-fingerprints for appId '${v1Schema.appId}': v1 (${fpV1}) and v2 (${fpV2}) verified.`);
console.log(`strict-v2-vector-runner: ${passed}/${fixture.validVectors.length} valid vectors passed; ${rejectionsPassed}/${fixture.rejectionVectors.length} rejections verified.`);
