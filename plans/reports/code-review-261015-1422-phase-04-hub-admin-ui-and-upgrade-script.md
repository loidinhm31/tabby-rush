# Code Review Report: Phase 04 Hub Admin UI and API-Only Upgrade Script

**Date**: 2026-10-15  
**Reviewer**: Senior Software Engineer (`Phase04Reviewer`)  
**Scope**: Phase 04 changes across `glean-hub-app` and `doggo`  
**Target Plan**: `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-04-hub-admin-ui-and-upgrade-script.md`  

---

## 1. Executive Summary & Quality Score

**Quality Score: 8.5 / 10**

Phase 04 delivers the administrative operator tooling required to discover strict contracts, evaluate migration preconditions, run non-mutating dry-run preflights, execute in-place atomic upgrades from `strict-v1` to `strict-v2`, and safely recover or resume operations across both the Glean Hub Web Admin UI and the Doggo CLI runner.

### Key Strengths
- **Strict Guarding Against Schema PUT**: Explicit defense-in-depth in both UI and backend forbids legacy arbitrary schema updates (`PUT /api/v1/apps/:appId/schema`) on strict apps.
- **Same-Migration-ID Invariant**: Both UI (`sessionStorage`) and CLI enforce re-use of the active migration ID upon network interruption or runner failure. No duplicate IDs are minted blindly.
- **Mandatory Final Read-Back Assertion**: Both surfaces query `GET /strict-contract` and verify `syncMode === 'strict-v2'`, canonical SHA-256 fingerprint parity, and all 16 columns prior to reporting completion.
- **Lean Tooling Architecture**: Vite preload recovery and Node native test runner (`node:test` via `test-register.mjs`) enable rapid unit test execution without bloated bundling dependencies.

### Prioritized Areas for Remediation
1. **Critical Data Invariant**: Fixed `reminderCount: 0` in `scripts/hub-live-smoke.mjs:607` to `reminderCount: 1` to comply with canonical `StrictV2TodoData::validate()` contract.
2. **Definitive Rejection vs Ambiguous Submission**: Separate definitive 4xx HTTP client rejections from transport interruptions in both CLI and UI. Currently, an HTTP 400/403/409 causes the CLI to poll a nonexistent migration ID for 60 seconds, and causes the UI to overwrite the server's descriptive rejection with a 404 polling error.
3. **Secret Redaction Completeness**: Extend `sanitizeLog` to directly scrub the active token string in addition to `Bearer <token>` pattern matching.

---

## 2. Critical Issues (MUST FIX)

### 1. `reminderCount: 0` in `scripts/hub-live-smoke.mjs` violates Strict-v2 Schema Contract [FIXED]
- **File**: `scripts/hub-live-smoke.mjs:607`
- **Severity**: **Critical**
- **Impact**: In `scripts/hub-live-smoke.mjs`, the strict-v2 task payload generator initialized `reminderCount: 0` when `deadlineDate` is null. The canonical Rust backend validator `StrictV2TodoData::validate()` strictly mandates:
  1. `!(1..=10).contains(&self.reminder_count)` -> `reminderCount must be 1..=10`.
  2. When `deadlineDate` is null, `self.reminder_count == 1`.
  Any live run against an admitted `strict-v2` instance would be rejected by the backend during `Push create operation` with `InvalidOperation`.
- **Status**: Remediation applied directly (`reminderCount: 1`). Offline fixture check re-verified.

---

## 3. Warnings (SHOULD FIX)

### 1. CLI Polling Loop Entered on HTTP 4xx Rejections
- **File**: `scripts/hub-strict-upgrade.mjs:338-364`
- **Severity**: **Warning / High Priority**
- **Problem**: `ApiClient.request('POST', ...)` returns an object `{ statusCode, body, rawText }` and only throws on transport/socket errors. When `POST /strict-upgrades` returns HTTP 400 (`InvalidRequest`), HTTP 403 (`Forbidden`), or HTTP 409 (`Conflict`), `startRes.statusCode === 200` is false. Execution enters the `else` block and begins polling `GET /strict-upgrades/:migrationId` every 1.5 seconds for up to 60 seconds. Because the server rejected the request before inserting a record, every poll returns 404, misleading the operator with `Migration ID '...' not yet found on server; waiting...` until an uninformative timeout (exit code 2).
- **Remediation**:
  ```js
  if (startRes) {
    if (startRes.statusCode === 200 && startRes.body?.state === 'ready-v2') {
      console.log(`[Upgrade] Migration ID '${migrationId}' reported instant transition to ready-v2.`);
    } else if (startRes.statusCode >= 400 && startRes.statusCode < 500) {
      console.error(`[Upgrade] ERROR (${startRes.statusCode}): Server rejected upgrade: ${sanitizeLog(startRes.rawText)}`);
      process.exit(1);
    } else if (startRes.statusCode === 200) {
      console.log(`[Upgrade] Migration initialized (${startRes.body?.state || 'unknown'}); polling for completion...`);
    } else {
      console.warn(`[Upgrade] Unexpected POST status (${startRes.statusCode}); attempting recovery...`);
    }
  }
  ```

### 2. UI Error Overwrite on Rejected Migration Start
- **File**: `glean-hub-app/src/components/organisms/strict-app-upgrade-panel.tsx:177-180`
- **Severity**: **Warning / High Priority**
- **Problem**: In `handleStartUpgrade`, if `startStrictUpgrade` throws due to an explicit server HTTP error (e.g. 403 unauthenticated, 400 invalid schema), the `catch` block sets `setActionError` to the server's descriptive message, but immediately invokes `pollMigration(newId)`. `pollMigration` immediately queries `GET /strict-upgrades/:newId`, which 404s and throws `"Failed to get strict upgrade status"`. This overwrites `actionError` in component state on the next tick, masking the actual root cause.
- **Remediation**: Check whether the caught error was a network transport error vs an explicit server rejection. Only call `pollMigration` if the error was an ambiguous network interruption.

### 3. Log Redaction Incomplete for Bare Tokens
- **File**: `scripts/hub-strict-upgrade.mjs:131-134`
- **Severity**: **Warning / Medium Priority**
- **Problem**: `sanitizeLog(str)` uses regex `str.replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer [REDACTED]')`. If an error response from an upstream proxy or server echoes the raw token string without the `Bearer ` prefix (e.g., `{"error": "Invalid token eyJhbGci..."}`), the token remains unredacted in output logs.
- **Remediation**:
  ```js
  function sanitizeLog(str, token) {
    if (typeof str !== 'string') return str;
    let s = str.replace(/Bearer\s+[A-Za-z0-9._-]+/gi, 'Bearer [REDACTED]');
    if (token && typeof token === 'string' && token.length > 8) {
      s = s.replaceAll(token, '[REDACTED_TOKEN]');
    }
    return s;
  }
  ```

---

## 4. Suggestions (NICE TO HAVE)

### 1. Bounded Polling and Cancel Control in UI
- **File**: `glean-hub-app/src/components/organisms/strict-app-upgrade-panel.tsx`
- **Suggestion**: The browser component currently polls every 1500ms without an upper time or count ceiling until unmounted. Adding an iteration bound (e.g., 200 attempts / 5 minutes) or an operator "Stop Polling" control prevents endless network polling if a backend worker deadlocks.

### 2. Transient Network Error Resilience in UI Polling
- **File**: `glean-hub-app/src/components/organisms/strict-app-upgrade-panel.tsx:102-106`
- **Suggestion**: If a single status poll throws a network error, `pollMigration` sets `actionError` and stops scheduling retries. Allowing 2-3 transient retry attempts before halting would align UI behavior with CLI resiliency.

### 3. URL Encoding Consistency for Legacy Endpoints
- **File**: `glean-hub-app/src/services/sync-admin-service.ts:245, 260`
- **Suggestion**: While all Phase 04 strict upgrade endpoints use `encodeURIComponent(appId)`, legacy methods `grantAccess` and `revokeAccess` interpolate `${appId}` directly. Wrapping them with `encodeURIComponent` ensures uniform URL safety across all admin service methods.

---

## 5. Reviewed Files

| Repository | File Path | Status | LOC |
|---|---|:---:|:---:|
| `glean-hub` | `glean-hub-app/src/services/sync-admin-service.ts` | Reviewed | 306 |
| `glean-hub` | `glean-hub-app/src/services/sync-admin-service.test.ts` | Reviewed | 249 |
| `glean-hub` | `glean-hub-app/src/pages/admin/edit-app.tsx` | Reviewed | 143 |
| `glean-hub` | `glean-hub-app/src/components/organisms/strict-app-upgrade-panel.tsx` | Reviewed | 257 |
| `glean-hub` | `glean-hub-app/src/components/organisms/strict-contract-card.tsx` | Reviewed | 75 |
| `glean-hub` | `glean-hub-app/src/components/organisms/strict-upgrade-controls.tsx` | Reviewed | 131 |
| `glean-hub` | `glean-hub-app/scripts/test-loader.mjs` | Reviewed | 33 |
| `glean-hub` | `glean-hub-app/scripts/test-register.mjs` | Reviewed | 2 |
| `glean-hub` | `glean-hub-app/package.json` | Reviewed | 114 |
| `glean-hub` | `docs/doggo-strict-sync.md` | Reviewed | 140 |
| `doggo` | `scripts/hub-strict-upgrade.mjs` | Reviewed | 445 |
| `doggo` | `scripts/hub-strict-upgrade.test.mjs` | Reviewed | 295 |
| `doggo` | `scripts/hub-schema-provision.mjs` | Reviewed | 201 |
| `doggo` | `scripts/hub-compatibility-smoke.mjs` | Reviewed | 225 |
| `doggo` | `scripts/hub-live-smoke.mjs` | Reviewed & Patched | 804 |
| `doggo` | `docs/hub-schema-integration.md` | Reviewed | 175 |

---

## 6. Validation Commands and Results

All checks executed locally on Linux x86_64:

```bash
# 1. Glean Hub App Unit Tests (Node native runner)
pnpm --prefix ../glean-hub/glean-hub-app test:unit
# Output: 13 passed, 0 failed, 2 suites (120ms)

# 2. Glean Hub App TypeScript & Vite Production Build
pnpm --prefix ../glean-hub/glean-hub-app build
# Output: tsc + vite build successful (10,411 modules, 22.02s)

# 3. Doggo Strict Upgrade CLI Test Suite (Mock server)
node scripts/hub-strict-upgrade.test.mjs
# Output: 7 passed, 0 failed, 1 suite (10.75s)

# 4. Doggo Hub Live Smoke Protocol Fixture
node scripts/hub-live-smoke.mjs --fixture-test
# Output: 18/18 checks passed (strict-v1 protocol fixtures)

# 5. Doggo Strict-v2 Vector Runner
node scripts/strict-v2-vector-runner.mjs
# Output: 23/23 checks passed (2 canonical fingerprints, 10 valid vectors, 11 rejections)

# 6. Doggo Diagnostic Release Security Check
node scripts/release-security-check.mjs --diagnose-source
# Output: 6/6 static checks passed (CLEAN)

# 7. Doggo Shared Kotlin Multiplatform JVM Tests
JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks
# Output: 15 actionable tasks executed, BUILD SUCCESSFUL (5s)

# 8. Glean Hub Sync Rust Backend Unit & Admission Tests
cargo test --manifest-path ../glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked
# Output: 54 passed, 1 ignored (live mongo gate), 0 failed (30.43s)
```

### Verification Boundaries & Limitations
- **Offline / Mock Verification Only**: All passing CLI and smoke checks were executed against in-process mock HTTP servers or offline protocol fixtures.
- **Live MongoDB Replica-Set Acceptance**: Live multi-instance cluster migration, exclusive fence draining under write concurrency, and live cross-entrypoint resume between browser and CLI remain unexercised pending the staging testbed (Gate G3 hold).
- **Source vs APK Security**: The security audit verified static XML configs and source manifest rules; binary APK verification remains part of the release pipeline.

---

## 7. Plan Tracking & Task Status

Updated `plans/261015-0752-task-deadlines-reminders-strict-upgrades/phase-04-hub-admin-ui-and-upgrade-script.md`:
- [x] Read-only version/schema UI and controlled action.
- [x] API-only script and exact readback/error semantics.
- [x] Version-aware provisioning/compatibility tools.
- [ ] Browser/CLI live success, response loss and cross-entrypoint resume evidence *(mock/fixture verified; live multi-node staging acceptance pending)*.

---

## 8. Unresolved Questions

1. **Definitive 4xx Handling in CLI**: Should `hub-strict-upgrade.mjs` immediately exit 1 upon receiving HTTP 400/403/409 responses from `POST /strict-upgrades` to prevent the 60-second polling timeout?
2. **UI Error Preservation**: Should `StrictAppUpgradePanel` distinguish network drop exceptions from HTTP error responses to avoid overriding descriptive 4xx messages with 404 polling errors?
3. **Strict-v2 Fixture Smoke Coverage**: Should an offline fixture test mode specifically for `strict-v2` wire transactions be added to `hub-live-smoke.mjs` so v2 payload paths are exercised offline?
4. **Staging Environment Schedule**: When will the multi-node MongoDB replica-set testbed be provisioned to unblock Gate G3 and live cross-entrypoint upgrade validation?
