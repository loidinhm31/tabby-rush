# Phase 03 — Hub Compatibility Evidence Summary

## Deliverables Summary
Phase 03 establishes the backend strict synchronization server, legacy isolation fences, admission validators, and operator tooling across 26 registry paths:

### Glean Hub Sync Plugin Artifacts
- `src/models/app.rs`: Tolerant `sync_mode` and `strict_epoch` fields on `AppSchema`; legacy constructor preserved; `new_strict` constructor added.
- `src/models/api.rs`: `RegisterStrictAppRequest` and `RegisterStrictAppResponse` DTOs; `GetSchemaResponse` echoes mode and epoch.
- `src/models/strong_sync.rs`: Strict DTOs, wire contracts, and canonical SHA-256 digest computation matching Phase 02 Kotlin vectors.
- `src/strong_sync_error.rs`: Strict-local error enum emitting structured JSON `{ error, code }` with HTTP status codes 400, 401, 404, 409, 426, 503.
- `src/middleware/sync_mode_guard.rs`: Route mode guard rejecting legacy routes for strict apps with HTTP 426; strong route scope coherence guard.
- `src/services/app_service.rs`: Strict schema validation, app ID validation, collection naming, epoch generation, admission checks, and publication-last registration.
- `src/services/strong_sync_service.rs`: Single-document atomic CAS mutations, durable `confirmationNonce` replay writes, and stable rowId pull scans.
- `src/routes/apps.rs`: Admin `POST /api/v1/strict-apps` handler and schema read-back.
- `src/routes/strong_sync.rs`: Mounted `POST /api/v1/sync/:app_id/strong/{bootstrap,push,pull}` routes.
- `src/bin/strong_sync_admission.rs`: Standalone operator preflight CLI invoking identical Rust admission validator.

### TabbyRush Schema & Provisioning Scripts
- `integration/glean-hub/tabbyrush-schema.json`: Canonical strict schema payload.
- `scripts/hub-schema-provision.mjs`: Node provisioning script with mandatory read-back verification.
- `scripts/hub-compatibility-smoke.mjs`: Smoke test script exercising mounted endpoints and isolation guards.

### Documentation
- `docs/hub-schema-integration.md`: Integration guide and lifecycle runbook.
- `docs/hub-compatibility-acceptance.md`: Architectural acceptance criteria and invariants.
- `docs/evidence/hub-compatibility-summary.md`: This implementation summary.
- `glean-hub/CLAUDE.md`: Updated plugin documentation in sibling repository.
- `glean-hub/CHANGELOG.md`: Strict-v1 extension changelog in sibling repository.
- `glean-hub/docs/tabbyrush-strict-sync.md`: Comprehensive operator protocol documentation in sibling repository.

## Runtime Environment & Live Gate Status
- **Static & Unit Verification**: All 36 unit and route tests pass deterministically in the Rust crate (`glean-hub-sync`: 36 passed, 0 failed, 1 ignored live test); Node.js provisioning and smoke scripts pass syntax verification.
- **Live MongoDB Prerequisites**: Full live database acceptance requires a provisioned MongoDB cluster (standalone with `{ w: 1, j: true }` or replica set with `{ w: "majority", j: true }`) reachable via `MONGO_TEST_URI`. In environments without a running MongoDB daemon, the live Mongo acceptance gate (`test_strong_sync_live_mongo_acceptance_gate`) is explicitly marked and hard-fails closed if attempted without the required connection string.
- **Deferred Acceptance Gates**: Mounted live router tests, actual previous-binary rollback tests, and unchanged legacy client integration tests remain future deployment acceptance gates (unexecuted during Phase 03; deferred to Phase 07 device sync and Phase 08 release operations).
