import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val detectedHost = when {
    System.getProperty("os.name").lowercase().contains("linux") -> "linux"
    System.getProperty("os.name").lowercase().contains("win") -> "windows"
    else -> "unsupported"
}

val requestedTarget = project.findProperty("desktopTarget")?.toString() ?: detectedHost

if (requestedTarget != "linux" && requestedTarget != "windows") {
    throw GradleException("Unsupported desktop target: '$requestedTarget'. Supported targets are 'linux' and 'windows'.")
}

if (project.hasProperty("desktopTarget") && requestedTarget != detectedHost) {
    throw GradleException("Requested target '$requestedTarget' does not match detected host OS '$detectedHost'")
}

val desktopTarget = requestedTarget

sourceSets {
    main {
        kotlin.srcDirs("src/main/kotlin", "src/${desktopTarget}Main/kotlin")
        resources.srcDirs("src/main/resources", "src/${desktopTarget}Main/resources")
    }
    test {
        kotlin.srcDirs("src/test/kotlin", "src/${desktopTarget}Test/kotlin")
        resources.srcDirs("src/test/resources", "src/${desktopTarget}Test/resources")
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)

    if (desktopTarget == "linux") {
        implementation(libs.dbus.java)
    } else if (desktopTarget == "windows") {
        implementation(libs.jna)
        implementation(libs.jna.platform)
    }

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "dev.doggo.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Msi)
            packageName = if (desktopTarget == "linux") "doggo" else "Doggo"
            packageVersion = "1.0.0"
            description = "Doggo personal task management with strict-v1 Glean Hub synchronization"
            vendor = "Doggo Team"
            copyright = "Copyright 2026 Doggo"

            linux {
                packageName = "doggo"
                rpmPackageVersion = "1.0.0"
                menuGroup = "Office"
                shortcut = true
            }

            windows {
                packageName = "Doggo"
                upgradeUuid = "a6c507c8-88fe-4e49-8f43-a6125039281a"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }

            modules("java.base", "java.desktop", "java.net.http", "java.sql", "java.naming", "jdk.unsupported")
            includeAllModules = true
        }
    }
}

dependencyLocking {
    lockFile.set(file("gradle-$desktopTarget.lockfile"))
    lockAllConfigurations()
}

tasks.register<JavaExec>("runDebug") {
    group = "application"
    description = "Runs Doggo Desktop in debug mode with local HTTP allowed"
    mainClass.set("dev.doggo.desktop.DevelopmentLauncher")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.withType<Test> {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
