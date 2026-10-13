#!/usr/bin/env bash
# scripts/android-sdk-env.sh - Export Android SDK and tooling environment variables
# Usage: source scripts/android-sdk-env.sh

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "Notice: This script is intended to be sourced. Run: source $0" >&2
fi

# 1. Base Android SDK location
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_SDK_ROOT="${ANDROID_HOME}"

# 2. Tool paths
for tool_dir in "${ANDROID_HOME}/cmdline-tools/latest/bin" "${ANDROID_HOME}/platform-tools" "${ANDROID_HOME}/emulator"; do
  if [[ ":${PATH}:" != *":${tool_dir}:"* ]]; then
    export PATH="${tool_dir}:${PATH}"
  fi
done
unset tool_dir

# 3. Detect and set compatible Java environment if not set
if [[ -z "${JAVA_HOME:-}" ]]; then
  # Prefer Studio bundled JBR or JDK 21 LTS if present
  if [[ -d "$HOME/.local/share/android-studio/jbr" ]]; then
    export JAVA_HOME="$HOME/.local/share/android-studio/jbr"
  elif [[ -d "/usr/lib/jvm/java-21-openjdk" ]]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
  elif command -v java >/dev/null 2>&1; then
    java_bin_path="$(readlink -f "$(command -v java)")"
    candidate_home="$(dirname "$(dirname "${java_bin_path}")")"
    java_ver="$(java -version 2>&1 | head -n 1 || true)"
    major_ver=""
    if [[ "${java_ver}" =~ \"([0-9]+) ]]; then
      major_ver="${BASH_REMATCH[1]}"
    fi

    if [[ -n "${major_ver}" && "${major_ver}" != "21" ]]; then
      # Non-21 system default Java (e.g. Java 25)
      if [[ "${ANDROID_ENV_VERBOSE:-0}" == "1" || "$-" == *i* ]]; then
        echo "Notice: Host Java is version ${major_ver}. AGP requires JDK 21 LTS or Studio JBR." >&2
        echo "Install java-21-openjdk-devel or set JAVA_HOME to Studio JBR." >&2
      fi
    else
      export JAVA_HOME="${candidate_home}"
    fi
    unset java_bin_path candidate_home java_ver major_ver
  fi
fi

# 4. Status summary (printed only in interactive shells or when ANDROID_ENV_VERBOSE=1)
# Guards against breaking non-interactive scp/sftp/rsync sessions
if [[ "${ANDROID_ENV_VERBOSE:-0}" == "1" || "$-" == *i* ]]; then
  echo "=== Android Environment Initialized ==="
  echo "ANDROID_HOME: ${ANDROID_HOME}"
  echo "JAVA_HOME:    ${JAVA_HOME:-Not set (requires JDK 21 / Studio JBR)}"
  if command -v adb >/dev/null 2>&1; then
    echo "adb:          $(command -v adb)"
  else
    echo "adb:          Not found on PATH (SDK platform-tools not installed yet)"
  fi
  if command -v emulator >/dev/null 2>&1; then
    echo "emulator:     $(command -v emulator)"
  else
    echo "emulator:     Not found on PATH (SDK emulator not installed yet)"
  fi
  if command -v java >/dev/null 2>&1; then
    echo "java:         $(command -v java) ($(java -version 2>&1 | head -n 1))"
  fi
  echo "========================================"
fi
