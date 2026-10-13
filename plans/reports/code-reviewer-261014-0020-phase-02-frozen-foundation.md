# Code Review: Phase 02 Frozen Foundation (37 files)

Date 2026-10-14 · Plan `plans/261013-1540-doggo-glean-hub-reuse/phase-02-frozen-foundation.md` · Score **5/10**

## Scope
37 owned files (root Gradle, shared build, 16 contracts, 4 tests, 5 docs) + `tests/test-phase-02-foundation.sh` + QA report. Static review; Gradle/Kotlin NOT runnable on host (only JDK 25 present, no `~/.gradle`, no Android SDK).

## Critical
1. **Wrong wrapper distribution SHA-256.** `gradle-wrapper.properties` pins `c11a684f…f515d`. Official `gradle-8.11.1-bin.zip.sha256` (fetched from services.gradle.org) = `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. First `./gradlew` run fails "Verification of Gradle distribution failed". Whole build blocked. Same wrong hash copied to `docs/foundation-evidence.md` §2.1 ("Official") and asserted by `tests/test-phase-02-foundation.sh` (test greps for the wrong value → tautology, passes while broken).
2. **Wrong wrapper-jar hash in docs.** Evidence says `96de2340…`; actual `sha256sum gradle-wrapper.jar` = `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`. "Proven hash constants" claim false.
3. **No build/test ever executed; evidence overclaims.** Plan step 2/7/8 required disposable-KMP compile proof, `:shared:allTests`, strict locking/verification. None run (QA report admits shell/static only). `foundation-evidence.md` claims "test vectors matching Rust backend specifications" — none exist in tests. Docs say "Verified" using URL lists only. Phase must not be marked accepted.
4. **Dependency locking/verification not enabled (plan step 7, security).** `verification-metadata.xml`: `verify-metadata=false`, `verify-signatures=false`, zero components. `shared/gradle.lockfile` = `empty=` and no `dependencyLocking { lockAllConfigurations() }` anywhere → lockfile inert. Zero supply-chain protection while docs imply it.
5. **Digest/SHA-256 has no known-answer tests.** `strict-digest-test.kt` only checks determinism, length-64, distinctness. No NIST vectors (""/abc/448-bit), no padding-boundary lengths (55/56/63/64), no fixed expected digest, no cross-language/Rust vector, no overflow/UUID/unknown-status tests (plan step 6 mandates). A wrong byte order would pass. (My independent Python port of the Kotlin algorithm matched `hashlib` for lengths 0–199,1000 and K table is correct, so impl itself looks right — but nothing in repo proves it.) Reference value for fixture (computed from doc layout; userId `65a123456789abcdef012345`, create/rev 0, data id f47ac10b…, title "Buy groceries", completed=false, createdAt 1791840000000): `69dfe7ff807736afcd95f303e97abc60a096d25a8bffcfbd99592b77df4fb865`. AccountKey("https://hub.example.com","user-uuid-1234") = `d7e01d886a17607df074104923d1cb17567e162e1de8f649d9ed300b182862f0`. Unverified against Rust backend → `[INFERENCE]`; confirm vs backend before freezing.

## Warnings (High/Medium)
1. **`implementation` hides public API types.** `shared/build.gradle.kts` commonMain uses `implementation(kotlinx.coroutines.core)` but `AuthSession`/`SyncCoordinator` expose `StateFlow`. Consumers (`androidApp`, `desktopApp`) won't see it on compile classpath → must be `api(...)`.
2. **Stable-tuple requirement violated.** Plan: "do not pin alpha by habit". Room `2.7.0-alpha12`, sqlite-bundled `2.5.0-alpha12`, security-crypto `1.1.0-alpha06` are alphas; doc admits no disposable compat proof. [INFERENCE: stable Room 2.7.x/sqlite 2.5.x exist by 2026; security-crypto deprecated upstream — verify].
3. **`StrictSyncDigest.computeDigest` silently accepts invalid combos.** CREATE/UPDATE with `data=null` → empty payload (collides with DELETE-shaped bytes); DELETE with non-null data ignores data. `expectedRevision`/`createdAt` negative or > 2^53 unchecked; `domain` overridable param weakens domain-separation. `StrictOperation` has no init invariants. Add `require`s (plan step 5 "safe-integer bounds, epoch UUID").
4. **EndpointPolicy host validation too lax.** No validation of host/port chars: accepts `https://hub .com`, `https://host:abc`, `https://host:99999`, backslash (`https://a\b` — parsers treat `\` as `/`), trailing dot, default `:443` not stripped (same server → two canonical origins → two AccountKeys). No tests for these.
5. **HTTP-status mapping not frozen.** Plan step 5: 503 notAdmitted, 409 epochMismatch→ResetRequired, 426/404/405→ProtocolIncompatible, 400 invalidRequest, UnknownSuperseded, retry/replay allowlist. Absent from `api-contract.md` and code. Also `StrictOperationStatus.fromWireName` returns null on unknown with no documented handling.
6. **api-contract.md ambiguity.** Uses `u32_be(origin.length)`; code uses UTF-8 **byte** length. State "byte length". Example digest `e3b0c442…b855` in push response = SHA-256 of empty string → misleading.
7. **Redaction untested.** No test asserts `CredentialBundle.toString()`/`HubConfig.toString()`; QA only greps the literal string. `CredentialBundle.hashCode` derives from raw token hashes; `equals` non-constant-time (low risk). `HubConfig` is a data class: `copy()/component3()` expose key; no init validation of origin/key blank.
8. **PII in default toString.** `AccountIdentity` (email), `AccessRequestInfo.message`, `Todo.title`, `TodoUiState`, `SafeError.message` print raw. Code-standard "never print credentials" fine, but add PII rule / redact `AccountIdentity.email`. `SafeError.message` has no "never carry server text/secrets" guard.
9. **Plan deliverables missing in shared/build.gradle.kts:** compose resources `packageOfResClass=dev.doggo.shared.resources`; androidInstrumentedTest/jvmTest roots; Room schema dir exists but no gate documented. Unused deps (YAGNI): datetime, serialization plugin, ktor, room, compose.material3, `coroutines-swing` (desktopApp concern), activity-compose, lifecycle — none used by Phase 02 sources.
10. **Docs misstate architecture.** `system-architecture.md` says shared is "Pure KMP library" while commonMain depends on Compose/Room/Ktor (only *signatures* are pure). Foundation-evidence/api docs hardcode absolute `/home/loidinh/WS/doggo` (toolchain-versions §3). Toolchain says JDK 21 required; host has only JDK 25 (no toolchain resolver configured, no `foojay`/`jvmToolchain`) → build will fail on host without JAVA_HOME setup.
11. **QA suite is mostly vacuous.** 56 asserts = file existence + greps; "functional" Node block tests Node's own crypto/regex, not Kotlin. Passes with items 1–2 broken. Don't count as acceptance.
12. **`.gitignore` gaps:** `.kotlin/` (Kotlin 2.x session dir), `*.pem`/`*.key`, `google-services.json`, `*.hprof`. Low.
13. `gradle.properties`: `kotlin.mpp.enableCInteropCommonization` (no native targets) and `kotlin.mpp.androidSourceSetLayoutVersion=2` (default/deprecated) are noise; `configuration-cache=false` undocumented.

## Suggestions (Low)
- TitlePolicy: boxed `MutableList<Int>` + `Set<Int>` per call, two passes, no early reject for huge input (cap before scan). Single pass with Int arrays/`when` ranges; use `String.codePointCount` equivalent via loop only.
- SHA: `padded` second full copy, `listOf(...).forEach(::writeStr)` and captured-`var` closures allocate; `v.toString(16)` per byte. Fine for tiny inputs; use hex lookup table + incremental block processing if reused for big data. Alternatively `expect/actual` to platform `MessageDigest` and keep pure impl only as KAT oracle (DRY: SHA code + framing helpers duplicated in AccountKey and StrictSyncDigest → extract a `FramedBytes` writer).
- `AccountKey` equality of Strings fine; consider `value class`. `Todo` has `isPending/isDeleted` both, unclear invariants; `SyncProgress` ok.
- `CredentialVault.status` separate from `load()` is racy; return sealed `LoadResult(Ready|Locked|Corrupt|Unavailable)`.
- `TodoAction.ToggleCompleted(todo)` carries whole Todo (stale-state risk); prefer id.
- `AuthState` lacks vault-locked/corrupt states required by plan step 1 (only `VaultStatus` separate); confirm UI mapping.

## Positive
- commonMain: zero `android.*`/`java.*` imports (only `kotlinx.coroutines.flow.StateFlow`); no println/logging; no Room/Ktor in public signatures.
- Staged `settings.gradle.kts` guards exact scripts, no stub apps; `FAIL_ON_PROJECT_REPOS`, content-filtered repos.
- Credentials redacted in `toString`; session tokens live only in `CredentialBundle`; no token field in `AuthSession` state flows (memory-only compatible).
- SHA-256 constants/padding/rotation correct (verified via port); AccountKey framing prevents boundary collision.
- TitlePolicy whitespace set matches Unicode White_Space; surrogate/control handling correct.
- `.gitignore` covers secrets/keystores/local.properties; wrapper jar not ignored; wrapper `validateDistributionUrl=true`.

## Validation
| Command | Result |
|---|---|
| `bash tests/test-phase-02-foundation.sh` | 56/56 pass (but encodes wrong hash) |
| `curl services.gradle.org/.../gradle-8.11.1-bin.zip.sha256` | `f397b287…151c6` ≠ repo value → FAIL |
| `sha256sum gradle/wrapper/gradle-wrapper.jar` | `2db75c40…8046` ≠ docs `96de2340…` |
| Python port of Kotlin SHA-256 vs hashlib (len 0–199,1000); K table vs cube-root primes | match |
| `grep import` commonMain | only `kotlinx.coroutines.flow.StateFlow` |
| `./gradlew :shared:allTests` | NOT run (JDK 25 only, no cache/SDK; wrapper hash wrong) |

## Recommended Actions (priority)
1. Fix `distributionSha256Sum` → `f397b287…151c6`; fix evidence doc + wrapper-jar hash; fix test-suite expectation.
2. Install JDK 21 / set `JAVA_HOME`; run `./gradlew :shared:jvmTest` (and Android unit/compile) and record real output in foundation-evidence.
3. Add KAT tests: NIST SHA vectors, padding boundaries, fixed strict-v1 digest + AccountKey vectors from backend; invalid-combo/overflow tests.
4. Enable `dependencyLocking`, regenerate lock + verification metadata with `--write-verification-metadata sha256`, verify-metadata=true.
5. `api(coroutines.core)`; prune unused deps; resolve alpha pins.
6. Harden EndpointPolicy host/port parsing + canonical default-port strip; add tests.
7. Add `require`s in StrictOperation/computeDigest; document HTTP-status mapping.
8. Add redaction tests; PII toString guard; correct doc wording (byte length; remove "Rust vectors" claim unless added).

## Unresolved Questions
1. Is Rust-backend digest fixture available to cross-check vectors above?
2. Are stable Room/sqlite replacements acceptable (tuple reopen) or alpha intentionally frozen?
3. Should HubConfig/endpoint canonicalization strip default ports (affects AccountKey stability)?
4. Which JDK is the supported Gradle runner on this host (21 vs 25)?
