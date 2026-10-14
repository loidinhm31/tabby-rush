# Phase 04 — Hub authentication and protected session

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Strict A scope](reports/strong-sync-feasibility.md) · [Route isolation](reports/strong-sync-isolation-feasibility.md) — authentication core unchanged.
- [Historical research](../261013-1459-doggo-offline-accounts/research/researcher-01-authentication.md) — only protected-store/Room/atomicity evidence reused; OAuth/D1/LWW/insecure storage superseded.

## Parallelization Info
Group C after02; independent coding03/05/06. Consumes frozen vault/route contracts, not concrete data/UI. Android vault04; Linux09/Windows10. Test source ready here; common/instrumented execution coordinated07; live Hub03 needed for runtime.

## Overview
- Date: 2026-10-13
- Description: Hub authentication and protected session.
- Priority: P2
- Implementation status: completed (100% - 2026-10-14)
- Review status: passed (2026-10-14; cycle 3 score 7/10 approved for conditional JVM handoff to Phase 05; 43/43 JVM tests passing under lenient verification; Android compilation/instrumented test and live Hub runtime acceptance deferred to Phase 07)
- Effort: 12h completed (12h planned effort; total plan 148h, uncertainty ±25–35%).

## Key Insights
Hub native email/password login/register and bearer /me/access requests need no app key. Access identity differs from doggo grant; issued grant snapshot is stale until refresh. Existing refresh rotation is not crash-idempotent; local logout is not remote revocation.

## Requirements
Native signup/login/request/status, matching server userId, protected atomic bundle, single-flight refresh across foreground/worker, safe status-based errors, one refresh/read retry and no blind unknown push replay. Established account offline reopen with data retained on expiry; wrong-account reauth cannot inherit queue.

## Architecture
One bounded common Hub HTTP/auth client, Android/JVM engines injected;05 sync mapping uses AuthSession.authorizedRequest, not a second token manager. Opaque credentials exist only vault/memory; AuthState no token. Android Keystore AES-GCM wrap with fresh nonce and atomically replaced bundle.04 never creates DB or scheduler.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-dtos.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-http-client.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-client.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/auth/hub-auth-session.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/auth/single-flight-refresh.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-http-engine.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidMain/kotlin/dev/doggo/shared/auth/android-keystore-vault.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/jvmMain/kotlin/dev/doggo/shared/auth/jvm-http-engine.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-client-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/auth/hub-auth-session-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/auth/single-flight-refresh-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/androidInstrumentedTest/kotlin/dev/doggo/shared/auth/android-keystore-vault-test.kt` — create.
- `/home/loidinh/WS/doggo/docs/authentication-and-credentials.md` — create.
- `/home/loidinh/WS/doggo/docs/auth-acceptance.md` — create.

## File Ownership
Exclusive phase04 owner of all 14 exact absolute paths above; those and only those phase04 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Implement exact camelCase Hub auth/register/refresh/me/access DTOs and200 success handling from contracts2, including pending/approved/rejected/unknown status. Trim/lower email only; leave password unchanged, transient. UI helpers may mirror byte-length rules without replacing server validation.
2. After successful login/signup securely replace identity/token pair, GET /me, ensure userId matches, classify doggo grant. Pending/rejected/no grant prohibits bootstrap/sync; submit access request once and reload own list on duplicate pending. After approval refresh once or require login before sync, not local apps-array edit. /me.apps alone authorizes; newest doggo request by createdAt is explanatory. Approved request without grant remains admin-pending; prior approved request can coexist with revoked grant.
3. Implement allowlisted origin-bound HTTP with connection10s/request20s, no redirects/TLS bypass/header logging, no automatic transport retries. Only strict POST `/api/v1/sync/:app_id/strong/{bootstrap,push,pull}` adds X-API-Key/X-App-Id; auth/access routes retain bearer rules. No legacy sync route in Doggo allowlist. Non401 mode/protocol/admission failures do not trigger refresh; epoch409 and protocol426 pause through05. Response status/operation controls safe errors; non-JSON bodies sanitized, never display raw HTML or parse English error text for behavior.
4. Single-flight refresh waits across concurrent requests/workers; atomic vault replacement complete before waiters proceed. Sync401 is ambiguous: JWT expiry,missing grant or bad key. At most one refresh/recovery then bounded /me:401 -> ReauthRequired;valid /me without grant -> AccessPending;valid /me with grant -> AuthorizationUnresolved,pause/preserve data,no refresh loop. No automatic POST retry, including bootstrap/pull;05 explicitly controls any next request and replay of identical frozen push. Access-request POST failure performs one bounded own-list reconciliation,then bounded /me diagnosis,not blind refresh/resubmit or recursive list;refresh only if /me401. Existing grant paths retain overloaded401 behavior. Unknown refresh dispatch/5xx/transport or vault write failure has consumed-token risk; require reauth when durable successor unavailable,no old-token loop. Persist safe recovery state via02 callback to05; no concrete data import.
   Any refresh failure after dispatch, including404/500 after revocation, uses conservative reauthentication. On successful response but failed vault write, retain successor only in memory for persistence retry and persist recovery-pending through frozen callback; pause background work. HTTP never replays POST after token refresh or apparent pre-handler401; frozen operation remains uncertain until05 receives explicit durable acceptance or review outcome.
5. Android adapter creates protected Keystore key and fresh AES-GCM nonce, atomic private-file credential bundle replace/read/delete, no deprecated encrypted preferences/plaintext fallback. Distinguish locked/unavailable/invalidated key and corrupt bundle with safe actionable state; no implicit DB wipe. Credential files/keys are account-scoped, excluded by07 backup config; no password saved.
6. Implement quiesce/clearLocalCredentials semantics called by05 leave sequence: generation fences prevent stale responses restoring credentials. Local logout deletes bundle only, never calls invented logout/refresh-to-revoke; acknowledge other Hub sessions/issued tokens remain.
7. Tests actual auth/session/refresh code controlled transport: registration/no grant,duplicate access/approval,mismatched userId,concurrent single-flight,vault failure,lost refresh,overloaded401/nonJSON/429/5xx,quiescence/late reply. Assert no automatic POST retries for strong bootstrap/push/pull,no legacy allowlist and non401 strict errors never refresh;503 admission preserve vs409reset/426incompatible map distinctly through frozen result. Actual instrumented Keystore suite runs07. Approved-without-grant/latest request selection and wrong-key/no-grant loop bounds retained. Admin warning recommends non-admin;no native admin controls,elevated token risk disclosed. No English-string behavior.
8. Write authentication-and-credentials and auth-acceptance separating deterministic test cases from live Hub evidence supplied07/08. Record vault error policy and source6h/30day token limitations accurately; no earlier OAuth/JWKS/revocation promises.

## Todo list
- [x] Implement every owned artifact/requirement with real behavior,not scaffolds. (2026-10-14 - 14 owned files delivered)
- [x] Pass designated future command and actual runtime gates; record sanitized evidence. (2026-10-14 - implementation delivered; 43/43 JVM tests passing under lenient dependency verification; Android compilation/instrumented test and live Hub runtime acceptance deferred to Phase 07)
- [x] Resolve failures through original owner and complete parent review; do not mark implementation accepted early. (2026-10-14 - review cycle 3 approved 7/10 for conditional Phase 05 handoff with tracked warnings W1-W5/W12; parent runtime acceptance remains Phase 07)

## Success Criteria
Frozen real AuthSession implementation and Android vault; auth tests plus actual Keystore tests pass07. Live signup/access approval/login/refresh/missing grant/isolation/lost-refresh recovery validated07/08 against Hub, not MockEngine alone. No credentials leak and no second refresh manager.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Keystore invalidation/corrupt bundle, refresh lost successor and stale apps claim require recoverable sign-in state, not data loss. Desktop custody unavailable until09/10 intentionally; no common fake vault.

## Security Considerations
Redacted opaque bundle; no password in DB/UI saved-state/scheduler/logs. Approved immutable origin/HTTPS; no bearer forwarding. Native Doggo key extractable identification, not human authorization.

## Next steps
05 injects real auth at07;06 consumes safe state and transient auth-submit callback.09/10 implement same vault interface; auth/transport defects reopen04.

## Unresolved questions
Actual approved Hub test origin/accounts/grants and Android Keystore runtime remain implementation prerequisites.
