# Code Review Cycle 3: Phase 06 Shared Compose UI + Accessible Light Theme

Date 2026-10-14 · Reviewer Phase06CodeReviewerCycle3 · Plan `plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md`

## Score: 6.5/10 — NOT ACCEPTED as phase-complete (acceptable only w/ explicit user-approved deviations, see C3)

Cycle 2 → 3: real progress on contrast test matrix, timestamp, unknown access status, admin banner, §6 gap doc. But docs now contain claims code does not back, admin warning is a string heuristic, and several cycle-2 warnings (W1–W5) remain untouched and undocumented.

## Scope
- `shared/src/commonMain/kotlin/dev/doggo/shared/ui/*.kt` (9), `theme/*.kt` (2), `commonTest/.../ui/todo-semantics-test.kt`, `composeResources/values/strings.xml`, `docs/design-guidelines.md`, `docs/font-provenance.md`, phase-06 plan, cycle-2 report.
- Not verified: Android/Linux/Windows runtime, TalkBack, 200% scale, IME (owned 07–10).

## Cycle 2 items: status
| Item | Status |
|---|---|
| C1 rendered-UI tests | NOT FIXED. Deferred in design §6.5. Harness missing (`grep uiTest` in gradle/toml = none); phase 06 may not edit shared Gradle. Deferral needs approval (C3) |
| C2 missing states | PARTIAL. Documented §6 (Load more, counts, leave tallies); but 2 doc claims false (C2) |
| W1 todo row at 200% | NOT FIXED, undocumented (fixed `Row`: checkbox + title + Edit + Delete) |
| W2 compact top bar not scrollable | NOT FIXED, undocumented; admin banner now added INSIDE topBar → worse |
| W3 `DismissError` as Decide Later | NOT FIXED. No handler in repo; not in §6 |
| W4 body/labels <16sp | NOT FIXED. `bodySmall` 14sp used 32× (errors, help, status, metadata); labelLarge 14, labelMedium/Small 12. Design §3.1 still says "strictly ≥16sp" |
| W5 dead strings.xml | NOT FIXED. 0 `Res.string`/`stringResource` uses; plan lists it as deliverable |
| W6 formatTimestamp | FIXED (traced whole-second + fractional; ok) |
| W7 doc numbers exact | NOT FIXED (see W-A) |
| W8 contrast test incomplete | FIXED (16 pairs incl. muted/cream, muted/coralS, primaryDeep/mintS) |
| W9 unknown status = none | FIXED (distinct "Access Status Unknown" w/ refresh) |
| Logout confirm | FIXED |
| Cap: dismissOnClickOutside=false on dialogs | FIXED |

## Critical
1. **C1 Admin warning = substring heuristic.** `doggo-app.kt:173-174`: `username/email.contains("admin", ignoreCase)`. False positives ("padmini", "sadmin@…", "Admin Assistant"), false negatives (any real admin not named so). `AccountIdentity` has no `isAdmin`; `LoginResponse.isAdmin` exists in `auth/hub-auth-dtos.kt:41` but is not surfaced. Plan step 4 requires warning for actual admin-login. Design §6.4 words it as "if identity indicates administrative role" → hides heuristic. Fix: raise contract amendment (`AccountIdentity.isAdmin` or `TodoUiState.adminSession`), or remove banner + record gap. Also state `adminWarningDismissed` lives inside `BoxWithConstraints` → resets on compact/expanded switch (rotation); and lines 173-175 mis-indented.
2. **C2 Design doc §6 claims unsupported by code.** §6.2 says UI "displays dedicated guidance banners when `SafeError.UncertainOutcome` present" — grep: `UncertainOutcome` never referenced in `ui/` (only `ResetRequired`, `ProtocolIncompatible` in `SafeErrorBanner`). UNCERTAIN count chip only (`sync-feedback.kt:75`). §5 claims "Escape (dismiss/cancel)" but editor `onDismissRequest = {}` (no-op), and "Transient password memory is cleared" (JVM String ref dropped only; KDoc on `AuthSubmitAction` "memory inspections" overclaims). §3.1 body "strictly ≥16sp" vs 32 `bodySmall` uses. Header status "Authoritative … Verified" unjustified. These docs are the sole basis for deferral → must be accurate.
3. **C3 Rendered-UI tests still absent; deferral not approved.** Plan step 7 + line 11: tests "written here", only *executed* in 07. §6.5 reinterprets as "authored in TodoSemanticsTest" — but 6 of 7 tests are getter/constructor tautologies (`assertEquals("pending", info.status)` etc.); `testLeaveGuardTalliesAndSafety` re-implements `hasUnsynced` locally (false proof); `testSyncFeedbackStatusFormattingAndTimestamp` asserts only data-class fields + 1 timestamp check. Real tests: contrast math, password `toString` redaction, `TitlePolicy` (belongs to contract). Zero composables rendered; password-cleared, IME Done gating, approved-vs-request form, compact Notices, row labels, conflict dispatch all untested. Blocker is legit (no `compose.uiTest` in 02 tuple; ownership forbids Gradle edits) but scope reduction requires user approval + registry/plan amendment + 02-owner ticket; none recorded. Ex-tautology tests should be deleted (misleading green count).

## Warnings (High)
- **W-A Doc contrast table not exact** (claim: "exact W3C", "matches computed"). Recomputed (python, WCAG): Ink/Page 17.15 (doc 17.02), Ink/Cream **16.25 (15.74)**, Ink/MintS 15.40 (15.46), Ink/CoralS 15.59 (15.54), White/Coral 5.62 (5.63), Coral/CoralS 4.91 (4.90), Muted/White 6.31 (6.28), Muted/MintS 5.44 (5.47), Muted/CoralS 5.51 (5.49), Muted/Cream **5.75 (5.56)**, Outline/White 4.72 (4.68). Matching: Ink/White 17.85, White/Primary 10.69, White/Deep 12.94, Deep/MintS 11.16. All pass thresholds; accuracy only. Test does not print values, so doc cannot be "test output". Theme KDoc may carry same drift.
- **W-B "Decide Later"/Esc on conflict = `TodoAction.DismissError`** (conflict-review.kt:166, doggo-app.kt:138). No handler anywhere (`grep DismissError` = def + 4 UI callers). Semantic overload (error banner dismiss vs defer-conflict). Either define in contract (`DeferConflict`) or document in §6 as 07-integration requirement.
- **W-C Body/labels <16sp** (plan Requirements: body ≥16sp). Unchanged from cycle 2; either raise `bodySmall` or record approved deviation. Doc says opposite.
- **W-D 200% text**: todo row fixed `Row` (todo-list.kt:179-253); compact top bar (title + 3 buttons + sync card + admin banner) non-scrolling in `Scaffold.topBar` (doggo-app.kt:316-397) can consume viewport at 200%/landscape [INFERENCE, needs device]. Sidebar/forms/dialogs scroll OK. Cycle-3 context claims scroll only for those — not these.
- **W-E strings.xml dead (42 strings)**; UI hard-coded English with diverging copy. Wire or delete + amend plan.
- **W-F Plan states still missing, not in §6:** REAUTH local-only logout / local-data access (reauth screen = plain login/signup toggle, signup mode reachable, email not prefilled); AuthorizationUnresolved; credential-persistence-pending; ConflictReview copy ("another client") same for all conflicts, no UnknownSuperseded/known-conflict distinction; leave dialog lacks sync-then-leave option. §6 covers only 4 gaps.
- **W-G Logout semantics** unchanged: "sign out … on this device" vs LeaveGuard "Wipe Local Data"; `AuthSubmitAction.Logout` undefined.

## Medium
- Editor `onDismissRequest = {}`: Android back / desktop Esc do nothing → keyboard/TalkBack escape trap (Cancel exists). Consider routing to Cancel with draft-loss confirm only when dirty.
- `AccessRequestPanel` unknown branch interpolates raw server `status` string into copy (`($status)`); sanitize/truncate.
- Editor `singleLine=false` + `ImeAction.Done`: Enter on desktop inserts newline; titles are scalar → set `singleLine=true`/maxLines or handle Enter.
- Hand-rolled `codePointCount` vs `TitlePolicy` differ (trim/lone surrogates).
- DRY (unchanged): `doggo-app.kt` 534 LOC (compact/expanded header dup), 7 dialog Card shells, `buttonColors(primary,onPrimary)` repeated (= default), `fontFamily=SansSerif` repeated.
- `font-provenance.md`: "Complete (Frozen)" but no upstream revision/checksum evidence (plan step 1 demands); "viewer displays notices for all bundled dependencies" = hand-curated list. System-font decision itself plan-permitted.
- `LicenseNotices` hard-coded; no single source for 09/10.
- Password `KeyboardCapitalization.None`/autofill hints absent.

## Low
- FilterChip labels default 14sp; "Notices"/"Leave" compact labels 12sp (labelMedium) — adjacent to destructive Leave.
- `catch (_: Exception) {}` around `requestFocus` swallows silently.
- Mis-indent doggo-app.kt:173-175.

## Positive
- Contrast palette correct; test is real WCAG math, 16 pairs incl. 3:1 outline; cycle-2 W8 closed.
- `submit()` single path; password cleared on submit/mode switch/dispose; `password()` semantics; redacted `toString`.
- Access panel: approved/pending/rejected/none/unknown all distinct, safe-error live region, sign-out confirmation.
- Dialogs: no outside-click loss, scrollable bodies, 48dp targets, stable keys, non-color completion state, per-row Edit/Delete labels.
- `formatTimestamp` verified for whole-second/fractional/UTC label.
- §6 gap documentation exists and correctly identifies Load more / counts / leave tallies / uiTest harness absence.

## Validation
- `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks --console=plain` → BUILD SUCCESSFUL. Result XML counts: 96 tests / 0 fail / 0 error / 0 skip (TodoSemanticsTest 7). Strict `./gradlew :shared:jvmTest` FAILS dependency verification (AGP/KSP transitive artifacts missing from `gradle/verification-metadata.xml`) — pre-existing, not phase-06; `lenient` bypass used (same as cycle 2).
- Python WCAG recompute of 16 pairs: all functional ≥4.5, outline ≥3; Mint/White 2.18 (restricted).
- Greps: `uiTest` (none), `DismissError` handlers (none), `Res.string|stringResource` (none), `UncertainOutcome` in ui (none), `bodySmall|labelSmall|labelMedium` in ui (32).
- Not run: runtime/TalkBack/200%/IME.

## Plan status
Plan Review line updated to cycle 3. Todo checkboxes remain unchecked; phase not accepted; implementation status stays pending.

## Required to reach acceptance
1. Fix/replace admin heuristic (contract field) or remove + document gap. Correct §6.2/§6.4/§5/§3.1 claims; mark doc status honestly.
2. User-approved written deviation for rendered-UI tests (+ ticket to 02 owner for `compose.uiTest`), delete tautology tests, or add harness.
3. Define `DismissError` vs defer-conflict.
4. Raise secondary text ≥16sp or record approval; wire/delete strings.xml; make compact top bar/row 200%-safe or document for 07 verification.
5. Regenerate contrast table from test output.

## Unresolved questions
1. User approval: defer rendered-UI semantic tests to 07 despite plan "written here"? Who amends 02 build (`compose.uiTest`)?
2. Will contract owner add `isAdmin` (hub `LoginResponse.isAdmin` exists) to identity/UI state?
3. `DismissError` coordinator behaviour when `conflict != null`; `Logout` = local wipe or session-only?
4. Is ≥16sp binding for secondary/label text?
5. Alexandria/Cal Sans revision+checksum audit evidence — required or system-font decision sufficient?
