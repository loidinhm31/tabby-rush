#!/usr/bin/env bash
set -euo pipefail

# TabbyRush Linux Desktop & RPM Package Verification Smoke Runner
# Phase 09 Validation & Diagnostics Harness

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RPM_FILE=""
DIST_DIR=""
MODE="validate"

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Options:
  --rpm <file>       Path to built RPM package (.rpm)
  --dist <dir>       Path to createDistributable application directory
  --diagnose         Diagnose host environment (rpm, rpm-build, D-Bus, secret-tool)
  --help             Show this help message

Examples:
  $0 --diagnose
  $0 --rpm desktopApp/build/compose/binaries/main/rpm/tabbyrush-1.0.0.rpm
  $0 --dist desktopApp/build/compose/binaries/main/app/tabbyrush
EOF
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --rpm)
            RPM_FILE="$2"
            shift 2
            ;;
        --dist)
            DIST_DIR="$2"
            shift 2
            ;;
        --diagnose)
            MODE="diagnose"
            shift
            ;;
        --help)
            usage
            ;;
        *)
            echo "Unknown argument: $1" >&2
            usage
            ;;
    esac
done

if [[ "$MODE" == "diagnose" ]]; then
    echo "=== TabbyRush Linux Desktop Environment Diagnostics ==="
    echo "1. Host OS: $(uname -s) $(uname -r) ($(uname -m))"

    echo -n "2. Java Runtime: "
    if command -v java >/dev/null 2>&1; then
        java -version 2>&1 | head -n 1
    else
        echo "NOT FOUND in PATH"
    fi

    echo -n "3. jpackage tool: "
    if command -v jpackage >/dev/null 2>&1; then
        jpackage --version 2>&1
    elif [[ -x "${JAVA_HOME:-}/bin/jpackage" ]]; then
        "${JAVA_HOME}/bin/jpackage" --version 2>&1
    else
        echo "NOT FOUND"
    fi

    echo -n "4. RPM query tool (rpm): "
    if command -v rpm >/dev/null 2>&1; then
        rpm --version
    else
        echo "MISSING"
    fi

    echo -n "5. RPM build tool (rpmbuild): "
    if command -v rpmbuild >/dev/null 2>&1; then
        rpmbuild --version
    else
        echo "NOT INSTALLED (Required for :desktopApp:packageRpm; install via 'sudo dnf install -y rpm-build')"
    fi

    echo -n "6. Secret Service CLI (secret-tool): "
    if command -v secret-tool >/dev/null 2>&1; then
        echo "PRESENT ($(which secret-tool))"
    else
        echo "NOT FOUND (Install via 'sudo dnf install -y libsecret')"
    fi

    echo -n "7. D-Bus session bus: "
    if [[ -n "${DBUS_SESSION_BUS_ADDRESS:-}" ]]; then
        echo "CONNECTED ($DBUS_SESSION_BUS_ADDRESS)"
    else
        echo "NOT CONNECTED (Headless / non-GUI session; run inside Xpra or desktop terminal)"
    fi

    echo -n "8. X11 / Wayland Display: "
    if [[ -n "${DISPLAY:-}" || -n "${WAYLAND_DISPLAY:-}" ]]; then
        echo "PRESENT (DISPLAY=${DISPLAY:-none}, WAYLAND=${WAYLAND_DISPLAY:-none})"
    else
        echo "NOT SET (Use Xpra display :100 or native desktop display)"
    fi

    echo "=== Diagnostics Complete ==="
    exit 0
fi

if [[ -n "$DIST_DIR" ]]; then
    echo "=== Verifying Unpacked Linux Distributable ==="
    if [[ ! -d "$DIST_DIR" ]]; then
        echo "ERROR: Distributable directory does not exist: $DIST_DIR" >&2
        exit 1
    fi

    LAUNCHER="$DIST_DIR/bin/tabbyrush"
    if [[ ! -x "$LAUNCHER" ]]; then
        LAUNCHER="$DIST_DIR/bin/TabbyRush"
    fi
    if [[ ! -x "$LAUNCHER" ]]; then
        echo "ERROR: TabbyRush executable launcher not found in $DIST_DIR/bin" >&2
        exit 1
    fi
    echo "✓ Found executable launcher: $LAUNCHER"

    RUNTIME_JAVA="$DIST_DIR/runtime/bin/java"
    if [[ -x "$RUNTIME_JAVA" ]]; then
        echo -n "✓ Bundled self-contained JRE: "
        "$RUNTIME_JAVA" -version 2>&1 | head -n 1
    else
        echo "WARNING: Bundled runtime binary not found at $RUNTIME_JAVA"
    fi

    # Check packaged jar for Linux provider and absence of Windows classes
    echo "Checking packaged application jars..."
    APP_JARS=$(find "$DIST_DIR/lib" -name "*.jar" 2>/dev/null || true)
    FOUND_LINUX_PROVIDER=false
    FOUND_WINDOWS_PROVIDER=false

    for jar in $APP_JARS; do
        if unzip -l "$jar" 2>/dev/null | grep -q "dev/tabbyrush/desktop/linux/LinuxSecretServiceVaultProvider.class"; then
            FOUND_LINUX_PROVIDER=true
        fi
        if unzip -l "$jar" 2>/dev/null | grep -q "WindowsDpapiVaultProvider"; then
            FOUND_WINDOWS_PROVIDER=true
        fi
    done

    if [[ "$FOUND_LINUX_PROVIDER" == "true" ]]; then
        echo "✓ Packaged LinuxSecretServiceVaultProvider detected"
    else
        echo "WARNING: LinuxSecretServiceVaultProvider not found in packaged jars"
    fi

    if [[ "$FOUND_WINDOWS_PROVIDER" == "true" ]]; then
        echo "ERROR: Forbidden WindowsDpapiVaultProvider detected in Linux distribution!" >&2
        exit 1
    else
        echo "✓ No Windows provider classes packaged in Linux bundle"
    fi

    echo "=== Distributable Verification Passed ==="
    exit 0
fi

if [[ -n "$RPM_FILE" ]]; then
    echo "=== Verifying RPM Package: $RPM_FILE ==="
    if [[ ! -f "$RPM_FILE" ]]; then
        echo "ERROR: RPM file does not exist: $RPM_FILE" >&2
        exit 1
    fi

    # Check SHA-256
    SHA256=$(sha256sum "$RPM_FILE" | awk '{print $1}')
    SIZE_BYTES=$(stat -c "%s" "$RPM_FILE" 2>/dev/null || stat -f "%z" "$RPM_FILE")
    echo "File: $(basename "$RPM_FILE")"
    echo "Size: $SIZE_BYTES bytes"
    echo "SHA-256: $SHA256"

    # Query RPM Metadata
    echo "--- RPM Package Info (rpm -qpi) ---"
    rpm -qpi "$RPM_FILE"

    echo "--- RPM Package Contents Summary (rpm -qlp) ---"
    FILE_COUNT=$(rpm -qlp "$RPM_FILE" | wc -l)
    echo "Total files packaged: $FILE_COUNT"

    # Verify key paths
    if rpm -qlp "$RPM_FILE" | grep -q "/bin/tabbyrush"; then
        echo "✓ RPM packages executable launcher: /bin/tabbyrush"
    else
        echo "WARNING: /bin/tabbyrush not found in RPM file list"
    fi

    if rpm -qlp "$RPM_FILE" | grep -q "/share/applications/.*\.desktop"; then
        echo "✓ RPM packages desktop entry file"
    fi

    # Check for any forbidden Windows artifacts
    if rpm -qlp "$RPM_FILE" | grep -iE '\.dll$|\.exe$|windows'; then
        echo "ERROR: Forbidden Windows binaries/paths detected in Linux RPM!" >&2
        exit 1
    else
        echo "✓ Clean Linux RPM: Zero Windows DLLs or executables"
    fi

    echo "=== RPM Package Verification Complete ==="
    exit 0
fi

echo "ERROR: Either --rpm, --dist, or --diagnose must be specified." >&2
usage
