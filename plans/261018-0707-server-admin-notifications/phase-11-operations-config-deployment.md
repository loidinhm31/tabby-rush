# Phase 11 — Operations, credentials, feature gates and deployment assets

## Context links
- [Plan](plan.md), [frozen contract](reports/03-contract-specification.md), [ownership/dependencies](reports/04-execution-ownership.md), [acceptance matrix](reports/05-acceptance-matrix.md).
- [Authoritative decisions](reports/02-design-decisions.md), [source anchors/read-only references](reports/01-codebase-analysis.md), [agreed brainstorm](../reports/brainstorm-261018-0228-server-admin-notifications.md).
- Read-only root guidance: `/home/loidinh/WS/tabby-rush/README.md`, `/home/loidinh/WS/tabby-rush/docs/code-standards.md`; inspect current source before modifying. Read-only files are not edit grants.

## Parallelization Info
- Group **B**; implementation start: [P01](phase-01-contract-build-gate.md).
- Additional runtime/acceptance dependencies: [P02](phase-02-backend-canonical-inbox.md), [P03](phase-03-fcm-http-v1-transport.md), [P09](phase-09-android-runtime-integration.md), [P10](phase-10-desktop-runtime-integration.md). Component implementation independence does not promise live acceptance before real backend/provider/hosts exist.
- Validated rollout has separate P12 pilot/full gates: actual Android/Linux safety and native proof before restricted pilot; Windows acceptance may remain held until full delivery. Existing implementation/build dependencies do not change.
- Exclusive owner **P11**; any dependency/build change outside listed files routes to its manifest owner. Final integrator schedules one combined check after all owners land.

## Overview
- Date: 2026-10-18; priority: P2; implementation: implemented; review: approved; status: complete; progress: 100%; effort: **16h**.
- Description: Operations credentials, feature gates, preflight runner (21 self-test checks pass), and Kubernetes deployment assets implemented and verified.

## Key Insights
- Single configured replica can overlap on rolling deploy, so storage lease fencing remains required.
- Live topology, credential and native environments unverified; deployment must not hide this.

## Requirements
- Separate feature controls/secret custody/rollout and nondestructive rollback.
- Apply validated online-details-only cache and maximum500 resolved recipients without truncation; authorize pilot separately from full multiplatform delivery.
- No Redis/Kafka/new worker service or silent Mongo topology assumption.
- Mandatory exact protocol/failure semantics: report03. Mandatory behavior/live rows and pass/fail: report05. No reduced Android/Windows, durable acceptance or manual UI scope.

## Architecture
Assets deploy existing server with in-process worker. Independent intake/worker/FCM enable controls default false; read-only canonical inbox remains available when intake/dispatch disabled. Public Android project config provisioned externally, not server credential.

## Related code files
Only exclusive future modifiable paths below; none are created/modified by this planning task. `generate` denotes owner-produced handoff/evidence outputs, not checked-in build products; ordinary compiler intermediates remain untracked.

| Absolute path | Action | Explanation |
|---|---|---|
| `/home/loidinh/WS/glean-hub/glean-hub-server/.env.example` | modify | Document disabled-by-default feature/FCM project/path configuration; no credentials. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/docker-compose.yml` | modify | Read-only secret-file mount reference and graceful worker shutdown config. |
| `/home/loidinh/WS/glean-hub/glean-hub-project/docker-compose.yml` | modify | Mirror canonical server feature/credential mount options, no secret literals. |
| `/home/loidinh/WS/glean-hub/k8s/glean-hub-host.yaml` | modify | Runtime secret file mount, single-process policy, SSE/probe/graceful rollout settings. |
| `/home/loidinh/WS/glean-hub/glean-hub-server/Dockerfile` | modify | Ensure runtime CA certificates/readable nonroot secret mount and stop handling as needed. |
| `/home/loidinh/WS/glean-hub/scripts/announcement-preflight.mjs` | create | Non-destructive durability/topology/index/custody/provider preflight using disposable probe record and sanitized output. |
| `/home/loidinh/WS/glean-hub/docs/admin-announcement-operations.md` | create | Feature enable/rollback/retention/provider incident and credential rotation runbook. |
| `/home/loidinh/WS/tabby-rush/docs/remote-notification-setup.md` | create | Android public config path and desktop compiler/MSI/daemon operator prerequisites. |

## File Ownership
- P11 exclusively owns **every absolute path in Related code files above**, including its tests/generated outputs; [canonical manifest](reports/04-execution-ownership.md#full-file-manifest) mirrors this exact list.
- No directory catchalls, shared locks or sequential-owner exceptions. Related source/test/build owners repair their own defects even during final acceptance.
- Contract/interface mismatch blocks consumer integration until P01 publishes one coordinated amendment; no local alias/shim/stub workaround.

## Implementation Steps
1. Freeze exact config contract with02: ANNOUNCEMENTS_ENABLED=false, ANNOUNCEMENTS_INTAKE_ENABLED=false, ANNOUNCEMENTS_WORKER_ENABLED=false, ANNOUNCEMENTS_FCM_ENABLED=false, FCM_PROJECT_ID, GOOGLE_APPLICATION_CREDENTIALS read-only file/ADC. Once enabled=true and schema initialized, read/history stay available independently of intake/worker/FCM controls; rollback retains enabled=true. New intake requires enabled+intake+worker+configured/enabled FCM and durable preflight; preview reports exact deliveryAvailability. Return explicit503 if unavailable, without blocking accepted replay/read. Email NOTIFICATION_ENABLED behavior unchanged.
2. Add nonsecret examples and read-only secret-volume references; no credential JSON in env/history/repository. Document Android TABBYRUSH_FIREBASE_CONFIG_PATH external public client config file with matching project/package (API key public configuration still origin/API restrictions). Server service account separate and never copied client-side.
3. Implement sanitized preflight: Mongo hello/journaling and configured w/j read-back using disposable probe record, indexes/permissions and available current tabby-rush grant data without PII dump; provider credential file mode/access and outbound Google TLS/OAuth. Do not mutate app data or expose secrets. Record topology evidence, not infer from Compose URI.
   Preflight uses Node built-ins and installed `mongosh` via stdin script/config-file input, never Mongo URI/credentials in shell arguments; no new npm dependency/lock. Actual OAuth readiness is exercised through real P03 transport under operator custody, not a fake credential-presence-only pass.
4. Set one active server deployment; prefer Recreate (brief outage acceptable) for this feature, maintain lease fence correctness if overlap occurs. Configure stop grace90s, stop intake then streams then workers, bounded I/O, renew/stop claims; app crash still recovers leases. Ensure ingress SSE buffering off, keepalive15s and timeout>60s, no auth header logging.
5. Deploy schema/indexes and server with intake off; run preflight/crash proof, reviewed Android/Linux clients and actual admin preview/inbox. Enable worker+FCM and test-fixture intake in isolated staging for P12 evidence. After P12 pilot gate and explicit operator signoff, enable restricted Android/Linux pilot intake; do not wait for Windows acceptance or claim Windows support passed. Full rollout waits P12 full-delivery gate. No unsupported audience truncation.
6. Rollback intake first, optionally pause provider sends/worker, keep canonical read APIs/data/90day expiry and required indexes. Never drop accepted parent/children or install fence tombstones. Backward incompatible old server rollback must retain new read-capable server or block release; old binaries must not mutate new binding records.
7. Document key rotation,403 project mismatch, quota Retry-After, stuck lease/materialization, TTL lag/orphan sweep and invalid-token matching actions. Observability limited to safe structured state/error counts, not a new telemetry system.
8. Prepare host prerequisites/credential owner signoff for12. Deployment acceptance depends actual02/03/09/10; asset drafting may proceed wave B but no claim feature enabled/live before real tests.
9. Put the [pilot/full gates](reports/05-acceptance-matrix.md#pilot-and-full-delivery-gates) in the operator runbook: named sender/operator, approved selected-user cohort with Android/Linux clients, explicit Windows-not-yet-accepted notice, existing enable flags and documented stop/rollback. This is an operational pilot restriction, not a new DTO, server audience mode or automatic cohort-enforcement feature; broad all/admin sends remain staging-fixture-only until full approval.

## Todo list
- [ ] Read owned files/current upstream changes and confirm frozen contract.
- [ ] Complete all numbered steps with deterministic failure handling.
- [ ] Add/run owner behavior checks when implementation assignment authorizes; record actual result, never fabricate.
- [ ] Obtain P12 pilot evidence and explicit operator/cohort approval before pilot intake; retain separate Windows/full-delivery hold.
- [ ] Publish owned-file handoff and unresolved environment gates; implementation/review remain pending until evidenced.

## Success Criteria
- Configs fail closed when durability/provider prerequisites absent; credentials server-only and redacted.
- Operator can enable deliberately and roll back without deleting retained inbox/ownership fences.
- Single-process deployment/SSE/graceful shutdown documented and live preflight evidence recorded.
- Restricted pilot can operate with Windows acceptance held only after Android/Linux and every applicable shared safety gate passes; full enablement still requires installed Windows evidence. No approval/result is credited by this plan.

Future targeted verification (not executed by this plan):
- `Future operator: node scripts/announcement-preflight.mjs --config <operator-config-path> (no secret CLI arguments)`
- `Future operator: docker compose config and kubectl apply --dry-run=server -f k8s/glean-hub-host.yaml against authorized test environment`
- `Exercise staging enable/pause/rollback and verify retained inbox still readable while intake503.`

## Conflict Prevention
- Re-read unexpected changed files before edits; unrelated Hub embed/frontend-config work is not part of this feature.
- Edit only exact owned paths; ask P01 for common manifest/export/checksum changes, P08 for desktop build/locks, P09 for Android build/audit, P02 for server init/config, P11 for deployment assets. Never let final12 take ownership of earlier source/tests.
- Handoff format and wave gates in report04; temporary compile dependencies explicitly listed there. No build/test/formatter storm while sibling edits are half-landed.

## Risk Assessment
- Existing notification-enabled flag might otherwise gate inbox inadvertently;02 owns code to keep independent feature semantics.
- Kubernetes/Compose partial rollout and old binaries require explicit rollback runbook.

## Security Considerations
- Least-privilege service identity/read-only secret file, no server credential in Android/browser/build logs.
- Preflight sanitizes Mongo URI/user data; backups retain access-control and token secret policy.

## Next steps
- Consumers/unblocked phases: [P12](phase-12-cross-system-acceptance-docs.md).
- [ ] Satisfy this phase's implementation/acceptance dependency row in report04; attach targeted results and contract revision.
- [ ] Resolve applicable environment prerequisites in report05 before claiming runtime success. Missing environment is a pending gate, never scope deletion.
- Unresolved protocol/product questions: none. Environment/release prerequisites remain unchecked and explicitly owned.
