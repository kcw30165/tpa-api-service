#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCENARIO="$ROOT/scripts/e2e/investment-choice-scenarios.sh"
LIBRARY="$ROOT/scripts/e2e/lib/cim-password.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

# shellcheck source=scripts/e2e/lib/cim-password.sh
source "$LIBRARY"

prompt_calls=0
prompt_value=''
read_secret_if_missing() {
  local variable="$1" prompt="$2"
  [[ "$variable" == CIM_PASSWORD && "$prompt" == 'Test member password: ' ]] || return 1
  prompt_calls=$((prompt_calls + 1))
  printf -v "$variable" '%s' "$prompt_value"
}

CIM_PASSWORD=explicit-password-sentinel
LOGIN_TEST_PASSWORD=login-password-sentinel
prompt_calls=0
acquire_cim_password >"$tmp/explicit.log" 2>&1
[[ "$CIM_PASSWORD" == explicit-password-sentinel && "$prompt_calls" -eq 0 ]] || {
  printf 'FAIL explicit CIM_PASSWORD must take precedence\n'
  exit 1
}

unset CIM_PASSWORD
LOGIN_TEST_PASSWORD=login-password-sentinel
prompt_calls=0
acquire_cim_password >"$tmp/login-password.log" 2>&1
[[ "$CIM_PASSWORD" == login-password-sentinel && "$prompt_calls" -eq 0 ]] || {
  printf 'FAIL LOGIN_TEST_PASSWORD must be reused when CIM_PASSWORD is absent\n'
  exit 1
}

unset CIM_PASSWORD LOGIN_TEST_PASSWORD
prompt_value='prompt-password-sentinel'
prompt_calls=0
acquire_cim_password >"$tmp/prompt.log" 2>&1
[[ "$CIM_PASSWORD" == prompt-password-sentinel && "$prompt_calls" -eq 1 ]] || {
  printf 'FAIL secure password prompt must remain the fallback\n'
  exit 1
}
grep -Fq "IFS= read -r -s -p \"\$prompt\" value </dev/tty" "$SCENARIO" || {
  printf 'FAIL password prompt is not silent and attached to /dev/tty\n'
  exit 1
}
grep -Fq 'acquire_cim_password' "$SCENARIO" || {
  printf 'FAIL CIM scenario does not use the password acquisition helper\n'
  exit 1
}

CIM_DRY_RUN=yes CIM_USE_PASSWORD=yes \
  CIM_PASSWORD=dry-run-cim-password-sentinel \
  LOGIN_TEST_PASSWORD=dry-run-login-password-sentinel \
  bash "$SCENARIO" >"$tmp/dry-run.log" 2>&1
cp "$tmp/dry-run.log" "$tmp/evidence.txt"
for secret in \
  explicit-password-sentinel \
  login-password-sentinel \
  prompt-password-sentinel \
  dry-run-cim-password-sentinel \
  dry-run-login-password-sentinel; do
  for file in "$tmp"/*.log "$tmp/evidence.txt"; do
    if grep -Fq -- "$secret" "$file"; then
      printf 'FAIL password appeared in output or evidence\n'
      exit 1
    fi
  done
done

printf 'PASS CIM password precedence, silent prompt fallback, and redaction\n'
