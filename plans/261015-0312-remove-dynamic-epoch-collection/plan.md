# Implementation Plan: Remove Dynamic Epoch Collection Naming and Reuse Target Collection for TabbyRush

**Plan Directory:** `plans/261015-0312-remove-dynamic-epoch-collection/`  
**Status:** Completed
**Target:** Glean Hub Sync Server & TabbyRush Compose Todos

---

## Overview

Transition Glean Hub's `strict-v1` synchronization storage layer from dynamic epoch collections (`_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`) to a direct target collection (`app_<appId>`, e.g. `app_tabbyrush`). Maintain full CAS revision semantics, schema validation, and push/pull HTTP contracts, while rechecking and updating TabbyRush's tooling, documentation, and client test suites.

---

## Phases

| Phase | Description | Status | Link |
|---|---|---|---|
| **Phase 01** | Backend Collection Resolver & Admission in Glean Hub | Completed | [phase-01-server-collection-naming.md](phase-01-server-collection-naming.md) |
| **Phase 02** | TabbyRush Recovery Tooling & App Recheck | Completed | [phase-02-tabbyrush-scripts-and-app-recheck.md](phase-02-tabbyrush-scripts-and-app-recheck.md) |
| **Phase 03** | Documentation Reconciliation & Full Quality Gates | Completed | [phase-03-documentation-and-final-validation.md](phase-03-documentation-and-final-validation.md) |

---

## Key Dependencies
1. Glean Hub Sync crate: `glean-hub-plugin/glean-hub-sync`
2. TabbyRush Shared & Tooling: `shared/`, `scripts/`, `docs/`
3. JDK 21 toolchain and Cargo 1.80+ toolchain
