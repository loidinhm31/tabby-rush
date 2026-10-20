# Plan: Strict Schema Update UI & Backend Support

**Plan ID**: `plans/261020-1131-strict-schema-update-ui`

**Date**: 2026-10-20

**Target Systems**: `glean-hub-app` (React/Vite admin UI), `glean-hub-sync` (Rust backend sync plugin)

**Status**: In Progress

---

## 1. Executive Summary & Problem Analysis

### 1.1 The Problem
In `glean-hub-app`, when navigating to edit a strict sync application (e.g., `tabby-rush` or `doggo` with `syncMode` starting with `strict-`):
1. **No Support for strict-v3**:
   - `strict-app-upgrade-panel.tsx` hardcodes `expectedVersion: "strict-v1"` and `targetVersion: "strict-v2"`.
   - When an app is already on `strict-v2` (such as `tabby-rush` in the current database), the UI displays "Application Active on strict-v2" and hides all upgrade controls.
   - There are no controls or workflows to initiate or preview an upgrade from `strict-v2` to `strict-v3` (17 columns, nullable `parentTaskId`).
2. **Hardcoded Fingerprints**:
   - `sync-admin-service.ts` hardcodes canonical fingerprints for `doggo` only (`3361a80f...` and `5f539551...`).
   - For `tabby-rush`, the canonical fingerprints differ (`132f914f...`, `8d641c2d...`, `7f033e7b...`), causing upgrade requests to fail with fingerprint mismatch conflicts.
3. **No Schema Diff or Evolution Preview**:
   - Operators cannot preview which columns are added or changed in the target protocol version before initiating a migration.
4. **Backend Target Version Support**:
   - The Rust backend (`glean-hub-sync/src/services/strict_upgrade_service.rs`) currently restricts `req.target_version == PROTOCOL_V2`. To allow end-to-end execution of `strict-v2` $\rightarrow$ `strict-v3`, the backend must recognize `PROTOCOL_V3`, validate its 17 canonical columns, compute the canonical fingerprint, and perform the batch transform (setting `parentTaskId: null` where `$exists: false`).

---

## 2. Preflight Contract

- **Output**:
  - `glean-hub-sync`: Models and service logic supporting `strict-v3` schema definitions, canonical fingerprints, and in-place batch migration (`strict-v2` $\rightarrow$ `strict-v3`).
  - `sync-admin-service.ts`: Protocol definitions, canonical column maps, dynamic fingerprint resolution for `tabby-rush` and `doggo`, and multi-version upgrade request types.
  - `strict-contract-card.tsx`: Enhanced active contract card showing active version badge, canonical fingerprint match status, and complete column breakdown with types.
  - `strict-upgrade-controls.tsx`: Enhanced controls component supporting version target selection (`strict-v1` $\rightarrow$ `strict-v2`, `strict-v2` $\rightarrow$ `strict-v3`), visual schema diff table, dry-run results, and migration state machine progress with same-ID resume.
  - `strict-app-upgrade-panel.tsx`: Unified controller orchestrating contract queries, preflight dry-runs, authoritative upgrades, bounded status polling, and post-publication read-back assertions.
  - `edit-app.tsx`: Clean routing and clear messaging for strict apps.

- **Acceptance Criteria**:
  1. Strict apps on `strict-v1` offer upgrade path to `strict-v2`.
  2. Strict apps on `strict-v2` offer upgrade path to `strict-v3` with clear diff showing added `parentTaskId: string (nullable)`.
  3. Canonical fingerprints resolve dynamically per `appId` (`tabby-rush` vs `doggo`).
  4. Preflight dry-run executes cleanly and displays record counts.
  5. Authoritative migration executes through `fencing` $\rightarrow$ `transforming` $\rightarrow$ `validating` $\rightarrow$ `publishing` $\rightarrow$ `ready-v3`.
  6. Read-back assertion confirms active `syncMode: strict-v3` and matching SHA-256 fingerprint.
  7. All backend unit tests pass (`cargo test -p glean-hub-sync`).
  8. Frontend builds with zero TypeScript errors (`pnpm --filter glean-hub-app build`).

- **Scope Boundary**:
  - In scope: `glean-hub-app` admin components and API service, `glean-hub-sync` backend strict upgrade models and service.
  - Out of scope: changing generic non-strict document schema editors.

- **Risk & Invariants**:
  - `strictEpoch` must never be altered during upgrade.
  - Tombstones (`deleted: true`, `data: {}`) must remain compact.
  - Non-strict apps must not be affected.

---

## 3. Work Breakdown & Implementation Phases

### Phase 1: Backend strict-v3 Support (`glean-hub-sync`)
1. In `models/strict_upgrade.rs`:
   - Define `pub const PROTOCOL_V3: &str = "strict-v3";`
   - Define canonical columns for `strict-v3`: 17 columns (16 v2 columns + `parentTaskId: string, nullable: true`).
   - Implement `canonical_v3_columns()`, `canonical_v3_schema_def()`, and `canonical_v3_schema_json(app_id)`.
   - Update canonical fingerprint constants and tests.
2. In `services/strict_upgrade_service.rs`:
   - Support `target_version == PROTOCOL_V3` with `expected_version == PROTOCOL_V2`.
   - Implement batch transform for v2 $\rightarrow$ v3: updates documents lacking `parentTaskId` with `parentTaskId: null`.
   - Update validation and publication state transitions to `ready-v3`.

### Phase 2: Frontend Service Layer (`sync-admin-service.ts`)
1. Add `PROTOCOL_VERSIONS` and canonical fingerprint lookup table:
   ```ts
   export const CANONICAL_FINGERPRINTS: Record<string, Record<string, string>> = {
     "tabby-rush": {
       "strict-v1": "132f914fc1a9856d5c9f2be8a145b2c41ddb1db1393efa76546f82e253924cd8",
       "strict-v2": "8d641c2de4142e6f62f6691d6fe91559076f6c96f7437b9467577075fd72c653",
       "strict-v3": "7f033e7b432a95e2e95e25fc32ea469a5d6715ea347a88b2edbf9264e6b88a8f",
     },
     "doggo": {
       "strict-v1": "3361a80fdbc304a09d7847fad90841f82a3617b143a6a6eb8273d8c9d41fadec",
       "strict-v2": "5f53955105701cc8c778f4189a2f9a4b90787e36763d62133e11616cf801f6e4",
       "strict-v3": "eec72663e7367b4330d1c94196f8e44888df02fa34e240fa4d3bd29222136094",
     },
   };
   ```
2. Add canonical columns definitions for v1 (4 cols), v2 (16 cols), v3 (17 cols) for schema diff computation.
3. Update `MigrationState` type to include `"ready-v3"`.
4. Export helper `getCanonicalFingerprint(appId: string, version: string): string`.
5. Export helper `getAvailableNextVersion(currentVersion: string): string | null`.

### Phase 3: UI Enhancement in `glean-hub-app`
1. `strict-contract-card.tsx`:
   - Show badge for active version (`strict-v1`, `strict-v2`, `strict-v3`).
   - Add verification badge confirming whether active fingerprint matches known canonical fingerprint.
   - Display total column count and clean column chips.
2. `strict-upgrade-controls.tsx`:
   - When next version is available (`strict-v1` $\rightarrow$ `strict-v2` or `strict-v2` $\rightarrow$ `strict-v3`):
     * Show header: "Schema Upgrade: {sourceVersion} $\rightarrow$ {targetVersion}".
     * Render **Schema Diff Preview**: list newly added columns with green badges (e.g. `+ parentTaskId (string, nullable)`).
     * Render Migration Safety & Invariants banner.
     * Buttons: "Preflight Dry Run" and "Start Upgrade to {targetVersion}".
     * Progress bar & live state machine display.
     * "Resume Migration" input.
   - When app is on latest version (`strict-v3`):
     * Display a clean "Latest Protocol Active" confirmation banner.
3. `strict-app-upgrade-panel.tsx`:
   - Dynamically compute `nextVersion`, source/target fingerprints using `getCanonicalFingerprint`.
   - Update polling logic to handle `ready-v2` and `ready-v3`.
   - Store and resume in-flight migrations cleanly.

### Phase 4: Verification & Smoke Test
1. Run `cargo test -p glean-hub-sync`.
2. Run `pnpm --filter glean-hub-app test:unit` and `pnpm --filter glean-hub-app build`.
3. Restart `glean_hub_server` and `glean_hub_app`.
4. Verify UI in browser / curl probes.
