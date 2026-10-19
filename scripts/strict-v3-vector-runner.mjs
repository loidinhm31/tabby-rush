#!/usr/bin/env node
/**
 * scripts/strict-v3-vector-runner.mjs
 *
 * Independent Node.js runner to verify canonical strict-v3 preimages and digests.
 * Validates cross-language agreement against integration/glean-hub/strict-v3-vectors.json.
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
    putNullableStr(d.parentTaskId);
  }
  const preimage = Buffer.concat(parts);
  const sha256Hex = crypto.createHash('sha256').update(preimage).digest('hex');
  return { preimageHex: preimage.toString('hex'), sha256Hex, byteLength: preimage.length };
}
function checkI64(val, name) {
  const min = -9223372036854775808n;
  const max = 9223372036854775807n;
  if (val < min || val > max) throw new Error(`${name} must not exceed i64::MAX (signed 64-bit range)`);
  return val;
}

function validateOp(op) {
  const rev = checkI64(BigInt(op.expectedRevision), 'expectedRevision');
  if (rev < 0n) throw new Error('expectedRevision must not be negative');

  if (op.kind === 'delete') {
    if (op.data !== null && op.data !== undefined) {
      throw new Error('DELETE operation must not have data payload');
    }
    return;
  }

  if (!op.data) {
    throw new Error('operation must have non-null data payload');
  }

  const d = op.data;
  if (!d.id || typeof d.id !== 'string') throw new Error('id must not be empty');
  checkI64(BigInt(d.createdAt), 'createdAt');
  if (BigInt(d.createdAt) < 0n) throw new Error('createdAt must be non-negative');
  checkI64(BigInt(d.reminderLeadMinutes), 'reminderLeadMinutes');
  if (BigInt(d.reminderLeadMinutes) < 0n) throw new Error('reminderLeadMinutes must be non-negative');

  const count = checkI64(BigInt(d.reminderCount), 'reminderCount');
  if (count < 1n || count > 10n) throw new Error('reminderCount must be 1..10');
  if (count === 1n && d.reminderIntervalMinutes !== null && d.reminderIntervalMinutes !== undefined) {
    throw new Error('reminderIntervalMinutes must be null when reminderCount is 1');
  }
  if (count > 1n) {
    if (d.reminderIntervalMinutes === null || d.reminderIntervalMinutes === undefined) {
      throw new Error('repeating reminderIntervalMinutes must be >= 15');
    }
    const interval = checkI64(BigInt(d.reminderIntervalMinutes), 'reminderIntervalMinutes');
    if (interval < 15n) throw new Error('repeating reminderIntervalMinutes must be >= 15');
  }

  if (d.parentTaskId !== null && d.parentTaskId !== undefined) {
    if (typeof d.parentTaskId !== 'string' || d.parentTaskId.trim().length === 0) {
      throw new Error('parentTaskId must not be blank');
    }
    if (d.parentTaskId === d.id) {
      throw new Error('parentTaskId must not equal id');
    }
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(d.parentTaskId)) {
      throw new Error('parentTaskId must be a canonical lowercase UUIDv4');
    }
  }

  if (!d.deadlineDate) {
    if (d.deadlineTime || d.deadlineZone || d.deadlineOffsetSeconds !== null && d.deadlineOffsetSeconds !== undefined ||
        d.reminderAnchorTime || d.deadlineAnchorEpochMs !== null && d.deadlineAnchorEpochMs !== undefined ||
        d.deadlineOverdueEpochMs !== null && d.deadlineOverdueEpochMs !== undefined ||
        d.scheduleToken || d.reminderEnabled || BigInt(d.reminderLeadMinutes) !== 0n || count !== 1n ||
        d.reminderIntervalMinutes !== null && d.reminderIntervalMinutes !== undefined) {
      throw new Error('deadline fields must be null when deadlineDate is null');
    }
  }
}

const fixturePath = resolve(process.cwd(), 'integration/glean-hub/strict-v3-vectors.json');
const fixtureBytes = readFileSync(fixturePath);
const fixtureSha256 = crypto.createHash('sha256').update(fixtureBytes).digest('hex');
const fixture = JSON.parse(fixtureBytes.toString('utf8'));

const hubFixturePath = resolve(process.cwd(), '../glean-hub/glean-hub-plugin/glean-hub-sync/tests/fixtures/strict-v3-vectors.json');
try {
  const hubFixtureBytes = readFileSync(hubFixturePath);
  const hubFixtureSha256 = crypto.createHash('sha256').update(hubFixtureBytes).digest('hex');
  if (hubFixtureSha256 !== fixtureSha256) {
    throw new Error(`Cross-repository fixture hash mismatch: TabbyRush (${fixtureSha256}) != Hub (${hubFixtureSha256})`);
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
  let threw = false;
  try {
    validateOp(r.op);
    encodePreimage(r.op);
  } catch (e) {
    threw = true;
    if (!e.message.includes(r.expectedError)) {
      throw new Error(`Rejection vector ${r.name} error message mismatch: got '${e.message}', expected to contain '${r.expectedError}'`);
    }
  }
  if (!threw) {
    throw new Error(`Rejection vector ${r.name} failed to reject invalid op`);
  }
  rejectionsPassed++;
}

function canonicalJson(obj) {
  if (obj === null || typeof obj !== 'object') return JSON.stringify(obj);
  if (Array.isArray(obj)) return '[' + obj.map(canonicalJson).join(',') + ']';
  return '{' + Object.keys(obj).sort().map((k) => JSON.stringify(k) + ':' + canonicalJson(obj[k])).join(',') + '}';
}

const v3Schema = JSON.parse(readFileSync(resolve(process.cwd(), 'integration/glean-hub/tabbyrush-schema-v3.json'), 'utf8'));
const EXPECTED_FP_TABBYRUSH_V3 = '7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f';
const fpV3 = crypto.createHash('sha256').update(canonicalJson(v3Schema)).digest('hex');
if (fpV3 !== EXPECTED_FP_TABBYRUSH_V3) throw new Error(`Schema v3 fingerprint mismatch: ${fpV3} != ${EXPECTED_FP_TABBYRUSH_V3}`);

console.log(`schema-fingerprints: v3 (${fpV3}) verified against canonical definition.`);
console.log(`strict-v3-vector-runner: ${passed}/${fixture.validVectors.length} valid vectors passed; ${rejectionsPassed}/${fixture.rejectionVectors.length} rejections verified.`);
