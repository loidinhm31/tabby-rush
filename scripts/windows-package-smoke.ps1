<#
.SYNOPSIS
    Doggo Windows Desktop & MSI Package Verification Smoke Runner.
    Phase 10 Validation & Diagnostics Harness.

.DESCRIPTION
    Verifies the Windows build environment, inspects self-contained distributable bundles,
    audits packaged JARs for target isolation (Windows DPAPI present, Linux D-Bus absent),
    computes SHA-256 hashes, inspects Authenticode signatures, and validates MSI metadata.

.PARAMETER Diagnose
    Displays host OS, Java runtime, jpackage, and WiX toolset status.

.PARAMETER DistDir
    Path to the createDistributable output directory to validate.

.PARAMETER MsiPath
    Path to the packaged .msi file to inspect.

.EXAMPLE
    .\scripts\windows-package-smoke.ps1 -Diagnose
    .\scripts\windows-package-smoke.ps1 -DistDir desktopApp\build\compose\binaries\main\app\Doggo
    .\scripts\windows-package-smoke.ps1 -MsiPath desktopApp\build\compose\binaries\main\msi\Doggo-1.0.0.msi
#>

[CmdletBinding()]
param(
    [switch]$Diagnose,
    [string]$DistDir = "",
    [string]$MsiPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Show-Diagnostics {
    Write-Host "=== Doggo Windows Desktop Environment Diagnostics ===" -ForegroundColor Cyan

    # 1. Host OS
    $os = [System.Environment]::OSVersion
    $arch = [System.Environment]::GetEnvironmentVariable("PROCESSOR_ARCHITECTURE")
    Write-Host "1. Host OS: Windows $($os.Version) ($arch)"

    # 2. Java Runtime
    Write-Host -NoNewline "2. Java Runtime: "
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $javaVer = (& java -version 2>&1 | Select-Object -First 1).ToString()
        $ErrorActionPreference = $prev
        Write-Host "PRESENT ($javaVer) at $($javaCmd.Source)" -ForegroundColor Green
    } else {
        Write-Host "NOT FOUND in PATH" -ForegroundColor Red
    }

    # 3. jpackage tool
    Write-Host -NoNewline "3. jpackage tool: "
    $jpCmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($jpCmd) {
        $jpVer = & jpackage --version 2>&1 | Select-Object -First 1
        Write-Host "PRESENT ($jpVer) at $($jpCmd.Source)" -ForegroundColor Green
    } else {
        Write-Host "NOT FOUND in PATH" -ForegroundColor Yellow
    }

    # 4. WiX Toolset (candle.exe, light.exe)
    Write-Host -NoNewline "4. WiX Toolset (candle / light): "
    $candleCmd = Get-Command candle -ErrorAction SilentlyContinue
    $lightCmd = Get-Command light -ErrorAction SilentlyContinue
    if ($candleCmd -and $lightCmd) {
        Write-Host "PRESENT (candle: $($candleCmd.Source), light: $($lightCmd.Source))" -ForegroundColor Green
    } else {
        Write-Host "NOT INSTALLED in PATH (Required for :desktopApp:packageMsi; install WiX 3.11+)" -ForegroundColor Yellow
    }

    # 5. PowerShell Environment
    Write-Host "5. PowerShell Version: $($PSVersionTable.PSVersion)"
    Write-Host "6. Execution Policy: $(Get-ExecutionPolicy)"

    Write-Host "=== Diagnostics Complete ===" -ForegroundColor Cyan
}

function Test-DistributableBundle {
    param([string]$Path)

    Write-Host "`n=== Auditing Distributable Directory: $Path ===" -ForegroundColor Cyan

    if (-not (Test-Path $Path)) {
        Write-Error "Distributable directory does not exist: $Path"
        return $false
    }

    # 1. Verify launcher executable
    $exeCandidates = @(
        (Join-Path $Path "Doggo.exe"),
        (Join-Path $Path "doggo.exe")
    )
    $launcher = $exeCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

    if ($launcher) {
        $fileInfo = Get-Item $launcher
        Write-Host "[PASS] Launcher executable found: $($fileInfo.Name) ($([math]::Round($fileInfo.Length / 1KB, 2)) KB)" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] Launcher executable (Doggo.exe) not found in $Path"
        return $false
    }

    # 2. Verify bundled runtime
    $jvmCandidates = @(
        (Join-Path $Path "runtime\bin\server\jvm.dll"),
        (Join-Path $Path "runtime\bin\java.dll"),
        (Join-Path $Path "runtime\bin\java.exe")
    )
    $runtimeBin = $jvmCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($runtimeBin) {
        Write-Host "[PASS] Bundled Java runtime found: $runtimeBin" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Bundled runtime not found at standard path in $Path\runtime" -ForegroundColor Yellow
    }

    # 3. Verify packaged application JARs and inspect classes
    $appDir = Join-Path $Path "app"
    if (-not (Test-Path $appDir)) {
        $appDir = $Path
    }

    $jarFiles = @(Get-ChildItem -Path $appDir -Filter "*.jar" -Recurse -ErrorAction SilentlyContinue)
    if ($jarFiles.Count -eq 0) {
        Write-Error "[FAIL] No JAR files found in app directory: $appDir"
        return $false
    }

    Write-Host "[INFO] Found $($jarFiles.Count) packaged JAR artifact(s) in $appDir"

    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $foundWindowsVault = $false
    $foundWindowsSpi = $false
    $foundLinuxDbus = $false
    $registeredProviders = [System.Collections.Generic.List[string]]::new()

    foreach ($jar in $jarFiles) {
        if ($jar.Name -like "*dbus*" -or $jar.Name -like "*secret-service*") {
            $foundLinuxDbus = $true
        }
        $zip = $null
        try {
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -like "*WindowsDpapiVault*") {
                    $foundWindowsVault = $true
                }
                if ($entry.FullName -eq "META-INF/services/dev.doggo.desktop.CredentialVaultProvider") {
                    $stream = $entry.Open()
                    $reader = $null
                    try {
                        $reader = New-Object System.IO.StreamReader($stream)
                        while (-not $reader.EndOfStream) {
                            $line = $reader.ReadLine().Trim()
                            if ($line -ne "" -and -not $line.StartsWith("#")) {
                                $registeredProviders.Add($line)
                                if ($line -eq "dev.doggo.desktop.windows.WindowsDpapiVaultProvider") {
                                    $foundWindowsSpi = $true
                                }
                            }
                        }
                    } finally {
                        if ($null -ne $reader) { $reader.Close() }
                        $stream.Close()
                    }
                }
                if ($entry.FullName -like "*LinuxSecretServiceVault*" -or $entry.FullName -like "*org/freedesktop/dbus/*") {
                    $foundLinuxDbus = $true
                }
            }
        } catch {
            Write-Error "[FAIL] Corrupted or unreadable JAR file detected: $($jar.FullName): $($_.Exception.Message)"
            return $false
        } finally {
            if ($null -ne $zip) {
                $zip.Dispose()
            }
        }
    }

    if ($foundWindowsVault) {
        Write-Host "[PASS] WindowsDpapiVault class packaged in desktop bundle" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] WindowsDpapiVault class NOT found in packaged JARs"
        return $false
    }

    if ($foundWindowsSpi) {
        Write-Host "[PASS] WindowsDpapiVaultProvider registered in META-INF/services" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] WindowsDpapiVaultProvider SPI registration NOT found in packaged JARs"
        return $false
    }

    if ($registeredProviders.Count -ne 1) {
        Write-Error "[FAIL] Expected exactly one CredentialVaultProvider, but found $($registeredProviders.Count): $($registeredProviders -join ', ')"
        return $false
    } else {
        Write-Host "[PASS] Exactly one CredentialVaultProvider registered: $($registeredProviders[0])" -ForegroundColor Green
    }

    if (-not $foundLinuxDbus) {
        Write-Host "[PASS] Target Isolation: Zero Linux D-Bus / SecretService classes leaked into Windows bundle" -ForegroundColor Green
    } else {
        Write-Error "[FAIL] Target Isolation Violated: Linux D-Bus classes detected in Windows bundle"
        return $false
    }

    Write-Host "=== Distributable Audit Passed ===" -ForegroundColor Green
    return $true
}

function Test-MsiPackage {
    param([string]$Path)

    Write-Host "`n=== Auditing Windows Installer Package: $Path ===" -ForegroundColor Cyan

    if (-not (Test-Path $Path)) {
        Write-Error "MSI file does not exist: $Path"
        return $false
    }

    $fileItem = Get-Item $Path
    Write-Host "1. File: $($fileItem.Name) ($([math]::Round($fileItem.Length / 1MB, 2)) MB)"

    # Compute SHA-256
    $hash = Get-FileHash -Path $Path -Algorithm SHA256
    Write-Host "2. SHA-256 Digest: $($hash.Hash)"

    # Check Authenticode signature
    $sig = Get-AuthenticodeSignature -FilePath $Path
    Write-Host "3. Authenticode Status: $($sig.Status) ($($sig.StatusMessage))"
    if ($sig.Status -eq "NotSigned") {
        Write-Host "   [NOTE] Package is unsigned (development/testing build; no public publisher certificate)" -ForegroundColor Yellow
    }

    # Inspect MSI properties via Windows Installer COM
    $installer = $null
    $database = $null
    $view = $null
    try {
        $installer = New-Object -ComObject WindowsInstaller.Installer
        $database = $installer.GetType().InvokeMember(
            "OpenDatabase",
            [System.Reflection.BindingFlags]::InvokeMethod,
            $null,
            $installer,
            @($Path, 0)
        )
        $view = $database.GetType().InvokeMember(
            "OpenView",
            [System.Reflection.BindingFlags]::InvokeMethod,
            $null,
            $database,
            @("SELECT Property, Value FROM Property")
        )
        $view.GetType().InvokeMember("Execute", [System.Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)

        $targetProps = @("ProductName", "ProductVersion", "ProductCode", "UpgradeCode", "Manufacturer")
        $extractedProps = @{}
        Write-Host "4. MSI Metadata Properties:"
        while ($true) {
            $record = $view.GetType().InvokeMember("Fetch", [System.Reflection.BindingFlags]::InvokeMethod, $null, $view, $null)
            if ($null -eq $record) { break }
            $prop = $record.GetType().InvokeMember("StringData", [System.Reflection.BindingFlags]::GetProperty, $null, $record, @(1))
            $val = $record.GetType().InvokeMember("StringData", [System.Reflection.BindingFlags]::GetProperty, $null, $record, @(2))
            if ($targetProps -contains $prop) {
                Write-Host "   - ${prop}: ${val}"
                $extractedProps[$prop] = $val
            }
        }

        # Validate mandatory properties and expected values
        if ($extractedProps["ProductName"] -ne "Doggo") {
            Write-Error "[FAIL] ProductName mismatch: expected 'Doggo', got '$($extractedProps['ProductName'])'"
            return $false
        }
        if ($extractedProps["ProductVersion"] -ne "1.0.0") {
            Write-Error "[FAIL] ProductVersion mismatch: expected '1.0.0', got '$($extractedProps['ProductVersion'])'"
            return $false
        }
        if ($extractedProps["Manufacturer"] -ne "Doggo Team") {
            Write-Error "[FAIL] Manufacturer mismatch: expected 'Doggo Team', got '$($extractedProps['Manufacturer'])'"
            return $false
        }
        if ($extractedProps["UpgradeCode"] -ne "{A6C507C8-88FE-4E49-8F43-A6125039281A}") {
            Write-Error "[FAIL] UpgradeCode mismatch: expected '{A6C507C8-88FE-4E49-8F43-A6125039281A}', got '$($extractedProps['UpgradeCode'])'"
            return $false
        }
        if ([string]::IsNullOrWhiteSpace($extractedProps["ProductCode"])) {
            Write-Error "[FAIL] ProductCode is missing or empty"
            return $false
        }
        Write-Host "[PASS] All mandatory MSI metadata properties validated successfully" -ForegroundColor Green
    } catch {
        Write-Error "[FAIL] MSI COM inspection failed: $($_.Exception.Message)"
        return $false
    } finally {
        if ($null -ne $view) {
            try { $view.GetType().InvokeMember("Close", [System.Reflection.BindingFlags]::InvokeMethod, $null, $view, $null) } catch {}
        }
        if ($null -ne $database) {
            [System.Runtime.InteropServices.Marshal]::ReleaseComObject($database) | Out-Null
        }
        if ($null -ne $installer) {
            [System.Runtime.InteropServices.Marshal]::ReleaseComObject($installer) | Out-Null
        }
    }

    Write-Host "=== MSI Audit Complete ===" -ForegroundColor Green
    return $true
}

# Entrypoint routing
if ($Diagnose) {
    Show-Diagnostics
    exit 0
}

$success = $true

if ($DistDir -ne "") {
    $res = Test-DistributableBundle -Path $DistDir
    if (-not $res) { $success = $false }
}

if ($MsiPath -ne "") {
    $res = Test-MsiPackage -Path $MsiPath
    if (-not $res) { $success = $false }
}

if (-not $Diagnose -and $DistDir -eq "" -and $MsiPath -eq "") {
    Write-Host "No action specified. Run with -Diagnose, -DistDir <path>, or -MsiPath <path>." -ForegroundColor Yellow
    Show-Diagnostics
}

if ($success) { exit 0 } else { exit 1 }
