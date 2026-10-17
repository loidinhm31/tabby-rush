#!/usr/bin/env node
/**
 * scripts/generate-strict-v2-vectors.mjs
 *
 * Generates canonical test vectors for strict-v2 sync protocol.
 * Emits JSON fixture at integration/glean-hub/strict-v2-vectors.json with
 * explicit preimageHex, sha256Hex, BigInt integer values, and error assertions.
 */

import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

  // 1-8 framed identity strings
  putStr(op.domain);
  putStr(op.appId);
  putStr(op.userId);
  putStr(op.epoch);
  putStr(op.tableName);
  putStr(op.rowId);
  putStr(op.opId);
  putStr(op.kind);

  // 9 expectedRevision
  putI64(op.expectedRevision);

  // 10-25 data payload (empty for delete)
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
  const hash = crypto.createHash('sha256').update(preimage).digest('hex');
  return {
    preimageHex: preimage.toString('hex'),
    sha256Hex: hash,
    byteLength: preimage.length,
  };
}

const baseOp = {
  domain: 'strict-v2',
  appId: 'tabby-rush',
  userId: '65a123456789abcdef012345',
  epoch: '550e8400-e29b-41d4-a716-446655440000',
  tableName: 'todos',
  rowId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
  opId: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
};

const validCases = [
  {
    name: 'migrated_null_default',
    description: 'Migrated record with null deadline and disabled default reminder rule',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Buy groceries',
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
      },
    },
  },
  {
    name: 'date_only_deadline',
    description: 'Date-only deadline with reminderAnchorTime and calendar day start overdue',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '1',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'File quarterly taxes',
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
      },
    },
  },
  {
    name: 'timed_dst_overlap_offset_standard',
    description: 'Timed deadline during DST overlap with chosen standard offset (-18000s)',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '2',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Fall back shift review',
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
      },
    },
  },
  {
    name: 'repeating_rule_count10',
    description: 'Repeating reminder with maximum count (10) and minimum interval (15m)',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '3',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Submit urgent report',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: '2026-10-20',
        deadlineTime: '17:00',
        deadlineZone: 'Asia/Saigon',
        deadlineOffsetSeconds: '25200',
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: '1792490400000',
        deadlineOverdueEpochMs: '1792490400000',
        reminderEnabled: true,
        reminderLeadMinutes: '135',
        reminderIntervalMinutes: '15',
        reminderCount: '10',
        scheduleToken: '00000000-0000-0000-0000-000000000003',
      },
    },
  },
  {
    name: 'disabled_rule_with_deadline',
    description: 'Deadline configured with preserved reminder preferences while reminderEnabled is false',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '4',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Optional review',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: '2026-10-25',
        deadlineTime: '10:00',
        deadlineZone: 'UTC',
        deadlineOffsetSeconds: '0',
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: '1792836000000',
        deadlineOverdueEpochMs: '1792836000000',
        reminderEnabled: false,
        reminderLeadMinutes: '30',
        reminderIntervalMinutes: '15',
        reminderCount: '2',
        scheduleToken: '00000000-0000-0000-0000-000000000004',
      },
    },
  },
  {
    name: 'cleared_deadline',
    description: 'Previously set deadline cleared, all deadline and token fields reset to null',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '5',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'No deadline task',
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
      },
    },
  },
  {
    name: 'unicode_title_and_zone',
    description: 'Unicode title characters and multibyte timezone representation',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '6',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Lập kế hoạch dự án 🎯 日本語',
        completed: false,
        createdAt: '1791840000000',
        deadlineDate: '2026-12-31',
        deadlineTime: '23:59',
        deadlineZone: 'Asia/Tokyo',
        deadlineOffsetSeconds: '32400',
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: '1798729140000',
        deadlineOverdueEpochMs: '1798729140000',
        reminderEnabled: true,
        reminderLeadMinutes: '60',
        reminderIntervalMinutes: '30',
        reminderCount: '3',
        scheduleToken: '00000000-0000-0000-0000-000000000005',
      },
    },
  },
  {
    name: 'delete_operation',
    description: 'Delete operation with empty data payload contributing 0 data bytes',
    op: {
      ...baseOp,
      kind: 'delete',
      expectedRevision: '7',
      data: null,
    },
  },
  {
    name: 'negative_epoch_instant',
    description: 'Signed i64 negative epoch milliseconds (pre-1970 date) two-complement representation',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '8',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Apollo 11 milestone anniversary',
        completed: true,
        createdAt: '1000',
        deadlineDate: '1969-07-20',
        deadlineTime: '20:17',
        deadlineZone: 'UTC',
        deadlineOffsetSeconds: '0',
        reminderAnchorTime: null,
        deadlineAnchorEpochMs: '-14182980000',
        deadlineOverdueEpochMs: '-14182980000',
        reminderEnabled: false,
        reminderLeadMinutes: '0',
        reminderIntervalMinutes: null,
        reminderCount: '1',
        scheduleToken: '00000000-0000-0000-0000-000000000006',
      },
    },
  },
  {
    name: 'max_i64_expected_revision',
    description: 'Signed i64 maximum expectedRevision (9223372036854775807) boundary representation',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '9223372036854775807',
      data: {
        id: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
        title: 'Max revision task',
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
      },
    },
  },
];

const rejectionCases = [
  {
    name: 'reject_count_zero',
    reason: 'reminderCount must be >= 1',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderCount: '0' },
    },
  },
  {
    name: 'reject_count_eleven',
    reason: 'reminderCount must be <= 10',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderCount: '11' },
    },
  },
  {
    name: 'reject_interval_below_15',
    reason: 'repeating reminderIntervalMinutes must be >= 15',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderCount: '2', reminderIntervalMinutes: '14' },
    },
  },
  {
    name: 'reject_interval_not_null_when_count_1',
    reason: 'reminderIntervalMinutes must be null when reminderCount is 1',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderCount: '1', reminderIntervalMinutes: '15' },
    },
  },
  {
    name: 'reject_negative_lead',
    reason: 'reminderLeadMinutes must be non-negative',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderLeadMinutes: '-1' },
    },
  },
  {
    name: 'reject_last_after_anchor',
    reason: 'last reminder occurrence cannot exceed anchor epoch',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '1',
      data: {
        ...validCases[1].op.data,
        reminderAnchorTime: null,
        deadlineTime: '12:00',
        deadlineAnchorEpochMs: '1000',
        deadlineOverdueEpochMs: '1000',
        reminderEnabled: true,
        reminderLeadMinutes: '10',
        reminderCount: '2',
        reminderIntervalMinutes: '15',
      },
    },
  },
  {
    name: 'reject_negative_created_at',
    reason: 'createdAt must be non-negative',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, createdAt: '-1' },
    },
  },
  {
    name: 'reject_missing_deadline_when_enabled',
    reason: 'deadline must be present when reminderEnabled is true',
    op: {
      ...baseOp,
      kind: 'create',
      expectedRevision: '0',
      data: { ...validCases[0].op.data, reminderEnabled: true },
    },
  },
  {
    name: 'reject_delete_with_data',
    reason: 'DELETE operation must not have data payload',
    op: {
      ...baseOp,
      kind: 'delete',
      expectedRevision: '1',
      data: { ...validCases[0].op.data },
    },
  },
  {
    name: 'reject_expected_revision_overflow',
    reason: 'expectedRevision must not exceed signed 64-bit max (9223372036854775807)',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '9223372036854775808',
      data: { ...validCases[0].op.data },
    },
  },
  {
    name: 'reject_negative_expected_revision',
    reason: 'expectedRevision must be non-negative',
    op: {
      ...baseOp,
      kind: 'update',
      expectedRevision: '-1',
      data: { ...validCases[0].op.data },
    },
  },
];

const processedValid = validCases.map((vc) => {
  const encoded = encodePreimage(vc.op);
  return {
    name: vc.name,
    description: vc.description,
    op: vc.op,
    expectedPreimageHex: encoded.preimageHex,
    expectedSha256Hex: encoded.sha256Hex,
    expectedByteLength: encoded.byteLength,
  };
});

const outputFixture = {
  version: 'strict-v2-vector-v1',
  domain: 'strict-v2',
  description: 'Canonical reference test vectors for TabbyRush strict-v2 sync protocol (Gate G1)',
  validVectors: processedValid,
  rejectionVectors: rejectionCases,
};

const targetPath = resolve(process.cwd(), 'integration/glean-hub/strict-v2-vectors.json');
writeFileSync(targetPath, JSON.stringify(outputFixture, null, 2) + '\n', 'utf8');
console.log(`Generated ${processedValid.length} valid vectors and ${rejectionCases.length} rejection vectors at ${targetPath}`);
