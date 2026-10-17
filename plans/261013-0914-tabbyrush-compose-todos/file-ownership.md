# Exclusive future file ownership

All paths repo-relative; every row has exactly one owner. All are **future** artifacts, not created by this planning task. Listed create paths include tests/runbooks/evidence. No owner may edit another row. Runtime local.properties, .dev.vars, signing files, SDK/AVD, .wrangler state and build outputs are ignored machine-local/generated artifacts, never tracked ownership transfers. Secrets are never committed.

| Path | Phase | Action |
|---|---|---|
| `scripts/start-xpra.sh` | 01 | create |
| `scripts/android-sdk-env.sh` | 01 | create |
| `scripts/start-android-emulator.sh` | 01 | create |
| `docs/headless-android-setup.md` | 01 | create |
| `docs/host-tooling-evidence.md` | 01 | create |
| `settings.gradle.kts` | 02 | create |
| `build.gradle.kts` | 02 | create |
| `gradle.properties` | 02 | create |
| `gradle/libs.versions.toml` | 02 | create |
| `gradle/verification-metadata.xml` | 02 | create |
| `gradle/wrapper/gradle-wrapper.jar` | 02 | create |
| `gradle/wrapper/gradle-wrapper.properties` | 02 | create |
| `gradlew` | 02 | create |
| `gradlew.bat` | 02 | create |
| `.gitignore` | 02 | create |
| `shared/build.gradle.kts` | 02 | create |
| `shared/gradle.lockfile` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-models.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/api-result.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-repository.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-ui-state.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-action.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/todo-controller.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/session-repository-factory.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/title-policy.kt` | 02 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/endpoint-policy.kt` | 02 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/title-policy-test.kt` | 02 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/contract/endpoint-policy-test.kt` | 02 | create |
| `docs/system-architecture.md` | 02 | create |
| `docs/api-contract.md` | 02 | create |
| `docs/code-standards.md` | 02 | create |
| `docs/toolchain-versions.md` | 02 | create |
| `worker/migrations/0001-create-todos.sql` | 03 | create |
| `worker/test/migrations.test.mjs` | 03 | create |
| `docs/d1-schema-and-migrations.md` | 03 | create |
| `worker/package.json` | 04 | create |
| `worker/package-lock.json` | 04 | create |
| `worker/tsconfig.json` | 04 | create |
| `worker/wrangler.jsonc` | 04 | create |
| `worker/vitest.config.ts` | 04 | create |
| `worker/src/index.ts` | 04 | create |
| `worker/src/auth.ts` | 04 | create |
| `worker/src/validation.ts` | 04 | create |
| `worker/src/cursor.ts` | 04 | create |
| `worker/src/todo-store.ts` | 04 | create |
| `worker/src/http-errors.ts` | 04 | create |
| `worker/test/auth.test.ts` | 04 | create |
| `worker/test/validation.test.ts` | 04 | create |
| `worker/test/crud.test.ts` | 04 | create |
| `worker/test/pagination.test.ts` | 04 | create |
| `docs/worker-development.md` | 04 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/wire-models.kt` | 05 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/data/ktor-todo-repository.kt` | 05 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/session-controller.kt` | 05 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/todo-reducer.kt` | 05 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/state/mutation-reconciliation.kt` | 05 | create |
| `shared/src/androidMain/kotlin/dev/tabbyrush/shared/data/platform-client.kt` | 05 | create |
| `shared/src/jvmMain/kotlin/dev/tabbyrush/shared/data/platform-client.kt` | 05 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/data/repository-test.kt` | 05 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/state/session-controller-test.kt` | 05 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/state/reconciliation-test.kt` | 05 | create |
| `docs/client-data-state.md` | 05 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/session-form.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-feedback.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-theme.kt` | 06 | create |
| `shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-typography.kt` | 06 | create |
| `shared/src/commonMain/composeResources/font/cal_sans_regular.ttf` | 06 | create |
| `shared/src/commonMain/composeResources/font/alexandria_regular.ttf` | 06 | create |
| `shared/src/commonMain/composeResources/font/alexandria_semibold.ttf` | 06 | create |
| `shared/src/commonMain/composeResources/files/cal-sans-license.txt` | 06 | create |
| `shared/src/commonMain/composeResources/files/alexandria-license.txt` | 06 | create |
| `shared/src/commonMain/composeResources/values/strings.xml` | 06 | create |
| `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt` | 06 | create |
| `docs/design-guidelines.md` | 06 | create |
| `docs/font-provenance.md` | 06 | create |
| `androidApp/build.gradle.kts` | 07 | create |
| `androidApp/gradle.lockfile` | 07 | create |
| `androidApp/src/main/AndroidManifest.xml` | 07 | create |
| `androidApp/src/debug/AndroidManifest.xml` | 07 | create |
| `androidApp/src/debug/res/xml/network-security-config.xml` | 07 | create |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | 07 | create |
| `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | 07 | create |
| `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt` | 07 | create |
| `scripts/android-smoke.sh` | 07 | create |
| `scripts/debug-fault-proxy.mjs` | 07 | create |
| `docs/android-acceptance.md` | 07 | create |
| `docs/evidence/android-debug-empty.png` | 07 | create |
| `docs/evidence/android-debug-populated.png` | 07 | create |
| `docs/evidence/android-debug-completed.png` | 07 | create |
| `docs/evidence/android-debug-editor.png` | 07 | create |
| `docs/evidence/android-debug-error.png` | 07 | create |
| `scripts/api-live-smoke.mjs` | 08 | create |
| `scripts/release-security-check.mjs` | 08 | create |
| `docs/deployment-guide.md` | 08 | create |
| `docs/security-and-recovery.md` | 08 | create |
| `docs/release-acceptance.md` | 08 | create |
| `docs/project-overview-pdr.md` | 08 | create |
| `docs/codebase-summary.md` | 08 | create |
| `docs/development-roadmap.md` | 08 | create |
| `docs/project-changelog.md` | 08 | create |
| `README.md` | 08 | create |
| `docs/evidence/android-release-empty.png` | 08 | create |
| `docs/evidence/android-release-populated.png` | 08 | create |
| `docs/evidence/android-release-completed.png` | 08 | create |
| `docs/evidence/android-release-editor.png` | 08 | create |
| `docs/evidence/android-release-error.png` | 08 | create |
| `desktopApp/build.gradle.kts` | 09 | create |
| `desktopApp/gradle.lockfile` | 09 | create |
| `desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt` | 09 | create |
| `desktopApp/src/test/kotlin/dev/tabbyrush/desktop/desktop-lifecycle-test.kt` | 09 | create |
| `scripts/linux-package-smoke.sh` | 09 | create |
| `docs/linux-desktop-guide.md` | 09 | create |
| `docs/linux-acceptance.md` | 09 | create |
| `docs/evidence/linux-empty.png` | 09 | create |
| `docs/evidence/linux-populated.png` | 09 | create |
| `docs/evidence/linux-completed.png` | 09 | create |
| `docs/evidence/linux-editor.png` | 09 | create |
| `docs/evidence/linux-error.png` | 09 | create |
| `docs/evidence/linux-expanded.png` | 09 | create |
| `scripts/windows-package-smoke.ps1` | 10 | create |
| `docs/windows-desktop-guide.md` | 10 | create |
| `docs/windows-acceptance.md` | 10 | create |
| `docs/evidence/windows-empty.png` | 10 | create |
| `docs/evidence/windows-populated.png` | 10 | create |
| `docs/evidence/windows-completed.png` | 10 | create |
| `docs/evidence/windows-editor.png` | 10 | create |
| `docs/evidence/windows-error.png` | 10 | create |
| `docs/evidence/windows-expanded.png` | 10 | create |

## Rules and exceptions
- Phase02 owns root config/catalog/wrapper/shared build and shared lock;07/09 own their module scripts/locks. Configure Gradle locking for module dependency configurations; each owner generates/reviews its exact lock at its first runnable gate, no empty placeholder lock. Verification metadata stays02-owned and is reopened for07/09/10 target dependencies. Compose generated Res/classes/build outputs belong to generating02 shared script, never tracked source.
- Phase 04 owns every Worker manifest/config/test harness dependency; phase 03 owns only migration SQL, node built-in migration test and schema runbook. Phase 03 test uses Node24 node:sqlite; deployed D1 semantics are proven by phase04/08.
- Phase 09 configures Linux RPM and Windows MSI targets once. Phase10 owns only PowerShell/runbook/evidence; packaging repairs reopen09. No CI workflow proposed for manual default; optional CI needs separately approved owned paths.
- Font rows exist only after verified redistribution rights/glyph coverage. If unavailable, phase06 selects accessible system font, omits unlicensed assets and records actual omission; do not create pretend font/license files.
- Screenshots are captured from actual running apps at runtime gates, never placeholders. Each owner records dimensions/device/version/commit in its acceptance doc.
- Phase08 owns general README/changelog/roadmap/summary throughout delivery; initial docs at Android release state Linux/Windows pending. Later platform status updates go back to08, not platform owners. No essential docs wait for Windows hardware.
- New files require ownership amendment before implementation. Same owner may repair its files after later gates; no extra phase becomes owner. Automatic package manager edits to another owner’s catalog/lock/config require reopening that owner.
- Plan artifacts are owned solely by PlannerResume for controlled completion of this assignment; parent research/reports/scout remain read-only.

## Unresolved questions
Font assets conditional on license audit; signing material and Cloudflare account access must be provisioned outside tracked files.
