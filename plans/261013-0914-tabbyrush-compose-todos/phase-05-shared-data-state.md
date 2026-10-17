# Phase 05 — Shared client data and session state

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Planning brief](reports/planning-brief.md)

## Parallelization Info
After02; group C with03/04/06. Depends only frozen contracts for coding; live Worker/Android integration07. Cannot edit contract/catalog/shared build.

## Overview
- Date: 2026-10-13
- Description: Shared client data and session state.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 9h

## Key Insights
No offline queue; last-loaded memory data can be stale. UUID only protects safe collision handling, not arbitrary idempotent retry. UI must receive complete state/actions independently of data internals.

## Requirements
Ktor repository, exact wire/error mapping, validated endpoint/token process lifecycle, all controller/actions/reconciliation/conflict behavior in contracts. No persisted secrets and no automatic mutation replay.

## Architecture
Common wire/repository/controller/reducer/reconciliation; Android OkHttp, JVM CIO client providers. Both platform adapters export factory + java.util.UUID v4 provider. Controller child coroutine Job, main-confined state, cancellation/generation guards; one mutation at a time.

## Related code files
Future create only; no existing source modified or deleted:
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/wire-models.kt` — create.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/ktor-todo-repository.kt` — create.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/session-controller.kt` — create.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/todo-reducer.kt` — create.
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/mutation-reconciliation.kt` — create.
- `shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/platform-client.kt` — create.
- `shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/platform-client.kt` — create.
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/repository-test.kt` — create.
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/state/session-controller-test.kt` — create.
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/state/reconciliation-test.kt` — create.
- `docs/client-data-state.md` — create.

## File Ownership
Only exact paths listed above and phase05 rows in registry; exclude every other file. Machine-local/generated artifacts ignored, not tracked. Reopen original owner for out-of-scope changes.

## Implementation Steps
1. Implement all camelCase wire models using serialization, exhaustive route/error/status mapping and protocol failures for invalid server shapes. Preserve original status/error code without trusting message for branching. DELETE204 no body parsing, direct DELETE404 converges absent state. Do not normalize unknown malformed data silently.
2. Export exact platform factory/newTodoId and common createTodoController symbols. Ktor redirects/logging disabled;10s connect/20s request timeouts; validated canonical origin before token use, default TLS checks. OkHttp retryOnConnectionFailure(false), followRedirects(false), followSslRedirects(false); neither engine installs Ktor HttpRequestRetry or mutation replay. Audit CIO behavior against pinned version and fault-test actual dispatch counts. Auth only same origin; close client on disconnect. Missing dependencies return02 owner.
3. Connect validates endpoint/build mode and64 lowercase hex token; masked session UI itself handles transient token input but clears after dispatch. Repository keeps secret only process memory with redacted representation. Child Job owned controller; controller close never cancels caller scope. Android retained ViewModel07 and desktop lifecycle09. After disconnect cancel tasks/increment generation/clear token+data+drafts; every response verifies generation. Auth401 moves unauthorized and clears active repository/token while retaining in-memory draft for re-entry; explicit disconnect clears draft too.
4. Implement Refresh/filter/read generations, LoadMore dedup latest-version, global counts, no snapshot claims. Failed read marks loaded rows stale; retry explicit. Successful mutation reloads first page but never silently loses unrelated editor draft. Serialized mutations prevent tap-spam; cancelled read does not update newer filter/session.
5. Implement retained create UUID/payload; preserve raw title on validation/failure. All ambiguous transport mutation outcomes expose pending uncertain+ReconcilePending. GET reconciliation comparisons exactly contracts; POST404 resubmit only explicit same UUID/payload; differing record conflict, never overwrite/new ID automatically.
   Publish canResubmitCreate only after same-session authenticated lookup404, clear it before dispatch/on session or auth change, and reject ResubmitConfirmedCreate otherwise. Preserve original ID/payload; test that malformed action sequences cannot bypass this gate.
6. PATCH expectedVersion with title/completion intent retained; conflict refresh preserves draft, explicit ReapplyConflictDraft rebinds latest confirmed version; unknown unchanged version remains uncertain. Delete confirmation retained on conflict; unknown missing GET converges absent; existing row requires refreshed explicit confirmation. Missing edit never implicitly recreates. Disconnect while dirty warns via UI06 before final action.
7. Permanent unit tests with Ktor MockEngine as transport boundary (not runtime proof) assert exact JSON/HTTP/error/redirect setup behavior, Unicode/wire status, cancellation/late-generation/filter generation, draft/pending transitions. State tests use genuinely controlled delayed responses/version conflicts, not mock function returning copied expected state. Cover ambiguous create404 race/collision, divergent reconcile, patch conflicts, delete404, unauthorized and no blind retries by call counts/intents. Secret absence from serializable state/redacted descriptions tested behaviorally.
   Include transport-fault tests dropping the response after request receipt, proving one mutation dispatch and UNKNOWN_OUTCOME; real local proxy fault scenario at07 confirms engine configuration rather than mock-only evidence.
8. Document session lifetime, protocol, online/stale limitations and actionable retry/conflict UX. Main07 runs tests/live integration once after all lanes land; no install/build mid-lane.

## Todo list
- [ ] Complete required owned behavior and permanent tests/runbooks.
- [ ] Record designated gate evidence; resolve failures through original owners.
- [ ] Review frozen contract and exclusive ownership; no runtime placeholders.

## Success Criteria
UI-facing state/action/factories match contracts; all route/error/conflict/unknown outcome paths implemented; no persistent token/draft/sync queue; behavior tests ready and actual local integration passes07.

## Conflict Prevention
No edits outside listed rows, including catalog/build/lock/docs/tests. New paths require registry amendment first. Integration defects reopen owner, then rerun affected gate.

## Risk Assessment
Cancel may not stop already dispatched server mutation; retain uncertainty, never claim cancellation undoes commit. Mutable flow races avoided main confinement/generation. Kotlin strings cannot guarantee heap erase.

## Security Considerations
Debug origin exact aliases vs releaseHTTPS; no bearer forwarding/redirect/logging/persistence/rememberSaveable. Prompt re-entry per process, rotation shared-dataset tradeoff documented.

## Next steps
Provide exact APIs to07/09;06 remains independently UI-only; faults reopened05 then07 gate rerun.

## Unresolved questions
Online-first/session-only approval frozen02; no further product expansion.
