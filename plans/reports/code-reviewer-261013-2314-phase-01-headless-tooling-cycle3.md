# Code Review (Cycle 3, final): Phase 01 Headless tooling and Android environment

Plan: plans/261013-1540-doggo-glean-hub-reuse/phase-01-headless-tooling.md (pending 0%; not edited, runtime gate unmet; do not mark accepted).
Score: **8.5/10** (prior 7.5). Static/script scope clean; all cycle-2 defects fixed + verified. Remaining: host-dependent test, unbounded `wait-for-device` in doc, runtime acceptance gate (env prerequisite).

## Critical
1. None in code/security. Runtime acceptance gate unmet (env prerequisite, not defect): no SDK/Studio/AVD/emulator, no `docs/evidence/android-tooling-launcher.png`. Phase stays pending.

## Warnings
1. **Java-25 test is host-dependent/brittle.** `tests/test-phase-01-tooling.sh:48` asserts "Host Java is version 25". Verified: `JAVA_HOME=/x bash tests/test-phase-01-tooling.sh` -> 30/31 FAIL. Also fails once JDK 21 / Studio JBR installed (the intended remediation) or JAVA_HOME preset. Fix: stub `java` in a temp PATH printing `openjdk version "25.0.1"` (+ `env -u JAVA_HOME`, HOME pointing at empty tmp dir to defeat JBR/java-21 dir branches), plus a stub 21 case asserting JAVA_HOME exported.
2. **Doc boot-wait not truly bounded.** `docs/headless-android-setup.md:152` `adb wait-for-device` blocks forever if emulator never registers; 120s timeout only covers the poll loop. Fix: `timeout 120 adb -s "$serial" wait-for-device || return 1`.
3. **JAVA_HOME left unset when only non-21 JDK** (`android-sdk-env.sh:37-45`). Safe/conservative and matches "don't replace host JDK", but plan step 5 wants "explicit compatible JAVA_HOME"; Gradle will silently use Java 25 until JDK 21/JBR installed. Plan also says bytecode 17 / runtime "selected by demonstrated compatibility" — hardcoded 21 is an assumption; JDK 17 would also be rejected. Acceptable now; revisit after plan step 4 compat proof. Also: unparsable `java -version` falls through to exporting candidate (else branch) — benign.
4. **`XPRA_DISPLAY` unvalidated and used as regex** (`start-xpra.sh:18`, `start-android-emulator.sh:58`). Verified: `XPRA_DISPLAY='.*' start-xpra.sh stop` -> "Stopping Xpra server on .*" with zero sessions (matches "No xpra sessions found"). Self-inflicted env only; fix: `[[ $DISPLAY_NUM =~ ^:[0-9]+$ ]] || exit 1` once in xpra script (and emulator script).

## Suggestions
- `start-xpra.sh:35`: xpra 6.5.4 emits "start-child used but exit-with-children not enabled, you should just use 'start'" at every start. Switch to `--start=xterm`; drop `--exit-with-children=no`. Update doc/evidence/test flag strings.
- DRY: display-match regex duplicated in 2 scripts; fine at this size.
- Doc §4: `sha256sum -c studio.tar.gz.sha256` — Google publishes the SHA-256 on the download page, not a `.sha256` file; use `echo "<sha>  file" | sha256sum -c -`. Mark `android-35`/`35.0.0` as examples (doc already says "example baseline"; plan says pick from `sdkmanager --list`).
- Evidence doc: `Xvfb` status "Ready" / `xterm`, `vncserver` "System package" lack version; `Xvfb -version` is not a valid flag (use `Xvfb -help` / `rpm -q xorg-x11-server-Xvfb`). Fine as is.
- Evidence doc: add "Live Xpra session: not recorded" and sanitized screenshot link once gate runs. Pending inventory (adb/emulator/Studio/AVD) now explicit — good.
- Tests are mostly static greps (docs). Add stubbed-`xpra` test for `:100` vs `:1000` matching.
- shellcheck not installed; run when available.

## Resolved from cycle 2 (verified)
- Java regex `\"([0-9]+)`: Java 25 detected, `JAVA_HOME` stays unset, banner/notice only interactive or `ANDROID_ENV_VERBOSE=1`.
- Port: `08`, `05554`, ` 5554`, `5554x`, `-5554`, `0`, `5553`, `5684`, `55540` all rejected with message; `5554`, `5680`, `5682` pass to emulator-binary check; normalized via `10#`. (EMULATOR_PORT="" -> default 5554 via `:-`.)
- Doc boot poll wrapped in function with `local`/`return`; no `exit` left.
- Evidence doc has explicit pending install inventory.

## Positive
No `--bind-tcp`; `--html=off`, `--mdns=no`; `set -euo pipefail`; quoted vars; `exec emulator`; safe empty-array expansion; no secrets; docs forbid `adb tcpip`, blanket-`yes` licensing, committing `local.properties`, chmod on `/dev/kvm`; SSH host-key verification step; small KISS scripts; idempotent start/stop.

## Reviewed files
scripts/start-xpra.sh, scripts/android-sdk-env.sh, scripts/start-android-emulator.sh, docs/headless-android-setup.md, docs/host-tooling-evidence.md, tests/test-phase-01-tooling.sh (~420 LOC).

## Validation
- `bash tests/test-phase-01-tooling.sh` -> 31/31 pass.
- `JAVA_HOME=/x bash tests/test-phase-01-tooling.sh` -> 30/31 (Java-25 test fails; W1).
- Port matrix (13 inputs) as above: all correct.
- `env -u JAVA_HOME; source android-sdk-env.sh` -> JAVA_HOME empty; verbose -> warning printed.
- **Live Xpra smoke (display :199, then stopped):** `start` rc=0, session LIVE in `xpra list`, `xpra`+`Xorg`+`xterm` procs; args show `--html=off --mdns=no`; `ss -ltn` no xpra TCP listeners; second `start` -> "already running"; `stop` -> clean exit, no leftover xpra procs. :5353 UDP listener present is system Avahi (pre-existing; xpra started `--mdns=no`).
- Host: xpra 6.5.4, OpenJDK 25.0.4.1 (only /usr/lib/jvm java-25*), node v24.16.0, kernel 7.1.10-200.fc44, `/dev/kvm` `crw-rw-rw-` — match evidence doc. No `adb`/`emulator`.
- Not run: shellcheck (absent), Studio/emulator/AVD (not installed).

## Unresolved questions
1. Install `java-21-openjdk-devel` vs Studio JBR (needed for explicit JAVA_HOME)? Should 17 be accepted given plan bytecode-17 baseline?
2. Runtime acceptance (installs, screenshot evidence, AVD boot) in scope now or later gated step?
3. Authoritative plan dir: 1540-glean-hub-reuse vs 0914-compose-todos (reviewed against 1540).
