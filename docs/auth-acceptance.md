# Hub Authentication & Protected Session Acceptance Criteria

Date: 2026-10-20  
Status: Current auth-session acceptance criteria; Phase 04 evidence is historical and does not establish connected-device acceptance.
Phase reference: Historical Phase 04 Auth & Protected Session
Plan: `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-04-auth-session.md`

---

## 1. Scope & Verification Strategy

This document specifies authentication and protected-session acceptance criteria. It separates:
1. **Deterministic Unit/Mock Tests**: Validate DTO serialization, allowlisted routing, headers, single-flight refresh concurrency, generation fencing, and safe error classifications.
2. **Android Keystore Instrumented Acceptance**: Device or emulator runs must establish platform-backed key custody, encryption behavior, and corrupt/tampered-file handling. Source/unit tests alone are insufficient.
3. **Hub Integration Acceptance**: A disposable or explicitly authorized Hub target must establish registration, login, token refresh, access requests, and network recovery. No production target is authorized by this document.

---

## 2. Test Cases & Verification Matrix

### 2.1 Route Policy & Header Verification
- `AUTH_ACCEPT_01`: Public auth routes (`POST /api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/refresh`) omit Authorization, X-API-Key, and X-App-Id headers.
- `AUTH_ACCEPT_02`: Bearer routes (`GET /api/v1/auth/me`, `/api/v1/apps/access-requests*`) send `Authorization: Bearer <token>` and omit X-API-Key/X-App-Id.
- `AUTH_ACCEPT_03`: Standard sync routes (`/api/v1/sync/{app_id}/{push,pull,delta}`) send Bearer + X-API-Key + X-App-Id headers.
- `AUTH_ACCEPT_04`: Requests outside the current allowlist, including obsolete strict sync routes, fail closed with local `SafeError.ProtocolIncompatible`.
- `AUTH_ACCEPT_05`: Canonical origin validation rejects mismatched hosts/schemes and query/fragment parameters.
- `AUTH_ACCEPT_06`: Redirects are disabled (`followRedirects = false`).

### 2.2 HubAuthClient & Session Transitions
- `AUTH_ACCEPT_07`: Registration normalizes email (trim + lowercase), validates byte lengths, and preserves raw transient password.
- `AUTH_ACCEPT_08`: Login verifies `/me.userId` matches login response `userId`; returns `SafeError.Unauthorized("USER_ID_MISMATCH")` on disparity.
- `AUTH_ACCEPT_09`: Established account with `tabbyrush` in `/me.apps` transitions to `AuthState.AUTHENTICATED`.
- `AUTH_ACCEPT_10`: Account without `tabbyrush` in `/me.apps` transitions to `AuthState.ACCESS_PENDING` and submits or reconciles access request.
- `AUTH_ACCEPT_11`: Access request submission failure (e.g. 400 duplicate) initiates bounded own-list reconciliation without recursive looping.

### 2.3 Single-Flight Refresh & Generation Fencing
- `AUTH_ACCEPT_12`: Multiple concurrent requests trigger exactly ONE network refresh call; all waiters receive successor bundle.
- `AUTH_ACCEPT_13`: Refresh failure returns `SafeError.Unauthorized` and transitions session to `AuthState.REAUTH_REQUIRED`.
- `AUTH_ACCEPT_14`: Session quiescence or logout increments generation counter; in-flight refresh completions with stale generation are discarded without vault write.
- `AUTH_ACCEPT_15`: Successful refresh does not automatically replay mutating POST requests; the coordinator explicitly manages retry/reconciliation.
- `AUTH_ACCEPT_16`: Local logout deletes vault bundle and resets session to `AuthState.UNAUTHENTICATED`.

### 2.4 Android Keystore Vault
- `AUTH_ACCEPT_17`: Vault stores credentials under AES-256-GCM with fresh 12-byte random nonces.
- `AUTH_ACCEPT_18`: Ciphertext is bound to account key via AAD; attempting decryption under mismatched account key fails authentication tag check.
- `AUTH_ACCEPT_19`: Atomic file replacement guarantees file integrity across process interruptions.
- `AUTH_ACCEPT_20`: Corrupt headers or truncated files set `VaultStatus.CORRUPT` without wiping database.
- `AUTH_ACCEPT_21`: Key invalidation sets `VaultStatus.LOCKED` without destroying local data.

---

## 3. Acceptance Status

| Validation suite | Recorded location | Target | Phase provenance |
|---|---|---|---|
| Hub Auth Client Test | `dev.tabbyrush.shared.auth.HubAuthClientTest` | Common JVM / Mock | Historical Phase 04 |
| Single-Flight Refresh Test | `dev.tabbyrush.shared.auth.SingleFlightRefreshTest` | Common JVM / Mock | Historical Phase 04 |
| Hub Auth Session Test | `dev.tabbyrush.shared.auth.HubAuthSessionTest` | Common JVM / Mock | Historical Phase 04 |
| Android Keystore Test | `dev.tabbyrush.shared.auth.AndroidKeystoreVaultTest` | Android Instrumented | Historical Phase 07 |
| Hub compatibility smoke | Former `scripts/hub-compatibility-smoke.mjs` reference is obsolete; the script is absent | Node / Glean Hub | Historical Phase 07/08 reference; not a current check |
>
> Parent reports a separate standalone Windows runtime smoke through strict Gradle JavaExec using real HTTP auth against disposable loopback services. This does not establish interactive UI or connected-Android acceptance. Native acceptance is not passed; production migration is unauthorized.
