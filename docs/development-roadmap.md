# Doggo Development Roadmap

**Document Version**: 1.1.0  
**Last Updated**: 2026-10-14  
**Primary Reference**: [Project Roadmap](./project-roadmap.md) · [Project Overview PDR](./project-overview-pdr.md)

---

## 1. Multiplatform Milestone Overview

| Milestone | Group | Target Deliverable | Phase Links | Status |
|---|:---:|---|---|:---:|
| **Milestone 1** | A | Headless Tooling & Display Virtualization | [Phase 01](../plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md) | **DONE** |
| **Milestone 2** | B & C | Contracts, Strict Hub Server, Local Sync & Light UI | [Phase 02](../plans/261013-1540-doggo-glean-hub-reuse/phase-02-frozen-foundation.md) to [Phase 06](../plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md) | **DONE** |
| **Milestone 3** | D | Android Composition & Runtime Integration | [Phase 07](../plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md) | **Source Delivered** |
| **Milestone 4** | E | Authorized Hub Operations & Signed Android Release | [Phase 08](../plans/261013-1540-doggo-glean-hub-reuse/phase-08-android-release-operations.md) | **Tooling Operational\*** |
| **Milestone 5** | F | Linux Desktop Distribution (Native RPM & Secret Service) | [Phase 09](../plans/261013-1540-doggo-glean-hub-reuse/phase-09-linux-distribution.md) | **Planned** |
| **Milestone 6** | G | Windows Desktop Distribution (Native MSI & DPAPI) | [Phase 10](../plans/261013-1540-doggo-glean-hub-reuse/phase-10-windows-distribution.md) | **Planned** |

*\*Tooling, security audit scripts, disaster recovery runbooks, and offline test harnesses are fully implemented. Live compilation and device acceptance require operator SDK setup and Phase 07 defect remediation.*

---

## 2. Platform Sequence Enforcement
In accordance with architectural policy:
1. Android delivery and release operations precede desktop packaging.
2. Linux RPM distribution (Phase 09) commences only after Phase 08 tooling and release procedures are established.
3. Windows MSI distribution (Phase 10) executes following Linux distribution acceptance.
