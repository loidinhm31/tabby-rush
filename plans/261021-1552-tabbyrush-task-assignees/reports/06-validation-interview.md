# Plan validation interview

Date: 2026-10-21. Plan directory: `G:/ws/sharing/doggo/plans/261021-1552-tabbyrush-task-assignees`.
Plan: [collaborative task assignees](../plan.md).
Workflow: published `evc-cmd-plan-x-validate` instructions applied to this explicit plan path. Session mode=prompt, questions=3–8. User selected Yes to interview.

## Questions and user answers

| Decision | User answer | Plan consequence |
|---|---|---|
| Unsent collaborative edits after authoritative access revocation | **Purge content; retain metadata** | Keep privacy-first purge of revoked content/drafts/outbox payloads; retain only content-free, non-replayable identity/version/failure evidence. No recovery quarantine. |
| Older TabbyRush clients when assignment policy is enabled | **Block legacy sync at opt-in** | Keep whole-app capability gate before any opted-in push/pull/delta. Old clients cannot sync until upgraded; offline caches cannot be remotely erased. Other apps unchanged. |
| Transactional write coordination | **Keep the simpler app-wide guard** | Keep transaction-capable Mongo replica set and one app-scoped authorization guard for TabbyRush writes. Prove edit/revoke ordering and measure contention in disposable acceptance. |

Three substantive decision questions asked. One clarification exchange for the third question; no extra product decision introduced.

## Clarification provided before third answer
Explained the check/revoke/write race: Hub may check Alice's permission, creator revoke, and Alice's stale checked edit otherwise commit. Transaction guard establishes commit order. Compared simple app-wide serialization against more concurrent but more complex owner/task-group guards, including parent/child overlap and coherent permission scans. Explained that other Hub apps are unaffected, offline local edits remain immediate, and a single-node replica set suffices for disposable transaction testing. No database configuration action authorized.

## Confirmed decisions
All three selected options match the proposed frozen design; no architecture redesign required. Their operational consequences are now synchronized into the existing plan/command overview and all nine phase files: relevant requirements, implementation checklists, success criteria and G0/G1–G6 statuses. Existing exclusive ownership, dependencies, technical contracts and284h planning estimate remain unchanged; no new plan/directory/files or implementation created.

## Action items
- [x] Record answers in plan Validation Summary and this report; propagate confirmed choices into all9 existing phases and related planning records.
- [x] Keep implementation/review pending; interview is not authorization to code, enable policy, deploy or migrate production.
- [ ] During future implementation, complete all planned behavioral/API/storage/native acceptance gates and report measured app-guard contention.

## Recommendation
Plan ready for separate implementation approval. Confirmed choices are reflected in phase instructions, not merely this interview record; G0 policy validation complete, G1–G6 implementation/runtime gates remain pending. Feature/runtime acceptance remains unproven; no design change or coding authorization inferred.

## Unresolved questions
None from the interview. Disposable transaction-capable services, native Android/desktop access, interactive approvals and separate production authorization remain future prerequisites.
