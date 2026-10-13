# Scope review pause
Date: 2026-10-13. Status: paused at user's request; not an implementation-ready plan.

## User direction
User interrupted the hosting-cost question: "why this app need server? stop thinking and answer my question, we need review scope".
Parent answered: local todos need no backend; automatic cross-device sync and private cloud accounts motivated Worker+D1. Worker is serverless API access to D1, not a self-managed VPS. Paid hosting was raised for secure password hashing, not basic todo functionality.
Open scope question: same todo list automatically synchronized between devices, or simply the same application available on Android/Linux/Windows with independent local data?
Do not freeze or expand architecture until this distinction is resolved. Do not treat the unanswered paid-hosting question as approval.

## Recorded choices before pause
These are conditional on retaining the cloud-account/sync scope, not permission to bypass the scope review:
- D1-owned username/password authentication; invite-only registration.
- Last server arrival wins for concurrent edits.
- Logout/account switch: local wipe with sync/cancel/explicit-discard guard.
- Android background sync in addition to foreground sync.
- OS-protected local todo storage; platform-protected credentials, no separate database encryption requirement.
- Previously confirmed: ForumFlash-inspired light theme, Android first through headless Fedora/Xpra, Linux next, manual native Windows build later.

## Completed work
- Planning and relevant workflow/skill guidance read.
- Existing plan and prior validation reviewed; no application source or requested project docs exist.
- Two researchers returned terminal reports; five tool calls each. Read-only scout completed; parent saved its report.
- Parent cross-checked D1 batch/consistency, Room Android/JVM support, credential deprecation and Worker password-hashing constraints using primary sources.
- Research reports are evidence inputs, NOT frozen design. They contain recommendations explicitly rejected by parent: deprecated EncryptedSharedPreferences, unspecified Linux encrypted-file key custody, generic OAuth copy/paste fallback, unsupported replication-lag guarantees and unproven batch deduplication/snapshot algorithms. Managed OIDC recommendations were superseded by the user's D1 choice before this broader pause.

## State and limits
No source code, installations, builds, tests, deployments, purchases or provisioning performed. No revised architecture/contracts/phase package has been completed or approved. Historical shared-token/online-only plan remains superseded and must not be implemented.
Session active-plan helper could not persist state because EVCRATE_SESSION_ID was unset; it reported only the intended new plan path.

## Next action
Wait for user's scope review answer. Then revise only the confirmed scope, review the complete package, offer validation, and retain Phase 01 headless tooling/Android setup as the first implementation step if the user continues with that platform plan.
