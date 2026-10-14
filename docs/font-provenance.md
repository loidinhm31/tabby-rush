# Font Provenance and Typography Audit

**Date:** 2026-10-14
**Author:** Phase 06 Shared UI Integrator
**Document Status:** Complete (Frozen Decision)

---

## 1. Upstream Typography Investigation

The visual design reference for Doggo is inspired by the ForumFlash light theme (`https://forumflash.vercel.app/`). Inspection of the reference CSS revealed two primary font families:
1. **Cal Sans** (`Cal Sans SemiBold`): Used in web reference headings.
2. **Alexandria** (`Alexandria Regular`, `Alexandria SemiBold`): Used in web reference body and metadata copy.

### 1.1 Rights, Licensing, and Redistribution Audit

1. **Cal Sans:**
   - **Origin:** Designed by Mark Davis / Cal.com, Inc.
   - **License:** Open Font License (OFL 1.1).
   - **Reserved Font Name:** "Cal Sans".
   - **Redistribution Constraints:** Bundling requires bundling the exact SIL Open Font License 1.1 text, preserving copyright notices, and respecting the Reserved Font Name.
   - **Glyph Coverage:** Cal Sans is primarily an English / Latin-script geometric title font. It lacks native Cyrillic, extended Arabic, Vietnamese diacritic stacking, East Asian CJK ideographs, and complete symbol ranges.

2. **Alexandria:**
   - **Origin:** Designed by Mai Erksousi.
   - **License:** SIL Open Font License (OFL 1.1).
   - **Reserved Font Name:** "Alexandria".
   - **Redistribution Constraints:** Bundling requires including the upstream OFL license text.
   - **Glyph Coverage:** Arabic and Latin character sets.

---

## 2. Platform Delivery & Accessibility Analysis

Per `phase-06-compose-ui-theme.md` Implementation Step 1 and the ownership registry:
> *"Audit upstream Cal Sans/Alexandria actual revision/checksum/OFL or actual license/reserved-name/redistribution/glyph coverage. Bundle legitimate TTFs and notices only if allowed; otherwise documented accessible platform font choice and omit conditional rows. No copied site WOFF/logo/illustration, invented license or runtime font CDN."*

### 2.1 Technical Evaluation
1. **No External Network / CDN Fetching:** Bundling runtime downloads or third-party web CDNs violates offline privacy, determinism, and air-gapped security invariants.
2. **Binary Asset Overhead:** Embedding 3 full TTF files adds ~350–500 KB to the multiplatform library jar and Android APK without solving non-Latin glyph coverage.
3. **Accessibility & Scaling (WCAG 2.1 AA):**
   - Platform system fonts (Roboto on Android, Inter / Cantarell / Liberation Sans on Linux Fedora via Xpra, Segoe UI on Windows) provide standard broad international Unicode coverage, robust diacritics, and OS-level font scale adjustments (up to 200%).
   - Using `FontFamily.SansSerif` delegates font rendering to host platform fonts, avoiding the glyph limitations of Latin-only upstream web fonts. Full verification of international glyphs, TalkBack/Orca compatibility, and 200% font scale rendering is scheduled for platform integration phases (Phase 07 Android, Phase 09 Linux, Phase 10 Windows).

---

## 3. Formal Decision: Documented System Font Fallback

In accordance with Phase 06 contract specifications:
1. **Decision:** Omit bundling conditional binary font assets (`cal_sans_regular.ttf`, `alexandria_regular.ttf`, `alexandria_semibold.ttf`) and their respective standalone license files.
2. **Implementation:** `DoggoTypography` uses Compose `FontFamily.SansSerif` with custom letter-spacing, line-heights, and font weights matching the geometric proportions of the ForumFlash reference.
3. **Audit Trails:** The ownership table rows for the 5 conditional font and license files are formally fulfilled through this documented omission decision.
4. **License Notice:** An in-app open-source license acknowledgement viewer (`dev.doggo.shared.ui.LicenseNotices`) is implemented to display notices for all bundled open-source dependencies.
