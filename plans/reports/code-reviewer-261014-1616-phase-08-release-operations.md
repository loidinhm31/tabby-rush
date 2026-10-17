# Phase 08 Code Review — Authorized Hub Ops & Signed Android Release

Score: **3/10 — NOT ACCEPTED**

## Critical (MUST FIX)
1. **hub-live-smoke.mjs contradicts frozen contract (docs/api-contract.md, hub-schema-provision.mjs, hub-compatibility-smoke.mjs).** Would fail against a real Hub; 15/15 is circular (fixture encodes same wrong contract).
   - Auth: register/login send `{username,password}`; contract = `{username,email,password}` / `{email,password}`. Reads `token`; contract = `accessToken`. No approval polling (`/apps/access-requests`), `/auth/me` result not used.
   - Admission read-back: `GET /api/v1/strict-apps/tabbyrush` (not a contract route) vs `GET /api/v1/apps/:id`. Epoch/protocol/schema not verified.
   - Push: omits `protocol`, `epoch`, per-op `epoch/tableName/digest`; uses `action/baseRevision`+`acks/ack/conflict` vs `kind/expectedRevision` + `results/accepted/revisionConflict`. Pull: `limit` vs `batchSize`/`tableName`/`epoch`/`cursor`. rowIds not UUIDv4.
   - Legacy rejection probes invented route `/sync/legacy/...`, accepts 400/403/404; contract = 426 `protocolUnsupported` on real legacy route.
   - Vacuous passes: pull-found-record and tombstone checks `passed:true` even when record/tombstone absent (L~500, ~545 "tombstone filtering"). Bootstrap negative checks send `{}` w/o dual auth proof.
   - Missing required checks: wrong JWT/app/cross-user, opId/cursor, 100/101 scan, frozen retry, UnknownSuperseded, equal-data, two-device, 409 epochMismatch.
   - HTTPS not enforced (plain `http` allowed; default `http://127.0.0.1:3000`). Secrets via argv (`--api-key`, `--password`; plan requires protected prompts). `--allow-destructive-cleanup` is dead; cleanup always runs w/ hardcoded `baseRevision:1`. Response bodies unbounded.
2. **hub-strict-recovery.js never performs recovery under documented invocation.** `mongosh --nodb` ⇒ `db` undefined ⇒ prints "outside interactive mongosh" and **exits 0**; `MONGO_URI` never used (no `connect()`), so "protected env" is hollow. [INFERENCE: mongosh not installed, not run; logic evident from `typeof db`.] Success exit on no-op = fail-open.
3. **Rust admission preflight is NOT executed** (Step 5 only `console.log`s), CAS proceeds regardless. Plan §9: "missing helper blocks publication". Docs (security-and-recovery §3.1 step 4, hub-recovery-summary §1.6) claim script "automatically executes" it → false claim.
4. **Missing recovery gates:** `SOURCE_BACKUP_COLLECTION` optional ⇒ empty fresh epoch published silently (data wipe); no source≠live-namespace check; no count/index/topology/journal validation pre-CAS; quiescence = env flag only (step labeled "Verifying writer quiescence" verifies nothing); ambiguous CAS (network error) throws generic "aborted" — no "traffic stays BLOCKED/may have published" guidance; success banner says "unblock writer traffic" with no Rust readmission/old-epoch-409 test (plan §9). `createCollection/createIndex` w/o majority concern. `Math.random` UUID fallback for epoch. Dry-run branch in `runRecoveryInMongosh` is dead code (main exits first).
5. **Namespace/contract drift in recovery + docs.** Script/docs use `sync_records_<appId>_<epoch_underscored>`; contract §4 = `_strict_v1_<appIdLen>_<appId>_<epochNoHyphens>`. Rust validator would not admit the script's namespace. Doc says error `epoch_mismatch`; contract `epochMismatch`. Docs say client "triggers staged full scan / clean state reconciliation" after 409 — contradicts contract (client pauses ResetRequired, never auto-rebase; pending/uncertain retained for review).
6. **release-security-check.mjs fail-open/shell-injection/misleading.**
   - `execSync` with interpolated `--apk` path in shell string ⇒ command injection/quoting break. Use `execFileSync`.
   - APK mode checks only apksigner success, debuggable, cleartext. **Not checked in APK mode:** allowBackup, backup rules, exported components, networkSecurityConfig (WARN only), approved-origin presence in APK, signing cert vs debug cert, secret scan of APK. Yet header, deployment-guide §4, changelog, release-acceptance claim all of these ("Debug network security configs stripped", "Only MainActivity exported", "Secret Scan … APK"). `--origin` only checked `startsWith('https://')`, never compared to APK. Default origin `https://hub.example.com` placeholder silently passes. v2/v3 signature absence not failing.
   - Absence-as-pass: `allowBackup` missing (default true) prints PASS "restricts backup"; same for cleartext. "Only MainActivity is exported" printed PASS unconditionally even after FAIL; services/receivers/providers/aliases ignored; `<activity>` regex fragile.
   - **Secret scan bypass (reproduced):** exclusion `fullPath.includes('test'|'mock'|'docs/')` applied to the ABSOLUTE path ⇒ repo under `/tmp/latest-x` hid a planted PEM (diag: "No hardcoded… detected"); same file under `/tmp/cleanroot` flagged. PEM regex misses `BEGIN RSA PRIVATE KEY`/`EC PRIVATE KEY`. Skips all `.md/.jar/.png`; any dir named `*test*` exempt (androidTest, etc.); follows symlinks.
7. **docs/security-and-recovery.md embeds credentialed Mongo URI example `mongodb+srv://admin:secret@…`** and `mongorestore --uri="$MONGO_URI"` argv; plan forbids URI in argv/log and secrets in tracked docs. Also `mongorestore --collection` into live db (not isolated SOURCE).
8. **Overclaimed status.** "Tooling & Harness Preparation: OPERATIONAL (100% Complete)", "PASS", "Live Smoke: executes full strict-v1 protocol", "Disaster Recovery: PASS … validates quiescence … executes registry CAS" in release-acceptance.md, android-release-summary.md, development-roadmap, PDR, changelog ("Completion of Phase 08 tooling"). Evidence is only `node --check`, circular fixture, a no-op dry-run (dry-run exits before any logic), and a prerequisite check. Tooling readiness vs live acceptance IS separated in wording (good), but tooling itself is not ready. Plan file still `pending 0%` — consistent with truth; docs should not say "operational".

## Warnings (SHOULD FIX)
- `hub-recovery-summary.md` dry-run log shows `Expected Epoch: (Not provided)` — evidence of nothing; dry-run needs no mongo.
- 7 PNGs are generated design mockups ("pixel-precise") named `android-release-*` under `docs/evidence`; plan requires sanitized *release* UI captures. Labeled "mockup/baseline" in docs (OK) but file names imply evidence of release build; rename or mark clearly. PDR/changelog "release visual design baseline".
- deployment-guide: Hub host prerequisite "Node.js v20+" wrong (Hub is Rust); read-back path `GET /api/v1/strict-apps/tabbyrush` wrong (`/apps/:id`); hardcoded `/home/loidinh/...` JAVA_HOME in doc and README; `--api-key` via argv in smoke example; no `--environment production --confirm-app tabbyrush` per plan §3 (flags match actual hub-schema-provision: `--origin --token --schema --out`, so plan text vs script diverge — resolve); no rollback/version-guard/ProtocolIncompatible section (plan §10); no admin-sign-in warning, no key-loss/no-rotation statement, no JWT-not-revocable disclosure (plan §5); no Mongo topology/journaling admission section (plan §1); `adb install -r` conflicts with debug-signature uninstall approval.
- README presents Linux/Windows vault, "Fail-Closed Account Lifecycle" etc. as features with no mention of Phase 07 NOT ACCEPTED blockers; README command §1 `--dependency-verification=off` presented as normal. Add blocker note.
- Phase 07 blockers (teardown generation rollback, cancellation swallowing, worker TOCTOU) ARE carried forward explicitly in release-acceptance.md, android-release-summary.md, roadmap; but Remediation checklist omits an explicit "no release until Phase 07 re-review ≥ accepted" gate and omits that sync-worker cancellation/TOCTOU need device test evidence.
- Duplicate smoke-header claims in `hub-live-smoke.mjs` docblock (items 3, 6) list checks not implemented.
- `hub-live-smoke.mjs` auto-generated password predictable pattern, registers real accounts on prod when run without `--username`; require explicit authorization flag.
- DRY: tombstone/ack parsing repeated across checks; one `push()` helper would shrink ~100 lines. 594-line file mixes client, fixture server, suites (docs.maxLoc fine, code-standards modularization suggests split; but ownership limits files — acceptable).
- Wrong-statement: `release-security-check.mjs` header "Verifies APK assets for private keys" absent.

## Suggestions
- Replace the circular fixture with contract-vector fixtures generated from the Rust/Kotlin digest vectors (shared test vectors) so fixture drift is detectable.
- recovery: `Mongo()`/`connect(process.env.MONGO_URI)` inside script, require `--nodb`; spawn admission helper via `child_process`-equivalent (mongosh lacks it → document `passthrough` step with operator attestation env `ADMISSION_ATTESTATION_SHA`) and abort if absent; require positive count equality `source == imported`; emit "TRAFFIC REMAINS BLOCKED until readmission + old-epoch 409 verified"; print namespace not epoch in "Old namespace".
- security-check: parse merged manifest with XML parser; fail on missing attributes; use `apkanalyzer files list` + `apksigner --print-certs` digest; require `--origin` explicit; compare to `resources`/`dex strings` for the origin and reject other `https?://` literals.
- Add `--require-https` w/ loopback exception only under `--fixture-test`.

## File ownership
PASS. `git status`: modified = codebase-summary, development-roadmap, project-overview-pdr; untracked = exactly the other 17 registered Phase 08 paths (+7 PNGs). Total 20 = registry. No other tracked file changed. Ignored build dirs only. Note: file-ownership says "create" for PDR/summary/roadmap but they pre-existed (modified) — registry inaccuracy, harmless.

## Reviewed files
All 20 owned artifacts; cross-read: phase-08 plan, file-ownership.md, contracts.md §4-6, docs/api-contract.md, scripts/hub-schema-provision.mjs, hub-compatibility-smoke.mjs, androidApp manifest, docs/project-roadmap.md.

## Validation commands & results
- `node scripts/hub-live-smoke.mjs --fixture-test` → 15/15 PASS, exit 0 (circular; see C1).
- `node scripts/release-security-check.mjs --diagnose-source` → CLEAN, exit 0.
- `node scripts/release-security-check.mjs` (no APK) → FATAL not found; fail-closed OK (only this path).
- `DRY_RUN=true CONFIRM_MAINTENANCE_QUIESCENCE=true node scripts/hub-strict-recovery.js` → PASS exit 0 (no-op); `node --check` OK.
- Reproduced secret-scan path-substring bypass with temp repos (cleaned up).
- Not run: mongosh (absent), apksigner/apkanalyzer (absent; SDK missing), Gradle tests (not Phase 08 code).

## Unresolved questions
- Is `apps` the real registry collection and are `strictEpoch/recoveryTimestamp/lastRecoveryStatus/epoch/updatedAt` fields consistent with Phase 03 Rust (external Hub repo not available to verify)?
- Plan §3 says `hub-schema-provision.mjs --environment production --confirm-app tabbyrush`; actual script has no such flags — plan wrong or 03 script incomplete?
- Should Phase 08 status be "implementation in progress / tooling not accepted" until C1–C6 fixed?
