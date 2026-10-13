# Authoritative synthesis and corrections

Date: 2026-10-13. Parent review supersedes conflicting recommendations in researcher-01/02. Proposed defaults require user review; planning only.

## Research quality gate
- Both researchers and scout returned terminal reports. Researcher-01 contains stale/incompatible toolchain recommendations and unsafe launch examples; do NOT copy its commands as the implementation runbook.
- Current official compatibility page reports Kotlin 2.4.21, KMP compatibility with Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1. These ranges do NOT prove arbitrary combinations work: selected AGP has its own Gradle/JDK requirements.
- Current Compose page reports Compose Multiplatform 1.12.1. Use current stable IDE wizard/template, explicitly record and pin its compatible Kotlin/Compose/AGP/Gradle/JDK/SDK tuple during foundation. Compiler plugin version equals Kotlin. Do not pin old 2.1.10/8.11.1 combination from researcher report.
- Sources: https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html ; https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html ; https://developer.android.com/kotlin/multiplatform/plugin ; https://kotlinlang.org/docs/multiplatform/quickstart.html
- Modern structure: androidApp Android application; shared KMP library using com.android.kotlin.multiplatform.library plus JVM target; desktopApp JVM application. Do not apply com.android.application and KMP together in one module. Prefer one shared module with package-level ownership over unnecessary core:* modules for this simple app.
- System Java 25 is not inherently broken. Select compatible Gradle runtime explicitly (IDE recommended JBR or compatible LTS); distinguish runtime JDK from Kotlin/Java bytecode target. Do not force Android bytecode target 21 without justification.

## Secure headless runbook requirements
- Xpra over SSH only, no --bind-tcp=0.0.0.0:10000; no public unauthenticated HTML5 endpoint. Keep boot mode unchanged, no full DE installation.
- Launch Studio/emulator from Xpra xterm to inherit display/auth, not DISPLAY alone. Choose executable actually shipped (current docs use bin/studio).
- sdkmanager --licenses is interactive review; no yes-pipe blanket acceptance. Record actual SDK/build-tools/system image selections, use x86_64 emulator.
- Current emulator graphics: auto first; software or swiftshader if needed, check emulator -help-gpu. swiftshader_indirect deprecated since 36.4.9, not baseline.
- Do not assert Radeon/Xpra rendering failure without test. KVM is verified, graphics are not. First emulator -accel-check; then actual boot, adb install/launch and visual screen inspection.
- Prefer USB debugging or modern paired wireless ADB. Do not expose legacy unauthenticated tcpip 5555.
- Local Worker binds 127.0.0.1:8787. Android emulator uses http://10.0.2.2:8787; desktop http://127.0.0.1:8787; USB device can adb reverse. HTTP is debug-only; release HTTPS and no cleartext exceptions. Do not expose Wrangler dev to LAN by default.
- Sources: https://github.com/Xpra-org/xpra/blob/master/docs/Network/SSH.md ; https://developer.android.com/studio/run/emulator-acceleration ; https://developer.android.com/tools/sdkmanager

## Product default
- Personal/private todo app (validate explicitly); create/list/rename/complete/reopen/delete, All/Active/Completed filters, counts and refresh. No due dates, reminders, tags, collaboration or app-store release workflow unless requested.
- Online-first with authoritative D1 data; no offline write queue/background sync/local SQL mirror. Preserve drafts on failure; last loaded data can remain in memory, clearly stale. Validate online-first assumption.
- Light theme inspired by measured ForumFlash colors and typography. See researcher-03-theme-observation.md; font-license verification mandatory.
- Android first full acceptance gate. Linux thereafter; Windows later on real Windows machine with its own pending acceptance, not silently dropped.

## Security and data defaults
- Worker API mediates all D1 access. Account/database identifiers are not passwords; actual Cloudflare credentials never reach the app. D1 is not an on-device SQLite file or direct public native-client API.
- For private-only scope propose one high-entropy app-scoped Bearer token, manually provisioned, Worker secret; never hard-code in builds/source/examples, never reuse Cloudflare token. Store only in app process memory initially (re-enter after restart), masked config form, explicit forget/disconnect. This intentionally avoids plaintext persistence and deprecated EncryptedSharedPreferences or desktop keyring integration. Validate usability/security tradeoff before implementation.
- All token holders share the same private dataset; no user isolation. Anyone requiring accounts/public app must revise schema/auth contract before implementation. Do not describe this token as zero-maintenance/zero-risk; document rotation/revocation and shared-device risks.
- HTTPS mandatory outside debug loopback/emulator alias; block bearer redirects/cross-origin forwarding, no token/title logging. Reject unauthenticated requests before touching D1; fail closed if server secret missing. Use supported constant-time comparison with validated encoding/length, not naive string equality. No CORS needed for native clients; CORS is not authentication.
- Single todos table: client UUID, trimmed title, completion boolean, server timestamps; prefer integer version for stale-write detection rather than silently overwrite multi-device edits. Freeze exact API/schema names, errors and pagination behavior in contract doc.
- Create duplicate ID must NEVER upsert/overwrite title as researcher-02 suggests: INSERT conflict -> 409; client reconciles via GET id after unknown outcome. Retry same id only after reconciliation, never generate a fresh id for an ambiguous submission. A UUID alone does not guarantee idempotency.
- PATCH with expected version -> atomic conditional UPDATE/version increment; 409 conflict refresh while retaining draft. DELETE expected version, explicit 404/409 behavior; unknown delete outcome reconciled by lookup. No automatic blind mutation retries.
- Title length policy must specify Unicode unit consistently across Kotlin/JS; UI validates same rule as API. Typed stable error envelope and bounded list response/pagination as appropriate; avoid sync machinery.
- Prepared SQL binding, bounded bodies/title, supported UUID validation. Research sample test-1 is not a valid UUID. Correct smoke examples accordingly.
- D1 limits/pricing depend on plan and date; do not promise free service or copy researcher claim 100 queries universally. Recheck official https://developers.cloudflare.com/d1/platform/limits/ and https://developers.cloudflare.com/d1/platform/pricing/ before provision. Costs are informational, not a prerequisite to speculative scaling.

## Parallel plan rules
- Exclusive file ownership across ALL phases, including build files, manifests, lockfiles, shared models, docs and test files. Foundation creates ONLY files it owns; it does not create stub entrypoints another phase replaces.
- Frozen interfaces/schema first. Then separate migrations, Worker, shared data/state, and shared UI work where truly independent. Consumers may code against frozen contracts without live service; do not call runtime integration independent.
- Integration phase owns app entrypoints/composition and its own acceptance report/tests, not edits to every other phase file. Faults reopen original owner before rerun, never transfer file ownership implicitly.
- Root catalog/build settings have one owner; module scripts may belong to their implementation owner if root does not generate them. Specify packages and exact ownership scope; no overlapping broad globs.
- Independent module tests belong to module owner. Runtime gates after required dependencies, no tests of implementation wording/wiring or duplicated mock echoes. Include user-visible state/error/concurrency/auth boundaries and actual app interaction.
- Phases remain pending; every named target has concrete commands/setup/acceptance and estimated effort. Only plan artifacts now, no scaffolds/installations.

## Unresolved decisions for validation
1. Private single-user shared token/session-only storage vs public multi-user accounts.
2. Online-first vs offline mutation support.
3. Light-only initial theme vs light/dark.
4. Windows manual workstation release vs CI as additional convenience (manual default matches request).
