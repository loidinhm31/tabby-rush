# Final integration verification — 2026-10-20

## Scope and isolation

Source: `G:/ws/sharing/doggo` and sibling `glean-hub`. Execution: sanitized mirrors under `G:/ws/standard-sync-verification-261020`; no original dotenv/keystores copied. Java 21.0.9, Windows x64, Node 24.21.0. Tests used a new official MongoDB 8.0.34 portable single-node replica set, loopback 27191, and a fresh `test_standard_sync_surface_261020` database, Hub 3100/admin UI 3102. All credentials/accounts were newly generated test-only values.

No live database conversion, original-service restart, normal installed-app data mutation, staging, commit or push. Initial failing reports remain historical evidence; the results below supersede their current acceptance state.

## Automated verification

| Exercised target | Result |
|---|---|
| Shared JVM | 264 passed, 0 failed/skipped |
| Desktop JVM | 131 passed, 0 failed/skipped |
| Android debug unit | 50 passed, 0 failed/skipped |
| Android debug APK | Built; merged manifest package `dev.tabbyrush.android.standardsmoke` |
| Android instrumentation APK | Built, not installed/executed |
| Windows desktop `createDistributable` | Built in mirror |
| Affected Rust plugin/client/engine | 83 passed, 1 pre-existing strict live gate ignored |
| Hub workspace `cargo check --workspace --all-targets` | Passed |
| Hub admin unit suite | 22 passed, 0 failed |
| Hub admin TypeScript/Vite production build | Passed; final rebuild 15.10s Vite / 31.42s total |
| Node proxy/version/security suites | 43 passed, 0 failed |

Native command, default strict artifact verification explicitly retained:

```text
gradlew.bat :shared:jvmTest :desktopApp:test :androidApp:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest :desktopApp:createDistributable -PdesktopTarget=windows -PisolatedTestConfig=true -PhubOrigin=http://127.0.0.1:3100 --max-workers=2 --dependency-verification=strict
```

Result: BUILD SUCCESSFUL, 1m6s. JUnit XML inspected across all 65 suites. Real SQLite migrations exercised exports 1/2/3→4 and restart, unusual SQLite storage-class evidence, rollback/atomicity, acknowledged-only draft clearing, stale-scan restart, unknown intent retention, latest-draft copy, byte-budget splitting and account fences. Room compiler generated actual v4 export; copied to source, not fabricated.

Rust command (explicit disposable URI, `MONGO_TEST_ALLOW_DISPOSABLE=1`, exact host allowlist `127.0.0.1:27191`):

```text
cargo test -p glean-hub-sync-engine -p glean-hub-sync-client -p glean-hub-sync --all-targets --features glean-hub-sync/mongo-integration-tests -- --test-threads=1
```

83 passes = 66 plugin + 5 schema-version real-Mongo integration + 7 cutover real-Mongo integration + 4 client + 1 engine. Cutover cases exercised actual binary dry-run/refusals/prepare/publish/repeat/standard roundtrip; explicit prepublication rollback; durable-stage recovery; invalid mixed/duplicate/owner/tampered evidence refusals; concurrent same-ID publish/resume/rollback exclusion; exact stopped-and-drained claim recovery; maintenance expiry at every rename/CAS boundary. Existing strict live acceptance gate remains ignored; local tests do not close production/multi-node gates.

Dependency verification: 50 new SHA-256 entries authenticated against independently retrieved official Google/Maven Central bytes and published sidecars/module records; local bytes matched. No verification bypass. See `dependency-verification.md`. Existing metadata/provenance and Glance release holds are not blanket-cleared.

Node command:

```text
node --test scripts/debug-fault-proxy.test.mjs scripts/bump-version.test.mjs scripts/release-security-check.test.mjs
```

Admin unit runner completed 22 passes after replacing an unsupported TypeScript constructor parameter property with an explicit field. Final `pnpm --dir glean-hub-app build` passed TypeScript and Vite (15.10s Vite, 31.42s total). Existing font/chunk warnings remain non-fatal.

## Real API and admin browser smoke

- Real registration/login created isolated admin/user accounts; grants/bootstrap only in the disposable DB.
- Ordinary provisioner issued/persisted a one-time key and GET-verified version 1, no strict metadata. Final Windows custody checks require a protected private parent before file creation and protect the new file before POST. Positive fresh-app issuance passed; unprotected parent refused with no file and GET 404 for that new app.
- `hub-live-smoke.mjs` against the real Hub passed create/pull/delta/update/stale-assumption conflict/delete, exact acknowledgments, unknown nested JSON retention and response schemaVersion.
- Actual rendered console: admin login; open TabbyRush schema; Paste JSON nullable `customNotes`; Update Schema moved 1→2 and showed authoritative PUT plus GET readback. Unchanged save stayed 2.
- Second browser tab loaded at 1; distinct `staleDraftOnly` change returned real 409. Raw draft remained intact; View Latest showed separate read-only version 2 without replacing it. Explicit discard confirmation/reload adopted 2. Screenshots observed. No mocked routes, fake responses, token injection into browser storage, or permanent browser suite.
- Browser tabs closed after observation.

## Real native runtime smoke — not a mock/JUnit test

A temporary standalone Kotlin `main`, compiled/run with strict dependency verification in the mirror, used production `WindowsDpapiVault`/JNA DPAPI, `HubAuthSession`, `RoomAccountStore`, `HubSyncClient` and `AccountSyncCoordinator` against the actual isolated Hub:

1. Login two independent native account stores with actual encrypted credential custody.
2. Save an offline root, close/reopen Room, then synchronize A→server→B.
3. Observe persisted `hubSchemaVersion=2`.
4. Write an opaque nested JSON value plus nullable `customNotes`, pull, edit a known title through the native store/coordinator, verify both unknown values survive on B.
5. Create/synchronize a child; delete the root/group; verify both server tombstones and absence from B's visible tasks, no unresolved outbox.

Observed output: `PASS real native runtime: DPAPI/auth + Room offline restart + two-store standard sync + schemaVersion2 + unknown JSON preservation + parent/child + group deletion`; BUILD SUCCESSFUL in 6s. Temporary account files were removed in `finally`; no installed profile used. Temporary main/Gradle launcher are cleanup-only, not permanent tests.

## Review and unresolved acceptance boundaries

Final `evc-code-reviewer` reports `review-native-final.md` and `review-hub-ui-final.md`: no remaining actionable findings in reviewed source. Five native safety findings, operator concurrency/deadline findings and Windows credential custody race resolved. Source review is not runtime proof; evidence above supplies exercised paths.

**Interactive native UI and connected Android acceptance remain held.** Required `computer.control.acquire` twice timed out after 120s; state stayed inactive. No bypass through adb/UI automation, normal app launch/profile or alternate desktop mechanism. Android/Desktop rendered create/edit/delete, launcher/shade/tray/reminder delivery and physical/multi-device acceptance are not claimed. Native runtime smoke proves actual core/network/storage behavior, not these surfaces.

Production cutover still requires explicit target/backup/maintenance authorization, operator restore rehearsal, stopped nodes/jobs and external isolation. Existing strict data is not automatically converted on deployment. Unknown/uncertain legacy operations retain evidence for review; no preservation guarantee depends on silently replaying them.

## Cleanup

Stopped only the three newly created verification services: Hub 3100, admin 3102 and MongoDB 27191. Closed managed browser tabs and released computer control. Removed the temporary native main/Gradle launcher, account/bootstrap helpers, generated test credential/config files and service launchers; retained isolated build artifacts and test-database files as non-running verification output. Normal app profiles, original Hub services and production configuration were untouched.

Documentation exception: the existing credential guide was intentionally left unread/unchanged after a privacy-approval gate; README points to the current API contract for routes. The docs agent unintentionally regenerated tracked `repomix-output.xml` outside ownership; preflight showed it unchanged, so the parent restored that file from HEAD. No generated snapshot change is retained.
