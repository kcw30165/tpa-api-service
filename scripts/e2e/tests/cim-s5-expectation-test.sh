#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="$ROOT/scripts/e2e/investment-choice-scenarios.sh"
block="$(sed -n '/^SCENARIO=S5$/,/^SCENARIO=S6$/p' "$SCENARIO" | sed '$d')"

grep -Fq 'readonly ISSUE_PATH=/api/v1/password-validation' "$SCENARIO" || {
  printf 'FAIL password-validation issuance endpoint changed\n'
  exit 1
}
grep -Fq 'MALFORMED_SUBMIT='\''{"switchSources":["CIM-SOURCE"],"allocations":[{"fundCode":"CIM-FUND","percentage":99}],"unknown":"member"}'\''' <<<"$block" || {
  printf 'FAIL S5 malformed CIM operationPayload changed\n'
  exit 1
}
grep -Fq "password_json CHANGE_INVESTMENT_CHOICE \"\$MALFORMED_SUBMIT\"" <<<"$block" || {
  printf 'FAIL S5 password-validation purpose or operationPayload changed\n'
  exit 1
}
grep -Fq "request POST \"\$ISSUE_PATH\" yes none present" <<<"$block" || {
  printf 'FAIL S5 no longer posts to the password-validation endpoint\n'
  exit 1
}
grep -Fq 'assert_result S5 422 err.investment-choice.request.invalid' <<<"$block" || {
  printf 'FAIL S5 expected result is not 422 err.investment-choice.request.invalid\n'
  exit 1
}
if grep -Fq 'err.password-validation.request.invalid' <<<"$block"; then
  printf 'FAIL stale password-validation envelope code remains\n'
  exit 1
fi
printf 'PASS CIM S5 operation-payload validation expectation\n'
