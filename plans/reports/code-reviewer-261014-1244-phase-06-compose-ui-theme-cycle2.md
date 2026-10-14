# Code Review Cycle 2: Phase 06 Shared Compose UI + Accessible Light Theme

Date 2026-10-14 · Reviewer Phase06CodeReviewerCycle2 · Plan `plans/261013-1540-doggo-glean-hub-reuse/phase-06-compose-ui-theme.md`

## Score: 6/10 — NOT ACCEPTED as phase-complete

Cycle 1 remediations mostly real + verified in code. Contrast fixed (recomputed). Blockers left: no rendered-UI tests (plan Success Criterion), required states still absent w/o documented contract-gap, 14sp body / row 200% layout unchanged.

## Scope
- Files: `shared/.../ui/*.kt` (9), `theme/*.kt` (2), `commonTest/.../ui/todo-semantics-test.kt`, `composeResources/values/strings.xml`, `docs/design-guidelines.md`, `docs/font-provenance.md`, plan phase-06, cycle-1 report.
- Contrast pairs recomputed via python (WCAG relative luminance).
- Android/Linux/Windows runtime, TalkBack, 200% scale, IME not verified (owned 07–10).

## Cycle 1 findings: status

| # | Cycle 1 item | Status |
|---|---|---|
| C2 | contrast fails | FIXED. coral/white 5.62, coral/coralS 4.91, muted/white 6.31, muted/mintS 5.44, outline/white 4.72 (all pass); contrast test added |
| C3 | notices unreachable compact | FIXED (`Notices` TextButton compact top bar) |
| C4 | notices wrong / OFL card | MOSTLY FIXED (OFL card gone; real deps listed). Copyright holders still hand-written, no OkHttp/androidx security-crypto/SQLite entries [verify vs 02 tuple] |
| H1 | no scroll forms/dialogs | FIXED forms/dialogs/sidebar. NOT fixed: compact top bar (see W2) |
| H4 | IME Done w/o username | FIXED (`submit()` w/ canSubmit) |
| H5 | raw epoch ms | FIXED-ish (W6) |
| H8 | outside-click discards draft | FIXED editor (`dismissOnClickOutside=false`, onDismiss no-op). Conflict: see W3 |
| H11 | VersionCard local-null wording | FIXED |
| H15 | initial focus | FIXED editor only |
| H2 | row 200% / truncation | PARTIAL: ellipsis removed, chips scroll; row still fixed Row (W1) |
| H3 | row semantics | FIXED (row clickable removed; per-row Edit/Delete labels) |
| C5 | missing states | PARTIAL: approved + logout confirm done. Rest NOT done (C2 below) |
| C1 | tests don't render UI | NOT FIXED (C1 below) |
| H6 | body <16sp | NOT FIXED (W4) |
| H12 | dead strings.xml | NOT FIXED (W5) |
| H13 | doc drift | PARTIAL (W7) |
| H14 | `onAuthSubmit = {}` default | FIXED (now required) |
| Medium DRY items | NOT addressed (S-list) |

## Critical
1. **Success Criterion unmet: no rendered-UI tests.** `todo-semantics-test.kt` still: 1 genuine test (contrast math) + `toString` redaction + `TitlePolicy` + 4 data-class getter tests. Zero composables rendered, no `compose.uiTest` dep (`grep uiTest shared/build.gradle.kts libs.versions.toml` → none). `testLeaveGuardTalliesAndSafety` still re-implements `hasUnsynced` (false proof). Plan step 7 explicit: render real shared composables, assert labels/actions/focus/password clearing, unknown ≠ accepted/rejected, no reset auto-reapply. Cycle-1 remediation list skips this; 96/96 green does not evidence it. Password-cleared-after-submit, IME Done gating, approved≠request form, compact Notices entry, Decide Later → all untested.
2. **Required states still not implemented nor recorded as contract gaps.** Plan step 4/6 + Requirements: UnknownSuperseded retained-intent review, ResetRequired old-epoch review, ProtocolIncompatible/DataInvalid repair guidance, AuthorizationUnresolved, credential-persistence-pending, admin-login warning (only in `strings.xml`, never rendered), Load more, leave counts for drafts/UnknownSuperseded/reset + sync-then-leave option, REAUTH local-data access/local-only logout. `SyncFeedback` still one-line labels; `ConflictReview` copy treats everything as "another client changed". Frozen contract (`todo-ui-state.kt`, `TodoAction`) may not carry them, but then must be documented in plan/design doc + raised to contract owner — grep finds no such note. Silent drop = scope shrink w/o approval.

## Warnings (High)
- W1. **Todo row at 200% text**: fixed `Row` 48dp checkbox + Edit + Delete TextButtons + `weight(1f)` title; ~40–80dp title width on 360dp [INFERENCE, needs uiTest/device]. Use FlowRow / stack actions under title when fontScale large / overflow menu.
- W2. **Compact top bar not scrollable** (`Scaffold topBar` Column: title + Add + Notices + Leave Row + SyncFeedback). At 200%/landscape it consumes viewport or clips; TodoList gets no space. Move into LazyColumn header or make bar collapse/scroll [INFERENCE].
- W3. **"Decide Later" = `TodoAction.DismissError`**: no handler anywhere in repo (grep: only definition + UI callers); `contracts.md` has no mention. Unless coordinator clears `conflict` on DismissError, dialog reappears → trap. Also semantic overload (error banner dismiss vs conflict defer). Needs explicit contract action (e.g. `DeferConflict`) or documented guarantee. Dialog `onDismissRequest` (Esc/back) same.
- W4. **Body <16sp** unchanged: `bodySmall` 14sp for errors, help, statuses, access note, version metadata; `labelLarge` 14sp / `labelMedium` 12sp / `labelSmall` 12sp for actions ("Edit", "Delete", "Notices", "Leave", "Reconcile", "Sync Now", "Pending sync"). Plan requires body ≥16sp. Design doc §3.1 claims body "strictly ≥16sp (bodyLarge/bodyMedium)" — sidesteps usage of bodySmall.
- W5. **`strings.xml` dead**: 42 strings, 0 `Res.string`/`stringResource` usage; UI hard-coded English; diverging copy. Wire or delete (YAGNI/DRY). Plan lists the file as owned deliverable → wire it.
- W6. **`formatTimestamp`**: `Instant.toString().substringBefore('.')` – whole-second instant has no '.' → leaves trailing `Z` ("…05:41:29Z"); fractional case drops `Z` → unlabeled UTC shown as local. Use `toLocalDateTime(TimeZone.currentSystemDefault())` and format explicit. Blanket `catch (Exception)` unnecessary.
- W7. **Docs numbers not "exact"** (doc claims script-exact luminance). Recomputed vs doc: Ink/Page 17.15 (doc 16.98), Ink/Cream 16.25 (15.74), White/PrimaryDeep **12.94 (doc 13.68)**, White/Coral 5.62 (5.63), Coral/CoralS 4.91 (4.90), Muted/White 6.31 (6.28), Muted/MintS 5.44 (5.47), Outline/White 4.72 (4.68), Mint/White **2.18 (doc "1.8")**. All pass thresholds; only accuracy. Theme comments carry same drift (`DoggoPrimaryDeep 13.68`). Other doc drift: §4 says "Licenses" button (actual "Notices"); §5 claims Escape/Enter keyboard support + "Transient password memory is cleared" (JVM `String` just dropped ref; KDoc `AuthSubmitAction` "memory inspections" overclaim) — unverified; status "Authoritative (Cycle 2 Verified)" unjustified w/o ui tests.
- W8. **Contrast test incomplete**: asserts 10 pairs; used-but-untested pairs: muted/cream (compact bar), muted/coralSurface (VersionCard), ink/mintSurface, ink/coralSurface, mintSurface-on-primaryDeep + coralSurface-on-primaryDeep (sidebar text/Leave), primary/mintSurface, primaryDeep/cream. All pass (5.74, 5.51, 15.4, 15.59, 11.16, 11.3, 9.22, 11.78) → add so future palette edits cannot regress. Test hand-lists pairs rather than tied to usage; acceptable but weak.
- W9. **Unknown access status → request form** (`else` branch) so unknown/`none` identical; plan: unknown distinct, approved-without-grant stays admin-pending. Only approved/pending/rejected distinct.
- W10. **Doc/provenance**: `font-provenance.md` still claims viewer shows "all bundled dependencies" (viewer is hand-curated); Alexandria authorship/checksum audit not evidenced (cycle 1 question open). System-font decision itself is plan-permitted.

## Medium
- `AccessRequestPanel` logout confirm OK, but copy says "sign out ... on this device" vs `LeaveGuard` "Leave & Wipe Local Data": `AuthSubmitAction.Logout` semantics undefined (local-only wipe vs session) — confirmation text may misstate.
- Compact top bar Leave `TextButton` + error color: fine contrast, but destructive action adjacent to Add/Notices at 12sp.
- Conflict dialog still says "another client" for all conflicts; known conflict vs UnknownSuperseded copy absent.
- DRY: `doggo-app.kt` 461 LOC duplicates compact/expanded header; 7 identical dialog `Card` shells; `ButtonDefaults.buttonColors(primary,onPrimary)` repeated ~10× (= default). `DoggoCustomColors` data class never varied (YAGNI). `fontFamily = SansSerif` repeated 15× (default).
- `String.codePointCount` hand-rolled counter vs `TitlePolicy` differ (lone surrogates, trim).
- Signup help says "bytes" limits good; password field not `KeyboardCapitalization.None`/autofill hints.
- `LicenseNotices` content hard-coded; phases 09/10 shall share — single source not established.

## Suggestions
1. Add `compose.uiTest` (jvm `runComposeUiTest`) + rewrite tests: password cleared after submit + mode switch; Done blocked without username; approved vs request form; compact layout shows Notices; row Edit/Delete labels; conflict actions dispatch; remote-deleted hides Overwrite; Decide Later dispatch; leave confirm/cancel; fontScale 2.0 reachable actions. Delete getter-only tests.
2. Document contract gaps (UnknownSuperseded/reset/protocol/admin/Load more/leave counts/DeferConflict) in plan Unresolved + design guidelines; request contract amendment from owner.
3. Raise action labels/help to ≥16sp or accept & record plan deviation w/ user approval.
4. Complete contrast test matrix + regenerate doc table from the test output; correct doc/theme comments.
5. Wire `strings.xml` or delete.
6. Scrollable compact header; FlowRow rows.
7. Extract `DoggoDialogCard`, `AuthGate`, `ExpandedWorkspace`, `CompactWorkspace`; drop default-equal color overrides.

## Positive
- Contrast palette fix correct; verified all asserted pairs; test is true math (not tautology) and covers non-text 3:1.
- `submit()` single path, password cleared on submit/mode/dispose, explicit `password()` semantics, live region on errors, keyboard options on email/username.
- Editor: `remember(rawTitle)`, focus requester w/ guarded request, no accidental draft loss.
- Approved state + confirmation dialogs (sign out, delete without echoing title, leave).
- Compact Notices reachable; notices list reflects actual stack, no misleading OFL card.
- Scroll on all dialog/form bodies; `required` `onAuthSubmit`.
- Light-only, dynamic color off, 48dp targets, stable keys, non-color completion state.

## Validation
- `JAVA_HOME=$HOME/.local/share/jvm/jdk-21 ./gradlew --dependency-verification=lenient :shared:jvmTest --rerun-tasks` → BUILD SUCCESSFUL (5.7s); 7 `TodoSemanticsTest` PASSED + rest of suite. Note `--dependency-verification=lenient` bypass (not phase-06 defect).
- Python WCAG recompute (table above/W7/W8): all functional pairs ≥4.5, outline ≥3.
- Not run: Android/desktop runtime, screenshots, TalkBack, 200% font scale.

## Plan status
Todo items remain unchecked; phase not accepted. Plan Review status updated.

## Unresolved questions
1. Are UnknownSuperseded / reset review / protocol repair / admin warning / Load more / extra leave counts deferred because contract frozen? Who amends (02 owner)?
2. What does coordinator do on `DismissError` when `conflict != null`? Need explicit defer action?
3. `AuthSubmitAction.Logout`: local wipe or session-only? Confirm copy depends.
4. Is body ≥16sp binding for secondary text (errors/help), or only primary body copy? If relaxed, record approval.
5. Alexandria/Cal Sans upstream authorship + license verification before keeping provenance doc claims.
