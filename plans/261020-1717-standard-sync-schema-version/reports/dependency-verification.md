# Dependency verification repair — 2026-10-20

## Result and boundaries
- Added **49 SHA-256 artifact entries** across **48 new components plus one existing component**: 450 → 499 artifact checksums; 447 → 495 components. Insertions only (+243 XML lines). No dependency version, lockfile, source, repository policy, trusted-artifact rule or verification switch changed.
- Covers all **41 distinct missing artifacts** in the three prior native verification HTML reports, including Compose’s Windows JDK-version probe, plus **8 debug unit/instrumentation dependency artifacts** discovered through resolution. Thirteen additional metadata-only candidates were deliberately excluded.
- Baseline identity: `gradle/verification-metadata.xml` SHA-256 `a55b1469aa031bb619c5adc1fea644b5de3431ee3bce71315f209ffe0e62b57e` before edits. Existing 450 checksums were preserved, not independently reauthenticated by this repair.
- Inherited Glance provenance/release holds and G2/G3/G5/G6/G7 acceptance boundaries remain open. This evidence authenticates only the 49 additions; it does not clear historical trust or runtime acceptance holds.

## Authentication method
- Fresh artifact GETs outside the Gradle cache, using TLS certificate/hostname validation and `Cache-Control: no-cache`, from **44 Google artifacts** at [dl.google.com/dl/android/maven2](https://dl.google.com/dl/android/maven2/) and **5 Central artifacts** at [repo.maven.apache.org/maven2](https://repo.maven.apache.org/maven2/). Redirect destinations restricted to these two official hosts.
- For every addition: SHA-256 of independently downloaded complete artifact bytes **equaled both Gradle-generated SHA-256 and the local cached artifact’s SHA-256**. Cache presence alone was never trust evidence. All 49 matched; zero digest mismatches.
- Published evidence also matched: **27 `.sha256` sidecars**, **11 Gradle `.module` file records** (all SHA-256/SHA-512/SHA-1 values checked), **11 `.sha1` sidecars** where SHA-256 was unavailable. SHA-1 corroborates the fresh TLS retrieval only; metadata pins SHA-256, never SHA-1.
- `.module` file records supplied the four datastore `*-release.aar` aliases → actual versioned Maven filenames; artifact identity was not inferred from cache names. No PGP signer identity claim. Inherited `verify-metadata=false` / `verify-signatures=false` remain unchanged; artifact verification was never disabled.

## Resolution-only execution
- Used a new temporary copy containing build scripts, wrapper, catalog, locks, metadata and sanitized Android SDK pointer only: no source files or dotenv. Java `G:/env/jdk-21.0.9`; properties `desktopTarget=windows`, `isolatedTestConfig=true`, `hubOrigin=http://127.0.0.1:3100`; no production credentials/services accessed.
- Commands: `gradlew.bat dependencyTrustResolveOnly -I resolve-only.gradle -PdesktopTarget=windows -PisolatedTestConfig=true -PhubOrigin=http://127.0.0.1:3100 --write-verification-metadata sha256 --max-workers=2 --console=plain --no-daemon`; same arguments with `dependencyTrustProbeOnly` for `org.jetbrains.compose:gradle-plugin-internal-jdk-version-probe:1.7.3` detached resolution.
- Resolver visits resolvable non-release configurations, uses a lenient artifact view filtered to external `ModuleComponentIdentifier` components, and records artifacts/failures. Only new artifacts used by non-metadata, non-UTP/native-compiler configurations (plus the previously reported probe) were eligible for insertion. The probe task only resolves its detached dependency; neither task depends on source tasks.
- Final resolver **succeeded, zero unresolved artifacts**; detached probe resolver **succeeded**. Two earlier temporary task attempts failed on a Groovy scope qualifier and project-variant ambiguity, corrected before collecting the successful candidate set. Gradle 9-incompatible deprecation warnings remain.
- No source compilation/tests, build/package/install, emulator, application launch or strict acceptance rerun. Parent owns strict integrated verification. Temporary build configuration and resolution scaffolding removed after recording this ledger.

## Authenticated additions
Each link identifies exact official artifact bytes; SHA-256 pins are in `gradle/verification-metadata.xml`. Evidence: **M** = publisher `.module` file record; **S256** = artifact URL + `.sha256`; **S1** = artifact URL + `.sha1` (SHA-256 sidecar unavailable). For M, metadata URL is the same directory + `<module>-<version>.module`.

| Coordinate | Official artifact | Evidence |
|---|---|---|
| `androidx.customview:customview:1.0.0` | [customview-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/customview/customview/1.0.0/customview-1.0.0.aar) | S1 |
| `androidx.datastore:datastore-android:1.1.7` | [datastore-release.aar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-android/1.1.7/datastore-android-1.1.7.aar) | M |
| `androidx.datastore:datastore-core-android:1.1.7` | [datastore-core-release.aar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-core-android/1.1.7/datastore-core-android-1.1.7.aar) | M |
| `androidx.datastore:datastore-core-okio-jvm:1.1.7` | [datastore-core-okio-jvm-1.1.7.jar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-core-okio-jvm/1.1.7/datastore-core-okio-jvm-1.1.7.jar) | M |
| `androidx.datastore:datastore-preferences-android:1.1.7` | [datastore-preferences-release.aar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-android/1.1.7/datastore-preferences-android-1.1.7.aar) | M |
| `androidx.datastore:datastore-preferences-core-android:1.1.7` | [datastore-preferences-core-release.aar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-core-android/1.1.7/datastore-preferences-core-android-1.1.7.aar) | M |
| `androidx.datastore:datastore-preferences-external-protobuf:1.1.7` | [datastore-preferences-external-protobuf-1.1.7.jar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-external-protobuf/1.1.7/datastore-preferences-external-protobuf-1.1.7.jar) | M |
| `androidx.datastore:datastore-preferences-proto:1.1.7` | [datastore-preferences-proto-1.1.7.jar](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences-proto/1.1.7/datastore-preferences-proto-1.1.7.jar) | M |
| `androidx.documentfile:documentfile:1.0.0` | [documentfile-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/documentfile/documentfile/1.0.0/documentfile-1.0.0.aar) | S1 |
| `androidx.fragment:fragment:1.1.0` | [fragment-1.1.0.aar](https://dl.google.com/dl/android/maven2/androidx/fragment/fragment/1.1.0/fragment-1.1.0.aar) | S1 |
| `androidx.legacy:legacy-support-core-utils:1.0.0` | [legacy-support-core-utils-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/legacy/legacy-support-core-utils/1.0.0/legacy-support-core-utils-1.0.0.aar) | S1 |
| `androidx.lifecycle:lifecycle-viewmodel:2.8.7` | [lifecycle-viewmodel-2.8.7.aar](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-viewmodel/2.8.7/lifecycle-viewmodel-2.8.7.aar) | S256 |
| `androidx.loader:loader:1.0.0` | [loader-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/loader/loader/1.0.0/loader-1.0.0.aar) | S1 |
| `androidx.localbroadcastmanager:localbroadcastmanager:1.0.0` | [localbroadcastmanager-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/localbroadcastmanager/localbroadcastmanager/1.0.0/localbroadcastmanager-1.0.0.aar) | S1 |
| `androidx.print:print:1.0.0` | [print-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/print/print/1.0.0/print-1.0.0.aar) | S1 |
| `androidx.test:core:1.6.1` | [core-1.6.1.aar](https://dl.google.com/dl/android/maven2/androidx/test/core/1.6.1/core-1.6.1.aar) | S256 |
| `androidx.test:monitor:1.7.2` | [monitor-1.7.2.aar](https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.7.2/monitor-1.7.2.aar) | S256 |
| `androidx.test:rules:1.6.1` | [rules-1.6.1.aar](https://dl.google.com/dl/android/maven2/androidx/test/rules/1.6.1/rules-1.6.1.aar) | S256 |
| `androidx.test:runner:1.6.2` | [runner-1.6.2.aar](https://dl.google.com/dl/android/maven2/androidx/test/runner/1.6.2/runner-1.6.2.aar) | S256 |
| `androidx.test.ext:junit:1.2.1` | [junit-1.2.1.aar](https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/1.2.1/junit-1.2.1.aar) | S256 |
| `androidx.test.services:storage:1.5.0` | [storage-1.5.0.aar](https://dl.google.com/dl/android/maven2/androidx/test/services/storage/1.5.0/storage-1.5.0.aar) | S256 |
| `androidx.viewpager:viewpager:1.0.0` | [viewpager-1.0.0.aar](https://dl.google.com/dl/android/maven2/androidx/viewpager/viewpager/1.0.0/viewpager-1.0.0.aar) | S1 |
| `androidx.work:work-testing:2.9.1` | [work-testing-2.9.1.aar](https://dl.google.com/dl/android/maven2/androidx/work/work-testing/2.9.1/work-testing-2.9.1.aar) | M |
| `com.google.android.datatransport:transport-api:3.0.0` | [transport-api-3.0.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/datatransport/transport-api/3.0.0/transport-api-3.0.0.aar) | S256 |
| `com.google.android.datatransport:transport-api:3.1.0` | [transport-api-3.1.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/datatransport/transport-api/3.1.0/transport-api-3.1.0.aar) | S256 |
| `com.google.android.datatransport:transport-backend-cct:3.1.9` | [transport-backend-cct-3.1.9.aar](https://dl.google.com/dl/android/maven2/com/google/android/datatransport/transport-backend-cct/3.1.9/transport-backend-cct-3.1.9.aar) | S256 |
| `com.google.android.datatransport:transport-runtime:3.1.9` | [transport-runtime-3.1.9.aar](https://dl.google.com/dl/android/maven2/com/google/android/datatransport/transport-runtime/3.1.9/transport-runtime-3.1.9.aar) | S256 |
| `com.google.android.gms:play-services-base:18.1.0` | [play-services-base-18.1.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-base/18.1.0/play-services-base-18.1.0.aar) | S256 |
| `com.google.android.gms:play-services-basement:18.3.0` | [play-services-basement-18.3.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-basement/18.3.0/play-services-basement-18.3.0.aar) | S256 |
| `com.google.android.gms:play-services-cloud-messaging:17.2.0` | [play-services-cloud-messaging-17.2.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-cloud-messaging/17.2.0/play-services-cloud-messaging-17.2.0.aar) | S256 |
| `com.google.android.gms:play-services-stats:17.0.2` | [play-services-stats-17.0.2.aar](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-stats/17.0.2/play-services-stats-17.0.2.aar) | S256 |
| `com.google.android.gms:play-services-tasks:18.1.0` | [play-services-tasks-18.1.0.aar](https://dl.google.com/dl/android/maven2/com/google/android/gms/play-services-tasks/18.1.0/play-services-tasks-18.1.0.aar) | S256 |
| `com.google.errorprone:error_prone_annotations:2.26.0` | [error_prone_annotations-2.26.0.jar](https://repo.maven.apache.org/maven2/com/google/errorprone/error_prone_annotations/2.26.0/error_prone_annotations-2.26.0.jar) | S1 |
| `com.google.firebase:firebase-annotations:17.0.0` | [firebase-annotations-17.0.0.jar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-annotations/17.0.0/firebase-annotations-17.0.0.jar) | M |
| `com.google.firebase:firebase-common:22.0.1` | [firebase-common-22.0.1.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-common/22.0.1/firebase-common-22.0.1.aar) | S256 |
| `com.google.firebase:firebase-components:19.0.0` | [firebase-components-19.0.0.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-components/19.0.0/firebase-components-19.0.0.aar) | S256 |
| `com.google.firebase:firebase-datatransport:18.2.0` | [firebase-datatransport-18.2.0.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-datatransport/18.2.0/firebase-datatransport-18.2.0.aar) | S256 |
| `com.google.firebase:firebase-encoders:17.0.0` | [firebase-encoders-17.0.0.jar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-encoders/17.0.0/firebase-encoders-17.0.0.jar) | S256 |
| `com.google.firebase:firebase-encoders-json:18.0.0` | [firebase-encoders-json-18.0.0.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-encoders-json/18.0.0/firebase-encoders-json-18.0.0.aar) | S256 |
| `com.google.firebase:firebase-encoders-proto:16.0.0` | [firebase-encoders-proto-16.0.0.jar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-encoders-proto/16.0.0/firebase-encoders-proto-16.0.0.jar) | S256 |
| `com.google.firebase:firebase-iid-interop:17.1.0` | [firebase-iid-interop-17.1.0.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-iid-interop/17.1.0/firebase-iid-interop-17.1.0.aar) | S256 |
| `com.google.firebase:firebase-installations:19.0.1` | [firebase-installations-19.0.1.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-installations/19.0.1/firebase-installations-19.0.1.aar) | S256 |
| `com.google.firebase:firebase-installations-interop:17.1.1` | [firebase-installations-interop-17.1.1.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-installations-interop/17.1.1/firebase-installations-interop-17.1.1.aar) | S256 |
| `com.google.firebase:firebase-measurement-connector:19.0.0` | [firebase-measurement-connector-19.0.0.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-measurement-connector/19.0.0/firebase-measurement-connector-19.0.0.aar) | S256 |
| `com.google.firebase:firebase-messaging:25.0.1` | [firebase-messaging-25.0.1.aar](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-messaging/25.0.1/firebase-messaging-25.0.1.aar) | S256 |
| `org.jetbrains.compose:gradle-plugin-internal-jdk-version-probe:1.7.3` | [gradle-plugin-internal-jdk-version-probe-1.7.3.jar](https://repo.maven.apache.org/maven2/org/jetbrains/compose/gradle-plugin-internal-jdk-version-probe/1.7.3/gradle-plugin-internal-jdk-version-probe-1.7.3.jar) | M |
| `org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22` | [kotlin-android-extensions-runtime-1.9.22.jar](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-android-extensions-runtime/1.9.22/kotlin-android-extensions-runtime-1.9.22.jar) | S1 |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22` | [kotlin-parcelize-runtime-1.9.22.jar](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-parcelize-runtime/1.9.22/kotlin-parcelize-runtime-1.9.22.jar) | S1 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1` | [kotlinx-coroutines-play-services-1.10.1.jar](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-play-services/1.10.1/kotlinx-coroutines-play-services-1.10.1.jar) | M |

## Unresolved artifacts / handoff
- Unresolved added artifacts: **none**. Strict Android debug/test and Windows distribution execution remains unverified until parent reruns its checks; dependency resolution is not compilation or package evidence.
- Unresolved questions: none.

## Integrated follow-up
- Parent authenticated one further Windows AAPT artifact after strict compilation exposed its detached configuration: [aapt2-8.7.3-12006047-windows.jar](https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/8.7.3-12006047/aapt2-8.7.3-12006047-windows.jar). Fresh official TLS bytes and local cached bytes both SHA-256 `9406af8e4742da01e4ce65773e23bda40ed5f599917b38cacc261c9ba0952331`; official `.sha1` matched. Total additions: **50**, final artifact checksum count **500**.
- Strict integrated command passed: shared JVM **264**, Desktop **131**, Android unit **50**, Android debug APK, instrumentation APK assembly and Windows `createDistributable`. See `verification-final.md` for exact command/boundaries. This supersedes the earlier “parent rerun unverified” handoff; existing Glance and release acceptance holds are not waived.
