# Proposed frozen contracts — freeze in phase 02

Source: [architecture](architecture.md), [authoritative decisions](reports/design-decisions.md). Proposed private, online-first scope requires approval. Freeze this package before parallel implementation; breaking change reopens all affected owners and gates. Paths in [ownership](file-ownership.md) are repo-relative; Kotlin package names below are authoritative.

## Product, Unicode and validation
CRUD create/list/rename/complete/reopen/delete, All/Active/Completed filters, global counts, refresh and Load more. Hard delete. No due dates, reminders, login provider, offline writes or public tenancy.

Title: strip leading/trailing **only** Unicode White_Space code points `0009–000D,0020,0085,00A0,1680,2000–200A,2028,2029,202F,205F,3000`. Reject malformed surrogate pairs and internal C0 `0000–001F`, DEL `007F` (after trim); allow other valid Unicode. Result 1–200 **Unicode scalar values**, not UTF-16 units, grapheme clusters or bytes; emoji combining sequences count multiple scalars. No normalization/case folding. JS spread/code-point iteration and Kotlin code-point iteration implement same vectors. SQL constraints support shape; Worker is authority for scalar/trim rule. UI displays count and normalized candidate without discarding raw draft on failure.

IDs canonical lowercase UUIDv4 regex `^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`. JSON numbers integers safe in JS/Kotlin: timestamps 0..9007199254740991 UTC milliseconds, versions 1..2147483647. At max version reject modification with 409 `VERSION_EXHAUSTED`, never wrap. Request JSON unknown fields rejected; null not accepted for optional values. JSON object only; boolean must be JSON boolean. POST/PATCH content-type `application/json` optionally UTF-8 charset, maximum body 4096 UTF-8 bytes (stream bounded even absent/false Content-Length); 413 above limit. GET/DELETE have no request body; reject nonempty body 400. Query duplicates/unknown keys rejected. No cache of authenticated responses (`Cache-Control: no-store`).

## JSON and HTTP
Base origin only (no URL path/query/fragment); API prefix `/api`. CamelCase JSON everywhere; SQL snake_case mapped explicitly.

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","title":"Buy milk","completed":false,"createdAt":1791840000000,"updatedAt":1791840000000,"version":1}
```
`Todo`: fields above, all required. Envelope error: `{"error":{"code":"VALIDATION_ERROR","message":"Title must contain 1–200 Unicode scalar values","field":"title"}}`; `field` omitted except validated `id|title|completed|expectedVersion|filter|limit|cursor|endpoint`. Message safe human text; clients branch on code only, never parse SQL/exception text.

| Method / route | Input | Success | Failures beyond common errors |
|---|---|---|---|
| GET `/api/todos` | `filter=all|active|completed` default all; `limit=1..100` default 50; optional cursor | 200 `{"items":[Todo],"nextCursor":null|string,"counts":{"all":N,"active":N,"completed":N}}` | 400 `VALIDATION_ERROR` |
| GET `/api/todos/{id}` | UUID path | 200 Todo | 404 `NOT_FOUND` |
| POST `/api/todos` | `{id,title}` | 201 Todo; `Location: /api/todos/{id}` | 409 `ID_EXISTS` (never overwrite) |
| PATCH `/api/todos/{id}` | `{expectedVersion,title?,completed?}`, at least one changed-field key | 200 Todo, version +1 even if values unchanged | 404 `NOT_FOUND`; 409 `VERSION_CONFLICT` or `VERSION_EXHAUSTED` |
| DELETE `/api/todos/{id}?expectedVersion=N` | positive int version | 204 empty | 404 `NOT_FOUND`; 409 `VERSION_CONFLICT` |

Common: missing/invalid bearer -> 401 `UNAUTHORIZED` with `WWW-Authenticate: Bearer`; missing/malformed configured server secret -> 503 `SERVICE_UNAVAILABLE`; request shape -> 400 `VALIDATION_ERROR`; content-type -> 415 `UNSUPPORTED_MEDIA_TYPE`; body >4096 -> 413 `PAYLOAD_TOO_LARGE`; method on known route ->405 `METHOD_NOT_ALLOWED` + Allow; unknown route ->404 `ROUTE_NOT_FOUND`; unexpected service/database ->500 `INTERNAL_ERROR`. All routes including unknown ones authenticate first. No unauthenticated health route or CORS configuration needed. Rate-limit provider 429 maps safe generic retry-later state; no automatic mutation replay. Errors contain no token, title or raw SQL.

## Pagination and totals
- Order `created_at DESC, id COLLATE BINARY DESC`; ID canonical lowercase makes Kotlin/JS lexical ordering equivalent to SQLite BINARY for IDs.
- Predicate after cursor `(created_at < ? OR (created_at = ? AND id < ? COLLATE BINARY))` plus current filter; fetch limit+1. Return limit records; nextCursor encodes last **returned** tuple only if an extra record exists, otherwise null. Empty page null. No offsets.
- Cursor unpadded base64url of UTF-8 JSON `{"v":1,"filter":"active","createdAt":1791840000000,"id":"550e8400-e29b-41d4-a716-446655440000"}` in that key order. Max encoded 256 ASCII bytes. Reject invalid alphabet/UTF-8/JSON, extra/missing keys, v !=1, invalid types/UUID/timestamp, or filter != current filter. Cursor is not a secret/auth capability; no HMAC needed.
- Counts all/active/completed nonnegative safe integers, all=active+completed, global whole-dataset counts independent of cursor/filter. Compute all counts in one aggregate statement; page and counts may reflect different instants. No snapshot guarantee across pages: completion/deletion changes membership, new rows preceding cursor appear after refresh.
- Controller deduplicates by ID, latest version wins; refresh/filter change clears pages/cursor and restarts. No background pagination/sync; Load more explicit. Mutations refresh first page after success; preserve independent unsaved editor draft. Counts/loading clearly not an offline truth.

## D1 schema / atomicity
```sql
CREATE TABLE todos (
  id TEXT PRIMARY KEY COLLATE BINARY NOT NULL,
  title TEXT NOT NULL CHECK(length(title) BETWEEN 1 AND 200),
  completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)),
  created_at INTEGER NOT NULL CHECK(created_at >= 0),
  updated_at INTEGER NOT NULL CHECK(updated_at >= created_at),
  version INTEGER NOT NULL DEFAULT 1 CHECK(version BETWEEN 1 AND 2147483647)
);
CREATE INDEX todos_created_id ON todos(created_at DESC,id DESC);
CREATE INDEX todos_completed_created_id ON todos(completed,created_at DESC,id DESC);
```
Worker supplies create time once (`Date.now`), `updated_at=max(now,previous updated_at)`; never rely on timestamp for concurrency. Prepared INSERT without conflict upsert. Atomic conditional UPDATE `WHERE id=? AND version=? AND version<2147483647 RETURNING ...`, updated_at computed in SQL against existing row; conditional DELETE `WHERE id=? AND version=? RETURNING id`. No returned row -> lookup to classify current missing/version conflict; race during classification can yield either 404 or409 without violating no-lost-write invariant. D1 binding `DB`, database logical name `doggo-db`, migration path `worker/migrations`, initial migration `0001-create-todos.sql`. Local and remote state distinct.

## Authentication, endpoint and lifecycle
- Provision 32 cryptographically random bytes as exactly 64 lowercase hex characters; secret `APP_SESSION_TOKEN`. Cloudflare API credentials are separate and never entered into app. Validate bearer header exact `Bearer ` +64 lowercase hex, secret same format, decode to32 bytes; compare via **documented Workers-supported constant-time primitive** (verify exact API against runtime typings, e.g. timingSafeEqual), not JS equality. Unsupported runtime primitive is a freeze blocker, never fake constant-time code.
- `BuildMode.DEBUG|RELEASE` passed by entrypoints, not user selectable. HTTPS endpoint: host present, no credentials/path (except `/`)/query/fragment; canonical origin, no redirects. Debug additionally allows HTTP only hosts `127.0.0.1`, `localhost`, `10.0.2.2` on port8787, no other HTTP; release disallows all HTTP. IPv6 HTTP intentionally out of scope. Client 10s connect /20s request timeout, redirects disabled and transport logging disabled. TLS certificate validation default; no bypass.
- Session config UI masked token; only memory. Never preferences, plaintext file/env app config, rememberSaveable, SavedStateHandle, clipboard auto-copy, crash/log payload, serialized state. Token is excluded from `TodoUiState` and every toString. Process death requires new entry. Endpoint may also remain memory for simplicity.
- Entry scope lifecycle: Android ViewModel retained through rotation, cleared at process/ViewModel shutdown; desktop window lifetime. Disconnect cancels repository jobs and closes HttpClient, clears drafts/list/token, increments generation; late callbacks from old generation ignored. Kotlin immutable String cannot guarantee heap zeroization; minimize copies and document memory compromise risk. No claims of hardware-secret storage.
- Android derives mode from its generated `BuildConfig.DEBUG`; release manifest denies cleartext. Desktop `MainKt` always passes RELEASE. A distinct `DevelopmentLauncher` in the same phase09-owned entrypoint file is selected only by the custom Gradle `runDebug` task; normal `run`, native distribution and installed launchers select `MainKt`. No property/env/CLI flag or working-directory/JAR heuristic changes its policy. Installation tests try mode-like arguments/properties and still require HTTP rejection.
- Disable engine-level replay: Android OkHttp `retryOnConnectionFailure(false)`, HTTP/SSL redirects disabled; no Ktor HttpRequestRetry plugin or implicit reconnect-and-replay policy on either engine. Audit selected engine version and fault-test dispatch counts for dropped mutation responses. Never attach the token to a redirected/retried different-origin request.

## Kotlin frozen symbols
Packages `dev.doggo.shared.contract`; use `Long` times, `Int` versions/counts (API reject counts >Int max as service error for this personal scope), kotlinx.coroutines `StateFlow`, suspend repository. Foundation owns real data classes/interfaces only, no runtime stubs.

```kotlin
enum class BuildMode { DEBUG, RELEASE }
enum class TodoFilter { ALL, ACTIVE, COMPLETED }
data class Todo(val id: String, val title: String, val completed: Boolean,
  val createdAt: Long, val updatedAt: Long, val version: Int)
data class TodoCounts(val all: Int, val active: Int, val completed: Int)
data class TodoPage(val items: List<Todo>, val nextCursor: String?, val counts: TodoCounts)
data class CreateTodo(val id: String, val title: String)
data class PatchTodo(val expectedVersion: Int, val title: String? = null, val completed: Boolean? = null)
sealed interface ApiResult<out T> {
  data class Success<T>(val value: T): ApiResult<T>
  data class Failure(val error: ApiFailure): ApiResult<Nothing>
}
enum class FailureKind { VALIDATION, UNAUTHORIZED, NOT_FOUND, CONFLICT, NETWORK,
  UNKNOWN_OUTCOME, SERVICE, PROTOCOL }
data class ApiFailure(val kind: FailureKind, val code: String?, val message: String, val field: String? = null)
interface TodoRepository {
  suspend fun list(filter: TodoFilter, cursor: String? = null, limit: Int = 50): ApiResult<TodoPage>
  suspend fun get(id: String): ApiResult<Todo>
  suspend fun create(request: CreateTodo): ApiResult<Todo>
  suspend fun patch(id: String, request: PatchTodo): ApiResult<Todo>
  suspend fun delete(id: String, expectedVersion: Int): ApiResult<Unit>
  fun close()
}
data class EditorState(val id: String?, val rawTitle: String, val expectedVersion: Int?, val error: String?)
enum class SessionStatus { DISCONNECTED, CONNECTING, CONNECTED, UNAUTHORIZED }
data class MutationState(val id: String, val kind: MutationKind, val uncertain: Boolean,
  val canResubmitCreate: Boolean = false)
enum class MutationKind { CREATE, PATCH, DELETE }
data class ConflictState(val id: String, val latest: Todo?, val missing: Boolean)
data class TodoUiState(val session: SessionStatus = SessionStatus.DISCONNECTED,
  val items: List<Todo> = emptyList(), val counts: TodoCounts = TodoCounts(0,0,0),
  val filter: TodoFilter = TodoFilter.ALL, val nextCursor: String? = null,
  val loading: Boolean = false, val loadingMore: Boolean = false, val stale: Boolean = false,
  val editor: EditorState? = null, val pending: MutationState? = null,
  val conflict: ConflictState? = null, val deleteCandidate: Todo? = null,
  val failure: ApiFailure? = null)
interface TodoController {
  val state: StateFlow<TodoUiState>
  fun dispatch(action: TodoAction)
  fun close()
}
```
`TodoAction` exact constructors: `Connect(endpoint:String, token:String)` **non-data class with redacted toString**; singleton `Disconnect`, `Refresh`, `LoadMore`, `OpenCreate`, `SubmitEditor`, `CancelEditor`, `DismissFailure`, `ConfirmDelete`, `CancelDelete`, `ReconcilePending`, `ResubmitConfirmedCreate`, `RefreshConflict`, `ReapplyConflictDraft`, `CancelConflict`; data classes `SelectFilter(filter:TodoFilter)`, `ChangeTitle(rawTitle:String)`, `OpenEdit(todo:Todo)`, `SetCompleted(todo:Todo, completed:Boolean)`, `RequestDelete(todo:Todo)`. UI dispatches no implicit retry; generic Retry button means Refresh or ReconcilePending depending state.

Validation symbols `TitlePolicy.normalizeAndValidate(raw:String): ApiResult<String>` and `EndpointPolicy.validate(raw:String,mode:BuildMode): ApiResult<String>` foundation implementation, tested vectors. Contract `SessionRepositoryFactory` has `fun open(origin:String, token:String): TodoRepository`; secret parameters never serialized/logged. Data exports `createSessionRepositoryFactory(): SessionRepositoryFactory` in Android/JVM platform source sets and common `createTodoController(factory:SessionRepositoryFactory, mode:BuildMode, scope:CoroutineScope, newId:()->String): TodoController`; platform `newTodoId():String` Java UUIDv4 adapters. Controller owns child job of supplied scope, close cancels child (not caller scope). Main dispatcher on JVM provided explicitly via coroutines-swing. UI exports `@Composable fun DoggoApp(state:TodoUiState,onAction:(TodoAction)->Unit)` and `@Composable fun DoggoTheme(content:@Composable ()->Unit)`; UI may depend only on contract, theme/resources, Compose—not data internals.

## Mutation decisions / no blind retries
- Before dispatch, create retains `{id,normalized title}` in memory; edits retain original version and proposed fields. Completed toggles also hold intent until confirmed. Transport failure after mutation dispatch is UNKNOWN_OUTCOME unless provably pre-dispatch validation; never silently repeat.
- Unknown create or ID_EXISTS: GET same id. Found version1, title equal, completed=false -> confirm successful compatible create. Found differing title/completion/version -> show possible collision/concurrent modification with latest record, retain draft; never overwrite or silently accept. 404 -> outcome still uncertain because original could commit later; explicit ResubmitConfirmedCreate sends **same id and same normalized payload**, collision handles race. Still offline -> retain pending and reconciliation action. New UUID requires explicit abandon after warning that original could exist, not automatic retry.
- `canResubmitCreate` is false initially and becomes true only after an authenticated same-session GET for the pending create ID returns404. This is permission for an explicit same-ID attempt, not proof the first request cannot commit later. Clear it before dispatch/reconciliation, on differing record, auth failure or session change. UI enables ResubmitConfirmedCreate only while true; controller independently rejects invalid action/state combinations. Pending payload remains immutable even if another title is typed.
- Unknown PATCH: GET. Exact desired changed fields AND version=expectedVersion+1 -> accept reconciled current result (no claim of causal proof). Unchanged version -> show uncertain action; explicit reapply uses latest confirmed version and existing draft, no automatic retry. Higher/different version -> conflict. Missing -> missing conflict; no implicit recreate. `RefreshConflict` loads current without dropping draft; `ReapplyConflictDraft` explicitly confirms latest version and requested fields; completion-only intent retained too. Missing conflict allows cancel only; user may separately create a new todo.
- Unknown DELETE: GET404 -> confirm absent; GET found -> preserve confirmation/conflict; explicit ConfirmDelete after RefreshConflict binds latest version (requires user confirmation). Direct DELETE404 -> already missing success UI; API still404. Any network reconciliation error stays unresolved.
- VERSION_CONFLICT -> GET for latest, keep draft/intent; do not auto replace expectedVersion. CancelConflict drops mutation intent and refreshes authoritative list. Success refreshes current filter first page. Disconnect drops all in-memory drafts after warning while editor/pending exists.

## Verification vectors and freeze gate
Shared/Worker independent fixtures: whitespace including NBSP/U+3000; 200/201 scalars; astral emoji; combining marks; unpaired surrogate; empty/unknown fields/null/wrong numeric/boolean types; invalid UUID; invalid cursor/filter; safe timestamps/version limits. Tests cover genuine schema/auth/concurrent-write/redirect/session-generation/draft behavior, not source text or echoed mocks. Live API and actual platform acceptance remain separate gates.

## Unresolved questions
Private/session-only, online-first and light-only approval; exact compatible tuple/constant-time runtime primitive/font licensing to freeze during implementation. Multi-user release requires redesign, not a flag.
