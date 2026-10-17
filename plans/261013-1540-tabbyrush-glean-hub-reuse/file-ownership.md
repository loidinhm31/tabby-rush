# Exclusive future implementation file ownership

Date: 2026-10-13. Planning registry, not files implemented. D=`/home/loidinh/WS/tabbyrush`; H=`/home/loidinh/WS/glean-hub`. Every row expands to the literal absolute path below; no glob ownership.

Only phase03 may alter external Hub source,exactly its listed strict-extension/model/registration/wiring/guard/test/docs paths. Parent-owned planning architecture/contracts/decisions/scouts/research read-only;phase/index/registry/completion planning artifacts excluded from future implementation registry. Existing embedded apps/shared Rust/TS libraries/adminUI/auth server and legacy sync engine/service/jobs unchanged intentionally.

## Path registry

| Path | Phase | Action |
|---|---|---|
| `/home/loidinh/WS/tabbyrush/scripts/start-xpra.sh` | 01 | create |
| `/home/loidinh/WS/tabbyrush/scripts/android-sdk-env.sh` | 01 | create |
| `/home/loidinh/WS/tabbyrush/scripts/start-android-emulator.sh` | 01 | create |
| `/home/loidinh/WS/tabbyrush/docs/headless-android-setup.md` | 01 | create |
| `/home/loidinh/WS/tabbyrush/docs/host-tooling-evidence.md` | 01 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-tooling-launcher.png` | 01 | create |
| `/home/loidinh/WS/tabbyrush/settings.gradle.kts` | 02 | create |
| `/home/loidinh/WS/tabbyrush/build.gradle.kts` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradle.properties` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradle/libs.versions.toml` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradle/verification-metadata.xml` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradle/wrapper/gradle-wrapper.jar` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradle/wrapper/gradle-wrapper.properties` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradlew` | 02 | create |
| `/home/loidinh/WS/tabbyrush/gradlew.bat` | 02 | create |
| `/home/loidinh/WS/tabbyrush/.gitignore` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/build.gradle.kts` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/gradle.lockfile` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/hub-config.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-identity.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-key.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/credential-vault.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/auth-session.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/account-store.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-coordinator.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/sync-scheduler.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-action.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/app-factories.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/safe-errors.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/title-policy.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/endpoint-policy.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-contract.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/strict-digest-test.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/title-policy-test.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/endpoint-policy-test.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/account-key-test.kt` | 02 | create |
| `/home/loidinh/WS/tabbyrush/docs/system-architecture.md` | 02 | create |
| `/home/loidinh/WS/tabbyrush/docs/api-contract.md` | 02 | create |
| `/home/loidinh/WS/tabbyrush/docs/code-standards.md` | 02 | create |
| `/home/loidinh/WS/tabbyrush/docs/toolchain-versions.md` | 02 | create |
| `/home/loidinh/WS/tabbyrush/docs/foundation-evidence.md` | 02 | create |
| `/home/loidinh/WS/tabbyrush/integration/glean-hub/tabbyrush-schema.json` | 03 | create |
| `/home/loidinh/WS/tabbyrush/scripts/hub-schema-provision.mjs` | 03 | create |
| `/home/loidinh/WS/tabbyrush/scripts/hub-compatibility-smoke.mjs` | 03 | create |
| `/home/loidinh/WS/tabbyrush/docs/hub-schema-integration.md` | 03 | create |
| `/home/loidinh/WS/tabbyrush/docs/hub-compatibility-acceptance.md` | 03 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/hub-compatibility-summary.md` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/app.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/api.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/mod.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/models/strong_sync.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/app_service.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/mod.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/services/strong_sync_tests.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/apps.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/mod.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strong_sync.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/routes/strong_sync_route_tests.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/mod.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/middleware/sync_mode_guard.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/strong_sync_error.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/lib.rs` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/src/bin/strong_sync_admission.rs` | 03 | create |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/CLAUDE.md` | 03 | modify |
| `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync/CHANGELOG.md` | 03 | create |
| `/home/loidinh/WS/glean-hub/docs/tabbyrush-strict-sync.md` | 03 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-dtos.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-http-client.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-client.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/hub-auth-session.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/auth/single-flight-refresh.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidMain/kotlin/dev/tabbyrush/shared/auth/android-http-engine.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidMain/kotlin/dev/tabbyrush/shared/auth/android-keystore-vault.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/jvmMain/kotlin/dev/tabbyrush/shared/auth/jvm-http-engine.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-client-test.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/hub-auth-session-test.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/auth/single-flight-refresh-test.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/auth/android-keystore-vault-test.kt` | 04 | create |
| `/home/loidinh/WS/tabbyrush/docs/authentication-and-credentials.md` | 04 | create |
| `/home/loidinh/WS/tabbyrush/docs/auth-acceptance.md` | 04 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-database.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/server-base-entity.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/outbox-entity.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/editor-draft-entity.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/scan-staging-entity.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/account-sync-meta-entity.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/tabbyrush-dao.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/database-migrations.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/room-account-store.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-dtos.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/staged-full-scan.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/android-database-builder.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/android-account-files.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/jvm-database-builder.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/jvm-account-files.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/hub-sync-client-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/staged-full-scan-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/outbox-reconciliation-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/sync/account-leave-protocol-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/jvmTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/androidInstrumentedTest/kotlin/dev/tabbyrush/shared/data/room-account-store-test.kt` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/schemas/dev.tabbyrush.shared.data.TabbyRushDatabase/1.json` | 05 | create |
| `/home/loidinh/WS/tabbyrush/docs/local-storage-and-sync.md` | 05 | create |
| `/home/loidinh/WS/tabbyrush/docs/sync-acceptance.md` | 05 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/account-form.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/access-request-panel.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/sync-feedback.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/conflict-review.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/leave-guard.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/license-notices.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-theme.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-typography.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/font/cal_sans_regular.ttf` | 06 | create only after rights/coverage audit; omit if documented system-font choice |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/font/alexandria_regular.ttf` | 06 | create only after rights/coverage audit; omit if documented system-font choice |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/font/alexandria_semibold.ttf` | 06 | create only after rights/coverage audit; omit if documented system-font choice |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/files/cal-sans-license.txt` | 06 | create only after rights/coverage audit; omit if documented system-font choice |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/files/alexandria-license.txt` | 06 | create only after rights/coverage audit; omit if documented system-font choice |
| `/home/loidinh/WS/tabbyrush/shared/src/commonMain/composeResources/values/strings.xml` | 06 | create |
| `/home/loidinh/WS/tabbyrush/shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt` | 06 | create |
| `/home/loidinh/WS/tabbyrush/docs/design-guidelines.md` | 06 | create |
| `/home/loidinh/WS/tabbyrush/docs/font-provenance.md` | 06 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/build.gradle.kts` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/gradle.lockfile` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/AndroidManifest.xml` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/res/xml/backup_rules.xml` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/res/xml/data_extraction_rules.xml` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/debug/AndroidManifest.xml` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/debug/res/xml/network_security_config.xml` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/android-sync-scheduler.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/background-isolation-test.kt` | 07 | create |
| `/home/loidinh/WS/tabbyrush/scripts/android-smoke.sh` | 07 | create |
| `/home/loidinh/WS/tabbyrush/scripts/debug-fault-proxy.mjs` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/android-acceptance.md` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-test-summary.md` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-empty.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-populated.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-completed.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-editor.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-error.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-conflict.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-access-pending.png` | 07 | create |
| `/home/loidinh/WS/tabbyrush/scripts/hub-live-smoke.mjs` | 08 | create |
| `/home/loidinh/WS/tabbyrush/scripts/release-security-check.mjs` | 08 | create |
| `/home/loidinh/WS/tabbyrush/scripts/hub-strict-recovery.js` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/deployment-guide.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/security-and-recovery.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/release-acceptance.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/project-overview-pdr.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/codebase-summary.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/development-roadmap.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/project-changelog.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/README.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-summary.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/hub-recovery-summary.md` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-empty.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-populated.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-completed.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-editor.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-error.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-conflict.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-access-pending.png` | 08 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/build.gradle.kts` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/gradle-linux.lockfile` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/credential-vault-provider.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/platform-vault-dispatcher.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/desktop-sync-scheduler.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/main/kotlin/dev/tabbyrush/desktop/single-instance-guard.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxMain/kotlin/dev/tabbyrush/desktop/linux/linux-secret-service-vault.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxMain/resources/META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-lifecycle-test.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/test/kotlin/dev/tabbyrush/desktop/platform-vault-dispatcher-test.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/linuxTest/kotlin/dev/tabbyrush/desktop/linux/linux-secret-service-vault-test.kt` | 09 | create |
| `/home/loidinh/WS/tabbyrush/scripts/linux-package-smoke.sh` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/linux-desktop-guide.md` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/linux-acceptance.md` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-test-summary.md` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-empty.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-populated.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-completed.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-editor.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-error.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-conflict.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/linux-expanded.png` | 09 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/gradle-windows.lockfile` | 10 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/windowsMain/kotlin/dev/tabbyrush/desktop/windows/windows-dpapi-vault.kt` | 10 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/windowsMain/resources/META-INF/services/dev.tabbyrush.desktop.CredentialVaultProvider` | 10 | create |
| `/home/loidinh/WS/tabbyrush/desktopApp/src/windowsTest/kotlin/dev/tabbyrush/desktop/windows/windows-dpapi-vault-test.kt` | 10 | create |
| `/home/loidinh/WS/tabbyrush/scripts/windows-package-smoke.ps1` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/windows-desktop-guide.md` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/windows-acceptance.md` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-test-summary.md` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-empty.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-populated.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-completed.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-editor.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-error.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-conflict.png` | 10 | create |
| `/home/loidinh/WS/tabbyrush/docs/evidence/windows-expanded.png` | 10 | create |

## Ownership rules
- One path, one original owner for its entire life; integration failures reopen that owner. No shared editing, alias path, ownership transfer or catch-all glob. New tracked paths require a reviewed registry amendment before creation.
- Related code files in each phase enumerate exactly that phase’s rows. Consumer links are read-only context, not ownership. No files are scheduled for deletion.
- Config, wrappers, generated **tracked** locks, Room schema exports, tests, licenses and sanitized evidence are rows. Build caches, generated Res/KSP sources, APK/RPM/MSI/JRE outputs, local SDK/AVD state and private test logs/DBs are untracked outputs. No empty placeholder locks, schema, fonts or screenshots.
-02 owns root/shared/catalog/verification and shared lock forever, including legitimate later Windows checksums.07 owns Android config/lock/manifest.09 owns frozen desktop build and Linux target lock.10 owns Windows target lock/provider/resources/tests only; does not edit09 artifacts.
-06 conditional font/license rows have one owner even if omitted with documented accessible system font decision; never fabricate font assets/notices.
-08 alone updates README/PDR/summary/roadmap/changelog after09/10; platform phases hand back evidence and requests, not direct edits.
- External Cargo manifest/lock,legacy service/routes/errors/dual-auth/jobs,core DTO/clients,embedded apps,adminUI/auth server/notification source unchanged.03 owns separate strict errors/mode middleware and admission,not shared legacy repair. Existing cleanup/direct-writer bypasses addressed by namespace isolation and real tests.08 recovery script is maintenance-only,not reset API; fresh epoch/new namespace/_id/index/import validation and registry CAS without dropping old namespace.

## Counts and exclusions
215 exclusive paths:01=6,02=37,03=26,04=14,05=29,06=20,07=25,08=20,09=23,10=15.03 includes6 TabbyRush artifacts and20 external Hub paths;all external edits phase03. Conditional five06 font/license rows included if omitted honestly.08 recovery script exclusive;03 read-only candidate admission helper invokes same validator,noHTTPreset.02 public contract/digest,05 wire adapter exclusive. No embedded/client migration. Counts planned enumeration,not validation evidence;parent performs exact-set/link checks.

## Unresolved questions
Exact dependency tuple, font rights/coverage and approved native metadata are implementation freeze gates. No secret values needed in this registry.
