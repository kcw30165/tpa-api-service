#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
umask 077

cat >"$tmp/feature.sh" <<'FEATURE'
{
  printf 'BASE_URL=%s\n' "$BASE_URL"
  printf 'SESSION_COOKIE=%s\n' "$SESSION_COOKIE"
  printf 'CSRF_TOKEN=%s\n' "$CSRF_TOKEN"
  printf 'ACCOUNT_REF=%s\n' "$ACCOUNT_REF"
  printf 'VESTING_SCALE_EXPECT_ABSENT=%s\n' "${VESTING_SCALE_EXPECT_ABSENT:-}"
  printf 'CIM_PASSWORD=%s\n' "${CIM_PASSWORD:-}"
  for name in LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD AUTH_FLOW_COOKIE_VALUE \
    PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN; do
    [[ -v "$name" ]] && printf '%s=present\n' "$name" || printf '%s=absent\n' "$name"
  done
  for name in SESSION_COOKIE CSRF_TOKEN ACCOUNT_REF CIM_PASSWORD; do
    declaration="$(declare -p "$name" 2>/dev/null || true)"
    if [[ ! -v "$name" ]]; then
      printf '%s=absent\n' "$name"
    elif [[ "$declaration" == declare\ -x* ]]; then
      printf '%s=exported\n' "$name"
    else
      printf '%s=shell-only\n' "$name"
    fi
  done
} >"$RUNNER_CAPTURE"
cat "/proc/$BASHPID/cmdline" >"$RUNNER_CMDLINE"
env -0 >"$RUNNER_ENV"
FEATURE

export RUNNER_CAPTURE="$tmp/capture"
export RUNNER_CMDLINE="$tmp/cmdline"
export RUNNER_ENV="$tmp/environment"
export BFF_BASE_URL=http://mock
export PORTAL_SESSION_COOKIE_VALUE=cookie-secret
export AUTH_FLOW_COOKIE_VALUE=auth-flow-secret
export XSRF_TOKEN=csrf-secret
export ACCOUNT_REF=account-secret
export LOGIN_TEST_USERNAME=login-user-secret
export LOGIN_TEST_PASSWORD=login-password-secret
export CIM_PASSWORD=explicit-cim-password
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only

feature_pack_path() {
  [[ "$1" == investment-choice || "$1" == vesting-scale ]] || return 1
  printf '%s\n' "$tmp/feature.sh"
}

contains_secret() {
  local file="$1" secret="$2" item index=0
  while IFS= read -r -d '' item; do
    index=$((index + 1))
    if [[ "$item" == *"$secret"* ]]; then
      CONTAINS_SECRET_FILE="$file"
      CONTAINS_SECRET_INDEX="$index"
      CONTAINS_SECRET_KEY="${item%%=*}"
      return 0
    fi
  done <"$file"
  return 1
}

contains_environment_key() {
  local file="$1" key="$2" item
  while IFS= read -r -d '' item; do
    [[ "$item" == "$key="* ]] && return 0
  done <"$file"
  return 1
}

contains_environment_entry() {
  local file="$1" expected="$2" item
  while IFS= read -r -d '' item; do
    [[ "$item" == "$expected" ]] && return 0
  done <"$file"
  return 1
}

orchestrator_run_feature investment-choice || {
  printf 'FAIL feature pack did not run with the authenticated contract\n'
  exit 1
}
[[ "$(cat "$RUNNER_CAPTURE")" == $'BASE_URL=http://mock\nSESSION_COOKIE=cookie-secret\nCSRF_TOKEN=csrf-secret\nACCOUNT_REF=account-secret\nVESTING_SCALE_EXPECT_ABSENT=\nCIM_PASSWORD=\nLOGIN_TEST_USERNAME=absent\nLOGIN_TEST_PASSWORD=absent\nAUTH_FLOW_COOKIE_VALUE=absent\nPORTAL_SESSION_COOKIE_VALUE=absent\nXSRF_TOKEN=absent\nSESSION_COOKIE=exported\nCSRF_TOKEN=exported\nACCOUNT_REF=exported\nCIM_PASSWORD=absent' ]] || {
  printf 'FAIL feature variables or secret export state changed\n'
  exit 1
}
for secret in cookie-secret csrf-secret account-secret auth-flow-secret \
  login-user-secret login-password-secret explicit-cim-password; do
  if contains_secret "$RUNNER_CMDLINE" "$secret"; then
    printf 'FAIL authentication secret appeared in feature process %s entry %s key=%s\n' \
      "$CONTAINS_SECRET_FILE" "$CONTAINS_SECRET_INDEX" "$CONTAINS_SECRET_KEY"
    exit 1
  fi
done
for secret in auth-flow-secret login-user-secret login-password-secret explicit-cim-password; do
  if contains_secret "$RUNNER_ENV" "$secret"; then
    printf 'FAIL unnecessary authentication secret appeared in feature environment key=%s\n' \
      "$CONTAINS_SECRET_KEY"
    exit 1
  fi
done
for name in LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD AUTH_FLOW_COOKIE_VALUE \
  PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN CIM_PASSWORD; do
  if contains_environment_key "$RUNNER_ENV" "$name"; then
    printf 'FAIL authentication variable was inherited by the feature environment\n'
    exit 1
  fi
done
for expected in SESSION_COOKIE=cookie-secret CSRF_TOKEN=csrf-secret ACCOUNT_REF=account-secret; do
  if ! contains_environment_entry "$RUNNER_ENV" "$expected"; then
    printf 'FAIL authenticated feature environment is missing a required variable\n'
    exit 1
  fi
done

CIM_PASSWORD=''
export CIM_USE_PASSWORD=yes
orchestrator_run_feature investment-choice || {
  printf 'FAIL password-reuse feature pack did not run\n'
  exit 1
}
[[ "$(cat "$RUNNER_CAPTURE")" == $'BASE_URL=http://mock\nSESSION_COOKIE=cookie-secret\nCSRF_TOKEN=csrf-secret\nACCOUNT_REF=account-secret\nVESTING_SCALE_EXPECT_ABSENT=\nCIM_PASSWORD=login-password-secret\nLOGIN_TEST_USERNAME=absent\nLOGIN_TEST_PASSWORD=absent\nAUTH_FLOW_COOKIE_VALUE=absent\nPORTAL_SESSION_COOKIE_VALUE=absent\nXSRF_TOKEN=absent\nSESSION_COOKIE=exported\nCSRF_TOKEN=exported\nACCOUNT_REF=exported\nCIM_PASSWORD=exported' ]] || {
  printf 'FAIL login-password reuse did not preserve the feature contract\n'
  exit 1
}
for secret in cookie-secret csrf-secret account-secret auth-flow-secret \
  login-user-secret login-password-secret explicit-cim-password; do
  if contains_secret "$RUNNER_CMDLINE" "$secret"; then
    printf 'FAIL authentication secret appeared in password-mode feature process %s entry %s key=%s\n' \
      "$CONTAINS_SECRET_FILE" "$CONTAINS_SECRET_INDEX" "$CONTAINS_SECRET_KEY"
    exit 1
  fi
done
for secret in auth-flow-secret login-user-secret explicit-cim-password; do
  if contains_secret "$RUNNER_ENV" "$secret"; then
    printf 'FAIL unnecessary authentication secret appeared in password-mode feature environment key=%s\n' \
      "$CONTAINS_SECRET_KEY"
    exit 1
  fi
done
for name in LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD AUTH_FLOW_COOKIE_VALUE \
  PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN; do
  if contains_environment_key "$RUNNER_ENV" "$name"; then
    printf 'FAIL authentication variable was inherited by password-mode feature environment\n'
    exit 1
  fi
done
if ! contains_environment_entry "$RUNNER_ENV" 'SESSION_COOKIE=cookie-secret' \
  || ! contains_environment_entry "$RUNNER_ENV" 'CSRF_TOKEN=csrf-secret' \
  || ! contains_environment_entry "$RUNNER_ENV" 'ACCOUNT_REF=account-secret' \
  || ! contains_environment_entry "$RUNNER_ENV" 'CIM_PASSWORD=login-password-secret'; then
  printf 'FAIL password-mode feature environment did not contain only required secrets\n'
  exit 1
fi

unset CIM_USE_PASSWORD
export E2E_ACCOUNT_INDEX=1
export VESTING_SCALE_EXPECT_ABSENT='planService'
orchestrator_run_feature vesting-scale || {
  printf 'FAIL vesting-scale pack did not run with the authenticated contract\n'
  exit 1
}
[[ "$(cat "$RUNNER_CAPTURE")" == $'BASE_URL=http://mock\nSESSION_COOKIE=cookie-secret\nCSRF_TOKEN=csrf-secret\nACCOUNT_REF=account-secret\nVESTING_SCALE_EXPECT_ABSENT=planService\nCIM_PASSWORD=\nLOGIN_TEST_USERNAME=absent\nLOGIN_TEST_PASSWORD=absent\nAUTH_FLOW_COOKIE_VALUE=absent\nPORTAL_SESSION_COOKIE_VALUE=absent\nXSRF_TOKEN=absent\nSESSION_COOKIE=exported\nCSRF_TOKEN=exported\nACCOUNT_REF=exported\nCIM_PASSWORD=absent' ]] || {
  printf 'FAIL vesting-scale child received an unexpected input contract\n'
  exit 1
}
if contains_environment_key "$RUNNER_ENV" E2E_ACCOUNT_INDEX; then
  printf 'FAIL shared account index was inherited by the vesting-scale child\n'
  exit 1
fi
for secret in cookie-secret csrf-secret account-secret auth-flow-secret \
  login-user-secret login-password-secret explicit-cim-password; do
  if contains_secret "$RUNNER_CMDLINE" "$secret"; then
    printf 'FAIL authentication secret escaped the vesting-scale contract\n'
    exit 1
  fi
done
for secret in auth-flow-secret login-user-secret login-password-secret explicit-cim-password; do
  if contains_secret "$RUNNER_ENV" "$secret"; then
    printf 'FAIL unnecessary authentication secret reached the vesting-scale child\n'
    exit 1
  fi
done
for name in LOGIN_TEST_USERNAME LOGIN_TEST_PASSWORD AUTH_FLOW_COOKIE_VALUE \
  PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN CIM_PASSWORD; do
  if contains_environment_key "$RUNNER_ENV" "$name"; then
    printf 'FAIL unexpected authentication variable reached the vesting-scale child\n'
    exit 1
  fi
done
unset E2E_ACCOUNT_INDEX VESTING_SCALE_EXPECT_ABSENT

unset PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF
if orchestrator_run_feature investment-choice >/dev/null 2>&1; then
  printf 'FAIL feature ran without authenticated account context\n'
  exit 1
fi
printf 'PASS post-login runner adapter\n'
