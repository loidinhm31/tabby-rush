# Client reuse scout
Date: 2026-10-13. Read-only scout `ClientReuseScout` returned terminal complete. Root `/home/loidinh/WS/glean-hub/`.

## Recommendation
Keep KMP/Compose. Reuse HTTP contracts, not Rust/Tauri/TypeScript implementation dependencies. Existing consumers prove intended integration shape, not suitability of their storage/security patterns for native TabbyRush.

## Evidence
- `glean-hub-core/glean-hub-sync-client/src/client.rs:20-47,129-210`: login/refresh DTOs, Rust token store, client-local logout; RwLock does not serialize entire refresh network operation.
- `glean-hub-core/glean-hub-sync-client/Cargo.toml:8-25`: Serde/Tokio and optional Reqwest; no Kotlin API.
- `glean-hub-core/glean-hub-sync-client-types/src/client.ts:14-53,90-99,135-194`: fetch transport, login/refresh and persistence callback, local logout. Not a JVM dependency.
- `glean-hub-app/src/services/auth-service.ts:129-244`: login `{email,password}`, signup `{username,email,password}`, refresh `{refreshToken}`, bearer GET /auth/me. Source consumers send app headers, but current server auth routes do not require them.
- Same service `:265-290`: shared in-flight refresh promise; adapt single-flight principle to Kotlin foreground+WorkManager.
- `glean-hub-app/src/services/sync-admin-service.ts:20-61,76-96,119-163`: schema registration and grant/revoke operations. Admin-only provisioning; never embed admin token.
- `glean-hub-app/src/services/access-request-service.ts:24-76`: request creation, own-status listing, admin approval/rejection.
- `glean-hub-app/src/pages/admin/app-registration.tsx:21-46`, `components/organisms/admin-access-panel.tsx:53-99`: existing administrative UI, no need to rebuild in TabbyRush.
- `glean-hub-app/src/contexts/auth-context.tsx:89-220`: account validation and mounted-app logout cleanup; `services/auth-service.ts:178-187,250-257` uses browser localStorage. Do NOT port shared browser token persistence to native.
- `embed-app/cham-lang/packages/ui/src/adapters/shared/QmServerAuthAdapter.ts:76-130`: another wire consumer. Its app identity headers are not proof they remain required on auth routes.

## TabbyRush differences
Dedicated appId/schema/key; native protected token store; account/origin-partitioned SQLite, durable outbox, guarded local wipe; standalone login is not inherited browser-cookie SSO. No username-login endpoint or server logout discovered. Use current server DTOs as authority over old guides.

## Validation
No builds/tests/lint/installation/secret reads/network probes by scout. Parent separately attempted documented health endpoints with no live success.

## Unresolved prerequisites
Reachable Hub, authorized TabbyRush registration and access approval, platform credential-store capability. Server auth behavior confirmed by parent source inspection; real login/refresh remains implementation acceptance.
