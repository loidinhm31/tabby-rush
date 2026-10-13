# Authentication research — reuse disposition
Date: 2026-10-13. Parent synthesis of the earlier parallel authentication researcher and current Hub source scouts/reviewer; not a new agent run or live proof.

## Recommendation
Reuse existing Glean Hub native register/login/refresh/me and app-access requests. No Worker/D1, external IdP, OAuth redirect/device-code flow, browser-cookie SSO or new password backend. User selected Hub login AND schema sync, plus signup/access approval.

## Evidence and boundaries
- [Source-derived review](../reports/contract-review.md) cites exact Hub files/lines; [decisions](../reports/design-decisions.md) distinguish source facts from failed health probes.
- Login email/password; signup username/email/password. New identity has no Doggo grant. `/me.apps` controls current cooperative-client access; approval record alone is insufficient.
- Existing HS256 access6h, hashed random rotating refresh30days. Refresh revokes old token before separate successor creation; uncertain result may require reauthentication. No OIDC/JWKS/issuer-audience contract or token-family revocation inferred.
- Sync uses dedicated app key + JWT app claim + user-owned rows. Native app key extractable; not human authorization, not a reason to share admin/signing/database secrets or introduce a proxy.
- No server logout or key rotation/recovery endpoint. Logout deletes local credentials; issued remote credentials may remain valid. Admin login receives privileged JWT: warn and recommend a non-admin account.
- Overloaded401 requires operation-specific diagnosis; no blind refresh loop on duplicate access requests or wrong app key.
- User subsequently selected Doggo-only strict sync; existing Hub auth/access contracts and embedded clients remain unchanged. Strict mode/provisioning/routes are isolated per [current contracts](../contracts.md); admin warning and local-only logout confirmed.

## Credential storage
Android Keystore AES-GCM wrapped atomic bundle; Linux Secret Service, fail closed if unavailable/locked; Windows user-scoped DPAPI. No tokens/passwords in SQLite, saved UI state or WorkManager arguments. Persist rotated pair before waiters resume; failed persistence pauses background work.

## Historical recommendations rejected
[Earlier authentication report](../../261013-1459-doggo-offline-accounts/research/researcher-01-authentication.md) is research history, not specification. Reject its deprecated EncryptedSharedPreferences, unproven Linux encrypted-file fallback, generic family-revocation claims, cached-account retention and managed OAuth/D1 architecture. Keep only platform custody/security references consistent with current contracts.

## Primary references
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Secret Service specification](https://specifications.freedesktop.org/secret-service-spec/latest/)
- [Windows CryptProtectData](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)
- [Native OAuth RFC8252](https://www.rfc-editor.org/rfc/rfc8252.html): relevant alternative, not implemented by inspected Hub and not selected.

## Unresolved prerequisites
Reachable approved Hub revision/test environment, operator Doggo registration/one-time key custody/access grants and actual protected-store availability. No credentials requested in chat; no successful live login claimed.
