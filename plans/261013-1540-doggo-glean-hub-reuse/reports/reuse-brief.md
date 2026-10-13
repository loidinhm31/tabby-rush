# Doggo / Glean Hub reuse brief
Date: 2026-10-13. Planning only. Plan directory `/home/loidinh/WS/doggo/plans/261013-1540-doggo-glean-hub-reuse/`.
User: "replan, check /home/loidinh/WS/glean-hub/glean-hub-server/, this one server has common mechanism that I used for many applications by using schema, and check if we can reuse authentication from this server to login in this Doggo app".
Historical investigation input; later [validated decisions](validation-summary.md) supersede backend/LWW uncertainty below. Selected Doggo-only strict A and148h plan are authoritative;no implementation now.

## Constraints
- No source edits, installations, database writes, registrations, deployment or authenticated production probes. Do NOT read .env or other secret-bearing local configuration.
- Main project `/home/loidinh/WS/doggo` contains planning artifacts only. Target reused server in `/home/loidinh/WS/glean-hub/glean-hub-server`; referenced local sync plugin `/home/loidinh/WS/glean-hub/glean-hub-plugin/glean-hub-sync`; protocol/client crates under `/home/loidinh/WS/glean-hub/glean-hub-core`.
- Environment: headless Fedora Linux 7.1.10-200.fc44.x86_64, x64, ~24GB RAM, Asia/Saigon; Doggo branch main. Android first via SSH Xpra, Linux next, Windows manual native build later.
- Retain KMP/Compose and ForumFlash-inspired light theme. Last clarified preferences: offline editing, last server arrival wins, logout/switch wipes local data with unsynced guard, Android background sync, OS-private SQLite plus protected credentials.
- Previous D1-owned invite-only username/password plan was PAUSED by user for scope review before this new request. Do not assume backend stays D1 or is replaced: research options then main agent asks material backend tradeoff. No repeat custom auth recommendation without examining existing server.
- Parent loaded planning/docs-seeker/mermaidjs-v11 guidance previously. No LSP configured (checked xd://lsp status); use native read/grep.

## Observed auth source (parent owns investigation)
Server src/routes/auth.rs exposes public /register,/login,/refresh and JWT /me under /api/v1/auth; source differs from older README API-key claims. LoginRequest has email/password, not username. AuthService Argon2, HS256 JWT with sub/apps/is_admin/exp; actual generation Duration::hours(6), not documentation's 1h. Refresh hashes random32-byte token,30-day expiry; atomically revokes old token, then route creates successor nontransactionally. No logout route observed. Parent verifies auth/per-account delegation details.

## Independent scout slices
- Sync protocol: inspect plugin schema/auth/isolation/push/pull/tombstone/receipt semantics and authoritative core wire DTOs. Determine safe reuse vs changes needed, particularly LWW mismatch and idempotent durable queue.
- Existing clients: locate actual login/refresh/credential storage/app schema registration consumers and shared sync client-engine implementations; explain what KMP can reuse at HTTP contract level vs Rust/Tauri-only implementation. Flag patterns not to copy blindly.

## Outputs
Read-only scouts return <=150-line markdown with file:line evidence and unresolved questions. Parent saves reports in this plan's scout/. No builds/tests/lint/formatters; only inspection. Return terminal completion and evidence/validation limits. Do not explore unrelated application domain logic.
