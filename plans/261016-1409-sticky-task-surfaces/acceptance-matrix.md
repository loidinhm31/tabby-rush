# Sticky surfaces — acceptance and future verification

All entries **pending / not executed**. Planning only. IDs below are sticky-surface brainstorm acceptance1–12, not historical strict-v2 AC01–AC12. [Scope](../reports/brainstorm-261016-1302-sticky-task-surfaces.md), [contracts](architecture-contracts.md), [ownership](file-ownership.md), [dependencies](dependency-matrix.md). Module owners author behavioral tests with implementation; phase07 integrates Android/Windows evidence; phase08 alone certifies Linux. Builds cannot substitute for actual OS surfaces.

## Mandatory criteria1–12

| ID | Owner / integrated verifier | Scenario | Required observable evidence | Platform / prerequisites |
| --- | --- | --- | --- | --- |
| 1 | 01+02 / 07 | Real launcher widget at small/large supported bounds, enough rows to scroll through Incomplete then Completed; complete/reopen at top/middle/bottom | Actual launcher capture + traversal record; stable per-section createdAt DESC/id DESC order; moved ID appears exactly once, all installed widget IDs converge | Android min26 and SDK35 target, launcher with Glance host; TalkBack/font-size check |
| 2 | 05+06 / 07 | Offline widget Add opens compact app creation editor; save; desktop compact quick-add independently saves; reconnect normal sync | Actual Add route and persisted task after restart, pending outbox then acknowledged sync; normalized title/default task behavior matches main create; no main-editor draft loss | Android + Windows; safe existing sync endpoint/test account, offline/reconnect control |
| 3 | 01+05+06 / 07 | Main, widget, notification and sticky converge after successful local title/deadline/completion/delete and completed background sync while main Activity/window absent/hidden | Real SQLite observer tests plus timestamped IDs/state before/after; actual OS content reflects final state; no Activity-only dependency, no promised latency | Android background worker and Windows hidden-main runtime, two test clients for completed sync |
| 4 | 01+03 / 07 | Two overdue + one future; oldest→next overdue→future→none after completions; date-only, equal deadlines and reminder-disabled task | Selector boundary tests, actual shade sequence, ID/date ordering; no-task cancels; date-only at next-day start, equality overdue, ties taskId ASC | Android API32 and33+/34+, strict-v2 saved deadline fixtures, known clock/zone |
| 5 | 03+05 / 07 | Complete after process recreation twice, delayed same-account old-generation callback, account replacement; Open selected task | Real receiver/navigation evidence and DB/outbox assertions: completed remains true, no second revision for duplicate, replacement DB unchanged, correct fresh task or harmless rejection | Android cold start, actual immutable PendingIntents; two accounts and captured stale action |
| 6 | 03+05 / 07 | Permission denied, app notifications off, channel off, API34+ user swipe; reminders independent | ActivityResult prompt after explicit opt-in, capability UI, channel identity/privacy; widget/add/complete still work, reminder prefs unchanged; same dismissed key never immediate repost | Android API26–32 +33 permission +34/35 dismissal devices/emulators; actual shade and Settings |
| 7 | 02+03+05 / 07 | Process death/restart, force-stop/manual relaunch, logout/wipe interrupted, account switch while update/action in flight | Launcher/shade old titles removed; no old-token actions; next safe restart locally hydrates without Activity; marker retained on cleanup failure; stale actions cannot reopen wiped Room | Android real Room/private preferences/installed widget IDs, controlled process kill and injected cleanup interruption |
| 8 | 04+06 / 07 | Sticky above ordinary Windows app, repeated Show from app/tray, profile lock contention | Actual Windows capture and process/window/tray observation: one sticky/main/tray, no duplicates; ordinary stacking and focus observed; secure/fullscreen limits stated | Interactive Windows10/11 matching desktopTarget; main+sticky+ordinary app |
| 9 | 06 / 07 | Close main then sticky and restore from tray; hidden-main task mutation/sync; explicit Quit twice safely | Actual UI flow, local/sync convergence while main hidden, process exit and successful profile-lock reacquisition/manual relaunch; no disposal on hide | Windows native tray; repeat with tray unsupported/init failure to show last-visible recovery controls |
| 10 | 04+06 / 07 | Visible/hidden sticky manual relaunch, resize/move, negative monitor origin, monitor removal/DPI change, corrupt prefs | Persisted geometry/visibility restoration, title bar and >=48dp content reachable, usable work-area clamp including tiny screens; no auto-start registration created | Windows multi-monitor/per-monitor-DPI environment; real restart, local presentation file inspected without task secrets |
| 11 | 08 / 08 | Repeat relevant8–10, offline add, grouping, account isolation, hidden sync on target Linux session | Actual distro/JDK/desktop/session/WM/tray capability record; native stacking/focus/recovery/geometry proof and process-lock release; separate verdict | **Later Linux machine + graphical session required; pending until observed**, no Windows/headless substitution |
| 12 | 01–06 module tests / 07 and08 | Existing persistence/sync/account isolation/reminder invariants plus real launcher/shade/window/tray interactions | Real SQLite/outbox/temporal regression results and module test reports, release auditor results, native evidence indexes; historical release holds left unchanged | JDK21, Android SDK35, approved test environment; no fake store/runtime acceptance or source-string tests |

## Race and semantic boundary matrix

| Edge | Owner tests / native verifier | Exact expected result and evidence |
| --- | --- | --- |
| Completion vs concurrent title/deadline edit | 01 SQLite /05+06,07 | Coordinate actual operations at lock barrier, not sequential stale-copy imitation; fresh setter preserves title/deadline/rule/token and FIFO, only completed+updatedAt changes; final outbox/base/reopen assertions |
| Duplicate delivery before/after sync ACK | 01+05 /07 | Two Complete=true callbacks never reopen; one new mutation only when first changes state; false widget desired state explicitly reopens; absence/tombstone no-op |
| Admission→leave before mutation/read | 05+06 /07 | Admission drains existing work or rejects; no DB access after close, no stale runtime creation; operation/teardown interleavings covered with real SQLite |
| Read→account switch before OS publish | 02+05 /07 | Pause real render/update handoff then leave/switch; token recheck prevents stale publish or drain completes it before subsequent blank; final launcher/shade contains no old account |
| Same account, new generation after restart | 05 /07 | Old action/navigation/delete receipt rejected despite equal account key; persisted generation fence and DB assertions |
| Drain/OS-clear failure and crash | 05+06 /07 | Bounded failure leaves non-Ready/frozen state and wipe marker; restart cleans jobs/notification/Glance sessions and all widget IDs before marker removal; no doomed DB open |
| Initial observer + full scan/conflict resolution | 01 SQLite,05+06 /07 | Cold replay gives current snapshot; real accepted reconcile/full-scan promotion/discard/reapply/copy-as-new changes reach observer without UI; conflict selection includes explicit marker, reminders retain suppression |
| Draft vs saved intentions | 01+03 /07 | In-memory edit never changes OS; notification ignores DB editor_drafts but includes submitted pending outbox; widget/main retain existing saved-local visible projection |
| Date-only at anchor vs next-day boundary | 01 /07 | 09:00 reminder anchor does not make all-day task overdue; boundary−1 upcoming, boundary and+1 overdue, comparator uses overdueEpochMs for both buckets |
| Time/zone/DST/clock backward-forward | 01+03 /07 | Saved timezone/boundaries unchanged, selector recomputes; no reminder-alert eligibility leakage or exact timing promise; one inexact no-network boundary job, no zero-delay loop |
| Conflict/reminders disabled | 01+03 /07 | Conflicted persisted deadline remains display-eligible with conflict indication; disabled reminder still selected; Complete does not resolve/drop conflict; existing reminder gate behavior unchanged |
| Dismissal across sync/restart/title edit | 03 /07 | Durable generation/task/full-deadline key suppresses same selection; title/reminder-only edits, clock movement and workers do not repost; observed after process recreation |
| Selection/deadline reset and delayed delete | 03 /07 | Changed/disappeared key or deliberate off→on resets; programmatic cancel invalidates receipt first; stale delete from old posted instance cannot suppress new selection |
| Permission/rationale/denial/resume | 03+05 /07 | Real runtime prompt only on explicit opt-in API33+; denial is unavailable; resume checks runtime/app/channel, independent reminder controls and widget actions |
| Compact input vs main edit | 06 /07 | Keep unsaved main title/deadline draft open; compact typing/invalid submit/success never edits/closes it. Compact commits use same title/default helper; account switch clears compact text |
| No tray/init failure/lost capability | 06 /07+08 | Last visible window refuses hide and retains Open/Show-other/Quit; capability loss restores main. Native observation, not API-exists assertion |
| Geometry reachability | 04 unit/06 native /07+08 | Negative virtual origins preserved, insets/DPI conversions valid, disappeared monitor relocates, tiny work area clamps min; persist only presentation values |
| Singleton/lifetime/cancellation | 06 /07+08 | Repeated Show never adds Window/Tray/runtime; Close doesn't stop scheduler; Quit idempotent and releases lock only after teardown; lock contention stays exit-only |

## Future commands — not executed by planner

Run after owning changes are integrated, on appropriate host; record actual exit code/output/test report paths and baseline failures. JDK21, SDK35 and trusted dependency provenance required. Main integrator coordinates one coherent aggregate run; no concurrent build storms. Never use broad verification-off as evidence of supply-chain acceptance.

### Foundation compatibility and metadata (phase01 owner)

```powershell
.\gradlew.bat :androidApp:dependencyInsight --dependency glance --configuration debugRuntimeClasspath --dependency-verification=strict
.\gradlew.bat :androidApp:compileDebugKotlin :shared:jvmTest --dependency-verification=strict
.\gradlew.bat :androidApp:dependencies --configuration debugRuntimeClasspath --dependency-verification=strict
```

Inspect actual Kotlin2.1.10/Compose1.7.3/Glance candidate graph/min26/SDK35 and authenticated artifacts. Review exact four lock paths in ownership. Resolve/write locks only for genuinely changed configurations under01 owner, on matching desktop host; new checksums require upstream authentication, not blind `--write-verification-metadata`. If inherited149 checksum mismatch blocks configuration, record external gate and obtain approved remediation, not general repair or fake compile proof.

### Android + Windows final integrated checks (phase07 orchestrates)

```powershell
.\gradlew.bat :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:assembleDebug :desktopApp:test -PdesktopTarget=windows --dependency-verification=strict
.\gradlew.bat :androidApp:connectedDebugAndroidTest --dependency-verification=strict
node scripts/release-security-check.test.mjs
node scripts/release-security-check.mjs --diagnose-source
.\gradlew.bat :desktopApp:createDistributable -PdesktopTarget=windows --dependency-verification=strict
.\gradlew.bat :desktopApp:runDebug -PdesktopTarget=windows --dependency-verification=strict
```

Do not use nonexistent `runDebugExecutable`; existing desktop task is `runDebug`. Source diagnosis does not replace merged-manifest/release APK audit. When existing signing custody/approved HTTPS origin/API key are available, use existing release path; never print secrets:

```powershell
.\gradlew.bat :androidApp:assembleRelease --dependency-verification=strict
node scripts/release-security-check.mjs --apk androidApp/build/outputs/apk/release/androidApp-release.apk
```

Record merged manifest receiver/exported policy, backup=false, no new overlay/FGS/exact-alarm permissions, isolated task channel/actions and release signature. Missing custody is a disclosed release-audit prerequisite, not authority to relax checks. Node auditor tests exercise real policy behavior on manifest input, not source-code text assertions.

### Native Android exercise (phase07, real device selection)

```text
adb devices
adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <serial> shell am start -n dev.doggo.android/.MainActivity
adb -s <serial> shell am kill dev.doggo.android
adb -s <serial> shell am force-stop dev.doggo.android
```

Use controlled test devices/accounts. Add widget through actual launcher, resize/scroll/toggle, use Add/Open, shade Complete/swipe, system Settings permission/channel changes, lock screen and native time/zone controls. `am kill` tests background process recreation; force-stop is separate and only next manual start is required to recover—no promise of force-stopped delivery. Use actual UI for consequential logout/wipe of disposable test account and record approval/scope; never wipe personal data. Capture multiple widget IDs and the delayed-publication/leave race, not only app screenshots. Verify ordinary offline data, background sync completed and process-death invariants.

### Windows native exercise (phase07)

Use existing `runDebug` then actual distributable if available. Observe native tray/window surfaces, ordinary-app stacking, both independent Close callbacks, repeated Show, foreground focus, hidden-main local/sync convergence, Quit and new launch/profile-lock reacquisition. Move across real negative-origin/DPI monitors, unplug saved display, manually relaunch with sticky shown/hidden. Exercise no-tray/init-failure via actual capability boundary and production fallback state, never mock UI as native proof. Check startup registration remained unchanged. Record OS/build, monitor work areas/scales, process/window counts and timings.

### Linux later gate (phase08, **on Linux only**)

```sh
./gradlew :shared:jvmTest :desktopApp:test :desktopApp:createDistributable -PdesktopTarget=linux --dependency-verification=strict
./gradlew :desktopApp:runDebug -PdesktopTarget=linux --dependency-verification=strict
```

Honor host/target guard. Record distribution, kernel/JDK, X11/Wayland, desktop/window manager, tray support or absence, work areas/scales, focus/stacking, restoration, hidden runtime and Quit/lock evidence. Display/session unavailable => phase08 pending; headless tests or Windows output never satisfy11. Linux reminder D-Bus bubble/Orca holds are separate unchanged gates.

## Evidence and verdict rules

- Each owner report <=150 lines; record exact snapshot/versions/commands, actual results, native scenario IDs and limitations, redact task/account credentials. Module tests live in their owning phase, not a later omnibus test rewrite.
- Phase07 writes declared Android/Windows evidence summaries + real captures; phase08 writes Linux evidence. Every matrix row has pass/fail/pending plus direct evidence path; no fabricated output or “compile implies UI” claims.
- This feature cannot close historical G2 physical reminders/accessibility/native Windows toast, G3 backend/cluster findings, G6 three-device live-sync hold, G7 production cutover, or supply-chain149 baseline. Do not edit historical acceptance totals or run backend migration. Any existing gap remains explicitly held.

## Unresolved questions

Exact stable Glance compatibility/session completion APIs, available Android device set/signing custody and later Linux machine/session are implementation prerequisites. Notification policy, deadline ordering/conflict choice, dismissal reset, compact input isolation and tray fallback are decided in contracts, not left open.
