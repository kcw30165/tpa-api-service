#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'TXT'
Usage: bash scripts/e2e/run-login-dev.sh <scenario>

Scenarios:
  cancel           Fresh Login start, then cancel. No OTP delivery.
  valid-otp        Send and verify one real OTP.
  invalid-otp      Send one OTP, derive and submit a different code.
  resend-cooldown  Send one OTP, then attempt one immediate resend.
  logout           Send and verify one OTP, then authenticated logout.
TXT
}

[[ $# -eq 1 ]] || { usage >&2; exit 2; }
scenario="$1"

case "$scenario" in
  cancel)
    scenario_id="start-and-cancel"
    effect_description="one Login start and one cancellation; no OTP delivery"
    guards=(E2E_ALLOW_LOGIN_CANCELLATION=true)
    ;;
  valid-otp)
    scenario_id="otp-send-and-verify"
    effect_description="one Login start, one OTP delivery, and one OTP verification"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_ACCOUNT_SELECTION=false)
    ;;
  invalid-otp)
    scenario_id="otp-send-and-invalid-verify"
    effect_description="one Login start, one OTP delivery, and one guaranteed-different OTP verification"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_EXPECT_INVALID_OTP=true)
    ;;
  resend-cooldown)
    scenario_id="otp-send-and-resend"
    effect_description="one Login start, one OTP delivery, and one immediate resend request"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_RESEND=true)
    ;;
  logout)
    scenario_id="otp-send-verify-and-logout"
    effect_description="one Login start, one OTP delivery, one OTP verification, and one authenticated logout"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true E2E_ALLOW_ACCOUNT_SELECTION=false)
    ;;
  -h|--help|help) usage; exit 0;;
  *) printf 'ERROR: Unknown Login scenario: %s\n\n' "$scenario" >&2; usage >&2; exit 2;;
esac

printf 'Login retest scenario: %s\n' "$scenario"
printf 'External-effect boundary: %s\n' "$effect_description"

if [[ -z "${LOGIN_TEST_USERNAME:-}" ]]; then
  read -r -p 'Login test username: ' LOGIN_TEST_USERNAME
fi
if [[ -z "${LOGIN_TEST_PASSWORD:-}" ]]; then
  read -r -s -p 'Login test password: ' LOGIN_TEST_PASSWORD
  printf '\n'
fi
[[ -n "$LOGIN_TEST_USERNAME" && -n "$LOGIN_TEST_PASSWORD" ]] || {
  printf 'ERROR: Login credentials are required.\n' >&2
  exit 2
}

LOGIN_OTP_DESTINATION_INDEX="${LOGIN_OTP_DESTINATION_INDEX:-}"
if [[ -z "$LOGIN_OTP_DESTINATION_INDEX" && "$scenario" != cancel ]]; then
  read -r -p 'OTP destination index [0]: ' LOGIN_OTP_DESTINATION_INDEX
  LOGIN_OTP_DESTINATION_INDEX="${LOGIN_OTP_DESTINATION_INDEX:-0}"
fi
LOGIN_OTP_DESTINATION_INDEX="${LOGIN_OTP_DESTINATION_INDEX:-0}"
[[ "$LOGIN_OTP_DESTINATION_INDEX" =~ ^[0-9]+$ ]] || {
  unset LOGIN_TEST_PASSWORD LOGIN_TEST_USERNAME
  printf 'ERROR: OTP destination index must be a non-negative integer.\n' >&2
  exit 2
}

export BFF_BASE_URL="${BFF_BASE_URL:-http://localhost:8888}"
export E2E_ENVIRONMENT="${E2E_ENVIRONMENT:-dev}"
export E2E_EVIDENCE_ROOT="${E2E_EVIDENCE_ROOT:-$ROOT/target/evidence}"
export E2E_BOOTSTRAP_MODE=anonymous
export E2E_SCENARIO_PACK=login
export E2E_SCENARIO_ID="$scenario_id"
export ACCEPT_LANGUAGE="${ACCEPT_LANGUAGE:-en}"
export REQUEST_ID_PREFIX="${REQUEST_ID_PREFIX:-login-retest-$scenario}"
export LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD LOGIN_OTP_DESTINATION_INDEX
for assignment in "${guards[@]}"; do export "$assignment"; done

set +e
bash scripts/e2e/run.sh
rc=$?
set -e
unset LOGIN_TEST_PASSWORD LOGIN_TEST_USERNAME
exit "$rc"
