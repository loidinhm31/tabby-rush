# Phase 04 Implementation Report

## Executed phase
- Phase: phase-04-admin-ui; plan: plans/261020-1717-standard-sync-schema-version.
- Status: implementation complete; integrated execution/browser acceptance pending coordinator.
- Ownership: only four assigned H UI source files changed; no Rust/generated types/config/auth/docs/other-phase files touched. This report is the sole plan write.
- Frontend-development skill and H/UI/published development guidance read. No configured language server available per coordinator; scoped TypeScript/TSX/JS/MJS symbol and API-route searches used instead.

## Files modified
Paths relative to H = G:/ws/sharing/glean-hub; counts are final source lines, not diff totals.
- glean-hub-app/src/services/sync-admin-service.ts — 449 lines.
- glean-hub-app/src/pages/admin/edit-app.tsx — 265 lines.
- glean-hub-app/src/components/organisms/schema-editor.tsx — 373 lines.
- glean-hub-app/src/pages/admin/app-registration.tsx — 153 lines.

## Tasks completed / behavior
- GET/PUT authoritative DTOs require matching appId, schema and positive safe-integer schemaVersion. Registration requires matching returned appId, API key and safe version; no client baseline synthesis.
- Required updateAppSchema(appId, {schema, expectedSchemaVersion, sharing?}) returns full authoritative DTO. Unversioned/void signature removed, sole UI caller migrated. Invalid expected counters fail before fetch.
- SchemaVersionConflictError exposes only a valid structured 409/currentSchemaVersion; malformed/unrelated 409 uses fixed safe feedback, never raw JSON/HTML. Other failures retain existing sanitizer/auth conventions.
- Loaded schema/sharing/version stored as a single baseline. Only authoritative initial load, successful readback or explicit discard/reload adopts a replacement.
- Server syncMode alone chooses ordinary editor versus existing strict panel. Strict helpers, canonical fingerprints and strict upgrade controls remain unchanged for other apps.
- Save captures loaded expectation, not pasted appId/schemaVersion or conflict metadata. Builder, exact raw JSON, input mode and sharing text remain unchanged on conflict, validation or network errors.
- Whole edit surface disabled through PUT plus GET readback; synchronous request guard also prevents overlapping page actions.
- Successful PUT retained and shown separately from current GET snapshot, including full schema/sharing JSON and accepted/current versions. Newer readback explicitly belongs to another save. No optimistic counter or immediate navigation.
- Failed GET after accepted PUT retains draft plus full accepted response, pauses editing and exposes GET-only refresh; no resubmission or unsaved-failure claim. Older-than-accepted readback also fails honestly.
- View latest fetches a separate read-only snapshot without changing baseline/drafts. Explicit discard/reload requires a warning confirmation, fetches fresh state, then adopts it. No auto-merge, hidden precondition upgrade or automatic retry.
- App-route key plus generation fencing prevents old GET/PUT/readback/latest completions from mutating another app. Failed initial load blocks save and permits an explicit retry.
- SchemaEditor synchronizes builder and JSON only when authoritative initialSchema changes; input mode retained. Native disabled fieldset covers all controls during save/readback.
- Registration retains returned appId/schemaVersion alongside transient API key in component state only. Registration input/sharing/schema busy state shared; direct wrapper-JSON save uses its imported app ID rather than stale React state. Existing-app imports cannot change identity/expectation.
- Blank/omitted and parsed-null sharing keep existing server semantics; no new clearing API. Backend owner confirmed omitted/null retain existing config, absent projection omitted.

## Caller inventory / intentionally unchanged
- updateAppSchema: service export and EditApp only; all migrated together, no compatibility shim.
- getAppSchema/AppSchemaWithSharing: service and EditApp only.
- registerApp/RegisterAppResponse: service and AppRegistration only.
- SchemaEditor: EditApp and AppRegistration only; both share disabled/save contract.
- Existing sync-admin-service.test.ts contains meaningful strict fingerprint/version/column behavior, unaffected; unchanged. No broken wording/wiring test discovered in owned scope. No new tests, permanent UI suite or temporary scripts created.
- StrictAppUpgradePanel, StrictContractCard, StrictUpgradeControls, app-list and generated core index read unchanged. No N+1 detail fetch added to app listing.

## Tests / pending commands
- Build/typecheck/unit/integration/browser: NOT RUN, as required during edit wave. No success claimed.
- Main, after backend/native land and backend-owned generation: from H, `pnpm --dir glean-hub-app test:unit`, then `pnpm --dir glean-hub-app build` (includes tsc; no redundant standalone typecheck).
- Backend generation, coordinator only: `cargo run -p glean-hub-sync-client --bin export_types`; output glean-hub-core/glean-hub-sync-client-types/src/index.ts. No UI-owned generated artifacts.
- Use sanitized source mirror/private disposable configuration, not existing .env or live 3000/3001. Parent owns disposable Mongo 27191 and test_standard_sync_* DB selection; no DB accessed by this phase.

## Real-browser scenario handoff
Use isolated Hub 3100 and separate explicitly configured UI origin, dedicated disposable admin, real backend/database; never request interception, mock fetch or live registrations. Browser work is pending, not exercised here.
1. Register demo-notes using builder and separately wrapper JSON. Verify returned identity/version shown, one-time key only in current component state; redact screenshots before key capture. Reload: no persisted registration key.
2. Open /admin/apps/demo-notes/edit. Paste schema wrapper including a deliberately unrelated schemaVersion; add a nullable column and save. Inspect actual PUT loaded expectedSchemaVersion, then GET. Stay on page; accepted/current full JSON visible. Reload confirms persisted version/content. Standard sync a valid record against the changed schema via parent harness.
3. Save unchanged and map-key-reordered schemas. Version/timestamp unchanged; reorder column array to establish a real increment. Two contexts submitting identical stale content should both receive authoritative success without extra increment; verify database/API state.
4. Two contexts load the same version. In B retain independent builder edits, raw JSON whitespace/input mode and sharing text. Save distinct A edit; B save must get exact 409/current counter with no auto PUT, navigation or reset. Switch B modes to inspect both drafts. View latest leaves loaded expectation/drafts untouched; cancellation of discard warning changes nothing. Confirm discard, then deliberately reapply intent and save with freshly loaded expectation.
5. Use real disposable read-path interruption after accepted PUT, without replacing acceptance. Verify accepted version/full response remains, original draft retained and controls paused. Restore read path, Refresh readback sends GET only. For newer readback, coordinate another real admin save between PUT and GET; verify accepted/current distinction and current content adopted, not claimed as submitted content.
6. Exercise invalid raw JSON, invalid sharing JSON, backend validation rejection and offline/network failure. Exact draft text/state retained; invalid initial metadata prevents rendering an editable baseline. Malformed/unrelated 409 produces safe generic conflict feedback, not raw response. Safe-integer/required-precondition API cases belong backend real request checks as well.
7. Throttle/delay actual disposable network responses; navigate from app A to B before initial GET and separately save/readback complete. Verify B identity/schema/version/sharing stay isolated and A late completion does not appear. No response fabrication.
8. Ordinary Hub fixture and converted TabbyRush fixture both expose existing builder/JSON. Unrelated strict fixture still exposes existing canonical upgrade panel, with loaded numeric registry version outside it; arbitrary ordinary PUT remains server-rejected.

## Issues / risks
- No observed ownership conflicts or blocked implementation prerequisites.
- Real server/browser execution still required for named acceptance; no acceptance checkbox/status changed.
- Counter checks do not replace authoritative server schema validation or make required/type-breaking schema changes safe for existing native data.
- Existing strict panel retains its own lifecycle; shared header explicitly labels its counter as the loaded registry snapshot, not an automatically polled current value.

## Unresolved questions
- None for implementation; coordinator supplies isolated browser origins/admin and real failure-window orchestration at verification.
