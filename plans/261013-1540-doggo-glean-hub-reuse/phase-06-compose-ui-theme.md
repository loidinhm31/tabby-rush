# Phase 06 — Shared Compose UI and accessible light theme

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Selected strict A UX limits](reports/strong-sync-design-feasibility.md) · [Source/isolation evidence](reports/strong-sync-isolation-feasibility.md).
- [Historical platform/theme evidence](../261013-0914-doggo-compose-todos/research/researcher-03-theme-observation.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group C after02 with03/04/05. Pure common contracts only; no data/auth implementation imports. Font audit before bundling. Semantic tests written here; executed07; actual Android07/08 then Linux09/Windows10.

## Overview
- Date: 2026-10-13
- Description: Shared Compose UI and accessible light theme.
- Priority: P2
- Implementation status: completed (100% - 2026-10-14T12:56:00Z, user-directed administrative completion)
- Review status: NOT ACCEPTED (Cycle 3 score 6.5/10; outstanding blockers; ../reports/code-reviewer-261014-1251-phase-06-compose-ui-theme-cycle3.md). Technical acceptance withheld due to implementation and test gaps (admin substring heuristic, unsupported doc claims, absent compose.uiTest rendered-UI test harness, missing states/semantics, typography <16sp, unwired strings.xml, non-scrolling compact top-bar). 96/96 :shared:jvmTest tests pass under lenient dependency verification (strict fails; includes 7 Phase 06 state/token tests; rendered-UI tests absent). Implementation and review gaps tracked for resolution by original owners during Phase 07 integration.
- Effort: 12h planned allocation (total plan 148h, uncertainty ±25–35%).

## Key Insights
ForumFlash measured light palette/type is visual inspiration, not IELTS domain/brand assets. Offline saved locally differs from explicitly accepted server revision. Access pending is not empty authorized account; strict conflicts, UnknownSuperseded and epoch reset need distinct retained-intent UI.

## Requirements
Signup/login/access request/status/rejection/approved refresh, offline/reauth/local-save/pending/uncertain/known-conflict/UnknownSuperseded/protocol/reset-review/leave states; all todo CRUD/filter/counts/load more; raw drafts retained. >=48dp controls,body>=16sp,200% text,normal contrast>=4.5:1,large/control>=3:1,focus/keyboard/TalkBack roles,not color-only.

## Architecture
DoggoTheme{DoggoApp(state,onAction,onAuthSubmit)} consumes public state, emits frozen actions. Transient masked password callback separate from persisted actions; no rememberSaveable secrets. Compact stacked and >=840dp sidebar/limited-width list, same feature set. Common fonts/licenses loaded through02 generated dev.doggo.shared.resources.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/doggo-app.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/account-form.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/access-request-panel.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-list.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/todo-editor.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/sync-feedback.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/conflict-review.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/leave-guard.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/ui/license-notices.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/theme/doggo-theme.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/kotlin/dev/doggo/shared/theme/doggo-typography.kt` — create.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/font/cal_sans_regular.ttf` — conditional create after real rights/coverage audit; otherwise documented omission.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/font/alexandria_regular.ttf` — conditional create after real rights/coverage audit; otherwise documented omission.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/font/alexandria_semibold.ttf` — conditional create after real rights/coverage audit; otherwise documented omission.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/files/cal-sans-license.txt` — conditional create after real rights/coverage audit; otherwise documented omission.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/files/alexandria-license.txt` — conditional create after real rights/coverage audit; otherwise documented omission.
- `/home/loidinh/WS/doggo/shared/src/commonMain/composeResources/values/strings.xml` — create.
- `/home/loidinh/WS/doggo/shared/src/commonTest/kotlin/dev/doggo/shared/ui/todo-semantics-test.kt` — create.
- `/home/loidinh/WS/doggo/docs/design-guidelines.md` — create.
- `/home/loidinh/WS/doggo/docs/font-provenance.md` — create.

## File Ownership
Exclusive phase06 owner of all 20 exact absolute paths above; those and only those phase06 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer context links are read-only. New tracked path requires reviewed registry amendment; integration reopens original owner.

## Implementation Steps
1. Audit upstream Cal Sans/Alexandria actual revision/checksum/OFL or actual license/reserved-name/redistribution/glyph coverage. Bundle legitimate TTFs and notices only if allowed; otherwise documented accessible platform font choice and omit conditional rows. No copied site WOFF/logo/illustration, invented license or runtime font CDN.
2. Implement light tokens primary#004646,deep#003838,mint#27C4B5,mint-surface#E6F0F2,cream#F7F4ED,coral#ED3838,coral-surface#FFEBEB,line#D9E3E4,ink#0F172A,muted#667279,white#FFFFFF,page#F8FBFA. Dynamic colors off; measure actual state pairs and adjust functional contrast, label adjustments not observed website values.4/8/12/16/24/32dp spacing,12dp controls/20dp cards.
3. Account form uses email/password login and username/email/password signup, help with byte-based limits without taking server authority. Password transient masked/non-saveable/cleared after submit/disposal, never spoken in semantics/screenshots. No token/origin form or browser OAuth assumptions. Safe errors only; submission cannot authorize by decoded JWT.
4. Access panel distinguishes pending/rejected/unknown, request message transient (non-secret), reload status and refresh/relogin for approval. No native admin grant panel. Existing established offline session can display cached data; fresh login/signup online-only. Locked vault/reauth shows actionable recovery preserving work.
   Approved request without current `/me.apps` grant stays admin-pending. Show admin-login warning recommending non-admin account; no admin controls. AuthorizationUnresolved pauses sync with operator recovery; DataInvalid/ProtocolIncompatible shows stale reconciliation and repair/upgrade guidance while retaining local edits. Credential-persistence-pending stops background activity and offers safe vault retry/reauthentication,never local wipe.
5. Todo UI stable keys, whole projection counts/filter, lazy pages/Load more, long Unicode wrap and non-color checkbox status; normalized scalar title help but raw draft remains. Explicit create/edit/complete/reopen/delete cancel/confirm and local-save failure. Offline saved locally/pending versus cloud reconciliation timestamp, never global up-to-date.
6. Feedback shows saved-locally/pending, exact accepted revision and last completed reconciliation, never global freshness. Known conflict means rejected current attempt; UnknownSuperseded means earlier uncertain delivery may have succeeded before a successor, never label it failed/accepted from equal content. Review remote base+retained intent with explicit discard, deliberate new operation after viewing current live base, or CopyAsNew UUID; deleted/missing identity cannot be updated/recreated. ProtocolIncompatible/DataInvalid pauses repair without legacy fallback. ResetRequired separates old-epoch intents/drafts and requires explicit review before any new-epoch copy; never automatic rebase.
   Leave dialog counts drafts/pending/uncertain/conflicts/UnknownSuperseded/reset-review and explains sync-then-leave/cancel/discard, local-only loss and possible remote commit. Permanent deletion fences contain no deleted title; no restore/undo endpoint invented. Admin warning/local-only logout remain confirmed.
7. Semantic tests render real shared composables from controlled immutable states and assert labels/actions/focus/password clearing: onboarding/access/offline/reauth, empty vs filtered-empty, edit validation, pending successor/known conflict/UnknownSuperseded/protocol/reset review, delete and leave confirm/cancel. Verify unknown is neither accepted nor rejected, equal remote payload does not hide review, no reset automatic-reapply action. No duplicated coordinator/source-text proof. Use tuple-proven02 common UI harness; execute07.
8. Write design-guidelines and font-provenance with observed/reference versus chosen tokens, actual rights/glyph audits/contrast methodology/responsive rules. Native screenshots/TalkBack/keyboard/IME/200% evidence owned07/08/09/10; previews/tests not runtime certification.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior,not scaffolds. (15 owned files delivered across shared UI, theme, resources, tests, and documentation; font assets omitted per documented platform font provenance audit; rendered-UI tests, admin contract field, and secondary text ≥16sp remain unmet)
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence. (7 Phase 06 state/token tests and 89 inherited JVM tests pass with --dependency-verification=lenient; strict verification fails; rendered Compose UI tests absent; Android instrumentation, TalkBack, and 200% scale gates unexecuted and deferred to Phase 07)
- [ ] Resolve failures through original owner and complete parent review; do not mark implementation accepted early. (Phase marked completed per user administrative directive; technical review cycle 3 scored 6.5/10 NOT ACCEPTED; outstanding review blockers C1-C3 and warnings W-A-W-G tracked for resolution by original owners during Phase 07 integration)

## Success Criteria
All frozen UI exports and required states usable without concrete auth/data coupling; password never persisted/leaked; accessible tokens/font rights/coverage or documented honest system fonts; semantic suite passes07 and actual platform acceptance supplies contrast/scale/keyboard/screen-reader evidence.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Glyph/license gaps and common resource/test API depend02 tuple. Website mint/coral pairs may fail contrast; accessibility wins. IME, large text and remote Skiko rendering require actual runtime corrections by06.

## Security Considerations
Secrets excluded from semantics/save-state/replay/logs/evidence; only safe errors. No copied private reference content or runtime third-party asset requests.

## Next steps
07 injects real public state/actions/auth callback; actual UI defects reopen06.09/10 share same resources and notices without duplicate owned assets.

## Unresolved questions
1. Admin identity contract: will contract owner add `isAdmin` (from Hub `LoginResponse.isAdmin`) to `AccountIdentity` or UI state to replace `contains("admin")` heuristic?
2. Rendered-UI test harness: will `compose.uiTest` dependency be added to `:shared` build (Phase 02 contract amendment) or deferred to Phase 07?
3. Action semantics: clarify `TodoAction.DismissError` coordinator behaviour when error dismissed during conflict vs defer-conflict.
4. Typography constraint: is ≥16sp strictly binding for secondary/label text (`bodySmall`, `labelSmall`) or is deviation approved?
5. Localization: wire `strings.xml` resources in Phase 07 or remove from owned file list?
