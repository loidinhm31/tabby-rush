## Phase Implementation Report

### Executed Phase
- Phase: phase-03-native-standard-sync
- Plan: plans/261020-1717-standard-sync-schema-version
- Status: source implementation completed; coordinator build, Room4 generation, automated checks and real native acceptance pending.
- Ownership: native shared/Android/Desktop/integration only; no scripts/docs/root README/other phase edits, live DB operations, service restarts, installed data changes or git operations.

### Files Modified
Current file lengths, not diff counts; 70 source/test/build/schema files:
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` (455 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt` (590 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt` (23 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-key.kt` (40 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt` (194 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt` (71 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-coordinator.kt` (32 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/safe-errors.kt` (55 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projector.kt` (255 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/task-data-contract.kt` (115 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sha256.kt` (76 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-database.kt` (34 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/database-migrations.kt` (251 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt` (550 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` (889 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/account-sync-meta-entity.kt` (33 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/outbox-entity.kt` (59 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt` (37 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/scan-staging-entity.kt` (37 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/legacy-sync-evidence-entity.kt` (14 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt` (100 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-dtos.kt` (84 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt` (248 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt` (105 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt` (75 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration.kt` (70 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/task-json-merge.kt` (97 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client.kt` (217 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/remote/remote-stream-client.kt` (243 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` (765 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt` (535 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt` (250 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/deadline-editor-section.kt` (382 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/conflict-review.kt` (347 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/sync-feedback.kt` (167 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/account-key-test.kt` (60 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/sha256-test.kt` (12 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/adversarial-scheduler-boundary-test.kt` (373 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/persisted-schedule-projection-test.kt` (234 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/remote-contract-vector-test.kt` (265 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/task-deadline-reminder-models-test.kt` (209 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-client-test.kt` (268 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol-test.kt` (239 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator-test.kt` (25 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/hub-sync-client-test.kt` (118 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation-test.kt` (22 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/staged-full-scan-test.kt` (203 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/legacy-standard-intent-migration-test.kt` (32 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/task-json-merge-test.kt` (45 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt` (722 lines)
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/remote/remote-inbox-client-test.kt` (260 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` (198 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/adversarial-sync-sqlite-test.kt` (223 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v1-to-v2-test.kt` (213 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-v2-to-v3-test.kt` (477 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-migration-to-v4-test.kt` (141 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-store-sqlite-test.kt` (358 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/task-surface-sync-sqlite-test.kt` (257 lines)
- `shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/reminder-scheduling-coordinator-sqlite-smoke-test.kt` (192 lines)
- `shared/src/commonMain/composeResources/values/strings.xml` (52 lines)
- `androidApp/build.gradle.kts` (266 lines)
- `desktopApp/build.gradle.kts` (165 lines)
- `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` (735 lines)
- `androidApp/src/test/kotlin/dev/tabbyrush/android/build-config-test.kt` (19 lines)
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` (1176 lines)
- `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/dot-env-loader.kt` (82 lines)
- `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-task-surfaces-test.kt` (1040 lines)
- `integration/glean-hub/tabbyrush-schema.json` (27 lines)
- `integration/glean-hub/tabbyrush-standard-schema.json` (27 lines)
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt` (188 lines)

### Files Deleted
- C/sync/contract-transition-coordinator.kt; C/contract/strict-sync-contract.kt; C/contract/strict-sync-v2-contract.kt; C/contract/strict-sync-v2-digest.kt; C/contract/strict-sync-v3-contract.kt; C/contract/strict-sync-v3-digest.kt.
- T/sync/contract-transition-test.kt; T/contract/strict-digest-test.kt; T/contract/strict-v2-digest-vector-test.kt; T/contract/strict-v2-digest-extended-test.kt; T/contract/strict-v3-digest-vector-test.kt.
- C = shared/src/commonMain/kotlin/dev/tabbyrush/shared; T = shared/src/commonTest/kotlin/dev/tabbyrush/shared.

### Tasks Completed
- Ordinary push/pull/delta DTOs and exact POST allowlist; Bearer/application headers retained; no strong/bootstrap/epoch/receipt/digest fallback. Independent delta decoding permits a valid push acknowledgement to survive an invalid pull. Positive exact schemaVersion metadata never acts as a feature gate.
- Account-bound token admission now occurs under the auth session mutex; old sync clients cannot obtain another account's token. Generation/frozen checks retained around asynchronous work; no automatic task POST refresh replay.
- Room4 explicit 1→2→3→4 chain: all old outbox columns archived as SQLite values; FLOAT bits/BLOB bytes tagged losslessly; typed task/outbox/draft visibility, settings and reminder ledger retained. Old bases never become standard-confirmed merely through migration. Unique evidence linkage and durable translation outcomes prevent replayed translation.
- Raw base/staging JSON, string composite checkpoints, scan generation/state, independent Hub observation, confirmed row versions and local revisions persisted. Page/checkpoint/schema observation and completed-generation promotion transactional; incomplete scans never establish absence.
- FIFO head-only dispatch, parent-first admission, immutable persisted envelopes, exact accepted tuples, terminal acceptance/convergence evidence and newer local edits preserved. Unknown/nested/null fields merge through captured known-field intent only. Exhaustion retains work instead of wrapping.
- Missing confirmed/uncertain rows never become creates. Unknown retention bound deliberately forces full scan before every reconnect/round, followed by incremental catch-up; malformed known data remains raw/reviewable and cannot schedule. Uncertain delivery can converge only on matching version/full JSON/deletion, explicitly not a receipt.
- Legacy PENDING alone, null/ambiguous history, Room1 provenance, dispatched/uncertain/superseded work require review. Only complete explicit zero-dispatch history plus identity/protocol/revision/state/mask proof translates automatically after fresh scan; historical evidence remains intact.
- Hierarchy/group deletion/last-child completion barriers, late-child per-row cleanup, known-deleted-parent visibility/scheduling suppression, unknown-parent recovery, own deadlines, scheduleToken/ledger, native surfaces and account leave preserved. Review offers refreshed intent-only reapply, discard and fresh-root copy; group cleanup explicitly described as non-atomic.
- Extracted ordinary task validation/reminder limits and unchanged SHA implementation. AccountKey framing unchanged; added ASCII/Unicode goldens, retained announcement hash vectors. Removed strict feature flags, upgrade warnings, reset/transition contracts and compatibility aliases across native callers.
- Active and named ordinary integration schemas contain all 17 task fields with no strict mode/fingerprint. Historical integration schemas/vectors and Room exports 1–3 retained; no fabricated Room4 export.

### Callsite Inventory / Intentionally Unchanged
- Shared auth transport, store/DAO, coordinator/reconciliation/scan, task validation/hash contracts, scheduling projection, native list/editor/conflict/feedback, Android SessionViewModel and DesktopController migrated. Remote inbox/stream calls only changed to named auth block arguments; announcement behavior otherwise unchanged.
- Additional native discoveries: safe-errors.kt, common strings, remote auth argument adapters, dotenv loader and build-config fixtures included under native-layer ownership; no cross-owner conflicts encountered.
- Both platform Room builders already use addMigrations(*DatabaseMigrations.ALL), so now include 3→4 without edits. Runtime factories/workers/reminder/surface adapters retain existing coordinator/store interfaces; scoped reference searches found no remaining retired strict API consumers. No configured LSP available; used scoped source references/search inventory.

### Tests Status
- Type check/build, unit tests, integration tests, formatters: NOT RUN, per coordinator-only integrated-wave instruction. No coverage/pass claims.
- Auth/transport cases authored: exact method/path/headers, schema metadata kinds, differing nested counters, partial delta decoding, one POST after refresh, captured-account rejection.
- Real BundledSQLite cases authored: historical exports 1/2/3→4, lossless unusual storage classes, restart, page/checkpoint and promotion rollback via SQLite abort triggers, translation rollback/idempotence, FIFO/exact ack/newer edits, frozen uncertainty, missing-row no-recreate, lost delta/full reconnect scan, opaque JSON reapply, root copy, hierarchy/delete/preservation, deadlines/tokens/settings/ledger.
- Obsolete strict wire/receipt/digest/transition suites removed/replaced with consumer behavior; primitive in-memory DAO retained only for existing lifecycle/unit fixtures, not claimed as SQLite atomicity proof. No new permanent UI suite.
- Only non-test API investigation performed: javap of cached SQLite API to resolve use/close/getColumnType; independent Python hashing derived identity test goldens. No Kotlin/Gradle execution.

### Pending Generation / Exact Coordinator Commands
- Prerequisites: sanitized source mirror without .env/production configuration; Java21, approved Android SDK, pinned Gradle dependencies with verification enabled; protected freshly provisioned test TABBYRUSH_API_KEY; APPROVED_HUB_ORIGIN=http://127.0.0.1:3100. Shared unit/SQLite suites need no live Hub/Mongo; native two-device smoke needs the isolated ordinary Hub and fresh test accounts.
- Room4 generation-only compile: `.\gradlew.bat :shared:compileKotlinJvm -PdesktopTarget=windows -PisolatedTestConfig=true -PhubOrigin=http://127.0.0.1:3100`.
- Compiler-owned output: `shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/4.json`; integrate actual generated output from the sanitized mirror, preserve exports 1–3. KSP is already wired by shared/build.gradle.kts; no manual fixture stand-in was written.
- Integrated wave: `.\gradlew.bat :shared:jvmTest :androidApp:testDebugUnitTest :desktopApp:test :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest -PdesktopTarget=windows -PisolatedTestConfig=true -PhubOrigin=http://127.0.0.1:3100`.
- Desktop smoke: isolated LOCALAPPDATA profile, explicit origin/fresh key, `.\gradlew.bat :desktopApp:runDebug -PdesktopTarget=windows -PisolatedTestConfig=true`. Property forwards into Desktop tests/runDebug and disables implicit dotenv probes; explicit disposable dotenv fixture files remain testable.
- Android isolated property skips dotenv/Firebase config discovery, applies debug-only applicationIdSuffix `.standardsmoke`; namespace/activity remain dev.tabbyrush.android. Release identity unchanged; isolated config is rejected for release packaging, normal HTTPS/key/signing gates retained. Verify actual merged manifest and instrumentation target before installation; never install over the user's normal package or clear its data.

### Issues / Risks / Parent Script Handoff
- No implementation blocker identified; compile/schema-generation and real acceptance remain unobserved. Default full scans intentionally cost more network traffic until a trustworthy retention bound exists. Ambiguous historical pending work intentionally requires review rather than speculative translation.
- Server provides no group transaction, permanent tombstones, strict receipts or hierarchy enforcement; client retains local suppression/evidence, not a replacement strict engine. Existing Windows notification/native acceptance holds unchanged.
- Obsolete script references/artifact consumers: hub-strict-upgrade.mjs, hub-strict-recovery.js, strict-v2-vector-runner.mjs, strict-v3-vector-runner.mjs, strict-v2-cutover-rehearsal.mjs; parent already reported removing/reworking strict tooling and ordinary hub-live-smoke/schema-provision/proxy. No active native dependency on removed scripts; historical integration fixtures stay evidence-only.
- Unresolved questions: none. Parent owns execution evidence, docs/changelog and phase/overview status updates.
