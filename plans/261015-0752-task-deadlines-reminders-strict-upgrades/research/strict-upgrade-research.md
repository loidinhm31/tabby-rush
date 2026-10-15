# Strict upgrade research

Date: 2026-10-15. Source: terminal report from `StrictUpgradeResearch`; parent verified schema admission and replay excerpts. Status: research complete; implementation/runtime not exercised.

## Current evidence
- Hub root: `/home/loidinh/WS/glean-hub`; paths below relative to it.
- `glean-hub-plugin/glean-hub-sync/src/models/strong_sync.rs:6-7,43-60,151-172,183-245`: fixed strict-v1 payload/digest; per-row lastOp.
- `glean-hub-plugin/glean-hub-sync/src/services/app_service.rs:102-158,435-477`: exact four primitive columns; ordinary schema update forbidden for strict apps.
- `glean-hub-plugin/glean-hub-sync/src/services/strong_sync_service.rs:358-393,615-638,905-950`: replay matches current row/lastOp and confirms with journaled conditional write. Subsequent writes replace lastOp; no full receipt history.
- `glean-hub-plugin/glean-hub-sync/src/middleware/dual_auth.rs:11-95,140-177`: app membership/API key for sync; global admin for management. App-scoped migration authorization not implemented.
- `glean-hub-plugin/glean-hub-sync/src/models/app.rs:29-88`, `models/api.rs:42-76`: syncMode/strictEpoch; no independent migration state.
- `glean-hub-plugin/glean-hub-sync/src/lib.rs:207-285`: existing route groups/legacy cleanup; no controlled migration worker.
- `glean-hub-app/src/services/sync-admin-service.ts:94-129`, `pages/admin/edit-app.tsx:1-127`, `components/organisms/schema-editor.tsx`: ordinary editor/update path.
- Doggo `shared/src/commonMain/kotlin/dev/doggo/shared/data/outbox-entity.kt:6-35`: frozen operation identity/payload/digest and uncertain/conflict states. `room-account-store.kt:118-203`: saved intentions coalesce only while pending.
- Doggo `scripts/hub-strict-recovery.js`: fresh-epoch maintenance recovery, not feature migration; do not use it for this upgrade.

## Recommended design
- Freeze explicit strict-v1/v2 schema pairs; canonical field presence and explicit nulls for v2. Never change v1 digest semantics.
- One app-scoped admin upgrade operation; same preconditions, idempotency and status for script/UI.
- Server-enforced durable maintenance gate across every instance; admitted requests drain before in-place transformation. Process-local mutex or expiring lease alone insufficient.
- Preserve exact collection identity, epoch, row IDs, revisions, tombstones and legacy lastOp. Default new live fields to null/disabled; tombstones remain empty.
- Durable phases fence→transform→validate→publish-last; retry resumes same migration. Unknown outcome remains fenced, not automatically unlocked.
- Reconciliation-only legacy receipt operation after v2 activation. Validate original v1 frozen digest; confirm matching current legacy lastOp without applying writes. Absence of historical receipt is not proof of nonacceptance.
- Never-sent safe intention can translate after scanning v2 base. Lost-response operation with current receipt can acknowledge. Superseded/unprovable legacy operation remains UNKNOWN_SUPERSEDED for explicit review; never automatically assign a replacement opId.
- Rebase changed intent fields over v2 base: blindly copying v1 whole rows would erase remote deadlines.

## Validation to implement later
- Disposable live MongoDB; two Hub instances; concurrent admission/fence/crash tests; interrupt before/after each checkpoint and final publish.
- Check IDs/revisions/tombstones/receipts with deterministic invariant fingerprints, not counts alone.
- Replay lost-response, superseded receipt, unchanged revision, absent create, repeated resolution and mismatched digest.
- Backend crate `cargo test --locked`; Hub UI `pnpm test:unit`, `pnpm build` (package.json observed); actual browser and script each invoke same operation on separate identical disposable fixtures.

## Unresolved questions
- Prove multi-instance permit drain and orphan-permit recovery on supported Mongo topology; exact algorithm is a phase-01/03 blocker, not an existing guarantee.
- App-scoped migration authorization ownership must be verified against current admin role model before route implementation.
- Full legacy receipt history cannot be reconstructed. Explicit conflict review is selected; no additional general receipt-ledger product is assumed.
