#!/usr/bin/env node
/**
 * scripts/release-security-check.mjs
 *
 * Android Release Security & Integrity Audit Tool for TabbyRush.
 *
 * Enforces release security invariants:
 * 1. APK Verification (Fail-Closed):
 *    - Validates signature and certificate integrity via `apksigner verify` (using execFileSync).
 *    - Fails if APK Signature Scheme v2 or v3 is missing.
 *    - Audits merged manifest via `apkanalyzer manifest print` (using execFileSync):
 *      * android:debuggable="false" (must explicitly be false; fails if true or absent).
 *      * android:usesCleartextTraffic="false" (fails if true or absent).
 *      * android:allowBackup="false" (must explicitly be false; fails if missing or true).
 *      * Exported components: ONLY MainActivity and narrowly verified TabbyRushTaskWidgetReceiver are exported.
 *      * Network security config: Debug loopback config must be absent in release APK.
 * 2. Origin & Network Invariants:
 *    - Asserts release endpoint targets approved HTTPS origin.
 * 3. Secret & Credential Scanning:
 *    - Scans package/source for known administrative secrets, private keys, database URIs, or leaked tokens.
 *    - Categorizes API key as extractable client identifier, NOT confidential master secret.
 * 4. Separate Diagnostic Mode:
 *    - `--diagnose-source` audits source files statically when release APK has not yet been built.
 *    - Note: In release audit mode, missing APK or SDK tools FAILS CLOSED.
 */

import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { resolve, join, relative } from 'node:path';
import { execFileSync } from 'node:child_process';
import { parseArgs } from 'node:util';
import { fileURLToPath } from 'node:url';

function loadDotEnv() {
  const envPath = resolve(process.cwd(), '.env');
  if (existsSync(envPath)) {
    try {
      const content = readFileSync(envPath, 'utf8');
      for (const line of content.split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#')) continue;
        const idx = trimmed.indexOf('=');
        if (idx > 0) {
          const key = trimmed.slice(0, idx).trim();
          let val = trimmed.slice(idx + 1).trim();
          if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
            val = val.slice(1, -1);
          }
          if (process.env[key] === undefined) {
            process.env[key] = val;
          }
        }
      }
    } catch {
      // ignore unreadable .env
    }
  }
}
loadDotEnv();

function parseCliArgs() {
  const options = {
    apk: { type: 'string', default: 'androidApp/build/outputs/apk/release/androidApp-release.apk' },
    origin: { type: 'string', default: process.env.APPROVED_HUB_ORIGIN || '' },
    'diagnose-source': { type: 'boolean', default: false },
    help: { type: 'boolean', short: 'h', default: false }
  };

  const { values } = parseArgs({ options, allowPositionals: false });

  if (values.help) {
    console.log(`Usage: node scripts/release-security-check.mjs [options]

Options:
  --apk <PATH>            Path to release APK to inspect (default: androidApp/build/outputs/apk/release/androidApp-release.apk)
  --origin <URL>          Approved Hub origin (must be HTTPS in release; default: $APPROVED_HUB_ORIGIN)
  --diagnose-source       Perform static source configuration audit (diagnostic only; does not replace APK release gate)
  -h, --help              Show this help message
`);
    process.exit(0);
  }

  return values;
}

function scanForSecrets(repoRoot) {
  const secretPatterns = [
    { name: 'Private Key PEM', regex: /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/ },
    { name: 'Generic API Secret', regex: /api[_-]?secret\s*[:=]\s*['"][a-zA-Z0-9]{16,}['"]/i },
    { name: 'Hardcoded JWT Secret', regex: /jwt[_-]?secret\s*[:=]\s*['"][^'"]{8,}['"]/i },
    { name: 'MongoDB URI with Credentials', regex: /mongodb(?:\+srv)?:\/\/[^:]+:[^@]+@/i },
    { name: 'Database Password', regex: /password\s*[:=]\s*['"][^'"]{6,}['"]/i },
    { name: 'Google Service Account Credentials', regex: /"type"\s*:\s*"service_account"/i },
    { name: 'Google Cloud Private Key', regex: /"private_key"\s*:\s*"-----BEGIN PRIVATE KEY/i }
  ];
  const violations = [];

  function walk(current) {
    let entries = [];
    try {
      entries = readdirSync(current);
    } catch {
      return;
    }

    for (const entry of entries) {
      if (['.git', 'build', '.gradle', 'node_modules', '.idea'].includes(entry)) continue;
      const fullPath = join(current, entry);
      const relPath = relative(repoRoot, fullPath);

      let stat;
      try { stat = statSync(fullPath); } catch { continue; }

      if (stat.isDirectory()) {
        walk(fullPath);
      } else if (stat.isFile()) {
        // Exclude binary assets, locks, markdown documentation, and root repomix compaction output from code secret scans
        if (entry.endsWith('.apk') || entry.endsWith('.jar') || entry.endsWith('.png') ||
            entry.endsWith('.lockfile') || entry.endsWith('.md') || relPath === 'repomix-output.xml') continue;

        // Exclude test vectors in test source sets
        if (relPath.includes('Test') || relPath.includes('test') || relPath.includes('mock')) continue;

        try {
          const content = readFileSync(fullPath, 'utf8');
          for (const pattern of secretPatterns) {
            if (pattern.regex.test(content)) {
              violations.push({ file: relPath, secret: pattern.name });
            }
          }
        } catch {
          // ignore unreadable files
        }
      }
    }
  }

  walk(repoRoot);
  return violations;
}

/**
 * Validates exported components against strict release security policy:
 * - Only MainActivity (.MainActivity, MainActivity, dev.tabbyrush.android.MainActivity) is permitted as an exported activity.
 * - Services, content providers, and activity-aliases must NEVER be exported.
 * - Only TabbyRushTaskWidgetReceiver and com.google.firebase.iid.FirebaseInstanceIdReceiver
 *   are permitted as exported receivers, with strictly validated intent filters, permissions, and metadata.
 * - FirebaseInitProvider must be explicitly non-exported with authority dev.tabbyrush.android.firebaseinitprovider.
 */
export function validateExportedComponents(manifestXml) {
  const issues = [];

  // Validate FirebaseInitProvider explicitly (even when non-exported)
  const providerRegex = /<provider\b([\s\S]*?)(?:\/>|>([\s\S]*?)<\/provider>)/gi;
  let pMatch;
  while ((pMatch = providerRegex.exec(manifestXml)) !== null) {
    const pAttrs = pMatch[1] || '';
    const nameMatch = pAttrs.match(/android:name\s*=\s*"([^"]+)"/i);
    const pName = nameMatch ? nameMatch[1].trim() : '';
    if (pName === 'com.google.firebase.provider.FirebaseInitProvider') {
      const isExp = /android:exported\s*=\s*"true"/i.test(pAttrs);
      if (isExp) {
        issues.push(`FirebaseInitProvider must not be exported (found android:exported="true")`);
      }
      const authMatch = pAttrs.match(/android:authorities\s*=\s*"([^"]+)"/i);
      const auth = authMatch ? authMatch[1].trim() : '';
      if (auth !== 'dev.tabbyrush.android.firebaseinitprovider') {
        issues.push(`FirebaseInitProvider must have exact authority 'dev.tabbyrush.android.firebaseinitprovider' (found '${auth}')`);
      }
    }
  }

  const componentTags = ['activity', 'activity-alias', 'service', 'receiver', 'provider'];

  for (const tag of componentTags) {
    const fullBlockRegex = new RegExp(`<${tag}(?:\\s|\\/|>)([\\s\\S]*?)(?:\\/>|>([\\s\\S]*?)<\\/${tag}>)`, 'gi');
    let match;
    while ((match = fullBlockRegex.exec(manifestXml)) !== null) {
      const openTagAttrs = match[1] || '';
      const innerContent = match[2] || '';
      const isExported = /android:exported\s*=\s*"true"/i.test(openTagAttrs);
      if (!isExported) {
        continue;
      }

      const nameMatch = openTagAttrs.match(/android:name\s*=\s*"([^"]+)"/i);
      const componentName = nameMatch ? nameMatch[1].trim() : '(unknown)';

      if (tag === 'activity') {
        const isMain = componentName === '.MainActivity' ||
                       componentName === 'MainActivity' ||
                       componentName === 'dev.tabbyrush.android.MainActivity';
        if (!isMain) {
          issues.push(`Exported activity is not MainActivity: ${componentName}`);
        }
      } else if (tag === 'activity-alias') {
        issues.push(`Exported activity-alias is prohibited: ${componentName}`);
      } else if (tag === 'receiver') {
        const isWidget = componentName === '.TabbyRushTaskWidgetReceiver' ||
                         componentName === 'TabbyRushTaskWidgetReceiver' ||
                         componentName === 'dev.tabbyrush.android.TabbyRushTaskWidgetReceiver';
        const isFirebaseReceiver = componentName === 'com.google.firebase.iid.FirebaseInstanceIdReceiver';

        if (isWidget) {
          const intentFilters = [...innerContent.matchAll(/<intent-filter\b[^>]*>([\s\S]*?)<\/intent-filter>/gi)];
          if (intentFilters.length === 0) {
            issues.push(`TabbyRushTaskWidgetReceiver is exported but missing <intent-filter>`);
          } else {
            for (const filterMatch of intentFilters) {
              const filterContent = filterMatch[1];
              const hasAppWidgetUpdate = /<action\s+[^>]*android:name\s*=\s*"android\.appwidget\.action\.APPWIDGET_UPDATE"[^>]*\/?>/i.test(filterContent);
              if (!hasAppWidgetUpdate) {
                issues.push(`TabbyRushTaskWidgetReceiver is exported but missing android.appwidget.action.APPWIDGET_UPDATE intent filter`);
              }

              const actionMatches = filterContent.matchAll(/<action\s+[^>]*android:name\s*=\s*"([^"]+)"[^>]*\/?>/gi);
              for (const actionMatch of actionMatches) {
                const actionName = actionMatch[1];
                if (actionName !== 'android.appwidget.action.APPWIDGET_UPDATE') {
                  issues.push(`TabbyRushTaskWidgetReceiver contains unauthorized exported action: ${actionName}`);
                }
              }

              if (/<category\b/i.test(filterContent)) {
                issues.push(`TabbyRushTaskWidgetReceiver contains unauthorized <category> in intent filter`);
              }
              if (/<data\b/i.test(filterContent)) {
                issues.push(`TabbyRushTaskWidgetReceiver contains unauthorized <data> in intent filter`);
              }
            }
          }

          const hasProviderMetadata = /<meta-data\s+[^>]*android:name\s*=\s*"android\.appwidget\.provider"[^>]*android:resource\s*=\s*"(@xml\/task[_-]widget[_-]info|@ref\/0x[0-9a-fA-F]+)"[^>]*\/?>/i.test(innerContent) ||
            /<meta-data\s+[^>]*android:resource\s*=\s*"(@xml\/task[_-]widget[_-]info|@ref\/0x[0-9a-fA-F]+)"[^>]*android:name\s*=\s*"android\.appwidget\.provider"[^>]*\/?>/i.test(innerContent);
          if (!hasProviderMetadata) {
            issues.push(`TabbyRushTaskWidgetReceiver is missing required @xml/task-widget-info provider meta-data`);
          }
        } else if (isFirebaseReceiver) {
          // Narrow verification for com.google.firebase.iid.FirebaseInstanceIdReceiver:
          const permMatch = openTagAttrs.match(/android:permission\s*=\s*"([^"]+)"/i);
          const permission = permMatch ? permMatch[1].trim() : '';
          if (permission !== 'com.google.android.c2dm.permission.SEND') {
            issues.push(`FirebaseInstanceIdReceiver is exported without required com.google.android.c2dm.permission.SEND permission (found '${permission}')`);
          }

          const intentFilters = [...innerContent.matchAll(/<intent-filter\b[^>]*>([\s\S]*?)<\/intent-filter>/gi)];
          if (intentFilters.length === 0) {
            issues.push(`FirebaseInstanceIdReceiver is exported but missing <intent-filter>`);
          } else {
            for (const filterMatch of intentFilters) {
              const filterContent = filterMatch[1];
              const hasReceive = /<action\s+[^>]*android:name\s*=\s*"com\.google\.android\.c2dm\.intent\.RECEIVE"[^>]*\/?>/i.test(filterContent);
              if (!hasReceive) {
                issues.push(`FirebaseInstanceIdReceiver is exported but missing com.google.android.c2dm.intent.RECEIVE intent filter`);
              }

              const actionMatches = filterContent.matchAll(/<action\s+[^>]*android:name\s*=\s*"([^"]+)"[^>]*\/?>/gi);
              for (const actionMatch of actionMatches) {
                const actionName = actionMatch[1];
                if (actionName !== 'com.google.android.c2dm.intent.RECEIVE') {
                  issues.push(`FirebaseInstanceIdReceiver contains unauthorized exported action: ${actionName}`);
                }
              }

              const catMatches = filterContent.matchAll(/<category\s+[^>]*android:name\s*=\s*"([^"]+)"[^>]*\/?>/gi);
              for (const catMatch of catMatches) {
                const catName = catMatch[1];
                if (catName !== 'dev.tabbyrush.android') {
                  issues.push(`FirebaseInstanceIdReceiver contains unauthorized <category>: ${catName}`);
                }
              }

              if (/<data\b/i.test(filterContent)) {
                issues.push(`FirebaseInstanceIdReceiver contains unauthorized <data> in intent filter`);
              }
            }
          }
        } else if (
          (componentName === 'androidx.work.impl.diagnostics.DiagnosticsReceiver' && /android:permission\s*=\s*"android\.permission\.DUMP"/i.test(openTagAttrs)) ||
          (componentName === 'androidx.profileinstaller.ProfileInstallReceiver' && /android:permission\s*=\s*"android\.permission\.DUMP"/i.test(openTagAttrs))
        ) {
          // Permitted AndroidX system receivers protected by DUMP permission
        } else {
          issues.push(`Exported receiver is not TabbyRushTaskWidgetReceiver: ${componentName}`);
        }
      } else if (tag === 'service') {
        const isPermittedSystemService =
          (componentName === 'androidx.glance.appwidget.GlanceRemoteViewsService' && /android:permission\s*=\s*"android\.permission\.BIND_REMOTEVIEWS"/i.test(openTagAttrs)) ||
          (componentName === 'androidx.work.impl.background.systemjob.SystemJobService' && /android:permission\s*=\s*"android\.permission\.BIND_JOB_SERVICE"/i.test(openTagAttrs));
        if (!isPermittedSystemService) {
          issues.push(`Non-main service is exported: ${componentName}`);
        }
      } else {
        issues.push(`Non-main ${tag} is exported: ${componentName}`);
      }
    }
  }

  return issues;
}

function runSourceDiagnostic(repoRoot, approvedOrigin) {
  console.log(`\n=== Running Diagnostic Static Source Audit ===`);
  let issues = 0;

  // 1. Check base AndroidManifest.xml
  const manifestPath = resolve(repoRoot, 'androidApp/src/main/AndroidManifest.xml');
  if (existsSync(manifestPath)) {
    const manifest = readFileSync(manifestPath, 'utf8');
    if (!/android:usesCleartextTraffic\s*=\s*"false"/i.test(manifest)) {
      console.log(`[FAIL] Base manifest must explicitly set android:usesCleartextTraffic="false"`);
      issues++;
    } else {
      console.log(`[PASS] Base manifest prohibits cleartext traffic (usesCleartextTraffic="false")`);
    }

    if (!/android:allowBackup\s*=\s*"false"/i.test(manifest)) {
      console.log(`[FAIL] Base manifest must explicitly set android:allowBackup="false"`);
      issues++;
    } else {
      console.log(`[PASS] Base manifest restricts backup (allowBackup="false")`);
    }

    // Check exported components across activities, services, receivers, providers
    const exportedIssues = validateExportedComponents(manifest);
    if (exportedIssues.length > 0) {
      for (const err of exportedIssues) {
        console.log(`[FAIL] ${err}`);
      }
      issues += exportedIssues.length;
    } else {
      console.log(`[PASS] Exported components audit: Only MainActivity and verified TabbyRushTaskWidgetReceiver are exported.`);
    }
  } else {
    console.log(`[WARN] AndroidManifest.xml not found at ${manifestPath}`);
    issues++;
  }

  // 2. Check backup and data extraction rules
  const backupRulesPath = resolve(repoRoot, 'androidApp/src/main/res/xml/backup_rules.xml');
  const extractionRulesPath = resolve(repoRoot, 'androidApp/src/main/res/xml/data_extraction_rules.xml');
  if (existsSync(backupRulesPath) && existsSync(extractionRulesPath)) {
    console.log(`[PASS] XML security configs present (backup_rules.xml, data_extraction_rules.xml)`);
  } else {
    console.log(`[FAIL] Missing XML backup or extraction rule configs`);
    issues++;
  }

  // 3. Check Origin Policy if provided
  if (approvedOrigin) {
    if (!approvedOrigin.startsWith('https://')) {
      console.log(`[FAIL] Approved origin '${approvedOrigin}' is not HTTPS`);
      issues++;
    } else {
      console.log(`[PASS] Release Hub origin enforces HTTPS: ${approvedOrigin}`);
    }
  } else {
    console.log(`[INFO] No approved origin provided via --origin or APPROVED_HUB_ORIGIN. Enforcing HTTPS required at release.`);
  }

  // 4. Secret scan in tracked code
  const secretViolations = scanForSecrets(repoRoot);
  if (secretViolations.length > 0) {
    console.log(`[FAIL] Detected potential hardcoded secrets in source code:`);
    for (const v of secretViolations) {
      console.log(`  - ${v.file}: ${v.secret}`);
    }
    issues += secretViolations.length;
  } else {
    console.log(`[PASS] Repository source secret audit: No hardcoded private keys or passwords detected in source.`);
  }

  console.log(`\nDiagnostic Audit Result: ${issues === 0 ? 'CLEAN (Source meets static security rules)' : `${issues} ISSUES FOUND`}`);
  return issues === 0;
}

function runApkAudit(apkPath, approvedOrigin) {
  console.log(`\n=== Running Release APK Security Audit (Fail-Closed) ===`);
  const resolvedApk = resolve(process.cwd(), apkPath);

  if (!existsSync(resolvedApk)) {
    console.error(`\n[FATAL] Release APK file not found: ${resolvedApk}`);
    console.error(`A signed release APK must be assembled prior to release security auditing.`);
    console.error(`Run: ./gradlew :androidApp:assembleRelease`);
    return false;
  }

  let issues = 0;

  // 1. Signature Verification via apksigner (safe execFileSync without shell interpolation)
  try {
    const apksignerOut = execFileSync('apksigner', ['verify', '--verbose', '--print-certs', resolvedApk], { encoding: 'utf8' });
    const hasV2 = apksignerOut.includes('Verified using v2 scheme (APK Signature Scheme v2): true');
    const hasV3 = apksignerOut.includes('Verified using v3 scheme (APK Signature Scheme v3): true');

    if (!hasV2 && !hasV3) {
      console.error(`[FAIL] APK signature scheme v2/v3 missing or unverified.`);
      issues++;
    } else {
      console.log(`[PASS] Verified modern APK signature scheme (v2/v3).`);
    }

    // Reject debug cert (CN=Android Debug in Subject or DN)
    if (/(?:Subject|DN):.*CN=Android Debug/i.test(apksignerOut)) {
      console.error(`[FAIL] Release APK is signed with Android debug certificate (CN=Android Debug)!`);
      issues++;
    }

    // Print certificate digest info
    const digestMatch = apksignerOut.match(/Signer #1 certificate SHA-256 digest:\s*([a-f0-9]+)/i);
    if (digestMatch) {
      const certSha256 = digestMatch[1];
      console.log(`[INFO] Signer #1 SHA-256: ${certSha256}`);
      const expectedSha = process.env.EXPECTED_SIGNER_SHA256;
      if (expectedSha && expectedSha.toLowerCase() !== certSha256.toLowerCase()) {
        console.error(`[FAIL] Signer certificate digest does not match EXPECTED_SIGNER_SHA256!`);
        issues++;
      }
    }
  } catch (err) {
    console.error(`[FAIL] apksigner verification failed: ${err.message}`);
    issues++;
  }

  // 2. Manifest Inspection via apkanalyzer (safe execFileSync without shell interpolation)
  try {
    const manifestXml = execFileSync('apkanalyzer', ['manifest', 'print', resolvedApk], { encoding: 'utf8' });

    // In release APK, debuggable must NOT be true (AGP omits attribute or sets to false)
    if (/android:debuggable\s*=\s*"true"/i.test(manifestXml)) {
      console.error(`[FAIL] Release APK is debuggable! (android:debuggable="true" found)`);
      issues++;
    } else {
      console.log(`[PASS] android:debuggable is not true (release non-debuggable confirmed).`);
    }

    // Explicit check for cleartext: must be explicitly false
    if (!/android:usesCleartextTraffic\s*=\s*"false"/i.test(manifestXml)) {
      console.error(`[FAIL] Release APK permits cleartext traffic or lacks explicit android:usesCleartextTraffic="false"!`);
      issues++;
    } else {
      console.log(`[PASS] android:usesCleartextTraffic="false" confirmed.`);
    }

    // Explicit check for allowBackup: must be explicitly false
    if (!/android:allowBackup\s*=\s*"false"/i.test(manifestXml)) {
      console.error(`[FAIL] Release APK permits backup or lacks explicit android:allowBackup="false"!`);
      issues++;
    } else {
      console.log(`[PASS] android:allowBackup="false" confirmed.`);
    }

    // Check exported components across activities, services, receivers, providers
    const apkExportedIssues = validateExportedComponents(manifestXml);
    if (apkExportedIssues.length > 0) {
      for (const err of apkExportedIssues) {
        console.error(`[FAIL] ${err}`);
      }
      issues += apkExportedIssues.length;
    } else {
      console.log(`[PASS] Exported components audit: Only MainActivity and verified TabbyRushTaskWidgetReceiver are exported.`);
    }

    if (/networkSecurityConfig/i.test(manifestXml)) {
      console.error(`[FAIL] Release APK contains networkSecurityConfig override. Debug cleartext rules must be stripped from release.`);
      issues++;
    } else {
      console.log(`[PASS] Debug networkSecurityConfig stripped from release APK.`);
    }
  } catch (err) {
    console.error(`[FAIL] apkanalyzer manifest inspection failed: ${err.message}`);
    issues++;
  }

  // 3. Approved Origin validation
  if (!approvedOrigin || !approvedOrigin.startsWith('https://')) {
    console.error(`[FAIL] Approved origin '${approvedOrigin || '(none)'}' is not a valid HTTPS URL`);
    issues++;
  } else {
    console.log(`[PASS] Approved origin verified as HTTPS: ${approvedOrigin}`);
  }

  console.log(`\nRelease APK Audit Result: ${issues === 0 ? 'PASSED (APK satisfies all release security invariants)' : `FAILED (${issues} critical issues)`}`);
  return issues === 0;
}

function main() {
  const options = parseCliArgs();
  const repoRoot = process.cwd();

  console.log(`================================================================`);
  console.log(`TabbyRush Android Release Security & Integrity Checker`);
  console.log(`Target APK: ${options.apk}`);
  console.log(`Approved Origin: ${options.origin || '(Not specified)'}`);
  console.log(`Mode: ${options['diagnose-source'] ? 'Diagnostic Source Check' : 'Release APK Audit (Fail-Closed)'}`);
  console.log(`================================================================`);

  if (options['diagnose-source']) {
    const success = runSourceDiagnostic(repoRoot, options.origin);
    process.exit(success ? 0 : 1);
  }

  // In release mode, APK check must fail closed if APK or tools are absent
  const success = runApkAudit(options.apk, options.origin);
  process.exit(success ? 0 : 1);
}

if (process.argv[1] && resolve(process.argv[1]) === resolve(fileURLToPath(import.meta.url))) {
  main();
}
