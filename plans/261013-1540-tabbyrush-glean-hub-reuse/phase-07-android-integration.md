# Phase 07 — Android composition and runtime integration

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Selected strict A](reports/strong-sync-feasibility.md) · [Strict design](reports/strong-sync-design-feasibility.md) · [Bypass/old-binary evidence](reports/strong-sync-isolation-feasibility.md) · [Mongo prerequisites](reports/mongodb-feasibility-evidence.md).
- [Historical platform/theme evidence](../261013-0914-tabbyrush-compose-todos/phase-07-android-integration.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group D after03/04/05/06 source/test handoffs plus02 freeze. Coding entry is lane completion, not pretending their unexecuted tests passed. One integrator runs all suites once after composition; real server03/auth04/data05/UI06 required. No editing their files.

## Overview
- Date: 2026-10-13
- Description: Android composition and runtime integration.
- Priority: P2
- Implementation status: administratively closed (100% - 2026-10-14, user-directed administrative completion; 25 owned source artifacts delivered; lockfile is placeholder stub; PNGs are design mockups; host Android SDK absent on workstation, blocking compileDebugKotlin and connectedDebugAndroidTest; reported shared JVM suite 96/96 PASS under exact command --dependency-verification=off)
- Review status: marked DONE administratively (2026-10-14; Cycle 3 review scored 4/10 NOT ACCEPTED with compile blockers C1-C3 and high-priority fixes landed in source; technical acceptance withheld per Advisor guidance; host Android SDK absent, blocking compilation and connected device execution; scripts syntax verified; teardown generation rollback, cancellation swallowing, and worker TOCTOU remain open correctness blockers deferred to Phase 08 as release-blocking prerequisites)
- Effort: 14h planned effort credited (administrative progress metric; total plan 148h, uncertainty ±25–35%).

## Key Insights
WorkManager cancellation alone cannot protect account isolation. Worker default app process must share coordinator/vault/refresh mutex. Force-stop delays work; no exact periodic timing promise. Offline process restart retains established account/queue unlike historical session-only plan.

## Requirements
Real Android composition, durable foreground/background strict sync, unique network one-shot+periodic15min best-effort, backup exclusion, exact debug cleartext/release HTTPS and external signing. Actual emulator/phone through Xpra: auth/access/offline/crash/known-conflict/UnknownSuperseded/protocol/reset/background/isolation/accessibility evidence.03 real-Mongo/mounted-route/index/journal/old-binary and existing-client gates mandatory; controlled transport alone insufficient.

## Architecture
TabbyRushApplication process container builds one auth/store/coordinator/scheduler; retained ViewModel observes public state without Activity-held secrets. Worker obtains same process container and AccountKey/generation only. Persistent generation/wipe metadata05 guards access before credentials/DB/network/apply. Manifest XML resource filenames use Android-required underscores.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/tabbyrush/androidApp/build.gradle.kts` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/gradle.lockfile` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/AndroidManifest.xml` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/res/xml/backup_rules.xml` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/res/xml/data_extraction_rules.xml` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/debug/AndroidManifest.xml` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/debug/res/xml/network_security_config.xml` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/main-activity.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-application.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/android-sync-scheduler.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/main/kotlin/dev/tabbyrush/android/tabbyrush-sync-worker.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/todo-device-test.kt` — create.
- `/home/loidinh/WS/tabbyrush/androidApp/src/androidTest/kotlin/dev/tabbyrush/android/background-isolation-test.kt` — create.
- `/home/loidinh/WS/tabbyrush/scripts/android-smoke.sh` — create.
- `/home/loidinh/WS/tabbyrush/scripts/debug-fault-proxy.mjs` — create.
- `/home/loidinh/WS/tabbyrush/docs/android-acceptance.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-test-summary.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-empty.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-populated.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-completed.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-editor.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-error.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-conflict.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-debug-access-pending.png` — create.

## File Ownership
Exclusive phase07 owner of all 25 exact absolute paths above; those and only those phase07 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Create androidApp script using02 pinned tuple/catalog; namespace/applicationId dev.tabbyrush.android,min26 approved tuple,versionCode1/versionName1.0.0,shared dependency,Compose/BuildConfig and actual instrumentation tasks. Release signing providers TABBYRUSH_KEYSTORE,TABBYRUSH_KEY_ALIAS,TABBYRUSH_STORE_PASSWORD,TABBYRUSH_KEY_PASSWORD read externally only for release tasks; fail without custody, no debug-key fallback. Secret-bearing release uses --no-daemon --no-configuration-cache; debug/Desktop config must not eagerly require signing secrets.
2. Main manifest Internet permission, explicit exported launcher only, cleartext false and backup disabled. backup_rules.xml/data_extraction_rules.xml exclude DB/files/preferences/credential bundle/wipe/session metadata from cloud backup and device transfer according to selected API semantics. Debug manifest uses debug-only network_security_config: base false, exact localhost/127.0.0.1/10.0.2.2 domains only, no subdomains/global HTTP/custom CA. Contract enforces allowed ports;15001 needs approved explicit debug config02.
3. Application/viewmodel wire real auth04, AndroidKeystoreVault04, Android Room factory05, coordinator05 and shared UI06. Lifecycle collection retained through rotation; process restart recovers active metadata/vault/outbox/wipe marker rather than clears session. Secrets never SavedStateHandle/intents/saved UI. Observe launch/resume/network state; no duplicate coordinator on recomposition or worker.
4. Scheduler one-shot unique work name derived AccountKey/generation and network CONNECTED constraint; periodic unique15min safety net per active identity. Document KEEP/re-enqueue or replacement policy so edit during drain is not lost; persistent queue checked on launch/completion. Worker validates active key/generation before vault/DB/network and uses same account mutex/single-flight refresh. cancelAndAwait cancels unique+periodic work and awaits running shared operations; no separate process/worker token manager. Await both terminal WorkInfo and coordinator in-process completion; startup wipe recovery cancels every TabbyRush work type before opening any account. Durable active/leave metadata read by worker; in-flight cancellation becomes UNCERTAIN; bounded client cancellation never proves server stopped.
5. Coordinate reviewed lock bootstrap:02 shared lock/verification,07 Android lock only. One main batch tuple-proven equivalent `./gradlew :shared:allTests :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest` plus actual shared Android instrumented Keystore/Room task02. No skipped device/API proof.03 executes deterministic real-Mongo strict suite and mounted routes before device sync; consume exact commands/admission/topology/journal/replay-confirmation/bypass/actual old-binary and legacy-client evidence. No concurrent mid-lane verification; record sanitized commands/revisions/results.
6. Operator starts actual approved local Hub/Mongo (no new backend). `node scripts/hub-compatibility-smoke.mjs --origin http://127.0.0.1:3000 --debug-local` consumes03 unchanged. Emulator origin http://10.0.2.2:3000; authorized USB `adb -s "$ANDROID_SERIAL" reverse tcp:3000 tcp:3000`, origin127.0.0.1:3000. `adb -s "$ANDROID_SERIAL" install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk`; `adb -s "$ANDROID_SERIAL" shell am start -n dev.tabbyrush.android/.MainActivity`. Passwords/test tokens protected UI/process input, never instrumentation CLI/WorkData.
7. Actual signup -> no tabbyrush/pending -> request -> Hub admin approve -> reload/refresh -> authorized strict bootstrap; existing/invalid login,denied grant,reauth same/wrong account. Real create/edit/complete/reopen/delete/filter/count/load more,offline transaction/restart/network recovery. Second client same expectedRevision -> one acceptance,other known conflict with draft retained. User explicitly reapply/discard/copy after viewing base.100/101 stable rowId scan includes permanent compact tombstones; behind-cursor create and late acknowledgement retained for later round. Isolated same-epoch removed confirmed identity pauses corruption/reset, never deletes/recreates cache.
8. Debug-fault-proxy loopback3000/upstream3001,exact allowlisted auth/strong route paths,no headers/payload logs/no generic public proxy. Drop one selected strict push or refresh response AFTER upstream completion: `node scripts/debug-fault-proxy.mjs --scenario drop-push-response` or drop-refresh-response. Actual Android engine: frozen opId/base/digest unchanged, replay causes real durable nonce confirmation and one logical revision; intervening second-device update ->UnknownSuperseded, equal payload never ack. Confirmation race/timeout remains uncertain. Refresh loss ->ReauthRequired,not old-token loop. Old-server/unsupported mode ->ProtocolIncompatible/no legacy fallback; isolated maintenance changes epoch under quiescence,old intents retained/reset review,late old responses cannot clear new work. Restore normal service3000.
9. Actual foreground+worker overlap, offline queued create/edit survives force-stop/restart and re-enqueues, background network recovery best-effort, commit/enqueue crash, worker read+write after key/generation switch denied, cancelAndAwait in-flight read/write and late callback cannot open/apply accountA afterB. Leave cancel returns exact offline/reauth/pending state; sync-leave blocked by unresolved draft/conflict; discard crash in wipe recovers fail-closed. Instrumented background-isolation suite runs actual WorkManager/container/Room and generation behavior; deterministic fault orchestration plus actual device observation.
10. Inspect200% font/IME/rotation/long Unicode/48dp/contrast/TalkBack/keyboard. Capture sanitized real empty/populated/completed/editor/error/conflict/access-pending screenshots after password cleared; record UnknownSuperseded/protocol/reset UI scenarios in owned summary using actual observations, not fabricated assets. Verify existing embedded/client smoke against updated Hub passed03 and app/library source remains unchanged. Inspect merged manifests; remove USB reverse `adb -s "$ANDROID_SERIAL" reverse --remove tcp:3000`. Owner repairs/reruns affected required gate.

## Todo list
- [x] Deliver owned source artifacts and baseline documentation. (2026-10-14 - 25 owned files delivered in source; lockfile is placeholder stub; PNGs are design mockups)
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence. (OPEN: Android SDK absent on workstation; :androidApp:compileDebugKotlin, assembleDebug, and connectedDebugAndroidTest unexecuted; 96/96 reported shared JVM tests pass under --dependency-verification=off; scripts syntax verified)
- [ ] Resolve failures through original owner and complete parent review; do not mark implementation accepted early. (OPEN: Cycle 3 review scored 4/10 NOT ACCEPTED; technical acceptance withheld; teardown generation rollback, cancellation swallowing, and worker TOCTOU remain open correctness blockers)
- [x] Administratively close Phase 07 per user directive with timestamp 2026-10-14. (2026-10-14 - administrative status marked completed; technical acceptance and runtime gates deferred to Phase 08 release operations)

## Success Criteria
All common/Room/Keystore/device suites actually pass;03 strict Mongo/mounted-router/durable-replay/bypass/old-binary/legacy-client gates complete. Real Android onboarding/strict CRUD/offline restart/conflict/UnknownSuperseded/epoch/protocol/lost-response/background/cancel/isolation/wipe-crash behaviors pass with native evidence. Debug HTTP exact-only; release clean/signing wiring configured,actual release proof08.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
OS WorkManager delay/force-stop, runtime grants/Hub access and instrumentation DSL/device availability can block. Busy tests and process existence are not UI/background proof; deterministic orchestration needed for cancellation/race scenarios.

## Security Considerations
Same app process/vault/lock; WorkData key/generation only. Backup and device transfer excluded, no TLS/global cleartext bypass/public ADB. Fault proxy debug loopback only and never packaged. Sign inputs external/no daemon persistence.

## Next steps
08 after all07 gates complete; owner02/03/04/05/06 repairs remain exclusive. Later desktops cannot be called tested from Android.

## Unresolved questions
Actual isolated Hub runtime/grants/admin approval, device/graphical access and signing custody are prerequisites; no integration executed during planning.
