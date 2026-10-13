# Research brief
Date: 2026-10-13. Planning only; no installs, application source, build/test/lint/formatter runs.
Project `/home/loidinh/WS/doggo`; new plan `plans/261013-1459-doggo-offline-accounts/`; historical plan `plans/261013-0914-doggo-compose-todos/`.
Environment: Fedora Linux 7.1.10-200.fc44.x86_64, x64, headless through SSH Xpra; Asia/Saigon; branch main; approximately 24 GB RAM. Android first, Linux installed RPM after Android acceptance, Windows manual native MSI on a later machine. No cross-build or Windows CI substitution.

## Observed scope
- Repo root contains only planning artifacts. README and docs directory absent. Mandatory codebase summary/standards/architecture/PDR cannot be read; fresh scout requested.
- User previously validated separate private user accounts, offline editing + sync, ForumFlash-inspired light-only theme, manual Windows builds.
- Existing source architecture/contracts are proposals with explicitly superseded shared-token/shared-dataset and online-only assumptions. Do not inherit them.
- Product: personal todos, create/rename/complete/reopen/delete, All/Active/Completed, counts, refresh, bounded list rendering. No collaboration, due dates, reminders or tags.
- Stack retained: Kotlin Multiplatform shared library + shared Compose UI, separate Android application and JVM desktop application; TypeScript Cloudflare Worker and D1. Backend data must remain D1, not auth vendor's app-data database.
- Native login must work Android, Linux, Windows. Cloudflare deployment credentials never in clients. Release HTTPS, no token/title logging, no plaintext credential fallback. Debug local Worker only loopback/emulator alias.
- First implementation action after plan revision/review/validation is Phase 01 headless tooling/Android setup. Nothing installed now.

## Research slices
1. Authentication: recommend native-compatible managed identity versus self-managed Worker auth; real providers and browser/PKCE callbacks; JWT validation/authorization; refresh/revocation/secure storage across platforms; logout/account-switch with unsynced data. Distinguish provider cost/config prerequisites from proof. Product tradeoff questions before freeze.
2. Persistence/sync: choose Room KMP vs SQLDelight/SQLite supported Android/JVM; durable account-isolated outbox and local view; D1 atomic changes+receipts+change feed and concurrency constraints; bounded pull/bootstrap/recovery, tombstones, idempotency, conflict policy, foreground vs background sync. Product tradeoff questions before freeze.

## Agent output rules
Each researcher at most FIVE tool calls total (read this brief, up to three targeted official-documentation searches/reads, write its report). Report <=150 lines including citations, dated facts vs recommendations/unknowns, unresolved questions at end. Scope each to its assigned report only. No package installation or code. Return terminal completion plus path, tool-call count and validation status. Parent loaded planning, docs-seeker, mermaidjs-v11, sequential-thinking skills; prefer primary-source documentation. Parent handles architecture and product questions after both reports finish.
