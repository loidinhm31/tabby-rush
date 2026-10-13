# Doggo System Architecture

Last Updated: 2026-10-13  
Phase: Phase 01 Headless Tooling & Android Environment  
Related Docs: [Headless Android Setup](./headless-android-setup.md) · [Host Tooling Evidence](./host-tooling-evidence.md) · [Project Roadmap](./project-roadmap.md) · [Codebase Summary](./codebase-summary.md)

---

## 1. Architectural Scope & Principles

Doggo is designed as an Android-first shared Compose Multiplatform application developed on a headless Linux host. Development and validation rely on an isolated virtualization architecture that enables full graphical IDE and Android emulator capabilities without installing desktop environments (GNOME/KDE) or exposing insecure network services.

### Core Architectural Principles
1. **Headless Isolation**: The host operates in headless server boot mode. Graphical access is strictly encapsulated within user-isolated virtual display servers.
2. **Encrypted Transport Only**: All remote graphical interaction transits SSH tunnels with out-of-band host key verification. Zero public TCP listeners are permitted.
3. **Hardware-Accelerated Emulation**: Android virtual devices leverage host Linux KVM (`/dev/kvm`) acceleration directly without compromising system device permissions.
4. **Idempotent Tooling**: Host management scripts are deterministic, side-effect free on error, and safe for automated or repetitive execution.

---

## 2. Infrastructure & Virtualization Architecture

### Component Topology

```mermaid
flowchart TD
  subgraph LocalWorkstation["Remote Developer Workstation"]
    Client["Xpra Client / SSH Client"]
  end

  subgraph SSHBoundary["Network Boundary (Port 22 SSH)"]
    Tunnel["Encrypted SSH Tunnel"]
  end

  subgraph HostServer["Fedora 44 Server (Headless)"]
    subgraph XpraSubsystem["Virtual Display Subsystem (:100)"]
      XpraServer["Xpra Display Daemon\n(--html=off, --mdns=no)"]
      UnixSocket["User Socket: /run/user/UID/xpra/100"]
      XtermSession["Child xterm Shell\n(Authenticates XAUTHORITY)"]
    end

    subgraph AndroidSubsystem["Android Tooling Environment"]
      Studio["Android Studio GUI\n(~/.local/share/android-studio)"]
      Emulator["Android Emulator\n(Port 5554, AVD: doggo_android)"]
      SDK["Android SDK\n($HOME/Android/Sdk)"]
    end

    subgraph KernelResources["Host Hardware & Kernel"]
      KVM["KVM Hardware Accel\n(/dev/kvm, AMD-V, API 12)"]
      GPU["GPU Controller\n(AMD Radeon 780M / SwiftShader)"]
    end
  end

  Client -->|SSH Tunnel| Tunnel
  Tunnel -->|SSH Forward| XpraServer
  XpraServer --- UnixSocket
  XpraServer --> XtermSession
  XtermSession --> Studio
  XtermSession --> Emulator
  Emulator --> KVM
  Emulator --> GPU
  Studio --> SDK
  Emulator --> SDK
```

---

## 3. Subsystem Breakdown

### 3.1 Remote Display Subsystem (Xpra)

The remote display architecture decouples graphical application execution from the host operating system's desktop stack:
- **Display Identifier**: Dedicated display `:100` managed by `scripts/start-xpra.sh`.
- **Socket Isolation**: UNIX domain socket located in user-specific `/run/user/$(id -u)/xpra/` directory (mode `0700`), inaccessible to other system users.
- **Security Flags**:
  - `--bind-tcp` omitted: completely disables direct TCP network socket creation.
  - `--html=off`: disables internal WebSockets/HTTP server to prevent browser-based unauthenticated access.
  - `--mdns=no`: disables local network broadcast (Avahi/Bonjour).
- **Authentication Inheritance**: Xpra launches an authenticated child `xterm` session. Graphical processes spawned from this shell inherit valid `DISPLAY` and `XAUTHORITY` credentials.

### 3.2 Android SDK & Tooling Subsystem

Configured through `scripts/android-sdk-env.sh`:
- **`ANDROID_HOME`**: Points to `$HOME/Android/Sdk`, accommodating command-line tools, platform tools, build tools, platforms, and system images.
- **Tool Resolution**: Adds `cmdline-tools/latest/bin`, `platform-tools`, and `emulator` to `PATH` without duplicating entries.
- **Java Runtime Compatibility**:
  - Respects existing `JAVA_HOME` if defined by the operator.
  - Automatically identifies compatible Studio JBR (`$HOME/.local/share/android-studio/jbr`) or system JDK 21 LTS (`/usr/lib/jvm/java-21-openjdk`).
  - Leaves `JAVA_HOME` unset if only host Java 25 is present, providing non-interactive safety and interactive configuration guidance.

### 3.3 Emulator Subsystem

Managed via `scripts/start-android-emulator.sh`:
- **Port Allocation**: Strictly bounded to decimal even integers in the range `5554` to `5682`, matching standard ADB console/ADB port pairs (`emulator-5554`). Leading-zero octal numbers and odd ports are rejected.
- **Hardware Acceleration**: Checks `/dev/kvm` read/write access. Relies on AMD-V CPU virtualization (KVM API 12).
- **Rendering Fallback**: Defaults to `-gpu auto`, allowing selection of software rasterization (`-gpu swiftshader`) if GLX rendering encounters issues under remote display forwarding.
- **Headless Mode**: Supports `EMULATOR_HEADLESS=1` (`-no-window -no-audio`) for automated CI runs.

---

## 4. Multiplatform Application Roadmap Architecture

Beyond Phase 01 tooling, Doggo follows a phased multiplatform application architecture:

```mermaid
flowchart LR
  subgraph Clients["Client Applications"]
    AndroidApp["Android App\n(dev.doggo.android)"]
    DesktopApp["Desktop App\n(dev.doggo.desktop)"]
  end

  subgraph SharedCore["Shared Kotlin Multiplatform Core"]
    UI["Shared Compose UI & Theme"]
    State["State & Task Controllers"]
    Data["Data Layer & Local SQLite"]
    Sync["Sync Adapter & Network Client"]
  end

  subgraph Backend["Backend Services"]
    SyncEndpoint["Cloud Sync Endpoint"]
    Database["Persistent Cloud Storage"]
  end

  AndroidApp --> UI
  DesktopApp --> UI
  UI --> State
  State --> Data
  Data --> Sync
  Sync -->|Authenticated HTTPS| SyncEndpoint
  SyncEndpoint --> Database
```

- **Shared Core (`shared/`)**: Planned Kotlin Multiplatform library targeting Android and JVM. Contracts and version tuples to be frozen in Phase 02.
- **Android Target (`androidApp/`)**: Planned Android application module hosting MainActivity and platform integrations (Phase 07).
- **Desktop Target (`desktopApp/`)**: Planned JVM desktop application module with packaging for native Linux RPM (Phase 09) and Windows MSI (Phase 10).

---

## 5. Security & Isolation Invariants

1. **Virtual Display Network Policy & Observation**: Script policy omits `--bind-tcp` and specifies `--html=off --mdns=no`. A smoke test on display `:199` documented in review cycle 3 (`plans/reports/code-reviewer-261013-2314-phase-01-headless-tooling-cycle3.md`) observed zero listening TCP sockets associated with the Xpra daemon process via `ss -ltn`. System services (such as Avahi daemon on UDP 5353) are host-level services independent of Xpra.
2. **Device Nodes**: `/dev/kvm` permissions (`0666` observed on host) are managed by system udev rules; scripts do not run `chmod` or `chown` on system devices.
3. **Privilege Separation**: Android Studio, the emulator, and all development scripts execute as an unprivileged user (`loidinh`), never as root.
4. **Credential Safety**: No authentication tokens, keystores, or device identifiers may be committed to version control; `local.properties` must be excluded via `.gitignore`.
