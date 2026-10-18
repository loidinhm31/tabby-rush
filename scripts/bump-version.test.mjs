#!/usr/bin/env node
/**
 * scripts/bump-version.test.mjs
 *
 * Automated test suite for bump-version.mjs.
 */

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import {
  parseSemver,
  calculateBump,
  updatePropertiesContent,
} from './bump-version.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const SCRIPT_PATH = resolve(__dirname, 'bump-version.mjs');

describe('bump-version: parseSemver', () => {
  it('parses valid semantic version triples', () => {
    assert.deepEqual(parseSemver('1.0.0'), { major: 1, minor: 0, patch: 0 });
    assert.deepEqual(parseSemver('0.1.0'), { major: 0, minor: 1, patch: 0 });
    assert.deepEqual(parseSemver('12.34.56'), { major: 12, minor: 34, patch: 56 });
  });

  it('rejects non-conforming versions', () => {
    assert.throws(() => parseSemver('1.0'), /Expected strict numeric format/);
    assert.throws(() => parseSemver('1.0.0.0'), /Expected strict numeric format/);
    assert.throws(() => parseSemver('1.0.0-rc1'), /Expected strict numeric format/);
    assert.throws(() => parseSemver('v1.0.0'), /Expected strict numeric format/);
    assert.throws(() => parseSemver('latest'), /Expected strict numeric format/);
  });
});

describe('bump-version: calculateBump', () => {
  it('increments patch version', () => {
    assert.equal(calculateBump('1.0.0', 'patch'), '1.0.1');
    assert.equal(calculateBump('1.0.9', 'patch'), '1.0.10');
  });

  it('increments minor version and resets patch', () => {
    assert.equal(calculateBump('1.0.5', 'minor'), '1.1.0');
    assert.equal(calculateBump('0.1.9', 'minor'), '0.2.0');
  });

  it('increments major version and resets minor and patch', () => {
    assert.equal(calculateBump('1.2.3', 'major'), '2.0.0');
  });

  it('accepts valid explicit versions', () => {
    assert.equal(calculateBump('1.0.0', '2.5.1'), '2.5.1');
  });

  it('rejects invalid explicit version arguments', () => {
    assert.throws(() => calculateBump('1.0.0', 'invalid'), /Expected strict numeric format/);
  });
});

describe('bump-version: updatePropertiesContent', () => {
  it('updates existing appVersion and appVersionCode while preserving other lines', () => {
    const original = `# Comment header
org.gradle.parallel=true
appVersion=1.0.0
appVersionCode=1
android.useAndroidX=true
`;
    const updated = updatePropertiesContent(original, '1.1.0', 2);
    assert.match(updated, /appVersion=1\.1\.0/);
    assert.match(updated, /appVersionCode=2/);
    assert.match(updated, /org\.gradle\.parallel=true/);
    assert.match(updated, /android\.useAndroidX=true/);
    assert.match(updated, /# Comment header/);
  });

  it('appends appVersion and appVersionCode if not present', () => {
    const original = `org.gradle.parallel=true\n`;
    const updated = updatePropertiesContent(original, '1.0.0', 1);
    assert.match(updated, /appVersion=1\.0\.0/);
    assert.match(updated, /appVersionCode=1/);
  });
});

describe('bump-version: CLI execution (dry-run mode)', () => {
  it('executes patch bump in dry-run mode without errors', () => {
    const stdout = execFileSync('node', [SCRIPT_PATH, 'patch', '--dry-run'], {
      encoding: 'utf8',
    });
    assert.match(stdout, /=== TabbyRush Version Bump ===/);
    assert.match(stdout, /Current Version: 1\.0\.0/);
    assert.match(stdout, /Target Version:  1\.0\.1/);
    assert.match(stdout, /\[Dry-Run\] No files modified\./);
  });

  it('executes minor bump in dry-run mode with custom code', () => {
    const stdout = execFileSync('node', [SCRIPT_PATH, 'minor', '--code', '10', '--dry-run'], {
      encoding: 'utf8',
    });
    assert.match(stdout, /Target Version:  1\.1\.0 \(versionCode: 10\)/);
    assert.match(stdout, /\[Dry-Run\] No files modified\./);
  });

  it('fails closed on missing bump type', () => {
    assert.throws(
      () => execFileSync('node', [SCRIPT_PATH], { stdio: 'pipe' }),
      /Command failed/
    );
  });

  it('fails closed on invalid version string', () => {
    assert.throws(
      () => execFileSync('node', [SCRIPT_PATH, 'not-a-version', '--dry-run'], { stdio: 'pipe' }),
      /Command failed/
    );
  });
});
