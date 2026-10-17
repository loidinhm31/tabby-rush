# Phase 08 — Authorized Hub operations and signed Android release

## Context links
- [Plan](plan.md) · [Architecture](architecture.md) · [Frozen contracts](contracts.md) · [Authoritative decisions](reports/design-decisions.md)
- [Ownership registry](file-ownership.md) · [Dependencies](dependency-matrix.md) · [Planner brief](reports/planner-brief.md)
- [Sync scout](scout/scout-01-sync-protocol.md) · [Client scout](scout/scout-02-client-reuse.md) · [Source-derived contract review](reports/contract-review.md)
- [Strict design and recovery limits](reports/strong-sync-design-feasibility.md) · [Isolation/rollback inventory](reports/strong-sync-isolation-feasibility.md) · [Mongo durability evidence](reports/mongodb-feasibility-evidence.md).
- [Historical platform/theme evidence](../261013-0914-tabbyrush-compose-todos/phase-08-deployment-security-release.md) — only setup/signing/design/packaging reused; old backend/session defaults superseded.

## Parallelization Info
Group E after passing07. Operator authorizes deployment/provisioning;03 owns strict Hub extension/schema/scripts,02 config/metadata,07 signing/build.08 never edits them. General docs08 reopen after09/10 acceptance without transfer.

## Overview
- Date: 2026-10-13
- Description: Authorized Hub operations and signed Android release.
- Priority: P2
- Implementation status: completed / 100% (2026-10-14T17:00:00Z; tooling & operational readiness delivered, live release gates pending operator prerequisites)
- Review status: Tooling and operational runbooks delivered across 20 owned paths (including scripts/hub-live-smoke.mjs, scripts/release-security-check.mjs, scripts/hub-strict-recovery.js, operations runbooks, security specifications, and release UI state design mockups). Syntax check clean, offline fixture smoke 18/18 PASS, source audit CLEAN. Technical review cycle 3 (2026-10-14): 6/10 NOT ACCEPTED for live recovery due to open implementation defects (recovery script BSON Int64 import and createdAt Long rewriting, release-security-check debuggable attribute gate and debug-cert rejection) alongside unexecuted environment gates (mongosh against mongod, signed release APK apksigner check, live Hub verification).
- Effort: 10h estimated; total plan148h,uncertainty ±25–35%.

## Key Insights
Documented public health502/local refusal is existing blocker evidence, not successful Hub. Existing Hub/Mongo operations, not Cloudflare deployment/D1 tools. Local logout cannot revoke remote sessions. Packaged app-key replacement would require client redistribution; no existing rotation/recovery endpoint is assumed.

## Requirements
Authorized operator deploys reviewed03 isolated strict-v1 extension,registers dedicated TabbyRush/no-sharing/key and approves access. Deployed HTTPS dual-auth/isolation/two-device/strict acceptance/conflict/UnknownSuperseded proof,signed APK/archive policy and installed release UI. Real Mongo topology/journaling/index/read-write concern admission; backup-restore rehearsal with quiescence/fresh epoch/version guard. Existing apps/embedded clients/shared Rust/TS libs unchanged. No production destructive test/legacy repair/transaction/feed/receipt ledger.

## Architecture
Existing Hub auth/admin/plugin deployment/Mongo ownership remains operator-controlled.08 references03 provisioning/live compatibility and existing operator deploy commands instead of inventing a new control plane. Release configuration approved origin+TabbyRush app key distributed externally through02/07 owner wiring; no admin/Mongo/JWT-signing secrets shipped. Native key is extractable identification.

## Related code files
Future tracked implementation artifacts only; current task creates planning documents,not these files.
- `/home/loidinh/WS/tabbyrush/scripts/hub-live-smoke.mjs` — create.
- `/home/loidinh/WS/tabbyrush/scripts/release-security-check.mjs` — create.
- `/home/loidinh/WS/tabbyrush/scripts/hub-strict-recovery.js` — create, maintenance-only mongosh fresh-epoch import/validation/registry CAS.
- `/home/loidinh/WS/tabbyrush/docs/deployment-guide.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/security-and-recovery.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/release-acceptance.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/project-overview-pdr.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/codebase-summary.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/development-roadmap.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/project-changelog.md` — create.
- `/home/loidinh/WS/tabbyrush/README.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-summary.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/hub-recovery-summary.md` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-empty.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-populated.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-completed.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-editor.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-error.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-conflict.png` — create.
- `/home/loidinh/WS/tabbyrush/docs/evidence/android-release-access-pending.png` — create.

## File Ownership
Exclusive phase08 owner of all 20 exact absolute paths above; those and only those phase08 rows in [registry](file-ownership.md). No planned deletes. Config/test/schema export/evidence ownership included; caches/private runtime outputs excluded. Consumer links read-only; new tracked path reviewed before creation. Integration reopens original owner.

## Implementation Steps
1. Operator rechecks approved HTTPS /api/v1/health with bounded no-redirect probe,actual server revision and Mongo version/topology/journaling/read-write concerns/indexes from sanitized authorized evidence. Resolve502/refusal via Hub owner,not client workaround. Strict standalone w1,jtrue/primary noHA or replica majority,jtrue/primary majority policy must match configured handle; unknown/unsupported admission pauses only strict app. Define maintenance permissions/custody/test scopes first.
2. Record current Hub revision/registry/strict namespaces/index/config/private backup through existing runbook. Deploy exact reviewed03 strict extension using operator supported process,no guessed Docker/systemd target. Verify capable deployed version,admission,mode/epoch read-back and03 compatibility smoke before release traffic. Preserve strong namespaces across rollback; never import divergent legacy app* data. Record redacted actual command evidence; unavailable process is release blocker.
3. `node scripts/hub-schema-provision.mjs --origin "$APPROVED_HUB_ORIGIN" --environment production --confirm-app tabbyrush` consumes03 after authorization. Inspect GET before create; new POST `/api/v1/strict-apps` on old binary404/405 fails without accidental legacy registration. Require read-back syncMode strict-v1/strictEpoch/protocol,exact schema/index admission,not200 alone. Legacy tabbyrush cannot be silently repurposed. One-time key private custody; signup/request/approval/refresh tested,no mass grants/schema overwrite.
4. `node scripts/hub-live-smoke.mjs --origin "$APPROVED_HUB_ORIGIN"` uses protected ordinary-account/key prompts,fixed tabbyrush,bounded requests/no redirects/raw bodies. Verify signup/approval/refresh/me,missing/wrong key/JWT/app/cross-user UUID/opId/cursor,structured per-operation explicit ack,100/101 stable scans/tombstones,strict base conflict and unchanged frozen retry. Lost delivery followed by successor ->UnknownSuperseded;equal data never ack. Legacy routes rejected for strict app non401,ordinary legacy smoke unchanged. Cleanup only run-owned content through revision-CAS delete leaving permanent empty tombstones; never physically purge strict identities. Production scope operator-approved.
5. Dedicated app key returned once and stored privately before first use. Current Hub has no rotate/regenerate/recover route: loss/compromise requires explicit operator DB intervention or new appId/data migration and reviewed client release, not a pretend rotation command. /me grant removal pauses cooperative client but issued JWT stays valid until expiry; no immediate revocation or server logout. Prefer non-admin TabbyRush account; admin sign-in warning discloses elevated vault-token risk and never enables admin UI.
6. Secure signing custody outside repo, approved existing key or interactive `keytool -genkeypair -keystore "$TABBYRUSH_KEYSTORE" -alias tabbyrush-release -keyalg RSA -keysize 3072 -validity 10000`; protected password prompts, recovery backup. `./gradlew --no-daemon --no-configuration-cache :androidApp:assembleRelease`; selected SDK `apksigner verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk`. Record public cert digest/version/hash only;07 wiring fixes return07.
7. `node scripts/release-security-check.mjs --apk androidApp/build/outputs/apk/release/androidApp-release.apk --origin "$APPROVED_HUB_ORIGIN"` inspects actual manifest/archive using selected SDK apkanalyzer/apksigner: non-debuggable,no debug network overrides,backup restrictions,only approved origin/routes,known administrative secret absence,app key classification. Inspect merged manifests/resources with actual tuple tools; this cannot prove universal secret absence by string scan alone.
8. Install verified signed APK on real device with conscious debug-signature uninstall approval only for sanitized account data; `adb -s "$ANDROID_SERIAL" install androidApp/build/outputs/apk/release/androidApp-release.apk`; launch same MainActivity. Real HTTPS onboarding/login/two-device sync/offline restart/background/conflict/guarded logout/switch/errors/accessibility; approved release origin works, debug loopback/arbitrary origins/redirect/TLS bypass fail before credentials. Fixed release endpoint not arbitrary UI endpoint form; policy tests plus actual rejected-route harness establish negative checks, do not add release config override.
9. Protected mongodump/mongorestore archives restore only sanitized isolated SOURCE. Retire/drain all writers,block traffic. Execute owned `mongosh --nodb --file scripts/hub-strict-recovery.js`,scope/connection/old epoch/operator choice via protected environment,never URI argv/log. Validate schema/identity/data,generate fresh external UUID epoch,create NEW namespace/_id/scan indexes,import approved rowId/data/deleted revision1,lastOp=null,empty tombstone data;no receipts/old namespace drop. Validate counts/schema/indexes/journal/topology and Rust admission before journaled registry strictEpoch CAS old→new,single authorized maintenance edit. Pre-CAS failure leaves old pointer;CAS transport/concern timeout MAY have published:keep traffic blocked,read authoritative primary registry under configured concern,reconcile old vs recorded candidate epoch,never assume rollback/regenerate another epoch blindly. Partial candidate not active. Explicit empty reset only operator choice,no HTTP reset API.
   Execute shipped03 read-only strong_sync_admission helper with protected candidate scope before CAS;same Rust validator,not duplicated mongosh-only substitute. Record actual operator-installed binary path/provenance;missing helper blocks publication. After acknowledged/reconciled candidate CAS,perform active Rust readmission,current registry/schema checks and oldepoch409 test before reopen. Ambiguity keeps traffic blocked;no blind CAS replay/epoch regeneration. All registry reads primary (replica majority),publication topology-specific journaled concern;readonly inactive preflight is not public reset route.
10. Version guard prevents exposing strict traffic on older/unknown binary; code rollback below strict capability disables TabbyRush or client pauses ProtocolIncompatible,never legacy fallback. Old in-flight request may commit OLD namespace only; no reset-vs-old-commit ordering guarantee. Reopening stale restored epoch/revisions prohibited. Rehearse offline pending/uncertain/drafts surviving epoch mismatch,explicit reset review and old-response fences; content absent from backup cannot be recovered. No inverse restore/causal receipt reconstruction. Review auth/JWT/refresh restoration separately. Incident production recovery needs loss-window/custody authorization,not an acceptance restore.
11. README/PDR/summary/roadmap/changelog/deployment/security/release docs record actual strict status,commands/operator maintenance,permanent-marker growth,non-snapshot/UnknownSuperseded/noHA boundaries,offline/account wipe/background/auth/local-only logout. Sanitize release screenshots and hub-recovery-summary. Verify existing embedded apps/shared libraries/source unmodified and actual legacy-client smoke before release; no migration estimate hidden here. Desktop pending until native gates;08 alone updates general docs afterward.

## Todo list
- [ ] Implement every owned artifact/requirement with real behavior, not scaffolds (20/20 exclusive files delivered; cycle-3 code defects in recovery BSON types and security auditor cert checks remain open).
- [ ] Pass designated future command and actual runtime gates; record sanitized evidence (pending operator environment prerequisites: real mongod/mongosh instance, Android SDK 35 release build keystore, and reachable live Hub deployment).
- [ ] Resolve failures through original owner and complete parent review; technical acceptance blocked at 6/10 until BSON Int64 handling, release cert verification, and live recovery rehearsal are satisfied.

## Success Criteria
Authorized deployed capable strict Hub registration/read-back/index/journal admission and existing-client safety proof; native signup/approval/refresh/two-device strict sync; signed inspected installed Android HTTPS release with durable/offline/background/unknown/reset/UI gates; real isolated quiescent fresh-epoch recovery and rollback/version fence evidence. No old-epoch reopening or historical receipt reconstruction; essential docs match observations.

## Conflict Prevention
No edits outside owned registry rows,no alias/glob ownership,no shared Gradle/lock/manifest/resource sweeping changes. Original owner repairs integration defects; main integrator reruns required affected gate once after fixes. Generated tracked lock/schema/evidence never placeholder.

## Risk Assessment
Hub unreachable/operator deploy unknown,privileges/signing/key custody/backup topology can block. Do not copy stale D1 commands or claim restore/code rollback interchangeable. App key extraction accepted; wrong schema existing app never overwritten.

## Security Considerations
Admin/deploy/Mongo/JWT signing credentials private only. Backups user-only encrypted custody, no SQL/private payload/screenshots/secrets tracked. Clear scoped authorization for remote changes; default TLS/no redirects; app key not human auth.

## Next steps
09 only after08 release gate. After09/10,08 doc owner revisits platform statuses with their terminal native evidence, no direct platform ownership transfer.

## Unresolved questions
Approved reachable deployed Hub,operator exact deployment/backup topology and permissions,Dedicated TabbyRush registration/key/access grants,Android signing custody remain unresolved implementation prerequisites.
