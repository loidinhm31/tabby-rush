# Phase 07 — Android composition and local runtime acceptance

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Host setup](phase-01-headless-tooling.md) · [Foundation](phase-02-frozen-foundation.md) · [UI/theme](phase-06-compose-ui-theme.md)
- [Android network security](https://developer.android.com/privacy-and-security/security-config)

## Parallelization Info
Group D after03–06 code/tests ready; passing tests are not an entry prerequisite. First blocking gate executes all lanes after Android script lands. Own composition/instrumentation/evidence only; faults reopen original owners. One main integrator runs checks, no concurrent build/test storms.

## Overview
- Date: 2026-10-13
- Description: Thin Android app around real shared UI/controller and actual local Worker/D1.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 6h

## Key Insights
Foundation intentionally has no stub app. Real API plus actual device UI is required, not preview/assemble-only. Emulator alias10.0.2.2 reaches host loopback; USB reverse uses127.0.0.1. Android debug ID matches release: switching signatures requires conscious uninstall, not silently replacing app data.

## Requirements
Actual CRUD/filter/count/page flows; network/auth/ambiguous mutation/conflict handling, drafts retained, debug-only local HTTP, rotation/background/process lifecycle, accessible theme, no persistent/logged token. Release signing configuration belongs07 but credentials are provisioned08 outside source.

## Architecture
MainActivity collects controller state with lifecycle awareness and calls shared TabbyRushTheme/TabbyRushApp. Retained SessionViewModel owns controller and viewModelScope, closes onCleared; no Activity reference, SavedStateHandle or persisted secret. Android `BuildConfig.DEBUG` selects mode; generated buildConfig enabled. Main manifest Internet permission and cleartext false; debug manifest merges an exact-host network-security config only for allowed aliases. No all-domain cleartext override.

## Related code files
Future create only, no existing application files modified/deleted:
- `androidApp/build.gradle.kts`
- `androidApp/gradle.lockfile`
- `androidApp/src/main/AndroidManifest.xml`
- `androidApp/src/debug/AndroidManifest.xml`
- `androidApp/src/debug/res/xml/network-security-config.xml`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt`
- `androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt`
- `androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt`
- `scripts/android-smoke.sh`
- `scripts/debug-fault-proxy.mjs`
- `docs/android-acceptance.md`
- `docs/evidence/android-debug-empty.png`
- `docs/evidence/android-debug-populated.png`
- `docs/evidence/android-debug-completed.png`
- `docs/evidence/android-debug-editor.png`
- `docs/evidence/android-debug-error.png`

## File Ownership
Only phase07 registry rows. Read contracts/source without claiming them; build/catalog/shared lock/verification metadata02, Worker config04, common state05/UI06. Signing keystore/passwords and local SDK properties ignored/untracked. Runtime captures must be real sanitized acceptance data.

## Implementation Steps
1. Create Android script using02 pinned catalog/plugins/SDK/bytecode tuple; namespace/applicationId dev.tabbyrush.android, min26 approved02, versionCode1/versionName1.0.0, shared dependency, Compose/BuildConfig enabled, instrumentation runner/dependencies from catalog. Wire release signing from external environment/provider values `TABBYRUSH_KEYSTORE`, `TABBYRUSH_KEY_ALIAS`, `TABBYRUSH_STORE_PASSWORD`, `TABBYRUSH_KEY_PASSWORD`; never print them or use debug keystore fallback. Signed release task fails clearly without signing inputs; debug builds remain possible. If selected Kotlin plugin folded into AGP, use proven template convention, not second plugin habit.
   Resolve signing providers only for release signing tasks; do not eagerly require secrets during debug/desktop project configuration. Disable configuration-cache use for secret-bearing release builds and use a nonpersistent Gradle process; no secret-valued generated build/source/config cache.
2. Main manifest exports launcher MainActivity only, Internet permission, backups disabled for this process-only app and cleartext false. Debug-only manifest references debug-only network config: base-config cleartext false, domain entries localhost/127.0.0.1/10.0.2.2 cleartext true with no subdomains; contract additionally enforces port8787. Release has no reference/resource/trust override. Explicit exported settings; no optional permissions/services.
3. Compose ViewModel/controller factories/newTodoId exactly contracts; retained viewModelScope lifetime through rotation, lifecycle-aware StateFlow collection, no secret in activity intents/saved state. onCleared closes controller/client. Background pauses collection, not a new repository/session or offline sync; foreground explicit refresh handles stale state. Process death clears session and drafts by design; re-entry notice. Back/cancel and disconnect warn when unsaved/uncertain; do not claim cancelling undoes server commit.
4. Resolve generated dependencies/locks with original02/07 owners before the gate.02 reviews verification metadata and shared lock;07 writes Android lock. Use `./gradlew :shared:allTests :androidApp:assembleDebug --write-locks` only under those owners' controlled gate; substitute tuple-proven task names recorded02 if needed, never skip common semantic/state tests. Then one main batch: `npm --prefix worker ci`; `npm --prefix worker test`; `npm --prefix worker run test:migrations`; `./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest`. Instrumentation device serial explicitly set; connected test must use real app plus real local Worker, not fake state.
5. In worker directory, with ignored private .dev.vars distinct dev token04 provisioned, run pinned `npx wrangler d1 migrations apply tabbyrush-db --local`, then `npx wrangler dev --ip 127.0.0.1 --port 8787`. Start service before live instrumentation step; backend tests use isolated D1 state, smoke uses dev state. Use emulator-5554 or actual authorized `$ANDROID_SERIAL`; USB: `adb -s "$ANDROID_SERIAL" reverse tcp:8787 tcp:8787`. Emulator endpoint http://10.0.2.2:8787; USB http://127.0.0.1:8787. Token entered masked or through protected test-process memory, never instrumentation command arguments.
6. `adb -s "$ANDROID_SERIAL" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`; `adb -s "$ANDROID_SERIAL" shell am start -n dev.tabbyrush.android/.MainActivity`. Smoke script selects explicit serial/APK, checks install/launch/boot failures, guides real UI scenarios and evidence; no success based solely on PID. Create sanitized todos, rename, complete/reopen, all filters/counts, delete cancel/confirm; create enough via authenticated fixture for Load more and verify real UI pagination (no private existing data deletion).
7. Real failure checks: stop local Worker -> failed read/stale rows and retained edit, restore -> explicit refresh; wrong token401 and re-entry; second authorized API client changes same todo/version -> Android stale PATCH/DELETE409 preserves intent, refresh then deliberate reapply/cancel. Invalid/200/201-scalar and astral titles agree client/server. Disconnect during delayed response -> late response ignored. No screenshot/token/title request logging.
8. Permanent debug-fault-proxy binds only127.0.0.1:8787, forwards only to127.0.0.1:8788, no payload/header logs, drops exactly one selected mutation response after upstream finishes and reports only method/count/status. For this test stop normal server, start Worker on8788 with same local state, run `node scripts/debug-fault-proxy.mjs`, interact with Android via unchanged alias8787. Confirm one upstream mutation, uncertain UI, explicit GET reconciliation, no automatic replay/new ID. Script must not expose generic proxy/public bind. Restore normal8787 server afterward. This checks actual OkHttp policy, not mock-only semantics.
9. Rotate with open edit/session -> retained state without duplicate requests; background/foreground -> usable; `adb -s "$ANDROID_SERIAL" shell am force-stop dev.tabbyrush.android` then relaunch -> session/token gone. Test200% system font/display scale, TalkBack labels/completion, >=48dp targets, IME, long title wrap, keyboard/focus and compact layout. Capture actual empty/populated/completed/editor/error screenshots via `adb -s "$ANDROID_SERIAL" exec-out screencap -p` to designated paths using smoke script; never capture token form. Record device/API/renderer/commit and contrast evidence.
10. Inspect debug/release merged manifests via selected AGP tooling; release cleartext denial remains08 runtime gate. Remove USB reverse with `adb -s "$ANDROID_SERIAL" reverse --remove tcp:8787`. Record lane test and actual runtime results in Android acceptance doc. On failure reopen owner, rerun affected tests plus full changed runtime scenario; do not lower acceptance.

## Todo list
- [ ] Implement thin Android entrypoint/lifecycle/network/signing configuration.
- [ ] Execute all lane checks once after integration; record actual outcomes.
- [ ] Complete actual local CRUD/error/conflict/fault/lifecycle/accessibility scenarios and screenshots.

## Success Criteria
All lane permanent checks pass; actual emulator/authorized device against real local D1 performs every CRUD/filter/count/page and error/conflict/reconcile scenario. Rotations retain session; process restart/disconnect removes token; no engine mutation replay. Debug HTTP only exact allowed origins, release manifest clean. Actual palette/font/IME/large-text/TalkBack inspected. Signing is configured, not falsely called released.

## Conflict Prevention
No integration sweep over other phases' files. Reopen02 for tuple/catalog/locks/resources,04 for backend,05 state/transport,06 UI; owner repairs and main gate reruns. Newly required paths registry-first.

## Risk Assessment
Physical reverse setup, Xpra graphics, instrumentation API changes and signature mismatch may block; document actual serial/version and resolve setup with01. Keep credential entry protected. Busy lane tests written but unrun cannot claim earlier acceptance.

## Security Considerations
No public local service, TLS trust bypass, global cleartext or legacy ADB5555. Session-only secret, no backups/saved state/log payload. External signing configuration fails closed; no secret in APK or evidence. Proxy for debug local fault testing only, not shipping code.

## Next steps
Only after complete07 gate proceed08 deployed HTTPS API plus actual signed Android release. Linux/Windows remain pending;07 cannot certify them.

## Unresolved questions
Signing custody/access resolved08. Device/client/sudo prerequisites from01 must be available; no runtime work performed by this plan.
