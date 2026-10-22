# Code Review — Phase 01 Cycle 2 (verification of cycle-1 fixes)

Date 2026-10-19. Reviewer: Phase1ReviewerCycle2. Plan: `plans/261019-1511-parent-child-tasks-deadline-surfaces/phase-01-contract-and-hub.md` (updated). Prior: `code-reviewer-261019-1800-phase-01-freeze-v3-contract-hub-upgrade.md`.

## Score: 7/10

Both cycle-1 criticals fixed and verified by inspection + tests. No critical open. Remaining: v3 Hub behaviour still unproven by any executed service-level test (C1-class regressions can recur silently); new recovery branch has gaps.

## Cycle-1 fix verification
| Item | Status | Evidence |
|---|---|---|
| C1 `authorize_strict_access` rejects v3 | FIXED | `strong_sync_service.rs:104-120` has `Some(PROTOCOL_V3)` arm; shared by bootstrap/push/pull/legacy-receipts. `validate_strict_schema_for_mode` dispatches v3 (`app_service.rs:349`). No test hits it (needs Mongo). |
| C2 CLI wrong target fp | FIXED | `hub-strict-upgrade.mjs:157-167` derives defaults post-parse from appId/version; test asserts POST body `expectedVersion=strict-v2`, `targetVersion=strict-v3`, `targetSchemaFingerprint=7f03…8a8f`. |
| W2 permanent fence after post-CAS crash | PARTIAL | `strict_upgrade_service.rs:235-262` releases fence + finalises record when `current==target`. See W1/W2 below. |
| W10 unused-import warning | FIXED | `cargo check --locked`: 0 warnings. |
| UUIDv4 `parentTaskId` | DONE (shape only) | Kotlin/Rust/Node all check 36-char lowercase hex + hyphens; none enforce version nibble despite "UUIDv4" messages. Parity OK. |
| Transition tests | WEAK | see W3. |
| W3 CLI loosened guards | NOT FIXED | still cross-app `isMatchingFingerprint`; already-on-target branch runs before `--expected-epoch` check. |
| W4 Hub permissive fingerprints | NOT FIXED | `is_valid_schema_fingerprint` still accepts doggo OR tabby-rush frozen constants for any app. |
| W5 explicit-null not validated | NOT FIXED | `validate_transformed_collection_v3` unchanged (serde default → missing key passes). |
| W7 v2 runner guard weakened | NOT FIXED | unchanged; v3/v2 runners still conditional on sibling fixture. |
| W9 admin UI `ready-v3` | NOT ADDRESSED | out of diff. |

## Critical issues
None.

## Warnings
1. **Recovery branch does writes under `dryRun`.** `strict_upgrade_service.rs:235-262` runs before the dry-run return: a `dryRun:true` request on an already-upgraded app calls `release_fence` + updates the migration record. Dry run must be side-effect free. Gate on `!req.dry_run`.
2. **Recovery branch unreliable/unreachable in practice.** (a) `release_fence` only releases when `fenceOwner == migrationId`; CLI generates a fresh random UUID per run unless `--migration-id` passed, and CLI "already on target" path (`hub-strict-upgrade.mjs:~330`) exits 0 *without POSTing at all* once contract reports target → lingering fence never cleared via CLI. (b) Both calls are `let _ =` — failure to release still returns Ready. (c) Update filter `{_id, appId}` will overwrite any state (e.g. `rolledBack`/`operatorRequired`) of an arbitrary caller-supplied id to ready; also runs before epoch/param-conflict checks. Fix: release by owner from coordination doc (or fence-owner lookup), propagate errors, only finalise a record in a non-terminal state with matching expected/target version; have CLI call Hub when fence lingers (or surface fence status in contract/status). Not reproduced; derived from code.
3. **`test_valid_upgrade_transitions_and_rejections` is tautological** (`strict_upgrade_tests.rs`): re-implements the match in a closure; never calls production code. Extract `validate_transition(expected, target) -> Result` as a pure fn in the service and test that. Same for v3 DTO tests: they exercise parse/validate only.
4. **No executed test of any v3 Hub path** (admission via authorize, replay-after-parent-delete, child under deleted/missing parent, grandchild, reparent/detach, v2 legacy receipts, resume/rollback/lost publish). C1 fix has zero regression guard. Live-Mongo test remains `#[ignore]` and not extended. Plan puts live proof in Phase 6, but at least mark C1/replay criteria 2-5 unproven in Phase 1 acceptance. Consider a trait-seam/in-process Mongo (e.g. mongod binary in CI) before Phase 2 relies on it.
5. **Explicit null not enforced** (W5 carry-over). A live row missing `data.parentTaskId` passes v3 validation; count `$exists` only under-reports. Check `data.contains_key("parentTaskId")` and `Null|String`.
6. **Hub fingerprint validation permissive** (W4 carry-over): any app can use either app's frozen constants; dynamic per-app value already covers legit cases. Restrict.
7. **CLI guard inconsistencies** (W3 carry-over): `--expected-epoch` ignored when already on target; `isMatchingFingerprint` cross-app; unknown `--app-id` silently falls back to doggo fingerprints (`parseArgs`), producing a confusing Hub 400 rather than a local error. One test assertion (`'reports target version'`) was deleted in the corrupted-v2 test without replacement; target-state assertion semantics changed — confirm intentional.
8. **Fresh child UPDATE under deleted/missing parent → `InvalidRecord`** (cycle-1 W6, open question). Phase 2 client cannot distinguish integrity failure from "parent deleted → enqueue child DELETE". Decide before Phase 2.
9. **Docs stale in Hub:** `docs/CHANGELOG.md` diff only covers justfile work; no strict-v3 / upgrade / legacy-receipt entry. TabbyRush `system-architecture.md §9` still "Proposed (Not Implemented)" while Phase 1 contract/Hub code exists (acceptable only if explicit that Phase 1 contract is frozen; add one line).
10. **v2 runner cross-repo parity** still conditional; both runners swallow missing sibling fixture as pass.

## Suggestions
- Drop `compute_v3_digest_helper` passthrough; remove unused `PROTOCOL_VERSION_V3`; fold v3 Create/Update arms (~90 near-dup lines) and `validate_strict_v3_schema` (75-line v2 copy, drive from `canonical_v3_columns()`) — DRY.
- `rollback_transform_v3` unsets `parentTaskId` on all live rows: add comment/guard "pre-publication only".
- Kotlin contract `LEGACY_DOGGO_*` + per-app `when` is YAGNI for tabby-rush-only client.
- 418-line vector generator duplicates the runner's encoder; delete or share after freeze.
- Rename messages "UUIDv4" → "canonical lowercase UUID" or enforce version/variant nibbles.
- Project `{deleted, data.parentTaskId}` on the extra parent/existing-row `find_one` in child create/update.

## Positive
- C1/C2 fixes minimal and correct; canonical fingerprint per app/version in CLI consistent with Hub dynamic value (`7f03…8a8f` agreed across Node/Rust/CLI).
- Replay-first ordering preserved; update immutability compares against authoritative stored parent; child DELETE not parent-gated.
- Explicit v1→v3 skip and downgrade rejected server-side; clean compiler output.
- CLI test now asserts exact request body (closes the mock-doesn't-validate gap from cycle 1).
- Kotlin/Rust/Node UUID validation identical.

## Security
No new vulnerabilities. Admin-only endpoint; owner-gated fence release prevents cross-migration release. Permissive fingerprint acceptance (W6) widens accepted admin input marginally; no token/secret leakage in CLI (sanitizeLog retained, tested). Recovery branch lets any holder of an app admin token flip a terminal migration record to ready (W2c), low severity.

## Performance
Per-child create/update adds one indexed `_id` lookup (acceptable). v3 transform uses batched `$in` update_many with per-batch checkpoint, same shape as v2. Validation cursor scans full collection under fence (O(n), same as v2).

## Reviewed files
TabbyRush: `scripts/{hub-strict-upgrade,hub-strict-upgrade.test,hub-schema-provision,strict-v2-vector-runner,strict-v3-vector-runner,generate-strict-v3-vectors}.mjs`, `integration/glean-hub/{tabbyrush-schema-v3,strict-v3-vectors}.json`, Kotlin `strict-sync-v3-{contract,digest}.kt` + vector test (UUID parity only), `docs/system-architecture.md`, plan phase-01.
Hub (`glean-hub-sync`): `services/{strong_sync_service,strict_upgrade_service,strict_upgrade_tests,strong_sync_tests,permit_service(release_fence)}.rs`, `models/{strict_upgrade,strong_sync_v3}.rs`, `services/app_service.rs` (v3 validate dispatch), `docs/CHANGELOG.md`.

## Validation (re-run this cycle)
| Command | Result |
|---|---|
| `cargo test --locked` (glean-hub-sync) | PASS 64 / 0 fail / 1 ignored (live Mongo) |
| `cargo check --locked` | PASS, 0 warnings |
| `node scripts/strict-v3-vector-runner.mjs` | PASS 6/6 valid, 5/5 rejections, fp ok |
| `node scripts/strict-v2-vector-runner.mjs` | PASS 10/10, 11/11 |
| `node --test scripts/hub-strict-upgrade.test.mjs` | PASS 13/13 |
| `./gradlew :shared:jvmTest --offline -q` | exit clean, no failures printed (completed in ~1s → likely up-to-date; 297-test count not independently observed) |
| Live Mongo / deployed Hub | NOT run (no mongod / `MONGO_TEST_URI`) |

Passing results do not cover W1–W5.

## Unresolved questions
1. Status for fresh child UPDATE under tombstoned/missing parent: keep `InvalidRecord` or add distinct outcome?
2. Hub accept any app's frozen fingerprint constants, or per-app dynamic only? Is `doggo` still a live strict app?
3. Admin UI `ready-v3` in Phase 1 scope or Phase 6?
4. Is in-process/live Mongo proof of success criteria 2-5 required in Phase 1 or only Phase 6? (Recommend an in-process service test now.)
5. Should recovery of a lingering fence be CLI-reachable (status/fence surfacing) or operator-manual with the original `--migration-id`?
