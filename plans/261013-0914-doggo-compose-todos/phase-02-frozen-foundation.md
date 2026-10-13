# Phase 02 — Frozen contracts and compatible foundation

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Planning brief](reports/planning-brief.md)

## Parallelization Info
After01; group B. Blocks03–06 until product/contracts/toolchain/file ownership signed off. Root/shared build/catalog only owner02.

## Overview
- Date: 2026-10-13
- Description: Frozen contracts and compatible foundation.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 6h

## Key Insights
Modern separate androidApp/shared-library/desktopApp structure avoids mixed application+KMP plugin. Existing project has no source/docs. Foundation creates real contracts, not app stubs.

## Requirements
Approve proposed private/session-only online-first/light scope, exact contracts/schema/action signatures. Pin compatible complete tuple using current wizard plus official compatibility proof. Common data classes/interfaces and real validation policy implementations.

## Architecture
Root Gradle project includes shared and androidApp; desktopApp included only when its owned build.gradle.kts exists (documented staged assembly). Final-repo Gradle commands start07 when Android script lands; no absent-module stubs. Wizard proof isolated outside repo.

## Related code files
Future create only; no existing source modified or deleted:
- `settings.gradle.kts` — create.
- `build.gradle.kts` — create.
- `gradle.properties` — create.
- `gradle/libs.versions.toml` — create.
- `gradle/verification-metadata.xml` — create.
- `gradle/wrapper/gradle-wrapper.jar` — create.
- `gradle/wrapper/gradle-wrapper.properties` — create.
- `gradlew` — create.
- `gradlew.bat` — create.
- `.gitignore` — create.
- `shared/build.gradle.kts` — create.
- `shared/gradle.lockfile` — create at first runnable final-repo gate; no empty placeholder.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-models.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/api-result.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-repository.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-ui-state.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-action.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/todo-controller.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/session-repository-factory.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/title-policy.kt` — create.
- `shared/src/commonMain/kotlin/dev/doggo/shared/contract/endpoint-policy.kt` — create.
- `shared/src/commonTest/kotlin/dev/doggo/shared/contract/title-policy-test.kt` — create.
- `shared/src/commonTest/kotlin/dev/doggo/shared/contract/endpoint-policy-test.kt` — create.
- `docs/system-architecture.md` — create.
- `docs/api-contract.md` — create.
- `docs/code-standards.md` — create.
- `docs/toolchain-versions.md` — create.

## File Ownership
Only exact paths listed above and phase02 rows in registry; exclude every other file. Machine-local generated state not tracked. Reopen original owner for out-of-scope changes.

## Implementation Steps
1. Review architecture.md/contracts.md with user; approve private dataset/token re-entry, online-first, light-only and Windows manual default. Multi-user/offline changes revise contracts/owners before lanes, never bolted on later.
2. From01 disposable current-stable wizard project select tuple: exact Studio, Kotlin/compiler, Compose, modern Android KMP library plugin, AGP, Gradle wrapper, runtime JDK/JBR, JVM bytecode, compile/target/min SDK, build-tools/system-image. Verify each relation against https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html , https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html , https://developer.android.com/build/releases/gradle-plugin , https://docs.gradle.org/current/userguide/compatibility.html and modern plugin https://developer.android.com/kotlin/multiplatform/plugin . Record URL/date/table constraints and successful disposable Android+JVM compile/test evidence. Reject tuple if modern plugin/wizard requires incompatible SDK/Gradle/JDK; choose supported stable template instead, no copied report pins.
3. Generate verified Gradle wrapper, checksums, root/catalog/properties/shared script **only**. Transfer no wizard application/sample files. Shared targets android and jvm; catalog includes serialization, Ktor/engines, coroutines/core/test/swing, Compose Material3/resources/UI-testing, Kotlin test and Android instrumentation dependencies. Configure dependency locking for module configurations;02 generates/reviews shared lock at07,07 owns Android lock,09 desktop lock. Verification metadata stays02-owned: bootstrap from disposable proof, then reopen02 for actual07/09/10 dependencies; never blindly trust generated checksums. JDK runtime and bytecode17 distinct, revised only with evidence.
4. Root settings use explicit includes shared/androidApp and existence-guard desktopApp; no broad module globs. Since androidApp script is owned07, final root configuration is not runnable until07; say so clearly. Shared script fully sets namespaces `dev.doggo.shared`, modern Android-library target with min26, JVM target17 and resources/test configurations. Do not put application plugin in shared.
5. Create exact contract models/repository/controller/state/actions/factory and policies. `Connect` redacts toString and is not serializable. Validate Unicode/endpoint vectors. Freeze Android BuildConfig mode and immutable desktop RELEASE MainKt versus explicit Gradle-only DevelopmentLauncher/runDebug; no packaged override. Interfaces contain no fake runtime implementations.
6. Freeze explicit package/symbol/JSON/schema/error/cursor/version/mutation-reconciliation contracts from contracts.md. Check independent UI/data types sufficient; enums serialize explicit lowercase filters, wire models camelCase; counts fit Int personal scope. Confirm documented supported constant-time Worker primitive through current runtime docs/types, else resolve before freeze.
7. Write permanent title/endpoint behavior tests, root shared dependency verification, architecture/API/code standards/toolchain docs. Tests run in main07 batch; compatibility compile from disposable project is not app proof. .gitignore excludes local.properties, .dev.vars*, .wrangler/, builds/.gradle/, signing keys/env backups. No secret-valued sample file.
   Shared Android library resources must be explicitly enabled using the selected modern plugin DSL; pin generated resources package `dev.doggo.shared.resources`. Font identifiers use underscores. Configure actual common Compose semantic-test support and choose task names from the proven tuple, not assumed legacy Android test DSL.
8. Publish frozen interfaces/ownership matrix to lanes; every new path or dependency catalog edit returns02 owner. Project architecture is source of truth during future implementation; reconcile approved drift via02.

## Todo list
- [ ] Complete requirements and owned implementation/runbooks.
- [ ] Record designated gate evidence; all failures resolved through original owners.
- [ ] Review frozen contract/ownership compliance; no runtime placeholders.

## Success Criteria
Exact compatible tuple pinned with disposable wizard evidence and official relation proof; all contract types/policies real; no platform stubs; exclusive ownership frozen; source/package/API agreement sufficient for independent C lanes.

## Conflict Prevention
No edits outside listed rows, including catalog/build/lock/docs/tests. New paths require registry amendment first. Integration defects reopen owner, then rerun gate.

## Risk Assessment
Current wizard may differ from report versions; final repository partial setup cannot falsely pass Gradle. Delay lanes until exact tuple/runtime constant-time primitive resolved.

## Security Considerations
HTTPS release validated before opening client; explicit debug origins. No secrets in Gradle properties/catalog/wrapper/download evidence; checksum/verification metadata guard dependencies.

## Next steps
Start03/04/05/06 together; any frozen contract change reopens all impacted owners and gates.

## Unresolved questions
Approve private-online-light/manual Windows defaults; exact tuple intentionally selected/proven during implementation, not claimed pinned now.
