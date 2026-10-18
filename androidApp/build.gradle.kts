import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

fun loadDotEnv(rootDir: File): Map<String, String> {
    val envFile = File(rootDir, ".env")
    if (!envFile.exists() || !envFile.isFile) return emptyMap()
    val env = mutableMapOf<String, String>()
    envFile.forEachLine(Charsets.UTF_8) { rawLine ->
        val line = rawLine.trim()
        if (line.isNotEmpty() && !line.startsWith("#")) {
            val idx = line.indexOf('=')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                var value = line.substring(idx + 1).trim()
                if (value.length >= 2 && (
                    (value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))
                )) {
                    value = value.substring(1, value.length - 1)
                }
                env[key] = value
            }
        }
    }
    return env
}

val dotEnv = loadDotEnv(rootDir)
val configuredOrigin = (project.findProperty("hubOrigin")?.toString()?.takeIf { it.isNotBlank() })
    ?: System.getenv("APPROVED_HUB_ORIGIN")?.takeIf { it.isNotBlank() }
    ?: dotEnv["APPROVED_HUB_ORIGIN"]?.takeIf { it.isNotBlank() }

val configuredApiKey = (project.findProperty("hubApiKey")?.toString()?.takeIf { it.isNotBlank() })
    ?: System.getenv("TABBYRUSH_API_KEY")?.takeIf { it.isNotBlank() }
    ?: dotEnv["TABBYRUSH_API_KEY"]?.takeIf { it.isNotBlank() }
val configuredFirebaseConfigPath = (project.findProperty("firebaseConfigPath")?.toString()?.takeIf { it.isNotBlank() })
    ?: System.getenv("TABBYRUSH_FIREBASE_CONFIG_PATH")?.takeIf { it.isNotBlank() }
    ?: dotEnv["TABBYRUSH_FIREBASE_CONFIG_PATH"]?.takeIf { it.isNotBlank() }

var firebaseConfigured = false
var firebaseProjectId = ""
var firebaseApplicationId = ""
var firebaseApiKey = ""
var firebaseGcmSenderId = ""

if (!configuredFirebaseConfigPath.isNullOrBlank()) {
    val configFile = file(configuredFirebaseConfigPath)
    if (configFile.exists() && configFile.isFile) {
        val content = configFile.readText(Charsets.UTF_8)
        // Security gate: Reject service-account / private-key files immediately
        val privateKeyMarker = "-----" + "BEGIN " + "PRIVATE KEY-----"
        if ((content.contains("\"type\"") && content.contains("\"service_account\"")) ||
            content.contains(privateKeyMarker) ||
            content.contains("\"private_key\"")
        ) {
            throw GradleException("TABBYRUSH_FIREBASE_CONFIG_PATH must contain public client configuration only. Detected private server service-account credentials!")
        }

        val jsonSlurper = groovy.json.JsonSlurper()
        val parsed = jsonSlurper.parseText(content) as? Map<*, *>
        if (parsed != null) {
            val projId = parsed["project_id"]?.toString()?.trim() ?: ""
            val appId = (parsed["application_id"] ?: parsed["app_id"] ?: parsed["mobilesdk_app_id"])?.toString()?.trim() ?: ""
            val apiKey = (parsed["api_key"] ?: parsed["current_key"])?.toString()?.trim() ?: ""
            val senderId = (parsed["gcm_sender_id"] ?: parsed["project_number"])?.toString()?.trim() ?: ""
            val pkgName = (parsed["package_name"] ?: parsed["client_package_name"])?.toString()?.trim()

            if (pkgName != null && pkgName.isNotBlank() && pkgName != "dev.tabbyrush.android") {
                throw GradleException("TABBYRUSH_FIREBASE_CONFIG_PATH package_name must equal 'dev.tabbyrush.android'. Found: '$pkgName'")
            }

            if (projId.isNotBlank() && appId.isNotBlank() && apiKey.isNotBlank() && senderId.isNotBlank()) {
                firebaseConfigured = true
                firebaseProjectId = projId
                firebaseApplicationId = appId
                firebaseApiKey = apiKey
                firebaseGcmSenderId = senderId
            }
        }
    }
}

fun escapeBuildConfig(str: String): String =
    "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "dev.tabbyrush.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.tabbyrush.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val debugOrigin = configuredOrigin ?: "http://10.0.2.2:3000"
        val releaseOrigin = configuredOrigin ?: ""
        val apiKey = configuredApiKey ?: ""

        buildConfigField("String", "HUB_API_KEY", escapeBuildConfig(apiKey))
        buildConfigField("String", "HUB_ORIGIN_DEBUG", escapeBuildConfig(debugOrigin))
        buildConfigField("String", "HUB_ORIGIN_RELEASE", escapeBuildConfig(releaseOrigin))
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfigured.toString())
        buildConfigField("String", "FIREBASE_PROJECT_ID", escapeBuildConfig(firebaseProjectId))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", escapeBuildConfig(firebaseApplicationId))
        buildConfigField("String", "FIREBASE_API_KEY", escapeBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_GCM_SENDER_ID", escapeBuildConfig(firebaseGcmSenderId))
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("TABBYRUSH_KEYSTORE")
            val keyAliasName = System.getenv("TABBYRUSH_KEY_ALIAS")
            val storePass = System.getenv("TABBYRUSH_STORE_PASSWORD")
            val keyPass = System.getenv("TABBYRUSH_KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() &&
                !keyAliasName.isNullOrBlank() &&
                !storePass.isNullOrBlank() &&
                !keyPass.isNullOrBlank()
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.all {
            it.testLogging {
                events("passed", "skipped", "failed")
            }
        }
    }
}

// Enforce that release builds fail closed when signing custody is missing,
// while preserving debug builds without requiring release secrets.
gradle.taskGraph.whenReady {
    val isReleaseTask = allTasks.any { task ->
        task.project == project && (task.name.contains("Release", ignoreCase = true) && !task.name.contains("UnitTest", ignoreCase = true))
    }
    if (isReleaseTask) {
        val signing = android.signingConfigs.getByName("release")
        if (signing.storeFile == null || !signing.storeFile!!.exists() ||
            signing.storePassword.isNullOrBlank() ||
            signing.keyAlias.isNullOrBlank() ||
            signing.keyPassword.isNullOrBlank()
        ) {
            throw GradleException("Release build requires TABBYRUSH_KEYSTORE, TABBYRUSH_KEY_ALIAS, TABBYRUSH_STORE_PASSWORD, and TABBYRUSH_KEY_PASSWORD environment variables with a valid keystore file.")
        }
        if (configuredOrigin.isNullOrBlank()) {
            throw GradleException("Release build requires an approved HTTPS origin. Set APPROVED_HUB_ORIGIN in environment or .env, or project property hubOrigin.")
        }
        val originUri = try { URI(configuredOrigin) } catch (_: Exception) { null }
        if (originUri == null || originUri.scheme != "https" || originUri.host.isNullOrBlank() ||
            originUri.rawUserInfo != null || originUri.rawQuery != null || originUri.rawFragment != null ||
            (originUri.rawPath.isNotEmpty() && originUri.rawPath != "/")
        ) {
            throw GradleException("Release build requires a valid canonical HTTPS origin without subpath, query, or credentials. Got: '$configuredOrigin'")
        }
        if (configuredApiKey.isNullOrBlank()) {
            throw GradleException("Release build requires a provisioned API key. Set TABBYRUSH_API_KEY in environment or .env, or project property hubApiKey.")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)

    // WorkManager for background sync scheduling
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Glance AppWidget
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // Firebase BoM and Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)


    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android Test dependencies
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.work:work-testing:2.9.1")
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.LENIENT)
}
