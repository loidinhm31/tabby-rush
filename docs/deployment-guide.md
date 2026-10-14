# Doggo Deployment & Operations Guide

**Document Version**: 1.1.0  
**Phase**: 08 — Authorized Hub Operations & Signed Android Release  
**Status**: Tooling & Runbooks Operational; Live Release Gates Pending Operator Provisioning  
**Related Documents**: [Security & Recovery](./security-and-recovery.md) · [Release Acceptance](./release-acceptance.md) · [Hub Schema Integration](./hub-schema-integration.md) · [System Architecture](./system-architecture.md)

---

## 1. Overview & Operational Responsibilities

This guide details the end-to-end operational procedures required to deploy and maintain Doggo with isolated strict-v1 synchronization against Glean Hub.

In strict adherence to architectural boundaries:
- **Client Distribution**: The Doggo Android client ships with fixed, compile-time approved Hub endpoints; no administrative or database credentials are ever distributed to end-user devices.
- **Backend Infrastructure**: Glean Hub (implemented in Rust with the `glean-hub-sync` extension) and its underlying MongoDB persistence remain strictly under operator custody.
- **Sync Protocol Isolation**: The strict-v1 protocol runs in dedicated namespaces and route trees, completely isolated from legacy Glean clients.

---

## 2. Glean Hub Strict Backend Deployment

### 2.1 Host & Database Prerequisites
1. **Glean Hub Binary**: Built with `glean-hub-sync` strict extension and Axum web framework.
2. **MongoDB Cluster**:
   - MongoDB 6.0+ or 7.0+ (Replica Set recommended for majority write concerns, or standalone writable primary with `w: 1, j: true`).
   - Storage engine: WiredTiger with journaling enabled.
   - Network: Firewalled, TLS-encrypted connections only; never exposed to public internet directly without a reverse proxy.
3. **Approved Reverse Proxy**:
   - Nginx, Caddy, or Cloudflare terminating public TLS.
   - Enforce HTTPS only (HTTP -> HTTPS redirect at proxy edge; application client does not follow redirects).

### 2.2 Provisioning the Strict-v1 Application
Execute the schema provisioning runner against the approved Hub instance:

```bash
# Set authorized operator admin token
export GLEAN_ADMIN_TOKEN="<operator_admin_jwt>"
export APPROVED_HUB_ORIGIN="https://hub.example.com"

# Provision strict app schema and verify admission read-back
node scripts/hub-schema-provision.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --token "$GLEAN_ADMIN_TOKEN" \
  --schema integration/glean-hub/doggo-schema.json \
  --out /secure/credentials/doggo-credentials.json
```

**Verification Invariants:**
- Inspects `GET /api/v1/apps/doggo` read-back.
- Asserts `syncMode === 'strict-v1'`.
- Validates the 4 canonical columns in `todos`: `id` (string), `title` (string), `completed` (boolean), `createdAt` (integer).
- Saves the dedicated `apiKey` into custody with restrictive `0600` file permissions.

---

## 3. Android Release Build & Signing

### 3.1 Keystore Setup (Operator Custody)
The release signing key must be managed securely outside the repository. To generate a release keypair:

```bash
export DOGGO_KEYSTORE="/secure/keystores/doggo-release.jks"
export DOGGO_KEY_ALIAS="doggo-release"

keytool -genkeypair \
  -keystore "$DOGGO_KEYSTORE" \
  -alias "$DOGGO_KEY_ALIAS" \
  -keyalg RSA \
  -keysize 3072 \
  -validity 10000 \
  -storetype JKS
```

### 3.2 Assembling the Release APK
Provide signing credentials via environment variables and run the release build:

```bash
export DOGGO_KEYSTORE="/secure/keystores/doggo-release.jks"
export DOGGO_KEY_ALIAS="doggo-release"
export DOGGO_STORE_PASSWORD="<store_password>"
export DOGGO_KEY_PASSWORD="<key_password>"

# Set path to JDK 21 LTS
export JAVA_HOME="/path/to/jdk-21"

./gradlew --no-daemon --no-configuration-cache :androidApp:assembleRelease
```

The resulting signed APK is placed at:
`androidApp/build/outputs/apk/release/androidApp-release.apk`

---

## 4. Release Security & Integrity Verification

Before distribution, execute the fail-closed security auditor:

```bash
export APPROVED_HUB_ORIGIN="https://hub.example.com"

node scripts/release-security-check.mjs \
  --apk androidApp/build/outputs/apk/release/androidApp-release.apk \
  --origin "$APPROVED_HUB_ORIGIN"
```

The audit script verifies:
1. **APK Signature**: Validates v2/v3 signature scheme via `apksigner verify`.
2. **Manifest Invariants**:
   - `android:debuggable="false"`
   - `android:usesCleartextTraffic="false"`
   - `android:allowBackup="false"`
   - Debug network security configurations are stripped.
   - Only `MainActivity` is exported.
3. **Secret Scan**: Confirms no private keys, JWT signing keys, or database passwords leaked into the APK assets or code.

---

## 5. Live Hub Verification Smoke Run

Run the live smoke verification suite to validate end-to-end functionality before opening user registration:

```bash
export APPROVED_HUB_ORIGIN="https://hub.example.com"
export DOGGO_API_KEY="<provisioned_api_key>"

# Live Hub execution
node scripts/hub-live-smoke.mjs \
  --origin "$APPROVED_HUB_ORIGIN" \
  --app-id doggo

# Or execute local fixture test mode for automated regression
node scripts/hub-live-smoke.mjs --fixture-test
```

---

## 6. Device Deployment & Verification

Install the signed release APK onto the target Android device or emulator:

```bash
adb -s "$ANDROID_SERIAL" install -r androidApp/build/outputs/apk/release/androidApp-release.apk
```

**Post-Installation Checklist:**
- [ ] Launch application; verify clean empty state.
- [ ] Create account; verify pending approval or approved state.
- [ ] Create, update, toggle, and delete tasks; verify local Room persistence.
- [ ] Connect network; verify push sync to Hub strict epoch collection.
- [ ] Verify background WorkManager sync operates every 15 minutes.
- [ ] Execute account logout; verify fail-closed wipe of local database, preferences, and Keystore credentials.
