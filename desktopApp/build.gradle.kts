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
        if (desktopTarget == "windows") {
            resources.srcDirs("build/native/windows/Release")
        }
    }
    test {
        kotlin.srcDirs("src/test/kotlin", "src/${desktopTarget}Test/kotlin")
        resources.srcDirs("src/test/resources", "src/${desktopTarget}Test/resources")
    }
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
val configuredVersion = project.findProperty("appVersion")?.toString()?.takeIf { it.isNotBlank() }
    ?: throw GradleException("appVersion property is missing or blank in gradle.properties")

compose.desktop {
    application {
        mainClass = "dev.tabbyrush.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Msi)
            packageName = if (desktopTarget == "linux") "tabbyrush" else "TabbyRush"
            packageVersion = configuredVersion
            description = "TabbyRush personal task management with standard Glean Hub synchronization"
            vendor = "TabbyRush Team"
            copyright = "Copyright 2026 TabbyRush"

            linux {
                packageName = "tabbyrush"
                rpmPackageVersion = configuredVersion
                menuGroup = "Office"
                shortcut = true
                iconFile.set(project.file("src/main/resources/tabbyrush-icon.png"))
            }

            windows {
                packageName = "TabbyRush"
                upgradeUuid = "a6c507c8-88fe-4e49-8f43-a6125039281a"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("src/windowsMain/resources/tabbyrush.ico"))
            }

            modules("java.base", "java.desktop", "java.net.http", "java.sql", "java.naming", "jdk.unsupported")
            includeAllModules = true
        }
    }
}

dependencyLocking {
    lockFile.set(file("gradle-$desktopTarget.lockfile"))
    lockMode.set(LockMode.LENIENT)
    lockAllConfigurations()
}

tasks.register<JavaExec>("runDebug") {
    group = "application"
    description = "Runs TabbyRush Desktop in debug mode with local HTTP allowed"
    mainClass.set("dev.tabbyrush.desktop.DevelopmentLauncher")
    classpath = sourceSets["main"].runtimeClasspath
    if (providers.gradleProperty("isolatedTestConfig").orNull == "true") {
        systemProperty("tabbyrush.isolatedTestConfig", "true")
    }
}

tasks.withType<Test> {
    if (providers.gradleProperty("isolatedTestConfig").orNull == "true") {
        systemProperty("tabbyrush.isolatedTestConfig", "true")
    }
    // Sanitize host credentials so unit tests run in clean hermetic test sandbox
    environment("APPROVED_HUB_ORIGIN", "")
    environment("TABBYRUSH_API_KEY", "")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Guard against bare packageMsi producing shortcuts without System.AppUserModel.ID
if (desktopTarget == "windows") {
    tasks.matching { it.name == "packageMsi" }.configureEach {
        doFirst {
            if (!project.hasProperty("allowBarePackageMsi")) {
                throw GradleException(
                    "Direct execution of 'packageMsi' is disabled to prevent shipping shortcuts without System.AppUserModel.ID. " +
                    "Use ':desktopApp:packageRemoteMsi', 'scripts/build-windows-remote-msi.ps1', or pass -PallowBarePackageMsi."
                )
            }
        }
    }
}

tasks.register("packageRemoteMsi") {
    group = "distribution"
    description = "Packages Windows per-user MSI with native tabbyrush-toast.dll and AUMID shortcut properties using WiX"
    doLast {
        if (desktopTarget != "windows") {
            throw GradleException("packageRemoteMsi requires Windows x64 environment with MSVC, Windows SDK, and WiX Toolset. Current target: $desktopTarget")
        }
        val script = rootProject.file("scripts/build-windows-remote-msi.ps1")
        val verification = project.findProperty("gradle_verification")?.toString() ?: "off"
        val appVer = project.findProperty("appVersion")?.toString() ?: configuredVersion
        val process = ProcessBuilder(
            "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath,
            "-Verification", verification, "-Version", appVer
        )
            .inheritIO()
            .start()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("build-windows-remote-msi.ps1 failed with exit code $exitCode")
        }
    }
}
