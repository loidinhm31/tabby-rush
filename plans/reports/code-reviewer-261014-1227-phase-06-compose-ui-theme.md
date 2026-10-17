# Code Review: Phase 06 Shared Compose UI + Accessible Light Theme

Date 2026-10-14 · Reviewer Phase06CodeReviewer · Plan `plans/261013-1540-tabbyrush-glean-hub-reuse/phase-06-compose-ui-theme.md`

## Score: 4.5/10 — NOT ACCEPTABLE as phase-complete; fix criticals first

Compiles + 96/96 JVM green, but green proves little: new test class renders no Compose. Contrast claims in docs are wrong; several required states/flows absent.

## Scope
- 15 active owned files (~2790 LOC) + 5 conditional font/license paths (documented omission, allowed by plan step 1).
- Contracts read: `todo-ui-state.kt`, `todo-action.kt`, `todo-models.kt`, `safe-errors.kt`, `sync-coordinator.kt`, `auth-session.kt`, `title-policy.kt`.
- Contrast ratios recomputed by script (WCAG 2.x relative-luminance formula).

## Critical
1. **Tests do not test UI.** `todo-semantics-test.kt` constructs data classes / calls `TitlePolicy` / `toString()`. Zero composables rendered; no `compose.uiTest` dep. Plan step 7 + Success Criteria require rendering real shared composables and asserting labels/actions/focus/password clearing. `testLeaveGuardTalliesAndSafety` re-implements `hasUnsynced` in test (duplicated logic = false proof). `testConflictReviewActionsAndTombstoneGuard`, `testSyncFeedbackStatusFormatting`, `testTodoUiStateDefaultsAndFilterInvariants`, `testAccessRequestStatuses` only assert constructor getters. Password "cleared after submit" never exercised.
2. **Contrast fails WCAG AA; docs misstate it.** Recomputed:
   | pair | actual | doc | need |
   |---|---|---|---|
   | white on coral #ED3838 | **4.02** | 4.6 | 4.5 |
   | coral on coralSurface | **3.51** | – | 4.5 |
   | coral on white | **4.02** | – | 4.5 |
   | muted on mintSurface | **4.26** | – | 4.5 |
   | muted on coralSurface | **4.32** | – | 4.5 |
   | muted on white | 4.94 | 5.2 | ok |
   | ink/white 17.85 (doc 18.5), white/primary 10.69 (doc 9.2), deep/mintS 11.16 (doc 12.3) | doc "measured" values wrong |
   Affected: "Leave & Wipe Local Data" + "Confirm Delete" buttons (14sp white on coral), every error text (`colorScheme.error` on white/coralSurface: form errors, SafeErrorBanner, sync paused statuses, leave-guard list, Delete/Dismiss text buttons), `VersionCard` status/rev text (muted on coralSurface), `NoticeItem` description (muted on mintSurface). Plan Risk section explicitly said "accessibility wins" → palette not adjusted. Fix: darker functional coral (e.g. ≈#C62828 gives ≥5:1 on white and ≥4.5 on coralSurface — verify), darker muted (≈#586369), re-measure, publish script-backed table.
3. **License notices unreachable in compact layout (Android primary target).** `showNotices` set only from the ≥840dp sidebar in `tabbyrush-app.kt`; compact top bar has only Add + Leave. Android users cannot open notices.
4. **Notices content wrong/incomplete.** Hard-coded 3 groups; omits Ktor, OkHttp, SQLite bundled, kotlinx-datetime, JNA (LGPL/Apache), dbus-java (LGPL/AFL), androidx security-crypto, Compose material. Copyright years/holders invented ("2020-2026 JetBrains… Google LLC"). Third card shows "SIL OFL 1.1" heading for fonts that are **not bundled** — misleading; drop it. `font-provenance.md` claims viewer shows "all bundled dependencies" → false.
5. **Required plan states/flows missing from UI.** (Some blocked by frozen contract — then must be documented as contract gap, not silently dropped.)
   - Todo "Load more"/lazy page: none (no action in contract).
   - UnknownSuperseded retained-intent review, ResetRequired old-epoch review, ProtocolIncompatible/DataInvalid repair guidance, AuthorizationUnresolved, credential-persistence-pending: only a one-line `SyncFeedback` label; no dedicated UI.
   - Admin-login warning: only in `strings.xml`, never rendered.
   - Approved-access flow: `AccessRequestPanel` `else` branch (any status other than pending/rejected, incl. `approved`/unknown) shows "Request TabbyRush Access" form → user can re-request after approval. Unknown must be distinct.
   - REAUTH_REQUIRED shows only login form: no cached/local data access, no "work preserved" message, no local-only logout. Plan step 4 says reauth must preserve work/actionable recovery.
   - Leave guard shows 3 counts (contract limit); no drafts/UnknownSuperseded/reset counts, no sync-then-leave option, no "possible remote commit" explanation.
   - Local-only logout/Sign Out in `AccessRequestPanel` has no confirmation (plan: logout stays confirmed).

## High / Warnings
1. **No scrolling on forms/dialogs/sidebar.** `AccountForm`, `AccessRequestPanel`, `ConflictReview`, `LeaveGuard`, `TodoEditor`, desktop sidebar, compact top bar: no `verticalScroll`. At 200% text / IME open / landscape, signup (3 fields + button) and conflict dialog (2 cards + 3 buttons) clip; user cannot reach password field/actions. Violates 200% requirement.
2. **Todo row at 200% text.** Fixed `Row`: 48dp checkbox + "Edit" + "Delete" TextButtons + title `weight(1f)`; on 360dp title squeezed to ~80dp. `maxLines = 3, Ellipsis` truncates user title — contradicts "long Unicode wrap" and design doc claim "without truncation". Filter chips `Row` not scrollable (doc claims horizontal scroll).
3. **Row semantics.** `Surface.clickable(onToggle)` wrapping Checkbox + Edit + Delete: duplicate toggle path, merged-semantics risk (nested clickables may collapse in TalkBack tree) [INFERENCE – needs on-device/uiTest]. Edit/Delete labels identical per row, no title context → TalkBack list of "Edit, Edit, Edit". Add `contentDescription = "Edit ${title}"`; remove row clickable or use `toggleable(role=Checkbox)` once.
4. **IME Done bug (`account-form.kt` onDone).** Condition omits `username.isNotBlank()` for SIGNUP; Button is correctly disabled but keyboard Done submits `Register("", …)`. Duplicated submit lambda (onDone + onClick) → extract single `submit()`.
5. **Raw epoch shown to users:** `"Last reconciled: ${lastSyncTime}ms"` in `sync-feedback.kt` — unreadable; needs date formatting (kotlinx-datetime available).
6. **Body < 16sp** used for substantive text: `bodySmall` 14sp for errors, help text, descriptions, statuses; `labelMedium` 12sp for action buttons (Edit/Delete/Sync Now/Reconcile/Dismiss). Plan: body ≥16sp. Controls at 12sp are weak at 100% scale.
7. **Non-text contrast:** `outline = TabbyRushLine #D9E3E4` on white = 1.31:1 → OutlinedTextField/OutlinedButton/Chip borders fail WCAG 1.4.11 (3:1) for inputs.
8. **Editor dialog dismiss-on-outside-click** calls `CancelEditor` → silently discards typed draft (plan: raw drafts retained; explicit cancel). Use `DialogProperties(dismissOnClickOutside=false)` or confirm. Conflict dialog `onDismissRequest = DismissError` mismatched semantics, and has no "decide later"/close → keyboard/Escape no-op; trap.
9. **No live-region** on errors/sync status; `contentDescription` on Text replaces text. Add `liveRegion = Polite`, drop redundant contentDescription (it duplicates visible text).
10. **Password semantics.** `contentDescription = "Password input field"` replaces the field label; no explicit `password()` semantics (Compose may add from `PasswordVisualTransformation` — unverified [INFERENCE]; add explicitly + test). Docs claim "wiped from memory": JVM `String` cannot be wiped; setting state to `""` only drops a reference. Reword claim; KDoc on `AuthSubmitAction` "memory inspections" overclaims. No autofill/`KeyboardCapitalization.None`/`autoCorrect=false` on email/username.
11. **`VersionCard` mislabels local null.** `todo == null` renders "Item was deleted on the server" even under "Your Local Version" (local delete intent / missing). Separate wording per side.
12. **`strings.xml` dead.** 40+ strings, zero `Res.string` usage; UI hard-codes English; values diverge ("Add Todo" vs "Add Task"; "Log In" vs "Sign In"). Either wire via `stringResource` or delete (YAGNI/DRY). `action_reapply_conflict`/`action_reconcile` etc. unused.
13. **Docs drift/fabrication.**
    - `design-guidelines.md` §2.2 ratios wrong (above) though titled "Measured". §4 describes FAB, header counts, scrolling chips, sidebar filters/counts — not implemented. §3.1/§5 "200% without truncation", "Escape/Enter keyboard support", "contentDescription on all icon actions" unverified. Status "Frozen/Authoritative" unjustified.
    - `font-provenance.md`: no upstream revision/checksum/license-file hash (plan step 1 demanded actual audit); Alexandria attribution "Mai Erksousi" appears wrong (Google Fonts credits Mohamed Gaber) [INFERENCE – verify upstream]; glyph-coverage claims for Cal Sans unevidenced; "Inspection of reference CSS" has no captured evidence. System-font decision itself is plan-permitted; the audit backing it is not.
14. `TabbyRushApp(onAuthSubmit = {})` default silently drops login/register → footgun; make required.
15. Dialog/focus: no initial focus (`FocusRequester`) on editor field, no focus restore; plan lists focus/keyboard.

## Medium
- DRY: `tabbyrush-app.kt` (437 LOC) duplicates Add button, user label, SyncFeedback, error banner, TodoList across compact/expanded; split `AuthGate`, `ExpandedWorkspace`, `CompactWorkspace`. Six identical `Card(surface, large, elevation)` shells → shared `TabbyRushDialogCard`. `ButtonDefaults.buttonColors(primary,onPrimary)` repeated ~10× = Material default → delete.
- `TitlePolicy.normalizeAndValidate` re-run every recomposition, allocating boxed `MutableList<Int>` per keystroke; wrap in `remember(editor.rawTitle)`. Private `String.codePointCount` extension hand-rolled; counts differ from policy (policy rejects lone surrogates, counter does not) and ignores trim.
- `TabbyRushCustomColors` data class + `staticCompositionLocalOf` never varied → YAGNI; plain `object` of constants suffices. Duplicate top-level `TabbyRushXxx` vals + class defaults.
- `TabbyRushTypography` repeats `fontFamily = SansSerif` 15×; default is already SansSerif — drop.
- Unused imports (`Icon`, `IconButton` in `todo-list.kt`, others likely).
- "My Tasks" not marked heading; `SyncFeedback` `Triple` allocated every recomposition.
- Signup help text states char limits; plan said byte-based limits.
- Reauth form allows toggling to Sign Up and has no email prefill.

## Suggestions
- Add `compose.uiTest` (jvm via `runComposeUiTest`) and write real tests: password cleared after submit/mode switch; Done blocked without username; row exposes single toggle + labelled Edit/Delete; conflict buttons dispatch correct actions; remote-deleted hides Reapply; compact layout shows notices entry; approved status ≠ request form; large `fontScale` doesn't clip actions.
- Add contrast unit test computing ratios from the token constants and asserting ≥4.5 for every text/surface pair actually used (replaces hand-written doc table).
- Wrap forms/dialog bodies in `verticalScroll(rememberScrollState())`; use `FlowRow` for chips and row actions; move Edit/Delete into overflow or stack under title at large fontScale.
- Generate notices from a single source (resource file) shared with phases 09/10; include actual third-party list.
- Raise contract gaps to plan owner (Load more, UnknownSuperseded/reset review state, richer LeaveGuardState, admin flag) — UI cannot implement them against frozen types.

## Positive
- `AuthSubmitAction` kept separate from persisted `TodoAction`; password never in `rememberSaveable`; `toString` redacts password; cleared on submit, mode switch, disposal.
- Dynamic color off; light-only; tokens match plan hex; 48dp `defaultMinSize` consistently applied; stable `LazyColumn` keys; `stateDescription` + strikethrough + text label (non-color-only completion); delete dialog avoids echoing title; responsive 840dp breakpoint; safe error messages only.
- Font decision (system font, no CDN, no copied assets) is allowed by plan and honest about omission.

## Validation
- `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks` → BUILD SUCCESSFUL 12s; 7/7 `TodoSemanticsTest` pass; (QA report: 96/96). Only warnings: pre-existing expect/actual Beta. Android target not compiled by this command; Android/Linux/Windows runtime, TalkBack, 200% scale, contrast-on-screen not verified (owned by 07–10).
- Contrast recomputed via script (see table). `--dependency-verification=lenient` bypasses checksum verification (251 unverified artifacts per QA report) — not a phase-06 defect but blocks strict gate.

## Plan status
Todo items remain unchecked: implementation incomplete against Requirements (states above) and Success Criteria (semantic suite renders UI; contrast ≥4.5). Do not mark phase accepted.

## Recommended actions (priority)
1. Fix functional palette (coral, muted, outline) + re-measure; correct docs table; add contrast test.
2. Add real Compose UI tests; delete or rewrite pseudo-tests.
3. Add scroll to forms/dialogs/sidebar; fix todo row layout/truncation/semantics for 200%.
4. Expose LicenseNotices in compact layout; correct/complete notice content; drop OFL card.
5. Fix approved/unknown access states, reauth recovery, logout confirm, admin warning, IME Done bug, raw timestamp, VersionCard null label, dismiss semantics.
6. Wire or delete `strings.xml`; dedupe tabbyrush-app/cards.
7. Request contract amendment (or document gap) for Load more / UnknownSuperseded / reset review / leave counts.
8. Re-audit font provenance with revision+checksum evidence or trim claims.

## Unresolved questions
- Are missing states (UnknownSuperseded, reset review, admin flag, Load more, extra leave counts) intentionally deferred because contracts are frozen? If yes, which phase/owner amends contracts?
- Does Compose MP 1.7.3 `PasswordVisualTransformation` auto-set `password()` semantics on all targets? Needs uiTest.
- Is `LogOut` (`AuthSubmitAction.Logout`) local-only wipe or session-only? Confirmation copy depends on it.
- Verify Alexandria/Cal Sans upstream authorship + OFL texts before keeping provenance doc.
