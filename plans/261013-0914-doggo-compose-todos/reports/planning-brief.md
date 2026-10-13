# Planning brief

Date: 2026-10-13. Planning only; no implementation or installation authorized by this request.
Plan directory: /home/loidinh/WS/doggo/plans/261013-0914-doggo-compose-todos

## User requirements
- Detailed, easy-to-follow implementation phases for a simple todo app, Android first.
- Include Android SDK/Studio/emulator setup on Fedora headless via Xpra virtual display; no GNOME/KDE or graphical boot mode.
- Kotlin/Jetpack Compose knowledge reusable through Compose Multiplatform; Linux desktop next, Windows on another machine later.
- Cloudflare D1 (SQLite-based) cloud data storage; use a Worker API, never cloud account credentials in the app.
- Visual theme inspired by https://forumflash.vercel.app/ (parent inspecting actual rendered site).
- Two parallel evc-researcher agents, each at most 5 tool calls and report <=150 lines.
- Comprehensive phase docs, strict exclusive file ownership, dependency matrix/graph; parallel only after frozen contracts. Runtime integration has honest sequential gates.
- All phase sections per user specification, plan.md YAML status pending/P2/effort sum/branch/created/tags, overview evc-cmd-plan.md <80 lines.
- Research and scout reports must be passed to planner; optional validation interview offered after plan.

## Observed host facts from this session
- Fedora 44 Server x86_64, Ryzen 7 8840U 8c/16t, ~23 GiB RAM, ~870 GiB available storage.
- AMD-V; user can access /dev/kvm. KVM API 12 and creation/close of empty VM succeeded.
- Radeon 780M enumerated by Mesa Vulkan; rendering through Xpra not verified.
- OpenJDK/Javac 25 installed. Android Studio, SDK tools, adb, emulator not found on PATH/usual paths.
- No DISPLAY/WAYLAND_DISPLAY in agent session; GNOME/KDE/display managers absent.
- sshd active; Xvfb and xorg-x11-xauth installed. Xpra and xterm not installed at last check.
- DNF unprivileged query failed because /usr/lib/sysimage/libdnf5/packages.toml is root-only; use sudo, do not chmod it.
- Recheck state before later implementation; do not assume prior installation guide was executed.

## Workspace findings
- Initial root listing empty, no README or docs or source visible. Scout to confirm; do not fabricate existing conventions.
- git branch --show-current reports main, but repository root must be verified (could be ancestor).
- Active-plan helper invoked; reports EVCRATE_SESSION_ID unset so session state not persisted. Pass explicit plan path to all agents.

## Conservative proposal for research evaluation
- Minimal CRUD: list, add, rename, complete/reopen, delete, All/Active/Completed filters.
- Online-first, no offline mutation queue or background sync unless user validates expansion. Preserve drafts and show actionable network failures.
- Android and desktop thin entrypoints around common UI/domain/repository; shared contracts fixed before parallel lanes.
- Authentication must not be hard-coded shared credentials/public write API. Evaluate simplest secure personal-app option and state tradeoff versus multi-user public release.
- Scope limits proposed, not user-approved product decisions; list material decisions for validation.

## Unresolved questions
- Personal/private app vs multi-user/public release; secure auth selection.
- Online-first acceptable vs offline writes (not requested).
- Exact desktop client used to access Xpra; provide client alternatives, not blocking research.
