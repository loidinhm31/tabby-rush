# Doggo Development Roadmap

**Document Version**: 1.2.0  
**Last Updated**: 2026-10-16
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
| **Cross-cutting initiative** | — | Sticky Task Surfaces foundation and platform leaves | [Plan 261016-1409](../plans/261016-1409-sticky-task-surfaces/phase-01-shared-foundation.md) | **Phase 01 delivered (conditional); 02–04 handoff authorized; provenance pending** |

- **Phase 08:** Tooling, security audit scripts, disaster recovery runbooks, and offline harnesses are implemented; live compilation and device acceptance require operator SDK setup and Phase 07 remediation.
- **Phase 09:** Source and standalone distributable were delivered; live RPM packaging/installation and Secret Service runtime gates remain pending operator prerequisites.
- **Phase 10:** Implementation was delivered and recorded at 100% (2026-10-14); technical acceptance is blocked pending operator prerequisites for interactive MSI installation, Narrator verification, and strict dependency verification. Separately, ACL enforcement is not guaranteed by the current source; clean-user runtime/sync and uninstall/reinstall evidence remain open. The recorded MSI is unsigned. See the [Project Roadmap](./project-roadmap.md) and [Windows Acceptance](./windows-acceptance.md).

---

## 2. Platform Sequence Enforcement
In accordance with architectural policy:
1. Android delivery and release operations precede desktop packaging.
2. Linux RPM distribution (Phase 09) commences only after Phase 08 tooling and release procedures are established.
3. Windows MSI distribution (Phase 10) executes following Linux distribution acceptance.
