# Code Review: Phase 01 Headless tooling and Android environment

Plan: plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md (status still pending 0%; not edited by reviewer)
Score: **6.5/10** (scripts/docs sound baseline; acceptance gate not met; several security/robustness gaps)

## Critical
1. **Acceptance incomplete.** Plan owns 6 paths incl. `docs/evidence/android-tooling-launcher.png` — missing (`docs/evidence/` absent). SDK, Studio, emulator, AVD not installed (`~/Android` absent). Plan Success Criteria (SSH-only Xpra + Studio + booted emulator + native screenshot + license/package inventory) unmet. Do not mark phase done. Tests (23/23) are static/smoke only.
2. **Xpra mDNS publishes session on LAN.** `xpra start --help`: `--mdns` default yes. `avahi-daemon` active on host -> session advertised. Contradicts "SSH-only/no public listener" claim. Fix: add `--mdns=no` in `start-xpra.sh`, update doc + evidence flags list.

## Warnings
1. `android-sdk-env.sh` silently falls back to host JDK 25 (observed `JAVA_HOME=/usr/lib/jvm/java-25-openjdk`). Docs/evidence say AGP needs JDK 21/JBR; plan says explicit compatible JAVA_HOME. Fallback should warn/skip (leave unset) unless major=21 (or Studio JBR). Header comment/doc "Detects JDK 21 LTS" misleading.
2. Doc §3 tells user to source env script from `.bashrc`; script prints banner and forks `adb --version`/`java -version` each shell -> stdout output in `.bashrc` breaks scp/sftp/rsync non-interactive shells + slows login. Make banner opt-in (`ANDROID_ENV_VERBOSE=1`) or quiet by default; drop `.bashrc` advice or guard with interactive check.
3. `start-xpra.sh` `xpra list | grep -q "${DISPLAY_NUM}"` substring match (`:100` matches `:1000`; verified). Use `grep -qE ":100( |$)"`-style anchored match / `xpra list` LIVE line parse. Also no post-start verification (daemon fork success).
4. `start-android-emulator.sh` hardcodes `:100` (ignores `XPRA_DISPLAY`) and same substring grep; duplicates display config (DRY). Use `${XPRA_DISPLAY:-:100}`.
5. Test "fails gracefully when emulator binary absent" = `! script` -> passes on ANY failure; non-hermetic: once SDK installed, it would **exec the real emulator** (script sources env, emulator found). Fix: run with `PATH`/`ANDROID_HOME` stubbed to temp dir and assert exit 1 + message text.
6. Doc boot-wait loop (§5) is unbounded; plan step 7 requires bounded poll. Add timeout (e.g. 300s) with failure exit.
7. Doc/evidence inconsistency: Security Invariant "keep KVM group permissions restricted" vs evidence `/dev/kvm` `crw-rw-rw-+` (world rw, Fedora udev default). Plan forbids chmod of KVM; reword invariant to "do not alter; note world-rw default" and record as accepted risk.
8. Doc is missing steps that plan requires: Studio download + official checksum verification + unpack to `~/.local/share/android-studio`; cmdline-tools bootstrap (chicken-egg: `sdkmanager` used before obtained); SSH host-key verification; `sdkmanager --list` to choose API/build-tools (doc hardcodes android-35 / 35.0.0 as if pinned); emulator host libs. Evidence doc lacks adb/emulator/licenses/renderer/Studio version inventory (acceptable as "not yet installed" only if stated explicitly; currently only "Candidate").

## Suggestions
- `android-sdk-env.sh`: SC2155 (`export JAVA_HOME="$(...)"` masks rc), leaked `JAVA_BIN`/`tool_dir` vars; `$(dirname $(dirname))` -> fine but quote ok. shellcheck not installed on host — run when available.
- `start-android-emulator.sh`: `exit 1` for missing emulator happens after KVM/accel prints—reorder; validate `EMULATOR_PORT` even in 5554–5682; `-accel-check || true` noise; consider failing when DISPLAY unset and not headless (qemu will fail later anyway).
- `start-xpra.sh`: `stop` swallows all errors (`|| true`) incl. real failures; `start`'s `whoami@<host-ip>` hint fine. Consider `--daemon=yes` explicit, `--pulseaudio=no`/`--notifications=no` only if needed (YAGNI otherwise). Hostname hint should say verified host (plan step 2).
- tests: relative paths require CWD=repo root — add `cd "$(dirname "$0")/.."`. `eval` of fixed strings OK but unnecessary; use function args. Host-prereq tests (kvm/node) mix env-checks with repo tests; split or label. Doc-content greps are weak (single keyword).
- Evidence doc: `Date` fixed; kernel/node versions are snapshot — fine, but state "observed" command + date per row for reproducibility. Node path under `~/.nvm` (user-specific) — sanitized? OK, no secrets seen.
- Script modes `rwx--x--x` (+ACL): ok for owner; consider 0755 for consistency/git.

## Positive
- No `--bind-tcp`, `--html=off`, `set -euo pipefail`, quoted vars, safe empty-array expansion (`${A[@]+"${A[@]}"}`), `exec emulator`, PATH dedupe, sourced-vs-run notice.
- Security docs: forbids `adb tcpip`, blanket `yes | sdkmanager --licenses`, committing `local.properties`.
- Flags match plan step 2 exactly. Scripts small, KISS, no secrets.
- Tests pass: 23/23.

## Reviewed files
scripts/start-xpra.sh, scripts/android-sdk-env.sh, scripts/start-android-emulator.sh, docs/headless-android-setup.md, docs/host-tooling-evidence.md, tests/test-phase-01-tooling.sh (~430 LOC total)

## Validation run
- `bash tests/test-phase-01-tooling.sh` -> 23/23 pass.
- `xpra list` -> no sessions; `xpra --version` v6.5.4; `xpra start --help` -> mdns default yes; `systemctl is-active avahi-daemon` -> active.
- `./scripts/start-android-emulator.sh x` -> rc=1 with expected message (emulator absent).
- `source scripts/android-sdk-env.sh` -> JAVA_HOME=java-25-openjdk, banner printed.
- Substring grep bug reproduced (`:1000` matches `:100`).
- shellcheck: not installed (not run). No GUI/Studio/emulator runtime validation possible (not installed).

## Unresolved questions
1. Is software (non-Xpra) install of Studio/SDK in scope for this review round, or only scripts/docs? (screenshot + inventory then deferred)
2. Is JDK 21 to be installed (`java-21-openjdk-devel`) or rely on Studio JBR? Only JDK 25 present now.
3. Accept world-rw `/dev/kvm` as-is (plan forbids chmod)?
4. Which plan dir is authoritative (1540-glean-hub-reuse vs 0914-compose-todos)? Both contain phase-01; reviewed against 1540.
