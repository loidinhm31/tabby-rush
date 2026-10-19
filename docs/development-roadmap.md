# TabbyRush Development Roadmap

**Document Version**: 1.19.0
**Last Updated**: 2026-10-19
**Release Baseline**: 1.0.17 (versionCode 18)
**Primary Reference**: [Project Roadmap](./project-roadmap.md) · [Project Overview PDR](./project-overview-pdr.md)

---

## 1. Multiplatform Milestone Overview

| Milestone | Group | Target Deliverable | Phase Links | Status |
|---|:---:|---|---|:---:|
| **Milestone 1** | A | Headless Tooling & Display Virtualization | [Phase 01](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-01-headless-tooling.md) | **DONE** |
| **Milestone 2** | B & C | Contracts, Strict Hub Server, Local Sync & Light UI | [Phase 02](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-02-frozen-foundation.md) to [Phase 06](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-06-compose-ui-theme.md) | **DONE** |
| **Milestone 3** | D | Android Composition & Runtime Integration | [Phase 07](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-07-android-integration.md) | **Source Delivered** |
| **Milestone 4** | E | Authorized Hub Operations & Signed Android Release | [Phase 08](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-08-android-release-operations.md) | **Tooling Operational; Live Release Gates Pending** |
| **Milestone 5** | F | Linux Desktop Distribution (Native RPM & Secret Service) | [Phase 09](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-09-linux-distribution.md) | **Implementation Delivered; RPM Acceptance Pending** |
| **Milestone 6** | G | Windows Desktop Distribution (Native MSI & DPAPI) | [Phase 10](../plans/261013-1540-tabbyrush-glean-hub-reuse/phase-10-windows-distribution.md) | **Implementation Delivered / 100% (2026-10-14); Technical Acceptance Blocked** |
| **Cross-cutting initiative** | — | Sticky Task Surfaces | [Plan 261016-1409](../plans/261016-1409-sticky-task-surfaces/plan.md) | **Phase 07 documentation and automated-verification evidence delivered (6h/12h); native Android/Windows hardware acceptance held (G2); Phase 08 Linux session pending; 118h/132h credited (89.4%)** |
| **Cross-cutting initiative** | — | Date & Time Picker Migration | [Plan 261017-0709](../plans/261017-0709-date-time-pickers/plan.md) | **Picker source delivered; 372/372 unit/build results reported before latest range change; conditional pending post-change rerun, rendered UI tests, and device/accessibility acceptance** |
| **Design & Visual Identity** | — | App Icon Design & Platform Delivery | [Plan 261017-1123](../plans/261017-1123-app-icon-design/plan.md) | **Source & assets delivered; master SVG, Android adaptive vectors (108dp / 66dp safe circle), desktop runtime (256px), tray (32px), Windows multi-size ICO, and unit/build verification passing** |
| **Cross-cutting initiative** | — | Server Admin Announcements | [Plan 261018-0707](../plans/261018-0707-server-admin-notifications/plan.md) | **Phases 01–12 delivered; restricted pilot approved (Android/Linux/Web Admin); Windows runtime held at Gate G5; 566 verifications PASS** |
| **Release Operations** | — | Release Packaging & Security Fixes (1.0.12–1.0.17) | [Deployment Guide](./deployment-guide.md) | **1.0.17 baseline; Windows MSI UTF-8 BOM elimination, release security check resource format validation, AndroidX service permissions** |
| **Hierarchy & Compact Surfaces** | — | Parent/Child Tasks & Compact Deadlines | [Plan 261019-1511](../plans/261019-1511-parent-child-tasks-deadline-surfaces/plan.md) | **Phase 01 DONE (2026-10-19; 24h credited); Phase 02 DONE (2026-10-19; 24h credited); 48h / 120h credited (40.0% planned-effort roll-up)** |

**Sticky Task Surfaces:** Phases 01–06 delivered; Phase 07 delivered Android/Windows acceptance documentation and automated-verification evidence (6h/12h credited), with native hardware acceptance conditionally held under Gate G2. Phase 08 requires an actual Linux target/session. The Phase 07 acceptance and code-review reports each list 364/364 Gradle tests; the earlier Cycle 2 review lists 363/363 (231 shared, 98 desktop, 34 Android). These totals are attributed to their respective reports; no tests were rerun in this roadmap update. The four PNGs in `docs/evidence/` are visual design baselines, not runtime OS captures. The 149-checksum root verification baseline and separate Glance artifact-provenance hold remain; Android `closeRuntimeInternal()` catches/logs a delivery-drain timeout and returns, after which `closeActiveRuntime()` clears the runtime reference while coroutine cleanup and Room closure are skipped. Glance cleanup failures and release gates G2/G3/G6/G7 remain open.

**Date & Time Picker Migration:** Replaced manual date/time entry for all four deadline/reminder values with shared Material 3 calendar/clock controls. UTC-midnight normalization prevents local-zone date drift. The 372/372 unit/build results are reported as passing before the current `0..9999` range change; no rerun after that change is evidenced. Rendered UI interactions and native accessibility/device checks remain pending, so technical acceptance is conditional.


**Server Admin Announcements:** Unidirectional administrator announcements delivered across Phases 01–12. Staged restricted pilot approved for Android (FCM generic alerts, Keystore install proof), Linux (D-Bus notifications), and Web Admin console. Windows desktop runtime remains held at Gate G5 pending native execution proof on physical Windows.

**Release Operations (1.0.12–1.0.17):** Delivers release baseline 1.0.17 (versionCode 18). Eliminates UTF-8 BOM in Windows MSI packaging configs (`scripts/build-windows-remote-msi.ps1`), validates widget provider resource formats (`@xml/` and `@ref/0x` patterns) in `scripts/release-security-check.mjs`, permits verified AndroidX system services/receivers, suppresses `InvalidFragmentVersionForActivityResult` in release lint, and fixes CI release step syntax.

- **Phase 08:** Tooling, security audit scripts, disaster recovery runbooks, and offline harnesses are implemented; live compilation and device acceptance require operator SDK setup and Phase 07 remediation.
- **Phase 09:** Source and standalone distributable were delivered; live RPM packaging/installation and Secret Service runtime gates remain pending operator prerequisites.
- **Phase 10:** Implementation was delivered and recorded at 100% (2026-10-14); technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source; clean-user runtime/sync and uninstall/reinstall evidence remain open. The recorded MSI is unsigned. See the [Project Roadmap](./project-roadmap.md) and [Windows Acceptance](./windows-acceptance.md).

**Parent/Child Tasks & Compact Deadlines:** Two-level task groups with automatic parent completion, preserved completion upon last-child deletion, main-app-only child creation, and own-deadline compact surfaces on Android Glance widget and Linux/Windows sticky mode. Phase 01 administratively marked DONE on 2026-10-19 (24h credited). Phase 02 marked DONE administratively on 2026-10-19 (24h credited); **48h / 120h credited as a planned-effort administrative roll-up (40.0%)**. Phase 02 delivered Room 3 migration (MIGRATION_2_3, nullable parentTaskId on server_base, editor_drafts, outbox, scan_staging), confirmed-parent first-dispatch gating (serialized by coordinator mutex rather than inside SQLite freeze transaction), uncertain replay bypass, versioned v3 push/pull/bootstrap, and V2->V3 safe intent translation. QA Cycle 3 reports 371 passing validations (318 JVM tests across 39 suites + 53 fixture/mock runner checks under --dependency-verification=off; no tests rerun in documentation update). Cycle 3 code review scored 7/10 (0 critical issues, C1 closed, H1-residual open). Technical acceptance remains conditional: H1-residual (release-blocking defect where uncertain/dispatched V2 operations are permanently fenced after READY_V2→V3) and live transition-recovery acceptance are tracked remediation gates in Phase 06 alongside inherited Phase 01 gates (Hub recovery dry-run writes, fence error handling, explicit-null enforcement, service-level coverage) and live MongoDB replica set staging. Phase 03 contract-based development is unblocked; stored last-child preservation review provenance remains an open Phase 03 implementation prerequisite.

---

## 2. Platform Sequence Enforcement
In accordance with architectural policy:
1. Android delivery and release operations precede desktop packaging.
2. Linux RPM distribution (Phase 09) commences only after Phase 08 tooling and release procedures are established.
3. Windows MSI distribution (Phase 10) executes following Linux distribution acceptance.
