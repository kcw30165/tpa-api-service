#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; cd "$ROOT"
mode="${1:-provided-session}"
case "$mode" in
 provided-session) exec bash scripts/e2e/run-account-balance-local-stubbed.sh ;;
 authenticated-live)
  : "${BFF_BASE_URL:=https://ngtpa-api-service-dev.bcthk.info}"; : "${ACCEPT_LANGUAGE:=en}"; : "${AUTHENTICATED_ACCOUNT_INDEX:=0}"
  [[ -n "${LOGIN_TEST_USERNAME:-}" ]] || read -r -p 'Login test username: ' LOGIN_TEST_USERNAME </dev/tty
  [[ -n "${LOGIN_TEST_PASSWORD:-}" ]] || { read -r -s -p 'Login test password: ' LOGIN_TEST_PASSWORD </dev/tty; printf '\n' >/dev/tty; }
  printf 'Account Balance budget: 1 OTP delivery, 1 OTP prompt, 1 request, 1 logout, no retries.\n'; read -r -p 'Type RUN to authorize: ' a </dev/tty; [[ "$a" == RUN ]] || exit 3
  export LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD BFF_BASE_URL ACCEPT_LANGUAGE AUTHENTICATED_ACCOUNT_INDEX
  export E2E_SCENARIO_PACK=account-balance E2E_SCENARIO_ID=list-authenticated E2E_BOOTSTRAP_MODE=anonymous E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true
  bash scripts/e2e/run.sh; rc=$?; unset LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD; exit "$rc" ;;
 *) printf 'Usage: bash scripts/e2e/run-account-balance-dev.sh {provided-session|authenticated-live}\n' >&2; exit 2 ;;
esac
