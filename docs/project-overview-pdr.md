# Doggo Project Overview & Product Development Requirements (PDR)

Last Updated: 2026-10-13  
Document Version: 1.0.0  
Status: Active  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md) · [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md)

---

## 1. Executive Summary & Product Vision

Doggo is an Android-first shared Compose Multiplatform application designed for fast, focused personal task management with multi-device cloud synchronization. 

The project prioritizes a clean multiplatform architecture, where the core domain logic, state management, and user interface are planned in Kotlin Multiplatform and Compose Multiplatform:
1. **Android Application (`androidApp/`)**: Planned primary mobile target (Phase 07).
2. **Desktop Application (`desktopApp/`)**: Planned desktop targets for native Linux RPM (Phase 09) and Windows MSI (Phase 10).
3. **Headless Development Infrastructure**: Immediate development, IDE execution, and emulator testing on headless Fedora Linux servers via secure Xpra virtual display sessions (delivered in Phase 01).

---

## 2. Product Development Requirements (PDR)

### 2.1 Functional Requirements (FR)

| ID | Requirement | Description |
|---|---|---|
| **FR-01** | Task Lifecycle | Users can create, view, edit title, toggle completion, and delete task items. |
| **FR-02** | Task Filtering | Users can filter tasks by status (All, Active, Completed) with immediate client-side query response. |
| **FR-03** | Aggregate Metrics | The UI displays total, active, and completed task counts dynamically reflecting current filters. |
| **FR-04** | Cloud Synchronization | Client syncs tasks with the backend service according to the contracts frozen in Phase 02. |
| **FR-05** | Local State Handling | Task mutations update local state; pending drafts survive transient network faults. |
| **FR-06** | Secure Authentication | Credentials and tokens are managed securely without plaintext storage in source or logs. |

### 2.2 Non-Functional Requirements (NFR)

| ID | Category | Requirement |
|---|---|---|
| **NFR-01** | Performance | Smooth, responsive UI interactions without main-thread blocking during network or disk I/O. |
| **NFR-02** | Security | Zero unauthenticated network listeners; HTTPS-only external transport; encrypted credential storage; no secrets in logs or code. |
| **NFR-03** | Portability | 100% shared Compose Multiplatform UI across Android and JVM desktop targets. |
| **NFR-04** | Maintainability | Strict package modularity, unidirectional data flow, immutable state models, and automated test coverage. |
| **NFR-05** | Reliability | Bounded network timeouts, deterministic error envelopes, and graceful handling of network transitions. |

---

## 3. Phase 01 Requirements & Acceptance: Headless Tooling & Environment

Phase 01 delivers the foundational headless virtualization and Android development tooling required to develop and validate the multiplatform application on a headless Fedora Linux host.

### 3.1 Headless Infrastructure Requirements
- **FR-ENV-01 (Virtual Display)**: Provide an isolated virtual X11 display (`:100`) via Xpra, accessible remotely only over encrypted SSH tunnels.
- **FR-ENV-02 (Security Invariants)**: Virtual display must not bind public TCP ports (`--bind-tcp` omitted), must disable HTML5 web clients (`--html=off`), and must disable mDNS broadcast (`--mdns=no`).
- **FR-ENV-03 (Display Authentication)**: Xpra must spawn an authenticated child `xterm` ensuring child processes inherit valid `XAUTHORITY` credentials.
- **FR-ENV-04 (SDK Environment)**: Provide an idempotent shell configuration script that exports `ANDROID_HOME`, adds SDK tools to `PATH`, detects compatible JDKs, and remains silent in non-interactive sessions.
- **FR-ENV-05 (Emulator Lifecycle)**: Provide an emulator startup script that validates port bounds (even integers 5554–5682), checks KVM virtualization, and supports both hardware acceleration and SwiftShader fallback.

### 3.2 Phase 01 Deliverables & Status Distinction

| Deliverable | Implementation Scope | Verification Status |
|---|---|---|
| `scripts/start-xpra.sh` | Starts/stops Xpra display `:100` cleanly without TCP exposure. | Script implemented; smoke test on `:199` verified zero TCP listeners via `ss -ltn`. |
| `scripts/android-sdk-env.sh` | Exports SDK paths, handles Java 25 / JDK 21 detection safely. | Script implemented; user overrides and non-interactive silence verified. |
| `scripts/start-android-emulator.sh` | Enforces port bounds, verifies `/dev/kvm`, handles missing binaries. | Script implemented; odd port, octal ports, and missing binary errors tested. |
| `docs/headless-android-setup.md` | Operations runbook with bounded boot checks and checksums. | Runbook published; wall-clock bounded `wait_for_emulator_boot` helper documented. |
| `docs/host-tooling-evidence.md` | Host capabilities and pending inventory recorded. | Published; AMD-V, KVM API 12, Xpra 6.5.4, Node v24 LTS verified; pending items explicit. |
| `tests/test-phase-01-tooling.sh` | Automated test suite exercising all script and doc contracts. | Suite implemented; 31/31 assertions passing cleanly. |
| **Runtime Acceptance Gate** | Interactive Studio GUI, SDK install, AVD creation, launcher screenshot. | **PENDING**: Requires interactive workstation operator attachment via `xpra attach`. |

---

## 4. Technical Constraints & Dependencies

1. **Host Environment**: Fedora Linux 44 Server (`x86_64`, kernel `7.1.10`), AMD-V hardware virtualization enabled.
2. **JDK Requirements**: AGP 8.x requires a compatible JDK (JDK 17 or JDK 21 LTS). System default Java 25 is not verified for AGP Gradle tasks; Studio bundled JBR or `java-21-openjdk-devel` is required.
3. **Interactive Licensing**: Android SDK licenses must be accepted interactively via `sdkmanager --licenses`; automated blanket acceptance is prohibited.
4. **Interactive GUI Acceptance**: Full visual verification of Android Studio and the emulator framebuffer requires interactive remote workstation attachment via `xpra attach ssh://.../100`.

---

## 5. Traceability & Phased Roadmap

Progress across the Doggo project is tracked in the living [Project Roadmap](./project-roadmap.md):
- **Phase 01 Tooling & Environment**: Scripts and documentation implemented (DONE); runtime GUI gate pending interactive attachment.
- **Phase 02 (Pending)**: Frozen foundation, Gradle multiplatform configuration, and contract freeze.
- **Phases 03–06 (Pending)**: Parallel implementation lanes for data models, backend API, local persistence, and Compose UI.
- **Phases 07–08 (Pending)**: Android integration, connected emulator testing, and release operations.
- **Phases 09–10 (Pending)**: Linux RPM and Windows MSI packaging.
