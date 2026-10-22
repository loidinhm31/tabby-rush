#!/usr/bin/env node
/**
 * scripts/hub-schema-provision.test.mjs
 *
 * Automated behavioral tests for hub-schema-provision.mjs.
 * Verifies schema and assignment policy validation, refusal on invalid policy before POST,
 * legacy omission preservation, and import safety.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readFileSync, existsSync, writeFileSync, unlinkSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import http from 'node:http';
import {
  validateProvisioningInput,
  validateAssignmentPolicy,
  validateSharingConfig,
  isMainModule,
} from './hub-schema-provision.mjs';

describe('hub-schema-provision: validation logic', () => {
  const validTabbyRushSchemaPath = resolve('integration/glean-hub/tabbyrush-schema.json');
  const validTabbyRushContent = JSON.parse(readFileSync(validTabbyRushSchemaPath, 'utf8'));

  it('accepts the canonical TabbyRush schema with taskAssignees and assignment policy', () => {
    const validated = validateProvisioningInput(validTabbyRushContent, 'tabby-rush');
    assert.equal(validated.appId, 'tabby-rush');
    assert.ok(validated.schema.todos);
    assert.ok(validated.schema.taskAssignees);
    assert.ok(validated.sharing);
    assert.equal(validated.sharing.sharingTable, 'taskAssignees');
    assert.equal(validated.sharing.parentTable, 'todos');
    assert.equal(validated.sharing.assignmentPolicy.version, 1);
    assert.equal(validated.sharing.assignmentPolicy.hierarchyParentField, 'parentTaskId');
    assert.equal(validated.sharing.assignmentPolicy.editableFields.length, 14);
  });

  it('accepts legacy schemas without sharing config', () => {
    const legacy = {
      appId: 'legacy-app',
      schema: {
        items: {
          primaryKey: 'id',
          columns: [{ name: 'id', type: 'string', nullable: false }],
        },
      },
    };
    const validated = validateProvisioningInput(legacy, 'legacy-app');
    assert.equal(validated.appId, 'legacy-app');
    assert.equal(validated.sharing, undefined);
  });

  it('accepts legacy schemas with document sharing but omitted assignment policy', () => {
    const legacySharing = {
      appId: 'vocab-app',
      schema: {
        collections: {
          primaryKey: 'id',
          columns: [{ name: 'id', type: 'string', nullable: false }],
        },
        collectionSharedUsers: {
          primaryKey: 'id',
          columns: [
            { name: 'id', type: 'string', nullable: false },
            { name: 'collectionId', type: 'string', nullable: false },
            { name: 'userId', type: 'string', nullable: false },
          ],
        },
      },
      sharing: {
        sharingTable: 'collectionSharedUsers',
        parentIdField: 'collectionId',
        userIdField: 'userId',
        parentTable: 'collections',
        childTables: [],
      },
    };
    const validated = validateProvisioningInput(legacySharing, 'vocab-app');
    assert.equal(validated.sharing.assignmentPolicy, undefined);
  });

  it('rejects appId mismatch with confirm-app', () => {
    assert.throws(
      () => validateProvisioningInput(validTabbyRushContent, 'wrong-app'),
      /--confirm-app must match/,
    );
  });

  it('rejects strict sync metadata and client-assigned schemaVersion', () => {
    assert.throws(
      () =>
        validateProvisioningInput(
          { ...validTabbyRushContent, schemaVersion: 2 },
          'tabby-rush',
        ),
      /without strict metadata or a client-assigned schemaVersion/,
    );
    assert.throws(
      () =>
        validateProvisioningInput(
          { ...validTabbyRushContent, syncMode: 'strict' },
          'tabby-rush',
        ),
      /without strict metadata or a client-assigned schemaVersion/,
    );
  });

  it('rejects unsupported assignment policy version (!= 1)', () => {
    const badVersion = JSON.parse(JSON.stringify(validTabbyRushContent));
    badVersion.sharing.assignmentPolicy.version = 2;
    assert.throws(
      () => validateProvisioningInput(badVersion, 'tabby-rush'),
      /Unsupported assignmentPolicy version: 2/,
    );
  });

  it('rejects structural, identity, and hierarchy fields in editableFields allowlist', () => {
    const forbidden = [
      'id',
      'createdAt',
      'userId',
      'ownerUserId',
      'taskId',
      'syncVersion',
      'syncedAt',
      'deleted',
      'deletedAt',
      'parentTaskId',
    ];

    for (const field of forbidden) {
      const badFields = JSON.parse(JSON.stringify(validTabbyRushContent));
      badFields.sharing.assignmentPolicy.editableFields = ['title', field];
      assert.throws(
        () => validateProvisioningInput(badFields, 'tabby-rush'),
        new RegExp(`cannot include structural, identity, or hierarchy field '${field}'`),
      );
    }
  });

  it('rejects missing or non-nullable hierarchyParentField', () => {
    const missingField = JSON.parse(JSON.stringify(validTabbyRushContent));
    missingField.sharing.assignmentPolicy.hierarchyParentField = 'nonExistentField';
    assert.throws(
      () => validateProvisioningInput(missingField, 'tabby-rush'),
      /does not exist in parent table/,
    );

    const nonNullable = JSON.parse(JSON.stringify(validTabbyRushContent));
    const parentCol = nonNullable.schema.todos.columns.find((c) => c.name === 'parentTaskId');
    parentCol.nullable = false;
    assert.throws(
      () => validateProvisioningInput(nonNullable, 'tabby-rush'),
      /must be nullable/,
    );
  });

  it('rejects relation and content table overlap', () => {
    const overlap = JSON.parse(JSON.stringify(validTabbyRushContent));
    overlap.sharing.sharingTable = 'todos';
    assert.throws(
      () => validateProvisioningInput(overlap, 'tabby-rush'),
      /cannot be the same as parentTable/,
    );

    const childOverlap = JSON.parse(JSON.stringify(validTabbyRushContent));
    childOverlap.sharing.childTables = ['taskAssignees'];
    assert.throws(
      () => validateProvisioningInput(childOverlap, 'tabby-rush'),
      /cannot be included in childTables/,
    );
  });

  it('rejects sharingTable lacking required relation columns', () => {
    const missingCol = JSON.parse(JSON.stringify(validTabbyRushContent));
    missingCol.schema.taskAssignees.columns = [{ name: 'id', type: 'string', nullable: false }];
    assert.throws(
      () => validateProvisioningInput(missingCol, 'tabby-rush'),
      /must have column 'taskId' matching parentIdField/,
    );
  });
});

describe('hub-schema-provision: CLI safety and refusal before network or files', () => {
  it('refuses invalid assignment policy before POST with 0 requests and no output file', async () => {
    let requestsReceived = 0;
    const server = http.createServer((req, res) => {
      requestsReceived++;
      res.writeHead(500);
      res.end('Should not receive requests');
    });

    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    const port = server.address().port;
    const origin = `http://127.0.0.1:${port}`;

    const tempDir = tmpdir();
    const badSchemaFile = join(tempDir, `bad-policy-${Date.now()}.json`);
    const outputFile = join(tempDir, `out-${Date.now()}.json`);

    try {
      const invalidPolicyContent = {
        appId: 'test-app',
        schema: {
          todos: {
            primaryKey: 'id',
            columns: [
              { name: 'id', type: 'string', nullable: false },
              { name: 'title', type: 'string', nullable: false },
            ],
          },
          taskAssignees: {
            primaryKey: 'id',
            columns: [
              { name: 'id', type: 'string', nullable: false },
              { name: 'taskId', type: 'string', nullable: false },
              { name: 'userId', type: 'string', nullable: false },
            ],
          },
        },
        sharing: {
          sharingTable: 'taskAssignees',
          parentIdField: 'taskId',
          userIdField: 'userId',
          parentTable: 'todos',
          childTables: [],
          assignmentPolicy: {
            version: 999, // UNSUPPORTED VERSION!
            hierarchyParentField: 'parentTaskId',
            editableFields: ['title'],
          },
        },
      };

      writeFileSync(badSchemaFile, JSON.stringify(invalidPolicyContent, null, 2), 'utf8');

      const result = spawnSync(
        process.execPath,
        [
          resolve('scripts/hub-schema-provision.mjs'),
          '--origin',
          origin,
          '--schema',
          badSchemaFile,
          '--out',
          outputFile,
          '--confirm-app',
          'test-app',
        ],
        {
          env: { ...process.env, GLEAN_ADMIN_TOKEN: 'mock-test-token' },
          encoding: 'utf8',
        },
      );

      // Must fail closed with non-zero exit code
      assert.notEqual(result.status, 0, 'CLI should exit with non-zero status on invalid policy');
      assert.match(
        result.stderr,
        /Unsupported assignmentPolicy version: 999/,
        'Error message should report unsupported policy version',
      );

      // Must NOT send any HTTP requests to server
      assert.equal(requestsReceived, 0, 'Zero HTTP requests should be dispatched on invalid policy');

      // Must NOT create or leave any output credential file
      assert.equal(existsSync(outputFile), false, 'Output credential file must not be created');
    } finally {
      if (existsSync(badSchemaFile)) unlinkSync(badSchemaFile);
      if (existsSync(outputFile)) unlinkSync(outputFile);
      await new Promise((resolve) => server.close(resolve));
    }
  });

  it('isMainModule returns false when imported', () => {
    assert.equal(isMainModule(), false);
  });
});
