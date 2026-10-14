# Android Release & Operations Acceptance Specification

**Document Version**: 1.1.0  
**Phase**: 08 — Authorized Hub Operations & Signed Android Release  
**Status**: Tooling, Security Checks & Operational Runbooks Implemented; Live Release Acceptance Blocked on Host SDK & Phase 07 Remediation  
**Related Documents**: [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [Android Acceptance](./android-acceptance.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. Acceptance Status & Operational Gates

Phase 08 establishes the operational tooling, security audit scripts, disaster recovery runbooks, and live Hub verification harnesses required for the production deployment of Doggo on Android.

### 1.1 Milestone Status Classification
To maintain absolute engineering integrity, acceptance is partitioned into three distinct classifications:
1. **Tooling & Harness Preparation**: **IMPLEMENTED & VERIFIED OFFLINE**. All scripts, security checks, and runbooks are implemented, syntactically verified, and tested against deterministic offline fixtures.
2. **Phase 07 Correctness Prerequisites**: **OPEN / RELEASE-BLOCKING**. Technical review of Phase 07 scored 4/10 NOT ACCEPTED. As recorded in `docs/project-roadmap.md`, three critical runtime defects remain release blockers:
   - **Teardown Generation Rollback**: Risk of generation counter reversion during interrupted account logout.
   - **Cancellation Swallowing**: Exception handling in sync workers obscuring WorkManager cancellation signals.
   - **Worker TOCTOU**: Time-of-check to time-of-use race condition between generation validation and database mutation.
3. **Live Release Acceptance**: **BLOCKED ON OPERATOR PREREQUISITES**. Bytecode assembly (`assembleRelease`), binary signing verification (`apksigner verify`), and connected device tests (`connectedDebugAndroidTest`) require host Android SDK installation (Android SDK 35, `cmdline-tools;latest`, `platform-tools`).

---

## 2. Release Acceptance Verification Matrix

| Area | Requirement | Verification Method | Status | Notes |
|:---|:---|:---|:---:|:---|
| **Security Audit Tooling** | `scripts/release-security-check.mjs` enforces fail-closed checks on release APK signature (v2/v3 scheme), explicit `allowBackup="false"`, `debuggable="false"`, `usesCleartextTraffic="false"`, non-main exported components rejection, networkSecurityConfig stripping, and secret absence. | Automated CLI test (`--help`, `--diagnose-source`, fail-closed test) | **PASS** | Diagnostic mode clean; release mode fails closed without APK or when attributes are missing. |
| **Live Smoke Tooling** | `scripts/hub-live-smoke.mjs` executes full strict-v1 protocol: health, admission read-back, auth session, structured push/pull with specific rowId assertions, base conflict, frozen replay, epochMismatch, and tombstones. | Automated fixture server test (`--fixture-test`) | **PASS** | 18/18 checks pass; connection failure fails closed. |
| **Disaster Recovery** | `scripts/hub-strict-recovery.js` validates writer quiescence, allocates fresh UUID epoch, provisions target collection (`app_<appId>`), validates source records, runs admission preflight, and executes journaled registry CAS on `_schemas`. | Syntax check, fail-closed assertions, and CLI dry-run (`DRY_RUN=true`) | **TOOLING READY** | Syntax verified (`node --check`), fail-closed checks pass, Node runtime exits fail-closed requiring mongosh, dry-run configuration parsing passes. Live mongosh cluster execution pending operator maintenance window. |
| **Common/JVM Regression** | `:shared` module multiplatform contract and unit test suite remains 100% green. | `./gradlew :shared:jvmTest --rerun-tasks --dependency-verification=off` with JDK 21 | **PASS** | 96/96 unit and SQLite integration tests pass. |
| **APK Bytecode Assembly** | Compile release APK via AGP 8.7.3 (`:androidApp:assembleRelease`). | Gradle release assembly task | **PENDING** | Host Android SDK not installed on workstation. |
| **APK Binary Signature** | Cryptographic verification via `apksigner verify --verbose --print-certs`. | apksigner binary inspection | **PENDING** | Requires compiled release APK and SDK tools. |
| **Live Hub Staging Gate** | Live end-to-end sync against deployed Glean Hub instance. | Live network execution of `hub-live-smoke.mjs` | **PENDING** | Requires authorized operator Hub deployment. |
| **Connected Device Suite** | Instrumented Android test execution on real device or emulator. | `androidApp:connectedDebugAndroidTest` via Xpra | **PENDING** | Requires Android SDK emulator/device attachment. |
| **Release UI Verification** | 7 core UI states documented: Empty, Populated, Completed, Editor, Error, Conflict, Access-Pending. | High-fidelity specification mockups in `docs/evidence/` | **DOCUMENTED** | Live device framebuffer captures scheduled upon interactive attachment. |

---

## 3. Mandatory Remediation Checklist for Release Authorization

Prior to authorizing public distribution of the Doggo Android release APK, the operator must execute the following remediation and verification steps:

1. **Remediate Phase 07 Correctness Defect**:
   - Resolve teardown generation rollback, cancellation swallowing, and worker TOCTOU in `:androidApp`.
   - Re-run `:androidApp:testDebugUnitTest`.
2. **Provision Host Android SDK**:
   - Install Android SDK 35 (`cmdline-tools;latest`, `platforms;android-35`, `platform-tools`, `emulator`).
   - Interactively accept licenses: `sdkmanager --licenses`.
3. **Execute Production Signing & Build**:
   - Configure external keystore environment (`DOGGO_KEYSTORE`, etc.).
   - Execute `./gradlew :androidApp:assembleRelease`.
4. **Run Release Security Audit**:
   - Execute `node scripts/release-security-check.mjs --apk androidApp/build/outputs/apk/release/androidApp-release.apk --origin "$APPROVED_HUB_ORIGIN"`.
   - Ensure 0 security issues.
5. **Run Device Smoke Runner**:
   - Execute `bash scripts/android-smoke.sh -a androidApp/build/outputs/apk/release/androidApp-release.apk -c`.
   - Capture live framebuffer screenshots to replace design specification baselines.
6. **Validate Disaster Recovery against Staging MongoDB**:
   - Verify mongosh Int64 BSON serialization (`revision: Long(1)`, `createdAt: Long(...)`) against Hub reader accessors (`doc.get_i64`).
   - Rehearse fresh-epoch recovery against a throwaway MongoDB staging cluster with real backup data, source validation, Rust admission preflight, and journaled registry CAS.
