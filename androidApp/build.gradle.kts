plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.doggo.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.doggo.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "HUB_API_KEY", "\"doggo-dev-api-key\"")
        buildConfigField("String", "HUB_ORIGIN_DEBUG", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "HUB_ORIGIN_RELEASE", "\"https://hub.glean.dev\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("DOGGO_KEYSTORE")
            val keyAliasName = System.getenv("DOGGO_KEY_ALIAS")
            val storePass = System.getenv("DOGGO_STORE_PASSWORD")
            val keyPass = System.getenv("DOGGO_KEY_PASSWORD")

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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
        if (signing.storeFile == null || !signing.storeFile!!.exists() || signing.storePassword.isNullOrBlank()) {
            throw GradleException("Release build requires DOGGO_KEYSTORE, DOGGO_KEY_ALIAS, DOGGO_STORE_PASSWORD, and DOGGO_KEY_PASSWORD environment variables with a valid keystore file.")
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
