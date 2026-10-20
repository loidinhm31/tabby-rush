# Release & Operations Acceptance

Document Version: 2.3.0  
Last Updated: 2026-10-20  
Status: **Release remains blocked. Native acceptance is not passed. Production migration is not authorized.**  
Related Docs: [Standard Sync Design](./standard-sync-cutover-design.md) · [Deployment Guide](./deployment-guide.md) · [Security & Recovery](./security-and-recovery.md) · [System Architecture](./system-architecture.md) · [Android Acceptance](./android-acceptance.md) · [Linux Acceptance](./linux-acceptance.md) · [Windows Acceptance](./windows-acceptance.md) · [Project Roadmap](./project-roadmap.md)

---

## 1. Current Verdict

The current native client uses ordinary Glean Hub registration and standard sync. Strict-v1/v2/v3 is not the current binary protocol. Backend/API and standalone runtime evidence below is limited to disposable loopback/local services and does not approve a live migration or establish native UI/device acceptance.

**Do not state that TabbyRush native acceptance passed or that release is unblocked.** UI provider approval timed out twice and connected-device acceptance was not run. Production has not been migrated or authorized.

## 2. Current Evidence Matrix

| Area | Reported evidence | Acceptance boundary |
|---|---|---|
| Standard Hub API/browser smoke | Disposable MongoDB 27191, Hub 3100, admin UI 3102. Ordinary provisioning, standard create/pull/delta/update/stale-conflict/delete, and unknown JSON retention passed. | Local/disposable only; no production migration or authorization. |
| Schema concurrency/UI | Nullable `customNotes` pasted in admin UI; `schemaVersion` 1→2, no-op remained 2, stale changed save returned 409 with draft preserved and latest schema read-only. | Admin/API behavior only; no generated native UI/Room fields. |
| Windows standalone native runtime | Strict Gradle JavaExec standalone main passed against disposable loopback services: Windows DPAPI, real HTTP auth, Room offline restart, two independent stores using actual coordinator push/pull/delta, schemaVersion 2, unknown nested JSON and nullable `customNotes` retained through native edit, parent/child sync and group deletion. | Runtime harness only; not an interactive Windows UI test, connected-Android proof, or cross-platform physical acceptance. |
| Offline Gradle/package suites | Parent reports current strict Gradle suites and Android/Desktop package outputs passed. | Build/test/package success does not establish native UI or device acceptance. Exact suite counts are maintained in parent verification reports. |
| Conversion operator | Parent reports 7/7 disposable-Mongo operator checks passed, including concurrency, deadline, and recovery. | Test evidence only. Operator does not authorize live production use. |
| Interactive native UI | Provider approval timed out twice. | Not run; native acceptance not passed. |
| Connected Android/device matrix | Connected-device acceptance was not run. | Not run; native acceptance not passed. |
| Production cutover | No production migration has occurred or been authorized. | Requires separate explicit approval and operator/backup/isolation gates. |

## 3. Current Release Criteria and Holds

A release decision requires, at minimum, review of current platform-specific acceptance documents, a successful interactive/native UI approval path, required connected-device verification, dependency/security review, and a separately authorized deployment decision. This document does not grant that authorization. Package creation and standalone harness execution are insufficient substitutes for connected-device or interactive acceptance.

Before any live standard-sync conversion, require separate explicit approval naming the exact Hub origin/database/app, backup/restore scope, external isolation plan, and maintenance window. The operator claim is durable and has no automatic expiry/takeover. After any failed mutating run, recover only through `maintenance.executionRecovery` with exact `expectedExecutionId`, `executorStopped: true` plus `executorStopEvidence`, `mongoOperationsDrained: true` plus `mongoDrainEvidence`, and `authorizedActions` containing `recoverExecution`. Renew and verify the maintenance window before each collection rename and registry CAS. See [Deployment Guide](./deployment-guide.md) and [Security & Recovery](./security-and-recovery.md). A live cutover still requires its own approval.

## 4. Historical Strict-v3 Acceptance Snapshot

**Historical record, 2026-10-20 and earlier; superseded for current protocol and not a release decision.** Earlier Phase 06/strict-v3 material described parent/child and deadline features, strict schema fingerprints/receipts, strict-v2/v3 upgrade tooling, and Gates G1–G7. One earlier Cycle 2 QA report claimed 749 verification targets across 15 groups, including one ignored live-Mongo test and Gradle execution with dependency verification disabled. Those counts and strict gate statuses describe that historical run only; they do not represent current standard-sync test counts, current native UI/device acceptance, or production readiness.

Former strict-v2 and strict-v3 runbooks have been retired; strict-specific scripts named by historical material have been removed. Consult [Project Roadmap](./project-roadmap.md) for current planning status; this release document intentionally does not duplicate roadmap tracking.
