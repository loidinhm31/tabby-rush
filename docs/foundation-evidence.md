# TabbyRush Phase 02 Foundation Evidence & Verification

Date: 2026-10-14  
Phase: Phase 02 Frozen Foundation & Contracts  
Plan: `plans/261013-1540-tabbyrush-glean-hub-reuse`  
Related Docs: [Toolchain Versions](./toolchain-versions.md) · [System Architecture](./system-architecture.md) · [API Contract](./api-contract.md)

---

## 1. Scope Assessment & Implementation Invariants

Phase 02 establishes the immutable foundational contracts, root Gradle configuration, dependency catalog, and core domain policies for the TabbyRush repository:
- **No Stubs**: Zero empty application entrypoints or fake repositories created.
- **Staged Module Assembly**: Root `settings.gradle.kts` unconditionally includes `:shared`, but conditionally guards `:androidApp` and `:desktopApp` upon existence of their respective build scripts.
- **Pure Contracts**: Common interfaces in `shared/src/commonMain/kotlin/dev/tabbyrush/shared/contract/` define complete function signatures, data classes, and sealed interfaces without leaking Room or Ktor implementations.
- **Verified Policies**: Pure Kotlin implementations of `TitlePolicy`, `EndpointPolicy`, `AccountKey`, and canonical `StrictSyncContract` SHA-256 digest computation with test vectors matching documented strict specification layout and NIST SHA-256 standards (cross-language live runtime execution verification pending integration).

---

## 2. Toolchain Evidence & Proven Hash Constants
### 2.1 Gradle Wrapper Distribution Verification
- **Gradle Version**: `8.11.1` (bin distribution)
- **Distribution URL**: `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip`
- **Official SHA-256 Checksum**:
  `f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`
- **Wrapper Jar Checksum**:
  `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`

### 2.2 Host System Observations
- **Host Architecture**: `x86_64` (Fedora 44 Server)
- **Host Java Default**: OpenJDK 25.0.4.1 (requires Gradle daemon configured via toolchains or explicit `JAVA_HOME` pointing to JDK 21 LTS).
- **Target Java Baseline**: JVM Bytecode Target 17; compileSdk 35; minSdk 26.

---

## 3. Contract & Digest Conformance Verification

1. **Title Policy Vectors**:
   - Trimming only explicit Unicode White_Space code points (`0009–000D, 0020, 0085, 00A0, 1680, 2000–200A, 2028, 2029, 202F, 205F, 3000`).
   - Scalar bounds: 1 to 200 Unicode scalar values.
   - Astral characters (e.g. `\uD83D\uDC36` Dog Emoji) count as 1 scalar value.
   - Rejection of malformed surrogate pairs and internal C0 / DEL control characters.
2. **Endpoint Policy Vectors**:
   - Canonical origin formatting (no paths, queries, fragments, credentials).
   - Release BuildMode: strictly requires HTTPS.
   - Debug BuildMode: additionally permits `http://localhost:3000`, `http://127.0.0.1:3000`, `http://10.0.2.2:3000`.
3. **AccountKey Vectors**:
   - Unambiguous length-framed SHA-256 hash of canonical origin and server userId:
     `SHA256(u32_be(origin.length) + origin_utf8 + u32_be(userId.length) + userId_utf8)`.
4. **Strict Digest Vectors**:
   - Cross-language domain separation tag: `strict-v1`.
   - Length-framed u32 big-endian encoding for string fields (`domain`, `appId`, `userId`, `epoch`, `tableName`, `rowId`, `opId`, `kind`, `title`).
   - Unsigned 64-bit big-endian encoding for numeric integers (`expectedRevision`, `createdAt`).
   - Single byte for boolean flags (`completed`: 0 or 1).
   - Canonical empty representation for delete payloads.
