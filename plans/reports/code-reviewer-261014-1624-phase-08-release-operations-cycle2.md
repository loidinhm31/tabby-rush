# Phase 08 Code Review — Cycle 2

Score: **4/10 — NOT ACCEPTED** (cycle 1: 3/10)

Smoke script now matches frozen contract (real gain). Recovery script still cannot run under its documented invocation and leaks the Mongo URI; security gate keeps absence-as-pass; evidence docs partly stale/overclaimed.

## Critical (MUST FIX)
1. **`hub-strict-recovery.js` fails under documented `mongosh --nodb --file`** (L305 `typeof db !== 'undefined'`). Verified with mongosh 2.13.0: in `--nodb`, merely evaluating `db` throws `[SHAPI-10004] No connected database` → `[FATAL ERROR] Recovery aborted`, exit 1. `connect()` branch never reached; "mongosh --nodb support" remediation claim is false. Fix: test `typeof connect === 'function'` + MONGO_URI first, never touch bare `db` in `--nodb`; if `db` path kept, switch to `CONFIG.dbName`.
2. **Dangling async `main()` under mongosh (fail-open).** Verified: `--file` does not await an un-awaited promise (timer promise never completed, exit 0), and top-level `await` is a SyntaxError in `--file` mode. Once #1 is fixed, a DB-op promise chain is likely abandoned mid-run with exit 0 [INFERENCE for DB ops — no mongod available; timer case observed]. Fix: write the script synchronously (mongosh auto-awaits shell API calls; `cursor.hasNext()/next()` sync, `execFileSync` already sync); no `async`/`await`.
3. **MONGO_URI (with password) printed to log** — L308 `console.log("Connecting via connect('${CONFIG.mongoUri}')")`. Verified with fake URI: password echoed. Violates plan §9 "never URI argv/log". Remove/redact. Also the `connect()` error path may echo host; keep URI out of messages.
4. **`mongosh --file` hint (L313) + `db`-first precedence ignore `MONGO_URI`/`MONGO_DB_NAME`.** If run via plain `mongosh --file`, script uses whatever default `db` is connected (wrong DB risk, maintenance script). Make `--nodb` + `connect(MONGO_URI)` the only path; fail otherwise.
5. **`docs/evidence/hub-recovery-summary.md` still stale vs contract/script** (cycle-1 #5 unfixed there): namespace `sync_records_${appId}_${cleanEpoch}` (contract: `_strict_v1_<len>_<appId>_<epochNoHyphens>`), CAS target `apps.strictEpoch` (script: `_schemas`), `epoch_mismatch` (contract `epochMismatch`), "triggers a staged full scan against the fresh epoch" (contract: client pauses `ResetRequired`, never auto-rebase). Also dry-run log shows "Expected Epoch: (Not provided)" and omits "Source Backup" line.
6. **Overclaimed status in `docs/release-acceptance.md` / `android-release-summary.md` / `project-changelog.md`**: "OPERATIONAL (100% Complete)"; Disaster Recovery row **PASS** from syntax check + `DRY_RUN` (dry-run exits before any logic; live path demonstrably broken per #1–#3); Live Smoke row (L30) "executes full strict-v1 protocol … 15/15" is a stale duplicate of L34 (16/16) and overclaims (no replay/JWT-negative/100-101 scan/two-device/UnknownSuperseded/cursor/opId checks). Tooling is not "complete"; downgrade to "implemented, partially verified offline".
7. **`release-security-check.mjs` still fail-open in APK mode**:
   - `allowBackup` absent (Android default = **true**) prints `PASS allowBackup="false" confirmed` (L235-240). Same wording for cleartext/debuggable absent. Require explicit attribute presence (fail if missing) or print "absent (default X)" and fail where default is unsafe.
   - `--origin` only `startsWith('https://')`; never compared to anything in the APK (plan §7 "only approved origin/routes"). Header #2 "Asserts release endpoint targets approved HTTPS origin" overclaims.
   - No APK secret scan (header #3 says "package/source"; only source mode scans). Plan §7 "known administrative secret absence".
   - Exported audit covers `<activity>` only; services/receivers/providers/`activity-alias` ignored; `MainActivity` regex matches any tag containing it.
   - v2/v3 scheme absence silently not failing; no cert digest output/debug-cert rejection (plan §6 records public cert digest).

## Warnings (SHOULD FIX)
### hub-live-smoke.mjs
- Pull check passes if **any** non-deleted record exists (`r.rowId === testRowId || !r.deleted`); tombstone check passes on **any** deleted record. Fixture proves it: PASS reports `row-active-1`, not the created row. Assert `rowId === testRowId`, revision, data equality, and tombstone for that row; page with cursor/`hasMore`.
- Docblock lists unimplemented checks: frozen-retry `replayed` (only fixture handler exists), missing/invalid **JWT 401**, access-request polling, `/auth/me` grants. Plan §4 checks still missing: wrong JWT/app/cross-user, opId/cursor, 100/101 stable scan, UnknownSuperseded/equal-data, two-device, pagination.
- `effectiveEpoch = serverEpoch || activeEpoch`: bootstrap epoch ≠ admission epoch not failed. Assert equality.
- HTTPS not enforced; default origin `http://127.0.0.1:3000`. Require https (loopback only under `--fixture-test`).
- Legacy-route check passes on 400/403/426 (compat smoke requires 426 + `protocolUnsupported`); probe is unauthenticated so a real Hub may return 401 first. Align with `hub-compatibility-smoke.mjs` (auth’d probe, require 426 + code).
- Fixture ignores request shape (`protocol`, `kind`, per-op `epoch`, `username`) → partly circular again. Validate request bodies against contract in fixture.
- Responses unbounded (no max-bytes). Auto-registers random account on target without explicit authorization flag; `--allow-destructive-cleanup` hardcodes `expectedRevision: 1` and runs delete even when create failed.
- Epoch-mismatch probe sends `operations: []`; real Hub may return 400 before 409.
### hub-strict-recovery.js
- Plain-node live run (no mongosh) prints notice and **exits 0** (verified) — fail-open; should exit 1.
- Source collection read from the same live DB; no guard `source != live strict namespace/_schemas`; plan §9 requires isolated SOURCE.
- `userId || 'usr_recovered'` fabricates ownership for rows missing `userId` → should abort.
- Step 3 labelled "with majority concern" but `createCollection`/`createIndex` carry none. Step 1 label "Verifying writer quiescence" verifies nothing (env flag only).
- No post-import `countDocuments(target)` vs validated count; pre-CAS failure leaves orphan candidate collection (document it).
- Dead dry-run branch in `runRecoveryInDatabase` (main exits first).
### docs
- `security-and-recovery.md` Step 2: `mongorestore --drop --nsInclude="glean_hub.staging_tabbyrush_restore" backup/tabbyrush/` — no `--nsFrom/--nsTo` mapping, `--drop` into live DB, comment "environment credentials" false (mongorestore has no such env). Plan: restore into sanitized isolated SOURCE.
- Step 6/7 omit Rust active readmission + old-epoch-409 test before reopening traffic (script banner says it; doc must).
- `deployment-guide.md`: still no rollback/version-guard/`ProtocolIncompatible`, no key-loss/no-rotation, JWT non-revocation, admin-sign-in warning; `adb install -r` vs debug-signature uninstall approval.
- `README.md`: hardcoded `/home/loidinh/...` JAVA_HOME; features listed with no Phase 07 NOT-ACCEPTED blocker note.
### release-security-check.mjs
- Secret-scan exemption `relPath.includes('test'|'Test'|'mock')` is a substring match on any path segment (`LatestSync.kt`, `Attestation*`, `contest/`). Match test source-set dirs only. Add `ENCRYPTED/PGP PRIVATE KEY`; symlink follow (`statSync`).

## Suggestions
- Share one `push()/pull()` helper in smoke; generate fixture vectors from Rust/Kotlin digest vectors; verify returned `digest` against §4 encoding.
- Parse manifest with XML parser (not regex on `apkanalyzer manifest print`); use `apkanalyzer files list`/`dex strings` to prove origin literal and reject other `https?://`.
- Recovery: print target namespace not epoch for "old"; require `--nodb`; emit "TRAFFIC REMAINS BLOCKED until readmission + old-epoch 409 verified".
- Reconcile plan §3 flags (`--environment production --confirm-app tabbyrush`) with actual `hub-schema-provision.mjs` flags.

## Positive
- Smoke: register `{username,email,password}`, login, refresh, `accessToken`, `GET /api/v1/apps/:id` read-back with `syncMode/strictEpoch/columns`, dual-auth + `X-App-Id`, `protocol/epoch/kind/expectedRevision`, structured `results`, 409 epochMismatch, 426 legacy in fixture; creds via env (no argv secrets); no-redirect handling; 16/16 fixture pass.
- Recovery: exact namespace `_strict_v1_<len>_<appId>_<epochNoHyphens>`, exact scan index, `_id: userId:rowId`, CAS on `_schemas` with `w:majority,j:true`, mandatory `SOURCE_BACKUP_COLLECTION`, helper via `execFileSync` with env (not argv), pre-CAS failure blocks CAS, ambiguous-CAS "traffic stays BLOCKED" guidance, `crypto.randomUUID`.
- Security-check: `execFileSync` (injection fixed), relative-path exemption bug fixed (cycle-1 bypass), PEM regex widened, placeholder default origin removed (APK mode fails w/o origin), fail-closed on missing APK/tools.
- Docs: no credentialed Mongo URI (placeholders only), Rust/`glean-hub-sync` clarified, tooling vs live acceptance split present with Phase 07 blockers listed. File ownership unchanged (20 registered paths; 3 pre-existing docs modified).

## Reviewed files
`scripts/hub-live-smoke.mjs` (711), `scripts/hub-strict-recovery.js` (323), `scripts/release-security-check.mjs` (297), `docs/security-and-recovery.md`, `docs/release-acceptance.md`, `docs/evidence/hub-recovery-summary.md`, `docs/evidence/android-release-summary.md` (grep), `docs/deployment-guide.md` (grep), `README.md` (grep), `docs/project-changelog.md` (grep); cross-read `docs/api-contract.md`, `hub-compatibility-smoke.mjs`, phase-08 plan, `androidApp/src/main/AndroidManifest.xml`.

## Validation commands & results
- `node scripts/hub-live-smoke.mjs --fixture-test` → 16/16 PASS, exit 0 (vacuous pull/tombstone noted).
- `node scripts/release-security-check.mjs --diagnose-source` → CLEAN, exit 0. Release mode without APK → FATAL exit 1 (fail-closed).
- `node --check scripts/hub-strict-recovery.js` OK; `DRY_RUN=true CONFIRM_MAINTENANCE_QUIESCENCE=true node …` → exit 0 (no logic exercised); non-dry plain-node run with env set → notice, **exit 0**.
- `mongosh@2.13.0` (npm, temp dir, removed): `mongosh --nodb --file scripts/hub-strict-recovery.js` → `No connected database`, exit 1. Probe: un-awaited async promise abandoned, exit 0; top-level await SyntaxError. Patched copy bypassing `typeof db` → leaked `mongodb://user:SECRETPW@…` in log.
- Not run: real mongod recovery, `apksigner`/`apkanalyzer` (absent; no Android SDK), Gradle tests (not Phase 08 code).

## Plan updates
`phase-08-android-release-operations.md` Review status line updated (4/10, not accepted). `plan.md` Phase 08 stays `pending 0%` (accurate).

## Unresolved questions
- Real Hub order of auth vs legacy-protocol rejection on `/api/v1/sync/:app/push` (401 vs 426) — needs Rust source/live Hub.
- Does `strong_sync_admission` accept `--app-id/--epoch` flags and env `MONGO_URI/MONGO_DB_NAME`? Not verifiable here (external Hub repo).
- Is `_schemas` + `strictEpoch/recoveryTimestamp/lastRecoveryStatus` the Rust-owned registry shape (index name `scan_user_table_row` accepted by validator)?
- Does DB-op async continuation survive mongosh `--file` exit (needs mongod to confirm #2)?
