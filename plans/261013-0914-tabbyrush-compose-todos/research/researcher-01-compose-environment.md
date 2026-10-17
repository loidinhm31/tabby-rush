# Research Report: Android & Compose Multiplatform Environment Setup

> REVIEW STATUS: Superseded draft. Do not execute the setup commands or adopt the version/module recommendations below. Parent review found outdated plugin structure, an incompatible version tuple, unsafe public Xpra binding, blanket license acceptance, and deprecated emulator flags. Use [authoritative corrections](../reports/design-decisions.md) and the final phase runbooks. Retained as research provenance, not approved guidance.

## 1. Toolchain & Compatibility Matrix

| Component | Target Version | Compatibility Pitfalls & Rules |
| :--- | :--- | :--- |
| **JDK** | OpenJDK 21 LTS | Host has OpenJDK 25. AGP 8.x and Gradle 8.x fail on Java 25. Install `java-21-openjdk-devel`. Set `JAVA_HOME` or Gradle toolchain to 21. |
| **Gradle** | 8.11.1 (bin) | Supports Kotlin 2.1, AGP 8.7.x, JDK 21. |
| **Kotlin** | 2.1.10 | Required for Compose Multiplatform 1.7.3+ and K2 compiler pipeline. |
| **Compose Multiplatform** | 1.7.3 / 1.8.x | Reuses Jetpack Compose UI code across Android and Desktop (JVM Skia). |
| **Compose Compiler** | Kotlin built-in | Applied via `org.jetbrains.kotlin.plugin.compose` (Kotlin 2.0+ decoupled compiler). |
| **AGP** | 8.7.3 | Compile SDK 35, Min SDK 24 (or 21), Java 21 compile target. |

## 2. Headless Fedora 44 Server Setup (Xpra & SDK)

### Remote Display & IDE
- **Xpra Server**: Install via `sudo dnf install -y xpra`. Headless display startup:
  `xpra start :100 --bind-tcp=0.0.0.0:10000 --html=on --exit-with-children=no`
  Access via browser (`http://<host>:10000`) or client `xpra attach ssh://loidinh@<host>:port/100`.
- **Android Studio (Optional GUI)**: Download Linux tarball, extract to `~/.local/share/android-studio`, run `DISPLAY=:100 ~/.local/share/android-studio/bin/studio.sh`. CLI-only workflow preferred.

### Headless Android SDK Installation
1. Install JDK 21: `sudo dnf install -y java-21-openjdk-devel`
2. Set environment:
   `export ANDROID_HOME=$HOME/Android/Sdk`
   `export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH`
3. Download Android `cmdline-tools` into `$ANDROID_HOME/cmdline-tools/latest`.
4. Accept licenses non-interactively: `yes | sdkmanager --licenses`
5. Install packages:
   `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" "system-images;android-35;google_apis;x86_64" "emulator"`

### KVM & Emulator Configuration
- Host AMD-V verified (`/dev/kvm` accessible, KVM API 12).
- Create AVD: `avdmanager create avd -n tabbyrush_api35 -k "system-images;android-35;google_apis;x86_64" --device "pixel_6"`
- **Graphics Pitfall**: Mesa Vulkan/Radeon 780M inside Xpra virtual X11 lacks native GL direct context. Starting emulator with `-gpu host` can crash GLX.
- **Remedy**: Use `-gpu swiftshader_indirect` (software rendering).
- **Headless / CI Boot**: `emulator -avd tabbyrush_api35 -no-window -no-audio -gpu swiftshader_indirect &`
- **Interactive UI via Xpra**: `DISPLAY=:100 emulator -avd tabbyrush_api35 -gpu swiftshader_indirect`
- **Physical Phone Alternative**: Connect via `adb tcpip 5555` / `adb connect <phone_ip>:5555`. Eliminates VM/rendering overhead on server.

### Local Dev Worker Loopback (10.0.2.2)
- Android Emulator routes host loopback (`127.0.0.1`) through `10.0.2.2`.
- Desktop JVM target routes through `127.0.0.1`.
- Physical phone uses `adb reverse tcp:8787 tcp:8787` (then accesses `127.0.0.1:8787`) or LAN IP.
- **Contract**: Multiplatform configuration provides `ApiConfig.baseUrl` dynamically per platform target.

## 3. Project Architecture & Module Contracts

```
tabbyrush/
├── composeApp/            # App entrypoints (Android Application + Desktop JVM main)
├── core/
│   ├── model/             # Pure Kotlin KMP: Data models, Todo schema, zero Android deps
│   ├── data/              # KMP: Ktor HTTP client, D1 Worker client, Repo interfaces
│   └── ui/                # KMP Compose: Material 3 UI components, theme, shared screens
├── gradle/wrapper/        # Gradle wrapper binaries & properties (8.11.1)
└── settings.gradle.kts
```

### Module Plugin Strategy
- `composeApp`: Applies `com.android.application`, `org.jetbrains.kotlin.multiplatform`, `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose`. Configures `androidTarget()` and `jvm("desktop")`.
- `core:ui`: Applies `com.android.library`, `org.jetbrains.kotlin.multiplatform`, `org.jetbrains.compose`, `org.jetbrains.kotlin.plugin.compose`. Pure Compose Multiplatform UI components.
- `core:data`: Pure `kotlin("multiplatform")` + `kotlinx.serialization` + `ktor-client-core` (with platform engines: `ktor-client-okhttp` for Android, `ktor-client-cio` or `java` for desktop). No AGP overhead.
- `core:model`: Pure `kotlin("multiplatform")`. Instant compile, fully isolated.
- **Wrapper Strategy**: Generate initial wrapper via `gradle wrapper --gradle-version 8.11.1`. Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

## 4. Desktop Packaging Strategy (Linux & Windows)

- **Compose Multiplatform Gradle Plugin**: Includes `compose.desktop.application.nativeDistributions`.
- **Linux Target (Current Fedora Host)**:
  - Run locally: `./gradlew :composeApp:run` (launches Skia window directly or inside Xpra).
  - Package RPM/DEB: `./gradlew :composeApp:packageRpm` using host `jpackage`.
- **Windows Target (Cross-compilation Limitation)**:
  - `jpackage` relies on OS-native toolchains (`WiX Toolset` on Windows) and **cannot cross-compile `.msi`/`.exe` from Linux**.
  - Strategy: Keep codebase 100% shared in Git. Execute `./gradlew :composeApp:packageMsi` on a secondary Windows workstation or GitHub Actions Windows runner.

## 5. Verification Matrix

| Target Platform | Runtime Environment | Build Command | Execution / Smoke Check |
| :--- | :--- | :--- | :--- |
| **Android (Emulator)** | Fedora headless (Xpra) | `./gradlew :composeApp:assembleDebug` | `adb install -r ...apk && adb shell am start -n com.tabbyrush/.MainActivity` |
| **Android (Physical)** | Android 10+ via Wi-Fi adb | `./gradlew :composeApp:installDebug` | Verify screen, check HTTP 127.0.0.1 via `adb reverse tcp:8787 tcp:8787` |
| **Linux Desktop** | Fedora headless (Xpra :100) | `./gradlew :composeApp:run` | Window appears in Xpra web/client; interactive CRUD |
| **Linux Distribution** | Fedora 44 | `./gradlew :composeApp:packageRpm` | `rpm -qip composeApp/build/compose/binaries/main/rpm/*.rpm` |
| **Windows Desktop** | Windows machine | `gradlew.bat :composeApp:packageMsi` | Launch generated MSI installer on Windows |

## 6. Effort Estimates & Prerequisites

| Phase | Scope | Est. Effort | Prerequisites |
| :--- | :--- | :--- | :--- |
| **P1: Host & Toolchain** | Install JDK 21, Xpra, Android SDK, cmdline-tools, licenses | 1.5 - 2.5 h | `sudo dnf` access for JDK 21 & Xpra |
| **P2: Project Scaffolding** | Gradle 8.11 wrapper, KMP root, `composeApp`, `core:*` | 1.5 - 2.0 h | JDK 21 installed |
| **P3: Emulator & Networking** | AVD creation, SwiftShader smoke test, 10.0.2.2 dev worker check | 1.0 - 1.5 h | KVM verified, SDK emulator installed |
| **P4: Shared Core & Data** | `core:model`, `core:data` Ktor client against dev Worker | 2.0 - 3.0 h | Dev Worker API contract frozen |
| **P5: Shared UI (CMP)** | Material 3 todo screens, theme adaptation | 3.0 - 4.5 h | Parent theme spec & Figma/ref review |
| **P6: Packaging** | Linux RPM verification, Windows packaging workflow doc | 1.0 - 1.5 h | Desktop runner working |

## 7. Primary Citations
- Compose Multiplatform Compatibility: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Android Gradle Plugin Compatibility: https://developer.android.com/build/releases/gradle-plugin
- Android Emulator Networking: https://developer.android.com/studio/run/emulator-networking
- Android SDK sdkmanager: https://developer.android.com/tools/sdkmanager
- Compose Desktop Native Packaging: https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md

## 8. Unresolved Questions
1. Sudo access confirmation: Will user run `sudo dnf install java-21-openjdk-devel xpra` or should local portable JDK 21 and user-space tooling be unpacked in `$HOME`?
2. Primary testing device: Will user prefer testing Android on an interactive physical device (via adb Wi-Fi) to save server RAM/CPU, or purely through Xpra emulator?
3. Windows packaging pipeline: Should Windows release builds be automated via GitHub Actions or manual checkout on the secondary Windows machine?
