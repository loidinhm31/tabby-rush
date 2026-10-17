<#
.SYNOPSIS
    TabbyRush Runner Script for Windows PowerShell
.DESCRIPTION
    Provides simple command shortcuts to build, test, and run TabbyRush on Desktop and Android.
.EXAMPLE
    .\run.ps1 desktop
    .\run.ps1 android
    .\run.ps1 test
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("desktop", "run-desktop", "desktop-debug", "run-desktop-debug", "android", "run-android", "install-android", "build-desktop", "build-android", "test", "clean", "help")]
    [string]$Command = "help",

    [Parameter()]
    [ValidateSet("off", "strict")]
    [string]$DependencyVerification = "off"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = $PSScriptRoot
$Gradlew = Join-Path $RootDir "gradlew.bat"
$PackageName = "dev.tabbyrush.android"
$MainActivity = ".MainActivity"

function Find-Adb {
    $adbCmd = Get-Command "adb" -ErrorAction SilentlyContinue
    if ($adbCmd) {
        return $adbCmd.Source
    }

    $localProps = Join-Path $RootDir "local.properties"
    if (Test-Path $localProps) {
        $sdkDirLine = Get-Content $localProps | Where-Object { $_ -match "^sdk\.dir=(.+)$" } | Select-Object -First 1
        if ($sdkDirLine -match "^sdk\.dir=(.+)$") {
            $sdkDir = $matches[1].Replace('\\', '\').Trim()
            $candidate = Join-Path $sdkDir "platform-tools\adb.exe"
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }

    $defaultSdk = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $defaultSdk) {
        return $defaultSdk
    }

    throw "adb not found in PATH or Android SDK directory ($defaultSdk). Please ensure Android SDK platform-tools is installed."
}

function Invoke-Gradle {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$GradleArgs
    )
    & $Gradlew @GradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE."
    }
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$AdbArgs
    )
    $adb = Find-Adb
    & $adb @AdbArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb command failed with exit code $LASTEXITCODE."
    }
}

function Show-Help {
    Write-Host "TabbyRush runner commands:" -ForegroundColor Cyan
    Write-Host "  .\run.ps1 desktop          - Run Compose Desktop application"
    Write-Host "  .\run.ps1 desktop-debug    - Run Compose Desktop in debug mode (local HTTP)"
    Write-Host "  .\run.ps1 android          - Build, install and launch Android app on device/emulator"
    Write-Host "  .\run.ps1 install-android  - Install debug APK to connected Android device"
    Write-Host "  .\run.ps1 build-desktop    - Build desktop standalone distributable"
    Write-Host "  .\run.ps1 build-android    - Build Android debug APK"
    Write-Host "  .\run.ps1 test             - Run shared and desktop unit tests"
    Write-Host "  .\run.ps1 clean            - Clean build artifacts"
    Write-Host ""
    Write-Host "Options:" -ForegroundColor Cyan
    Write-Host "  -DependencyVerification off|strict (default: off due to 149 AGP/KSP buildscript hashes in verification metadata)"
}

trap {
    $code = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
    exit $code
}

Push-Location $RootDir
try {
    $gradleOpts = @("--dependency-verification=$DependencyVerification")

    switch ($Command) {
        { $_ -in "desktop", "run-desktop" } {
            Write-Host "Starting TabbyRush Desktop..." -ForegroundColor Green
            $argsToPass = @("-PdesktopTarget=windows", ":desktopApp:run") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        { $_ -in "desktop-debug", "run-desktop-debug" } {
            Write-Host "Starting TabbyRush Desktop (Debug)..." -ForegroundColor Green
            $argsToPass = @("-PdesktopTarget=windows", ":desktopApp:runDebug") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        "install-android" {
            Write-Host "Installing TabbyRush to Android device/emulator..." -ForegroundColor Green
            $argsToPass = @(":androidApp:installDebug") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        { $_ -in "android", "run-android" } {
            Write-Host "Building and installing TabbyRush to Android device/emulator..." -ForegroundColor Green
            $argsToPass = @(":androidApp:installDebug") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
            $targetComponent = "$PackageName/$MainActivity"
            Write-Host "Launching $targetComponent via adb..." -ForegroundColor Green
            Invoke-Adb -AdbArgs @("shell", "am", "start", "-n", $targetComponent)
        }
        "build-desktop" {
            Write-Host "Building Desktop Distributable..." -ForegroundColor Green
            $argsToPass = @("-PdesktopTarget=windows", ":desktopApp:createDistributable") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        "build-android" {
            Write-Host "Building Android Debug APK..." -ForegroundColor Green
            $argsToPass = @(":androidApp:assembleDebug") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        "test" {
            Write-Host "Running unit tests..." -ForegroundColor Green
            $argsToPass = @("-PdesktopTarget=windows", ":shared:jvmTest", ":desktopApp:test") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        "clean" {
            Write-Host "Cleaning build directory..." -ForegroundColor Green
            $argsToPass = @("clean") + $gradleOpts
            Invoke-Gradle -GradleArgs $argsToPass
        }
        Default {
            Show-Help
        }
    }
}
finally {
    Pop-Location
}
