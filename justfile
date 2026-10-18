# TabbyRush Task Runner (Justfile)
# Requires Just: https://github.com/casey/just
# Install on Windows via: winget install Casey.Just  or  cargo install just

set windows-shell := ["cmd.exe", "/c"]

# Target OS detection (supported desktop targets: windows, linux)
desktop_target := if os() == "windows" { "windows" } else if os() == "linux" { "linux" } else { error("Unsupported desktop OS: " + os()) }
gradlew := if os() == "windows" { ".\\gradlew.bat" } else { "./gradlew" }
android_sdk := env_var_or_default("ANDROID_HOME", if os() == "windows" { env_var_or_default("LOCALAPPDATA", "") / "Android" / "Sdk" } else { "" })
adb := env("ADB", if os() == "windows" { android_sdk / "platform-tools" / "adb.exe" } else { "adb" })
emulator := env("EMULATOR", if os() == "windows" { android_sdk / "emulator" / "emulator.exe" } else if android_sdk != "" { android_sdk / "emulator" / "emulator" } else { "emulator" })
default_avd := env("AVD_NAME", "tabbyrush_android")

# Application configuration (refactored namespace)
package_name := "dev.tabbyrush.android"
main_activity := ".MainActivity"

# Supply-chain verification default (set to "off" by default because 149 AGP/KSP buildscript hashes are unrecorded in gradle/verification-metadata.xml; pass 'strict' to enforce)
gradle_verification := "off"

# List available recipes
default:
    @just --list

# Run TabbyRush Compose Desktop application (e.g. `just run-desktop` or `just run-desktop strict`)
run-desktop verification=gradle_verification:
    {{ gradlew }} -PdesktopTarget={{ desktop_target }} :desktopApp:run --dependency-verification={{ verification }}

# Run TabbyRush Compose Desktop in debug mode (local HTTP enabled)
run-desktop-debug verification=gradle_verification:
    {{ gradlew }} -PdesktopTarget={{ desktop_target }} :desktopApp:runDebug --dependency-verification={{ verification }}

# Install debug APK to connected Android device or emulator
install-android verification=gradle_verification:
    {{ gradlew }} :androidApp:installDebug --dependency-verification={{ verification }}

# Build, install, and launch TabbyRush Android application on device or emulator
run-android verification=gradle_verification:
    {{ gradlew }} :androidApp:installDebug --dependency-verification={{ verification }}
    "{{ adb }}" shell am start -n {{ package_name }}/{{ main_activity }}

# List available Android emulators (AVDs)
list-emulators:
    "{{ emulator }}" -list-avds

alias emulators := list-emulators
alias list-avds := list-emulators

# Start Android emulator (e.g. `just start-emulator`, `just start-emulator Pixel_6`)
start-emulator avd=default_avd *args="":
    "{{ emulator }}" -avd {{ avd }} {{ args }}

alias run-emulator := start-emulator

# Build desktop standalone distributable
build-desktop verification=gradle_verification:
    {{ gradlew }} -PdesktopTarget={{ desktop_target }} :desktopApp:createDistributable --dependency-verification={{ verification }}

# Build Android debug APK
build-android verification=gradle_verification:
    {{ gradlew }} :androidApp:assembleDebug --dependency-verification={{ verification }}

# Run shared and desktop unit tests
test verification=gradle_verification:
    {{ gradlew }} -PdesktopTarget={{ desktop_target }} :shared:jvmTest :desktopApp:test --dependency-verification={{ verification }}

# Clean build artifacts
clean verification=gradle_verification:
    {{ gradlew }} clean --dependency-verification={{ verification }}

# Bump application version in gradle.properties (e.g. `just bump patch`, `just bump minor`, `just bump 1.1.0 --dry-run`)
bump type_or_version="patch" *args="":
    node scripts/bump-version.mjs {{ type_or_version }} {{ args }}

# Bump application version and create git release tag (e.g. `just bump-tag patch`)
bump-tag type_or_version="patch" *args="":
    node scripts/bump-version.mjs {{ type_or_version }} --git-tag {{ args }}
