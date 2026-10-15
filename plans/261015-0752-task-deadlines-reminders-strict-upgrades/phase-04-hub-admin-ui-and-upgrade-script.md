# Phase 04 — Hub admin UI and shared-operation upgrade script

## Context links
[Parent](plan.md) · [API design](phase-01-freeze-contract-and-transition-design.md) · [Backend dependency](phase-03-hub-versioned-contract-and-migration.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 12h. Owners: Hub admin frontend + Doggo integration tools; integration lead owns operation contract. Depends on phase 03 API/G3.

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
Create Doggo `/home/loidinh/WS/doggo/scripts/hub-strict-upgrade.mjs`. Modify `scripts/hub-schema-provision.mjs`, `scripts/hub-compatibility-smoke.mjs`, `scripts/hub-live-smoke.mjs`, `integration/glean-hub/doggo-schema.json`, `docs/hub-schema-integration.md`. Read existing CLI help before adding flags; do not change `hub-strict-recovery.js` into a feature migration.

## Implementation Steps
1. Extend admin contract/service types and read-only strict version/schema display; keep legacy editor route intact.
2. Add supported-target confirmation, preconditions, start/status/resume/error rendering and authorization-disabled states.
3. Add CLI with `--help`, explicit safe preconditions, same migration ID resume and final GET assertion; secrets redacted, nonzero incomplete/failure exit.
4. Update provision/readback/smoke scripts for exact supported pairs; fresh v2 provisioning uses canonical v2, never mutates existing app with registry-only additions.
5. On separate identical disposable populated apps, initiate upgrade once from browser, once from CLI; observe resulting state/data. Interrupt each via dropped response/runner restart, resume same ID from opposite entrypoint.
6. Exercise unauthorized/stale/unsupported/concurrent errors in both surfaces. Update operator docs with exact observed output, no credentials.

## Todo list
- [ ] Read-only version/schema UI and controlled action.
- [ ] API-only script and exact readback/error semantics.
- [ ] Version-aware provisioning/compatibility tools.
- [ ] Browser/CLI live success, response loss and cross-entrypoint resume evidence.

## Success Criteria
Hub `pnpm test:unit`, `pnpm build`; script `node --check scripts/hub-strict-upgrade.mjs`. Browser actual admin surface and actual CLI each complete same supported migration with preserved invariant fingerprints. Rejections are nonmutating; ordinary schema PUT stays blocked. No success label while fenced/indeterminate or readback mismatches.

## Risk Assessment
Client polling timeout cannot imply rollback. Retry with new operation ID could conflict; persist/display original ID and status. Do not rewrite existing wiring-only tests; remove incidental assertions and cover observable state/error behavior.

## Security Considerations
App authorization enforced server-side, not button visibility. Tokens never saved in plan/docs/logs; environment input recommended over arguments. No arbitrary schema textarea for strict apps.

## Next steps
Entry points ready for phase 11 adversarial acceptance and phase 12 operator cutover; no production upgrade in this phase without separate approved runbook.

## Unresolved questions
None beyond phase-03 gate; exact API names are frozen in phase 01 before this phase starts.
