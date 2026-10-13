# Final revised-plan review — Doggo strict A
Date: 2026-10-13. Planning complete; implementation pending. [Plan](../plan.md) · [Contracts](../contracts.md) · [Decision interview](validation-summary.md).

## Verdict
Complete revised planning package reviewed against the inspected Hub implementation and selected product decisions. No unresolved product choice; no implementation, installation, build, test suite, deployment or database mutation performed. Architectural feasibility is supported by source and cited Mongo documentation, not deployed runtime proof.

## Scope retained
- Reuse existing Glean Hub signup/login/refresh/me, schema registration, access request/approval and dual authentication. Replace Worker/D1; no new auth backend or shared human token.
- Doggo-only immutable strict-v1 routes and isolated app+epoch storage. Existing embedded apps, shared Rust/TypeScript clients, admin UI and auth server unchanged. Legacy clients require no migration for this opt-in extension; actual unchanged-client compatibility remains mandatory.
- Offline Room KMP/bundled SQLite; account+origin partition; platform-protected credentials; Android best-effort background sync. ForumFlash-inspired light UI; headless Fedora/Xpra Android first, Linux RPM next, manual Windows MSI last.
- Guarded logout/account switch preserves unresolved work until explicit resolution or discard, then durable fenced wipe. Local-only logout and admin-account warning accepted.

## Contract review disposition
Earlier [strict contract review](strict-contract-review.md) records draft findings, not remaining blockers. Final contracts and affected phases resolve them:
| Finding | Final disposition |
|---|---|
|R1 epoch authority/recovery|Every request reads active registry epoch/schema before collection use. Fresh-epoch maintenance recovery; immutable captured old namespace; no reset-versus-accept atomicity claim.|
|R2 collection admission/identity|Intrinsic `_id=userId:rowId`; ordinary collection, exact scan index, simple collation, no TTL/custom validator. Per-app+epoch admission does not fail whole Hub initialization.|
|R3 replay/outcome ambiguity|Real journaled conditional nonce-write confirmation; matched count governs acknowledgment. Retryable failure may have applied. Conservative UnknownSuperseded; no expected+1 inference or observed-tombstone auto-clear.|
|R4 scan promotion|Stable-key full scans, not snapshots; complete staging only; retain higher/late confirmed revisions. Missing previously known identity pauses instead of inferring deletion. Epoch mismatch discards staging.|
|R5 frozen identity/digest|Canonical UUIDv4 opId and persisted immutable request; exact length-framed digest; createdAt immutable; invalid mutation does not create receipt.|
|R6 tombstones|Permanent compact deleted rows with empty data; no purge, compaction job or resurrection. Growth/storage limits explicit.|
|R7 registration/errors/isolation|Separate strict-apps route fails closed on old binaries; tolerant optional registry strings; strict-local typed errors; atomic schema-update mode predicate; legacy-only route guard.|

Additional review corrections included:
- Imported recovery rows start revision1/lastOp=null; never invent acceptance history.
- Phase03 read-only candidate-admission binary reuses actual Rust validator; Phase08 recovery script must pass it before registry publication. Negative index/collation/TTL gates included.
- Epoch/schema are authoritative per request, not stale admission-cache authority.
- Recovery registry CAS timeout is MAYBE committed: keep traffic blocked, reconcile authoritative old/candidate pointer; no blind replay, rollback assumption or new epoch regeneration.
- Pre-CAS validation failure leaves old pointer unchanged. Post-CAS admission and old-epoch409 verification precede reopening. No old namespace deletion or reopening rolled-back epoch.
- Strict error mapping preserves local intent; protocol/epoch errors never masquerade as authentication expiration. Legacy service/routes/errors/dual-auth/jobs remain unmodified.

## Executed planning validation
Read-only Python validator checked the final phase/registry/dependency package; assertions passed:
- 10 phase files; all 15 required sections in order.
- 215 distinct literal implementation paths; every phase's path set exactly equals its ownership registry; no duplicate owner.
- Per-phase counts: 6,37,26,14,29,20,25,20,23,15. All20 external Hub paths belong Phase03; excluded embedded/core/auth and legacy engine paths absent.
- 12 dependency edges match the Mermaid graph exactly; no forward-order cycle in phase traversal.
- Phase estimates sum148h; computed ideal critical path96h. Uncertainty ±25–35%; no calendar promise.
- Main frontmatter has all required fields and pending status. Main overview and command index under80 lines; research reports at most150 lines.
- 236 local Markdown links resolved before adding this review and its inbound links. Added review links were checked separately.
- Targeted active-document search found old100h/online-only/shared-token/Worker/D1 assumptions only in explicit supersession, historical links or exclusions; first login still intentionally requires connectivity.

## Parallel execution assessment
Phase01 →02 → {03 server,04 auth,05 data/sync,06 UI} →07 Android integration →08 Android release →09 Linux →10 Windows. Group C has frozen-interface coding independence, not fictitious runtime independence. One integration owner runs coordinated suites after handoffs. Original owners repair their files; root/build/locks/general docs retain named owners through later platform gates. No separate duplicate test phase or shared edit lane.

## Unexecuted acceptance / prerequisites
Approved reachable Hub/Mongo revision, topology, journaling, indexes, operator registration/key/access/recovery and sanitized previous-binary/existing-client fixtures still needed. Real Mongo CAS/replay/epoch/admission and bypass tests, native vault/background/wipe/UI checks, deployed HTTPS two-device proof and installed APK/RPM/MSI gates are future acceptance, not passed here. Previous public502/local-refused observations remain unchanged.

Stable toolchain compatibility, Xpra permissions, font rights/coverage, signing custody and actual Windows host remain implementation prerequisites. Unsupported topology or failed isolation gates require report/replan, never silent legacy fallback.

## Next step
Phase01 headless tooling and Android environment setup only after implementation is requested. No remaining planning decision blocks that first step.
