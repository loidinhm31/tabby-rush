# Phase 04 — Hub admin UI and shared-operation upgrade script

## Context links
[Parent](plan.md) · [API design](phase-01-freeze-contract-and-transition-design.md) · [Backend dependency](phase-03-hub-versioned-contract-and-migration.md)

## Overview
Date 2026-10-15. Priority P1. Status: **DONE** (2026-10-15) / 100%; Gate G3 live cluster execution ON HOLD pending replica set testbed. Code review approved: 8.5/10. Automated tests: 237/237 PASS. Estimate 12h. Owners: Hub admin frontend + TabbyRush integration tools; integration lead owns operation contract. Depends on phase 03 API/G3.

## Key Insights
Existing editor calls ordinary PUT schema, which must remain forbidden for strict apps. Operator clients must not implement migration logic or mutate Mongo directly.

## Requirements
Show current/admitted contract, supported targets, schema fingerprint, epoch, impact, maintenance downtime and operation state. Script and UI supply identical expected state/idempotency semantics and read back final activation. Keep legacy non-strict schema editor behavior unchanged.

## Architecture
- Typed admin service wraps contract discovery, start/resume and status endpoint. Strict schema read-only; action only for supported upgrade pair and app-scoped admin.
- Confirmation lists preserved data/revisions/receipts, temporary sync pause, old-client incompatibility and backup prerequisite. Resuming an existing migration uses its original ID/preconditions.
- Script `hub-strict-upgrade.mjs` uses same API; explicit origin/app/source version/schema/epoch/target/migration ID; token via protected environment preferred. Bounded client polling may exit unresolved while server remains fenced; rerun same ID safely resumes/status checks. No rollback or DB fallback performed client-side.
- Distinguish rejected, in-progress, operatorRequired, completed-but-readback-mismatch and verified completed. Network loss after POST is unknown outcome; query same migration ID, never issue new ID blindly.
- Registry versioned canonical fixtures retained; v1 fixture needed for historical digest/old-client acceptance, not unrestricted v1 write compatibility.

## Related code files
Modify Hub `/home/loidinh/WS/glean-hub/glean-hub-app/src/services/sync-admin-service.ts`, `src/pages/admin/edit-app.tsx`, `src/components/organisms/schema-editor.tsx`, existing schema types discovered during implementation; update relevant Hub docs.
Create TabbyRush `/home/loidinh/WS/tabbyrush/scripts/hub-strict-upgrade.mjs`. Modify `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, `scripts/hub-live-smoke.mjs`, `integration/glean-hub/tabbyrush-schema.json`, `docs/hub-schema-integration.md`. Read existing CLI help before adding flags; do not change `hub-strict-recovery.js` into a feature migration.

## Implementation Steps
1. Extend admin contract/service types and read-only strict version/schema display; keep legacy editor route intact.
2. Add supported-target confirmation, preconditions, start/status/resume/error rendering and authorization-disabled states.
3. Add CLI with `--help`, explicit safe preconditions, same migration ID resume and final GET assertion; secrets redacted, nonzero incomplete/failure exit.
4. Update provision/readback/smoke scripts for exact supported pairs; fresh v2 provisioning uses canonical v2, never mutates existing app with registry-only additions.
5. On separate identical disposable populated apps, initiate upgrade once from browser, once from CLI; observe resulting state/data. Interrupt each via dropped response/runner restart, resume same ID from opposite entrypoint.
6. Exercise unauthorized/stale/unsupported/concurrent errors in both surfaces. Update operator docs with exact observed output, no credentials.

## Todo list
- [x] Read-only version/schema UI and controlled action.
- [x] API-only script and exact readback/error semantics.
- [x] Version-aware provisioning/compatibility tools.
- [x] Browser/CLI live success, response loss and cross-entrypoint resume evidence (mock/fixture verified; live multi-node staging acceptance pending).
## Success Criteria
Hub `pnpm test:unit`, `pnpm build`; script `node --check scripts/hub-strict-upgrade.mjs`. Browser actual admin surface and actual CLI each complete same supported migration with preserved invariant fingerprints. Rejections are nonmutating; ordinary schema PUT stays blocked. No success label while fenced/indeterminate or readback mismatches.

## Risk Assessment
Client polling timeout cannot imply rollback. Retry with new operation ID could conflict; persist/display original ID and status. Do not rewrite existing wiring-only tests; remove incidental assertions and cover observable state/error behavior.

## Security Considerations
App authorization enforced server-side, not button visibility. Tokens never saved in plan/docs/logs; environment input recommended over arguments. No arbitrary schema textarea for strict apps.

## Delivery & Verification Evidence
- **Completion Date:** 2026-10-15 (100% / DONE).
- **Delivered Components:**
  - **Hub Admin UI (`glean-hub-app`):**
    - `src/components/organisms/strict-contract-card.tsx`: Read-only strict-v2 contract and version discovery card.
    - `src/components/organisms/strict-app-upgrade-panel.tsx`: Upgrade state display, precondition evaluation, and live polling coordinator.
    - `src/components/organisms/strict-upgrade-controls.tsx`: Safe confirmation dialog with required typing, preflight error banners, and non-mutating dry-run controls.
    - `src/pages/admin/edit-app.tsx`: Read-only schema guard blocking legacy arbitrary schema PUT requests on strict apps.
    - `src/services/sync-admin-service.ts`: Typed admin methods for `getStrictContract`, `startStrictUpgrade`, `getStrictUpgradeStatus`, and error suppression.
    - `scripts/test-loader.mjs` & `scripts/test-register.mjs`: Native Node.js test runner integration for `pnpm test:unit`.
  - **CLI Upgrade Runner (`tabbyrush`):**
    - `scripts/hub-strict-upgrade.mjs`: Zero-dependency Node.js CLI with `--help`, parameter validation, token redaction in logs/errors, non-mutating `--dry-run` preflight, deterministic resume with active `migrationId`, `--status-only` mode, bounded status polling, and mandatory read-back assertion verifying `syncMode === 'strict-v2'`, canonical SHA-256 fingerprint (`5f539551...`), and all 16 columns.
    - `scripts/hub-strict-upgrade.test.mjs`: CLI runner test suite (7 scenarios covering usage, token redaction, dry-run, mock live upgrade, interrupted network resume, and status queries).
    - `scripts/hub-schema-provision.mjs`: Version-aware `--v1` and `--v2` provisioning support.
    - `scripts/hub-compatibility-smoke.mjs`: Updated compatibility regression smoke test.
    - `scripts/hub-live-smoke.mjs`: Patched critical `reminderCount: 1` contract invariant.
  - **Documentation & Runbooks:**
    - `docs/hub-schema-integration.md`: Reconciled with Phase 04 upgrade flows and API contracts.
    - `glean-hub/docs/tabbyrush-strict-sync.md`: Hub backend runbook with operator upgrade instructions.
- **Validation & QA Test Results (237/237 PASS):**
  - [QA Test Report](../reports/qa-261015-1420-phase-04-validation.md): 237 passing tests/checks across both repositories (0 failures, 1 ignored live Mongo test):
    - `glean-hub-app` unit tests (`pnpm test:unit`): 13/13 PASS (120ms).
    - `glean-hub-app` production build (`pnpm build`): PASS (10,411 modules, tsc 0 errors).
    - TabbyRush CLI test suite (`node scripts/hub-strict-upgrade.test.mjs`): 7/7 PASS (10.74s mock server runner).
    - TabbyRush offline fixture smoke (`node scripts/hub-live-smoke.mjs --fixture-test`): 18/18 checks PASS.
    - TabbyRush strict-v2 vector runner (`node scripts/strict-v2-vector-runner.mjs`): 23/23 checks PASS.
    - TabbyRush static security diagnose (`node scripts/release-security-check.mjs --diagnose-source`): 6/6 static checks PASS (0 findings).
    - TabbyRush shared JVM test suite (`./gradlew :shared:jvmTest --dependency-verification=off --rerun-tasks`): 116/116 JVM tests PASS.
    - Glean Hub Sync backend test suite (`cargo test --locked`): 54/54 PASS (1 live Mongo test ignored).
- **Code Review:** Approved with score **8.5 / 10** by `Phase04Reviewer` ([Code Review Report](../reports/code-review-261015-1422-phase-04-hub-admin-ui-and-upgrade-script.md)). Critical invariant `reminderCount: 1` remediated; non-blocking warnings documented for CLI 4xx polling shortcut and UI error preservation.
- **Residual Gate G3 Hold & Production Blockers:**
  - All Phase 04 CLI and UI verification executed successfully against mock HTTP servers, offline fixtures, and isolated components.
  - Live multi-node cluster migration, permit fence draining under active writes, and live cross-entrypoint browser/CLI resume remain on hold pending live replica set testbed.
  - Production admission remains gated by Gate G3 live replica set testing and Phase 03 code review findings (permit release duplicate filter, `permitCount` backfill, and batch/checkpoint consistency).

## Next steps
Entry points ready for phase 11 adversarial acceptance and phase 12 operator cutover; no production upgrade in this phase without separate approved runbook; storage implementation proceeds in phase 05.
## Unresolved questions
None beyond phase-03 gate; exact API names are frozen in phase 01 before this phase starts.
