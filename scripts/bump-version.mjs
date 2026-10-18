#!/usr/bin/env node
/**
 * scripts/bump-version.mjs
 *
 * TabbyRush Application Version Bump Utility.
 *
 * Updates `appVersion` and `appVersionCode` in `gradle.properties` as the single source
 * of truth for Android, Linux, and Windows multiplatform builds.
 *
 * Usage:
 *   node scripts/bump-version.mjs <patch|minor|major|<explicit-version>> [options]
 *
 * Options:
 *   --dry-run       Preview changes without writing to gradle.properties or creating tags
 *   --tag, --git-tag Automatically create git tag v<version>
 *   --code <int>    Explicitly specify new Android appVersionCode (defaults to current + 1)
 *   --help, -h      Show help message
 *
 * Examples:
 *   node scripts/bump-version.mjs patch
 *   node scripts/bump-version.mjs minor
 *   node scripts/bump-version.mjs major
 *   node scripts/bump-version.mjs 1.1.0
 *   node scripts/bump-version.mjs patch --git-tag
 *   node scripts/bump-version.mjs minor --dry-run
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT_DIR = resolve(__dirname, '..');
const GRADLE_PROPERTIES_PATH = resolve(ROOT_DIR, 'gradle.properties');

const SEMVER_REGEX = /^[0-9]+\.[0-9]+\.[0-9]+$/;

function printUsage() {
  console.log(`
TabbyRush Version Bump Utility

Usage:
  node scripts/bump-version.mjs <patch|minor|major|<explicit-version>> [options]

Arguments:
  patch                  Increment patch version (e.g. 1.0.0 -> 1.0.1)
  minor                  Increment minor version (e.g. 1.0.0 -> 1.1.0)
  major                  Increment major version (e.g. 1.0.0 -> 2.0.0)
  <explicit-version>     Set exact version (e.g. 1.2.0)

Options:
  --dry-run              Preview version bump without modifying files
  --tag, --git-tag       Create annotated git tag v<version>
  --code <int>           Specify custom integer appVersionCode
  --help, -h             Display this documentation
`);
}

function parseSemver(versionStr) {
  if (!SEMVER_REGEX.test(versionStr)) {
    throw new Error(`Invalid semantic version: '${versionStr}'. Expected strict numeric format 'X.Y.Z'.`);
  }
  const [major, minor, patch] = versionStr.split('.').map(Number);
  return { major, minor, patch };
}

function calculateBump(currentVersion, bumpType) {
  const { major, minor, patch } = parseSemver(currentVersion);
  switch (bumpType.toLowerCase()) {
    case 'patch':
      return `${major}.${minor}.${patch + 1}`;
    case 'minor':
      return `${major}.${minor + 1}.0`;
    case 'major':
      return `${major + 1}.0.0`;
    default:
      // If explicit version was passed
      parseSemver(bumpType);
      return bumpType;
  }
}

function readProperties() {
  if (!existsSync(GRADLE_PROPERTIES_PATH)) {
    throw new Error(`gradle.properties not found at: ${GRADLE_PROPERTIES_PATH}`);
  }
  const content = readFileSync(GRADLE_PROPERTIES_PATH, 'utf8');

  let currentVersion = null;
  let currentCode = null;

  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const match = trimmed.match(/^([a-zA-Z0-9._-]+)\s*=\s*(.*)$/);
    if (match) {
      const key = match[1].trim();
      const value = match[2].trim();
      if (key === 'appVersion') currentVersion = value;
      if (key === 'appVersionCode') currentCode = parseInt(value, 10);
    }
  }

  if (!currentVersion) {
    throw new Error(`'appVersion' key is missing from ${GRADLE_PROPERTIES_PATH}`);
  }
  if (!currentCode || isNaN(currentCode)) {
    throw new Error(`'appVersionCode' key is missing or not an integer in ${GRADLE_PROPERTIES_PATH}`);
  }

  return { content, currentVersion, currentCode };
}

function updatePropertiesContent(originalContent, nextVersion, nextCode) {
  const lines = originalContent.split(/\r?\n/);
  let foundVersion = false;
  let foundCode = false;

  const updatedLines = lines.map((line) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || !trimmed) return line;

    if (trimmed.startsWith('appVersion=')) {
      foundVersion = true;
      return `appVersion=${nextVersion}`;
    }
    if (trimmed.startsWith('appVersionCode=')) {
      foundCode = true;
      return `appVersionCode=${nextCode}`;
    }
    return line;
  });

  if (!foundVersion) {
    updatedLines.push(`appVersion=${nextVersion}`);
  }
  if (!foundCode) {
    updatedLines.push(`appVersionCode=${nextCode}`);
  }

  return updatedLines.join('\n');
}

function main() {
  const args = process.argv.slice(2);

  if (args.length === 0 || args.includes('-h') || args.includes('--help')) {
    printUsage();
    process.exit(args.length === 0 ? 1 : 0);
  }

  let bumpArg = null;
  let dryRun = false;
  let createTag = false;
  let explicitCode = null;

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--dry-run') {
      dryRun = true;
    } else if (arg === '--tag' || arg === '--git-tag') {
      createTag = true;
    } else if (arg === '--code') {
      const val = args[++i];
      if (!val || isNaN(parseInt(val, 10)) || parseInt(val, 10) <= 0) {
        throw new Error(`--code requires a positive integer argument, got '${val}'`);
      }
      explicitCode = parseInt(val, 10);
    } else if (!arg.startsWith('-') && !bumpArg) {
      bumpArg = arg;
    } else {
      console.error(`Unknown argument: ${arg}`);
      printUsage();
      process.exit(1);
    }
  }

  if (!bumpArg) {
    console.error('Error: missing bump type (patch|minor|major) or explicit version.');
    printUsage();
    process.exit(1);
  }

  const { content, currentVersion, currentCode } = readProperties();
  const nextVersion = calculateBump(currentVersion, bumpArg);
  const nextCode = explicitCode !== null ? explicitCode : currentCode + 1;

  if (nextCode <= currentCode && explicitCode === null) {
    throw new Error(`Next version code (${nextCode}) must be greater than current code (${currentCode})`);
  }

  console.log('=== TabbyRush Version Bump ===');
  console.log(`Current Version: ${currentVersion} (versionCode: ${currentCode})`);
  console.log(`Target Version:  ${nextVersion} (versionCode: ${nextCode})`);
  console.log(`Target Git Tag:  v${nextVersion}`);

  if (dryRun) {
    console.log('\n[Dry-Run] No files modified.');
    process.exit(0);
  }

  const newContent = updatePropertiesContent(content, nextVersion, nextCode);
  writeFileSync(GRADLE_PROPERTIES_PATH, newContent, 'utf8');
  console.log(`\nUpdated ${GRADLE_PROPERTIES_PATH} successfully.`);

  if (createTag) {
    const tagName = `v${nextVersion}`;
    console.log(`Creating git tag ${tagName}...`);
    try {
      execFileSync('git', ['tag', '-a', tagName, '-m', `Release ${tagName}`], {
        cwd: ROOT_DIR,
        stdio: 'inherit',
      });
      console.log(`Git tag ${tagName} created. Push using: git push origin ${tagName}`);
    } catch (err) {
      console.error(`Failed to create git tag: ${err.message}`);
      process.exit(1);
    }
  } else {
    console.log(`\nNext step: Commit changes and tag release:`);
    console.log(`  git add gradle.properties`);
    console.log(`  git commit -m "chore(release): bump version to ${nextVersion}"`);
    console.log(`  git tag v${nextVersion}`);
    console.log(`  git push origin main v${nextVersion}`);
  }
}
export { parseSemver, calculateBump, updatePropertiesContent, readProperties };

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  try {
    main();
  } catch (err) {
    console.error(`\nError: ${err.message}`);
    process.exit(1);
  }
}
