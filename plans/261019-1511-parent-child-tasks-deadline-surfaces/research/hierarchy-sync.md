# Hierarchy, storage and sync research

Date: 2026-10-19. Source: terminal report from read-only `HierarchySyncResearch`, with parent verification below. No builds/tests/runtime executed.

## Findings
- No existing hierarchy field/path identified. `Todo` and strict-v2 task payload are flat.
- `strict-sync-v2-contract.kt:3-29` freezes protocol/fingerprints and 16 canonical fields; adding a relationship changes schema/digest. Preserve legacy encodings; introduce v3.
- `integration/glean-hub/tabbyrush-schema.json:1-15` still specifies strict-v1. This is source state, not proof of live deployment mode.
- Room is v2 with four task-bearing stores: `server_base`, `editor_drafts`, `outbox`, `scan_staging`; explicit `DatabaseMigrations.MIGRATION_1_2` and `ALL` registry.
- `room-account-store.kt:417-440` completion reloads current row under mutation admission; `:446-531` deletion currently stages one task or cancels a provably unsent create.
- `outbox-reconciliation.kt:38-63` allows one eligible head per row. `:74-84` preserves exact frozen uncertain operations. There is no relationship ordering.
- Backend `../glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs:184-259` bounds push size, rejects duplicate row IDs, checks active protocol, then processes distinct rows serially with independent results.
- **Research correction:** distinct parent and child row IDs may share a batch. Duplicate-row rejection does not prohibit that. The constraints are lack of cross-row atomicity and lack of confirmed-parent dependency handling.
- Existing receipt/transition support is specifically v1→v2. v3 requires actual Hub and client changes, including recovery for outstanding uncertain v2 writes.

## Existing edit boundaries
Paths below relative to repository root unless prefixed `../`.

| Boundary | Existing files |
|---|---|
| Domain/wire/digest | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{todo-models,account-store,strict-sync-contract,strict-sync-v2-contract,strict-sync-v2-digest}.kt` |
| Four task payload stores | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/{server-base-entity,editor-draft-entity,outbox-entity,scan-staging-entity}.kt` |
| DB/atomic mutation/projection | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/{database-migrations,tabbyrush-database,tabbyrush-dao,room-account-store,persisted-schedule-snapshot}.kt` |
| Sync | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/{hub-sync-dtos,hub-sync-client,outbox-reconciliation,staged-full-scan,account-sync-coordinator,contract-transition-coordinator}.kt` |
| Reminder eligibility | `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/{persisted-schedule-projector,persisted-task-schedule,task-surface-projector}.kt`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/reminder-scheduling-coordinator.kt` |
| Immutable schema/vector baseline | `integration/glean-hub/{tabbyrush-schema-v1,tabbyrush-schema-v2,strict-v2-vectors}.json` |
| Operator tooling | `scripts/{hub-schema-provision,hub-strict-upgrade,strict-v2-cutover-rehearsal,strict-v2-vector-runner,generate-strict-v2-vectors}.mjs` |
| Hub models/services | `../glean-hub/glean-hub-plugin/glean-hub-sync/src/models/{strong_sync,strong_sync_v2,strict_upgrade}.rs`; `src/services/{strong_sync_service,strict_upgrade_service,strict_upgrade_tests}.rs` in that crate |

## Design conclusions
- Nullable immutable `parentTaskId` on each independent child row; existing tasks remain top-level standalone or parent-capable; no moving existing tasks, no reparenting, no grandchildren, no embedded child arrays, and no synced parent aggregates.
- Root effective completion is derived when live children exist (all done => done; add/reopen incomplete child => active). Parent toggle is read-only while children exist; standalone roots have manual completion. Shared projection must run before filtering and feed committed scheduling; unsubmitted editor drafts never schedule or cancel reminders.
- Locally observed last-child deletion preserves immediately preceding effective parent state into raw completion via an explicit atomic parent mutation in SQLite (user-confirmed), not by reusing stale manual state; the childless root becomes manually completable again, subject to unresolved-intent/conflict admission. This is atomic against the complete locally known snapshot, not a globally coordinated guarantee across offline devices. If disjoint child deletions occur across devices with neither observing a last deletion, no parent UPDATE is staged; the root settles on authoritative stored manual completion without synthetic CAS conflict.
- An unresolved preserving parent UPDATE creates a client-local scheduling barrier surviving restart until accepted/discarded, not a barrier for ordinary pending parent edits. Reapply retains provenance/barrier while a new preservation intent is unresolved. Other clients may schedule authoritative state; discard recomputes without undoing accepted child deletes. Conversely, accepted parent UPDATE + uncertain/conflicted child DELETE retains independent outcomes: child-delete discard never rolls back the accepted parent update, and surviving applicable children restore derived completion/reminder eligibility.
- Local group deletion stages parent and all known child deletes atomically in one SQLite transaction; remote CAS results remain per-row. Permanent parent tombstone dominates late child rows. Missing parent without tombstone is an unresolved recovery item, not deletion proof. Discarding a conflicted root DELETE removes local suppression for surviving rows only and never resurrects acknowledged child deletions.
- Hidden child uncertainty/conflicts stay recoverable. Late surviving children receive ordinary cleanup deletes only after original frozen operations are resolved; no payload rewrite or re-ID shortcut.
- Before a fresh child's initial freeze/dispatch, confirm a live root in the current account/epoch under existing eligibility admission. Already-admitted frozen/`UNCERTAIN` replay bypasses renewed parent-liveness gating, preserves exact wire bytes, and matches durable receipts before current live-parent checks; account authorization and identity/integrity checks still apply.
- Strict-v3 cutover must fence unsupported clients and support legacy receipt reconciliation without applying old-version writes after publication.

## Existing behavioral test seams
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt`: populated DB upgrade/reopen pattern.
- `room-account-store-test.kt`, `task-surface-store-sqlite-test.kt`, `task-surface-sync-sqlite-test.kt`, `adversarial-sync-sqlite-test.kt` in the same directory: real SQLite mutations/restart/outbox proof.
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/{account-sync-coordinator,outbox-reconciliation,staged-full-scan}-test.kt`: protocol, ack, scan/conflict behavior.
- Existing frozen v2 vectors and `scripts/strict-v2-vector-runner.mjs`: legacy parity regression baseline; add separately versioned v3 vectors/runner.

## Commands found, not run
- `./gradlew :shared:jvmTest --dependency-verification=off` (README and current recipes; strict verification remains a release hold).
- `node scripts/strict-v2-vector-runner.mjs`.
- `cargo test --manifest-path ../glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked` (documented Hub suite).

## Unresolved prerequisites
Exact v3 contract/fingerprint/upgrade/receipt formats need implementation-phase freeze. Live Mongo staging and operator access have not been established here.
