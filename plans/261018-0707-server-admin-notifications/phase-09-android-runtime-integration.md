# Phase 09 — Android Firebase runtime, install operations and inbox integration

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **C**; implementation start: [P01](phase-01-contract-build-gate.md), [P03](phase-03-fcm-http-v1-transport.md), [P05](phase-05-shared-remote-core.md), [P06](phase-06-shared-inbox-ui.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P11](phase-11-operations-config-deployment.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Exclusive owner **P09**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: in-progress; progress: 100% implementation (runtime device gate pending); effort: **32h**.
- Description: Android Firebase runtime, install operations, Keystore store and inbox integration implemented; release security audit unit (21) and source check (5) PASS; physical connected device execution and signed APK verification held under Gate G2.

## Key Insights
- Background notification+data auto-renders without Firebase service callback; no client exactly-once claim.
- Existing Android close may swallow drain failure; fix only lifecycle safety needed by remote integration.

## Requirements
- Google-enabled FCM foreground/background, cold/warm tap, permission denial, durable inbox fallback.
- Install operation proof/fencing persists independently of account; offline unbind never blocks cleanup.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Firebase SDK init via external public project config/FirebaseOptions. Messaging service feeds install serializer and Android generic adapter; shared controller never posts Android SSE alerts. Runtime/factory/view-model/activity own all existing host integration. Keystore no-backup install state is not inbox/read persistence.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/tabby-rush/androidApp/build.gradle.kts` | modify | Firebase dependency, external public config validation/generated BuildConfig and Android test wiring. |
| `/home/loidinh/WS/tabby-rush/androidApp/gradle.lockfile` | modify | Android resolution owner; provenance/checksums coordinated to01. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/AndroidManifest.xml` | modify | Native Messaging service/default channel/icon and permission; preserve other components. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime.kt` | modify | Own remote controller lifetime and fenced close/drain. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/account-runtime-factory.kt` | modify | Compose remote core/Android adapter/install coordinator and existing leave gates. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` | modify | Firebase init and retain fenced runtime on failed local drain, not clear active reference under surviving work. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` | modify | Expose remote state/actions and current runtime generation to UI. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` | modify | Bind inbox UI/permission and consume exact cold/warm remote extras safely. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/composite-leave-scheduler-cancel-gate.kt` | modify | Join remote local cancellation; best-effort unbind never blocks local leave. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-firebase-messaging-service.kt` | create | Native token/foreground message/onDeletedMessages entry with fixed generic alert only. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-notification-adapter.kt` | create | Separate channel/group/tag, capability checks, immutable intents and scoped cancellation. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-install-store.kt` | create | Keystore-encrypted no-backup install ID/proof/generation and pending install command atomic persistence. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-install-coordinator.kt` | create | Serialized persist-before-enqueue binding/token/switch/unbind operations and lost-response handling. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/kotlin/dev/tabbyrush/android/remote-notification-intents.kt` | create | Shared exact payload-key parsing, consumed-once target and auth-generation routing. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/res/drawable/remote_notification_icon.xml` | create | Dedicated monochrome system remote icon. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/main/res/values/remote-notification-strings.xml` | create | Fixed generic channel and capability copy, no private content resources. |
| `/home/loidinh/WS/tabby-rush/scripts/release-security-check.mjs` | modify | Narrow merged Firebase receiver allowlist preserving all other exported/service/provider prohibitions. |
| `/home/loidinh/WS/tabby-rush/scripts/release-security-check.test.mjs` | modify | Positive actual SDK merge and negative permission/action/authority/export fixtures. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/test/kotlin/dev/tabbyrush/android/remote-install-coordinator-test.kt` | create | Persistent generation races, lost response, logout offline and token rotation tests. |
| `/home/loidinh/WS/tabby-rush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/remote-notification-integration-test.kt` | create | Cold/warm taps, permission/channel, generation and local drain UI/device tests. |
| `/home/loidinh/WS/tabby-rush/androidApp/build/generated/source/buildConfig/debug/dev/tabbyrush/android/BuildConfig.java` | generate | Public Firebase debug options from external config, no service credentials; generated only. |
| `/home/loidinh/WS/tabby-rush/androidApp/build/generated/source/buildConfig/release/dev/tabbyrush/android/BuildConfig.java` | generate | Validated release options matching Android package; generated only. |

## File Ownership
- P09 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Consume externally supplied Firebase client config path from11; validate package dev.tabbyrush.android/project/sender/app ID before enabled build. Generate BuildConfig public options, initialize FirebaseApp with FirebaseOptions; no service key or required committed google-services.json/plugin. Firebase auto-init occurs only after valid config; signed-out token stored privately.
2. Declare nonexported custom MessagingService, MESSAGING_EVENT and default remote channel/icon metadata; preserve launcher/widget/reminder policy. Implement POST_NOTIFICATIONS/channel capability display without forcing permission or settings changes.
3. Implement remote-install-store with encrypted no-backup atomic command/generation persistence and fresh random ID/proof. Serialize all token/account operations; increment generation before enqueue, bind captured auth generation, exact same-command replay, discarded old-account intent. Reinstall/lost Keystore uses new identity and fresh Firebase token on conflict, never guesses server proof.
4. Implement foreground generic post and SDK background sole rendering; exact type/appId/messageId keys and stable remote tag/group/channel. onDeletedMessages and app foreground refresh inbox silently; never SSE/inbox-generated Android OS alert.
5. Handle onCreate/onNewIntent with explicit immutable PendingIntent, exact key/UUID validation, consumed-once target. Signed-out/wrong-account/expired target falls to safe inbox/auth UI, no retained target auto-open after unrelated login. Fetch current authorization before content.
6. Wire P05/P06 runtime state/actions/foreground refresh and native tap into existing factory/view-model/activity. Account generation+wiping guard every callback, including SDK token updates signed out.
7. Join leave gate with quiesce/drain/remote-only cancel. Best-effort unbind uses captured old owner/expected binding, no dependency on network/token deletion. If local drain fails retain fenced runtime handle and prevent publishing a replacement; do not close resources under surviving work. Keep existing reminder/sticky/task cancellation semantics.
8. Inspect actual merged release manifest with locked Firebase SDK. Add only exact protected FirebaseInstanceIdReceiver policy in03; keep providers/services nonexported, validate FirebaseInitProvider authority. Negative fixtures cover wrong/missing SEND permission, extra action/category/data and arbitrary exported component. No blanket Firebase exception.
9. Add owner tests for rotation/invalid old token, stale register arriving after switch (both orders), lost-response replay, offline logout and late generic tap. Actual FCM network foreground/background/force-stop/denial/cold/warm and regression proof runs with02/03/11 in12.

## Todo list
- [x] Read owned files/current upstream changes and confirm frozen contract.
- [x] Complete all numbered steps with deterministic failure handling.
- [x] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [x] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Permitted device shows generic foreground/background alert; cold/warm tap opens only currently authorized detail and marks read online.
- Denied/offline does not bypass OS; inbox recovers and local cleanup succeeds offline.
- Persisted operation fences survive crash/restart and stale cleanup cannot remove new binding; audit passes actual merged manifest without blanket exceptions.

Future targeted verification (not executed by this plan):
- `./gradlew :androidApp:testDebugUnitTest :androidApp:connectedDebugAndroidTest --dependency-verification=strict`
- `node --test scripts/release-security-check.test.mjs; node scripts/release-security-check.mjs --diagnose-source`
- `Release owner runs signed :androidApp:assembleRelease with strict verification then actual APK merged-manifest audit (not source-only) and device evidence.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- SDK merged components conflict existing audit: explicit narrow protected receiver exception required.
- Offline server unbind cannot guarantee no future generic push; privacy protected by generic payload/current authorization.

## Security Considerations
- Proof/token encrypted no-backup, HTTP proof header redacted; service credential never enters APK.
- Scoped remote cleanup excludes reminders/sticky; late generic OS auto-render accepted but never old-account content.

## Next steps
- Consumers/unblocked phases: [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
