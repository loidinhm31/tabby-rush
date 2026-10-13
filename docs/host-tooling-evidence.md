# Host Tooling & Virtualization Evidence

Date: 2026-10-13  
Host: Fedora Linux 44 Server (x86_64)  
Environment: Headless Server (no desktop environment installed)  
Related Docs: [Headless Android Setup](./headless-android-setup.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. System & Hardware Specifications

| Component | Verified Specification |
|---|---|
| **OS** | Fedora Linux 44 Server |
| **Kernel** | `7.1.10-200.fc44.x86_64` |
| **Architecture** | `x86_64` (AMD64) |
| **CPU Virtualization** | AMD-V enabled; `/dev/kvm` accessible (`crw-rw-rw-+`) |
| **KVM API** | Version 12 verified |
| **GPU Controller** | AMD Radeon 780M (`HawkPoint1 [1002:1900] rev 01`) |

---

## 2. Display & Virtualization Tooling

| Binary | Installed Path | Version / Package | Status |
|---|---|---|---|
| `xpra` | `/usr/bin/xpra` | `6.5.4-1.fc44.x86_64` | Ready |
| `xterm` | `/usr/bin/xterm` | System package | Ready |
| Xvfb | `/usr/bin/Xvfb` | `21.1.24-1.fc44.x86_64` | Ready |
| vncserver | `/usr/bin/vncserver` | System package | Available fallback |

### Remote Display Configuration
- **Socket Path**: User-owned runtime dir (`/run/user/$(id -u)/xpra/`)
- **Transport**: SSH tunnel (`ssh://user@host/100`)
- **Flags**: `--start-child=xterm --exit-with-children=no --html=off --mdns=no`
- **Security Check**: Public TCP binding disabled (`--bind-tcp` omitted); mDNS discovery disabled (`--mdns=no`).

### Verified Smoke Test Evidence
- **Live Session Smoke (Display `:199`)**: Executed during review cycle 3 (`plans/reports/code-reviewer-261013-2314-phase-01-headless-tooling-cycle3.md`) using `scripts/start-xpra.sh`. Verified process startup (xpra, Xorg, xterm), confirmed session active in `xpra list`, verified zero listening TCP sockets associated with Xpra via `ss -ltn` (system port 5353 belongs to host Avahi daemon; Xpra was started with `--mdns=no`), and verified clean shutdown via `start-xpra.sh stop`.
- **Environment Installation & Graphical Acceptance**: Android Studio download, SDK command-line tools, platform tools (`adb`), emulator binary, AVD (`doggo_android`), and visual inspection of the emulator framebuffer remain **PENDING** interactive workstation operator execution.
---

## 3. Runtimes & Development Tools

| Runtime | Path | Active Version | Status / Notes |
|---|---|---|---|
| **Java** | `/usr/bin/java` | OpenJDK 25.0.4.1 | Host system default; `scripts/android-sdk-env.sh` leaves `JAVA_HOME` unset if only Java 25 is present. AGP requires JDK 21 LTS or Studio JBR. |
| **Node.js** | `~/.nvm/versions/node/v24.16.0/bin/node` | v24.16.0 | Meets Node 24 LTS requirement |
| **KVM** | `/dev/kvm` | rw permissions (0666) | Host-observed permissions; no chmod on system device state |
| **adb** | Unset | Not yet installed | Pending interactive SDK platform-tools install |
| **emulator** | Unset | Not yet installed | Pending interactive SDK emulator package install |
| **Android Studio**| Unset | Not yet installed | Pending interactive download & extraction to `~/.local/share/android-studio` |
| **AVD** | Unset | Not yet created | Planned device: `doggo_android` (`pixel_6`, API 35 image) |
| **Screenshot** | `docs/evidence/android-tooling-launcher.png` | Not yet captured | Pending visual emulator session |

---

## 4. Android SDK Target Directory Structure

Configured via `scripts/android-sdk-env.sh`:
- **`ANDROID_HOME`**: `$HOME/Android/Sdk`
- **`cmdline-tools`**: `$ANDROID_HOME/cmdline-tools/latest/bin`
- **`platform-tools`**: `$ANDROID_HOME/platform-tools`
- **`emulator`**: `$ANDROID_HOME/emulator`
- **Candidate Studio Path**: `$HOME/.local/share/android-studio`

---

## 5. Security & Isolation Invariants

1. **No Desktop Environment**: Multi-user systemd target maintained; GNOME/KDE packages not installed.
2. **Encrypted Remote Transport**: GUI access restricted to SSH-tunneled Xpra.
3. **No Local Network Discovery**: Xpra mDNS broadcast disabled (`--mdns=no`).
4. **No Unauthenticated ADB**: Legacy `adb tcpip 5555` port forbidden.
5. **No Blanket Licensing**: `sdkmanager --licenses` requires interactive acceptance.
6. **Device State Integrity**: `/dev/kvm` permissions are system-managed (`0666`); never modified via `chmod`.

---

## 6. Automated Validation Summary

Automated checks executed via `tests/test-phase-01-tooling.sh`:
- **Syntax & Permissions**: `bash -n` and executable bits verified across all scripts.
- **CLI Handling**: `--help`, status commands, invalid/odd port rejection (`5555`, `08`, `05554`), and missing binary error exits verified.
- **Host Prerequisites**: `/dev/kvm` read/write access, `xpra`, `xterm`, `java`, and `node` verified present on host.
- **Documentation Contracts**: Setup runbook and evidence document structure and security flags verified.
- **Validation Scope Boundary**: Test suite validates syntax and configuration integrity; full Phase 01 visual acceptance requires subsequent interactive session verification.
