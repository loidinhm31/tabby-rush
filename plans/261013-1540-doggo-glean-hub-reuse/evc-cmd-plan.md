# EVC command plan index — Doggo isolated strict sync
Date:2026-10-13. [Main plan](plan.md) · [Contracts](contracts.md) · [Ownership](file-ownership.md) · [Dependencies](dependency-matrix.md).
User selected strict A;planning revision only,all implementation pending0%. Admin warning/local-only logout confirmed. No installation/build/source implementation or runtime verification now.

## Phase index
| Phase | Group | Status | Progress | Estimate | Detailed link |
|---|---|---|---|---|---|
|01|A|pending|0%|6h|[headless-tooling](phase-01-headless-tooling.md)|
|02|B|pending|0%|12h|[frozen-foundation](phase-02-frozen-foundation.md)|
|03|C|pending|0%|40h|[isolated strict server](phase-03-hub-schema-compatibility.md)|
|04|C|pending|0%|12h|[auth-session](phase-04-auth-session.md)|
|05|C|pending|0%|28h|[local strict sync](phase-05-local-sync-state.md)|
|06|C|pending|0%|12h|[compose-ui-theme](phase-06-compose-ui-theme.md)|
|07|D|pending|0%|14h|[android-integration](phase-07-android-integration.md)|
|08|E|pending|0%|10h|[android-release-operations](phase-08-android-release-operations.md)|
|09|F|pending|0%|8h|[linux-distribution](phase-09-linux-distribution.md)|
|10|G|pending|0%|6h|[windows-distribution](phase-10-windows-distribution.md)|

## Execution groups
- A01:SSH Xpra/Studio/SDK/AVD visual environment first;setup unchanged.
- B02:stable tuple/disposable proof/frozen DTO,status,epoch,digest,factories/build/ownership.
- C03–06:independent strict server/auth/local persistence/UI coding;tests each lane owned.
- D07:one coordinated lane suite plus actual Android strict/WorkManager/offline/isolation/reset gates.
- E08:operator Hub version/provision/admission/live/recovery;signed installed HTTPS Android.
- F09:Linux Secret Service/target inclusion/lock/installed RPM in Xpra;shared strict parity.
- G10:manual Windows DPAPI/target lock/installed MSI;shared parity,no routine09 file edit.
DAG01→02→{03,04,05,06}→07→08→09→10 unchanged12edges;[matrix](dependency-matrix.md).

## Gate navigation
03:deterministic real Mongo exact CAS/replay REAL nonce confirmation/journal/tombstone/epoch/index gate,actual mounted routes/bypass/previous Hub binary and unchanged legacy/embedded clients. Missing Mongo cannot silently skip. No shared-client or legacy upsert repair.
07:actual Gradle/instrumentation/ADB/fault-proxy commands;lost response→immutable retry/explicit acceptance or UnknownSuperseded,monotonic rowId scan promotion/no equality ack,epoch/no fallback,auth/vault/background/wipe/native UI.
08:strict-apps old-binary404/405/read-back;capable version/index/journal/signing/installed Android. Owned `mongosh --nodb --file scripts/hub-strict-recovery.js`,protected env/noURIargv,quiescence/newnamespace/freshepoch/importrev1lastOpnull. Shipped03 read-only SAME-validator helper preflights inactive candidate before journaled registryCAS;ambiguousCAS trafficblocked+authoritative pointer reconciliation,postCASreadmission/oldepoch409 before reopen. No old namespace drop/invented receipts.
09/10:native target build/installed provider+vault/package lifecycle and strict protocol parity;Android→Linux→manual Windows order.02 records actual tuple-dependent tasks rather than guessing plugin support.
Failures return exclusive owner;config/locks/schema/tests/docs no overlap.08 alone revisits general docs after desktop evidence. Future suites run at coordinated handoff gates,not concurrently midflight. [Final planning validation passed](reports/strict-plan-review.md);runtime gates unexecuted.

## Estimates and evidence
148h=6+12+40+12+28+12+14+10+8+6 (+48h superseded baseline),±25–35%;ideal critical96h=6+12+40+14+10+8+6 excludes waits/rework.215 exclusive planned paths;existing embedded/shared-client modification effort zero.
[Selected feasibility](reports/strong-sync-feasibility.md) · [Strict design](reports/strong-sync-design-feasibility.md) · [Source isolation](reports/strong-sync-isolation-feasibility.md) · [Mongo evidence](reports/mongodb-feasibility-evidence.md) · [Strict review](reports/strict-contract-review.md) · [Completion](reports/strict-plan-completion.md). Source analysis not runtime proof;historical baseline reports superseded.

## Unresolved prerequisites
Approved Hub/Mongo topology/journal/index/revision/operator recovery/registration/grants,old binary/legacy-client fixtures,Xpra permissions,stable tuple,font/vault/signing,Windows host. Strict A/admin warning/local-only logout already resolved;no fallback or automatic rebase. No receipt ledger/feed/snapshot/transaction/GC introduced.
