# Code Review — Phase 01: Freeze v3 contract + Hub upgrade/receipts

Date 2026-10-19. Reviewer: Phase1Reviewer. Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-01-contract-and-hub.md`.

## Score: 4/10 — NOT acceptable as Phase 01 complete

Contract freeze (schema, fingerprints, 6 golden vectors, Kotlin/Rust/Node parity) is solid. Hub runtime is not: a published v3 app cannot sync. Operator CLI cannot do v2→v3 against a real Hub without extra flags.

## Scope
- TabbyRush: `integration/glean-hub/{tabbyrush-schema-v3,strict-v3-vectors}.json`, Kotlin `strict-sync-v3-{contract,digest}.kt` + `strict-v3-digest-vector-test.kt`, `scripts/{generate-strict-v3-vectors,strict-v3-vector-runner,strict-v2-vector-runner,hub-strict-upgrade,hub-schema-provision}.mjs`, `hub-strict-upgrade.test.mjs`, `docs/system-architecture.md`.
- Hub (`glean-hub-sync`): `models/{strong_sync_v3,strict_upgrade,mod}.rs`, `services/{app_service,strong_sync_service,strict_upgrade_service}.rs`, `strict_upgrade_tests.rs`, `strong_sync_tests.rs`, `tests/fixtures/strict-v3-vectors.json`.
- ~2.4k LOC (≈1.5k TabbyRush new/changed, ≈0.9k Hub diff + 458 new Rust + 370 fixture).

## Critical issues (blockers)
1. **Hub rejects strict-v3 on every strict endpoint.** `strong_sync_service.rs:104-113` `authorize_strict_access` matches only `PROTOCOL_V1`/`PROTOCOL_V2`; anything else → `ProtocolUnsupported`. Callers: bootstrap (157), push (220), pull (991), legacy-receipts (1136). After the new v2→v3 publish step, the app is bricked: no push/pull/bootstrap, no v2 receipt reconciliation (plan success criterion "authorized receipt reconciliation works" fails). The new `PROTOCOL_V3` apply arm is unreachable in production. Missed because no service-level test exists (only DTO/schema/vector tests; live-Mongo gate ignored and not extended for v3). Found by inspection; not executed. Fix: add `Some(PROTOCOL_V3)` arm + update error text; add a test for each of the 4 entry points.
2. **CLI v2→v3 sends wrong target fingerprint.** `hub-strict-upgrade.mjs:114-117` defaults stay doggo v1→v2; `--target-version strict-v3` alone still POSTs `targetSchemaFingerprint` = doggo **v2** value (lines ~373-408 use `options.targetFingerprint`). Real Hub (`is_valid_schema_fingerprint(app, v3, v2fp)`) rejects. New CLI test passes only because the mock never validates the POST body. Fix: derive default expected/target fingerprints after parsing from `(appId, version)`; assert the exact POST body in the test.

## Warnings (high/medium)
1. **No behavioural test of any v3 Hub path**: replay-after-parent-delete, child under deleted/missing parent, grandchild, reparent/detach, cross-user parent, v2 legacy receipts, transform/resume/rollback, lost publish. Plan success criteria 2-5 are unproven; the QA report's "404 passing verifications" is vectors + DTO tests, not these. Run live Mongo (`MONGO_TEST_URI`; no mongod locally, docker has no mongo image) or add an in-process harness.
2. **Possible permanent fence after post-CAS crash** (hypothesis from code, not reproduced): `strict_upgrade_service.rs` CAS publish (≈557) precedes `release_fence` (≈584). If the process dies between, retry hits the `current_sync_mode == target_version` early return (≈235) which returns Ready without releasing the fence or finalising the migration record. Plan step 5 asks for postpublication recovery. Same shape pre-existed for v2; phase says resolve it. Release fence (if owned by this migration id) + finalise record in that branch; add crash/retry test.
3. **CLI fingerprint guard loosened + inconsistent.** `isMatchingFingerprint` accepts either app's frozen fp for any app (lines 71-77); explicit `--expected-fingerprint` is silently overridden (351-353); final read-back accepts aliases (506) while already-on-target check uses exact equality (328) → rerun on tabby-rush with defaults fails. Already-on-target branch runs `assertFinalContract` with the *observed* epoch before `--expected-epoch` is checked (327-335) → wrong-epoch invocation can succeed. Plan step 7: "reject wrong fingerprint/version, unsupported skip paths and downgrade" — CLI does no client-side transition check (Hub does).
4. **Hub fingerprint validation permissive.** `is_valid_schema_fingerprint` (strict_upgrade.rs) accepts dynamic fp OR doggo OR tabby-rush frozen constant for ANY app. Old code required exact constant. Constants are redundant with the dynamic check; drop them from acceptance (keep in tests).
5. **v3 validation doesn't require explicit null.** `validate_transformed_collection_v3` deserialises to `StrictV3TodoData` (serde default → missing key = None). A live row lacking `data.parentTaskId` passes validation; count uses `$exists:true` so it would just under-report. Contract says explicit null. Check key presence (and `Null|String`).
6. **Child UPDATE under deleted/missing parent → `InvalidRecord`** (service ~708-721). Exact replay is correctly resolved first (row exists + lastOp match precedes this), so criterion 3 holds for replay; but a *fresh* child edit gets a status the Phase 2 client may treat as integrity failure, not "parent deleted → enqueue DELETE". Contract is silent; decide status (see questions). Also extra `find_one` per child update/create (indexed `_id`; fine, could project `deleted,data.parentTaskId`).
7. **v2 runner guard weakened.** `strict-v2-vector-runner.mjs`: cross-repo hash comparison now skipped when first-vector `appId` differs (TabbyRush=`tabby-rush`, Hub fixture=`doggo`) and fp table now keyed by schema appId. Reason is legitimate (HEAD runner already failed after rebrand) but v2 TabbyRush↔Hub vector parity is no longer asserted. Re-freeze Hub v2 fixture under tabby-rush or add an explicit compare of preimage/digest per vector. Both v2/v3 runners also swallow `ENOENT` on the Hub fixture → silent "parity" pass when the sibling repo is absent.
8. **QA report inaccuracy** (`qa-261019-1754-…`): says rejection vectors cover "malformed JSON, cross-user, depth > 2, immutable link". Fixture actually has: self-parent, blank parent, delete-with-data, create-with-null-data, revision overflow. Cross-user/depth/immutable rules are server-only and untested (see W1). Also Kotlin parity not re-run by me.
9. **Admin UI unaware of v3**: `glean-hub-app` `sync-admin-service.ts:103` state union lacks `ready-v3`; upgrade panel/contract card hard-coded to v2. Plan said migrate consumers incl. management callers; at minimum add `ready-v3` to the union.
10. **Compiler warning**: unused `SCHEMA_FINGERPRINT_V1/V2/V3` import, `strict_upgrade_service.rs:8` (confirmed via `cargo check`).

## Suggestions (low / DRY-KISS-YAGNI)
- `compute_v3_digest_helper` (strong_sync_service ~1491) is a pure pass-through to `compute_v3_digest`; call directly.
- Rust `PROTOCOL_VERSION_V3` (strong_sync_v3.rs:6) unused and duplicates `PROTOCOL_V3`; remove (v1/v2 have the same legacy pattern; don't add a third).
- v3 Create/Update arms in `apply_operation` are ~90 near-identical lines; factor into one non-Delete branch. `validate_strict_v3_schema` (app_service) is a 75-line copy of v2; drive by `canonical_v3_columns()`. `StrictV3TodoData::validate` copies v2 validate (Rust+Kotlin); extract shared deadline/reminder check.
- Parent id: `validate()` only checks non-blank/≠id (Rust+Kotlin); canonical-UUID check lives only in the Hub create path. Contract says "blank/noncanonical IDs rejected" — move into shared validation for parity. Kotlin reminder math is unchecked `Long`; Rust uses `checked_*` (pre-existing divergence).
- Kotlin contract ships `LEGACY_DOGGO_*` fingerprints and per-app `when` for a client that only serves `tabby-rush` (YAGNI).
- 418-line generator re-implements the encoder already in the runner; keep one encoder module or delete generator once frozen.
- Stale docs: CLI header says "strict-v1 → strict-v2"; `system-architecture.md §9` still "Proposed (Not Implemented)" while contract/Hub code now exists; Hub `docs/CHANGELOG.md` has no v3 entry; no tests of rollback-on-failure for v3.
- Reverse-rollback `rollback_transform_v3` unsets `parentTaskId` on all live rows; safe pre-publication (v3 writes impossible), but add a comment/guard that it must never run post-publish.

## Positive
- Preimage layout: frozen v2 order + presence-tag `parentTaskId` last; Kotlin/Rust/Node byte-identical on 6 vectors; fixture byte-identical across repos; schema fp `7f03…88a8f` agrees in 3 runtimes.
- Replay-first ordering correct: row exists → lastOp match → `confirm_replay`, before any live-parent check; create-only parent validation (same user via `{user}:{parent}` id, live, depth-1 root check); UPDATE immutability compares against authoritative stored parent (blocks root→child, detach, reparent).
- Upgrade preserves lastOp/revisions/tombstones; v1→v3 skip and downgrade rejected server-side; migration-record param-conflict check; v1/v2 preimages untouched.
- Legacy receipts now dispatch by original protocol (v1/v2) without applying mutations.

## Validation (run by reviewer)
| Command | Result |
|---|---|
| `node scripts/strict-v3-vector-runner.mjs` | PASS 6/6 valid, 5/5 rejections, fp ok |
| `node scripts/strict-v2-vector-runner.mjs` | PASS 10/10, 11/11 (cross-repo hash step now conditional) |
| `node --test scripts/hub-strict-upgrade.test.mjs` | PASS 13/13 |
| `cargo test --locked` (glean-hub-sync) | PASS 62 / 0 fail / 1 ignored (live Mongo) |
| `cargo check --locked` | 1 warning (unused imports) |
| Gradle `:shared:jvmTest` | not re-run; relied on QA report (297 pass) |
| Live Mongo / deployed Hub | NOT run — no mongod/image, `MONGO_TEST_URI` unset |

Passing results do not cover C1/W1.

## Plan status
Phase 01 header/todo updated: implementation partial, review = blocked on C1, C2. Todo "Verify v1/v2 unchanged and v3 parity" partially done.

## Unresolved questions
1. Status for fresh child UPDATE under tombstoned/missing parent: keep `InvalidRecord`, or add/reuse a distinct "parent deleted" outcome so Phase 2 can enqueue the child DELETE per design-contract §Deletion.5?
2. Should Hub accept any app's frozen fingerprint constants, or only the per-app dynamic fingerprint (recommended)? Is `doggo` still a live strict app?
3. Is `glean-hub-app` upgrade UI in Phase 1 scope (ready-v3), or deferred to Phase 6?
4. Is live-Mongo proof for criteria 2-5 expected in Phase 1 or only at Phase 6 gate (plan text says Phase 6)? If Phase 6, still need in-process service tests now to catch C1-class bugs.
