# QA Test Execution & Validation Report: Phase 04 Hub Admin UI and API-Only Upgrade Script

**Date**: 2026-10-15  
**Execution Context**: Linux Fedora 44 x86_64 | Node.js v24.16.0 | pnpm 10.28.2 | JDK 21 LTS (`/home/loidinh/.local/share/jvm/jdk-21`) | Gradle 8.11.1  
**Target Phase**: Phase 04 — Hub admin UI (`glean-hub-app`) and API-only upgrade script (`doggo`)  
**Repositories Tested**:
- `/home/loidinh/WS/glean-hub/glean-hub-app`
- `/home/loidinh/WS/doggo`
- `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync` (backend sanity check)

---

## 1. Test Results Overview

| Index | Command / Check | Working Directory | Scope / Description | Category | Executed Units | Pass | Fail | Exit Status | Duration |
|:---:|---|---|---|---|:---:|:---:|:---:|:---:|:---:|
| 1 | `pnpm test:unit` | `glean-hub-app` | Sync admin service upgrade contracts & preload recovery | Node Unit Tests | 13 tests | 13 | 0 | 0 | 120.31 ms |
| 2 | `pnpm build` | `glean-hub-app` | TypeScript type-check (`tsc`) + Vite production bundle | Build | 10,411 modules | Pass | 0 | 0 | 21.95s (9.87s vite) |
| 3 | `node scripts/hub-strict-upgrade.test.mjs` | `doggo` | CLI upgrade runner preconditions, recovery, polling, redaction (mock server) | Node CLI Tests | 7 tests | 7 | 0 | 0 | 10.80s (10.74s runner) |
| 4 | `node scripts/hub-live-smoke.mjs --fixture-test` | `doggo` | Offline fixture validation of strict-v1 sync protocol | Protocol Fixture Checks | 18 checks | 18 | 0 | 0 | Not captured |
| 5 | `node scripts/strict-v2-vector-runner.mjs` | `doggo` | Canonical fingerprints & strict-v2 wire vector suite | Wire Vector Checks | 23 checks (2 fp + 21 vectors) | 23 | 0 | 0 | Not captured |
| 6 | `node scripts/release-security-check.mjs --diagnose-source` | `doggo` | Static manifest, export, cleartext, secret rules | Static Audit Checks | 6 checks | 6 (0 findings) | 0 | 0 | Not captured |
| 7 | `JAVA_HOME=... ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks` | `doggo` | Shared Kotlin Multiplatform JVM test suites | Gradle Task Run | 15 tasks executed | Pass | 0 | 0 | 6.97s wall |
| * | `cargo test --locked` | `glean-hub-sync` | Rust Hub sync models, admission, and upgrade routes | Rust Backend Tests | 54 tests (+1 ignored) | 54 | 0 | 0 | 30.73s wall |

**Execution Result**: All requested validation commands completed successfully with zero reported failures. UI interaction behavior and live backend migration remain unverified.

---

## 2. Per-Command Detailed Breakdown

### 2.1 Glean Hub App Unit Tests (`pnpm test:unit`)
- **Working Dir**: `/home/loidinh/WS/glean-hub/glean-hub-app`
- **Runner**: Node.js test runner via `--experimental-strip-types --import ./scripts/test-register.mjs --test 'src/**/*.test.ts'`
- **Suites**: 2 suites, 13 tests total (13 passed, 0 failed, 120.31 ms)
- **Suite: `sync-admin-service strict upgrade contracts` (8/8 PASS)**:
  - `getAppSchema preserves syncMode and strictEpoch for strict apps`: PASS (0.94ms)
  - `getAppSchema encodes special characters in appId`: PASS (0.22ms)
  - `getStrictContract queries GET /api/v1/apps/:appId/strict-contract`: PASS (0.26ms)
  - `getStrictContract handles 403 Forbidden with descriptive error`: PASS (0.56ms)
  - `startStrictUpgrade sends valid payload to POST /strict-upgrades`: PASS (0.65ms)
  - `startStrictUpgrade handles dryRun correctly`: PASS (0.19ms)
  - `getStrictUpgradeStatus queries GET /strict-upgrades/:migrationId`: PASS (0.15ms)
  - `updateAppSchema fails when server rejects strict updates`: PASS (0.20ms)
- **Suite: `registerVitePreloadRecovery` (5/5 PASS)**:
  - `prevents the event and reloads only once for an asset/build pair`: PASS (0.92ms)
  - `keeps a consumed marker through load to bound late chunk failures`: PASS (0.16ms)
  - `uses the hashed entry URL as a build marker when meta is absent`: PASS (0.20ms)
  - `does not persist query-string tokens in the recovery key`: PASS (0.15ms)
  - `stops at the visible boundary when session storage is unavailable`: PASS (0.11ms)
- **Observed Diagnostics**:
  - `[auth] Missing API keys for: glean-hub (VITE_GLEAN_HUB_API_KEY)...`: logged during test environment initialization.
  - `[AuthService] Suppressed server error: {"error":"Admin token not scoped to app doggo"}`: logged during 403 test case.

### 2.2 Glean Hub App Production Build (`pnpm build`)
- **Working Dir**: `/home/loidinh/WS/glean-hub/glean-hub-app`
- **Command**: `tsc && vite build --mode production`
- **Result**: PASS (exit 0)
- **TypeScript Compilation**: `tsc` completed with zero diagnostic errors.
- **Vite Bundler**: Transformed 10,411 modules in 9.87s (21.95s total wall time).
- **Generated Chunks (Phase 04 Assets)**:
  - `dist/assets/edit-app-BIfMKf8h.js`: 13.45 kB (gzip: 4.33 kB)
  - `dist/assets/sync-admin-service-D6UWCxte.js`: 3.43 kB (gzip: 1.01 kB)
- **Observed Build Warnings**:
  - Runtime font URL reference warnings: `/fonts/jetbrains-mono/*.woff2` and `/fonts/ibm-plex-sans/*.woff2` were not resolved at build time and remain unchanged for runtime resolution.
  - Dynamic import notices for embedded sub-apps (`money-insight`, `cham-lang`).

### 2.3 Doggo Strict Upgrade CLI Test Suite (`node scripts/hub-strict-upgrade.test.mjs`)
- **Working Dir**: `/home/loidinh/WS/doggo`
- **Command**: `node scripts/hub-strict-upgrade.test.mjs`
- **Result**: PASS (exit 0, duration 10.74s runner / 10.80s wall time)
- **Suite: `hub-strict-upgrade.mjs CLI tests` (7/7 PASS)**:
  - `exits 0 with usage instructions on --help`: PASS (43.48ms)
  - `exits nonzero and errors when token is missing`: PASS (34.08ms)
  - `redacts sensitive bearer tokens in logs upon error`: PASS (38.85ms)
  - `executes preflight dry-run without mutating schema version`: PASS (52.67ms)
  - `executes full live upgrade with bounded polling and final read-back assertion`: PASS (257.01ms) *(uses local mock HTTP server; not a live network cluster deployment)*
  - `handles interrupted POST by recovering status using the same migration ID`: PASS (10259.18ms)
  - `handles --status-only query for an existing migration`: PASS (41.48ms)

### 2.4 Doggo Hub Live Smoke Fixture Test (`node scripts/hub-live-smoke.mjs --fixture-test`)
- **Working Dir**: `/home/loidinh/WS/doggo`
- **Scope**: Offline local HTTP fixture server self-test validating strict-v1 protocol invariants
- **Result**: PASS (exit 0, 18/18 checks passed, duration not captured)
  - **Suite 1: Health & App Admission (2/2 PASS)**
    - `GET /api/v1/health` healthy without redirect (HTTP 200)
    - `GET /api/v1/apps/doggo` echoes strict admission mode and schema (`syncMode=strict-v1`, `epoch=550e8400-e29b-41d4-a716-446655440000`)
  - **Suite 2: Security & Negative Route Isolation (3/3 PASS)**
    - Strict sync bootstrap rejects missing API key (HTTP 403)
    - Strict sync bootstrap rejects invalid API key (HTTP 403)
    - Legacy sync route rejects strict-v1 app with HTTP 426 (`protocolUnsupported`)
  - **Suite 3: Authentication & Access Session (4/4 PASS)**
    - User account registration (`POST /auth/register` HTTP 200)
    - User credential login (`POST /auth/login` accessToken scoped to `doggo`)
    - Token refresh exchange (`POST /auth/refresh`)
    - Identity and grants verification (`GET /auth/me`)
  - **Suite 4: Strict Strong Sync Operations (7/7 PASS)**
    - Bootstrap establishes authoritative protocol and epoch
    - Strict sync rejects invalid JWT token (HTTP 401)
    - Epoch mismatch rejected (`POST /push` with mismatched epoch returns HTTP 409 `epochMismatch`)
    - Push create: insert-only create (`expectedRevision: 0`) accepted at revision 1
    - Idempotent frozen retry: identical `opId` + digest returns `replayed` (confirmed revision 1)
    - Push conflict: base revision mismatch returns `revisionConflict` (current revision 1)
    - Pull operation: retrieves record `377d8d6a-8c2a-45f1-b75f-18a46f1bb5fb` at revision 1
  - **Suite 5: Tombstone Deletion & Invariants (2/2 PASS)**
    - Revision-CAS delete produces permanent empty tombstone at revision 2
    - Pull confirms tombstone preservation (`rowId` match, `deleted: true`, `data: {}`)

### 2.5 Doggo Strict-v2 Vector Runner (`node scripts/strict-v2-vector-runner.mjs`)
- **Working Dir**: `/home/loidinh/WS/doggo`
- **Result**: PASS (exit 0, duration not captured)
- **Fingerprint Parity (2/2 PASS)**:
  - Canonical strict-v1 SHA-256: `3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec`
  - Canonical strict-v2 SHA-256: `5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4`
- **Valid Vectors (10/10 PASS)**:
  1. `v2-create-date-only-minimal`: PASS
  2. `v2-create-timed-dst-overlap`: PASS
  3. `v2-create-date-only-reminder-repeat10`: PASS
  4. `v2-update-clear-deadline`: PASS
  5. `v2-delete-zero-data-bytes`: PASS
  6. `v2-migrated-null-defaults`: PASS
  7. `v2-disabled-rule-with-deadline`: PASS
  8. `v2-unicode-title-and-zone`: PASS
  9. `v2-max-i64-expected-revision`: PASS
  10. `v2-negative-epoch-instant`: PASS
- **Rejection Vectors (11/11 PASS)**:
  - Rejections verified for revision out of range, payload on delete, negative created timestamp, negative lead minutes, repetition count out of bounds, interval not null when count=1, interval < 15 min, deadline parameters present without deadlineDate, missing required fields when deadlineDate present, invalid reminder anchor on timed deadlines, last reminder after anchor.

### 2.6 Doggo Release Security Audit (`node scripts/release-security-check.mjs --diagnose-source`)
- **Working Dir**: `/home/loidinh/WS/doggo`
- **Mode**: Diagnostic Static Source Audit
- **Result**: CLEAN (0 findings / 0 violations under script's static rules, exit 0, duration not captured)
  - `usesCleartextTraffic="false"` verified in base Android manifest: PASS
  - `allowBackup="false"` verified in base Android manifest: PASS
  - Exported components audit: Only `MainActivity` exported: PASS
  - XML security configs present (`backup_rules.xml`, `data_extraction_rules.xml`): PASS
  - Release Hub origin enforces HTTPS (`https://api-glean-hub.qm-hub-v001.cloud`): PASS
  - Secret scanning audit: 0 hardcoded private keys or passwords detected in source: PASS

### 2.7 Doggo Shared JVM Multiplatform Test Suite (`./gradlew :shared:jvmTest`)
- **Working Dir**: `/home/loidinh/WS/doggo`
- **Command**: `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`
- **Result**: BUILD SUCCESSFUL in 6.97s wall time (15 actionable tasks: 15 executed)
- **Note on Test Counts**: Individual test and suite counts are not directly emitted to stdout by Gradle without custom test logging and direct access to generated report XML files was not available during this run. The task completed with exit status 0 and 15 executed tasks.

---

## 3. Scope Boundaries & Coverage Limitations

1. **Frontend UI Scope vs Service Unit Tests**:
   - `pnpm test:unit` in `glean-hub-app` tests `sync-admin-service.ts` contracts (mock HTTP layer, token injection, error handling, dry-run parsing, parameter encoding).
   - React components (`strict-app-upgrade-panel.tsx`, `strict-contract-card.tsx`, `strict-upgrade-controls.tsx`, `edit-app.tsx`) are validated for strict TypeScript type-checking and production bundling via `pnpm build`. Rendered UI component interaction tests (e.g. Playwright or React Testing Library) are not configured in `test:unit`.
2. **Offline Fixture Smoke vs Live Network**:
   - `hub-live-smoke.mjs --fixture-test` runs an in-process mock server verifying protocol contracts, CAS logic, error code mappings, and tombstone persistence. Live deployment against staged MongoDB requires running without `--fixture-test` against live cluster endpoints.
3. **Static Source Audit vs Binary APK Verification**:
   - `release-security-check.mjs --diagnose-source` audits source files, Android manifests, XML security rules, and origin configurations under that script's static rules. Full APK signature (v2/v3) verification requires building the unsigned/signed release APK artifact.
4. **Gradle Dependency Verification**:
   - Gradle runs with `--dependency-verification=off` due to missing checksum entries for Kotlin Multiplatform compiler plugins.

---

## 4. Failed / Flaky Tests & Critical Issues

- **Failed Tests**: 0
- **Flaky Tests**: None observed in the executed runs.
- **Critical Issues / Blockers**: None. All requested validation commands completed successfully with exit code 0.

---

## 5. Performance Metrics

- `glean-hub-app` unit tests: 120.31 ms execution time
- `glean-hub-app` production build: 9.87s Vite build (21.95s total wall time)
- `hub-strict-upgrade.test.mjs`: 10.74s runner duration (includes simulated 10s network interrupted retry cycle, 10.80s wall time)
- `:shared:jvmTest` (fresh execution with `--rerun-tasks`): 6.97s wall time (cached UP-TO-DATE run: 1.10s)
- Timings for fixture smoke, vector runner, and security diagnose were not captured.

---

## 6. Recommendations & Next Steps

1. **Phase 05 Preparedness**: Proceed with Phase 05 end-to-end integration or staged test deployment of the strict-v1 to strict-v2 live migration.
2. **Component Testing**: Consider adding React Testing Library or Playwright component tests for `StrictAppUpgradePanel` to verify user interaction events (confirmation modal typing, dry-run button state transitions).
3. **Gradle Verification Metadata**: Regenerate `gradle/verification-metadata.xml` to re-enable `--dependency-verification=strict` in CI.

---

## 7. Unresolved Questions

1. Should Playwright E2E tests for the Hub Admin UI (`edit-app.tsx` strict upgrade panel) be added to the CI pipeline once live backend staging is available?
2. When should `gradle/verification-metadata.xml` checksums be regenerated so `--dependency-verification=off` can be retired?
