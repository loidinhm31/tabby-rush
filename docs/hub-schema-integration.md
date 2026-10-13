# Doggo Hub Schema Integration

Date: 2026-10-14  
Phase: Phase 03 Strict Server & Legacy Isolation (Implementation Complete; Runtime Acceptance Pending)  
Related Docs: [Hub Compatibility Acceptance](./hub-compatibility-acceptance.md) · [API Contract](./api-contract.md) · [System Architecture](./system-architecture.md) · [Code Standards](./code-standards.md) · [Evidence Summary](./evidence/hub-compatibility-summary.md)

---
## Overview
Doggo integrates with Glean Hub using the opt-in `strict-v1` synchronization protocol. This integration establishes complete namespace and logic isolation from legacy sync applications while reusing Hub's dual-authentication and app registry infrastructure.

## Schema Specification
Strict apps register with an immutable, strict schema containing exclusively the `todos` table:

```json
{
  "appId": "doggo",
  "syncMode": "strict-v1",
  "schema": {
    "todos": {
      "columns": [
        { "name": "id", "type": "string", "nullable": false },
        { "name": "title", "type": "string", "nullable": false },
        { "name": "completed", "type": "boolean", "nullable": false },
        { "name": "createdAt", "type": "integer", "nullable": false }
      ],
      "primaryKey": "id"
    }
  }
}
```

## Registration & Admission Lifecycle
1. **Admin Registration**:
   - Registered via admin-only endpoint: `POST /api/v1/strict-apps`.
   - Validates `appId` charset: `[a-z0-9-]{1,40}`.
   - Validates exact `todos` table structure (no extra tables, no sharing).
2. **Server-Generated Epoch**:
   - Server creates a lowercase UUIDv4 epoch string (e.g. `550e8400-e29b-41d4-a716-446655440000`).
3. **Storage Provisioning**:
   - Creates isolated MongoDB collection: `_strict_v1_<appIdLength>_<appId>_<epochNoHyphens>`.
   - Sets up compound scan index `{ userId: 1, tableName: 1, rowId: 1 }`.
   - Verifies collection is uncapped, has no custom validators, no TTL indexes, and simple/absent collation.
4. **Registry Publication Last**:
   - Server registers `AppSchema` in `_schemas` collection only after storage and indexes pass admission.
5. **Mandatory Read-Back**:
   - Operator and provisioning scripts must verify `GET /api/v1/apps/doggo` echoing `syncMode: "strict-v1"` and matching `strictEpoch`.
