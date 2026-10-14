# Code Review (cycle 3, final): Phase 04 Hub Auth & Protected Session

Date: 2026-10-14 | Prior: cycle1 5/10, cycle2 6.5/10 (`code-reviewer-261014-0838-phase-04-hub-auth-rereview.md`)
Score: **7/10**. JVM scope OK for Phase 05 handoff w/ warnings W1-W5 tracked. Android scope unverified (no SDK); C1 only partially closed -> Phase 07 gate blocker, not a Phase 04 JVM blocker.

## Cycle-2 critical (C1 / B4) status: PARTIALLY RESOLVED
| Item | Status | Evidence |
|---|---|---|
| (a) `assertFalse` arg order | FIXED | instrumented test L83 message-first |
| (c) AAD test missing-key `UNAVAILABLE` | FIXED (runtime semantics) | L69 stores B first so key exists -> `CORRUPT` |
| (c') AAD test really isolates AAD | NOT fixed | A-ciphertext under B's key fails on **key mismatch**, not AAD. Test passes even if AAD removed. |
| (b) `androidInstrumentedTest` deps | NOT fixed | `shared/build.gradle.kts` unchanged (git clean): no `androidx.test:core`, `androidx.test.ext:junit`, `androidx.test:runner`, `junit`. `ApplicationProvider`/`AndroidJUnit4`/`org.junit.*` unresolved -> source set cannot compile. Plan ownership forbids Phase 04 editing shared Gradle -> must be routed to Gradle owner (Phase 02/07); `dependencyLocking.lockAllConfigurations()` => lockfile regen needed too. |
| (d) `clear` alias assertion / unused `assertNotEquals` | NOT fixed | test never queries KeyStore for alias absence; unused import L13. |

Verdict: not a Phase 04 JVM blocker (androidTest not part of `build`/`jvmTest`; QA states Phase 07 execution), but acceptance `Actual Keystore suite passes 07` cannot pass until deps added.

## Other cycle-2 items re-verified
- B1 residual **fixed**: `hub-http-client.kt` executeRaw returns constant `NETWORK_ERROR`/"Network transport failure"; `parseResponseError` 400 now constant text; no body/token in `SafeError`. `CancellationException` rethrown in executeRaw (W3 partial).
- B2/B5 fixed (unchanged). Fences on every post-await state write.
- B3 now consistent: persistence failure no longer flips REAUTH in `checkAccess`/`authorizedRequest` (guard `error is Unauthorized`). Successor stays in `inMemoryBundle`; callback outside mutex.
- W10 JVM no-redirect: ktor `followRedirects=false` + JDK HttpClient default `Redirect.NEVER`; OkHttp explicit. Acceptable; no test.
- JVM validation: `:shared:jvmTest --rerun-tasks` BUILD SUCCESSFUL; result XML (fresh, 02:10 UTC): 43 tests / 0 fail / 0 skip (Client 8, Session 8, SingleFlight 3, contract 24).

## Critical issues
None new for JVM scope. (C1 residual = Phase 07 gate, see above.)

## Warnings (ordered by severity)
- **W1 High (security, unresolved)** `SingleFlightRefreshCoordinator` global, not keyed by (accountKey, generation). Caller for account B (gen 7) joining a hung account-A flight (<=20s timeout) receives A's successor; `refresh()` fence passes on caller's gen -> `inMemoryBundle`+vault under B key hold A's tokens: cross-account credential adoption, violates "wrong-account reauth cannot inherit queue". Low probability, high impact, cheap fix (key flights by `Pair(key, gen)` or re-validate result origin). Docs L29 claim "cannot overwrite newer credentials" is false for this path.
- **W2 Medium (unresolved)** Refresh token read under mutex but coordinator entered outside it: caller can become new leader with already-consumed rotating token -> 401 -> false REAUTH_REQUIRED. Leader should re-read bundle inside flight (or skip if bundle != token read).
- **W3 Medium (partial)** Coordinator: leader `finally { mutex.withLock }` not `NonCancellable`; contended+cancelled leader leaves `inFlightDeferred` set forever (cancelled deferred) -> every later refresh throws CancellationException. Followers inherit leader's cancellation; network call runs in first caller's scope, so cancelling one UI caller kills refresh for all (token possibly consumed, memory keeps old). `hub-auth-client.kt` all six `catch (_: Exception)` around `bodyAsText()`/decode + `parseResponseError` still swallow CancellationException -> cancelled refresh returns MALFORMED failure -> session sets REAUTH_REQUIRED. No cancellation test.
- **W4 Medium-High (unresolved)** `logout()` swallows `vault.clear` failure and reports success; `AndroidKeystoreVault.clear` ignores `File.delete()` result, swallows alias deletion error, sets `READY` unconditionally. Credentials may survive logout and be restored by `restoreSession`. Surface failure (keep REAUTH/ pending-wipe flag) or retry.
- **W5 Medium** Contract §3: 401 recovery = refresh then `/me`; only invalid identity -> REAUTH. `authorizedRequest` sets REAUTH_REQUIRED on **any** post-refresh `/me` failure (network/5xx/429) and returns `TOKEN_REFRESHED`/`REAUTH_REQUIRED` as Unauthorized; no `AuthorizationUnresolved`. `parseResponseError` maps **403 -> Unauthorized**, so 403 triggers refresh+/me; contract says "no generic 403 grant assumption".
- **W6 Medium** `restoreSession`: `vault.load` exceptions (Android throws ISE for CORRUPT/LOCKED/UNAVAILABLE) uncaught; no generation bump (in-flight login can overwrite restored state); not guarded vs existing session.
- **W7 Medium** `requestAccess`: on ANY POST failure returns `Success(latest)` if own list non-empty (masks 400/429/network, returns old `rejected` as success). Plan step 4 "then bounded /me diagnosis, refresh only if /me 401" not implemented.
- **W8 Medium** Docs drift: `authentication-and-credentials.md` L28 "waiters suspend until successor atomically committed" - false: coordinator releases waiters before persistence; each caller persists separately (N redundant vault writes; persistence failure not shared). AUTH_ACCEPT_13 says refresh failure returns Unauthorized; actually returns whatever error (Network) and still flips REAUTH. "hardware-backed" claim unverified (no `setIsStrongBoxBacked`/attestation check).
- **W9 Low-Med** `parseResponseError` reads unbounded `bodyAsText()` into `val sanitized` which is **never used** (dead computation, memory/DoS surface, unused-variable warning). Success bodies also unbounded (20s timeout only).
- **W10 Low-Med** Test gaps: no refresh-failure->REAUTH test; no `checkAccess`/`requestAccess` tests; no stale-reply (B5) test; no account-switch-during-flight test (W1); no cancellation test; no redaction (`toString`/SafeError) regression; B3 test never asserts retention (2nd refresh uses successor token / state stays AUTHENTICATED); `FakeCredentialVault`/`TestHttpEngine` cross-file coupling; unused `assertFalse` import in session test.
- **W11 Low** Android vault: `_status` not `@Volatile`, global rather than per-account (load of absent file resets READY over another account's LOCKED); `getSecretKey` exceptions (UnrecoverableKeyException) escape without status update; backup exclusion deferred to 07 (no manifest in repo; auto-backup of `filesDir/doggo_vault` w/o key -> permanent UNAVAILABLE on restore); `androidx.security.crypto` dep unused (deprecated).
- **W12 Low** Phase 05 consumes concrete `HubAuthSession` (`authorizedRequest`, `quiesce`, `clearLocalCredentials`, not on `AuthSession`). `onCredentialPersistenceFailed` receives raw `CredentialBundle` and its exceptions propagate unguarded. `USER_ID_MISMATCH` message embeds both ids. `RegisterRequest.toString` includes email (PII).

## Suggestions
- Replace six duplicated response blocks with one `executeJson<T>` that rethrows `CancellationException`; one shared `Json`; split 575-LOC session (login / refresh / access) per modularization rule.
- Run network leg of refresh in `NonCancellable`/coordinator-owned scope; clear `inFlightDeferred` in `withContext(NonCancellable)`.
- Drop or cap (`take(256)` on stream) body read in `parseResponseError`.
- AAD test: same key, flip AAD-bound input (e.g. rename file to other account with same key via test-only alias) or tamper version; assert alias absent after `clear` via `KeyStore`.
- Add `androidInstrumentedTest.dependencies` (androidx.test core/ext-junit/runner, junit) + regenerate lockfile via Gradle owner.
- Update docs L28/L29/AUTH_ACCEPT_13 to match behavior or fix behavior.

## Positives
Strict route classification/allowlist, no auto-retry plugin, POST never replayed (tested), OkHttp `retryOnConnectionFailure(false)`, redacted DTO/bundle `toString`, constant transport/HTTP error messages, mutex-guarded generation fence on every post-await write, AES-GCM+AAD+magic+atomic move+IO dispatcher vault, `load` never mints keys, clean JVM tests 43/43.

## Reviewed files
All 12 targets (~2,430 LOC) + `contract/{auth-session,credential-vault,account-key,safe-errors}`, `shared/build.gradle.kts`, `libs.versions.toml`, `docs/authentication-and-credentials.md`, `docs/auth-acceptance.md`, plan `261013-1540-doggo-glean-hub-reuse/{contracts §2-3, phase-04}`, QA report 0904. Client test checked by grep only.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL; XML: 43/0/0 (lenient bypasses checksum verification) |
| Android compile / instrumented | NOT run: `ANDROID_HOME` unset, no SDK. Findings by static reading. |

Plan file status NOT modified (acceptance gates 07/08 pending; owner Main decides).

## Unresolved questions
1. Who owns adding `androidInstrumentedTest` deps + lockfile (Phase 02 Gradle owner vs Phase 07)? Accept deferral to 07?
2. Fix W1/W2/W3 (coordinator keyed + NonCancellable) before Phase 05 or carry as tracked debt?
3. Should network/5xx on post-refresh `/me` and 403 keep forcing REAUTH (conservative) or follow contract §3 (Reauth only on invalid identity)?
4. Is Hub refresh rotation strictly single-use (sets W2 severity)?
5. Is `lenient` dependency verification acceptable beyond Phase 04 (metadata lacks transitive hashes)?
