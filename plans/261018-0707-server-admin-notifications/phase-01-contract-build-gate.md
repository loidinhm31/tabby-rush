# Phase 01 — Contract, pure interfaces and common build gate

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **A**; implementation start: None; first gate.
- Additional runtime/acceptance dependencies: Gate A contract checks only. Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P01**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **12h**.
- Description: Contract, pure interfaces, golden vectors (13 vectors), and common build gate implemented and verified.

## Key Insights
- Fresh analysis replaces scout; architecture section8 is proposed only.
- Gate A may implement pure contracts and dependency exports, never fake runtime stubs to make later components compile.

## Requirements
- Freeze all sections of report03 and exclusive ownership before wave B.
- Resolve public shared Compose/runtime/transitive types and build assumptions; no task schema changes.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Contract JSON and golden vectors mirror report03; Kotlin and Rust expose pure types. Existing modules remain the build units. Shared UI dependencies needed in host signatures use api where required; no new generic framework.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/integration/announcements/contract-v1.json` | create | Versioned route/DTO/error/limit/platform contract; machine-readable source for vectors, not generated runtime client. |
| `/home/loidinh/WS/tabby-rush/integration/announcements/vectors-v1.json` | create | Canonical normalization, UUIDv5, cursor and binding-generation golden vectors. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/remote-inbox-contracts.kt` | create | Pure remote state/actions/controller and native adapter boundaries; no runtime implementation. |
| `/home/loidinh/WS/tabby-rush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/remote-contract-vector-test.kt` | create | Golden contract serialization, limits and UUID input vectors. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/announcement_contract.rs` | create | Pure serde DTOs, transport trait and typed outcomes; no placeholder transport. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/src/lib.rs` | modify | Export pure announcement contract once; preserve existing producer exports. |
| `/home/loidinh/WS/glean-hub/Cargo.toml` | modify | Single workspace dependency declaration if required; workspace membership unchanged. |
| `/home/loidinh/WS/glean-hub/Cargo.lock` | modify | Only owner resolves/records all planned Rust dependencies. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/Cargo.toml` | modify | Declare backend Mongo/time/test dependencies ahead of components. |
| `/home/loidinh/WS/glean-hub/glean-hub-notification/Cargo.toml` | modify | Pin HTTP/OAuth dependencies for FCM component. |
| `/home/loidinh/WS/tabby-rush/gradle/libs.versions.toml` | modify | Pin Firebase BoM34.4.0/messaging alias and any common library aliases; no unreviewed version range. |
| `/home/loidinh/WS/tabby-rush/shared/build.gradle.kts` | modify | Public Compose exports required by hosts and Ktor streaming compile prerequisite; do not change Room schema. |
| `/home/loidinh/WS/tabby-rush/shared/gradle.lockfile` | modify | Shared dependency resolution owner. |
| `/home/loidinh/WS/tabby-rush/gradle/verification-metadata.xml` | modify | Only owner adds verified artifacts for shared/desktop/Android dependency requests, never disables strict verification. |
| `/home/loidinh/WS/tabby-rush/docs/announcement-contract.md` | create | Permanent concise API/invariant reference linking versioned vectors. |

## File Ownership
- P01 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Read all authoritative reports and existing build conventions. Record current source hashes and unrelated-change boundaries; use actual tabby-rush identity.
2. Encode exactly report03 routes, DTO discriminators, errors, immutable digest field order and UUIDv5 name encoding in contract/vectors. Include selected/all/admin overflow, title scalar vs UTF-8 limits, UUID non-ordering, numeric generation and wrong-scope cursor vectors.
3. Define pure Kotlin state/action/controller/adapter interfaces and Rust AnnouncementPushTransport DTOs/trait. Export only files created now; do not reference future missing source or add no-op implementations.
4. Resolve reviewed HTTPS/OAuth dependencies and Firebase BoM34.4.0 artifacts; pin exact versions in owned manifests/locks. Audit source provenance/checksums rather than accepting generated checksums blindly; inherited checksum/Glance holds remain open. Coordinate later platform resolution requests through this owner.
5. Fix shared public Compose export prerequisite without changing unrelated dependency visibility. Existing Ktor core streaming API suffices; no unnecessary SSE abstraction/plugin.
6. Publish frozen contract and handoff signatures to02..08/11. Run contract-only checks after gate edits if authorized by implementation assignment; no later source component completion credited. Live Mongo preflight prerequisite belongs02/11, not a fake passed Gate A.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Golden vectors agree across languages; all consumers can implement against actual pure interfaces.
- No overlapping source/build owners; no unresolved protocol choice; inherited holds unchanged.

Future targeted verification (not executed by this plan):
- `At implementation Gate A: ./gradlew :shared:jvmTest --tests "*RemoteContractVectorTest" --dependency-verification=strict`
- `At H: cargo test -p glean-hub-notification announcement_contract --locked`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Dependency provenance may block release; keep blocker explicit and do not turn verification off.
- Unexpected build API exposure: fix once in shared build owner, not host workarounds.

## Security Considerations
- Contract rejects content-bearing hints and caller-supplied ownership.
- No service credentials/public-config secrets embedded in contract vectors.

## Next steps
- Consumers/unblocked phases: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md), [P04](phase-04-react-admin-composer.md), [P05](phase-05-shared-remote-core.md), [P06](phase-06-shared-inbox-ui.md), [P07](phase-07-linux-native-adapter.md), [P08](phase-08-windows-native-packaging.md), [P09](phase-09-android-runtime-integration.md), [P11](phase-11-operations-config-deployment.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
