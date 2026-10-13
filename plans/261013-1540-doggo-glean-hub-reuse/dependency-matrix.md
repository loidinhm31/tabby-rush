# Coding and runtime dependency matrix

Date: 2026-10-13. Planning only; all implementation pending0%. [Plan DAG](plan.md) · [Ownership](file-ownership.md) · [Contracts](contracts.md).
Evidence:[selected A](reports/strong-sync-feasibility.md),[strict design](reports/strong-sync-design-feasibility.md),[source isolation](reports/strong-sync-isolation-feasibility.md),[Mongo](reports/mongodb-feasibility-evidence.md),[strict review](reports/strict-contract-review.md). Parent owns matching architecture/contracts/decisions and final validation.

## Execution edge list
| Prerequisite | Consumer | Gate | Meaning |
|---|---|---|---|
|01|02|Host/native visual access|First implementation always tooling; current supported tuple proof needs actual environment.|
|02|03|Frozen strict protocol/digest/config/registry|Opt-in immutable mode,isolated strong server routes/storage/admission;legacy apps unchanged.|
|02|04|Frozen auth/vault/transport contract|Auth lane needs no concrete data/UI/server-change imports.|
|02|05|Frozen strict DTO/digest/epoch/status/store/auth/scheduler/state|Local strict sync codes against interfaces,not waiting04 implementation.|
|02|06|Frozen public UI state/actions/auth callback|UI codes against contracts, not Room/Ktor/auth internals.|
|03|07|Strict source/tests complete;actual server gate passing before device sync|Deterministic real Mongo,durable confirmation,route/bypass/reset/index gates,actual old binary and unchanged legacy-client smoke;no skipped proof.|
|04|07|Auth+Keystore source/tests complete|Real single-flight auth and protected credential implementation injected.|
|05|07|Room/sync/lifecycle source/tests complete|Real DB/outbox/scans/leave/generation injected.|
|06|07|UI/resources/tests complete|Actual common UI injected; font audit resolved.|
|07|08|Actual Android local/debug suite+runtime gates passed|Authorized deployed release may start only after integration.|
|08|09|Signed installed Android+deployed HTTPS+recovery gates passed|Linux native gate next, not parallel platform release.|
|09|10|Installed Linux RPM/provider+target inclusion mechanism passed|Manual Windows native gate last; no Linux MSI substitute.|

## Coding versus runtime
| Lane | Coding prerequisite | Runtime dependencies | Execution owner |
|---|---|---|---|
|03 strict server|02 contracts/digest vectors only|Real isolated Hub/Mongo,topology/journal/index/operator/two accounts/actual old binary and sanitized existing clients|03 deterministic server/mounted-router/bypass/old-binary regression;07/08 consume owned scripts/evidence.|
|04 auth|02 interfaces only|Existing real Hub auth,/me/access approvals; Android Keystore device|07 coordinated common/Keystore execution,live07/08; desktop vault09/10 later.|
|05 data/strict sync|02 interfaces only|Concrete auth04;strict admission/server03;Room tuple02;scheduler07/09|07 coordinated common/JVM/Android Room and actual strict/background/isolation/epoch gates.|
|06 UI|02 immutable public contracts only|Real state05/auth04/resources02;native platform surfaces|07 common semantic/native Android;08 release;09 Linux;10 Windows.|
|07 composition|03–06 source handoffs|All03–06 real implementations plus native01/device|One integrator executes full required suites after handoffs; tests written-but-unrun not accepted.|
|09 desktop|08 release;frozen02/04/05/06|Secret Service,user D-Bus,Xpra,RPM,JVM engine|09 Linux native tests/runtime;no Windows class/service file needed.|
|10 desktop|09 target/build/SPI frozen|Actual Windows,JDK/WiX,DPAPI,operator test users|10 Windows provider/descriptor/test/custom lock only;02 verification owner may reopen.|

03/04/05/06 have **no cross-lane coding edges**. Runtime wiring is not permission for fake implementations or for consumers to edit providers. Contract defects return02; tests belong their lane,not duplicate module-test phases.

## Command gates and owner re-entry
-01 executes actual SSH Xpra/Studio/AVD/ADB visual setup.02 executes isolated disposable stable tuple proof,freezes actual Gradle task mappings and catalog;final shared KSP configuration may await05 source.
-03 executes deterministic fail-closed real-Mongo strict CAS/replay/durable nonce/tombstone/epoch/index and mounted-router/bypass tests plus actual old-binary/legacy-client smoke. Missing/unreachable Mongo fails required suite; existing optional tests cannot substitute. Production deployment/registration waits authorized08.
-07 runs tuple-mapped `:shared:allTests`,shared Android instrumented Room/Keystore suites,`:androidApp:assembleDebug`,`:androidApp:connectedDebugAndroidTest` in a single coordinated batch after lanes. No concurrent build/lint/formatter/test storms.
-08 runs authorized capable-version/read-back/admission/legacy-client/live strict smoke,signing/APK security/installed HTTPS Android and isolated quiescent fresh-epoch Mongo recovery. No old-epoch reopening,inverse receipt reconstruction,Worker/D1 or invented deploy command.
-09/10 run selected target strict lock/test/createDistributable/packageRpm or native packageMsi,then installed provider/vault/UI gates. Early09 proof validates Linux build with Windows sources/lock absent;10 validates opposite source/resource inclusion on Windows.
-02 alone maintains catalog/shared lock/verification as legitimate later dependencies resolve;07 Android lock,09 Linux desktop lock,10 Windows desktop lock.09 configures custom lock path once so10 writes no09 file.
-08 alone updates its README/PDR/summary/roadmap/changelog after09/10 terminal acceptance. These doc-update edges do not restart downstream coding or transfer ownership.

## Estimated effort versus elapsed
Fixed estimates **148h** =01(6)+02(12)+03(40)+04(12)+05(28)+06(12)+07(14)+08(10)+09(8)+10(6); +48h versus superseded baseline. Uncertainty ±25–35%,not measured effort/calendar promise. Ideal unlimited-staff/no-wait critical dependency path **96h** =6+12+max(40,12,28,12)+14+10+8+6. External operator/hardware waits,rework/topology migration or legacy data import excluded;no effort to modify existing embedded apps/shared clients.

## Unresolved questions
Approved reachable Hub/Mongo topology/journal/version/index/operator provisioning/recovery/access approvals,old-binary/legacy-client fixtures,Xpra client/package permissions,stable tuple,vault/font/signing and Windows host remain gates. Strict A/compact permanent markers confirmed;historical UnknownSuperseded/non-snapshot scans/storage-loss and operator reset boundaries remain explicit,not a pending A-versus-B decision.
