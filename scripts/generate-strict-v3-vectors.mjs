#!/usr/bin/env node
/**
 * scripts/generate-strict-v3-vectors.mjs
 *
 * Generates canonical test vectors for strict-v3 sync protocol.
 * Emits JSON fixture at integration/glean-hub/strict-v3-vectors.json with
 * explicit preimageHex, sha256Hex, BigInt integer values, and error assertions.
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import crypto from 'node:crypto';

function encodePreimage(op) {
  const parts = [];

  function putU32(val) {
    const b = Buffer.alloc(4);
    b.writeUInt32BE(val, 0);
    parts.push(b);
  }

  function putStr(str) {
    const b = Buffer.from(str, 'utf8');
    putU32(b.length);
    parts.push(b);
  }

  function putNullableStr(str) {
    if (str === null || str === undefined) {
      parts.push(Buffer.from([0x00]));
    } else {
      parts.push(Buffer.from([0x01]));
      putStr(str);
    }
  }

  function putBool(val) {
    parts.push(Buffer.from([val ? 0x01 : 0x00]));
  }

  function putI64(val) {
    const b = Buffer.alloc(8);
    b.writeBigInt64BE(BigInt(val), 0);
    parts.push(b);
  }

  function putNullableI64(val) {
    if (val === null || val === undefined) {
      parts.push(Buffer.from([0x00]));
    } else {
      parts.push(Buffer.from([0x01]));
      putI64(val);
    }
  }

  // Envelope
  putStr(op.domain);
  putStr(op.appId);
  putStr(op.userId);
  putStr(op.epoch);
  putStr(op.tableName);
  putStr(op.rowId);
  putStr(op.opId);
  putStr(op.kind);
  putI64(op.expectedRevision);

  // Data payload
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
    // 17th field: parentTaskId
    putNullableStr(d.parentTaskId);
  }

  const preimage = Buffer.concat(parts);
  const sha256Hex = crypto.createHash('sha256').update(preimage).digest('hex');
  return {
    preimageHex: preimage.toString('hex'),
    sha256Hex,
    byteLength: preimage.length,
  };
}

const baseOp = {
  domain: 'strict-v3',
  appId: 'tabby-rush',
  userId: '65a123456789abcdef012345',
  epoch: '550e8400-e29b-41d4-a716-446655440000',
  tableName: 'todos',
  rowId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
  opId: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
};

const parentRowId = 'e1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c';

const validCases = [
  {
    name: 'root_null_parent',
    description: 'Root task with null parentTaskId, null deadline, and disabled reminders',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Parent project plan',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: null,
      },
    },
  },
  {
    name: 'child_with_parent',
    description: 'Child task referencing valid parentTaskId with null deadline',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Subtask 1 - Setup CI',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: parentRowId,
      },
    },
  },
  {
    name: 'child_with_own_deadline',
    description: 'Child task with its own date-only deadline and parent reference',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '1',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Subtask 2 - Draft specs',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: '2026-10-31',
        deadlineTime: null,
        deadlineZone: 'America/New_York',
        deadlineOffsetSeconds: '-14400',
        reminderAnchorTime: '09:00',
        deadlineAnchorEpochMs: '1793451600000',
        deadlineOverdueEpochMs: '1793505600000',
        reminderEnabled: true,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: '00000000-0000-0000-0000-000000000001',
        parentTaskId: parentRowId,
      },
    },
  },
  {
    name: 'child_timed_deadline_dst',
    description: 'Child task with timed deadline during DST overlap and parent reference',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '2',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Subtask 3 - Review code before cutover',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: '2026-11-01',
        deadlineTime: '01:30',
        deadlineZone: 'America/New_York',
        deadlineOffsetSeconds: '-18000',
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: '1793514600000',
        deadlineOverdueEpochMs: '1793514600000',
        reminderEnabled: false,
        reminderLeadMinutes: '15',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: '00000000-0000-0000-0000-000000000002',
        parentTaskId: parentRowId,
      },
    },
  },
  {
    name: 'child_completed_update',
    description: 'Child task update marking it completed while keeping parentTaskId immutable',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '3',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Subtask 1 - Setup CI',
        completed: true,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: parentRowId,
      },
    },
  },
  {
    name: 'delete_operation_empty_data',
    description: 'Delete operation in strict-v3 with null data payload',
    op: {
      ...baseOp,
      kind: 'delete',
      expectedRevision: '4',
      data: null,
    },
  },
];

const validVectors = validCases.map((c) => {
  const enc = encodePreimage(c.op);
  return {
    name: c.name,
    description: c.description,
    op: c.op,
    expectedPreimageHex: enc.preimageHex,
    expectedSha256Hex: enc.sha256Hex,
    expectedByteLength: enc.byteLength,
  };
});

const rejectionVectors = [
  {
    name: 'self_referential_parent',
    description: 'Rejection: parentTaskId must not equal id (self-reference)',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Self-referencing task',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      },
    },
    expectedError: 'parentTaskId must not equal id',
  },
  {
    name: 'blank_parent_task_id',
    description: 'Rejection: parentTaskId must not be empty or blank string',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Blank parent task',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: '   ',
      },
    },
    expectedError: 'parentTaskId must not be blank',
  },
  {
    name: 'delete_with_non_null_data',
    description: 'Rejection: delete operation must not have data payload',
    op: {
      ...baseOp,
      kind: 'delete',
      expectedRevision: '1',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Delete with payload',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: null,
      },
    },
    expectedError: 'DELETE operation must not have data payload',
  },
  {
    name: 'create_with_null_data',
    description: 'Rejection: create operation must have non-null data payload',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: null,
    },
    expectedError: 'operation must have non-null data payload',
  },
  {
    name: 'expected_revision_overflow',
    description: 'Rejection: expectedRevision exceeds i64::MAX',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '9223372036854775808',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Overflow revision',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: null,
        deadlineTime: null,
        deadlineZone: null,
        deadlineOffsetSeconds: null,
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: null,
        deadlineOverdueEpochMs: null,
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: null,
        parentTaskId: null,
      },
    },
    expectedError: 'expectedRevision must not exceed i64::MAX',
  },
];

const output = {
  version: 'strict-v3-vector-v1',
  domain: 'strict-v3',
  description: 'Canonical reference test vectors for TabbyRush strict-v3 sync protocol (Hierarchy and parentTaskId)',
  validVectors,
  rejectionVectors,
};

const outputPath = resolve(process.cwd(), 'integration/glean-hub/strict-v3-vectors.json');
mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, JSON.stringify(output, null, 2) + '\n', 'utf8');
console.log(`Wrote ${validVectors.length} valid and ${rejectionVectors.length} rejection vectors to ${outputPath}`);
