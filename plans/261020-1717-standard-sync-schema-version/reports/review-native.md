## Code Review Summary

### Scope
- Source-only review; no source fixes, git operations, builds, tests, formatters, live services/databases, secrets or verification mirrors accessed.
- Read `README.md`, PDR/code standards, published development rules/code-review skill, plan/contracts, phase02/03 inventories.
- Core reviewed: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/{data/database-migrations.kt,data/tabbyrush-dao.kt,data/room-account-store.kt,sync/account-sync-coordinator.kt,sync/outbox-reconciliation.kt,sync/staged-full-scan.kt,sync/legacy-standard-intent-migration.kt,sync/task-json-merge.kt,sync/hub-sync-client.kt,sync/hub-sync-dtos.kt,contract/persisted-schedule-projector.kt,contract/todo-models.kt,contract/task-data-contract.kt}`.
- Supporting sections: auth session/HTTP routing; Android SessionViewModel/build configuration; Desktop controller/dotenv/build configuration; conflict review; deadline helper/calculator; ordinary schema; authored SQLite migration/adversarial and scan cases; backend standard upsert/checkpoint semantics.
- Approximately 4,500 relevant source/test lines inspected. Plan unchanged by assignment; report is sole write.
- All reproduction chains below are **[INFERENCE] from source, not executed**. Review completion is not feature verification.

### Overall Assessment
Room migration archives original outbox values; raw JSON merge and exact tuple acknowledgement are sensible. Two high-priority safety holes remain: indefinitely resumable scans defeat the retention defense, and ambiguous legacy reapply silently becomes all-field overwrite. Three medium findings affect conflict recovery, scheduling and large opaque records.

### Critical Issues
None established in reviewed source.

### High Priority Findings

#### N1 — Resuming an old full scan can recreate a row whose tombstone expired
- Locations: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt:467-473,518-529`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt:18-26`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:110-120`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt:21-27,37-46`.
- `beginScanAtomic` resumes any `SCANNING`/`COMPLETE` generation, without age or reconnect invalidation. Coordinator changes only `READY` to `REQUIRED`; staged live rows remain authoritative across arbitrary downtime.
- **[INFERENCE] Reproduction:** scan page1 stages task A live; pause before final page; another device deletes A; stay offline past tombstone retention; reconnect and finish from persisted checkpoint. A is still in `seen`, so promotion restores its staged live base. Incremental catch-up cannot return the purged tombstone. A retained title edit now freezes as an ordinary update and is submitted against a missing server row.
- User impact: deleted task reappears locally and can be recreated remotely automatically. Backend ordinary missing-row upsert is intentional (`G:/ws/sharing/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/sync_service.rs:331-348,360-402`), so native absence protection must not depend on old staging.
- Minimal fix: invalidate incomplete/completed-but-unpromoted scan generations on reconnect/restart when no trustworthy retention bound exists; restart from null before authorizing writes. If resumability retained within one uninterrupted session, persist/enforce the boundary and never promote arbitrarily old staging as fresh evidence.

#### N2 — Unknown legacy intent becomes an undisclosed overwrite of every known field
- Location: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:210-217`; UI promise: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/conflict-review.kt:191-205`.
- Invalid/null `intentMask` falls back to `TITLE|COMPLETED|DEADLINE|REMINDER|PARENT`. Ambiguous historical masks are legitimate review inputs: Room1 migration explicitly emits `REQUIRES_REVIEW` for unclassifiable operations (`data/database-migrations.kt:85-98`), and translation correctly refuses unknown masks (`sync/legacy-standard-intent-migration.kt:58-59`). Reapply reverses that protection.
- **[INFERENCE] Reproduction:** existing migrated review operation has null/unknown mask and a pre-deadline snapshot; current remote task has a deadline/reminder or newer completion. User chooses **Reapply My Changed Fields**. Fallback captures all fields; `TaskJsonMerge.merge` copies historical null deadline/default reminder/completion onto refreshed raw base (`sync/task-json-merge.kt:18-28`). Next sync persists the wipe.
- User impact: server values not known to be edited locally are destroyed despite intent-only action wording. Unknown JSON keys survive, but known fields added/evolved since that snapshot do not.
- Minimal fix: reject ambiguous reapply until user explicitly selects intended fields, or expose a separate clearly confirmed full-known-field overwrite showing the complete diff. Do not fabricate an intent mask.

### Medium Priority Improvements

#### N3 — Copy-as-new copies the oldest operation, not the local version shown to the user
- Locations: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:222-235`; Android conflict presentation `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:503-513`; Desktop equivalent `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:870-880`.
- Conflict UI preferentially shows `allTodos` (latest draft), while copy decodes `reviewHead(rowId).intentJson()` and discards only that head.
- **[INFERENCE] Reproduction:** freeze title A; edit same task to title B during dispatch; first delivery becomes review. Conflict card shows B. Choose **Copy Local as New Root Task**: new root contains A. Newer B remains queued against original identity and can later dispatch independently, rather than being the copy the user selected.
- Minimal fix: take a mutation-locked latest retained local snapshot for copy; atomically establish the copy and explicitly reconcile the source revisions represented by the action. Preserve any revisions not included. Ensure displayed conflict version and copied version are the same.

#### N4 — Inconsistent deadline timestamps pass projection and can schedule the wrong day
- Locations: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/task-json-merge.kt:50-57`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt:18-28,89-98`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/reminder-schedule-calculator.kt:47-63`.
- Decode parses date/time/zone independently, but neither `TaskData.validate()` nor `TaskDeadline` checks that captured offset/anchor/overdue describe those civil fields. Generic schema only checks column types; the removed strict service cannot be assumed to enforce native schedule semantics.
- **[INFERENCE] Reproduction:** standard row contains `deadlineDate=2026-10-20`, `deadlineTime=10:00`, `deadlineZone=UTC`, offset 0, but anchor/overdue both point to 2026-10-21 10:00 UTC; enable one reminder with a token. Row projects without `projectionError`; persisted scheduler uses the anchor epoch, while native UI displays October 20. Date-only rows can similarly supply an overdue value unrelated to next local midnight.
- Minimal fix: validate these relationships using the existing timezone resolver at incoming projection; preserve inconsistent raw JSON as incompatible and suppress scheduling. Respect captured overlap offset; do not silently rewrite persisted timestamps. Editor already computes these relationships (`ui/deadline-calculator-helpers.kt:77-145`).

#### N5 — Oversized multi-row batch is frozen before byte-size admission and misclassified as uncertain
- Locations: `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt:10-15,46-50`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt:74-80`; `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:54-62,76-80`.
- Selection limits count only. HTTP client rejects bodies over 256 KiB before networking, after every selected operation has already become `DISPATCHING`. Coordinator marks the entire batch uncertain; next round sends those edits to manual review rather than splitting them.
- **[INFERENCE] Reproduction:** two existing server rows each contain roughly 140 KiB of valid opaque JSON. Edit a native title on each. Either row is individually submittable, but combined delta exceeds the local limit; neither POST is attempted, and both known-unsent edits become delivery-uncertain/review.
- Minimal fix: budget serialized delta bytes before freezing; choose a fitting prefix/subset, retaining unselected rows as pending. For a single oversized record return actionable validation without claiming unknown delivery.

### Positive Observations
- Migration 3→4 archives all original outbox columns, including tagged FLOAT bits/BLOB bytes; no destructive Room fallback introduced.
- Completed-generation promotion and page/checkpoint changes are DAO transactions. Interrupted dispatches remain uncertain; absence is not inferred from an incomplete scan.
- Exact `(tableName,rowId,version)` accepted tuples; newer draft revisions retained; terminal acknowledgement/convergence evidence retained.
- Raw JSON merge starts from refreshed server object; nested/nullable unknown fields are not round-tripped through typed task models.
- Missing/deleted/incompatible confirmed rows blocked at dispatch; uncertain operations never automatically replayed; convergence explicitly not called a receipt.
- Account-bound auth token admission under session mutex; no task POST refresh replay. Isolated Android package suffix and suppressed implicit dotenv/Firebase discovery are appropriate source-level guards.
- Group mutations remain locally transactional; generic server independence/partial cleanup is disclosed rather than falsely claiming strict group atomicity.

### Recommended Actions
1. Resolve N1/N2 before acceptance. Add source-owned regressions for stale staged scan across deletion retention and ambiguous legacy intent reapply.
2. Align conflict copy snapshot with displayed latest local state; exercise concurrent newer revisions and copy interruption.
3. Cover inconsistent calendar/epoch rows and byte-budget splitting without changing generic server guarantees.
4. Coordinator owns compilation, Room4 generation and integrated verification. No reruns performed here; authored tests are not execution evidence.

### Metrics
- Concrete findings: 2 high, 3 medium; no critical established.
- Type coverage/test coverage/lint counts: not measured (source-only assignment).
- Plan task status: not updated by design; implementation/acceptance status remains coordinator-owned.

### Unresolved Questions
None blocking this source review. Runtime acceptance, platform behavior and dependency/build outcomes remain unobserved here.
