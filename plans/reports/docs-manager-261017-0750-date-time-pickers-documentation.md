# Date & Time Picker Documentation Update Report

**Date:** 2026-10-17  
**Scope:** Calendar and clock picker migration documentation in `docs/`  
**Outcome:** Requested architecture, inventory, changelog, and roadmap updates completed. Picker behavior is documented as delivered; technical acceptance remains conditional pending rendered Compose interaction tests and device/accessibility checks.

## Current State Assessment

- Before this update, the architecture and changelog described manual deadline date/time inputs; the shared UI inventory omitted the new picker and helper-test files; the development roadmap had no date/time picker migration item.
- Source review confirms shared Material 3 `DoggoDatePickerDialog` and `DoggoTimePickerDialog`, button triggers, disabled date input-mode toggle, clock dial without `TimeInput`, UTC-midnight date conversion, canonical `YYYY-MM-DD` / `HH:mm` output, and date-picker year range `0..9999`. Existing DST gap/overlap handling remains in the deadline calculation path.
- The latest picker code-review report records 372/372 Gradle unit tests, Android `assembleDebug`, desktop compilation, and 32/32 Node checks passing. The plan records these unit/build results predate the current `yearRange = 0..9999` configuration, and no post-change rerun is evidenced. Rendered Compose interaction tests are absent and native device/accessibility acceptance remains open. No tests were rerun during this documentation pass.
- The earlier QA report records two failures in its first run; the later code-review report says full reruns passed but does not explain the transition. Preserve the latest reported pass status while keeping this evidence discrepancy visible.

## Changes Made

- `docs/system-architecture.md`: Documented picker components in the shared Compose UI, four no-typing controls, UTC-midnight normalization, canonical formats, configured 0..9999 year range, and conditional acceptance status.
- `docs/codebase-summary.md`: Added `date-time-pickers.kt` and `date-time-pickers-test.kt` to the shared UI and test inventories; refreshed Repomix snapshot metrics.
- `docs/project-changelog.md`: Added the 2026-10-17 picker migration entry and report-attributed verification/acceptance status.
- `docs/development-roadmap.md`: Added a cross-cutting Date & Time Picker Migration item linked to its plan, with unit/build verification delivered and the technical acceptance gate open.
- `docs/design-guidelines.md`: Added the picker interaction rules, input-mode restrictions, confirmation boundary, retained DST behavior, and testing/acceptance boundary. This extra update satisfies the Phase 04 plan's documentation scope for test limitations.
- Regenerated `repomix-output.xml` with Repomix v1.18.1. Its output reports 518 included files, 6 security-scanner exclusions, 1,189,124 tokens, and 5,277,366 characters; the compaction is an allowlist, not a full-repository snapshot.

## Gaps and Recommendations

1. Add rendered Compose interaction tests for all four triggers, semantics, confirmation/cancel behavior, and absence of editable date/time nodes. Keep technical acceptance conditional until those tests exist.
2. Complete native Android and desktop keyboard/focus, screen-reader, font-scaling, and device interaction acceptance; do not equate unit tests or `assembleDebug` with platform acceptance.
3. Reconcile the initial red QA report with the later passing reruns by recording the fix and exact rerun evidence in the QA artifact.
4. The documentation validator is warning-heavy: its output reports many code-reference and uppercase-token/config-key warnings. Validate new code names directly against source; do not rewrite accurate technical identifiers merely to satisfy its regex heuristics.

## Validation and Metrics

- The published fallback validator ran against `docs/` because `.omp/evcrate/scripts/validate-docs.cjs` is absent in the checkout. Final run checked 29 top-level Markdown files: 217 internal links, 32 code references, and 10 config keys validated; no broken-link warnings were reported. It emitted 1,512 code-reference and 553 config-key warnings. Its default source paths omit the Kotlin module roots and its uppercase-token pattern treats Android/protocol constants as config keys, so the warnings are noisy; the picker identifiers were checked directly against source.
- The docs inventory contains 37 Markdown files across `docs/` and `docs/evidence/`, totaling 7,664 LOC after these changes. Updated files remain under the 800-LOC target: architecture 725, codebase summary 654, changelog 770, development roadmap 36, design guidelines 165.
- **Requested documentation coverage:** 4/4 named files updated (100%); one additional planned design-guideline file updated. This was a focused review of the affected documents and picker source, not a line-by-line audit of every documentation file.
- **Update date:** 2026-10-17. Historical update cadence is not inferred.
- **Maintenance status:** Documentation reflects delivered picker code and keeps conditional technical acceptance and evidence gaps explicit.

## Unresolved Questions

- What exact fix caused the two failures in the initial QA run to pass in subsequent full reruns, and can the QA report record that evidence?
- When will rendered Compose interaction tests and native device/accessibility acceptance be completed so the technical gate can close?
