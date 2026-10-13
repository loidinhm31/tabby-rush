pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "doggo"

// Staged module assembly: shared is included immediately.
// Application modules are guarded by the existence of their owned build scripts.
include(":shared")

if (file("androidApp/build.gradle.kts").exists()) {
    include(":androidApp")
}

if (file("desktopApp/build.gradle.kts").exists()) {
    include(":desktopApp")
}
