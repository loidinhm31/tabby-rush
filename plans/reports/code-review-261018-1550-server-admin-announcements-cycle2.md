# Code Review: Server Admin Announcements (Cycle 2 Re-Review)

**Identifier**: code-review-261018-1550-server-admin-announcements-cycle2
**Date**: 2026-10-18
**Author**: CodeReviewerCycle2
**Verdict**: Approved (Score: 8/10, 0 critical issues, 12 warnings remaining)
**Scope**: Verification of critical fixes C1–C6 and remaining warning findings across TabbyRush and GleanHub.

---

## Executive Summary
**Score: 8/10.** All six critical fixes (C1–C6) are resolved correctly. There are no new critical issues, and 12 warnings remain for operational disposition.

### Executed Checks
- `cargo test -p glean-hub-server --bins`: 41 passed, including BSON date, `deviceCursor`, and pagination tests.
- `cargo test` in `glean-hub-notification`: passed (35 passed, 1 ignored doc-test).
- Native toast tests compiled and ran on Linux with g++: 5 passed (exercises non-Windows stub path).
- `:shared:jvmTest --tests '*remote*'`: build successful.
- Environment holds: frontend vitest (not installed in glean-hub-app), Android connected and Windows native runtime tests.

---

## C1–C6 Remediation Status
- **C1, BSON dates and TTL: resolved.** Every TTL document model (`AnnouncementPreviewDoc`, `AnnouncementDoc`, `AnnouncementInboxDoc`, `AnnouncementDispatchDoc`, `AnnouncementTargetBatchDoc`) uses `chrono_datetime_as_bson_datetime`. Worker upserts write `BsonDateTime::from_chrono` for `expiresAt`, `createdAt`, and lease fields.
- **C2, cursor anchored on last returned item: resolved.** Both `query_sent_page` and `query_inbox_page` pop the look-ahead row and build cursor from `items.last()`. Ordering is consistent (`createdAt` and `_id` descending, `$lt` against tuple).
- **C3, `deviceCursor` null: resolved.** Worker writes `Bson::Null` on insert and `Bson::String` on advance. `deserialize_optional_uuid` handles null, empty/whitespace strings, and valid UUIDs.
- **C4, silent reconnect in controller stream loop: resolved.** `reconnectTrigger` wired into `runStreamLoop` through a watcher job. Buffer overflow, 2048 dedup cap, and lagged reconcile complete trigger; loop restarts cleanly.
- **C5, tr-remote group clear in native teardown: resolved.** `tr_toast_destroy` closes admission, executes `RemoveGroup("tr-remote")` on worker via `post_teardown_and_wait`, drains callbacks, joins worker, and erases handle. `tr_toast_show_local` rejects `tr-remote` group.
- **C6, `tr_toast_setting` pointer lifetime: resolved.** Result stored in `shared_ptr<atomic<int32_t>>` captured by queued lambda. Caller's `out_setting` written only after `TR_S_OK`.

---

## Unresolved Warnings (12)
1. **W1**: `boundAt` and `updatedAt` stored as strings in binding doc; lexicographic comparison in cutoff filter.
2. **W2**: Stuck dispatches on missing install_id/token (re-claimed every 60s instead of failing).
3. **W3**: Worker errors swallowed without logging in top-level loops.
4. **W4**: Pagination tests simulated in-memory without hitting live MongoDB filter/sort.
5. **W5**: N+1 queries on read paths (`query_sent_page`, `query_inbox_page`).
6. **W6**: Cursor JSON built by hand formatting strings without escaping.
7. **W7**: No automated controller unit test for silent reconnect trigger paths.
8. **W8**: Controller concurrency: `activeJobs` mutation from completion handler during quiescence; tight reconnect loop potential.
9. **W9**: Stream client catches generic `Exception` and labels as `OversizedFrameException`, swallowing cancellation.
10. **W10**: `windows-toast-bridge.kt` marks destroyed even if native teardown returns false, preventing retry.
11. **W11**: Native callback drain potential lost wakeup if mutex not held; worker init timeout stop_and_join risk.
12. **W12**: Android install identity regenerated silently on transient Keystore/IO failure.

---

## Unresolved Questions
- Is multi-replica deployment in scope? The in-memory SSE broker and per-process ordinal require shared state for horizontal scaling.
- Should unhandled dispatch states (W2) be terminal `failed` or retryable?
