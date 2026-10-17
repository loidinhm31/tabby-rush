# Virtual Screen Feasibility Assessment for TabbyRush Compose Todos

Date: 2026-10-13
Target Plan: `plans/261013-0914-tabbyrush-compose-todos/`
Status: Feasible for Linux/Android UI phases; Infeasible for Windows distribution; Plan itself is superseded.

## 1. Executive Verdict
- **Can a virtual screen be used? YES** for the headless server implementation and verification of Linux and Android components (Phases 01, 06, 07, 09). In fact, `phase-01-headless-tooling.md` and `phase-09-linux-distribution.md` explicitly mandate a virtual display architecture (`Xpra :100` over SSH / `Xvfb`) to operate on this headless Fedora 44 host without desktop environments.
- **Can a virtual screen complete the entire plan? NO**, because Phase 10 (Native Windows MSI) requires a physical/native Windows workstation. WiX toolsets and `jpackage` MSI bundling cannot cross-compile or run on Linux virtual displays.
- **Plan Status Warning**: Plan `261013-0914-tabbyrush-compose-todos` is marked **HISTORICAL / SUPERSEDED** by `plans/261013-1540-tabbyrush-glean-hub-reuse/` following user validation (which rejected the shared-token online-first model in favor of multi-user accounts and offline sync). The virtual screen architecture remains valid for the active successor plan.

## 2. Phase-by-Phase Feasibility Breakdown

| Phase | Virtual Screen Role | Feasible? | Details / Constraints |
|---|---|---|---|
| **01 Headless Tooling** | Android Studio wizard & AVD boot | **YES** | Plan specifies `xpra start :100 --start-child=xterm`. `xpra` 6.5.4 and `/usr/bin/Xvfb` are pre-installed. KVM API 12 accessible (`/dev/kvm` rw). |
| **02 Frozen Foundation** | Build & tooling verification | **N/A** | Headless CLI (Gradle wrapper, catalog, locks). Display not required. |
| **03 D1 Migrations** | Local SQL migration tests | **N/A** | Headless Node.js 24 + Wrangler CLI. Display not required. |
| **04 Worker API** | TypeScript Worker service | **N/A** | Headless Node.js/Wrangler runtime on 127.0.0.1:8787. Display not required. |
| **05 Shared Data/State** | KMP repository & state engine | **N/A** | Headless JVM unit tests (`./gradlew :shared:allTests`). Display not required. |
| **06 Compose UI/Theme** | Shared Compose UI & semantics | **YES** | Common composables and semantic tests run in JVM test runner; visual checks render in virtual X11 screen. |
| **07 Android Integration** | Android app & local Worker gate | **YES** | AVD boots in virtual display (`emulator -gpu swiftshader` or auto). Visual evidence via `adb exec-out screencap -p` and Xpra attach. |
| **08 Deployment & Release** | Production Worker & APK signing | **N/A** | Headless CLI operations. Signed APK verified via ADB. |
| **09 Linux Desktop (RPM)** | Compose Desktop JVM & RPM test | **YES** | Skiko/Compose Desktop requires X11 (`DISPLAY=:100`). Runs under Xpra/Xvfb with llvmpipe/software rendering. Screen captures captured via Xpra/import. |
| **10 Windows Distribution** | Native Windows MSI packaging | **NO** | OS-bound (`jpackage` + WiX Toolset on Windows OS). Cannot be built or installed on Linux virtual screen. |

## 3. Host Environment Evidence
- **Host OS**: Fedora Linux 44 Server (`x86_64`, kernel 7.1.10).
- **Installed Display Servers**: `xpra-6.5.4-1.fc44.x86_64` (`/usr/bin/xpra`), `xorg-x11-server-Xvfb-21.1.24-1.fc44.x86_64` (`/usr/bin/Xvfb`), `/usr/bin/vncserver`.
- **KVM Acceleration**: `/dev/kvm` exists with read-write permissions (`crw-rw-rw-+`).
- **GPU / Rendering**: AMD Radeon 780M (`HawkPoint1 [1002:1900]`). In virtual X11 environments, direct hardware GLX/DRI3 may be limited; SwiftShader (`-gpu swiftshader`) and Mesa llvmpipe provide verified software rendering fallback.

## 4. Key Risks & Operational Requirements
1. **GPU Context inside Virtual Screen**: Direct DRI3 hardware acceleration under Xpra/Xvfb can fail with Mesa drivers. The plan accounts for this by defaulting to `auto` or fallback to `swiftshader`.
2. **Phase 10 Gap**: A separate Windows machine or Windows runner remains mandatory for the MSI deliverable.
3. **Plan Superseded**: Do not execute implementation against `261013-0914-tabbyrush-compose-todos`; apply virtual screen workflows to `261013-1540-tabbyrush-glean-hub-reuse` when authorized.

## 5. Unresolved Questions
1. Does the workstation operator intend to attach interactively via `xpra attach ssh://.../100` or rely exclusively on automated headless captures (`adb exec-out screencap -p` and `xpra screenshot` / Xvfb)?
2. Will Windows MSI validation (Phase 10) be performed on a dedicated secondary Windows workstation or an automated CI runner?
