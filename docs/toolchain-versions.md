# TabbyRush Frozen Toolchain Versions & Compatibility Tuple

Date: 2026-10-17  
Phase: Sticky Task Surfaces Phase 07 delivered / documentation & automated verification evidenced; native Android/Windows acceptance conditionally held at Gate G2; Linux acceptance pending Phase 08
Plan: `plans/261016-1409-sticky-task-surfaces` (and `plans/261013-1540-tabbyrush-glean-hub-reuse`)  
Related Docs: [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [API Contract](./api-contract.md) · [Foundation Evidence](./foundation-evidence.md)

---

## 1. Authoritative Toolchain Tuple

The following version tuple is frozen for Phase 02 and governs all downstream implementation lanes (Phase 03 through Phase 10):

| Component | Pinned Version | Scope / Purpose | Compatibility Matrix Constraints |
|---|---|---|---|
| **Kotlin** | `2.1.10` | Language & K2 compiler | Compatible with Compose Multiplatform 1.7.3, AGP 8.7.3, KSP 2.1.10-1.0.29 |
| **Compose Multiplatform** | `1.7.3` | Shared UI framework | Pinned to Kotlin 2.1.10 (JetBrains Compose compiler decoupled) |
| **Android Gradle Plugin (AGP)** | `8.7.3` | Android build tooling | Requires Gradle 8.9+, supports compileSdk 35, minSdk 26 |
| **Gradle Wrapper** | `8.11.1` | Build orchestration | Official SHA-256 verified wrapper distribution |
| **JDK Runtime** | `OpenJDK 21 LTS` / `Studio JBR` | Gradle daemon execution | Compatible with AGP 8.7.3 and Gradle 8.11.1 |
| **JVM Bytecode Target** | `17` | Bytecode generation | Common baseline across Android (minSdk 26 API desugaring) and Desktop JVM |
| **Android compileSdk** | `35` | Android API target | Android 15 (Vanilla Ice Cream) |
| **Android minSdk** | `26` | Minimum Android baseline | Android 8.0 (Oreo), provides Java 8+ / modern security APIs |
| **Android targetSdk** | `35` | Target execution API | Android 15 runtime behavior |
| **Room KMP** | `2.7.0-alpha12` | Multiplatform local persistence | Kotlin Multiplatform SQLite Room abstraction for Android & Desktop |
| **KSP (Kotlin Symbol Processing)**| `2.1.10-1.0.29`| Code generation | Required for Room KMP database schema generator |
| **Bundled SQLite Driver** | `2.5.0-alpha12` | Native SQLite driver | `androidx.sqlite:sqlite-bundled` cross-platform bundled engine |
| **Ktor Client** | `3.1.0` | HTTP client engine | Multiplatform HTTP client (OkHttp engine on Android, Java engine on Desktop) |
| **Kotlinx Coroutines** | `1.10.1` | Async & StateFlow | Reactive UI state management and structured concurrency |
| **Kotlinx Serialization** | `1.8.0` | JSON serialization | Format-agnostic typed DTO encoding/decoding |
| **Kotlinx DateTime** | `0.6.1` | Timestamp handling | UTC epoch millisecond manipulations |
| **Jetpack Glance** | `1.2.0` | Native Android task widget | `androidx.glance:glance`, `glance-appwidget`, `glance-material3`. Compatible with AGP 8.7.3, minSdk 26, compileSdk 35. Artifact provenance under root strict dependency verification remains an external hold. |

---

## 2. Official Compatibility References

1. **Kotlin Multiplatform Compatibility Guide**:
   - URL: `https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html`
   - Verified: Kotlin 2.1.x is compatible with Gradle 7.6.3 through 9.7.0 and AGP 8.5.2 through 9.3.1.
2. **Compose Multiplatform Versioning**:
   - URL: `https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html`
   - Verified: Compose Multiplatform 1.7.3 binds to Kotlin 2.1.10.
3. **Android Gradle Plugin Releases**:
   - URL: `https://developer.android.com/build/releases/gradle-plugin`
   - Verified: AGP 8.7.x requires minimum Gradle 8.9 and JDK 17+ (JDK 21 LTS recommended).
4. **Gradle Compatibility Matrix**:
   - URL: `https://docs.gradle.org/current/userguide/compatibility.html`
   - Verified: Gradle 8.11.1 supports Java 21 compilation and execution.
5. **Android Kotlin Multiplatform Library Plugin**:
   - URL: `https://developer.android.com/kotlin/multiplatform/plugin`
   - Verified: Modern `com.android.kotlin.multiplatform.library` and `com.android.library` DSL for pure KMP shared code.
6. **Room KMP Guide**:
   - URL: `https://developer.android.com/kotlin/multiplatform/room`
   - Verified: Room KMP 2.7.0+ supports CommonMain, Android, and JVM targets with `androidx.sqlite:sqlite-bundled`.

7. **AndroidX Glance Guide**:
   - URL: `https://developer.android.com/jetpack/compose/glance`
   - Verified: Glance 1.2.0 provides declarative widget composition, `SizeMode.Responsive`, and AppWidgetManager interoperability on Android minSdk 26 through compileSdk 35.
---

## 3. Staged Module Assembly Architecture

To maintain strict file ownership and prevent broken builds during multi-phase implementation:
1. `shared` is registered immediately in `settings.gradle.kts`.
2. `androidApp` is included conditionally: only when `/home/loidinh/WS/tabbyrush/androidApp/build.gradle.kts` exists on disk.
3. `desktopApp` is included conditionally: only when `/home/loidinh/WS/tabbyrush/desktopApp/build.gradle.kts` exists on disk.
4. **Zero Stub Applications**: Neither `androidApp` nor `desktopApp` stubs are created in Phase 02. Phase 07 owns `androidApp`; Phase 09 owns `desktopApp`.

## 4. Phase 07 Reported Build and Test Environment
The Phase 07 acceptance report identifies Windows 11 Build 26200 (AMD64), OpenJDK 21.0.9 LTS, Android SDK Platform 35, Build-Tools 35.0.0, and AGP 8.7.3. It records 364 passing Gradle tests (231 shared JVM, 99 desktop, 34 Android unit), 12 Node security tests, a debug APK build, and a Windows distributable build. These results were run with Gradle dependency verification disabled; they do not resolve the 149 unmatched checksum baseline or the separate Glance provenance hold.

This report records automated verification, not interactive Android launcher/shade or Windows window/tray/multi-monitor acceptance. Those native scenarios remain conditionally held at Gate G2. Linux acceptance remains pending a real Linux session in Phase 08.
