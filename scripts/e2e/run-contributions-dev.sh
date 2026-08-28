#!/usr/bin/env bash
set -Eeuo pipefail
BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$BASE/../.."
[[ "${1:-}" == list ]] || { printf 'Usage: bash scripts/e2e/run-contributions-dev.sh list\n' >&2; exit 2; }
command -v jq >/dev/null || { printf 'ERROR: jq is required.\n' >&2; exit 2; }
: "${BFF_BASE_URL:=https://ngtpa-api-service-dev.bcthk.info}"
: "${ACCEPT_LANGUAGE:=en}"
: "${CONTRIBUTIONS_FROM_DATE:=20/03/2026}"
: "${CONTRIBUTIONS_TO_DATE:=29/06/2026}"
: "${CONTRIBUTIONS_ACCOUNT_INDEX:=0}"
if [[ -z "${LOGIN_TEST_USERNAME:-}" ]]; then read -r -p 'Login test username: ' LOGIN_TEST_USERNAME </dev/tty; fi
if [[ -z "${LOGIN_TEST_PASSWORD:-}" ]]; then read -r -s -p 'Login test password: ' LOGIN_TEST_PASSWORD </dev/tty; printf '\n' >/dev/tty; fi
export LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD BFF_BASE_URL ACCEPT_LANGUAGE CONTRIBUTIONS_FROM_DATE CONTRIBUTIONS_TO_DATE CONTRIBUTIONS_ACCOUNT_INDEX
export E2E_SCENARIO_PACK=contributions E2E_SCENARIO_ID=list-authenticated E2E_BOOTSTRAP_MODE=anonymous
export E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true
printf 'Contributions Gate D budget: 1 OTP delivery, 1 OTP prompt, 1 Contributions request, 1 logout, no automatic retries.\n'
read -r -p 'Type RUN to authorize this suite: ' authorization </dev/tty
[[ "$authorization" == RUN ]] || { printf 'Suite not authorized.\n' >&2; exit 3; }
bash scripts/e2e/run.sh
