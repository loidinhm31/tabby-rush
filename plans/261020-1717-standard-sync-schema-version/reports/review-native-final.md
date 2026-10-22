## Code Review Summary

### Scope
- Follow-up to `review-native.md`; latest native safety fixes only. Source review, not runtime acceptance.
- Read project README/PDR/standards, native phase requirements, published development rules and code-review skill.
- Production files reviewed under `shared/src/commonMain/kotlin/dev/tabbyrush/shared/`:
  - `sync/{account-sync-coordinator,outbox-reconciliation,staged-full-scan,task-json-merge,hub-sync-client,hub-sync-dtos}.kt`.
  - Relevant `data/{tabbyrush-dao,room-account-store}.kt` transactions/projections.
  - `contract/{task-data-contract,endpoint-policy,todo-models,persisted-schedule-projector}.kt`.
- Supporting source: Android/JVM timezone resolvers; Android/Desktop conflict presentation/action callsites.
- Regression source: `native-sync-safety-sqlite-test.kt`, `task-json-merge-test.kt`, and relevant scan/outbox/endpoint tests.
- Approximately 2,000 relevant production/test lines inspected. No plan/source edits; this report is the sole write.
- No git commands, builds, tests, linters, formatters, secrets, live databases/services or desktop control used.

### Overall Assessment
**Approve the scoped source fixes: N1–N5 resolved at source-review level.** No remaining actionable defect established in these fixes. This is not a claim that native interactive acceptance passed.

### Critical Issues
None established.

### High Priority Findings
None remaining from this review.

#### N1 — Stale staged scans: resolved
- `data/tabbyrush-dao.kt:484-491` always clears staging, advances the generation and resets the scan checkpoint; neither interrupted `SCANNING` nor unpromoted `COMPLETE` generations resume.
- `sync/staged-full-scan.kt:16-44` starts that fresh generation before pulling and promotes only the completed current invocation. Failure/page-budget exit requires a new scan next round.
- `sync/account-sync-coordinator.kt:44-53,107-128` completes a fresh scan and incremental catch-up before migration/reconciliation/write preparation.
- Completed-scan absence becomes a retained deleted base (`data/tabbyrush-dao.kt:544-546`); dispatch preparation rejects it rather than treating an update as create (`sync/outbox-reconciliation.kt:27-34`).
- Authored SQLite regression covers both interrupted and completed-but-unpromoted staging across reopening, retained draft, fresh null checkpoint and blocked write (`native-sync-safety-sqlite-test.kt:47-73`). Not executed here.

#### N2 — Unknown intent inventing all-field overwrite: resolved
- `sync/account-sync-coordinator.kt:206-220` rejects null/unrecognized masks with `UNKNOWN_LOCAL_INTENT`; no all-known-field fallback and no replacement operation on rejection.
- Existing merge independently rejects unknown masks (`sync/task-json-merge.kt:18-20`). Known-mask reapply still starts from refreshed raw base.
- SQLite regression retains the exact original review operation for null, `REQUIRES_REVIEW` and partially unknown masks, with no eligible dispatch (`native-sync-safety-sqlite-test.kt:75-98`). Not executed here.

### Medium Priority Improvements
None required to close the scoped findings.

#### N3 — Copying oldest operation instead of latest local version: resolved
- Coordinator delegates to `copyLatestConflictAsNew` (`sync/account-sync-coordinator.kt:223-225`).
- `data/room-account-store.kt:398-408` captures the latest draft/outbox projection under mutation lock, creates a new root UUID and a distinct deadline schedule token, and records the represented source revision boundary.
- `data/tabbyrush-dao.kt:451-466` commits new draft/outbox/meta and retirement of represented source revisions in one Room transaction. Source edits cannot interleave through the store mutation lock; later independent edits are not retired by this action.
- Regression includes a newer edit after freezing, forced SQL rollback during retirement, reopen persistence and a later independent source edit (`native-sync-safety-sqlite-test.kt:100-125`). Not executed here.

#### N4 — Civil deadline/epoch contradictions scheduling incorrectly: resolved
- `TaskData.validate()` keeps structural and checked-arithmetic validation; incoming decode now also calls `validateDeadlineInstants` (`sync/task-json-merge.kt:50-68`).
- Anchor must equal resolver output for the captured date/time/zone/offset. Timed overdue equals that anchor; date-only overdue equals next calendar-day start, not an assumed 24-hour duration.
- Both platform resolvers honor either valid captured overlap offset and reject gaps/invalid offsets. Invalid raw records receive `projectionError` while retaining JSON (`sync/task-json-merge.kt:71-86`); persisted scheduling excludes them before overlays (`contract/persisted-schedule-projector.kt:35-37`).
- Authored tests cover wrong-day epoch, mismatched offset, DST date-only boundary and both overlap choices/crossed anchors (`task-json-merge-test.kt:53-99`). Not executed here.

#### N5 — Oversized batch frozen before byte admission: resolved
- `sync/outbox-reconciliation.kt:15-75` budgets before freezing each candidate using the same `syncWireJson` codec as HTTP.
- Budget includes actual captured pull/checkpoint, wrapper JSON, serialized record UTF-8 bytes and record separators. Coordinator passes that same pull into the eventual delta (`sync/account-sync-coordinator.kt:52-55`).
- Individually fitting records that do not fit the current batch remain pending/unfrozen. A single oversized record becomes a known-unsent conflict with actionable guidance, not uncertain delivery.
- SQLite regressions cover two large multibyte opaque payloads, escaped/multibyte checkpoint, retained unselected operation, subsequent admission and single-record oversize retention across reopening (`native-sync-safety-sqlite-test.kt:127-159`). Not executed here.

### Low Priority Suggestions
None; no style-only findings added.

### EndpointPolicy Assessment
- `contract/endpoint-policy.kt:4-14` explicitly admits debug HTTP for localhost, 127.0.0.1 and Android-emulator host alias 10.0.2.2 on ports 3100/3101, alongside existing port3000 entries.
- Release HTTP rejection still precedes debug allowlist admission (`:146-167`); HTTPS path unchanged (`:142-145`). No broad LAN/wildcard HTTP exception introduced.
- No actionable defect established in this requested policy change. Existing endpoint test source lacks dedicated 3100/3101 matrix cases; policy assessment here is source-based, not claimed runtime evidence.

### Positive Observations
- Fixes preserve raw opaque fields, durable evidence and explicit user recovery rather than weakening safety checks.
- Copy retirement is transactional; byte admission distinguishes known-unsent from unknown delivery; deadline projection fails closed without rewriting remote timestamps.

### Recommended Actions
1. Accept N1–N5 as resolved for this follow-up source review.
2. Retain parent-owned integrated verification evidence separately; no checks rerun here.
3. Native UI smoke remains held because required computer-control approval timed out, as supplied in assignment context. Source approval does not close that acceptance boundary. Unrelated historical release holds were not re-reviewed.

### Metrics
- New actionable findings: 0 critical, 0 high, 0 medium, 0 low.
- Prior findings: 5/5 resolved at source-review level.
- Type/test coverage and lint counts: not measured; no execution performed.
- Plans: intentionally unchanged; plan status belongs to the coordinating owner.

### Unresolved Questions
None blocking this scoped source review.
