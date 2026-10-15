# Phase 05 — Shared task model and explicit Room migration

## Context links
[Parent](plan.md) · [Frozen fields](phase-01-freeze-contract-and-transition-design.md) · [Storage evidence](research/reminder-platform-research.md) · [Existing storage spec](../../docs/local-storage-and-sync.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 20h. Owner: shared storage/contract lead. Depends on phase 01 G1; can run alongside phase 03. This phase does not activate v2 network writes.

## Key Insights
Every persisted representation carries task intentions. Outbox schema extension must keep old frozen digest/preimage intact. UI draft and submitted saved intention are different eligibility sources.

## Requirements
- Typed deadline/rule domain, same canonical wire mappings as phase 01; all saves validated before committing.
- Explicit Room 1→2 preserving existing base/outbox/drafts/staging/meta and account partitioning.
- Store source contract version, immutable dispatch evidence, transition checkpoint/changed-field intent mask; preserve frozen v1 data on local upgrade.
- Local settings and delivery ledger are account-local, wiped with account, never sent to Hub.

## Architecture
- Prefer grouped immutable domain types `TaskDeadline` and `ReminderRule`, flattened persisted/wire fields per phase 01. Optional fields canonicalized once; one validator/calculator shared by UI/storage/native.
- All task-bearing tables gain metadata; migrated old live task has null deadline/token, disabled reminders, lead0/count1/intervalnull. Existing tombstones retain canonical null payload.
- Outbox gains contractVersion default v1 and dispatch-history/provenance. Existing DISPATCHING/UNCERTAIN/UNKNOWN_SUPERSEDED are historical; any state lacking proof of never-dispatch remains frozen, not inferred safe from PENDING alone.
- Intent mask derives from old intention and observed base where provable; uncertain/incomplete history flags require review. Persist full saved v2 intentions so later title/completion updates do not erase deadline fields.
- New account-local settings defaults: notifications off until user enables, new-task reminder default disabled, lead0/count1, date-only anchor09:00. Capability is refreshed from OS rather than treating persisted permission status as authoritative. Defaults change future tasks only.
- Ledger key account/generation/task/token/index; due time, claim outcome, posted platform ID and cancellation state sufficient for callback/restart/wipe recovery. Settings/ledger tables included in migration/export; no server-controlled data for local ownership.
- Schedule token generated on deadline/rule semantic change; title/completion do not replace it. Clearing deadline resets token. No task deadline means no scheduling records.
- Migration registered in existing builders on Android/JVM/controller paths; no fallbackToDestructiveMigration, no downward auto-migration.

## Related code files
Doggo root `/home/loidinh/WS/doggo`.
Modify `shared/src/commonMain/kotlin/dev/doggo/shared/contract/{todo-models,todo-action,todo-ui-state,account-store,strict-sync-contract}.kt`; data `{server-base-entity,outbox-entity,editor-draft-entity,scan-staging-entity,account-sync-meta-entity,doggo-dao,doggo-database,database-migrations,room-account-store}.kt`; shared Android/JVM `data/{android,jvm}-database-builder.kt`; desktop `main.kt` Room builder as required.
Create focused deadline/rule policy/calculator, local settings/ledger entities and DAO methods within existing `contract/` and `data/`; exported `shared/schemas/dev.doggo.shared.data.DoggoDatabase/2.json` alongside v1 export. Extend `shared/src/jvmTest/kotlin/dev/doggo/shared/data/room-account-store-test.kt`; create meaningful migration behavior suite in same test source set. No obsolete v1 export deletion.

## Implementation Steps
1. Add immutable grouped model and canonical flattened mapping; central checked schedule/consistency validation. Update all constructor/copy/save/toggle/delete/conflict callsites; use LSP references if available at execution.
2. Add persisted fields/provenance/masks/checkpoints/settings/ledger; write explicit SQL migration with canonical defaults/nullability/indexes.
3. Register migration in all builders and export Room schema using pinned toolchain; compare actual validation against migration, not source-text checks.
4. Make saved mutation transaction copy entire valid deadline/rule and only rotate token for semantic edits; draft-only updates cannot write eligibility/ledger.
5. Migrate real v1 fixture containing live/tombstone, draft, queued/uncertain/conflict operations and metadata; reopen on bundled SQLite/JVM and Android.
6. Update storage spec with migration/default/provenance guarantees after smoke evidence. v1 frozen op bytes/digest remain verifiable with v1 encoder.

## Todo list
- [ ] Domain/policy and all task-bearing projections updated.
- [ ] Room v2 entities/explicit migration/export/builders.
- [ ] Frozen provenance and intent change masks.
- [ ] Local settings/ledger and migrated-fixture restart smoke.

## Success Criteria
Future `JAVA_HOME=/home/loidinh/.local/share/jvm/jdk-21 ./gradlew :shared:jvmTest`; run targeted migration suite selected from implemented test class, then actual v1 DB→v2 open/save/restart scenario. Verify exact rows, revisions, queue ordering, epochs, original digests/drafts/tombstones and defaults, not only row counts. No deadline created by migration. Invalid save leaves state/schedule unchanged; clearing round-trips explicit nulls.

## Risk Assessment
New fields on full replacements can be dropped in one overlooked copy path; constructor/reference audit plus behavioral round-trip fixtures. Old PENDING may not establish historical certainty; classify conservatively.

## Security Considerations
Account key/generation partitions unchanged; ledger/local settings excluded from sync/backup and removed on wipe. No task text in OS handle or immutable PendingIntent data.

## Next steps
Phase 06 version transition and phase 07 draft-free scheduling consume completed storage. Phase 02 resolver proof required before final calendar behavior accepted.

## Unresolved questions
None; exact migration SQL/type schema generated during execution from frozen phase-01 contract.
