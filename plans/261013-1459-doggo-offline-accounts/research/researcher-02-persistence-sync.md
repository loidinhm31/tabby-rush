# Research Report: Persistence & Offline Sync Architecture

Date: 2026-10-13 | Target: Android & JVM Desktop (Fedora Linux, Windows) | Backend: Cloudflare Workers + D1

## 1. Engine Choice: Room KMP vs SQLDelight
- **Room KMP (2.7+)**: Official Jetpack Multiplatform release. First-class Kotlin Multiplatform support for Android and Desktop (JVM) via `androidx.room:room-runtime` and `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`).
  - *Pros*: Unified C-bundled SQLite across Android and desktop (no system SQLite version drift on Linux/Windows); native Kotlin Flow DAOs; built-in compile-time schema verification and automated migrations via Room Gradle Plugin.
  - *Cons*: Requires KSP compiler plugin; build-time code generation overhead.
- **SQLDelight (2.0+)**: Mature multiplatform SQL-first library.
  - *Pros*: SQL-file code generation, fast compilation, no KSP.
  - *Cons*: Requires separate driver implementations (`AndroidSqliteDriver` vs `JdbcSqliteDriver` on JVM); migration testing across disparate drivers is more complex.
- **Recommendation**: **Room KMP with BundledSQLiteDriver**. Provides uniform SQLite behavior across Android and Linux/Windows desktop, direct Compose StateFlow ergonomics, and standardized schema migration tooling.

## 2. Local Storage: Account-Partitioned SQLite & Outbox Atomicity
- **Account Partitioning**: Database per account file path:
  - JVM/Linux: `~/.local/share/doggo/accounts/<account_id>/data.db`
  - Android: `context.getDatabasePath("account_<account_id>.db")`
  - Strict filesystem segregation eliminates cross-tenant data leaks and trivializes per-account wipe/backup.
- **Local Tables**:
  - `todos`: Local projection (`id` UUID, `title` TEXT, `is_completed` INT, `server_version` INT, `updated_at` INT, `is_deleted` INT).
  - `mutation_outbox`: FIFO queue (`mutation_id` UUID PK, `todo_id` UUID, `mutation_type` TEXT, `payload_json` TEXT, `created_at` INT, `status` TEXT [PENDING, IN_FLIGHT]).
- **Outbox Atomicity**: Local edits execute within a single SQLite transaction:
  ```sql
  -- Atomic UI mutation + queue
  UPDATE todos SET title = :newTitle WHERE id = :id;
  INSERT INTO mutation_outbox(mutation_id, todo_id, mutation_type, payload_json, created_at, status)
  VALUES (:mutationId, :id, 'RENAME', :payload, :now, 'PENDING');
  ```
  UI observes `todos` filtered by `is_deleted = 0`. Instant optimistic feedback with zero blocking.

## 3. Cloudflare D1 Constraints & Server Architecture
- **D1 Hard Constraints**:
  - Single-writer SQLite architecture: write queries serialized on primary; global read replicas have replication lag (<1s).
  - No interactive transactions (`BEGIN` ... roundtrip ... `COMMIT` unsupported).
  - Transaction primitive is `db.batch([...])`: array of prepared statements executed sequentially inside a single internal SQLite transaction; rolls back completely on any failure.
  - Bound parameters limit: max 100 parameters per query. Single statement max 100 KB. DB max 10 GB.
- **Server Schema**:
  - `account_sync_state`: `(account_id TEXT PRIMARY KEY, latest_server_seq INT)`
  - `mutation_receipts`: `(account_id TEXT, client_mutation_id TEXT PRIMARY KEY, server_seq INT, processed_at INT)`
  - `todos`: `(id TEXT PRIMARY KEY, account_id TEXT, title TEXT, is_completed INT, server_version INT, updated_at INT, is_deleted INT)`
  - `change_feed`: `(account_id TEXT, server_seq INT, todo_id TEXT, mutation_type TEXT, payload_json TEXT, created_at INT, PRIMARY KEY(account_id, server_seq))`
- **Atomic Mutation + Receipt + Feed (`db.batch`)**:
  - Worker processes push with prepared statements in one `db.batch()`:
    1. Idempotency gate: Verify `client_mutation_id` in `mutation_receipts`. If already present, return existing receipt (deduplication).
    2. Upsert/update `todos` with monotonic server version.
    3. Update `account_sync_state`: `UPDATE account_sync_state SET latest_server_seq = latest_server_seq + 1 WHERE account_id = ? RETURNING latest_server_seq`.
    4. Append to `change_feed` with new `server_seq`.
    5. Insert into `mutation_receipts (account_id, client_mutation_id, server_seq)`.
  - All statements execute atomically in 1 roundtrip.

## 4. Pull Protocol, Bootstrapping, Retention & Tombstones
- **Ordering**: Server-assigned `server_seq` integer per account. Device clocks are strictly untrusted for ordering.
- **Incremental Pull**:
  - `GET /sync/pull?cursor=<last_server_seq>&limit=100`
  - D1 executes: `SELECT * FROM change_feed WHERE account_id = :acc AND server_seq > :cursor ORDER BY server_seq ASC LIMIT 100;`
  - Returns `{ changes: [...], cursor: <new_seq>, has_more: boolean }`.
- **Tombstones & Retention**:
  - Deletions are soft-deletes (`is_deleted = 1`) appended to `change_feed`.
  - Retention window: 60 days. D1 cron task purges `change_feed` records older than 60 days where `server_seq < min_active_client_seq`.
- **Bootstrap / Cursor Expiry**:
  - If client cursor < oldest available `change_feed` record (or client cursor = 0):
  - Server returns `410 GONE` / `RESET_REQUIRED`.
  - Client initiates full snapshot bootstrap: `GET /sync/bootstrap` fetching full active `todos` (`is_deleted = 0`) + current `latest_server_seq`.
  - Client resets local synced projection and reapplies pending local outbox mutations.

## 5. Conflict Resolution, Drafts & Delete-vs-Edit
- **Conflict Model**:
  - Single-user multi-device sync (personal todos). Complex operational transformation or CRDTs violate KISS/YAGNI.
  - Field-level merge for independent fields: `is_completed` and `title` mutated independently.
  - LWW by Server Version for concurrent conflicting edits to the same field across devices.
- **Delete vs Edit**:
  - Tombstone wins: If Device A deletes todo X and Device B edits todo X offline:
  - If delete arrives first at server, server marks `is_deleted = 1`. When edit arrives, server detects existing tombstone and rejects edit or treats as dropped. Todo remains deleted.
  - Re-creation: Only an explicit recreate action with new UUID or explicit un-delete can revive.
- **Draft & Outbox Preservation (Rebase Pattern)**:
  - When background pull receives incoming server changes while local `mutation_outbox` has PENDING items:
  - Local base projection is updated to server state.
  - Pending local outbox operations are replayed sequentially over base state, ensuring unsynced user edits are never overwritten on screen.

## 6. Scheduling, Lifecycle & Logout Behavior
- **Sync Scheduling**:
  - *Foreground & Event-driven*: Trigger sync on app open, debounced 500ms after user mutation, manual pull-to-refresh/button, and network reconnected event.
  - *Background Sync Tradeoff*: Android WorkManager (`PeriodicWorkRequest`, 15-min min interval) can drain outbox during idle, but JVM Desktop lacks system WorkManager.
  - *Recommendation*: Prioritize immediate foreground/debounced sync on both platforms. Add simple Android WorkManager task as secondary safety net; JVM Desktop syncs strictly while running.
- **Logout with Unsynced Changes**:
  - Before session termination: Check `mutation_outbox WHERE status IN ('PENDING', 'IN_FLIGHT')`.
  - If rows exist: Display confirmation barrier: *"You have unsaved changes that have not synced to the cloud. Logging out will discard these changes. [Cancel] [Discard & Log Out]"*.
  - On confirmed logout: Wipe local account database file and keychain/secure storage tokens.

## 7. Primary Sources & Citations
- [Android Developers: Set up Room for KMP](https://developer.android.com/kotlin/multiplatform/room)
- [Android Developers: BundledSQLiteDriver API](https://developer.android.com/reference/kotlin/androidx/sqlite/driver/bundled/BundledSQLiteDriver)
- [Cloudflare D1: Limits & Platform Architecture](https://developers.cloudflare.com/d1/platform/limits/)
- [Cloudflare D1: Sessions & Consistency Guide](https://developers.cloudflare.com/d1/)
- [CashApp: SQLDelight Documentation](https://cashapp.github.io/sqldelight/)

## 8. Unresolved Questions
1. Should local account SQLite databases be completely deleted on logout, or locked with encryption key and preserved for fast re-login?
2. Does the free-tier Cloudflare D1 limit (100,000 read units / 50,000 write units per day) require throttling the change feed poll frequency, or is debounced push/pull sufficient?
3. Should the local outbox implement an exponential backoff cap (e.g., 5 attempts max before marking FAILED) or retry indefinitely while network is unreachable?
