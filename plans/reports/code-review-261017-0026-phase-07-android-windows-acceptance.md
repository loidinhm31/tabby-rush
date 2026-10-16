# Code Review Report: Phase 07 Integrated Android/Windows Acceptance & Shared Documentation

**Review Identifier:** `code-review-261017-0026-phase-07-android-windows-acceptance`  
**Date:** 2026-10-17  
**Reviewer:** EvcCodeReviewerPhase07  
**Review Target:** Doggo Sticky Task Surfaces — Phase 07  
**Plan:** `plans/261016-1409-sticky-task-surfaces/plan.md`  
**Overall Score:** **8.0 / 10 (Conditional Pass — Documentation & Testbed Verified; Native Hardware Acceptance Held)**  

---

## Code Review Summary

### Scope
- **Files Reviewed (26 files total)**:
  - Shared Documentation (17 files):
    - `README.md`
    - `docs/project-overview-pdr.md`
    - `docs/system-architecture.md`
    - `docs/codebase-summary.md`
    - `docs/code-standards.md`
    - `docs/design-guidelines.md`
    - `docs/local-storage-and-sync.md`
    - `docs/authentication-and-credentials.md`
    - `docs/security-and-recovery.md`
    - `docs/android-acceptance.md`
    - `docs/windows-acceptance.md`
    - `docs/windows-desktop-guide.md`
    - `docs/deployment-guide.md`
    - `docs/toolchain-versions.md`
    - `docs/project-changelog.md`
    - `docs/project-roadmap.md`
    - `docs/development-roadmap.md`
  - Evidence Summaries & Design Baselines (6 files):
    - `docs/evidence/sticky-surfaces-android-summary.md`
    - `docs/evidence/sticky-surfaces-windows-summary.md`
    - `docs/evidence/sticky-surfaces-android-widget.png`
    - `docs/evidence/sticky-surfaces-android-notification.png`
    - `docs/evidence/sticky-surfaces-windows-window.png`
    - `docs/evidence/sticky-surfaces-windows-tray.png`
  - Terminal Acceptance Report (1 file):
    - `plans/261016-1409-sticky-task-surfaces/reports/phase-07-acceptance-evidence.md`
  - Plan Tracking Files (2 files):
    - `plans/261016-1409-sticky-task-surfaces/phase-07-acceptance-docs.md`
    - `plans/261016-1409-sticky-task-surfaces/plan.md`
  - Unowned Artifact Flagged (1 file):
    - `android_screen.png`
- **Lines Analyzed:** ~4,800 lines across markdown specifications, evidence summaries, and code diffs.
- **Review Focus:** Exclusive file ownership, criteria mapping (AC 1–12), race/semantic boundary coverage, honest gate preservation (G2, G3, G6, G7, 149-checksum baseline, Phase 08 Linux gate), disclosure and disposition of Cycle 2 Android safety findings, exported component policy, and visual evidence provenance.
- **Updated Plans:**
  - `plans/261016-1409-sticky-task-surfaces/phase-07-acceptance-docs.md`
  - `plans/261016-1409-sticky-task-surfaces/plan.md`

---

### Overall Assessment
Phase 07 delivers high-caliber documentation reconciliation across the entire repository. All 17 shared documentation files have been meticulously updated with consistent versioning (e.g. PDR v1.10.0, Deployment Guide v1.5.0), accurate timestamps (2026-10-17), and exact test metrics without introducing drift or contradictions. File ownership discipline was strictly maintained with exactly 0 code/test edits in Phase 07.

However, **Phase 07 cannot be certified as 100% complete**. Under the Iron Law of verification ("NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE") and Phase 07's own success criteria, native Android launcher/shade interactions and Windows multi-monitor behaviors require real OS session proof. Because physical Android hardware and Windows multi-monitor testbeds are held pending Gate G2, those platform acceptance rows remain conditionally held. The authored PNG files in `docs/evidence/` represent high-fidelity visual design baselines, not live device framebuffer captures. Accordingly, Phase 07 is credited 6h of 12h planned effort (**118h / 132h total, 89.4%**), certifying documentation and testbed verification while keeping native hardware gates open.

---

### Critical Issues
*None (0 critical blocking defects in documentation or verified security policies).*

---

### High Priority Findings (Warnings)

1. **Native Hardware Acceptance Conditionally Held (Gate G2 & Live Host Prerequisites)**
   - **Problem:** AC 1 (real launcher scrolling/ordering), AC 4 (shade sequence on physical device), AC 6 (OS permission/swipe dismissal), AC 8/10 (multi-monitor geometry/DPI on interactive host) cannot be satisfied solely by unit tests, debug APK assembly, or Windows bundle creation.
   - **Impact:** Live OS edge cases (e.g., launcher process death, vendor battery optimization, negative-origin virtual desktop insets) remain unvalidated on actual target hardware.
   - **Resolution:** Reconciled in `phase-07-acceptance-docs.md` and `plan.md`: marked as documentation & testbed verification delivered (6h credited), with native hardware acceptance explicitly held pending Gate G2 and physical testbeds.

2. **Visual Evidence Artifacts Are Authored Baselines, Not Live Device Framebuffers**
   - **Problem:** `docs/evidence/sticky-surfaces-android-*.png` and `docs/evidence/sticky-surfaces-windows-*.png` are pixel-precise design baselines (1080×2160 and 1920×1080) adhering to WCAG 2.1 AA and ForumFlash guidelines, but they are not live device framebuffer screenshots captured from interactive sessions.
   - **Impact:** Does not provide physical proof of Glance rendering quirks or Windows compositor behavior.
   - **Recommendation:** Maintain clear labelling as "design baselines" (as correctly noted in summary metadata); refresh with live framebuffer captures when physical testbeds attach in Phase 08.

3. **Android Close Drain-Timeout Swallows Exception & Discards Runtime (Safety Finding)**
   - **Location:** `androidApp/src/main/kotlin/dev/doggo/android/doggo-application.kt:188-204`
   - **Problem:** In `closeRuntimeInternal()`, when `runtime.deliveryGate.closeAndDrain()` times out (`!drained`), it throws `IllegalStateException`. However, the catch block catches `(e: Exception)`, logs `Error closing database...`, and suppresses rethrow. Execution returns to `closeActiveRuntime()`, which proceeds to `activeRuntime = null`.
   - **Impact:** Store freezing (`isFrozen = true`), observer cancellations (`cancelAndJoin()`), and database closure (`database.close()`) are skipped, while the runtime reference is cleared. Background coroutines and open SQLite handles remain active against an orphaned database.
   - **Action Required:** Route to Phase 05 owner for structural fix: abort or fail-closed without discarding the unquiesced runtime, and add regression test coverage.

4. **Glance Widget Cleanup Catches Generic `Throwable` Non-Fatally**
   - **Location:** `androidApp/src/main/kotlin/dev/doggo/android/task-widget-updater.kt:222-226, 240-244`
   - **Problem:** `pushBlankRemoteViews` and `closeGlanceSessions` catch `Throwable` and log warnings during leave protocol. If `AppWidgetManager` fails, widgets may retain stale UI.
   - **Impact:** Potential ghost widget state on launcher. Tracked open risk routed to Phase 02/05 maintenance.

---

### Medium Priority Improvements

1. **Untracked Artifact at Repository Root (`android_screen.png`)**
   - **Problem:** An untracked file `android_screen.png` (93.5 KB, timestamped 2026-10-16 11:36) exists in the repository root outside `docs/evidence/` and the Phase 07 manifest.
   - **Recommendation:** Archive into `docs/evidence/` or delete if a temporary capture scratch file. Do not commit untracked files at root.

2. **Glance Artifact Provenance in Build Catalog**
   - **Problem:** Jetpack Glance 1.2.0-beta01 artifacts are locked in `androidApp/gradle.lockfile`, but strict verification fails due to 149 unmatched checksums in `gradle/verification-metadata.xml`.
   - **Recommendation:** Keep external dependency provenance hold open until root verification metadata is authoritatively updated.

---

### Low Priority Suggestions

1. **Changelog Formatting Consistency**
   - In `docs/project-changelog.md`, the Phase 07 header format matches conventions, but sub-bullet indentations can be compacted for readability.

---

### Positive Observations
- **Exclusive File Ownership:** Phase 07 authored strictly docs and evidence files; exactly 0 lines of production, test, or build code were edited.
- **Honest Gate Preservation:** Every single historical release hold (Gates G2, G3, G6, G7), the 149-checksum dependency baseline, and the Phase 08 Linux gate were transparently maintained across all 17 documents.
- **PDR Alignment:** Accurately refined the 100%-shared-UI claim in `docs/project-overview-pdr.md` to reflect ~95% shared Compose UI with native Glance/platform shells.
- **Security Auditor Test Coverage:** 12/12 Node security tests pass cleanly, strictly enforcing that only `MainActivity` and canonical `DoggoTaskWidgetReceiver` (with `APPWIDGET_UPDATE` and provider metadata) are exported.
- **Tooling Verification:** Hub live-fixture smoke (18/18) and strict-v2 cutover rehearsal (8/8) pass cleanly.

---

### Validation Commands & Results

| Check / Tool | Exact Command | Exit Code | Observed Result |
|---|---|:---:|---|
| **Exported Component Security Auditor** | `node --test scripts/release-security-check.test.mjs` | `0` | **PASS (12/12 passing in 85ms)** |
| **Diagnostic Source Security Audit** | `node scripts/release-security-check.mjs --diagnose-source` | `0` | **PASS (CLEAN; 0 hardcoded secrets, backup=false, HTTPS origin)** |
| **Hub Offline Live-Fixture Smoke** | `node scripts/hub-live-smoke.mjs --fixture-test` | `0` | **PASS (18/18 passing in fixture mode)** |
| **Strict-v2 Cutover Rehearsal Self-Test**| `node scripts/strict-v2-cutover-rehearsal.mjs --self-test` | `0` | **PASS (8/8 passing in 10.97s)** |
| **Git Working Tree Audit** | `git status --short` | `0` | 19 modified docs/plans, 8 untracked evidence/report files, 0 code/test edits |

---

### Metrics
- **Documentation Completeness:** 100% (17 shared docs + 2 summaries + 1 acceptance report updated)
- **File Ownership Adherence:** 100% within Phase 07 manifest
- **Security Auditor Tests:** 12 / 12 passing (100%)
- **Static Security Diagnostic:** CLEAN
- **Gradle Test Suite (Reported):** 364 / 364 passing (231 shared JVM, 99 desktop, 34 Android debug unit)
- **Effort Credited:** 118h / 132h (89.4% overall project progress; Phase 07 credited 6h of 12h)

---

### Recommended Actions
1. **[PHASE 05 HARDENING]** In `DoggoApplication.closeRuntimeInternal()`, escalate delivery gate drain timeout to fail-closed behavior before nulling `activeRuntime` or closing SQLite.
2. **[PHASE 08 GATE]** Conduct Linux-only session verification on actual Linux machine with X11/Wayland and active notification daemon.
3. **[GATE G2 HARDWARE]** Execute interactive launcher and multi-monitor verification on physical Android 14/15 hardware and multi-monitor Windows workstation when attached.
4. **[CLEANUP]** Remove or relocate untracked `android_screen.png` from repository root.

---

### Unresolved Questions
1. **Physical Testbed Availability:** When will physical Android devices (API 34/35) and connected emulators be provisioned to unblock Gate G2?
2. **Phase 08 Linux Environment:** Which Linux distribution and desktop environment (GNOME / KDE / Wayland / X11) will be designated for the Phase 08 StatusNotifierItem tray verification?
3. **Root Checksum Baseline:** When will the 149 unmatched classpath checksums in `gradle/verification-metadata.xml` be updated upstream to restore `--dependency-verification=strict`?
