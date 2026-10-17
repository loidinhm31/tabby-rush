# Diagnostic Report: "Internal Server Error" on Sync Now

**Date**: 2026-10-16  
**Investigator**: SyncInternalErrorDebugger  
**Scope**: TabbyRush Compose Multiplatform (Desktop PID 26988, Android PID 5156 on emulator-5554) and Glean Hub sync backend (`https://api-glean-hub.qm-hub-v001.cloud`)  
**Target File**: `plans/reports/debugger-261016-0236-sync-now-internal-server-error.md`  

---

## 1. Executive Summary

### Issue Description
Invoking "Sync Now" in TabbyRush fails, rendering an "Attention: Internal server error" banner (`SafeError.Network("SERVER_ERROR", "Internal server error")`). The failure halts client synchronization during the initial bootstrap phase.

### Verified Findings & Primary Diagnosis
1. **Failing Endpoint**: The exact HTTP call failing with HTTP 500 is `POST /api/v1/sync/tabby-rush/strong/bootstrap` (along with all other endpoints under `/api/v1/sync/tabby-rush/*`).
2. **Failure Point**: Probing demonstrates the failure occurs on the server during request pre-validation / API-key verification, prior to route handler dispatch or JWT authentication.
3. **App-Specific Behavior Matrix**:
   - `nonexistent_app` -> HTTP 404 `{"error":"App not found: nonexistent_app"}`.
   - Known legacy apps (`cham-lang`, `code-notes`, `fin-catch`, `money-insight`) -> HTTP 401 `{"error":"Invalid API key"}` when probed with an arbitrary key.
   - `tabbyrush` -> HTTP 500 `{"error":"Internal server error"}` with any `X-API-Key` (valid provisioned key or test probe).
4. **Leading Root Cause Hypothesis**:
   The Hub authentication layer (`dual_auth_middleware` / `AppService::verify_api_key`) loads the app's registry record from MongoDB `_schemas` using a legacy `AppSchema` struct requiring `pub tables: SchemaDefinition`. For `tabbyrush`, registered as a strict app via `POST /api/v1/strict-apps`, the stored document uses the strict format (`"schema": { "todos": ... }`, `syncMode: "strict-v1"`, `strictEpoch: "..."`). Deserialization into the legacy model fails, and the database/BSON deserialization error is coerced to HTTP 500 `{"error":"Internal server error"}`. (This remains a high-probability hypothesis pending direct server log or raw database record inspection).

---

## 2. Technical Analysis & Evidence

### Active Environment Parameters
- **Active Hub Origin**: `https://api-glean-hub.qm-hub-v001.cloud` (defined in `APPROVED_HUB_ORIGIN`)
- **Application ID**: `tabbyrush`
- **Active Account**:
  - `userId`: `6acfc41a859b012e0793684b`
  - `username`: `loidinh_tabbyrush`
  - `email`: `loidinh.tabbyrush@tabbyrush.dev`
  - `accountKey`: `0cd10864b18e938c363d1b44c1d9dcca437601efe9534f2d0d0909cc822dec53`
  - Verified identical in Desktop `%LOCALAPPDATA%\tabbyrush\accounts\active_account.dat` and Android `/data/data/dev.tabbyrush.android/files/active_account.dat`.
- **Application API Key**: 32-character key present in environment (`TABBYRUSH_API_KEY`).

### Exact Network Reproduction

#### Request: Bootstrap Dispatch
```http
POST /api/v1/sync/tabby-rush/strong/bootstrap HTTP/1.1
Host: api-glean-hub.qm-hub-v001.cloud
Content-Type: application/json
X-API-Key: XoaJ...[REDACTED]
X-App-Id: tabbyrush
Authorization: Bearer eyJ0eXAi...[REDACTED]

{}
```

#### Captured Response:
```http
HTTP/1.1 500 Internal Server Error
Date: Thu, 15 Oct 2026 19:43:27 GMT
Content-Type: application/json
Content-Length: 33
Connection: keep-alive
access-control-allow-credentials: true
vary: origin, access-control-request-method, access-control-request-headers
nel: {"report_to":"cf-nel","success_fraction":0.0,"max_age":604800}
cf-cache-status: DYNAMIC
cf-ray: a4b15014cb93aa81-SIN
Server: cloudflare

{"error":"Internal server error"}
```

### Empirical Probing Matrix

Probes executed directly against `https://api-glean-hub.qm-hub-v001.cloud` isolate the failure:

| Target Endpoint | App ID Tested | Headers Supplied | HTTP Status | Response Payload | Analysis |
|---|---|---|---|---|---|
| `/api/v1/sync/{app}/strong/bootstrap` | `tabbyrush` | Missing `X-API-Key` | **400 Bad Request** | `{"error":"Missing header: X-API-Key"}` | Header presence guard passes |
| `/api/v1/sync/{app}/strong/bootstrap` | `tabbyrush` | `X-API-Key: test` | **500 Internal Server Error** | `{"error":"Internal server error"}` | App lookup/verification fails |
| `/api/v1/sync/{app}/strong/bootstrap` | `tabbyrush` | Valid `X-API-Key` + valid JWT | **500 Internal Server Error** | `{"error":"Internal server error"}` | Fails before JWT validation |
| `/api/v1/sync/{app}/strong/bootstrap` | `cham-lang` | `X-API-Key: test` | **401 Unauthorized** | `{"error":"Invalid API key"}` | Clean deserialization, key rejected |
| `/api/v1/sync/{app}/strong/bootstrap` | `code-notes` | `X-API-Key: test` | **401 Unauthorized** | `{"error":"Invalid API key"}` | Clean deserialization, key rejected |
| `/api/v1/sync/{app}/strong/bootstrap` | `nonexistent_app` | `X-API-Key: test` | **404 Not Found** | `{"error":"App not found: nonexistent_app"}` | Registry miss handled cleanly |
| `/api/v1/sync/{app}/push` (legacy) | `tabbyrush` | `X-API-Key: test` | **500 Internal Server Error** | `{"error":"Internal server error"}` | Fails identically on legacy route |
| `/api/v1/sync/{app}/push` (legacy) | `cham-lang` | `X-API-Key: test` | **401 Unauthorized** | `{"error":"Invalid API key"}` | Key rejected cleanly |

---

## 3. Call Stack Tracing

### Client Layer (TabbyRush App)
```
1. UI action dispatched: TodoAction.TriggerSync
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/sync-feedback.kt:147
2. Controller invokes coordinator:
   desktopApp/src/main/kotlin/dev/tabbyrush/desktop/main.kt:599
   androidApp/src/main/kotlin/dev/tabbyrush/android/session-view-model.kt:344
3. Coordinator executes sync round:
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:60
   -> Verifies AuthState.AUTHENTICATED
   -> Emits SyncStatus.SYNCING
   -> Calls syncClient.bootstrap()
4. HTTP request dispatched via Ktor:
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt:36
   -> POST /api/v1/sync/tabby-rush/strong/bootstrap
5. HTTP 500 response received:
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/hub-sync-client.kt:408
   -> parseStrictError(status=500, rawBody="{\"error\":\"Internal server error\"}")
   -> Mapped to SafeError.Network("SERVER_ERROR", "Internal server error")
6. Coordinator records failure:
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/sync/account-sync-coordinator.kt:288
   -> Updates progress: status=IDLE, safeError=SafeError.Network(...)
7. UI updates:
   shared/src/commonMain/kotlin/dev/tabbyrush/shared/ui/tabbyrush-app.kt:168
   -> Renders SafeErrorBanner: "Attention: Internal server error"
```

### Hypothesized Server Execution Stack (Glean Hub)
*Note: Derived from architectural analysis and source inspection of `qm-plugin/qm-sync` and `glean-hub-sync` specifications; subject to confirmation against deployed server logs.*
```
1. Request ingress at Axum HTTP router:
   POST /api/v1/sync/tabby-rush/strong/bootstrap
2. Route passes through dual_auth_middleware:
   qm-plugin/qm-sync/src/middleware/dual_auth.rs:27
   -> Header check: X-API-Key present
   -> Path extraction: app_id = "tabbyrush"
   -> AppService::verify_api_key(&app_id, api_key).await?
3. AppService schema lookup:
   qm-plugin/qm-sync/src/services/app_service.rs:119
   -> AppService::get_app_schema("tabbyrush")
   -> db.collection::<AppSchema>("_schemas").find_one(doc! { "appId": "tabby-rush" })
4. Deserialization error:
   MongoDB BSON deserializer fails attempting to populate AppSchema from strict-registered document.
5. Error conversion:
   qm-plugin/qm-sync/src/error.rs:57
   -> Error coerced to SyncError::Database(e)
   -> IntoResponse renders StatusCode::INTERNAL_SERVER_ERROR {"error":"Internal server error"}
```

---

## 4. Operational Side Effects & Audit Notes

1. **Probe Registration Side Effect**: During investigation of access request endpoints, a diagnostic probe request created a pending access request on the live Hub:
   - Request ID: `6ad12ed9e0ec2d57a2d9ed3c`
   - User: `probe_user` (`6ad12cc9e0ec2d57a2d9e86a`)
   - App: `tabbyrush`, Status: `pending`
2. **Working Tree Audit**: No application code or schema files were modified. Pre-existing untracked files or local build caches remain untouched per invariant rules.

---

## 5. Recommended Remediation Directions (For Backend / Database Operator Review)

*Do NOT implement code changes in the TabbyRush client repository; these directions apply to the Glean Hub backend and database infrastructure.*

1. **[Backend Code - Defensive Schema Deserialization]**:
   In Hub's `qm-plugin/qm-sync/src/models/app.rs`, update `AppSchema` so that `tables` is optional or aliased (`#[serde(default, alias = "schema")]`), and add `#[serde(default)]` for strict fields (`sync_mode`, `strict_epoch`). Ensure deserialization never fails closed with 500 when encountering valid strict-app registrations.
2. **[Backend Code - Lightweight Auth Projection]**:
   In `AppService::verify_api_key`, query only the `apiKey` field via projection (`doc! { "apiKey": 1 }`) using an untyped BSON document (`collection::<Document>("_schemas")`), decoupling API-key checking from table schema structure.
3. **[Database - Operator Document Inspection & Reconcile]**:
   Inspect the live `_schemas` document for `appId: "tabby-rush"`. If `tables` is missing and `schema` is present, evaluate whether adding a compatibility `tables` field matching `schema` unblocks legacy middleware without compromising strict invariants.
4. **[Storage - Verify `app_tabbyrush` Collection & Index]**:
   Once authentication passes, verify that MongoDB collection `app_tabbyrush` exists with compound index `{ userId: 1, tableName: 1, rowId: 1 }` so `validate_strict_admission` does not immediately fail closed with HTTP 503 `notAdmitted`.

---

## 6. Unresolved Questions

1. **Live Server Logs**: What is the exact internal exception string logged by the Axum server process on `api-glean-hub.qm-hub-v001.cloud` when `POST /api/v1/sync/tabby-rush/strong/bootstrap` emits HTTP 500?
2. **Deployed Binary Revision**: Does the live container running at `api-glean-hub.qm-hub-v001.cloud` run the Phase 03 `glean-hub-sync` extension directly, or does it run mainline `qm-hub-server` with `qm-sync` routing traffic?
3. **Account Access Grant for `loidinh_tabbyrush`**: Once the API-key authentication stage passes, does user `6acfc41a859b012e0793684b` already have `tabbyrush` in their JWT `apps` claim on the live server, or is an administrative access approval required?
4. **MongoDB Replica Set Status**: Is the underlying MongoDB instance on `api-glean-hub.qm-hub-v001.cloud` configured as a replica set with majority journaling enabled, or as a standalone instance?
