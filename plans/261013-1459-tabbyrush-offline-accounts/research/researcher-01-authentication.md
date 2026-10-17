# External Authentication & Identity Architecture Research
Date: 2026-10-13 | Target: TabbyRush Offline Accounts (KMP Android / JVM Desktop + Cloudflare Worker / D1)

## 1. Recommendation: Managed IdP vs. Self-Managed Worker+D1
- **Recommendation**: Managed OIDC/OAuth 2.0 IdP (e.g., Supabase Auth used strictly as headless GoTrue IdP, Clerk, or Auth0) using standard Authorization Code + PKCE.
- **Managed IdP Trade-offs**:
  - *Pros*: Turnkey security (RFC 7636 PKCE, refresh rotation, replay detection, email verification/magic links, password hashing, brute-force throttling), zero auth crypto/SMTP maintenance in Worker.
  - *Cons*: Third-party availability dependency, vendor user limits/pricing cliffs, requirement to validate external JWKS on Cloudflare Workers. App data remains strictly in Cloudflare D1.
- **Self-Managed Worker+D1 Trade-offs**:
  - *Pros*: Zero external vendor cost, no 3rd-party lock-in, data sovereignty within Cloudflare D1, direct `users` table joins.
  - *Cons*: High engineering burden (must build PKCE, secure session/refresh storage, token rotation, family revocation, email deliverability/DKIM/SPF in Worker via Resend/Mailgun, constant security maintenance).

## 2. Authorization Flow & PKCE (Android, JVM Linux, Windows)
- **Protocol**: OAuth 2.0 Authorization Code with PKCE (RFC 7636, S256 code challenge), external system browser (RFC 8252). Embedded WebViews strictly forbidden.
- **Android**: Chrome Custom Tabs via AndroidX Browser. Callback via Android App Links (`https://auth.tabbyrush.app/callback`) with fallback to private-use URI scheme (`tabbyrush://oauth/callback`).
- **JVM Desktop (Linux / Windows)**: System browser launch via `java.awt.Desktop.browse()`. Callback handled by an ephemeral loopback HTTP server (`http://127.0.0.1:<port>/callback`) on a dynamically allocated port (RFC 8252 §7.3).
- **Desktop Headless / SSH Xpra Implications**:
  - In remote SSH Xpra sessions, `Desktop.browse()` launches the remote browser inside Xpra. If the host lacks a graphical browser or user operates terminal-only, loopback callbacks cannot receive host redirection from a local client machine.
  - *Remediation*: Support RFC 8628 (OAuth 2.0 Device Authorization Grant) or manual out-of-band authorization code entry in the desktop client fallback dialog.

## 3. Worker API Token Validation, JWKS, & Tenant Identity
- **Token Spec**: RS256/ES256 asymmetric JWT access token (RFC 7519).
- **Worker Validation**: Cloudflare Worker verifies tokens using `jose` (`jwtVerify`) and `createRemoteJWKSet(new URL(JWKS_URI))` with in-memory / cache-key caching.
- **Claims Enforcement**: Validate `iss` (matches IdP issuer URL), `aud` (matches TabbyRush Worker audience), and `exp`.
- **Tenant Isolation**: Extract subject claim `sub` (unique IdP user ID). Every D1 query enforces `WHERE user_id = :sub`. All tables (`todos`, `outbox`) have `user_id TEXT NOT NULL`. Request bodies must never dictate tenant ID.

## 4. Refresh Rotation, Offline Expiration, & Revocation
- **Refresh Token Rotation (RTR)**: Every `/oauth/token` refresh call issues a new refresh token and invalidates the prior one. Replay of an invalidated token triggers revocation of the entire token family (compromise detection).
- **Offline Tolerance**: Access tokens have short TTLs (15–60 min); refresh tokens have long TTLs (30–90 days).
- **Offline Invariant**: Expired access tokens MUST NOT lock out local offline todo editing. Local edits proceed in SQLite. Network sync pauses and triggers refresh upon reconnect. If refresh token is revoked/expired (HTTP 401 `invalid_grant`), app alerts "Session Expired", retaining unsynced outbox data until re-login.

## 5. Platform Credential Custody
- **Android**: `androidx.security.crypto.EncryptedSharedPreferences` backed by Android Keystore (`KeyGenParameterSpec`, AES-256-GCM master key).
- **Linux Desktop**: freedesktop.org Secret Service API (`org.freedesktop.secrets`) over D-Bus (GNOME Keyring / KWallet). *Headless/Xpra Fallback*: If Secret Service daemon is absent, store AES-GCM encrypted tokens in user-scoped file (`chmod 0600` under `~/.config/tabbyrush/`) with explicit terminal warning.
- **Windows Desktop**: Windows Data Protection API (DPAPI) via `CryptProtectData` / `CryptUnprotectData` (via JNA).
- **KMP Abstraction**: Common `expect interface CredentialStore` with platform `actual` implementations.

## 6. Multi-Account, Logout with Unsynced Changes, & SSO
- **Unsynced Changes on Logout/Switch**:
  - Never discard uncommitted outbox rows on logout/switch.
  - If outbox has pending changes, UI warns user: "You have N unsynced changes. Switching accounts requires syncing or discarding."
  - Multi-tenant local storage: isolate local SQLite databases by user ID (`tabbyrush_<user_id>.db`). Switching accounts switches the open database instance, preserving offline drafts per account.
- **Provider-Browser SSO**: External browser retains IdP session cookies. App logout clears local credentials. To enable account switching without auto-logging into prior session, auth requests must pass `prompt=login` or `prompt=select_account`.
- **Cached-Account Offline Unlock**: Allow entering offline mode using local credentials without contacting IdP.

## 7. Local Data Encryption: SQLCipher vs. OS Protection
- **Recommendation**: Rely on OS sandbox and user profile file protection (`0600` permissions on Linux, DPAPI/EFS on Windows, Android app-private storage `/data/data/...`), reserving SQLCipher for a future phase.
- **Rationale**: Full SQLCipher encryption requires bundling native binaries (+5–10MB footprint, cross-compilation friction across Fedora/Windows/Android) and managing a database key that must still be stored in the OS credential store.

## 8. Provider Plan & Configuration Uncertainties
- **Pricing & Tier Caps**: Free-tier limits for third-party IdPs (e.g., Auth0 7,500 MAU; Clerk 10,000 MAU; Supabase 50,000 MAU) must be verified before production commitment.
- **Custom Domains & Redirect URIs**: Provider must support arbitrary loopback redirect URIs (`http://127.0.0.1:*`) and custom schemes without enterprise plan restrictions.
- **Worker Egress**: Outbound JWKS fetching counts toward Cloudflare subrequest limits (cached JWKS mitigates this).

## 9. Material User Tradeoff Questions
1. **Managed IdP vs. Self-Hosted Worker Auth**: Do you prefer an external managed IdP (e.g. Supabase Auth/Clerk with zero maintenance but third-party dependency) or a fully self-managed Worker+D1 auth system (zero external dependencies/costs, but requires email service setup and custom auth logic)?
   - *Recommendation*: Use Managed OIDC IdP for reliability and standard PKCE compliance.
2. **Headless SSH Xpra Login UX**: When running Desktop on Fedora headless via Xpra without a local browser, should we support RFC 8628 Device Code flow (user opens auth URL on local machine and enters 8-character code in TabbyRush)?
   - *Recommendation*: Yes, implement Device Code flow or manual code entry to avoid Xpra browser launch issues.
3. **Pending Edits Policy on Logout**: If a user logs out with unsynced offline edits, should TabbyRush block logout until synced, allow switching with local drafts preserved per-user, or permit destructive logout?
   - *Recommendation*: Preserve per-user databases (`tabbyrush_<subUUID>.db`) and warn the user, preventing data loss.

## 10. Primary-Source Citations
- RFC 8252 (OAuth 2.0 for Native Apps): https://www.rfc-editor.org/rfc/rfc8252.html
- RFC 7636 (Proof Key for Code Exchange by OAuth Public Clients): https://www.rfc-editor.org/rfc/rfc7636.html
- RFC 8628 (OAuth 2.0 Device Authorization Grant): https://www.rfc-editor.org/rfc/rfc8628.html
- RFC 7519 (JSON Web Token - JWT): https://www.rfc-editor.org/rfc/rfc7519.html
- Cloudflare Workers JWT Validation: https://developers.cloudflare.com/cloudflare-one/identity/authorization-cookie/validating-json
- Android Keystore System: https://developer.android.com/privacy-and-security/keystore
- freedesktop.org Secret Service Specification: https://specifications.freedesktop.org/secret-service-spec/latest/
- Microsoft DPAPI Documentation: https://learn.microsoft.com/en-us/windows/win32/seccrypto/c-and-cpp-cryptography-functions

## 11. Unresolved Prerequisites & Questions
- Selection of specific external IdP vendor (Auth0 vs. Clerk vs. Supabase Auth) vs. self-managed Worker auth decision pending user validation.
- Email delivery provider and SMTP credentials (e.g. Resend, Mailgun) required if passwordless/magic links or self-managed auth is chosen.
- Headless Xpra testbed desktop environment configuration (whether GNOME Keyring daemon and desktop browser are available in headless Fedora container).
