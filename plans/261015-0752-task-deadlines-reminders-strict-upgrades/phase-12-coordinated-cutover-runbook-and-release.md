# Phase 12 — Coordinated cutover, recovery runbook and release documentation

## Context links
[Parent](plan.md) · [Backend migration](phase-03-hub-versioned-contract-and-migration.md) · [Operator surfaces](phase-04-hub-admin-ui-and-upgrade-script.md) · [Acceptance gate](phase-11-adversarial-integration-and-platform-acceptance.md) · [Deployment guide](../../docs/deployment-guide.md) · [Recovery constraints](../../docs/security-and-recovery.md)

## Overview
Date 2026-10-15. P1. Implementation pending 0%; review pending. Estimate 12h. Owners: Hub operator + Doggo release/integration lead. Depends on phases 01–11 and G6. Gate G7: verified target activation and release evidence; operator authorization required before actual production mutation.

## Key Insights
Deploying schema additions alone is unsafe. Existing fresh-epoch recovery is not a feature upgrade/rollback. Offline old clients cannot all be drained. Updated clients must preserve local intentions across the supported transition.

## Requirements
- Coordinated versioned server/client/operator deployment, backups and explicit recovery at every state.
- Same server operation available through UI and script; no direct registry edit/Mongo transformation in operator clients.
- No release before separately observed platform/security/dependency/signing gates pass.
- Docs distinguish delivered implementation, runtime acceptance and unresolved operator prerequisites; do not inherit old phase100% as this feature's acceptance.

## Architecture
### Deployment order
1. Approve reviewed contract/vectors/gates and collect release/artifact digests. Inventory all Hub replicas, ingress/maintenance paths, Mongo topology, supported client versions and target app scope.
2. Take restorable journaled backup of registry/app records+collection/indexes; verify restore on isolated storage. Record invariant fingerprints/expected v1 version/schema/epoch and authorization without secrets.
3. Deploy fence-aware version-capable Hub binary to every instance; keep app strict-v1. Confirm old binary cannot serve/write target collection and legacy cleanup is isolated. Observe v1 compatibility/receipt behavior before target upgrade.
4. Publish updated Doggo binaries containing Room/protocol transition and new metadata support. While app v1, ordinary representable v1 edits work; deadlines clearly blocked. Do not require every offline client to return before upgrade.
5. Start controlled upgrade through script OR UI with one durable migration ID and expected source state. Server fences new traffic and drains admitted requests. Client timeouts query same ID; no independent schema PUT or fresh epoch.
6. Follow operation through transform/validate/publish. `operatorRequired` retains fence; investigate safe persisted state and drain proof. After atomic target publication, GET verifies exact v2 schema/version and unchanged epoch/collection identity/protected fingerprints.
7. Confirm updated client v2 bootstrap, safe legacy receipt reconciliation and offline intentions; old binary gets upgrade-required with no local deletion. Observe native notification on a benign locally known task per platform; verify local toggles/permissions.
8. Observe post-cutover CAS/scan/replay/metadata smoke; retain backups/checkpoints per documented operator retention window. Enable feature release only after final status and native acceptance evidence; archive operation ID/results.

### Recovery rules
- Before fence acquisition: rejection means unchanged app; correct authorization/preconditions, no destructive retry.
- Fencing with admitted/orphan permits: stop/drain old owners and prove Mongo commands terminated; never clear permit by TTL or blindly start another runner. If proof unavailable stay fenced.
- Partial transform/validate: same migration ID resumes idempotently. Preactivation rollback under fence removes ONLY migration-added fields and verifies original protected snapshot; publish ready-v1 last after exact validation.
- Lost activation response: reread migration and registry; target present means verify/resume completion, not revert. Version/schema disagreement leaves maintenance and operator resolution.
- After any v2 write: forward repair under fence. Do not restore v1 backup/downward migrate or run fresh-epoch recovery; it would erase new synchronized/offline intentions. Disaster restore is separate incident decision, not routine feature rollback.
- Client binary/Room rollback unsupported; preserve account data and offer upgraded repair build. Protocol error never automatically wipes database/outbox.

## Related code files
Modify Doggo `/home/loidinh/WS/doggo/README.md`, `docs/{project-overview-pdr,system-architecture,local-storage-and-sync,hub-schema-integration,sync-acceptance,android-acceptance,linux-acceptance,windows-acceptance,deployment-guide,security-and-recovery,project-roadmap,project-changelog}.md`, relevant desktop guides/release acceptance; update package description/version via existing release convention.
Modify Hub `/home/loidinh/WS/glean-hub/docs/doggo-strict-sync.md`, relevant deployment/recovery/docs CHANGELOG entries. Prefer existing docs; create focused feature migration runbook only if existing deployment/recovery docs exceed readable scope. Never replace current recovery command with same-name feature upgrade semantics.

## Implementation Steps
1. Convert tested migration state recovery into executable operator runbook; document preconditions, commands/API body and evidence location. Read actual new CLI help before writing final examples.
2. Verify signed Android package and Windows release signing/dependency/installed acceptance, Linux package/runtime; no verification-off diagnostic counted as production gate.
3. Rehearse complete deployment/backup/restore/preactivation rollback/response-loss resume on disposable populated app using same API semantics as production.
4. Obtain operator execution approval and app-scoped admin access; perform the deployment order above without arbitrary schema/epoch edits.
5. Execute production-scoped benign readback/CAS/scan/receipt checks and native platform release smoke; record operation ID, final contracts, data preservation and limitations.
6. Update roadmap/changelog/specs/evidence only to actual implementation/acceptance state. Remove proof scaffolds and obsolete unversioned schema assumptions/callers; retain only required historical v1 digest/fixture support.

## Todo list
- [ ] Verified backup/restore and every migration-state recovery procedure.
- [ ] Fence-aware all-node deployment and client publication.
- [ ] Same-operation rehearsed/approved cutover and target readback.
- [ ] Actual feature/security/platform docs, changelog and final release evidence.

## Success Criteria
Rehearsal and approved cutover preserve collection/epoch/IDs/revisions/tombstones/replay receipts; G6 platform evidence and release security/dependency/signing prerequisites complete. Docs describe actual behavior and limitations. All affected callers/specs/fixtures updated; no deprecated aliases or registry-only migration path. If operator execution unavailable, release gate explicitly blocked rather than claimed done.

## Risk Assessment
Production restoration can lose post-cutover writes: forbid automatic downgrade and prioritize forward recovery. Don't interpret old clients failing v2 as data loss; their local intentions remain pending for upgraded reconciliation.

## Security Considerations
Backup access controlled; tokens via protected environment/credential custody, no plans/logs. Explicit operator app authority, no ordinary token upgrade. Sensitive notification content removed during account wipe where platform allows.

## Next steps
Mark feature implemented/accepted only after G7; otherwise preserve exact pending gate/evidence. No unrelated feature follow-up hidden in this release.

## Unresolved questions
Operator window, deployed topology/replica inventory and release signing/runtime access are execution prerequisites. No date or reduced-platform scope assumed.
