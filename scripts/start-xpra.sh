#!/usr/bin/env bash
# scripts/start-xpra.sh - Manage secure headless Xpra virtual display
# Complies with Phase 01 headless architecture: SSH-only, no public TCP bind, no mDNS broadcast.
set -euo pipefail

DISPLAY_NUM="${XPRA_DISPLAY:-:100}"
ACTION="${1:-start}"

check_xpra() {
  if ! command -v xpra >/dev/null 2>&1; then
    echo "Error: xpra binary not found. Install via: sudo dnf install xpra" >&2
    exit 1
  fi
}

is_running() {
  # Exact token match to avoid :100 matching :1000
  xpra list 2>/dev/null | grep -E "(^|[[:space:]/])${DISPLAY_NUM}([[:space:]]|$)" >/dev/null 2>&1
}

start_server() {
  check_xpra
  if is_running; then
    echo "Xpra server on ${DISPLAY_NUM} is already running."
    return 0
  fi

  echo "Starting Xpra virtual display on ${DISPLAY_NUM}..."
  # Security requirements:
  # 1. No --bind-tcp: sockets remain local and accessible only via SSH tunnel
  # 2. --html=off: disable unauthenticated web interface
  # 3. --mdns=no: prevent advertising session over local network (Avahi)
  # 4. Child xterm provides authenticated environment for Studio and emulator
  xpra start "${DISPLAY_NUM}" \
    --start-child=xterm \
    --exit-with-children=no \
    --html=off \
    --mdns=no

  # Verify daemon started
  local count=0
  while ! is_running; do
    sleep 0.5
    count=$((count + 1))
    if [ "$count" -ge 10 ]; then
      echo "Error: Timed out waiting for Xpra server on ${DISPLAY_NUM} to start." >&2
      exit 1
    fi
  done

  echo "Xpra started successfully on ${DISPLAY_NUM}."
  echo "Connect from remote workstation via:"
  echo "  xpra attach ssh://$(whoami)@<host-ip>/${DISPLAY_NUM#:}"
}

stop_server() {
  check_xpra
  if ! is_running; then
    echo "No Xpra server running on ${DISPLAY_NUM}."
    return 0
  fi
  echo "Stopping Xpra server on ${DISPLAY_NUM}..."
  xpra stop "${DISPLAY_NUM}"
  echo "Xpra server stopped."
}

show_status() {
  check_xpra
  echo "Active Xpra sessions:"
  xpra list || echo "No active Xpra sessions found."
}

show_help() {
  cat <<EOF
Usage: $0 [start|stop|status]

Commands:
  start   Start secure headless Xpra server on ${DISPLAY_NUM} (default)
  stop    Stop Xpra server on ${DISPLAY_NUM}
  status  Display running Xpra sessions

Environment Variables:
  XPRA_DISPLAY  Display number (default: :100)
EOF
}

case "${ACTION}" in
  start)
    start_server
    ;;
  stop)
    stop_server
    ;;
  status)
    show_status
    ;;
  --help|-h|help)
    show_help
    ;;
  *)
    echo "Unknown action: ${ACTION}" >&2
    show_help >&2
    exit 1
    ;;
esac
