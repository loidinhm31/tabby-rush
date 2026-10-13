# Phase 08 — Cloud deployment, security and signed Android release

## Context links
- [Parent plan](plan.md)
- [Architecture](architecture.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md)
- [Android local gate](phase-07-android-integration.md) · [Authoritative decisions](reports/design-decisions.md)
- [D1 export/import](https://developers.cloudflare.com/d1/best-practices/import-export-data/) · [Time Travel](https://developers.cloudflare.com/d1/reference/time-travel/) · [Worker rollback](https://developers.cloudflare.com/workers/versions-and-deployments/rollbacks/)
- [D1 limits](https://developers.cloudflare.com/d1/platform/limits/) · [Pricing](https://developers.cloudflare.com/d1/platform/pricing/) · [Android signing](https://developer.android.com/studio/publish/app-signing)

## Parallelization Info
Group E after complete07. Requires authorized Cloudflare account/scoped control-plane credentials and Android signing custody.04 reopens its config for real IDs;07 owns signing build wiring;02 owns verification metadata. No one edits those files from08. Release gate precedes09; general docs updated through08 after09/10 without ownership transfer.

## Overview
- Date: 2026-10-13
- Description: Real HTTPS Worker/D1, secure recovery operations and installed signed Android release.
- Priority: P2
- Implementation status: pending
- Review status: pending
- Effort: 6h

## Key Insights
Local D1 is not deployed D1; Worker rollback never restores DB schema/data. Token auth is private shared-dataset access, not per-user accounts. Release must reject debug/local HTTP; deployed HTTPS provides release-positive test. No speculative local TLS proxy/trust bypass needed.

## Requirements
Remote migrations/secret provisioning/rotation and recovery runbook; explicit operation permissions; live local/deployed auth/CRUD/conflicts/pagination/security evidence; actual signed release HTTPS UI plus local-HTTP rejection; sanitized release screenshots. README/architecture-linked essential docs ship now while Linux/Windows honestly pending.

## Architecture
One explicit production Wrangler configuration owned04 with account_id/database_id and DB binding. No separate anonymous write API, client DB credentials or CORS-as-auth. Deployment uses separate Cloudflare control-plane token, app uses APP_SESSION_TOKEN only. Scripts prompt secrets into memory, accept origin/paths as nonsecret CLI inputs, suppress sensitive output and disable redirects. Remote operations have an account/name/ID confirmation gate; no unspecified environment.

## Related code files
Future create only, no existing application files modified/deleted:
- `scripts/api-live-smoke.mjs`
- `scripts/release-security-check.mjs`
- `docs/deployment-guide.md`
- `docs/security-and-recovery.md`
- `docs/release-acceptance.md`
- `docs/project-overview-pdr.md`
- `docs/codebase-summary.md`
- `docs/development-roadmap.md`
- `docs/project-changelog.md`
- `README.md`
- `docs/evidence/android-release-empty.png`
- `docs/evidence/android-release-populated.png`
- `docs/evidence/android-release-completed.png`
- `docs/evidence/android-release-editor.png`
- `docs/evidence/android-release-error.png`

## File Ownership
Only phase08 exact registry rows above. Production config/Worker lock04; SQL03; Android signing/manifests/build/lock07; common artifacts original owners. Backups, credentials, signed binaries, signing keys and temporary recovery state are private machine-local/generated outside tracked files. No ownership-by-integration.

## Implementation Steps
1. Recheck actual D1 pricing/plan/quotas/retention before provisioning; no promise free. Select account and production naming doggo-api/doggo-db with user. Prefer expiring API token scoped to selected account: Account Workers Scripts Edit for deploy/secrets/rollback, Account D1 Edit for create/migrate/import/export/restore. workers.dev subdomain setup may require Account Workers Subdomain Edit; custom domain/route, if explicitly chosen, requires specific Zone Workers Routes Edit/Zone Read. No zones/global account access by default. Existing subdomain plus workers.dev keeps scope smallest. Document actual endpoint authorization/scope grants including export; do not assume a D1 Read-only token permits export/restore or use Global API Key. Grant only operation-required capabilities; remove provisioning permissions from later routine credentials where possible.
2. Use pinned Wrangler from worker directory: protected interactive `read -rsp 'Cloudflare deploy token: ' CLOUDFLARE_API_TOKEN; export CLOUDFLARE_API_TOKEN` (no tracing/history value); `npx wrangler whoami`; `npx wrangler d1 list`; if authorized/not existing, `npx wrangler d1 create doggo-db`.04 owner sets real IDs, compatibility_date and selected public HTTPS workers.dev route, reviews config and reruns impacted permanent backend tests. IDs are not secrets. Verify current `npx wrangler --help`/command help against pinned version, not unpinned npx download.
3. Before every remote migration: verify account/DB IDs, `npx wrangler d1 migrations list doggo-db --remote`, `npx wrangler d1 info doggo-db`, `npx wrangler d1 time-travel info doggo-db`. For existing data, export first with private `umask 077`, create user-only backup directory outside repo, select unique `$BACKUP_FILE`; `npx wrangler d1 export doggo-db --remote --output "$BACKUP_FILE"`; enforce0600/check nonempty/hash and save bookmark/version outside public docs. Export blocks other DB queries: schedule brief maintenance and warn app users. Empty newly created DB still records pre-migration bookmark/schema state. Then interactive `npx wrangler d1 migrations apply doggo-db --remote`; inspect resulting table/index/version constraints without printing todo titles.
4. Provision separate production 32-byte token through trusted generator/vault (64 lowercase hex, not Cloudflare token/dev token). Deploy actual fail-closed Worker with `npx wrangler deploy`; before secret exists verify503 without DB access, never create a fake Worker stub. `npx wrangler secret put APP_SESSION_TOKEN` prompts securely; verify correct Worker/account first. Confirm active deployment/secret operation and HTTPS origin, run valid app auth afterwards. Store token in user-controlled secure vault if approved for server custody, not app persistence/source/sample. Rotation: generate new token, secret put, confirm old token401/new token success from live smoke and app, inform all holders re-enter; emergency revocation secret delete ->503 fail closed until reprovision. No committed secret or production .dev.vars.
5. Implement permanent api-live-smoke: explicit allowed local or deployed origin, masked interactive token prompt, no redirects, bounded requests and failure exit. Create unique UUIDv4 sanitized test records, preserve IDs for ambiguous outcome cleanup, verify duplicate409 no overwrite, list/filter/count/page, rename/complete/reopen, two same-version PATCHs one200/one409, stale delete409, lookup404 and direct delete behavior. Test missing/wrong auth401, malformed bodies/Unicode/cursors/status/no-store. Cleanup only IDs created by this run, version-conditioned, never bulk-delete real dataset. No tokens/titles/request headers in output. Commands from repo: `node scripts/api-live-smoke.mjs --origin http://127.0.0.1:8787 --debug-local`; `node scripts/api-live-smoke.mjs --origin "$DOGGO_HTTPS_ORIGIN"`. Local and deployed are separate gates.
6. Implement release-security-check for APK/archive manifests/resources and real API boundaries: no embedded known app/control-plane token, debug cleartext/trust config, debuggable flag, unexpected exported components or secret-valued config; use selected SDK apkanalyzer/apksigner, ZIP-safe inspection, protected token prompts. Live auth-before-DB/missing-secret tests use isolated local Worker/test harness; never unset production secret as a test without explicit maintenance approval. Redirect rejection/client one-dispatch behavior proven07 and release endpoint validation tested here. Script evidence complements runtime, not a proof all secrets cannot exist. `node scripts/release-security-check.mjs --apk androidApp/build/outputs/apk/release/androidApp-release.apk --origin "$DOGGO_HTTPS_ORIGIN"`.
7. Signing: approved private directory outside repo, `keytool -genkeypair -keystore "$DOGGO_KEYSTORE" -alias doggo-release -keyalg RSA -keysize 3072 -validity 10000` interactive password/identity; or approved existing key, never overwrite. Record custody/recovery and secure offline backup. Provision07 env through protected prompts, no password CLI/Gradle properties. `./gradlew --no-daemon --no-configuration-cache :androidApp:assembleRelease`; SDK `apksigner verify --verbose --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk`. Record public cert digest/version/hash only. Unset password/control-plane token env after child processes finish; no daemon/config-cache secret retention. No app-store workflow implied.
8. Install verified APK on actual device: stop app, consciously remove prior debug install only after approving loss of sanitized test state/signature switch, then `adb -s "$ANDROID_SERIAL" install androidApp/build/outputs/apk/release/androidApp-release.apk`; launch dev.doggo.android/.MainActivity. Connect deployed HTTPS/token and repeat actual CRUD/filter/page/conflict/reconcile/network/unauthorized/lifecycle/accessibility scenarios from07, with sanitized dedicated records. Enter local HTTP alias endpoint -> rejected before token/request; arbitrary LAN HTTP also rejected. Default TLS validation remains intact, no user CA/debug trust override. This is signed release against deployed HTTPS plus release rejection of local HTTP, not a claim of release accepting local cleartext. Capture actual release empty/populated/completed/editor/error states and annotate device/commit/signature/deployment.
9. Recovery: export/import rehearsal to a new private local persistence directory `$RECOVERY_DIR`, `npx wrangler d1 execute doggo-db --local --persist-to "$RECOVERY_DIR" --file "$BACKUP_FILE"`; verify schema, migrations ledger and aggregate sanitized counts/invariants without dumping titles. Do not import full dump atop existing production tables or reapply0001 over restored ledger. Record rehearsal result and secure backup retention/deletion. Worker incident: inspect `npx wrangler deployments list`, `npx wrangler versions list`, validate chosen version compatible with current DB and current-secret revocation policy, then authorized `npx wrangler rollback "$KNOWN_GOOD_VERSION"`; verify active version/auth/smoke. Never assume code rollback also undoes secret rotation or DB migration.
10. Destructive D1 recovery is incident-only with explicit operator authorization, write quiescence/client warning/current export/current bookmark and known data-loss window: `npx wrangler d1 time-travel restore doggo-db --bookmark "$APPROVED_BOOKMARK"` with confirmation. Inspect restored schema/ledger; deploy compatible Worker through04, reprovision current secret if needed, rerun API/app gate. Record undo bookmark and exact retention for plan (not universal30d); account D1 Edit required. Prefer forward corrective migration through03 for schema faults, registry amendment first. First deployment with no older version has no code rollback target: fail closed/repair and redeploy actual code.
11. Write deployment/security/recovery/release docs and essential README/PDR/summary/roadmap/changelog describing actual architecture, commands/permissions, private shared dataset/session-only tradeoffs, online limitations and verified Android release. Link02 architecture/API and06 design/font docs; state Linux09/Windows10 pending. Reopen08 after each later acceptance to update only its docs. Architecture drift returns02; no credentials/private backups in evidence.

## Todo list
- [ ] Provision real remote binding/migrations/secret with scoped permissions and recovery preparation.
- [ ] Pass local/deployed API security checks and real signed Android release gate.
- [ ] Record recovery rehearsal/custody and essential docs with honest platform status.

## Success Criteria
Remote schema matches frozen contracts, app auth scoped/separate and rotation revokes old token. Permanent live smoke passes local and deployed origins; no overwrite/lost version update or anonymous write. Signed release signature/manifest/archive inspection and actual HTTPS UI pass; localHTTP rejected before network. Export/recovery rehearsal and version/DB rollback limitations documented; no destructive production restore performed merely for acceptance. Essential docs shipped; Linux/Windows not prematurely complete.

## Conflict Prevention
Reopen04 for all Wrangler/deployed config,03 for migration correction,07 for signing/build/manifest,02/05/06 for their defects. Gate reruns after owners repair; no unowned secrets/config/docs/lockfiles. Recovery files remain outside tracked tree.

## Risk Assessment
Account/billing/export permissions, D1 downtime/retention, lost signing key and rollback/schema mismatch can block release. Token rotation affects all holders. First deployment has no earlier rollback. Do not weaken TLS/auth or fake credentials to unblock; gate stays pending.

## Security Considerations
No Cloudflare credentials in app, source, APK or screenshots. App token memory-only; administrative env transient/protected. Secrets/backup SQL/signed export URLs private (signed URLs are capabilities). Export0600/dir0700 encrypted custody; no broad global key. Restore loses intervening writes; explicit authorization and record required.

## Next steps
Proceed09 only after deployed API and signed Android gate.08 owner updates general docs after09 and10 terminal acceptance; Windows remains required, not erased if workstation unavailable.

## Unresolved questions
Cloudflare account/plan/permissions, signing-key custody and secure server-token provisioning access required at implementation. Private/session-only, online-first/light defaults must already be approved02; public/multi-user means redesign before deploy.
