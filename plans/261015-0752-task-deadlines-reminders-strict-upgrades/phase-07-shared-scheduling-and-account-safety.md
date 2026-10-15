# Phase 07 — Shared scheduling, persisted eligibility and account safety

## Context links
[Parent](plan.md) · [Rule/time contract](phase-01-freeze-contract-and-transition-design.md) · [Storage](phase-05-shared-model-and-room-migration.md) · [Sync transitions](phase-06-client-version-transition-and-replay.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 20h. Owner: shared lifecycle lead. Depends on phase 05 and phase 01; final sync/conflict integration depends on 06. Gate G5: persisted scheduler state/races exercised before adapters enabled.

## Key Insights
`loadTodos()` includes drafts and is unsafe. UI reload/coordinator progress does not cover all storage changes. Posting and ledger commit cannot be atomic; exactly-once visible delivery is impossible.

## Requirements
- One shared checked calculator/preview; draft-free reactive persisted schedule projection and durable occurrence state.
- Local saved changes schedule offline; unsaved editor/cancel never schedule/cancel.
- Account wipe/mutation delivery serialization and stale callbacks fail closed.
- Grace exactly five minutes: post at most latest eligible opportunity per task, older opportunities skipped, future retained; no burst after resume/clock jump.

## Architecture
- DAO/store Flow recomputes atomic base+submitted outbox snapshot after committed local save/delete/ack/scan/conflict/transition. Never read editor_drafts for eligibility. Observe ledger/settings changes separately to avoid feedback loops.
- Apply latest applicable saved intentions in localRevision order. Pending/dispatching/uncertain are submitted intentions; conflict/unknown heads suppress unresolved schedule, never expose unreviewed conflict drafts. Saved completion/delete/disable remain cancellation barriers even when another rule is conflicted. Resolving/discarding uses a new committed projection.
- Keep OS handles out of domain rule calculation. Adapter contract reconcileSchedule/cancelAccount/postOwned/removeOwned/queryCapability; one scheduling coordinator owns per-account lifecycle.
- Occurrence identity accountKey+persisted generation+rowId+scheduleToken+index. Token only changes on schedule semantics; title update refreshes notification content without duplicating opportunity. Completion→reopen retains prior consumed opportunities, not restart count.
- For now n: choose largest i with due(i)<=n and n-due(i)<=300000ms and not consumed; older due entries marked skipped. If latest eligible already consumed, do not fall back to an older one. Keep all due>n. Exactly 300000ms included; 300001ms skipped. Tasks saved in past are valid but missed opportunities do not burst.
- Durable claim-before-post policy: serialize concurrent callbacks, re-read eligibility/current content/capability, mark occurrence consumed/attempted transactionally, then native post. Crash after claim may lose alert; never replay consumed claim on restart. Posting failure recorded as blocked/failed attempt; no retry beyond rule/grace designed here. Count means opportunities, not visible notifications.
- Shared delivery gate covers task saved mutations, posting and teardown. Recheck wipe marker/persisted generation before opening DB and immediately before native post; do not hold database transaction across slow OS/network calls. Close delivery admission first during leave; pending callbacks stop. Gate drains before Room close; cancel OS handles/remove posted notifications, clear ledger with account wipe. No task text serialized into stale handles.
- Android local identity restore and desktop runtime use same persisted projection; delivery doesn't require valid network credentials or foreground auth refresh. Revoked local account/wipe still blocks.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify shared `contract/account-store.kt`, `data/{doggo-dao,room-account-store}.kt`, `sync/{account-sync-coordinator,outbox-reconciliation,staged-full-scan,account-leave-protocol}.kt`; existing `contract/sync-scheduler.kt` only for combined leave cancellation contract, not repurposing network scheduler for alarms.
Create focused `contract/reminder-scheduler.kt`, `contract/reminder-capability.kt`, `data/reminder-occurrence-store.kt`, shared schedule coordinator/calculator at existing package boundary. Extend shared account-leave/storage/sync behavior tests. Android/desktop runtime integration owned phases 08/09.

## Implementation Steps
1. Implement checked calculator and deterministic clock/strict resolver input; verify arithmetic/boundary vectors, no platform API in commonMain.
2. Implement draft-free consistent observable projection and explicit conflict/cancellation precedence; include every storage mutation path in DAO invalidation rather than UI callbacks.
3. Implement stable identity/durable claim/skip/platform ownership handling and reconcile delta (cancel obsolete, keep unchanged, schedule future). Restart rebuilds handles from current state.
4. Integrate delivery-vs-save/complete/delete/disable/teardown gate with documented lock ordering: lifecycle/delivery gate→store mutex→DB transaction; never inverse, never wait network under gate.
5. Extend leave cancellation gate to both sync and reminders, startup pending-wipe cleanup and posted-notification removal where supported. Failed cancellation retains wipe marker.
6. Exercise throwaway actual SQLite scheduling scenario: local saved create/unsaved edit/title change/complete/reopen/delete/conflict/remote scan, clock jumps/resume, duplicate callback and wipe race.
7. Update storage/lifecycle design with observed state transitions and explicit crash-boundary loss tradeoff.

## Todo list
- [ ] Shared arithmetic/strict time handling and preview contract.
- [ ] Persisted projection invalidation/conflict precedence.
- [ ] Ledger identity/grace/reconcile/claim lifecycle.
- [ ] Saved-mutation and fail-closed wipe race proof.

## Success Criteria
Focused permanent regressions for grace boundary, count1/10 and rejects, title-edit identity, conflicting rule suppression, duplicate callbacks and wipe-vs-post interleavings. Smoke actual SQLite with local save and simulated adapter observes exact intended due/skip/cancel state. Tests alone are insufficient; native proof follows phases 08/09/11.

## Risk Assessment
Holding gate during OS call can delay user action: keep bounded local/native calls, explicit timeout failure; never let timeout reopen wipe admission. Single per-account coordinator avoids duplicate observers.

## Security Considerations
At delivery fetch current task text only after account/generation eligibility. Account-specific posted IDs prevent removing another account's notifications. Native removal limitations recorded, not bypassed.

## Next steps
G5 unlocks phase 08/09 account-aware adapters. Final remote-completion acceptance requires phase 06 and sync receipt, no instant remote cancellation promise.

## Unresolved questions
None; claim-before-post trades possible crash loss for avoiding repeat attempts and is explicitly within agreed opportunity semantics.
