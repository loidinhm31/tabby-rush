# Doggo Project Roadmap

**Current Status:** In Progress  
**Last Updated:** 2026-10-14  
**Target Plan:** [plans/261013-1540-doggo-glean-hub-reuse/plan.md](../plans/261013-1540-doggo-glean-hub-reuse/plan.md) (Authoritative Evolution Plan)  
**Historical Plan:** [plans/261013-0914-doggo-compose-todos/plan.md](../plans/261013-0914-doggo-compose-todos/plan.md) (Superseded)  
**Overall Completion:** 12% (18h / 148h completed; Phase 01 & Phase 02 DONE)

---

## 1. Executive Summary

Doggo is an Android-first shared Compose Multiplatform application for task management with cloud synchronization. The development follows a phased headless-first architecture on Fedora Linux, progressing from virtualized display infrastructure to shared Kotlin Multiplatform libraries, backend API, Android client integration, and native desktop packaging (Linux RPM and Windows MSI).

Phase 01 (Headless Tooling and Android Environment) was executed and marked complete on 2026-10-13 for tooling scripts, SDK environment setup, and verification suites. Note that interactive visual runtime acceptance (physical Studio first-run wizard and running emulator framebuffer screenshot) remains an environment gate pending interactive operator execution.

Phase 02 (Frozen Foundation & Contracts) was executed and marked complete on 2026-10-14, establishing the Gradle multiplatform root and `:shared` library baseline, pinned version catalog, 16 core Kotlin contract files, 4 commonTest suites, foundational documentation, and 57/57 passing QA assertions.

## 2. Phase Progress & Status

| Phase | Description | Group | Effort | Status | Completed Date | Progress |
|:---|:---|:---:|---:|:---:|:---:|---:|
| **01** | [Headless Tooling & Android Environment](../plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md) | A | 6h | **DONE** | 2026-10-13 | 100% |
| **02** | [Frozen Foundation & Contracts](../plans/261013-1540-doggo-glean-hub-reuse/phase-02-frozen-foundation.md) | B | 12h | **DONE** | 2026-10-14 | 100% |
| **03** | [Isolated Strict Server](../plans/261013-1540-doggo-glean-hub-reuse/phase-03-hub-schema-compatibility.md) | C | 40h | Pending | — | 0% |
| **04** | [Auth Session & Vault](../plans/261013-1540-doggo-glean-hub-reuse/phase-04-auth-session.md) | C | 12h | Pending | — | 0% |
| **05** | [Local Strict Sync](../plans/261013-1540-doggo-glean-hub-reuse/phase-05-local-sync-state.md) | C | 28h | Pending | — | 0% |
| **06** | [Compose UI & Light Theme](../plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md) | C | 12h | Pending | — | 0% |
| **07** | [Android Integration](../plans/261013-1540-doggo-glean-hub-reuse/phase-07-android-integration.md) | D | 14h | Pending | — | 0% |
| **08** | [Android Release Operations](../plans/261013-1540-doggo-glean-hub-reuse/phase-08-android-release-operations.md) | E | 10h | Pending | — | 0% |
| **09** | [Linux Installed RPM Distribution](../plans/261013-1540-doggo-glean-hub-reuse/phase-09-linux-distribution.md) | F | 8h | Pending | — | 0% |
| **10** | [Windows Installed MSI Distribution](../plans/261013-1540-doggo-glean-hub-reuse/phase-10-windows-distribution.md) | G | 6h | Pending | — | 0% |
| **Total** | | | **148h** | | | **12%** |

---

## 3. Milestones & Timeline

### Milestone 1: Development Infrastructure (Group A) — COMPLETED
- **Phase 01:** Headless tooling, Xpra virtual display isolation, Android SDK environment scripts.
- **Completion Date:** 2026-10-13.
- **Key Deliverables:**
  - `scripts/start-xpra.sh`: Safe virtual X11 server runner (`--html=off`, `--mdns=no`, user-scoped socket).
  - `scripts/android-sdk-env.sh`: Idempotent SDK and compatible JDK configuration.
  - `scripts/start-android-emulator.sh`: AVD emulator lifecycle script with safe validation and hardware/software acceleration support.
  - [headless-android-setup.md](headless-android-setup.md): Complete operations runbook and security guide.
  - [host-tooling-evidence.md](host-tooling-evidence.md): Verified host capability matrix (KVM, OpenJDK 25, Node v24 LTS, Xpra 6.5.4).
  - Automated test suite (`tests/test-phase-01-tooling.sh`): 23/23 QA tests passing; 31/31 review cycle 3 checks passing.
  - *Note on Runtime Acceptance Gate:* Static script validation, syntax checks, and live Xpra smoke test (display :199) passed. Interactive Android Studio wizard, SDK license acceptance, and live AVD framebuffer capture (`android-tooling-launcher.png`) remain operational prerequisites for downstream interactive lanes.

### Milestone 2: Contracts & Shared Core (Groups B & C) — IN PROGRESS
- **Phase 02 (COMPLETED 2026-10-14):** Contract freeze, multiplatform Gradle build baseline (`:shared` module, Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12 KMP, Gradle 8.11.1 wrapper, 16 contract files, 4 commonTest test suites, 57/57 QA assertions passed).
  - **Key Deliverables:**
    - Root and module build files: `settings.gradle.kts`, `build.gradle.kts`, `shared/build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`.
    - Verified Gradle wrapper (8.11.1) with official distribution SHA-256 checksum and verified jar hash.
    - Contract specifications: `shared/src/commonMain/kotlin/dev/doggo/shared/contract/*.kt` (HubConfig, endpoint policy, Unicode scalar title policy, credential vault & bundle with toString redaction, strict-v1 protocol and canonical digest).
    - Unit test suites: `shared/src/commonTest/kotlin/dev/doggo/shared/contract/*.kt` (strict digest NIST vectors, title policy, endpoint policy, account key derivation).
    - Foundational documentation: `docs/system-architecture.md`, `docs/api-contract.md`, `docs/code-standards.md`, `docs/toolchain-versions.md`, `docs/foundation-evidence.md`.
    - Automated verification suite: `tests/test-phase-02-foundation.sh` (57/57 QA assertions passing).
  - *Note on Runtime Acceptance Gate:* Static script validation, configuration checks, and reference Node vectors passed (57/57 QA assertions). Full `./gradlew :shared:jvmTest` execution and `shared/gradle.lockfile` population are deferred to downstream CI/integration lanes with compatible JDK 17/21 and Android SDK.
- **Phases 03–06 (UPCOMING):** Parallel implementation lanes (Hub isolated strict server, auth session & credential vault, local strict sync with Room v1, Compose UI and light theme components).

### Milestone 3: Android Release & Live Sync (Groups D & E) — UPCOMING
- **Phase 07:** Full Android integration, Hub sync coordinator, WorkManager background sync, Android KeyStore credential vault, connected UI tests on emulator (`emulator-5554`).
- **Phase 08:** Android release operations, isolated live Hub verification, signing and recovery runbooks, signed release APK over HTTPS.
### Milestone 4: Desktop Packaging (Groups F & G) — UPCOMING
- **Phase 09:** Native Linux RPM packaging and Xpra desktop verification.
- **Phase 10:** Native Windows MSI packaging and manual installer lifecycle gate.

---

## 4. Changelog

### [0.2.0-alpha.1] — 2026-10-14
#### Completed
- **Phase 02 Frozen Foundation & Contracts**:
  - Configured Gradle multiplatform root and `:shared` library module with Gradle 8.11.1 verified wrapper.
  - Pinned stable multiplatform dependency catalog (`gradle/libs.versions.toml`): Kotlin 2.1.10, Compose 1.7.3, AGP 8.7.3, Room 2.7.0-alpha12.
  - Implemented 16 core Kotlin contract files under `dev.doggo.shared.contract` (HubConfig, endpoint policy, Unicode scalar title policy, credential vault & bundle with toString redaction, strict-v1 protocol and NIST SHA-256 canonical digest).
  - Implemented 4 contract test suites under `commonTest` (strict-digest, title-policy, endpoint-policy, account-key).
  - Published foundational architecture and contract documentation (`docs/system-architecture.md`, `docs/api-contract.md`, `docs/code-standards.md`, `docs/toolchain-versions.md`, `docs/foundation-evidence.md`).
  - Executed QA test suite `tests/test-phase-02-foundation.sh` passing 57/57 assertions (100% pass rate).

### [0.1.0-alpha.1] — 2026-10-13
#### Completed
- **Phase 01 Headless Tooling & Android Environment**:
  - Implemented secure Xpra virtual display runner without public TCP exposure.
  - Implemented Android SDK environment exporter with compatible JDK fallback logic.
  - Implemented Android emulator startup script with strict port validation (`5554-5682`, even numbers).
  - Added test suite `tests/test-phase-01-tooling.sh` validating syntax, permissions, CLI handling, host binaries, and documentation structure.
  - Executed QA validation (23/23 passed) and code review cycle 3 (score 8.5/10, live Xpra session verified).
  - Published comprehensive setup runbook (`docs/headless-android-setup.md`) and host evidence record (`docs/host-tooling-evidence.md`).
