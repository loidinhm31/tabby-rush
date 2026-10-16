# Project Manager Progress Report: Phase 01 Shared Foundation

**Date:** 2026-10-16  
**Phase:** 01 — Shared contracts, authoritative store and build foundation  
**Status:** **DELIVERED — 100% of planned Phase 01 scope (24h / 24h); technical acceptance conditional, dependency-provenance gate pending**  
**Author:** EvcProjectManager  
**Target Plan:** [Sticky Task Surfaces](../261016-1409-sticky-task-surfaces/plan.md)  
**QA:** 307/307 Gradle tests passed; separate 18/18 fixture, 6/6 source-security, and 8/8 strict-v2 self-test checks reported  
**Code Review:** 9.4/10, Conditional Pass — Terminal Handoff Authorized; External Provenance Gate Pending ([Cycle 3 review](code-review-261016-1911-phase-01-shared-foundation-cycle-3.md))

---

## 1. Executive Summary & Progress

Phase 01 delivered its full planned shared foundation and received a terminal conditional handoff authorization to Phases 02, 03, and 04. Those three leaves are unblocked and may proceed against frozen contracts. The Cycle 2 server-tombstone/local-draft-or-outbox finding is resolved and covered by two SQLite regression tests. Strict Glance dependency provenance remains an external, unwaived hold; leaf-native behavior remains to be proven downstream.

- **Phase 01:** 100% of planned scope delivered; 24h / 24h credited.
- **Overall sticky-surfaces plan:** in progress; 24h / 132h credited (**18.2% effort roll-up**), not a runtime-acceptance percentage.
- **Technical acceptance:** conditional. Dependency verification has not passed; downstream native acceptance has not occurred.

## 2. Delivered Scope

1. **Authoritative task store:** `AccountStore.observeTodos()` and desired-state `setCompletedLocally(id, completed, nowEpochMs)` delivered with single-lock mutation behavior.
2. **Shared contracts and projections:** `TaskSurfaceAccess`, readiness and section contracts, `selectNextDeadline()`, and `partitionSurfaceTodos()` frozen for consumers.
3. **Platform contracts:** Android task-surface host/owner contract and desktop sticky task state/actions delivered without temporary runtime scaffolds.
4. **Build foundation:** Glance 1.2.0 selected; Android dependency declaration and lock foundation delivered. Reported compilation used the project toolchain.
5. **Finding resolution:** authoritative server tombstones now return `Missing` before local overlays are mutated; residual-draft and residual-outbox regression cases pass.

## 3. QA & Review Evidence

The [full-suite QA report](qa-261016-1911-phase-01-full-suite-validation.md) reports:

| Check | Reported result |
|---|---:|
| Shared JVM | 231 / 231 passed |
| Desktop | 64 / 64 passed |
| Android unit tests | 12 / 12 passed |
| **Gradle total** | **307 / 307 passed; 0 failures, 0 skips** |
| Offline Hub fixture smoke | 18 / 18 passed |
| Source security diagnostics | 6 / 6 clean |
| Strict-v2 cutover self-test | 8 / 8 passed |

The QA report states Gradle tasks were rerun to ensure tests executed; these runs used `--dependency-verification=off`. They establish unit/regression results in that mode, not strict dependency integrity, live Hub behavior, APK/release acceptance, Android device instrumentation, or live native surface delivery. Coverage percentages were not generated.

The [Cycle 3 code review](code-review-261016-1911-phase-01-shared-foundation-cycle-3.md) scored **9.4/10** with **0 critical issues** and authorized terminal conditional handoff. It verifies the tombstone-overlay remediation via `testSetCompletedLocallyTombstoneWithResidualDraftReturnsMissingWithoutException` and `testSetCompletedLocallyTombstoneWithResidualOutboxReturnsMissingWithoutException`.

This PM report reconciles those existing artifacts; no tests or builds were rerun for this documentation update.

## 4. Open Risks & Acceptance Follow-ups

1. **Dependency provenance hold — open.** Glance 1.2.0 versions are locked, but the review reports no Glance entries in `gradle/verification-metadata.xml`. Gradle was run with verification disabled because of the pre-existing 149-unmatched-checksum root classpath baseline. Version locking is not cryptographic artifact authentication; strict verification remains pending and is not waived by the handoff.
2. **Leaf-native proof — open downstream gate.** Launcher widget rendering and account-switch clearing, notification permission/dismissal and shade behavior, desktop sticky window/tray behavior, and later Linux session behavior are not established by Phase 01 tests. Complete leaf/integration work and prove native behavior in the assigned later acceptance phases; do not infer runtime success from shared tests or compilation.
3. **Non-gating review hardening — disposition pending.** Cycle 3 recommends bounding initial observer-test awaits and ensuring collector cleanup, removing a redundant server-base lookup, and aligning one test name with its sequential setup. These are separate from the resolved tombstone finding and do not block the authorized leaf handoff; do not treat them as new acceptance gates.
4. **Coverage evidence — unavailable.** The QA report generated no line/branch/function coverage report; produce one only if project thresholds require it.

## 5. Next Steps — Main Agent

**Main agent MUST complete the overall implementation plan and its unfinished work; Phase 01 delivery is not completion of Sticky Task Surfaces.** Keep the plan `in-progress` and continue the following:

1. Start Phases 02, 03, and 04 against the frozen Phase 01 contracts; they are unblocked and may proceed concurrently within their ownership boundaries.
2. Complete those leaf implementations and their behavioral tests; preserve the real-host boundary and do not substitute mocks for runtime proof.
3. Track the dependency-provenance hold: identify the owner and schedule for trusted metadata regeneration, reconcile Glance artifact checksums, and rerun strict dependency verification before claiming provenance.
4. Complete Phases 05 and 06 after their specified leaf prerequisites, then Phase 07 Android/Windows acceptance and shared documentation; retain real launcher, notification, Windows, and later Linux evidence as separate gates.
5. Main integrator performs one coherent aggregate verification after owner changes land; do not run concurrent build/test storms against partial integration.
6. Track the non-gating Cycle 3 hardening recommendations separately; they do not gate the authorized leaf handoff. Update plan status only when actual acceptance gates change.

The [roadmap](../../docs/project-roadmap.md) now records Phase 01 delivery, 24/132h progress, and the downstream gates; the [changelog](../../docs/project-changelog.md) records the delivered foundation and unresolved provenance/native-proof boundaries.

## 6. Unresolved Questions

1. Who owns and when is the repository-wide 149-checksum baseline and Glance artifact provenance regeneration scheduled? The Cycle 3 review raises this question; no schedule is recorded in the available evidence.
