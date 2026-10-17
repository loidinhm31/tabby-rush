# Plan validation summary

Date: 2026-10-13. User accepted validation interview; four questions asked and answered.
Status: validation complete; architecture/phase revision REQUIRED before implementation.

## Confirmed decisions
| Topic | User choice | Effect |
|---|---|---|
| Access | Separate user accounts | Each user private list/sign-in; replace shared-dataset Bearer-token design with identity and per-user authorization. |
| Connectivity | Offline editing and sync | Persistent local data, durable mutation queue and explicit synchronization/conflict/deletion semantics required. |
| Theme | Reference light theme | Keep measured cream/white, teal/mint design; no additional dark theme scope. |
| Windows | Manual Windows build | Keep native Windows setup/MSI/manual runtime acceptance; no CI packaging addition. |

These explicit answers override contrary proposed defaults in architecture.md, contracts.md, reports/design-decisions.md and phases. Existing phase files are intentionally NOT rewritten during validation per the command workflow; they are now historical proposals where inconsistent.

## Required revisions before implementation
- [ ] Identity design: choose native-compatible provider/login protocol, Android/desktop browser callback handling, token expiry/refresh/logout, platform-protected credential custody and separate dev/prod configuration. Do not ship client secrets or reuse Cloudflare deploy credentials.
- [ ] Per-user authorization: validated server identity claims; owner-scoped every query/mutation/list/sync/cursor; never trust client user_id. Revise D1 schema, indices, contracts and cross-account isolation tests. Define account deletion/data retention if supported, rather than inventing feature scope.
- [ ] Local persistence on Android/JVM: choose supported local database library, migrations and transactional mutation+outbox updates; isolate local data per account and define logout/account-switch behavior.
- [ ] Offline contract: stable operation IDs/idempotency, retry acknowledgements, interrupted/repeated delivery, create/update/delete coalescing, ordered durable outbox, server change feed/cursors and tombstone retention. Specify atomic server operation receipt/write behavior rather than blindly replay current POST.
- [ ] Consistency rules: choose/document conflict resolution, device identity where needed, clock independence, pull/push ordering, partial failures and first-login/expired-auth/offline session constraints. Offline edits are provisional local state, not guaranteed server commits.
- [ ] Scope foreground/manual synchronization versus background scheduling; offline editing does not imply background sync requirements. Confirm next architecture choice explicitly where it materially changes behavior.
- [ ] UI/state: authenticated account session, local-first startup, queued/syncing/error/conflict indicators, pending offline deletes and user-visible resolution; retain current light visual language/accessibility.
- [ ] Rework exclusive ownership/dependency graph/phases and effort after contracts change. Update affected tests: cross-user isolation, crash-safe queue, restart persistence, duplicate delivery, two-device concurrent changes/deletes, expired credentials and account switch.
- [ ] Update setup/release/security/recovery and Android/Linux/Windows real-device acceptance for accounts plus offline restart/reconnect. Preserve SSH-only headless setup and real native packaging gates.
- [ ] Supersede old architecture/contracts/decisions and synchronize overview/command index/phase docs only in the next planning revision. The current60h estimate belongs to the old private/online-first scope and is no longer an estimate for the approved product.

## Review disposition
Completed ten-phase document package remains useful as setup/theme/platform reference, but NOT implementation-ready for validated scope. Do not freeze phase02 or start dependent app implementation until revision and review complete. No application implementation performed.

## Unresolved design questions
- Identity provider and sign-in method.
- Sync conflict policy and background-sync scope.
- Logout/account-switch local-data retention policy.
These need informed design/research and targeted clarification in the revision, not an assumed return to old defaults.
