#!/usr/bin/env bash

set -Eeuo pipefail

SERVICE_PID=""

log() {
  printf "[%s] %s\n" "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

cleanup() {
  if [[ -n "${SERVICE_PID:-}" ]]; then
    if kill -0 "$SERVICE_PID" >/dev/null 2>&1; then
      log "Shutting down Spring Boot service (PID: $SERVICE_PID)"
      kill "$SERVICE_PID" || true
      # Wait briefly for graceful shutdown
      for _ in {1..50}; do
        if kill -0 "$SERVICE_PID" >/dev/null 2>&1; then
          sleep 0.2
        else
          break
        fi
      done
    fi
  fi
}

trap cleanup EXIT INT TERM

require_cmd() {
  local cmd="$1"; shift || true
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Error: Required command '$cmd' not found in PATH" >&2
    exit 127
  fi
}

wait_for_url() {
  local url="$1"
  local timeout="${2:-60}"
  local interval=1
  local elapsed=0
  while (( elapsed < timeout )); do
    if curl -sSf "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$interval"
    (( elapsed += interval ))
  done
  return 1
}

main() {
  # Pre-flight checks
  require_cmd oci
  require_cmd curl
  require_cmd k6
  if [[ ! -x "./mvnw" ]]; then
    echo "Error: ./mvnw is missing or not executable" >&2
    exit 1
  fi

  # 1) Ask whether to reuse existing OCI session token (default: reuse)
  local auth_choice="Y"
  if IFS= read -r -p "Reuse existing OCI session token? [Y/n]: " auth_choice_input; then
    auth_choice="${auth_choice_input:-Y}"
  fi
  if [[ "$auth_choice" =~ ^[Nn] ]]; then
    log "Authenticating OCI session (NEW token; profile: bmc_operator_access, region: us-ashburn-1)"
    oci session authenticate --region us-ashburn-1 --profile-name bmc_operator_access
  else
    log "Reusing existing OCI session token (skipping 'oci session authenticate')"
  fi

  # 2) Start Spring Boot service
  log "Starting Spring Boot service via Maven Wrapper"
  # Use quiet logs to keep output focused; full logs in service.log
  ./mvnw -q -pl rest-api spring-boot:run > e2e-service.log 2>&1 &
  SERVICE_PID=$!
  log "Service started (PID: $SERVICE_PID). Tailing logs at e2e-service.log"

  local base_url="http://localhost:7171"
  local health_url="$base_url/actuator/health"

  log "Waiting for service readiness at $health_url (timeout: 120s)"
  if ! wait_for_url "$health_url" 120; then
    log "Health endpoint unavailable; trying base URL $base_url (timeout: 20s)"
    if ! wait_for_url "$base_url" 20; then
      echo "Error: Service did not become ready in time. Check e2e-service.log for details." >&2
      exit 1
    fi
  fi
  log "Service is ready. Proceeding with k6 tests."

  # 3) Run k6 tests (will stop on first failure due to 'set -e')
  log "Running k6 test: e2e-tests/ask.test.js"
  k6 run -e BASE_URL="$base_url" e2e-tests/ask.test.js

  log "Running k6 test: e2e-tests/ask-stream.test.js"
  k6 run -e BASE_URL="$base_url" e2e-tests/ask-stream.test.js

  log "All k6 tests completed successfully."
}

main "$@"
