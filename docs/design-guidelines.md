# TabbyRush Design Guidelines & UI Standards

**Date:** 2026-10-17
**Applies to:** Shared Compose UI (Android, Linux, Windows) and Android/desktop task-surface presentation
**Status:** Token contrast calculations are automated; rendered UI/accessibility acceptance is not established by Phase 07 design baselines and remains platform-specific.

---

## 1. Visual Language & Inspiration

TabbyRush's shared visual language is derived from the measured ForumFlash light theme (`https://forumflash.vercel.app/`), adapted for native task management.
- **Tone:** Focused, calm, clean geometric aesthetic with deep teal structure, soft mint containers, warm cream headers, and disciplined coral alert accents.
- **Mode:** Accessible light theme only. Dark theme is out of scope for initial multiplatform release.
- **Dynamic Color:** Disabled to avoid OS-level palette overrides that would alter calculated contrast ratios. Calculations are automated; rendered UI contrast across OEM devices remains pending platform acceptance.

---

## 2. Design Tokens & Color Palette

### 2.1 Palette Tokens

| Token Name | Hex Code | Material 3 Mapping | Primary Usage |
|---|---|---|---|
| `TabbyRushPrimary` | `#004646` | `primary` | App bar headers, primary buttons, active selected states |
| `TabbyRushPrimaryDeep` | `#003838` | `secondary`, `onPrimaryContainer` | Sidebar header, emphasized titles, deep action accents |
| `TabbyRushMint` | `#27C4B5` | `tertiary` | Decorative accent chips, tab indicator underlines |
| `TabbyRushMintSurface` | `#E6F0F2` | `primaryContainer`, `surfaceVariant` | Soft container backgrounds, todo item borders, hover fills |
| `TabbyRushCream` | `#F7F4ED` | `secondaryContainer` | Top header background in compact mode, warm card surfaces |
| `TabbyRushCoral` | `#C62828` | `error` | Functional dark coral for destructive buttons, error text, alerts |
| `TabbyRushCoralSurface` | `#FFEBEB` | `errorContainer` | Conflict callouts, error card backgrounds |
| `TabbyRushLine` | `#D9E3E4` | `outlineVariant` | Decorative thin divider lines (1dp) |
| `TabbyRushOutline` | `#687677` | `outline` | Functional component borders (meets 3:1 non-text contrast) |
| `TabbyRushInk` | `#0F172A` | `onSurface`, `onBackground` | Primary typography, body text, dialog titles |
| `TabbyRushMuted` | `#546269` | `customColors.muted` | Secondary timestamps, hint text, metadata |
| `TabbyRushWhite` | `#FFFFFF` | `surface` | Card surfaces, dialog backgrounds, input background |
| `TabbyRushPage` | `#F8FBFA` | `background` | Window root background, page canvas |

### 2.2 Relative Luminance & Contrast Verification (WCAG 2.1 AA)

Calculated ratios below evaluate 16 opaque token pairs using the standard W3C relative luminance formula:
$L = 0.2126 \times R_{lin} + 0.7152 \times G_{lin} + 0.0722 \times B_{lin}$ and $Ratio = (L_1 + 0.05) / (L_2 + 0.05)$.
These mathematical token calculations are verified by automated unit assertions in `dev.tabbyrush.shared.ui.TodoSemanticsTest`. They establish baseline palette contrast thresholds, not exhaustive proof of contrast for every rendered control state or overall WCAG compliance across devices:
| Foreground | Background | Measured Ratio | WCAG Requirement | Status |
|---|---|---|---|---|
| `TabbyRushInk` (`#0F172A`) | `TabbyRushPage` (`#F8FBFA`) | **17.15:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushInk` (`#0F172A`) | `TabbyRushWhite` (`#FFFFFF`) | **17.85:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushInk` (`#0F172A`) | `TabbyRushCream` (`#F7F4ED`) | **16.25:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushInk` (`#0F172A`) | `TabbyRushMintSurface` (`#E6F0F2`) | **15.40:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushInk` (`#0F172A`) | `TabbyRushCoralSurface` (`#FFEBEB`) | **15.59:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushWhite` (`#FFFFFF`) | `TabbyRushPrimary` (`#004646`) | **10.69:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushWhite` (`#FFFFFF`) | `TabbyRushPrimaryDeep` (`#003838`) | **12.94:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushWhite` (`#FFFFFF`) | `TabbyRushCoral` (`#C62828`) | **5.62:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushCoral` (`#C62828`) | `TabbyRushWhite` (`#FFFFFF`) | **5.62:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushCoral` (`#C62828`) | `TabbyRushCoralSurface` (`#FFEBEB`) | **4.91:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushMuted` (`#546269`) | `TabbyRushWhite` (`#FFFFFF`) | **6.31:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushMuted` (`#546269`) | `TabbyRushMintSurface` (`#E6F0F2`) | **5.44:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushMuted` (`#546269`) | `TabbyRushCoralSurface` (`#FFEBEB`) | **5.51:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushMuted` (`#546269`) | `TabbyRushCream` (`#F7F4ED`) | **5.74:1** | >= 4.5:1 (Normal Text) | Pass (AA) |
| `TabbyRushPrimaryDeep` (`#003838`) | `TabbyRushMintSurface` (`#E6F0F2`) | **11.16:1** | >= 4.5:1 (Normal Text) | Pass (AAA) |
| `TabbyRushOutline` (`#687677`) | `TabbyRushWhite` (`#FFFFFF`) | **4.72:1** | >= 3.0:1 (Non-Text Controls) | Pass (AA) |

*Critical Accessibility Invariant:* `TabbyRushMint` (`#27C4B5`) has a contrast ratio of only 2.18:1 on white and is strictly restricted to graphical accents or paired with dark teal (`#003838`).

---

## 3. Typography & Spacing Scales

### 3.1 Typography Scale
- **Display / Headings:** Sans-serif bold geometric styling. Display large is 34sp, headline large is 22sp.
- **Body:** Standard primary body text is strictly `>= 16sp` (`bodyLarge` at 16sp and `bodyMedium` at 16sp). Secondary helper copy, form error text, and metadata use `bodySmall` at 14sp.
- **Metadata / Labels:** Minimum text size is strictly `>= 12sp` (`labelSmall` and `labelMedium` at 12sp; interactive filter chips use `labelLarge` at 14sp).
- **Dynamic Text Scaling:** All typography uses scalable `sp` units to support system font scaling up to `200%`. Layout wrapping and accessibility at large font scales remain unverified on native devices; the Phase 07 sticky-surface evidence does not close this gate.

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

TabbyRush implements a single unified Compose tree with responsive breakpoint adaptation:
1. **Compact Layout (< 840dp):**
   - Vertical layout with scrolling list container.
   - Warm cream top header showing account info, action buttons ("Add", "Notices", "Leave"), and sync feedback. The top bar is in non-scrolling `Scaffold.topBar`; viewport constraints at 200% font scale or landscape remain unverified.
   - Horizontal scrolling filter chips (`All`, `Active`, `Completed`).
   - Todo items list with unconstrained title wrapping.
2. **Expanded Desktop Layout (>= 840dp):**
   - Two-column layout with fixed-width deep teal navigation sidebar (`280.dp`).
   - Scrolling sidebar displays user account badge, sync feedback badge, "Open Source Notices" button, and "Leave Account" action.
   - Main workspace displays list container with constrained reading width (`max 760.dp`), centered on the canvas.

---

## 5. Accessibility, Semantics & Interaction Standards

1. **Non-Color Conveyance:** Status (completed, active, conflict, error) does not rely on color alone. Checkboxes visually display checkmarks and emit accessibility state (`stateDescription`). Error callouts include descriptive text.
2. **Keyboard Navigation:** Tab, Shift-Tab, and Enter (submit/activate) navigation support. Dialogs provide explicit Cancel/Dismiss buttons. Hardware Escape and system back-handler behavior require platform acceptance and remain unverified.
3. **Screen Readers (TalkBack, Orca, Narrator):**
   - Explicit semantics: distinct `contentDescription` on interactive actions (e.g., `"Edit [Title]"`).
   - Heading roles on section titles.
   - Live regions (`LiveRegionMode.Polite`) on dynamic sync status and form errors.
4. **Credential Security & Privacy:**
   - Password fields use `PasswordVisualTransformation` for visual masking.
   - Passwords include `semantics { password() }` intended to guide accessibility services to suppress plain speech, though actual screen-reader behavior remains unverified on physical devices.
   - Transient password strings are dereferenced from UI component state upon form submission, mode toggling, or composable disposal. Note that because JVM strings are immutable, in-memory erasure cannot be cryptographically guaranteed.
   - `AuthSubmitAction.toString()` masks passwords with `[REDACTED]` to prevent accidental logging.

### 5.1 Deadline and Reminder Date/Time Pickers

- Use labeled `DateTimePickerButton` triggers for the four temporal controls; each is a >=48dp button with a visible label/value, never editable text.
- Select dates with Material 3 `DatePicker`, `showModeToggle = false`, and `yearRange = 0..9999`. Normalize initial and selected milliseconds through UTC midnight and retain canonical `YYYY-MM-DD` dates.
- Select times with the Material 3 clock dial only (`TimePicker` without `TimeInput`) in 24-hour mode; retain canonical `HH:mm` values. Picker confirmation updates the editor/settings draft; settings defaults persist only on outer Save, and Cancel/dismissal does not commit a tentative selection.
- Keep timezone and numeric reminder parameters as their existing input controls. Deadline date/time/anchor changes continue through `DeadlineCalculatorHelpers` and `StrictTimeZoneResolver`, preserving DST gap rejection and explicit overlap-offset disambiguation.
- Pure conversion/formatting and model tests do not establish rendered picker interaction coverage. Keep technical acceptance conditional until rendered Compose semantics/interactions and device keyboard, focus, screen-reader, and scaling behavior are verified.

---

## 6. Phase 02 Frozen Contract Gaps & Downstream Deferrals

Per project dependency architecture, Phase 02 contracts (`shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/`) and root dependency configurations are frozen. Phase 06 UI consumes these contracts as specified, noting the following integration gaps:

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

---

## 7. Sticky Task Surfaces & Companion UI Standards (Phase 07)

### 7.1 Android Glance Task Widget
- **Responsive Grid Layouts**: Uses Glance `SizeMode.Responsive` for 120×120dp (2×2), 240×120dp (3×2), 240×240dp (3×3), and 320×360dp (4×4+) grid configurations.
- **Section Partitioning**: A single scrollable column partitioned into Incomplete tasks and Completed tasks in stable `createdAt DESC, id DESC` order.
- **Touch Targets**: All interactive controls (checkboxes, Add button, Open App icon) adhere strictly to >= 48dp minimum touch bounds.
- **Privacy-Preserving Non-Ready States**: When unauthenticated, locked, or during initial cold load (`WidgetDisplayState.Loading`, `SignedOut`, `Locked`, `Unavailable`), the widget displays clean generic guidance with **zero private task titles** rendered.

### 7.2 Quiet Deadline Notification
- **Quiet System Alerts**: Low-importance channel (`tabbyrush_task_surfaces`), sound and vibration disabled.
- **WCAG AA Status Badging**: Overdue deadlines use high-contrast `TabbyRushCoral` (`#C62828`) text on `TabbyRushCoralSurface` (`#FFEBEB`), exceeding 4.5:1 text contrast.
- **Lockscreen Masking**: Private view displays full task title; lockscreen public version (`setPublicVersion`) renders generic text ("Task deadline reminder", "Unlock device to view task title and details") protecting sensitive task titles.

### 7.3 Desktop Sticky Companion Window
- **Always-On-Top Chrome**: Pinned companion window floating `alwaysOnTop = true` above ordinary desktop workspaces.
- **Independent Quick-Add**: Inline text input validated against `TitlePolicy` (1–200 Unicode scalars), submitting on `Enter` or `Add` click without overwriting unsaved drafts in the main window.
- **Permanent Top Recovery Controls**: Window header embeds explicit `Open App` and `Quit` buttons to ensure application control is retained even if OS window controls are obstructed.

### 7.4 Acceptance Evidence Boundary
The sticky-surface PNGs in `docs/evidence/` are visual design baselines only, not native application captures or proof of launcher, notification-shade, window-stacking, tray, screen-reader, or multi-monitor behavior. See the [Android acceptance evidence](./evidence/sticky-surfaces-android-summary.md) and [Windows acceptance evidence](./evidence/sticky-surfaces-windows-summary.md) for the recorded automated scope and Gate G2 holds.

---

## 8. Date & Time Picker Controls (Date & Time Picker Migration)

The date and time selection surfaces replace manual text entry with Material 3 calendar and clock dialogs:
- **Trigger Component (`DateTimePickerButton`):** Stacked label and formatted value; full-width clickable container enforcing `>= 48.dp` minimum touch target (`TabbyRushThemeTokens.MinTouchTargetSize`). Semantics declare `Role.Button` and `stateDescription`.
- **Calendar Modal (`TabbyRushDatePickerDialog`):** Compose Material 3 DatePicker with input-mode toggle disabled (`showModeToggle = false`), enforcing calendar-only selection. Normalized to UTC midnight and configured for years `0..9999`.
- **Clock Modal (`TabbyRushTimePickerDialog`):** Compose Material 3 TimePicker dial in 24-hour format (`is24Hour = true`), wrapped in a scrollable dialog container to accommodate compact displays without obstructing action buttons.
- **Verification Boundary:** Palette contrast and UTC date/time helper conversions are verified by automated unit tests. Rendered Compose interaction tests and on-device TalkBack/Narrator screen-reader verification remain platform acceptance tasks.

---

## 9. TabbyRush App Icon & Visual Identity

### 9.1 Concept: "TabbyRush: The Sprinting Mascot & Milestone Trail"
The official visual identity of TabbyRush embodies focus, speed, and playful task completion:
- **Mascot Character & Stance:** A chubby brown domestic tabby cat poised in a low runner's preparation crouch—strong planted front paws, muscular hind legs, soft primordial belly pouch, alert ears, and focused, earnest green eyes fixed forward.
- **Milestone Trail & Finish Flag:** Curving behind the mascot is a dynamic dotted progress trail featuring mint-cyan circular task checkpoint badges and paw prints, culminating in a triumphant orange finish flag at top-right, grounded by energetic speed streaks.
- **Desktop & Master Art:** Master composite features the mascot illustration centered on a crisp, rounded squircle canvas (`#FFFFFF` with `#D9E3E4` border and soft ambient drop shadow) to ensure maximum contrast and legibility across dark and light OS desktop taskbars and wallpapers.

### 9.2 Android Adaptive Icon Geometry (minSdk 26)
- **Canvas Viewport:** 108×108 dp canvas (rendered at 432×432 px at xxxhdpi).
- **Background Layer (`ic_launcher_background.xml`):** Solid `#FFFFFF` vector path covering the full 108×108 dp.
- **Foreground Layer (`ic_launcher_foreground.png`):** Transparent 32-bit RGBA bitmap featuring the TabbyRush mascot and milestone trail centered strictly within the inner **66 dp-diameter guaranteed safe circle** (radius 33.0 dp / 132 px from center `(216, 216)`). The artwork's maximum measured pixel radius from center across all non-transparent pixels is **31.02 dp (124.08 px)**, providing an explicit **1.98 dp (7.92 px) margin of safety**. Exactly zero pixels sit outside the 66 dp boundary, preventing clipping under circular, squircle, pebble, or rounded-rectangle OEM launcher masks.
- **Adaptive Mipmaps:** Referenced via `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` in `androidApp/src/main/AndroidManifest.xml`.

### 9.3 Desktop Runtime & Packaging Icons
- **Desktop Window & Taskbar Icon:** `desktopApp/src/main/resources/tabbyrush-icon.png` (256×256 RGBA PNG). Configured on both `MainWindow` and `StickyWindow` via `painterResource("tabbyrush-icon.png")`.
- **Desktop System Tray:** `desktopApp/src/linuxMain/resources/task-surface-tray.png` and `desktopApp/src/windowsMain/resources/task-surface-tray.png` (32×32 RGBA PNG). Features high-contrast optical artwork utilizing rows 4–26 and columns 3–28 for crisp legibility on dark and light system taskbars.
- **Windows Packaging ICO:** `desktopApp/src/windowsMain/resources/tabbyrush.ico` (multi-resolution ICO embedding 16×16, 24×24, 32×32, 48×48, 64×64, 128×128, and 256×256 32-bit RGBA frames). Configured in `desktopApp/build.gradle.kts` under `compose.desktop.application.nativeDistributions.windows.iconFile`.
- **Linux Packaging PNG:** `desktopApp/src/main/resources/tabbyrush-icon.png` configured under `nativeDistributions.linux.iconFile`.

### 9.4 Canonical Master Vector
- **Master SVG Source:** `docs/assets/tabbyrush-app-icon.svg` (512×512 SVG with semantic group layers for desktop background squircle and TabbyRush mascot).

---

## 10. Server Admin Announcements UI & Accessibility Standards (Phase 12)

### 10.1 In-App Inbox Access & Unread Badging
- **Top Bar Entry**: Accessible via an announcement bell icon on the primary application bar.
- **Touch Targets**: Minimum interactive bounds of $\ge 48\text{dp}$ (`TabbyRushThemeTokens.MinTouchTargetSize`).
- **Unread Indicator**: Visual badge displays numeric unread count when $>0$. Semantics explicitly declare `contentDescription = "Announcements, %d unread"` for TalkBack/Orca accessibility.

### 10.2 Inbox List & Detail Presentation
- **Card Layout & Typography**: Announcements are presented in a vertical scrollable column in descending creation order (`createdAt DESC`).
- **Non-Color State Indicators**: Unread items feature a primary dot indicator and an explicit text badge ("Unread"), ensuring status is legible without relying solely on color vision.
- **Literal Plain-Text Rendering**: Announcement titles and bodies are rendered as literal plain text using `Text()` composables. HTML tags, Markdown formatting, and URL execution are disabled to eliminate injection vectors.
- **Detail Navigation**: Tapping an item opens the full detail screen/dialog and triggers an idempotent `PUT /read` request. The dialog displays normalized title, body, author, creation timestamp, and 90-day expiration date in the local timezone.

### 10.3 Offline, Revoked & Teardown UX
- **Safe Offline State**: When network connectivity is lost, the inbox displays an informative banner ("Offline — announcements unavailable"). Opening detail views is disabled to prevent presenting stale or unverified content.
- **Revocation / Signed-Out UX**: When an account grant is revoked (`appAccessRevoked`) or the user signs out, all in-memory announcement state is wiped immediately. The UI renders a clean signed-out placeholder with zero residual text.
- **Native Tap Deep Linking**: Tapping an OS notification (Android notification shade or Linux desktop alert) opens TabbyRush, verifies current active account authorization, and opens the requested announcement directly.
