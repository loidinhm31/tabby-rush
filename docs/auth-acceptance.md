# Hub Authentication & Protected Session Acceptance Criteria

Date: 2026-10-14  
Status: Active  
Phase: Phase 04 Auth & Protected Session  
Plan: `plans/261013-1540-doggo-glean-hub-reuse/phase-04-auth-session.md`  

---

## 1. Scope & Verification Strategy

This document establishes the acceptance criteria for Phase 04 Hub Authentication & Protected Session. Acceptance separates:
1. **Deterministic Unit/Mock Test Suite**: Executed against controlled transport mock engines, validating DTO serialization, allowlisted routing, headers, single-flight refresh concurrency, generation fencing, and safe error classifications.
2. **Android Keystore Instrumented Acceptance**: Executed on real Android devices or emulators during Phase 07, proving hardware-backed key generation, AES-256-GCM encryption with fresh nonces, AAD binding, and corrupt/tampered file handling.
3. **Live Server Integration Acceptance**: Executed against live Glean Hub instance during Phase 07/08, proving real login, register, token refresh rotation, access requests, admin approvals, and network recovery.

---

## 2. Test Cases & Verification Matrix

### 2.1 Route Policy & Header Verification
- `AUTH_ACCEPT_01`: Public routes (`/auth/register`, `/auth/login`, `/auth/refresh`) omit Authorization, X-API-Key, and X-App-Id headers.
- `AUTH_ACCEPT_02`: Bearer routes (`/auth/me`, `/apps/access-requests*`) send `Authorization: Bearer <token>`, omit X-API-Key/X-App-Id.
- `AUTH_ACCEPT_03`: Strict routes (`/sync/{appId}/strong/*`) send Bearer + X-API-Key + X-App-Id headers.
- `AUTH_ACCEPT_04`: Legacy sync routes (`/api/v1/sync/:app_id`) and non-allowlisted paths return local `SafeError.ProtocolIncompatible`.
- `AUTH_ACCEPT_05`: Strict origin validation rejects mismatched hosts, schemas, and query/fragment parameters.
- `AUTH_ACCEPT_06`: Redirects are strictly disabled (`followRedirects = false`).

### 2.2 HubAuthClient & Session Transitions
- `AUTH_ACCEPT_07`: Registration normalizes email (trim + lowercase), validates byte lengths, and preserves raw transient password.
- `AUTH_ACCEPT_08`: Login verifies `/me.userId` matches login response `userId`; returns `SafeError.Unauthorized("USER_ID_MISMATCH")` on disparity.
- `AUTH_ACCEPT_09`: Established account with `doggo` in `/me.apps` transitions to `AuthState.AUTHENTICATED`.
- `AUTH_ACCEPT_10`: Account without `doggo` in `/me.apps` transitions to `AuthState.ACCESS_PENDING` and submits or reconciles access request.
- `AUTH_ACCEPT_11`: Access request submission failure (e.g. 400 duplicate) initiates bounded own-list reconciliation without recursive looping.

### 2.3 Single-Flight Refresh & Generation Fencing
- `AUTH_ACCEPT_12`: Multiple concurrent requests trigger exactly ONE network refresh call; all waiters receive successor bundle.
- `AUTH_ACCEPT_13`: Refresh failure returns `SafeError.Unauthorized` and transitions session to `AuthState.REAUTH_REQUIRED`.
- `AUTH_ACCEPT_14`: Session quiescence or logout increments generation counter; in-flight refresh completions with stale generation are discarded without vault write.
- `AUTH_ACCEPT_15`: Successful refresh does NOT automatically replay strict POST requests; coordinator receives `TOKEN_REFRESHED` to manage idempotent frozen replay.
- `AUTH_ACCEPT_16`: Local logout deletes vault bundle and resets session to `AuthState.UNAUTHENTICATED`.

### 2.4 Android Keystore Vault
- `AUTH_ACCEPT_17`: Vault stores credentials under AES-256-GCM with fresh 12-byte random nonces.
- `AUTH_ACCEPT_18`: Ciphertext is bound to account key via AAD; attempting decryption under mismatched account key fails authentication tag check.
- `AUTH_ACCEPT_19`: Atomic file replacement guarantees file integrity across process interruptions.
- `AUTH_ACCEPT_20`: Corrupt headers or truncated files set `VaultStatus.CORRUPT` without wiping database.
- `AUTH_ACCEPT_21`: Key invalidation sets `VaultStatus.LOCKED` without destroying local data.

---

## 3. Acceptance Status

| Test Suite | Location | Target | Gate |
|---|---|---|---|
| Hub Auth Client Test | `dev.doggo.shared.auth.HubAuthClientTest` | Common JVM / Mock | Phase 04 |
| Single-Flight Refresh Test | `dev.doggo.shared.auth.SingleFlightRefreshTest` | Common JVM / Mock | Phase 04 |
| Hub Auth Session Test | `dev.doggo.shared.auth.HubAuthSessionTest` | Common JVM / Mock | Phase 04 |
| Android Keystore Test | `dev.doggo.shared.auth.AndroidKeystoreVaultTest` | Android Instrumented | Phase 07 |
| Live Hub Sync Integration | `scripts/hub-compatibility-smoke.mjs` | Node / Glean Hub | Phase 07/08 |
