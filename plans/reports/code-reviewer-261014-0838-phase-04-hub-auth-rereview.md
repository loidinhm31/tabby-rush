# Code Review (cycle 2): Phase 04 Hub Auth & Protected Session

Date: 2026-10-14 | Prior: `code-reviewer-261014-0730-phase-04-hub-auth-review.md` (5/10)
Score: **6.5/10**. JVM scope acceptable for Phase 05 handoff with warnings W1-W4; Android scope (B4) NOT fixed.

## B1-B5 verdict

| # | Verdict | Evidence |
|---|---|---|
| B1 token leak in errors | **Mostly fixed** | All `MALFORMED_*` catches in `hub-auth-client.kt` use constant messages; coordinator `REFRESH_EXCEPTION` constant; vault `e.message` no longer interpolated; DTOs (`Login/Register/Refresh` req/resp) override `toString` w/ `[REDACTED]`. Residual: `hub-http-client.kt:135` forwards transport `e.message` (HTML strip only, no token scrub); 400 body surfaced as `Validation.message` (L186). No regression test asserting no token/body snippet in any `SafeError`. |
| B2 logout/refresh race | **Fixed** | `refresh()` L255-295: generation re-check + `vault.store` inside `sessionMutex`; `logout()` bumps gen + clears under same mutex. Test `testLogoutDuringRefreshPreventsStaleVaultResurrection` passes but only exercises "logout before persist", not interleaving; acceptable. |
| B3 vault failure after refresh | **Partially fixed** | `refresh()` keeps successor in `inMemoryBundle`, returns `CREDENTIAL_PERSISTENCE_FAILED`, callback invoked outside mutex. BUT `authorizedRequest` (L476-489) and `checkAccess` (L322-335) treat any `refresh()` failure as REAUTH_REQUIRED -> persistence failure after successful rotation flips state to REAUTH_REQUIRED while memory holds a valid successor; inconsistent with `refresh()` (stays AUTHENTICATED). No persist-retry API; default callback is silent no-op. Test named `...RetainsSuccessorInMemory` never asserts retention (no 2nd refresh / token check). |
| B4 instrumented compile | **NOT fixed** (host can't verify; static reading) | (a) `assertFalse(nonce1.contentEquals(nonce2), "msg")` L83: JUnit4 signature is `assertFalse(String, boolean)` -> compile error. (b) `shared/build.gradle.kts` has no `androidInstrumentedTest` deps (`androidx.test:core`, `androidx.test.ext:junit`, `androidx.test:runner`, junit) -> `ApplicationProvider`/`AndroidJUnit4`/`org.junit.*` unresolved. (c) Runtime: `testAadBindingPreventsCrossAccountDecryption` expects `CORRUPT` but `load(B)` hits missing KeyStore alias -> `UNAVAILABLE` (load no longer creates keys) -> assertion fails; it also doesn't isolate AAD. (d) `testClearDeletesFileAndAlias` never checks alias removal; unused `assertNotEquals` import. Deferral to Phase 07 is plan-sanctioned, but QA "source-level fixed" claim is incorrect. |
| B5 state w/o generation fence | **Fixed** | Every `_state` write after an await (`register/login` failure, `completeLoginWithMe`, `refresh`, `checkAccess`, `handleMeAccessEvaluation`, `authorizedRequest`) now re-checks `currentGeneration == gen` under mutex. Generation only read/written under mutex (no `@Volatile` need). No unit tests cover stale `checkAccess`/`authorizedRequest` replies. |

## Critical issues
- **C1 (B4)** Android instrumented test does not compile + has a runtime-failing assertion + missing test deps in build file (see above). Blocks Phase 07, not Phase 05.

## Warnings
- **W1 (H1, unresolved)** `SingleFlightRefreshCoordinator` is global, not keyed by `(accountKey, generation)`. Caller after logout+login(other account) joining a stale in-flight (up to 20s timeout) receives old account's successor; `refresh()` then fences on the *caller's* gen (passes) and persists it under the new account's key.
- **W2 (H2, unresolved)** Caller reads `bundle.refreshToken` under mutex, then releases it before entering coordinator; if leader finishes in between, caller becomes new leader with consumed rotating token -> false 401 -> REAUTH_REQUIRED. Fix: leader re-reads bundle inside flight or compare-and-skip.
- **W3 (H3, unresolved)** Cancellation: `executeRaw` `catch (Exception)` and all `bodyAsText`/parse `catch (_: Exception)` swallow `CancellationException`. Coordinator `finally { mutex.withLock }` not `NonCancellable`; on contended cancelled leader `inFlightDeferred` stays set forever -> later refreshes join a cancelled deferred and throw CancellationException. Followers get leader's cancellation. No cancellation test.
- **W4 (H5, unresolved)** `logout()` swallows `vault.clear` failure and reports success; credentials may survive on disk and be restored by `restoreSession`. `AndroidKeystoreVault.clear` ignores `File.delete()` result, sets `READY` unconditionally.
- **W5** B3 inconsistency (authorizedRequest/checkAccess vs refresh) and no persistence-retry path / "background paused" signal.
- **W6** `restoreSession` does not catch `vault.load` exceptions (Android `load` throws ISE for CORRUPT/LOCKED/UNAVAILABLE), does not bump generation (in-flight login can overwrite).
- **W7** `requestAccess` failure path returns `Success(latest)` of own list on ANY POST failure (masks 400/429/network; old `rejected` returned as success). Not touched by B1-B5 but deviates from plan step 4.
- **W8** `authorizedRequest` returns `TOKEN_REFRESHED` unconditionally after refresh+/me; no second attempt / `AuthorizationUnresolved`. `AuthSession` interface still lacks `authorizedRequest/quiesce/clearLocalCredentials` (Phase 05 must depend on concrete class). Needs owner decision.
- **W9** Test gaps: no B1 redaction regression test; no session-level `refresh()` REAUTH test; no `checkAccess`/`requestAccess` tests; no stale-reply (B5) test; B3 test lacks retention assertion; `FakeCredentialVault` declared in session test, `TestHttpEngine` in client test (cross-file coupling).
- **W10** `jvm-http-engine.kt` sets no explicit no-redirect (relies on ktor `followRedirects=false` at client level; Java engine behavior not verified). `DUAL_AUTH_STRICT`/`BEARER_ONLY` with null token silently omit/partially send headers instead of failing locally.

## Suggestions
- Collapse six duplicated `when` blocks and three `Json{}` configs in client/http-client into one `executeJson<T>`; split 585-LOC `hub-auth-session.kt` (login/refresh/access) per modularization rule.
- Replace `e.message` in `executeRaw` with fixed text by exception class; map 400 body to code only (plan: never surface raw bodies).
- `AndroidKeystoreVault`: store `applicationContext`; remove unused `androidx.security.crypto` dep; consider Keystore-generated IV; drop "hardware-backed" claim in docs unless verified.
- AAD test: same key, tamper AAD/version, assert `CORRUPT`; assert alias absent after `clear` via `KeyStore`.
- `USER_ID_MISMATCH` message embeds both user ids; harmless but constant text is safer.

## Positives
Strict route classification/allowlist; no auto-retry plugin; OkHttp `retryOnConnectionFailure(false)`; POST never replayed; DTO/`CredentialBundle`/`HubConfig` redacted `toString`; mutex-guarded generation fence consistently applied; vault AES-GCM with AAD + magic + atomic move + per-vault mutex + IO dispatcher (H8 fixed); `load` no longer mints keys.

## Reviewed files
All 12 target files (~2,100 LOC): hub-auth-dtos, hub-http-client, hub-auth-client, hub-auth-session, single-flight-refresh, android-http-engine, android-keystore-vault, jvm-http-engine, 3 commonTest files (client test checked by grep only), android-keystore-vault-test. Cross-checked `contract/{account-key,credential-vault,...}`, `shared/build.gradle.kts`, `libs.versions.toml`, QA report `qa-261014-0833-...`.

## Validation
| Command | Result |
|---|---|
| `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL (5s). Per-suite counts not independently read (build dir blocked by hook); QA reports 43/43. Lenient mode bypasses checksum verification. |
| Android compile / instrumented tests | NOT run: `ANDROID_HOME` unset, no SDK. B4 findings by static reading of JUnit4 API + build file. |

## Unresolved questions
1. Phase 05 depends on concrete `HubAuthSession` or will `AuthSession` be extended (owner of frozen contract)?
2. Should persistence failure after successful rotation yield REAUTH_REQUIRED (callers) or stay AUTHENTICATED + paused-background (refresh())? Pick one.
3. Is Hub refresh-token rotation strictly single-use? (W2 severity.)
4. Is B4 compile fix required before Phase 04 sign-off, or strictly Phase 07 (QA says deferred)?
5. Does Ktor Java engine honor `followRedirects=false` for the JVM target (no test)?
