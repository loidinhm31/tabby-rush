# Persistence and sync research — reuse disposition
Date: 2026-10-13. Parent synthesis of earlier parallel persistence researcher and current Hub source scouts/reviewer; not a new agent run or runtime proof.

## Recommendation
Room KMP + bundled SQLite, isolated per canonical Hub origin and exact userId; durable base/outbox/drafts/staging/session state. OS-private database, not SQLCipher. Native protected token stores are separate. User selected offline edits, Android background sync and guarded wipe on logout/switch.

## Why Room
Shared Android/JVM DAO/Flow model and bundled SQLite avoid host SQLite version drift. SQLDelight is viable SQL-first alternative; choose one stack, not both. Exact stable Kotlin/Compose/AGP/Gradle/JDK/Room/KSP tuple must be proven in Phase02, not copied from an alpha documentation snippet.

## Selected strict per-record sync
- [Existing-source scout](../scout/scout-01-sync-protocol.md) describes legacy behavior only. User selected [strict A after feasibility](../reports/strong-sync-feasibility.md); current [contracts](../contracts.md) control.
- Doggo-only immutable opt-in mode,new strict routes,separate non-app-prefixed namespace. Existing embedded apps/shared clients unchanged. No legacy narrow-upsert patch now.
- Server-owned revisions,exact expected-base CAS,insert-only create,no update-upsert/implicit undelete. Permanent canonical-empty tombstones retain identity. Stable-rowId full scans,not timestamp cursor or snapshots; retain higher confirmations/scan-start identities.
- Durable frozen opId/digest/expectedRevision;explicit accepted/replayed only acknowledgement. Real metadata confirmation write for replay durability. No payload-equality receipt. Last-op superseded after uncertain dispatch->UnknownSuperseded review;no auto-rebase.
- Epoch everywhere,registry active check before namespace use;maintenance restore uses fresh namespace/epoch and explicit old-intent review. No transactional receipt/feed,snapshot or universal storage-loss claim.
- Real Mongo/index/journal/replay/legacy-bypass/old-client runtime gates remain mandatory.148h revised estimate,not runtime proof.

## Scheduling and leave
Foreground coordinator on every platform; Android same-process WorkManager unique one-shot plus periodic15min best effort. Durable DISPATCHING before socket, UNCERTAIN after interruption. Shared generation/refresh locks; edits arriving during worker drain cannot lose wakeup.
Guard counts drafts/pending/uncertain/conflicts: sync then leave, cancel, explicit discard. Freeze edits; persist generation+wipe marker outside DB; cancel/await work; close/wipe DB/WAL/SHM/cache/drafts; remove credentials/identity. Crash recovery before DB/vault startup; failed wipe fails closed. Normal app exit retains established account and offline work; logout does not revoke Hub sessions.

## Historical recommendations rejected
[Earlier persistence report](../../261013-1459-doggo-offline-accounts/research/researcher-02-persistence-sync.md) retains useful Room/atomicity references only. D1 receipt/feed/RESET_REQUIRED, field-merge/LWW, authoritative retention/default free-tier claims are not current contracts. Neither optimistic UI nor account filenames alone prove isolation.

## Primary references
- [Room KMP](https://developer.android.com/kotlin/multiplatform/room)
- [BundledSQLiteDriver](https://developer.android.com/reference/kotlin/androidx/sqlite/driver/bundled/BundledSQLiteDriver)
- [SQLDelight](https://cashapp.github.io/sqldelight/)
- [WorkManager persistent-work definition](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## Unresolved prerequisites
Stable toolchain tuple, isolated Hub/Mongo/current unique index, runtime regression and actual Android/background/vault/wipe evidence. All are future implementation gates; no implementation/install now.
