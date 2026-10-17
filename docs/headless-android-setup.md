# Headless Android Tooling & Emulator Setup

This runbook documents the configuration and execution of the Android SDK, Android Studio, and Android Emulator on a headless Fedora 44 Server without installing a desktop environment.

Related documentation and artifacts:
- [Host Tooling & Virtualization Evidence](./host-tooling-evidence.md)
- [Project Roadmap](./project-roadmap.md)
- Scripts: `scripts/start-xpra.sh`, `scripts/android-sdk-env.sh`, `scripts/start-android-emulator.sh`
- Automated Test Suite: `tests/test-phase-01-tooling.sh`

---

## 1. Architecture Overview

```
[ Remote Workstation ]
        │  SSH tunnel (verified host key, port 22)
        ▼
[ Fedora 44 Server (Headless) ]
        │
        ├── Xpra Server (:100 virtual X11 display)
        │     ├── xterm (authenticated session shell)
        │     ├── Android Studio GUI (~/.local/share/android-studio/bin/studio)
        │     └── Android Emulator (port 5554, windowed via Xpra)
        │
        └── Host Resources
              ├── KVM hardware acceleration (/dev/kvm)
              └── Android SDK ($HOME/Android/Sdk)
```

- **Transport**: SSH-only. No public TCP listeners (`--bind-tcp` prohibited), HTML5 client disabled (`--html=off`), and local network discovery disabled (`--mdns=no`).
- **Display**: Virtual display `:100` managed by Xpra.
- **Boot Mode**: Fedora server multi-user target retained; no GNOME/KDE packages.

---

## 2. Remote Display Setup (Xpra)

### Host Key Verification
Before connecting, verify the SSH host key fingerprint via an out-of-band trusted administrator channel:
```bash
ssh-keygen -l -f /etc/ssh/ssh_host_ed25519_key.pub
```

### Start Virtual Display
On the server:
```bash
./scripts/start-xpra.sh start
```
Starts `xpra` on display `:100` with child `xterm`, `--html=off`, and `--mdns=no`.

### Attach from Graphical Workstation
From your local workstation:
```bash
xpra attach ssh://<username>@<server-ip>/100
```
This opens the remote `xterm` window on your desktop. Run graphical tools (Android Studio, emulator) directly inside this authenticated `xterm` session.

> **Important**: Launching GUI tools directly inside the Xpra `xterm` shell ensures the process inherits valid display socket authority (`XAUTHORITY`). Setting `DISPLAY=:100` in an unauthenticated SSH shell without Xpra authorization cookies will fail.

### Stop Virtual Display
```bash
./scripts/start-xpra.sh stop
```

---

## 3. Environment & Java Configuration

Source the environment script in your shell:
```bash
source ./scripts/android-sdk-env.sh
```

- Preserves existing `ANDROID_HOME` if set; otherwise exports `ANDROID_HOME="$HOME/Android/Sdk"` (and mirrors to `ANDROID_SDK_ROOT`).
- Prepends `cmdline-tools/latest/bin`, `platform-tools`, and `emulator` to `PATH` if not already present.
- Preserves user-defined `JAVA_HOME` if already exported.
- If `JAVA_HOME` is unset, attempts automated detection:
  1. Prefers Studio bundled JBR at `$HOME/.local/share/android-studio/jbr` if present.
  2. Falls back to `/usr/lib/jvm/java-21-openjdk` if present.
  3. If host default `java` on `PATH` is not JDK 21 (e.g., host OpenJDK 25), leaves `JAVA_HOME` unset and emits an informational guidance notice.
- Note: Setting `JAVA_HOME` supplies the JDK path for Gradle and tooling scripts, but does not alter the system default `java` binary priority on `PATH`. AGP 8.x requires a compatible JDK (typically JDK 17 or JDK 21 LTS); host Java 25 is not verified for AGP 8.x Gradle tasks.
- Status banner is suppressed in non-interactive sessions to avoid interfering with `scp`, `sftp`, and automated remote pipelines.
---

## 4. Android SDK & Studio Installation

### Android Studio Setup
1. Download Linux tarball from https://developer.android.com/studio.
2. Verify official SHA-256 checksum published on Google download page:
   ```bash
   echo "<expected_sha256>  android-studio-<version>-linux.tar.gz" | sha256sum -c -
   ```
3. Extract to user-owned path:
   ```bash
   mkdir -p "$HOME/.local/share/android-studio"
   tar -xzf android-studio-*.tar.gz -C "$HOME/.local/share/android-studio" --strip-components=1
   ```
4. In the Xpra xterm, launch Studio:
   ```bash
   "$HOME/.local/share/android-studio/bin/studio"
   ```

### Command-line Tools Setup
Alternatively, setup headless CLI tools:
1. Download `commandlinetools-linux-*_latest.zip` from https://developer.android.com/studio#command-line-tools-only.
2. Verify official SHA-256 checksum:
   ```bash
   echo "<expected_sha256>  commandlinetools-linux-<version>_latest.zip" | sha256sum -c -
   ```
3. Unpack to `$HOME/Android/Sdk/cmdline-tools/latest/`:
   ```bash
   mkdir -p "$HOME/Android/Sdk/cmdline-tools/latest"
   unzip -q commandlinetools-linux-*.zip -d "$HOME/Android/Sdk/cmdline-tools/temp"
   mv "$HOME/Android/Sdk/cmdline-tools/temp/cmdline-tools/"* "$HOME/Android/Sdk/cmdline-tools/latest/"
   rm -rf "$HOME/Android/Sdk/cmdline-tools/temp"
   ```

### Installing SDK Packages
Inspect available packages:
```bash
sdkmanager --list
```
Install required platform, build tools, and system image (illustrative baseline; verify availability via `sdkmanager --list`):
```bash
sdkmanager "platform-tools" \
           "emulator" \
           "platforms;android-35" \
           "build-tools;35.0.0" \
           "system-images;android-35;google_apis;x86_64"
```

### Reviewing Licenses
Run license review interactively (blanket `yes` piping is prohibited):
```bash
sdkmanager --licenses
```
Review and accept each license terms deliberately.

---

## 5. Android Virtual Device (AVD)

### Create AVD
Inspect device definitions to select a device ID (e.g., `pixel_6`):
```bash
avdmanager list device
avdmanager create avd \
  --name tabbyrush_android \
  --package "system-images;android-35;google_apis;x86_64" \
  --device "pixel_6"
```

### Launch Emulator
Launch inside the Xpra session:
```bash
./scripts/start-android-emulator.sh tabbyrush_android
```

- **GPU Acceleration**: Defaults to `-gpu auto`. If GLX errors occur under Xpra, set `EMULATOR_GPU=swiftshader` for software rasterization.
- **Headless Automation**: For non-interactive headless testing, set `EMULATOR_HEADLESS=1`.

### Verify Boot Completion (Bounded Shell Function with 120s Nominal Budget + Termination Overhead)
```bash
wait_for_emulator_boot() {
  local serial="${1:-emulator-5554}"
  local max_wait=120
  local start_time
  start_time="$(date +%s)"
  local deadline=$((start_time + max_wait))

  echo "Waiting for device ${serial} (overall deadline: ${max_wait}s)..."
  local remaining=$((deadline - $(date +%s)))
  if [ "${remaining}" -le 0 ] || ! timeout --kill-after=5s "${remaining}" adb -s "${serial}" wait-for-device; then
    echo "Error: Timed out waiting for ${serial} device connection within ${max_wait}s." >&2
    return 1
  fi

  echo "Device ${serial} connected. Polling sys.boot_completed..."
  while true; do
    local now
    now="$(date +%s)"
    remaining=$((deadline - now))
    if [ "${remaining}" -le 0 ]; then
      echo "Error: Timed out waiting for ${serial} boot completion after ${max_wait}s total." >&2
      return 1
    fi
    local cmd_timeout=5
    if [ "${remaining}" -lt "${cmd_timeout}" ]; then
      cmd_timeout="${remaining}"
    fi

    local boot_status
    boot_status="$(timeout --kill-after=2s "${cmd_timeout}" adb -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "${boot_status}" == "1" ]]; then
      local elapsed=$(($(date +%s) - start_time))
      echo "Emulator ${serial} boot completed successfully in ~${elapsed}s."
      return 0
    fi
    sleep 2
  done
}
wait_for_emulator_boot emulator-5554
```

---

## 6. Physical Device Alternative

If virtualization is constrained:
1. Connect device via USB and accept RSA key prompt.
2. Verify connection:
   ```bash
   adb devices -l
   ```
3. Reverse local debug service port for local integration testing:
   ```bash
   adb -s <SERIAL> reverse tcp:8787 tcp:8787
   ```
   Remove the port reverse rule once verification completes:
   ```bash
   adb -s <SERIAL> reverse --remove tcp:8787
   ```
4. Note: Legacy unauthenticated `adb tcpip 5555` is strictly forbidden. Use modern `adb pair` and `adb connect` if using Wi-Fi debugging on trusted networks.
---

## 7. Security Invariants & Operational Notes
1. Never bind Xpra or ADB ports to `0.0.0.0` or public network interfaces.
2. Do not broadcast Xpra sessions via mDNS (`--mdns=no` flag enforced in scripts).
3. Never commit `local.properties` or generated keystores; ensure `local.properties` is listed in `.gitignore`.
4. Host virtualization device (`/dev/kvm`) permissions are system-managed (`0666` observed on host); do not modify system device state with `chmod`.
5. Operational Status: Phase 01 automation scripts and technical documentation are completed and verified. Host environment installation (downloading Android Studio, installing SDK tools and system images, creating the AVD, and capturing visual framebuffer evidence) remains pending interactive workstation operator execution.
