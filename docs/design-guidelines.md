# Doggo Design Guidelines & UI Standards

**Date:** 2026-10-14
**Applies to:** Shared Compose UI (Android, Linux, Windows)
**Status:** Administratively complete per user directive; technical acceptance withheld (Review Cycle 3 score 6.5/10; outstanding integration gaps deferred to Phase 07)

---

## 1. Visual Language & Inspiration

Doggo's shared visual language is derived from the measured ForumFlash light theme (`https://forumflash.vercel.app/`), adapted for native task management.
- **Tone:** Focused, calm, clean geometric aesthetic with deep teal structure, soft mint containers, warm cream headers, and disciplined coral alert accents.
- **Mode:** Accessible light theme only. Dark theme is out of scope for initial multiplatform release.
- **Dynamic Color:** Disabled to avoid OS-level palette overrides that would alter calculated contrast ratios; rendered UI contrast across OEM devices requires Phase 07 device testing.

---

## 2. Design Tokens & Color Palette

### 2.1 Palette Tokens

| Token Name | Hex Code | Material 3 Mapping | Primary Usage |
|---|---|---|---|
| `DoggoPrimary` | `#004646` | `primary` | App bar headers, primary buttons, active selected states |
| `DoggoPrimaryDeep` | `#003838` | `secondary`, `onPrimaryContainer` | Sidebar header, emphasized titles, deep action accents |
| `DoggoMint` | `#27C4B5` | `tertiary` | Decorative accent chips, tab indicator underlines |
| `DoggoMintSurface` | `#E6F0F2` | `primaryContainer`, `surfaceVariant` | Soft container backgrounds, todo item borders, hover fills |
| `DoggoCream` | `#F7F4ED` | `secondaryContainer` | Top header background in compact mode, warm card surfaces |
| `DoggoCoral` | `#C62828` | `error` | Functional dark coral for destructive buttons, error text, alerts |
| `DoggoCoralSurface` | `#FFEBEB` | `errorContainer` | Conflict callouts, error card backgrounds |
| `DoggoLine` | `#D9E3E4` | `outlineVariant` | Decorative thin divider lines (1dp) |
| `DoggoOutline` | `#687677` | `outline` | Functional component borders (meets 3:1 non-text contrast) |
| `DoggoInk` | `#0F172A` | `onSurface`, `onBackground` | Primary typography, body text, dialog titles |
| `DoggoMuted` | `#546269` | `customColors.muted` | Secondary timestamps, hint text, metadata |
| `DoggoWhite` | `#FFFFFF` | `surface` | Card surfaces, dialog backgrounds, input background |
| `DoggoPage` | `#F8FBFA` | `background` | Window root background, page canvas |

### 2.2 Relative Luminance & Contrast Verification (WCAG 2.1 AA)

Calculated ratios below evaluate 16 opaque token pairs using the standard W3C relative luminance formula:
$L = 0.2126 \times R_{lin} + 0.7152 \times G_{lin} + 0.0722 \times B_{lin}$ and $Ratio = (L_1 + 0.05) / (L_2 + 0.05)$.
These mathematical token calculations are verified by automated unit assertions in `dev.doggo.shared.ui.TodoSemanticsTest`. They establish baseline palette contrast thresholds, not exhaustive proof of contrast for every rendered control state or overall WCAG compliance across devices:
| Foreground | Background | Measured Ratio | WCAG Requirement | Status |
|---|---|---|---|---|
| `DoggoInk` (`#0F172A`) | `DoggoPage` (`#F8FBFA`) | **17.15:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoInk` (`#0F172A`) | `DoggoWhite` (`#FFFFFF`) | **17.85:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoInk` (`#0F172A`) | `DoggoCream` (`#F7F4ED`) | **16.25:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoInk` (`#0F172A`) | `DoggoMintSurface` (`#E6F0F2`) | **15.40:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoInk` (`#0F172A`) | `DoggoCoralSurface` (`#FFEBEB`) | **15.59:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoWhite` (`#FFFFFF`) | `DoggoPrimary` (`#004646`) | **10.69:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoWhite` (`#FFFFFF`) | `DoggoPrimaryDeep` (`#003838`) | **12.94:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoWhite` (`#FFFFFF`) | `DoggoCoral` (`#C62828`) | **5.62:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoCoral` (`#C62828`) | `DoggoWhite` (`#FFFFFF`) | **5.62:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoCoral` (`#C62828`) | `DoggoCoralSurface` (`#FFEBEB`) | **4.91:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoMuted` (`#546269`) | `DoggoWhite` (`#FFFFFF`) | **6.31:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoMuted` (`#546269`) | `DoggoMintSurface` (`#E6F0F2`) | **5.44:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoMuted` (`#546269`) | `DoggoCoralSurface` (`#FFEBEB`) | **5.51:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoMuted` (`#546269`) | `DoggoCream` (`#F7F4ED`) | **5.74:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `DoggoPrimaryDeep` (`#003838`) | `DoggoMintSurface` (`#E6F0F2`) | **11.16:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `DoggoOutline` (`#687677`) | `DoggoWhite` (`#FFFFFF`) | **4.72:1** | >= 3.0:1 (Non-Text Controls) | Pass (AA) |

*Critical Accessibility Invariant:* `DoggoMint` (`#27C4B5`) has a contrast ratio of only 2.18:1 on white and is strictly restricted to graphical accents or paired with dark teal (`#003838`).

---

## 3. Typography & Spacing Scales

### 3.1 Typography Scale
- **Display / Headings:** Sans-serif bold geometric styling. Display large is 34sp, headline large is 22sp.
- **Body:** Standard primary body text is strictly `>= 16sp` (`bodyLarge` at 16sp and `bodyMedium` at 16sp). Secondary helper copy, form error text, and metadata use `bodySmall` at 14sp.
- **Metadata / Labels:** Minimum text size is strictly `>= 12sp` (`labelSmall` and `labelMedium` at 12sp; interactive filter chips use `labelLarge` at 14sp).
- **Dynamic Text Scaling:** All typography uses scalable `sp` units to support system font scaling up to `200%`. Verification of layout wrapping (especially in compact top bar and todo rows) requires device-level testing in Phase 07.

### 3.2 Spacing & Radii
- **Spacing Scale:**
  - `SpacingXSmall`: `4.dp` (tight icon/label gaps)
  - `SpacingSmall`: `8.dp` (compact internal padding)
  - `SpacingMedium`: `12.dp` (standard component gap)
  - `SpacingLarge`: `16.dp` (screen edges, card padding)
  - `SpacingXLarge`: `24.dp` (section margins)
  - `SpacingXXLarge`: `32.dp` (modal margins)
- **Corner Radii:**
  - Controls (Buttons, TextFields, Filter Chips): `12.dp`
  - Cards & Modals (Todo items, dialogs, panels): `20.dp`
- **Touch Target Boundary:**
  - All interactive buttons, checkboxes, icon buttons, and list row triggers must meet the minimum touch target requirement of strictly `>= 48.dp`.

---

## 4. Responsive Layout Architecture

Doggo implements a single unified Compose tree with responsive breakpoint adaptation:
1. **Compact Layout (< 840dp):**
   - Vertical layout with scrolling list container.
   - Warm cream top header showing account info, action buttons ("Add", "Notices", "Leave"), and sync feedback. Note: In the current implementation, the top bar is placed within non-scrolling `Scaffold.topBar`; handling of vertical viewport constraints under 200% font scaling or landscape orientation is tracked as a Phase 07 verification item.
   - Horizontal scrolling filter chips (`All`, `Active`, `Completed`).
   - Todo items list with unconstrained title wrapping.
2. **Expanded Desktop Layout (>= 840dp):**
   - Two-column layout with fixed-width deep teal navigation sidebar (`280.dp`).
   - Scrolling sidebar displays user account badge, sync feedback badge, "Open Source Notices" button, and "Leave Account" action.
   - Main workspace displays list container with constrained reading width (`max 760.dp`), centered on the canvas.

---

## 5. Accessibility, Semantics & Interaction Standards

1. **Non-Color Conveyance:** Status (completed, active, conflict, error) does not rely on color alone. Checkboxes visually display checkmarks and emit accessibility state (`stateDescription`). Error callouts include descriptive text.
2. **Keyboard Navigation:** Tab, Shift-Tab, and Enter (submit/activate) navigation support. Dialogs provide explicit Cancel/Dismiss buttons. Hardware Escape key dismiss and system back-handler trapping are deferred to platform integration in Phase 07.
3. **Screen Readers (TalkBack, Orca, Narrator):**
   - Explicit semantics: distinct `contentDescription` on interactive actions (e.g., `"Edit [Title]"`).
   - Heading roles on section titles.
   - Live regions (`LiveRegionMode.Polite`) on dynamic sync status and form errors.
4. **Credential Security & Privacy:**
   - Password fields use `PasswordVisualTransformation` for visual masking.
   - Passwords include `semantics { password() }` intended to guide accessibility services to suppress plain speech, though actual screen-reader behavior remains unverified on physical devices.
   - Transient password strings are dereferenced from UI component state upon form submission, mode toggling, or composable disposal. Note that because JVM strings are immutable, in-memory erasure cannot be cryptographically guaranteed.
   - `AuthSubmitAction.toString()` masks passwords with `[REDACTED]` to prevent accidental logging.

---

## 6. Phase 02 Frozen Contract Gaps & Downstream Deferrals

Per project dependency architecture, Phase 02 contracts (`shared/src/commonMain/kotlin/dev/doggo/shared/contract/`) and root dependency configurations are frozen. Phase 06 UI consumes these contracts as specified, noting the following integration gaps:

1. **Cursor Pagination & Load More:** `TodoUiState` currently exposes a flat `items: List<Todo>` without pagination cursors or a `LoadMore` action in `TodoAction`. Infinite scroll / pagination triggers await contract amendment.
2. **UnknownSuperseded & Reset Counts:** `SyncProgress` does not expose separate counter fields for `unknownSupersededCount` or `resetReviewCount`. The UI renders `SyncStatus.RESET_REQUIRED` and `SyncStatus.UNCERTAIN` chips in `SyncFeedback`, and displays dedicated guidance banners when `SafeError.ResetRequired` or `SafeError.ProtocolIncompatible` are present.
3. **LeaveGuardState Scope:** `LeaveGuardState` exposes `pendingCount`, `uncertainCount`, and `conflictCount`. Drafts count and old-epoch review tallies are not present in the Phase 02 contract; the leave dialog renders all available tallies and warns of local wipe risks.
4. **Admin Identity Representation:** `AccountIdentity` contains `(userId, username, email)` without an `isAdmin` boolean. The UI currently uses a username/email substring check (`contains("admin", ignoreCase)`); a formal contract amendment (`AccountIdentity.isAdmin` or `TodoUiState.adminSession`) is deferred to Phase 07.
5. **Conflict Deferral Semantics (`DismissError`):** `ConflictReview` invokes `TodoAction.DismissError` when the user selects "Decide Later". Introducing a dedicated `TodoAction.DeferConflict` action is deferred to Phase 07 integration.
6. **Rendered Compose UI Testing & Harness:** The Phase 02 shared build does not declare `compose.uiTest` dependencies. As designated in `phase-06-compose-ui-theme.md`, semantic unit validation is authored in `TodoSemanticsTest` (validating contrast math, action `toString` redaction, `TitlePolicy` validation, and state properties), while full rendered UI automation, TalkBack screen-reader testing, IME Done handling, and OS-level 200% font scale validation in compact top bar and todo rows are deferred to platform integration phases (Phase 07 Android, Phase 09 Linux, Phase 10 Windows).
7. **Resource Strings (`strings.xml`):** The `strings.xml` resource catalog is defined under `composeResources/values/strings.xml`, but UI strings currently use direct inline text. Full wiring to `stringResource` is scheduled for release operations (Phase 08).
8. **Reauth and Sync Recovery States:** Detailed multi-step reauth flows and explicit copy differentiation between regular conflicts and UnknownSuperseded mutations are deferred to Phase 07.
9. **Compact Top Bar & Todo Row at 200% Scale:** The compact top bar is currently non-scrolling in `Scaffold.topBar`, and todo row actions are laid out in a fixed `Row`. Verification and layout adjustments for large font scales are deferred to Phase 07 device testing.
10. **Missing States & UI Granularity:** REAUTH local-only data access with prefilled email, distinct copy for UnknownSuperseded vs regular conflicts, and explicit sync-then-leave options in `LeaveGuardDialog` are deferred to Phase 07.
