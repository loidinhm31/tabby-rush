# Phase 06 — Shared Compose UI and measured theme

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Authoritative decisions](reports/design-decisions.md) · [Measured ForumFlash evidence](research/researcher-03-theme-observation.md)
- [Shared resources setup](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html) · [Resource use/fonts](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html)

## Parallelization Info
After02 frozen contracts; group C with03/04/05. Reads contracts only, never data internals. Font-license audit precedes bundling. Code/semantic tests ready here; first execution at07, actual Android then Linux09/Windows10. No claim that previews prove runtime.

## Overview
- Date: 2026-10-13
- Description: One accessible todo UI and light visual system shared across platforms.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 9h

## Key Insights
ForumFlash is an IELTS vocabulary site: reuse measured visual language only, no content/logo/illustrations/brand. Teal panels do not imply dark mode. Tiny website labels are unsuitable for Android. Light-only remains an approval gate02.

## Requirements
- Create/list/rename/complete/reopen/delete; filters, whole-dataset counts, refresh and explicit Load more.
- Loading, empty dataset, empty filter, loaded, pending, stale/offline, validation, unauthorized, service error, uncertain mutation, version/missing conflict and delete-confirmation states.
- Preserve drafts; no blind retry/optimistic success. Explicit reconcile/refresh/reapply/cancel actions reflect frozen controller semantics.
- Touch >=48dp; body >=16sp; scalable 200% text; normal contrast >=4.5:1 and large text/control indicators >=3:1. Keyboard/focus/TalkBack semantics; completion not color-only.
- Session input masked/process-only, no rememberSaveable/logs or automatic clipboard copy. Readable license notices included when fonts bundled.

## Architecture
`TabbyRushTheme { TabbyRushApp(state, onAction) }` is the common boundary. UI consumes immutable contract state, emits actions; it does not instantiate a repository. Controller owns title drafts; transient endpoint/token form fields use non-saveable remember and clear after Connect/disposal. At >=840dp use sidebar filters/counts with constrained list/editor; smaller widths stacked. No separate desktop feature fork.

## Related code files
Future create only, no existing application files modified/deleted:
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/session-form.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-list.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-editor.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/todo-feedback.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-theme.kt`
- `shared/src/commonMain/kotlin/dev/tabbyrush/shared/theme/tabbyrush-typography.kt`
- `shared/src/commonMain/composeResources/font/cal_sans_regular.ttf`
- `shared/src/commonMain/composeResources/font/alexandria_regular.ttf`
- `shared/src/commonMain/composeResources/font/alexandria_semibold.ttf`
- `shared/src/commonMain/composeResources/files/cal-sans-license.txt`
- `shared/src/commonMain/composeResources/files/alexandria-license.txt`
- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonTest/kotlin/dev/tabbyrush/shared/ui/todo-semantics-test.kt`
- `docs/design-guidelines.md`
- `docs/font-provenance.md`

## File Ownership
Only phase06 exact registry rows above. Font/license creation conditional on genuine rights/coverage; system-font fallback omits those rows' artifacts and records omission, not fake assets. All resources/build/test dependency configuration stays02-owned. Generated Res is a build output, not an extra owned source. No other tracked icons/assets needed: labeled Material controls suffice.

## Implementation Steps
1. Confirm02 approves scope and generated resource package/configuration. Audit font authoritative upstream source, exact revision, file checksum, license/redistribution/reserved-name terms, static regular/semibold generation if necessary and Latin/Vietnamese/emoji fallback. Obtain TTF from upstream, not site's WOFF/web assets. Record proof in font-provenance; bundle unmodified notices with accessible in-app license view. If rights/coverage unavailable, use accessible platform font family and record visual deviation; never download silently at runtime or invent a license.
2. Implement shared Material3 tokens: primary `#004646`, deep `#003838`, mint `#27C4B5`, mint surface `#E6F0F2`, cream `#F7F4ED`, coral `#ED3838`, coral surface `#FFEBEB`, divider `#D9E3E4`, ink `#0F172A`, muted `#667279`, white `#FFFFFF`, page `#F8FBFA`. Light scheme, dynamic colors off. Teal/white primary, mint/deep-teal secondary; coral accents/destructive surface with contrast-safe dark text, not presumed white-on-coral. Measure actual foreground/background/state pairs and adjust functional contrast without mislabeling adjusted tokens as observed.
3. Typography Cal Sans headings/Alexandria body if audited; Compose common resource identifiers use underscores. Use type/spacings appropriate for app: 4/8/12/16/24/32dp, controls about12dp radius, cards20dp. Compact cream title/count header; no marketing hero. Long Unicode titles wrap, controls remain reachable, content resizes to IME/window insets and large type.
4. Build session form with explanatory session-only token/private shared dataset notice, endpoint field and redacted masked token field; trim/validate endpoint through policy, never trim/change a token into another credential. Clear token input immediately after dispatch; errors retain non-secret useful fields. For dirty editor/uncertain mutation, confirm forget before Disconnect and warn server commit may already exist. Do not put token in state/saved-state/screenshot/test names.
5. Build list/filter/count UI with stable item keys, semantic checkbox completion status, labeled edit/delete, refresh/Load more and per-item pending. Do not infer total count from loaded rows. Differentiate empty filter from empty dataset. Expose stale banner and actionable Retry read, unauthorized re-entry and safe failure text.
6. Editor dispatches ChangeTitle/SubmitEditor/CancelEditor using controller draft; show normalized scalar count and title error without destroying raw input. Delete confirmation uses RequestDelete/ConfirmDelete/CancelDelete. Pending disables duplicate mutation submit, not all read navigation unnecessarily.
7. Feedback maps uncertain state to ReconcilePending, confirmed-absent create to explicit ResubmitConfirmedCreate same ID/payload, conflicts to RefreshConflict/ReapplyConflictDraft/CancelConflict. Reapply discloses latest state/version; missing record cannot reapply/recreate implicitly. Ordinary retry never replays a mutation. Provide cancel/abandon explanation where original may already exist.
   Enable the explicit same-ID create resubmit control only from state.pending.canResubmitCreate; uncertain=true alone is insufficient. State controller revalidates the action; labels explain that an earlier request may still finish.
8. Permanent semantic tests render real composables with controlled immutable states and collect dispatched actions: filter, title validation, submit, confirmation, pending duplicate prevention, conflict/reconciliation, unauthorized and focus labels. Tests assert visible behavior/actions, not source text or duplicated controller logic. Test token absent from public semantics and cleared after dispatch. Test keyboard Enter submit/Escape cancel where supported. Final runnable task chosen02; main07 executes shared test suite with platform tests once.
9. Record actual palette pairs, typography/font audit, accessibility rules and responsive behavior in owned docs. Runtime screenshots/contrast/touch/TalkBack/200% text checks belong07/09/10, not invented here; reopen06 on rendering/accessibility defects.

## Todo list
- [ ] Complete common UI/state actions and measured theme.
- [ ] Complete font rights/coverage audit and real notices or documented system-font selection.
- [ ] Write semantic tests; pass07 execution and actual accessibility gate.

## Success Criteria
Exact frozen UI exports; all CRUD/error/conflict flows usable without data coupling. Licensed common font resources or honest accessible system fallback; no IELTS/brand assets. Semantic tests pass at07; Android actual rendered palette/keyboard/IME/TalkBack/200% checks pass there. Desktop actual rendering remains separate09/10 gates.

## Conflict Prevention
Never edit shared script/catalog/contracts or platform source. Dependency/resource/test harness fixes reopen02; behavioral state gaps reopen05; UI fixes remain06. New tracked assets require exact registry amendment first.

## Risk Assessment
Font license/glyph gaps, generated resources plugin DSL and common UI-test support can differ with tuple. Resolve against02 proof, not old wizard sample. Reference mint/coral pairs may fail contrast; measured values are inspiration, accessibility wins. Small screens/IME/text scaling can expose overflow.

## Security Considerations
Token fields are non-saveable/masked, no logging/serialized replay; descriptions never speak token text. Only safe error strings rendered. Screenshot evidence taken after connection form cleared. No live font CDN or copied private todo titles in evidence.

## Next steps
Hand common UI to07 composition. Main07 executes lane tests then actual Android local UI gate. Later09/10 inspect packaged font/license resources and native keyboard/accessibility; original06 owner repairs defects.

## Unresolved questions
Light-only approval required02. Font licenses/glyph coverage not yet verified; recorded upstream audit or system-font alternative required before bundling.
