# Doggo Development Roadmap

**Document Version**: 1.3.0  
**Last Updated**: 2026-10-17
**Primary Reference**: [Project Roadmap](./project-roadmap.md) · [Project Overview PDR](./project-overview-pdr.md)

---

## 1. Multiplatform Milestone Overview

| Milestone | Group | Target Deliverable | Phase Links | Status |
|---|:---:|---|---|:---:|
| **Milestone 1** | A | Headless Tooling & Display Virtualization | [Phase 01](../plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md) | **DONE** |
| **Milestone 2** | B & C | Contracts, Strict Hub Server, Local Sync & Light UI | [Phase 02](../plans/261013-1540-doggo-glean-hub-reuse/phase-02-frozen-foundation.md) to [Phase 06](../plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md) | **DONE** |
| **Milestone 3** | D | Android Composition & Runtime Integration | [Phase 07](../plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md) | **Source Delivered** |
| **Milestone 4** | E | Authorized Hub Operations & Signed Android Release | [Phase 08](../plans/261013-1540-doggo-glean-hub-reuse/phase-08-android-release-operations.md) | **Tooling Operational; Live Release Gates Pending** |
| **Milestone 5** | F | Linux Desktop Distribution (Native RPM & Secret Service) | [Phase 09](../plans/261013-1540-doggo-glean-hub-reuse/phase-09-linux-distribution.md) | **Implementation Delivered; RPM Acceptance Pending** |
| **Milestone 6** | G | Windows Desktop Distribution (Native MSI & DPAPI) | [Phase 10](../plans/261013-1540-doggo-glean-hub-reuse/phase-10-windows-distribution.md) | **Implementation Delivered / 100% (2026-10-14); Technical Acceptance Blocked** |
| **Cross-cutting initiative** | — | Sticky Task Surfaces | [Plan 261016-1409](../plans/261016-1409-sticky-task-surfaces/plan.md) | **Phase 07 documentation and automated-verification evidence delivered (6h/12h); native Android/Windows hardware acceptance held (G2); Phase 08 Linux session pending; 118h/132h credited (89.4%)** |
| **Cross-cutting initiative** | — | Date & Time Picker Migration | [Plan 261017-0709](../plans/261017-0709-date-time-pickers/plan.md) | **Picker source delivered; 372/372 unit/build results reported before latest range change; conditional pending post-change rerun, rendered UI tests, and device/accessibility acceptance** |

**Sticky Task Surfaces:** Phases 01–06 delivered; Phase 07 delivered Android/Windows acceptance documentation and automated-verification evidence (6h/12h credited), with native hardware acceptance conditionally held under Gate G2. Phase 08 requires an actual Linux target/session. The Phase 07 acceptance and code-review reports each list 364/364 Gradle tests; the earlier Cycle 2 review lists 363/363 (231 shared, 98 desktop, 34 Android). These totals are attributed to their respective reports; no tests were rerun in this roadmap update. The four PNGs in `docs/evidence/` are visual design baselines, not runtime OS captures. The 149-checksum root verification baseline and separate Glance artifact-provenance hold remain; Android `closeRuntimeInternal()` catches/logs a delivery-drain timeout and returns, after which `closeActiveRuntime()` clears the runtime reference while coroutine cleanup and Room closure are skipped. Glance cleanup failures and release gates G2/G3/G6/G7 remain open.

**Date & Time Picker Migration:** Replaced manual date/time entry for all four deadline/reminder values with shared Material 3 calendar/clock controls. UTC-midnight normalization prevents local-zone date drift. The 372/372 unit/build results are reported as passing before the current `0..9999` range change; no rerun after that change is evidenced. Rendered UI interactions and native accessibility/device checks remain pending, so technical acceptance is conditional.

- **Phase 08:** Tooling, security audit scripts, disaster recovery runbooks, and offline harnesses are implemented; live compilation and device acceptance require operator SDK setup and Phase 07 remediation.
- **Phase 09:** Source and standalone distributable were delivered; live RPM packaging/installation and Secret Service runtime gates remain pending operator prerequisites.
- **Phase 10:** Implementation was delivered and recorded at 100% (2026-10-14); technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source; clean-user runtime/sync and uninstall/reinstall evidence remain open. The recorded MSI is unsigned. See the [Project Roadmap](./project-roadmap.md) and [Windows Acceptance](./windows-acceptance.md).

---

## 2. Platform Sequence Enforcement
In accordance with architectural policy:
1. Android delivery and release operations precede desktop packaging.
2. Linux RPM distribution (Phase 09) commences only after Phase 08 tooling and release procedures are established.
3. Windows MSI distribution (Phase 10) executes following Linux distribution acceptance.
