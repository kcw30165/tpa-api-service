#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="$ROOT/scripts/e2e/investment-choice-scenarios.sh"

extra_block="$(awk '
  $0 == "SCENARIO=S9-extra" { capture = 1 }
  $0 == "SCENARIO=S9-blank" && capture { exit }
  capture { print }
' "$SCENARIO")"
blank_block="$(awk '
  $0 == "SCENARIO=S9-blank" { capture = 1 }
  capture && $0 ~ /^if \[\[ .*USE_PASSWORD.* == yes \]\]; then$/ { exit }
  capture { print }
' "$SCENARIO")"

[[ -n "$extra_block" && -n "$blank_block" ]] || {
  printf 'FAIL could not delimit S9-extra and S9-blank scenario blocks\n'
  exit 1
}

grep -Fq 'readonly ISSUE_PATH=/api/v1/password-validation' "$SCENARIO" || {
  printf 'FAIL password-validation issuance endpoint changed\n'
  exit 1
}

grep -Fq "MALFORMED_CANCEL='{\"referenceNo\":\"000000000\",\"extra\":\"member\"}'" \
  <<<"$extra_block" || {
  printf 'FAIL S9-extra cancel operationPayload changed\n'
  exit 1
}
grep -Fq "password_json CANCEL_INVESTMENT_CHOICE \"\$MALFORMED_CANCEL\"" \
  <<<"$extra_block" || {
  printf 'FAIL S9-extra purpose or operationPayload changed\n'
  exit 1
}
grep -Fq "request POST \"\$ISSUE_PATH\" yes none present" <<<"$extra_block" || {
  printf 'FAIL S9-extra no longer posts to the password-validation endpoint\n'
  exit 1
}
grep -Fq 'assert_result S9-extra 422 err.investment-choice.request.invalid' \
  <<<"$extra_block" || {
  printf 'FAIL S9-extra expected result is not 422 err.investment-choice.request.invalid\n'
  exit 1
}

grep -Fq "MALFORMED_CANCEL='{\"referenceNo\":\" \"}'" <<<"$blank_block" || {
  printf 'FAIL S9-blank cancel operationPayload changed\n'
  exit 1
}
grep -Fq "password_json CANCEL_INVESTMENT_CHOICE \"\$MALFORMED_CANCEL\"" \
  <<<"$blank_block" || {
  printf 'FAIL S9-blank purpose or operationPayload changed\n'
  exit 1
}
grep -Fq "request POST \"\$ISSUE_PATH\" yes none present" <<<"$blank_block" || {
  printf 'FAIL S9-blank no longer posts to the password-validation endpoint\n'
  exit 1
}
grep -Fq 'assert_result S9-blank 422 err.investment-choice.cancel.ref-required' \
  <<<"$blank_block" || {
  printf 'FAIL S9-blank expected result is not 422 err.investment-choice.cancel.ref-required\n'
  exit 1
}

printf 'PASS CIM S9 validation expectation contracts\n'
