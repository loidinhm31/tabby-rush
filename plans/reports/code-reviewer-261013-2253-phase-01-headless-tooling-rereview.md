# Code Re-Review (Cycle 2): Phase 01 Headless tooling and Android environment

Plan: plans/261013-1540-tabbyrush-glean-hub-reuse/phase-01-headless-tooling.md (still pending 0%; not edited)
Score: **7.5/10** (prior 6.5). Scripts/docs fixes land; 2 new defects; runtime acceptance gate still unmet.

## Critical
1. **Acceptance gate unmet (unchanged).** No `docs/evidence/android-tooling-launcher.png`; SDK/Studio/AVD/emulator not installed; no live Xpra session. Tests are static/smoke only. Phase must not be marked done. (Environment prerequisite, not a code defect.)

## Warnings
1. **Java 25 warning is dead code.** `android-sdk-env.sh:32` matches `*" 25."*`, but `java -version` line is `openjdk version "25.0.4.1" ...` (quote, not space, before 25). Verified: `NOMATCH`. Fix claimed in cycle 2 does not work; JAVA_HOME still silently = java-25-openjdk. Fix: parse major `[[ "$java_ver" =~ \"([0-9]+) ]]` and warn when != 21 (or >21). Consider leaving JAVA_HOME unset instead of exporting an incompatible JDK.
2. **EMULATOR_PORT validation bypass for `08`/`09`.** `start-android-emulator.sh:24` `$((PORT % 2))` treats leading-zero as octal; `08` -> "value too great for base" and script **continues**, skipping the emulator-binary/KVM checks, printing "Starting emulator ... port 08" (reproduced). `05554` passes (octal 2924 even, `-lt` decimal) and is passed to emulator as `05554`. Fix: `PORT=$((10#${PORT}))` after the `^[0-9]+$` check, or regex `^[1-9][0-9]{3}$`, and pass normalized PORT.
3. Tests don't cover either defect above (port test only odd `5555`; no Java warning assertion) -> 27/27 green masks them.
4. Doc §5 boot-poll snippet uses `exit 1` — if pasted in interactive shell it kills the session. Wrap in a script/function using `return`/subshell.

## Suggestions
- Test grep for display tokens: tests don't exercise `:100` vs `:1000` matching of `is_running` (regex verified manually OK: `:1000` no match, `LIVE session at :100` match). Add a test with stub `xpra` in PATH.
- Display-match regex duplicated in `start-xpra.sh` and `start-android-emulator.sh` (DRY); acceptable at this size, could share via env script.
- `start-xpra.sh` `status` prints "No active..." on `xpra list` failure; fine. `exec` after prints OK.
- shellcheck not installed; run when available (SC2155 gone; `source` directive present).
- Doc §4 hardcodes `android-35`/`35.0.0`; plan says choose from `sdkmanager --list` (doc does instruct listing first; mark values as example).
- Test uses `bash -c "$cmd"` on strings — fine, hermetic cd added.
- Evidence doc: add "not yet installed" rows for adb/emulator/Studio/licenses explicitly so inventory gap is visible.

## Resolved from cycle 1 (verified)
- mDNS: `--mdns=no` in script, docs, evidence, test.
- Display anchored match in both scripts; XPRA_DISPLAY honored in emulator script.
- Banner suppressed for non-interactive shells (`$-`/`ANDROID_ENV_VERBOSE`); sourced, no output in scp.
- Port validation present (works for decimal inputs, odd/non-numeric rejected). Defect only on leading-zero (W2).
- Bounded boot poll documented (120s); Studio checksum, cmdline-tools layout, host-key verification documented.
- KVM 0666 wording consistent across docs; modes 0755 (observed `rwxr-xr-x+`).
- Hermetic test (PATH/ANDROID_HOME stubbed, specific message asserted); `cd` to repo root.

## Positive
No `--bind-tcp`, `--html=off`, `--mdns=no`; `set -euo pipefail`; quoted vars; `exec emulator`; safe empty-array expansion; no secrets; docs forbid `adb tcpip`, blanket `yes` licensing, committing `local.properties`; small KISS scripts.

## Reviewed files
scripts/start-xpra.sh, scripts/android-sdk-env.sh, scripts/start-android-emulator.sh, docs/headless-android-setup.md, docs/host-tooling-evidence.md, tests/test-phase-01-tooling.sh

## Validation
- `bash tests/test-phase-01-tooling.sh` -> 27/27 pass.
- `xpra list` -> no sessions.
- Display regex: `:1000` line vs `:100` -> no match; `LIVE session at :100` -> match.
- `EMULATOR_PORT=abc` -> rejected msg; `=5555` rejected (test); `=08` -> arithmetic error, validation bypassed (BUG); `=05554` -> accepted unnormalized.
- Java match: `java -version` -> `openjdk version "25.0.4.1"`; `*" 25."*` -> NOMATCH (BUG).
- shellcheck: not installed; no Studio/emulator/Xpra runtime validation possible.
- File modes: scripts 0755.

## Unresolved questions
1. Install JDK 21 (`java-21-openjdk-devel`) or rely on Studio JBR? Only JDK 25 present; should env script refuse (leave JAVA_HOME unset) on non-21?
2. Is runtime acceptance (install, screenshot, AVD boot) in scope this round or deferred to a later gated step?
3. Authoritative plan dir: 1540-glean-hub-reuse vs 0914-compose-todos (reviewed against 1540).
