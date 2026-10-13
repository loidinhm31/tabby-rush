# Phase 02 — Frozen contracts and compatible foundation

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Selected strict A](reports/strong-sync-feasibility.md) · [Strict design](reports/strong-sync-design-feasibility.md) · [Isolation inventory](reports/strong-sync-isolation-feasibility.md) · [Mongo evidence](reports/mongodb-feasibility-evidence.md).
- [Historical platform/theme evidence](../261013-0914-doggo-compose-todos/phase-02-frozen-foundation.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group B after01. Freeze interfaces/config/ownership before independent03/04/05/06 coding. Own root/shared Gradle/catalog/verification/lock forever; other modules belong later phases.

## Overview
- Date: 2026-10-13
- Description: Frozen contracts and compatible foundation.
- Priority: P2
- Implementation status: pending (0%)
- Review status: parent plan review complete; implementation/runtime acceptance pending.
- Effort: 12h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Current repository has plans only; README.md absent. Prior online-only/shared-token/Worker assumptions superseded. Foundation implements real contracts/policies only, never stub app roots, fake repositories or dummy platform vaults.

## Requirements
Exact stable compatible Kotlin/compiler/Compose/AGP/Gradle/JDK/Room/KSP/bundled SQLite/Ktor tuple, Android min26 baseline and proven JVM bytecode17; Room schema export directory. Freeze all constructors/errors/state/actions/DTO policy from current strict contracts.md, including epoch/revision/opId/digest/outcome and factory boundaries before03–06. Both OS credential binding dependencies approved now; UI and sync code against contracts only. Legacy embedded clients/shared Rust/TS libraries excluded.

## Architecture
Root includes shared immediately; include androidApp/desktopApp only when their exact owned build.gradle.kts exists. Explicit existence guards, not broad scans. Shared Android library+jvm targets are independently configurable without absent application scripts. Common exports have no Room/Ktor API leakage. Factory signatures expose real auth/store/coordinator injection; no runtime implementation in02.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/settings.gradle.kts` — create.
- `/home/loidinh/WS/doggo/build.gradle.kts` — create.
- `/home/loidinh/WS/doggo/gradle.properties` — create.
- `/home/loidinh/WS/doggo/gradle/libs.versions.toml` — create.
- `/home/loidinh/WS/doggo/gradle/verification-metadata.xml` — create.
- `/home/loidinh/WS/doggo/gradle/wrapper/gradle-wrapper.jar` — create.
- `/home/loidinh/WS/doggo/gradle/wrapper/gradle-wrapper.properties` — create.
- `/home/loidinh/WS/doggo/gradlew` — create.
- `/home/loidinh/WS/doggo/gradlew.bat` — create.
- `/home/loidinh/WS/doggo/.gitignore` — create.
- `/home/loidinh/WS/doggo/shared/build.gradle.kts` — create.
- `/home/loidinh/WS/doggo/shared/gradle.lockfile` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/hub-config.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-identity.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-key.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/credential-vault.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/auth-session.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/account-store.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/sync-coordinator.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/sync-scheduler.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-models.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-ui-state.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-action.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/app-factories.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/safe-errors.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/title-policy.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/endpoint-policy.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/contract/strict-sync-contract.kt` — create, public strict protocol/domain and canonical digest implementation.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/contract/strict-digest-test.kt` — create, cross-language canonical vectors and bounds.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/contract/title-policy-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/contract/endpoint-policy-test.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/contract/account-key-test.kt` — create.
- `/home/loidinh/WS/doggo/docs/system-architecture.md` — create.
- `/home/loidinh/WS/doggo/docs/api-contract.md` — create.
- `/home/loidinh/WS/doggo/docs/code-standards.md` — create.
- `/home/loidinh/WS/doggo/docs/toolchain-versions.md` — create.
- `/home/loidinh/WS/doggo/docs/foundation-evidence.md` — create.

## File Ownership
Exclusive phase02 owner of all 37 exact absolute paths above; those and only those phase02 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Read authoritative architecture/contracts/decisions, explicitly supersede historical token/D1/OAuth/LWW/session-loss defaults. Freeze native signup/access requests, server-wins review, offline established account, protected credentials and guarded wipe. Unknown vault provider, locked/unavailable vault and corrupt bundle have distinct safe states; none trigger plaintext fallback or DB deletion. Offline access only for previously established identity and approved vault/local metadata policy.
2. Use01 disposable KMP project to prove exact stable tuple using current official Kotlin KMP/Compose compatibility, AGP/Gradle/JDK matrices and Room KMP/KSP docs. Compile Android+JVM with Room generation and common UI resource/semantic tests there. Record actual command/task names and relation links/date in toolchain-versions/foundation-evidence. Not final application proof; do not pin alpha/latest by habit.
3. Generate wrapper with distribution SHA256, root/catalog/properties/shared configuration and .gitignore. Ignore local.properties, secrets/.env, signing keys, local Hub data, build caches and binary outputs. No secret-valued template/config. Shared uses modern supported Android-library DSL (not application plugin), namespace dev.doggo.shared and jvm target. Enable resources and generated package dev.doggo.shared.resources.
4. Catalog pins all lanes and target credential bindings including supported Linux D-Bus binding and Windows JNA/DPAPI plus native packaging/test dependencies. Configure Room/KSP android+jvm generation and `shared/schemas`;05 exclusively owns dev.doggo.shared.data.DoggoDatabase/1.json. Configure actual androidInstrumentedTest/jvmTest/commonTest roots and task mappings from tuple; no guessed legacy DSL.
5. Implement redacted CredentialBundle/Vault, identity/origin hashing with unambiguous framing/full SHA256, HubConfig/endpoint policy, Unicode scalar title policy, auth/access/generation/leave/scheduler/store/state/actions/factory/error contracts. Preserve existing auth/vault boundaries and freeze exact POST strong/bootstrap,push,pull allowlist; none may be automatically replayed by HTTP. No Room/Ktor types in public factories.
   Freeze strict-v1 epoch UUID, server revision/expectedRevision safe-integer bounds, immutable opId/kind/identity/payload, per-operation status decoding and canonical digest per contracts sections5–8. Digest domain and u32-length-framed UTF8 identity fields, u64 big-endian integers/boolean byte have matching Kotlin/Rust vectors; delete canonical empty data. Concrete serialization adapter05 consumes frozen public models; server isolated DTO03, no shared core DTO migration.
   Freeze accepted/replayed versus conflict/missing/deleted/invalid/retryable/protocol/reset statuses; exact epoch/opId/digest/acceptedRevision alone acknowledges captured localRevision. UnknownSuperseded distinct from known rejection; payload/pull absence never acknowledges.503notAdmitted transient backoff/preserve,409epochMismatch ResetRequired,426/404/405 ProtocolIncompatible,400invalidRequest protocol pause; retryableFailure MAYBE applied. Auth callback04→05 interface-only; UI separates uncertainty/conflict/protocol/reset. Old-epoch intents never retarget,strict mode immutable/no legacy fallback.
6. Write title/endpoint/account-key tests covering malformed surrogates, astral/Unicode whitespace, internal controls,201 scalars, canonical origins/ports/redirects and different user/origin partitions. Strict digest tests include exact UTF8 vectors, delete, integer overflow, malformed UUID, identity/epoch/base/kind/opId changes and unknown statuses. Freeze late-response generation/epoch and public state no-secret invariants. Types/policies are real implementations; interfaces have no fake default results.
7. Enable strict dependency locking/verification. Bootstrap shared lock with reviewed resolutions and known checksums; never blindly trust fetched hashes. Until05 Room database declaration lands, final shared KSP gate may not be runnable: report this accurately; disposable proof is the freeze gate. Reopen02 for later legitimate module configurations/platform verification metadata/shared lock, while07/09/10 own their module locks.
8. Publish docs/system-architecture, api-contract, code-standards, tuple/evidence and exact ownership/factory signatures before lanes. At integration run tuple-recorded equivalents of `./gradlew :shared:allTests :androidApp:assembleDebug`;02 maintains mapping if plugin changes names, never silently omit semantic/instrumented tests.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior,not scaffolds.
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence.
- [ ] Resolve failures through original owner and complete parent review; do not mark implementation accepted early.

## Success Criteria
Exact stable tuple and actual disposable compatibility evidence; real frozen types/policies plus permanent tests; independent lane interfaces and exact Room export path; reviewed wrapper/dependency metadata. Freeze is not whole-app build proof. All02 tests executed later07, with actual shared configuration gaps resolved by02.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Room/KSP/modern Android plugin/resource-test compatibility and vault library support may force tuple change before freeze. Root guards do not excuse fake app builds; lock/checksum bootstrap records trust limits.

## Security Considerations
Release HTTPS approved origin only; debug exact aliases/ports. Credential redaction and no secret arguments in persistent states/logs. Wrapper/download hashes and dependency verification reviewed; no plaintext vault fallback.

## Next steps
Start03/04/05/06 concurrently after frozen review. API/catalog drift reopens02 and impacted lanes, never integration sweeping edits.

## Unresolved questions
Exact stable tuple and actual dependency/task DSL await implementation proof; no source build has been run now.
