# Phase 06 — Cross-device acceptance, rollout and documentation

## Context links
[Parent plan](./evc-cmd-plan.md), [behavior contract](./design-contract.md), phases [1](./phase-01-contract-and-hub.md), [2](./phase-02-room-and-sync.md), [3](./phase-03-hierarchy-and-main-ui.md), [4](./phase-04-android-widget.md), [5](./phase-05-desktop-sticky.md).
[Existing release gates](../../docs/release-acceptance.md), [v2 cutover precedent](../../docs/strict-v2-cutover-runbook.md).

## Overview
Date: 2026-10-20. Priority: P2. Implementation: pending. Review: pending. Provisional effort: 24h (subtotal 48h for surface & rollout phases 4–6). Prove integrated behavior, safely activate v3 on staging/production, remediate tracked Phase 01/02/03 review findings, and reconcile documentation with actual delivery.

## Key Insights
- Tests alone cannot prove Glance launcher/desktop sticky behavior. Runtime screenshots and interactions are required on Android, Linux and Windows.
- Local cascade atomicity and independent remote outcomes must be reported separately. Existing inherited platform/cluster/supply-chain holds remain unchanged unless independently exercised.
- Current recipes use dependency verification off; that mode is diagnostic evidence, not supply-chain release acceptance.

## Requirements
- Every confirmed user behavior works end-to-end, offline and after restart/sync; no hidden partial hierarchy implementation or local-only relationship fallback.
- Validate the confirmed behaviors and per-row refinements in the scenario matrix below against the [design contract](./design-contract.md); local atomicity is not globally coordinated deletion or account-wide scheduling suppression. Depend on Phases 1–5 and matching sibling Hub support before acceptance/cutover.
- Existing v1/v2 data/frozen retries remain intact across upgrade; unsupported old writes fail closed without stranding legitimate receipts.
- Preserve announcements/auth/account isolation and existing surface lifecycle behavior intentionally unchanged outside necessary callsite migration.
- Tracked Phase 03 follow-up remediation gates:
  - Mixed-group queue drainage (W1): when deleting a group where the root is synced but a child CREATE is pending, avoid stranding child operations in `[create:PENDING, delete:PENDING]` when root DELETE is accepted; ensure leaf rows are safely cancelled locally or properly drained without leaving pending count and leave guard uncleared.
  - Preservation UPDATE coalescing & marker retention (W2): ensure preservation UPDATE does not bypass normal mutation coalescing (avoiding extraneous wire noise / permanent barriers for unsent children), and ensure subsequent manual root edits coalescing into a pending preservation op preserve the `LAST_CHILD_PRESERVATION` intent mask and client-local scheduling barrier until accepted or discarded.
  - Sync-layer late-child cleanup & tombstone evidence: implement Step 7 late-child cleanup resolving exact operation state with per-row DELETE and verify production tombstone evidence path.
  - Unresolved-child recovery UI: render `unresolvedChildren` (tasks whose parent is absent without a tombstone) in a dedicated recovery section.
  - Conflict-review copy: implement explanatory copy in `conflict-review.kt` for preservation/barrier CAS conflicts and partial group outcomes.
  - Native UI smoke verification: execute real UI smoke on Android and desktop (group creation, completion, deletion, filters).

- Tracked Phase 04 Android launcher acceptance gate:
  - Physical launcher pinning & live rendering: pin Glance widget to connected Android device/emulator launcher and capture runtime screenshots.
  - Responsive layout scaling: verify rendering across 120×120, 240×120, 240×240, and 320×360dp sizes.
  - Accessibility: verify 200% font scaling, TalkBack aggregated descriptions (title + subtask progress + deadline), and >=48dp physical hit areas across checkbox and row actions.
  - Deadline text wrapping: verify long timed deadline labels (`Due YYYY-MM-DD HH:mm (zone) • Overdue`) wrap cleanly without truncating the absolute date.
  - Lifecycle & security refresh: verify widget state updates cleanly across child completion/reopen, app restart, remote sync, and account switch/sign-out without data leakage.

- Tracked Phase 05 Desktop sticky acceptance & remediation gates:
  - Native Windows/Linux physical display verification: interactive session verification on Linux (X11/Wayland) and native Windows host (DPAPI, WiX/MSI), high-DPI scaling (100%-200%), always-on-top window behavior, tray interaction/recovery, and Orca/Narrator accessibility tree traversal.
  - Controller concurrency & interleaving remediation: release-blocking remediation of readiness conflation vs `startObserving` interleaving during rapid account switches. When StateFlow conflates Ready(A)→Ready(B) where `startObserving(B)` precedes delayed `Ready(B)` collection, the collector must not cancel the new observer job or blank valid account state. Serialize observer teardown and state resets synchronously in `startObserving(token)`, remove asynchronous job cancellation from the collector, and provide deterministic tests under multi-threaded dispatchers verifying that stale emissions and obsolete-account state/actions cannot leak through.
## Architecture
Backend-first version-capable deployment → publish clients with local Room 3 but hierarchy creation gated → **staging environment:** staging dry-run/backup/permit checks → fenced v2→v3 staging migration → schema/fingerprint/epoch read-back → receipt reconciliation/base scan → `ready-v3` staging hierarchy unlock → cross-device acceptance on staging → **production environment:** separate operator-authorized procedure (pre-migration backup, permit checks, fenced cutover, read-back, unlock).

v1 accounts explicitly complete the supported v1→v2 prerequisite. Production migration is an operator-authorized action, not a default developer smoke or fixture command. No post-v3 downgrade.

## Related code files
Prefix `S` = `/home/loidinh/WS/tabby-rush`.
- Existing behavioral suites: `S/shared/src/{commonTest,jvmTest}/kotlin/dev/tabbyrush/shared/` contract/store/sync/scheduler paths named in Phases 1–3; Android widget/device suites and desktop surface suites named in Phases 4–5.
- Existing scripts `S/scripts/{strict-v2-vector-runner,hub-live-smoke,hub-strict-upgrade,release-security-check}.mjs` and proposed v3 runner/tooling from Phase 1.
- Update after exercised delivery: `S/README.md`, `S/docs/{system-architecture,project-overview-pdr,local-storage-and-sync,hub-schema-integration,code-standards,design-guidelines,codebase-summary,project-roadmap,development-roadmap,project-changelog}.md`.
- Update actual acceptance boundaries: `S/docs/{android-acceptance,linux-acceptance,windows-acceptance,release-acceptance}.md`.
- Create `S/docs/strict-v3-cutover-runbook.md` for this version; retain v2 runbook as the historical/prerequisite procedure rather than relabeling it.
- Create implementation evidence under this plan's `reports/` and platform evidence under `S/docs/evidence/`, clearly labeled runtime captures, commands, hosts, protocol and verification mode.
- Active `S/integration/glean-hub/tabbyrush-schema.json` changes only with explicit coordinated target publication, not as proof that production is upgraded.

## Implementation Steps
1. After integrated edits settle, run focused behavioral suites and one full existing regression/build pass on each supported host; no repeated build/format churn mid-flight. Keep deterministic real SQLite and production-path tests; delete obsolete wording/source/incidental-default tests instead of re-pinning them.
2. Run real production-path smoke with temporary fixtures/drivers as needed: populated DB upgrade/reopen, protocol receipts/freeze/scan, hierarchy/scheduler transitions. Keep permanent tests only for plausible consumer-visible edges; remove throwaway drivers after evidence capture.
3. On a live staging Hub/Mongo testbed, prove v2→v3 dry-run, fenced migration, restart/resume, lost publish response read-back, original-version receipts and old-client write rejection. Unavailable live staging is an explicit hold, not a fixture PASS.
4. Exercise the scenario matrix cross-device using Android plus Linux/Windows: existing-root child creation, forbidden moving/reparenting, root-only compact Add, automatic completion/reopen, completed/incomplete last-child capture followed by manual completion, confirm/cancel group deletion, exact main/compact filter differences, drafts versus committed scheduling, replay after root deletion, remote scan and conflict discard/reapply. Observe UI, counts, widget/sticky and reminder eligibility after each convergence.
5. Capture actual launcher and native desktop screenshots and record device/OS/window size/font scale; use non-sensitive test titles/deadlines. Existing PNG designs are not evidence of this feature.
6. Before production cutover, verify operator authorization, fresh data-preserving backup/rehearsal, all-node v3 capability, client receipt compatibility, inherited migration safety findings, and explicit rollback boundaries. Production writes require approved operator gate.
7. Execute upgrade only after prerequisites pass; read back exact computed v3 fingerprint/columns/epoch and `ready-v3`, verify legacy pending work recovery and real cross-device use. After publication/hierarchy writes, forward repair only.
8. Update docs/changelog/roadmaps and replace the architecture's proposed status with accurate delivered/held status only after evidence. Record remaining runtime/supply-chain gates rather than copying historical counts as fresh results.

## Todo list
- [ ] Run integrated behavioral regression and production-path smoke.
- [ ] Prove live staging upgrade/receipts/fencing and adversarial convergence.
- [ ] Capture Android, Linux and Windows actual surface acceptance.
- [ ] Remediate Phase 03 W1: mixed-group queue drainage and unsent child op cancellation.
- [ ] Remediate Phase 03 W2: preservation-marker retention during edit coalescing and preservation bypass.
- [ ] Deliver Phase 03 deferred recovery items: late-child sync cleanup, unresolved-section UI, and conflict-review copy.
- [ ] Execute Phase 04 physical launcher acceptance: responsive sizes (120×120 to 320×360dp), 200% font scale, physical touch targets, deadline wrapping, and lifecycle refresh.
- [ ] Remediate Phase 05 controller concurrency: serialize observer replacement in `startObserving`, remove async job cancellation from readiness collector, and add deterministic multi-threaded interleaving tests.
- [ ] Execute Phase 05 native desktop sticky verification: physical Linux/Windows display, high-DPI scaling, always-on-top, and tray recovery.
- [ ] Complete authorized rollout/read-back or report exact operational hold.
- [ ] Reconcile docs/changelog/phase status with exercised evidence.

## Success Criteria
| Scenario | Required observable outcome |
|---|---|
| Populated Room 1→2→3 and 2→3 reopen | Existing records/schedules/settings/ledger and frozen legacy operation identities preserved; existing tasks migrate as root (`parentTaskId = null`) |
| Existing-root receives child / no reparenting | Pre-existing standalone root successfully receives newly created child in main app; attempts to reparent existing tasks, create grandchildren, or self-reference are strictly rejected |
| Root-only compact Add vs main-app child creation | Android widget and desktop sticky quick-add create independent root tasks only (even when viewing a group); child creation is supported exclusively in main app root action |
| v3 parity | Computed Kotlin/Rust/Node bytes/hash agree; v1/v2 vectors and hashes remain unchanged |
| Automatic completion | All live children done completes parent everywhere; reopen/add active child reactivates parent; parent checkbox is read-only/automatic with no manual toggle |
| Locally observed last-child deletion and restored manual completion | Without concurrent conflict: raw root false + completed last child → deletion captures true; raw root true + incomplete last child → deletion captures false. Each survives DB reopen, then an opposite manual completion request succeeds. Never restore the older pre-children value or override unresolved review. A later remote child re-establishes derived state; no global ordering guarantee |
| Cascade/local crash boundary | One local commit stages group; restart finds no half-staged group; independent remote outcomes remain visible in recovery |
| Group deletion confirmation | Cancel leaves the group unchanged; confirm re-reads and atomically stages the parent plus all currently known children. Confirmation covers the group, not a stale enumerated list; remote acknowledgements remain per-row |
| Two-device disjoint child deletion | Two completed children, raw parent incomplete: each device deletes a different non-last child; after both accept with no parent UPDATE staged, empty root uses authoritative raw incomplete state without synthetic CAS conflict |
| Mixed-completion concurrent deletion | Opposite delivery orders converge on committed per-row state; any parent-intent conflict stays reviewable; no invented global “preceding” completion |
| Child deletes accepted, preserving parent UPDATE conflicted | Unrelated parent edit wins CAS; holding client retains preservation review and client-local scheduling barrier after restart; ordinary pending parent edits do not acquire it. Other clients may schedule authoritative state. Discard clears that intent's barrier and recomputes from surviving authoritative state; reapply carries provenance/barrier while its new preservation intent remains unresolved, never an automatic conflict override or child-delete rollback |
| Preserving parent UPDATE accepted, child DELETE uncertain/conflicted | Restart retains independent outcomes; accepted raw parent update is not rolled back when the child deletion is discarded. Any surviving applicable child keeps parent completion derived even if raw value differs. Existing local submitted-delete suppression may differ until reconciliation/discard; afterward recompute UI/reminder eligibility from surviving committed/submitted state, without treating an uncertain deletion as accepted |
| Child deletes accepted, root DELETE conflicted/discarded | Pending local suppression survives restart; discarding root intent removes suppression and restores surviving authoritative root/children only, never resurrecting acknowledged child deletions; raw/effective state and reminder eligibility recomputed |
| Drafts never affect scheduling | Persisted reminder and quiet deadline scheduling uses only committed base + submitted outbox state; unsubmitted editor drafts (including an unsubmitted child-create/deadline draft) never schedule, alter, or cancel reminders |
| First-dispatch gating versus frozen uncertain replay | Fresh child CREATE cannot initially freeze/dispatch until a live root is confirmed in the current account/epoch. Already-admitted frozen/UNCERTAIN replay never gates on renewed root liveness, preserves exact wire bytes, and matches receipts before live-parent validation; account authorization and identity/integrity checks remain required |
| Lost child-create ack then parent delete | Exact frozen retry proves original acceptance via receipt match before live-parent checks; no fresh-parent rejection shortcut or rehash |
| Concurrent offline late child | Parent tombstone dominates and suppresses row/count/navigation/reminders; safe durable cleanup deletes surviving child without silently dropping uncertainty |
| Missing parent/partial scans | Child whose parent is absent without tombstone stays visible in unresolved recovery section; excluded from normal lists/schedules; no auto-promotion or deletion from temporary absence |
| Parent reminders | Completing last active child cancels existing parent reminder delivery; reopening incomplete child re-enables parent reminder within existing ledger/grace rules without duplicate consumed alerts |
| Filter exact mixed group example | Root R has active child C1 and completed child C2. R's effective state is active (since C1 is incomplete). Under main app Active filter: R matches directly and C1 matches directly (match count = 2), while C2 is omitted. Under main app Completed filter: C2 matches directly (match count = 1), while R appears solely as context for C2 without inflating the Completed match count (C1 is omitted). In contrast, compact surfaces (widget/sticky) show whole group (R + C1 + C2) together under Active, never splitting the group across sections or omitting siblings |
| Own deadlines | Row with no deadline omits deadline metadata line entirely (no placeholder or "No deadline" text); date-only exact `Due YYYY-MM-DD`; timed `Due YYYY-MM-DD HH:mm (captured zone)`; reminder anchor time is never shown as deadline time; overdue rows retain absolute date visible |
| Actual surfaces | Android launcher, Linux sticky and Windows sticky screenshots/interactions at narrow width and 200% text; native tray/recovery behavior observed |
| Account lifecycle | Stale generation actions/publications rejected via `SurfaceAccountToken`; sign-out/switch does not expose another account's hierarchy/date data |
| Live upgrade | Preserved row IDs/epoch/revisions/tombstones/lastOp evidence; v3 read-back correct; legacy writes fenced and original receipts recoverable; forward-only repair after v3 publication |

Future commands, not results: `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest :desktopApp:test :androidApp:assembleDebug`; legacy/v3 vector runners; Hub `cargo test --manifest-path ../glean-hub/glean-hub-plugin/glean-hub-sync/Cargo.toml --locked`; `node scripts/hub-live-smoke.mjs --fixture-test` for existing legacy fixture regression only; actual `just run-android`/`just run-desktop` plus native Windows run. JDK 21 required; any `--dependency-verification=off` use must be disclosed and cannot close strict-verification holds.

## Risk Assessment
Staging credentials/Mongo topology, inherited permit/checkpoint findings, native Windows access and supply-chain verification can hold production acceptance. Conflating local atomicity with global completion preservation, main filters with compact groups, or client-local conflict barriers with account-wide suppression invalidates acceptance. Finish reachable code/smoke/docs but do not label rollout or platform behavior accepted without observations. No destructive production reset/downgrade.

## Security Considerations
Use authorized staging/operator accounts, environment-protected secrets, redacted logs, non-sensitive screenshots, account-scoped data and existing native privacy barriers. Security diagnostics do not prove native cleanup under all OS failures.

## Next steps
Review actual evidence and settle phase completion versus operational holds. No implementation begins as part of this planning command. Unresolved product questions: none. External acceptance prerequisites remain explicitly unverified until execution.
