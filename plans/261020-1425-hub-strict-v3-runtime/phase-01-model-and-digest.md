# Phase 01 — Distinct v3 model and canonical digest

Priority P1 · Pending · 3h · Dependency: frozen client contract and vectors already present.

## Context and requirements

Read together:
- `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v3-contract.kt`
- `S/shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/strict-sync-v3-digest.kt`
- `S/integration/glean-hub/{tabbyrush-schema-v3,strict-v3-vectors}.json`
- `P/src/models/{strong_sync,strong_sync_v2,strong_sync_v2_tests,strict_upgrade,mod}.rs`
- [Frozen hierarchy semantics](../261019-1511-parent-child-tasks-deadline-surfaces/design-contract.md#strict-v3-and-sync-convergence)

V3 has the exact 16 v2 fields plus nullable `parentTaskId`. Roots need a null-parent byte even when they otherwise look like v2. V3 DELETE uses domain `strict-v3`, expected revision framing and no data bytes. A v2 digest with a v3 label is not equivalent.

## File changes

| Action | Path | Purpose |
|---|---|---|
| Create | `P/src/models/strong_sync_v3.rs` | `StrictV3TodoData`, validation, `build_v3_preimage`, `compute_v3_digest` |
| Modify | `P/src/models/mod.rs` | Register/export v3 model and test module using existing pattern |
| Create | `P/src/models/strong_sync_v3_tests.rs` | Production-function vector and serde/validation tests |
| Create | `P/tests/fixtures/strict-v3-vectors.json` | Exact canonical copy from S for standalone Hub test execution |
| Preserve | `P/src/models/strong_sync_v2.rs`, existing v1/v2 fixtures | No accepted-input or digest changes |

No source deletions. Rust filenames follow this module's existing snake_case convention. Reuse existing `PROTOCOL_V3` from `models/strict_upgrade.rs`; avoid competing constants.

## Implementation steps

1. Add separately typed camelCase serde v3 DTO with `deny_unknown_fields`. Keep integer wire types compatible with the current v2 envelope. Explicitly serialize `parentTaskId: null` for roots; never omit it from persisted live v3 records. Do not flatten permissive maps or silently discard parent data through a v2 DTO.
2. Match existing temporal/reminder rules, including checked arithmetic, count/interval constraints, deadline-null consistency and timed deadline anchor consistency. Add parent checks: nonblank canonical lowercase UUID shape and not equal to task ID. The existing UUID helper checks lowercase hexadecimal shape, not RFC UUID version bits; do not add a UUIDv4-only bit restriction based on an error-message label. Keep title, row-ID equality and operation envelope checks at the established runtime boundary.
3. Preserve established optional-field serde behavior unless the frozen wire contract explicitly requires presence. Distinguish this from storage: live v3 BSON must contain an explicit null/string parent; do not interpret missing/corrupt persisted parent as authoritative null during link comparisons. Add tests documenting omitted versus explicit-null input behavior rather than silently tightening the client contract.
4. Implement canonical encoding in a single growing byte buffer following existing Rust code. Frame domain/app/user/epoch/table/row/op/kind as 32-bit big-endian UTF-8 byte lengths; expected revision is 8-byte big-endian. Append the v2 field sequence unchanged, then parent `0x00` for null or `0x01 + framed UTF-8 string` for child. Bool is one byte; nullable integers and strings retain existing markers; signed i64 uses big-endian two's-complement bytes. No JSON hashing, string normalization, lowercase coercion or delimiter concatenation.
5. Reject non-null DELETE data, null CREATE/UPDATE data and digest revision values above signed i64 range as fixtures require. Runtime retains the stricter existing safe-integer `MAX_REVISION` limit; do not conflate helper representability and runtime CAS limits.
6. Adapt existing fixture reader pattern to v3. Canonical JSON stores i64 values as decimal strings for precision; convert only in test fixture adapters, not by weakening production wire models. Assert fixture preimage hex, byte length and SHA-256 independently using production functions. Preserve canonical fixture contents; do not regenerate expected values from the code under test.
7. Make missing fixture a hard failure. Use the checked-in Hub copy for standalone tests; the main agent's cross-repository acceptance must compare it with S's canonical bytes. No conditional pass when a sibling fixture is missing.

## Acceptance checks to implement, not run here

- Consume every valid and rejection vector; cover root null parent, child parent, own deadline, Unicode framing and DELETE exactly as the fixture specifies.
- Root v3 digest differs from v2 for equivalent legacy data; changing only parent changes the digest.
- Reject blank, self, malformed/noncanonical parent, wrong type, extra field, mismatched row ID at runtime, invalid reminder combinations and overflow.
- JSON → model → BSON → JSON retains all 17 fields and explicit root null. Tombstones are handled separately as `{}`, never serialized through a live-task DTO.
- Existing v1/v2 vectors remain unchanged and v2 still rejects a v3 parent field.

## Risks, security and handoff

Largest risk is a superficially plausible v2 reuse that loses the null marker or relationship field. Use byte-exact vectors, not hash-only spot checks. Do not copy Kotlin's unchecked arithmetic into Rust; retain existing safe temporal validation. Parent existence, scope and immutability require authoritative rows and belong in phase 02, not standalone DTO validation.

Next: wire the typed payload/digest into every runtime branch before admitting v3 in any deployed binary. No tests, builds, formatters or lint runs in this planning assignment.

## Unresolved questions

None. Optional input versus explicit persisted null is an intentional distinction, not permission to weaken migrated-storage invariants.
