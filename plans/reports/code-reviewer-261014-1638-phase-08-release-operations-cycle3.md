# Phase 08 Code Review — Cycle 3

Score: **6/10 — NOT ACCEPTED for live recovery; tooling gains real** (cycle 2: 4/10)

Cycle-2 findings (mongosh `db` access, un-awaited promise, URI logging, absence-as-pass, stale evidence, vacuous smoke asserts) verified fixed by code reading + offline runs. New blocker: recovery script writes/validates BSON types the Hub cannot read (Int64 vs Int32). mongosh path never executed (no mongosh/mongod on host) — evidence docs must say so.

## Critical (MUST FIX)
1. **`hub-strict-recovery.js` imports `revision: 1` as non-Int64.** Hub reads `doc.get_i64("revision")` (`strong_sync_service.rs:363,621,745`, write side `1i64`). mongosh serializes JS `1` as Int32/Double → every imported row: pull fails with `Internal("Document missing revision field")` (whole page), push → `Internal`. Recovery would "succeed" (counts match, admission preflight passes — it only checks indexes/collection options) and then production sync breaks for all imported rows. Fix: `revision: Long(1)` (`Long.fromNumber(1)`/`NumberLong(1)`). Same for any numeric normalisation (`acceptedRevision` n/a since lastOp=null).
2. **`validateTodoRecord` rejects real backups.** `typeof data.createdAt !== 'number'` — Hub writes `createdAt` as i64 (`StrictTodoData.created_at: i64`); `mongorestore` of a Hub backup (docs/security-and-recovery.md step 2) preserves Int64, which mongosh returns as `Long` object (`typeof === 'object'`) → every record fails → "Record validation failed … Aborting" (fail-closed, but recovery unusable on the documented path). Conversely if source was JSON-imported (Int32/Double) and copied verbatim, Hub `get_i64("createdAt").unwrap_or(0)` (`:467`) = 0 → later updates trip createdAt-immutability. Fix: accept Number|Long|Int32, check integer 0..2^53-1, and **rewrite** `data` into a fresh whitelisted object `{id,title,completed,createdAt: Long(n)}`. [INFERENCE for mongosh Long promotion; Hub-side Rust accessors verified in source.]
3. **Recovery path never executed under mongosh** (no `mongosh`/`mongod` here; `which mongosh` empty). Cycle-2 reviewer's mongosh runs are the only mongosh evidence. #1/#2 prove this is not cosmetic. Required before acceptance: run against throwaway mongod: seed source with Hub-written Int64 rows (+ tombstones), run script, then run real Hub pull/push against candidate. Docs must stop labelling Disaster Recovery as **PASS** (`release-acceptance.md:31`, `android-release-summary.md` item 03/§3.3) — Node dry-run only checks env vars and exits before any DB logic.

## Warnings (SHOULD FIX)
### hub-strict-recovery.js
- `executeAdmissionPreflight` `if (!isNode) { … return; }` is a fail-open no-op before CAS. Currently unreachable (non-Node ⇒ `env={}` ⇒ prereq failure) but one refactor from a silent skip. Throw instead.
- `validateTodoRecord` skips contract checks: userId exact 24-hex (Hub `is_valid_user_id`), rowId canonical lowercase UUIDv4, title trimmed 1..200 scalar values, `tableName === 'todos'` (script hard-codes `'todos'`, silently coercing other tables), tombstone `data` must be `{}`/absent. Extra keys in `data` are copied (`StrictTodoData` is `deny_unknown_fields`). Whitelist fields (see Critical 2).
- Source guard only `startsWith('_strict_v1_')`; also reject `_schemas`, `system.*`, names starting `app`. (Prefix check is adequate vs. namespace formula otherwise.)
- Extra fields `epoch`, `updatedAt` written; Hub's own docs have `confirmationNonce` and no such fields. Harmless today (`get_*` accessors ignore) — drop to keep documents identical to Hub-written shape.
- No cross-check that `importedCount + tombstoneCount === targetCol.countDocuments()` (only source count vs loop counter; both come from the same cursor). Compare against target collection count after inserts.
- `findOne(query, {}, {readConcern})` third-arg options form is mongosh-version dependent [INFERENCE, untested]; `require('node:child_process')` availability inside `mongosh --file` also untested.
- `docs/security-and-recovery.md` step 2: `mongorestore --nsInclude="glean_hub.staging_doggo_restore"` restores by source namespace; to stage a `_strict_v1_*` dump under another name needs `--nsFrom/--nsTo`. As written it matches nothing / restores into the strict namespace itself.

### release-security-check.mjs
- **`android:debuggable="false"` required explicitly, but source manifests never set it** (`androidApp/src/main/AndroidManifest.xml`; release build type leaves `isDebuggable` default). AGP normally omits the attribute in release merged manifests [INFERENCE, no APK built]. Gate may fail every correct release; hard-coding it risks lint `HardcodedDebugMode`. Decide: accept "absent or false" (absent ⇒ false) and fail on `"true"`, or prove on first real APK. Diagnose mode does not check debuggable at all, so it cannot catch this.
- Signature gate accepts any valid signature, incl. Android **debug** cert (`CN=Android Debug`). Add reject on debug DN and compare digest to pinned `EXPECTED_SIGNER_SHA256` (plan §6 records public cert digest). Multi-signer (`Signer #2`) ignored.
- `--origin` check only validates the CLI arg; not compared to anything in the APK (`HUB_ORIGIN_RELEASE`). Header claim "Asserts release endpoint targets approved HTTPS origin" overstates; compare with BuildConfig/strings extracted from dex (`apkanalyzer dex packages`/`strings`) or reword.
- No secret scan of the APK, only source mode. Source scan exempts any path containing `test`/`Test`/`mock` (also matches `latest`, `contest`, `Attestation`) and never flags committed `*.jks`/`*.keystore`/`keystore.properties`.
- Exported audit: `<activity[^>]*>` also matches `<activity-alias>`; `isMain = /MainActivity/i.test(tag)` matches any tag text containing it (e.g. `com.x.MainActivityProxy`). Match `android:name` exactly (`.MainActivity` or FQCN) and require `exported` explicitly; implicit export via intent-filter with no attribute not flagged (targetSdk≥31 forces explicit, acceptable).
- Debug diagnose passes with source manifest only; `main` manifest `debuggable`/backup rules fine, debug-only networkSecurityConfig correctly in `src/debug`.

### hub-live-smoke.mjs
- "Invalid JWT" probe uses literal `Bearer invalid_tampered_jwt_token` (malformed, not a tampered valid token) — rejects at parse stage; doesn't prove signature verification. Flip a char in the signature segment of the real `authToken`.
- Pull/tombstone checks read only first `batchSize:100` page, no `hasMore`/cursor follow; a reused `TEST_EMAIL` account with >100 rows false-fails. Follow cursor until found.
- Origin not constrained: credentials (`TEST_PASSWORD`, API key, JWT) sent over plain `http://` to any `--origin`. Require `https:` except loopback/`--fixture-test`.
- Response body unbounded (no max bytes); auto-registers a random account on target with no explicit authorization flag; `refresh`/`me` checks conditionally skipped (18 only if refresh token returned; Suite 5 only with `--allow-destructive-cleanup`, so live default run is 16, docs' "18/18" is the fixture+flag case).
- Epoch-mismatch probe uses `00000000-…` (not UUIDv4) — works because Hub compares epoch before validating (`authorize_strict_access`), but a canonical random UUIDv4 is the intended probe.
- Fixture is the same author's mock: PASS proves script self-consistency, not Hub behaviour (e.g. fixture `result.currentRevision` vs Hub `current`). Detail text only.

## Suggestions
- `generateUuidV4` Math.random fallback: fine for non-secret epoch; prefer `crypto.randomUUID` hard-fail if absent.
- Print a `[WARN]` banner when `DRY_RUN` skips DB connection so "dry-run PASS" is not read as DB validation; consider read-only connect + counts in dry-run.
- Record the Hub `strong_sync_admission` binary path/sha256 in recovery output (plan §9 provenance).
- Add a tiny Node unit test for `validateTodoRecord` with Long-like objects to lock Critical 2.
- Evidence summary: `hub-recovery-summary.md` §2 should state "mongosh path unexecuted" explicitly.

## Positive observations
- No credential output anywhere in recovery script; URI only in env; child helper gets URI via env, not argv.
- `connect` typeof gating, all-sync flow, `process.exit(1)` outside mongosh verified: live Node run → `[FATAL] … requires mongosh`, exit 1; no-quiescence → exit 1; dry-run exit 0.
- CAS: right collection (`_schemas`), filter on epoch+syncMode, majority+journal, ambiguity keeps traffic blocked, old namespace never dropped. Namespace formula and scan index match `strict_collection_name`/`validate_strict_admission` (extra index names fine; backend only checks keys).
- Security checker: apksigner/apkanalyzer via `execFileSync` (no shell), fail-closed on missing APK/tools (exit 1 verified), explicit-false attribute checks, four component tags, v2/v3 requirement, cert digest print.
- Smoke: asserts exact `testRowId`, tombstone `deleted:true,data:{}`, replay, bootstrap==admission epoch, 18/18 reproducible in fixture.
- Docs: `_schemas`, `epochMismatch`, `ResetRequired` aligned; Mongo credential placeholders sanitized (`mongodb+srv://<db_user>:<db_password>@…`; no real URIs found by grep).

## Validation (run by me)
- `node --check` ×3 scripts: OK.
- `node scripts/hub-live-smoke.mjs --fixture-test`: 18/18 PASS, exit 0.
- `node scripts/release-security-check.mjs --diagnose-source --origin https://hub.example.com`: CLEAN.
- `node scripts/release-security-check.mjs --apk /nonexistent.apk --origin https://x`: exit 1 (fail-closed).
- Recovery: live in Node (all env set) → `[FATAL]` mongosh required, exit 1; no env → exit 1; `DRY_RUN=true CONFIRM_MAINTENANCE_QUIESCENCE=true` → exit 0.
- Cross-read Hub source: `strong_sync_service.rs`, `app_service.rs` (`validate_strict_admission`, index rules), `bin/strong_sync_admission.rs`, `models/strong_sync.rs`.
- NOT run: mongosh live recovery (no mongosh/mongod), `apksigner`/`apkanalyzer` on real APK (no SDK/APK), Gradle `:shared:jvmTest` 96/96 (claimed in docs, not re-run), live Hub.

## Task completeness (Phase 08)
Scripts/docs delivered; implementation/runtime acceptance still pending: live recovery on mongod+Hub, real signed APK audit, connected-device, live Hub smoke, Phase 07 correctness defects. Plan status stays pending.

## Unresolved questions
1. Does AGP-merged release manifest omit `android:debuggable`? (decides whether gate always fails.)
2. Does `mongosh --file` expose `require('node:child_process')` and `findOne` options arg on the operator's mongosh version?
3. Will operator stage source via `mongorestore` of a Hub backup (Int64) or JSON import (Int32/Double)? Script must handle both.
4. Is a pinned release-cert SHA-256 available to compare in the audit?
