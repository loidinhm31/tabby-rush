# Phase 04 — Existing Hub Schema Editor With Versioned Saves

## Context / overview
- Status: **source implementation complete; verification in progress.** Priority: P1.
- Root `H = G:/ws/sharing/glean-hub`; all source paths below relative to H. Read [contract/ownership](contracts-and-ownership.md), [scout evidence](reports/scout-findings.md#hubsyncscout), [architecture](../../docs/standard-sync-cutover-design.md), [backend wire/examples](phase-02-hub-backend.md#exact-proposed-public-json-examples), H `glean-hub-app/AGENTS.md`.
- Dependency: phase01 contract freeze and phase02 API/types are delivered. Admin source and partial browser-smoke evidence are above; phase05 owns the remaining browser matrix and integrated acceptance.
- Exclusive ownership: affected H `glean-hub-app/src/services/`, `src/pages/admin/`, existing `src/components/organisms/schema-editor.tsx` and their listed tests. No Rust, generated core TS index, native, auth/config, strict-panel runtime, top-level plan or docs writes. Phase05 owns API docs/changelog, fixture services and integrated browser evidence.

## Current status — 2026-10-20

- The existing admin schema editor retains its builder and JSON modes; no second editor was added. Current browser evidence covers only the JSON-paste path, not a separate builder scenario.
- Parent-reported actual disposable admin-browser smoke pasted nullable `customNotes`, moving `schemaVersion` 1→2; identical save/no-op stayed at 2. A stale distinct save returned 409, preserved the draft, and `View latest` remained read-only. Explicit discard/reload adopted version 2. The full browser matrix is not complete; see [final integration verification](reports/verification-final.md).
- Production admin accounts/registrations were not used. The named unchecked rows below are **acceptance holds, not implementation TODOs**; source delivery is complete. Unexercised cases include stale-identical retry/timestamp and ordering semantics, full reload/readback variants, preservation of builder/JSON/sharing drafts, failure/newer-readback handling, registration/mode/strict-panel behavior and delayed route changes. See [phase04 implementation](reports/phase-04-implementation.md), [source review](reports/review-hub-ui.md), and [final follow-up review](reports/review-hub-ui-final.md); the latter covers operator/provisioner fixes, not a new full UI audit.

## Insights / requirements
- Existing service `sync-admin-service.ts:219-231,292-305` drops read metadata and returns void from PUT. `edit-app.tsx:53-72` saves without a precondition then navigates away. `schema-editor.tsx:34-58,103-165` already supports builder plus JSON import, but initial-schema changes reset only builder state. Reuse it; no second schema editor/parser.
- Both normal `glean-hub` and **converted** `tabby-rush` use the same ordinary editor based on actual server mode, never appId/fingerprint allowlists. Other strict apps retain `StrictAppUpgradePanel` and canonical strict helpers/tests; no arbitrary strict schema writes or UI-driven conversion.
- Display read-only `schemaVersion` on app detail and registration success. Distinguish numeric registry counter from strict protocol/epoch/fingerprint. Do not make it an editable record column, JSON-paste setting, feature flag or native exact-version lock.
- Required save expectation comes from the authoritative loaded schema snapshot, not pasted JSON, a hardcoded 1, a global app constant or the latest conflict number silently substituted into an old draft.
- Ordinary GET/PUT success requires appId/schema/schemaVersion and existing optional sharing projection. A missing/invalid counter is an actionable incompatible-API error; do not synthesize a baseline client-side. Server legacy baseline handling belongs phase02.
- Preserve exact builder state, raw JSON text/input mode and sharing draft on 409, validation error or network failure. Never remount/reset the editor, navigate away, overwrite draft with server data, or automatically retry a changed save using the conflict version.
- Complete success path includes PUT authoritative response and GET readback, read-only accepted/current versions, full schema/sharing and explicit success status. No success claim from only HTTP status/message or optimistic local increment.

## Required API/service contract
- Extend existing `AppSchemaWithSharing` with required `appId: string` and `schemaVersion: number`; normal/strict metadata remains optional. Extend normal RegisterAppResponse with required schemaVersion; preserve one-time API-key handling. Use generated core response types where already consumed, but do not edit the generated index (phase02 owns it); admin schema DTOs remain the existing service convention.
- Migrate exported ordinary save function and its caller together: `updateAppSchema(appId, {schema, expectedSchemaVersion, sharing?}): Promise<AppSchemaWithSharing>`. `expectedSchemaVersion` is required in the TypeScript payload and actual JSON. Keep sharing omission/null behavior unchanged; do not interpret an empty sharing text area as a new clearing API.
- Keep `getValidAccessToken`/existing auth headers and URL encoding. Validate numeric fields with `Number.isSafeInteger(value) && value >= 1`; reject counters above 9007199254740991 and mismatched response appId without fallback. Server remains authoritative for schema validation; UI guards do not replace it.
- Add a small typed service error `SchemaVersionConflictError` carrying currentSchemaVersion from HTTP 409 with the exact phase02 JSON below. Parse this branch before generic sanitized error handling; malformed/unrelated 409 stays a safe generic error, never raw server HTML/JSON rendering. Do not broaden global sanitizer patterns or add fetch retries.

## Exact public request / response example
Fixture ordinary app `demo-notes`; schema is illustrative, not TabbyRush's required 17-field seed. GET `/api/v1/apps/demo-notes` initially returns:
```json
{"appId":"demo-notes","schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false}],"primaryKey":"id"}},"schemaVersion":1}
```
PUT `/api/v1/apps/demo-notes/schema` sends the loaded expectation and edited schema:
```json
{"schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false},{"name":"title","type":"string","nullable":true}],"primaryKey":"id"}},"expectedSchemaVersion":1}
```
HTTP 200 PUT success and an unchanged subsequent GET both return the full projection:
```json
{"appId":"demo-notes","schema":{"todos":{"columns":[{"name":"id","type":"string","nullable":false},{"name":"title","type":"string","nullable":true}],"primaryKey":"id"}},"schemaVersion":2}
```
Same effective schema/sharing with stale expectation 1 returns this same 200/version 2, no timestamp change. A different stale draft receives HTTP 409 exactly:
```json
{"error":"Schema version conflict","currentSchemaVersion":2}
```
Normal registration success includes the server counter, not an editable form value:
```json
{"appId":"demo-notes","apiKey":"fixture-issued-key","schemaVersion":1}
```
Sharing-enabled success also contains the authoritative existing `sharing` object. Optional mode/epoch appear only where server projection provides them; do not invent nulls or add a top-level schemaVersion to delta (its nested push/pull carry it).

## Related code files / exclusive ownership
- MODIFY `glean-hub-app/src/services/sync-admin-service.ts`: required version DTOs, typed conflict, complete GET/save responses, required expectation and all in-file affected consumers. Retain strict contract/upgrade helpers for other strict apps; no compatibility overload for unversioned saves.
- MODIFY `glean-hub-app/src/pages/admin/edit-app.tsx`: loaded snapshot/precondition, read-only version, success/readback state, draft-safe conflict/latest inspection, no immediate navigate-on-save, request fencing for app-route changes.
- MODIFY `glean-hub-app/src/components/organisms/schema-editor.tsx`: preserve builder/JSON parsing workflow; optional disabled/save-blocked state during save/readback; synchronize builder+JSON only on authoritative initial/success/explicit reload. No conflict-driven initialSchema reset.
- MODIFY `glean-hub-app/src/pages/admin/app-registration.tsx`: retain returned version with returned appId/API key and display registry version in success. Keep API key transient; no extra app-list reads or localStorage persistence.
- MODIFY `glean-hub-app/src/services/sync-admin-service.test.ts` only if existing meaningful tests break: retain useful strict tests, migrate broken contract expectations, remove incidental wording/wiring assertions rather than re-pin them. Do not add payload/forwarding/mock-echo tests.
- No new permanent UI test framework, Playwright spec or config. Phase05 exercises this actual surface with browser helpers against the isolated deployment; retain existing meaningful tests and update only contracts broken by this change. Temporary interaction scripts are removed after proof.
- READ unchanged `glean-hub-app/src/components/organisms/strict-app-upgrade-panel.tsx`, `strict-contract-card.tsx`, `strict-upgrade-controls.tsx`, `src/pages/admin/app-list.tsx`, generated `glean-hub-core/glean-hub-sync-client-types/src/index.ts`. No list endpoint version/N+1 detail fetch required; display on detail and registration where authoritative version exists.
- No existing whole-file deletions identified. Remove obsolete void response/message-only handling, old save calls and immediate success navigation. No unversioned shim/re-export. Newly found UI caller belongs this phase; update ownership manifest before adding paths. Backend/core/native callers go to their owners, not shared edits.

## Numbered implementation steps
1. Use TypeScript language-server references for updateAppSchema, AppSchemaWithSharing, registerApp and SchemaEditor props before changing exported signatures; supplement with whole-H API-route search. Current observed ordinary caller is EditApp; inventory any newly discovered normal-app save callers, migrate all, and pass script/docs consumers to phase05. Do not assume other normal apps use strict panels.
2. Implement required versions/expected value and full read/save decoding in service. Expose typed conflict only for valid structured response; use existing sanitized messages otherwise. Return authoritative DTO, not optimistic input or `{message}`. Do not add forwarding, serialized-payload, source-text or mock-echo tests; concurrency/no-op correctness belongs the real backend behavior suite and browser proof.
3. In EditApp, store loaded appId/schema/sharing/version as one baseline snapshot. Render version outside the schema editor; initialize drafts once per successful initial load. Reset loading/errors/version/drafts deliberately on route change; cancel/ignore late GET/PUT completions from a different app so one app cannot overwrite another editor's state.
4. Submit a captured draft plus that baseline's expectedSchemaVersion. Disable editing and repeated save through the complete request/readback sequence; sharing and SchemaEditor honor the same busy state. Do not allow a mid-flight local edit to be discarded by later response. Missing baseline/version disables save and displays the real load/API failure.
5. On successful PUT, retain its authoritative accepted version and full state, then GET the same app for full readback. Display accepted version and readback schema/sharing/current version; replace baseline/drafts with authoritative readback only on this acknowledged-success path. Remain on the page so a human can inspect saved JSON/version and choose navigation explicitly.
6. If GET is newer than PUT (another editor saved meanwhile), report saved-at version and newer current version distinctly; never claim the latest content was this editor's submitted draft. If readback fails after acknowledged PUT, say save accepted/version but readback unavailable, preserve draft and accepted response; offer explicit refresh. Do not label it an unsaved failure, rollback or blindly resubmit.
7. On 409, retain baseline expectation and all draft representations exactly; show loaded version/currentSchemaVersion and a conflict state. `View latest` fetches a separate read-only snapshot without changing draft/baseline. Offer explicit discard-and-reload with data-loss warning; only that deliberate action adopts latest schema/sharing/version. To keep draft intent, user copies/reviews/reapplies it after reload; no auto-merge, hidden expectation upgrade or automatic overwrite/retry.
8. Other errors leave draft and baseline untouched with safe actionable feedback. Server no-op success naturally displays the same version. Imported wrapper JSON may contain appId/schemaVersion metadata, but only schema is editable for an existing app: never use imported version as expected value or turn it into a column. Keep current builder/JSON formats and sharing semantics.
9. Ensure SchemaEditor synchronizes both builder and JSON from new authoritative initialSchema after initial load/success/explicit reload, preserving chosen input mode where possible. Do not pass conflict/latest preview as initialSchema or change component key on errors; invalid raw JSON must remain available for correction. Reuse existing table conversion helpers, not another schema parser.
10. Registration records returned appId/schemaVersion alongside existing transient API key; show version 1 from the response, never assume it. Actual strict app details continue through StrictAppUpgradePanel, numeric registry version displayed in shared page header; ordinary converted TabbyRush automatically reaches the standard editor after GET shows mode absent.
11. Update affected existing behavioral tests only; do not introduce a new UI suite. Phase05 has exercised a real-browser smoke subset; the remaining acceptance cases are listed below and remain phase05-owned. No request interception, mock fetch or stubbed server acceptance; use explicit isolated UI/Hub origins, a dedicated real test admin and separate contexts for stale saves; never current live registrations.

## Acceptance holds — named browser scenarios / commands
- [ ] `json_paste_save_reads_back_version`: real admin JSON paste through existing editor; request contains loaded expectation; nullable-field addition produces exactly one increment; full PUT+GET schema/sharing/version are visible, survive page reload and permit a valid standard sync record through the same real Hub.
- [ ] `semantic_noop_and_stale_identical_retry`: unchanged/reordered-map save and stale-identical second context keep version/timestamp; column reorder is a real change. Validate persisted state through actual API/backend evidence, not only text in UI.
- [ ] `stale_different_save_preserves_all_drafts`: two contexts load 1; A publishes 2; B's distinct JSON/builder/sharing draft gets exact 409/current 2, no extra PUT, navigation, remount or mutation. View-latest leaves B's draft and expectation 1 unchanged; explicit discard/reload adopts 2 before deliberate new save.
- [ ] `readback_failure_and_newer_readback_are_honest`: after real accepted save, operationally interrupt/readmit the disposable read path or coordinate another real admin save; accepted version remains distinct from unavailable/newer current state. Network failure, invalid JSON and failed precondition preserve intent; no mock-only evidence.
- [ ] `mode_drives_editor_and_registration_displays_counter`: normal Hub fixture and actual converted TabbyRush fixture show builder/JSON; unrelated strict fixture still exposes strict panel and rejects arbitrary ordinary save. Registration displays returned 1, key not persisted. Missing/invalid metadata never becomes fake version 1; metadata is not editable/pasted into record columns.
- [ ] `route_change_ignores_old_completion`: switch app while real response is delayed; no cross-app schema/version/sharing overwrite. Verify safe-integer limits, required expected version and malformed conflict feedback through actual consumer-visible behavior; no wiring/payload-only permanent tests.
- [x] Main ran the integrated Hub admin unit/build verification: final report records 22 unit passes and successful TypeScript/Vite production build.
- [ ] Full real-browser matrix on isolated Hub/Mongo and frontend with dedicated accounts: only the smoke subset above is proven; execute the remaining named cases before closing phase04 acceptance. Existing performance-browser tests are not schema-editor evidence, and no new permanent UI spec/config is required.
- [ ] Record complete matrix evidence and strict-panel preservation. Existing evidence proves JSON paste/version update, no-op, stale draft retention, read-only latest, and explicit discard/reload only; do not treat that subset as full acceptance.

## Conflict prevention / risks / security / next steps
- Phase04 owns service/page/schema-editor changes only. Phase02 owns Rust and generated index; phase05 owns real browser/service orchestration and docs. No new UI test configuration, performance config/auth-constant edits or second parser.
- Prevent lost draft from initialSchema effects, async route races and success readback; do not clear sharing just because an optional projection is absent. A conflict's counter is information, not overwrite permission. Read latest without destructive reset; explicit discard is clearly labeled.
- Preserve existing admin JWT/token refresh boundaries, URL encoding and safe error display. Do not log credentials/drafts, persist one-time keys, access live registrations in tests or add non-admin schema access. Registry version is metadata, not authorization.
- Required/type-breaking changes remain client/data evolution work; warn in editor guidance without adding a new validation engine or pretending nullable columns create native UI. Phase05 documents this and the sharing semantics unchanged by this task.
- Next: source delivery and integrated Hub admin unit/build verification are complete. Phase05 owns the remaining named browser acceptance holds and the controlled rollout gates; do not read unchecked acceptance rows as missing source implementation.

## Unresolved questions
- No design disagreement. Execution needs dedicated isolated Hub/UI origins, real fixture admin credentials supplied securely at runtime, converted TabbyRush fixture and another strict fixture. No live service/account configuration is inferred or read during planning.
