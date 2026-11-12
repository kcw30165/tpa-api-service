#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/private-root" "$tmp/evidence" "$tmp/unrelated"
chmod 755 "$tmp/unrelated"
printf 'unrelated-test-data\n' >"$tmp/unrelated/keep.txt"
chmod 644 "$tmp/unrelated/keep.txt"
export E2E_PRIVATE_STATE_DIR="$tmp/unrelated"
export E2E_PRIVATE_STATE_OWNED_DIR="$tmp/unrelated"
export TMPDIR="$tmp/private-root"
export E2E_REPOSITORY_ROOT="$ROOT"
export E2E_EVIDENCE_ROOT="$tmp/evidence"
export E2E_ALLOW_EXTERNAL_EFFECTS=true
export E2E_ALLOW_OTP_VERIFICATION=true
export E2E_OFFLINE_TEST_MODE=true
export E2E_TEST_OTP_CODE_FILE="$tmp/otp.txt"
export BFF_BASE_URL=http://fake-e2e.invalid
export LOGIN_TEST_USERNAME=login-user-sentinel
export LOGIN_TEST_PASSWORD=login-password-sentinel
export REQUEST_ID_PREFIX=private-state-test
printf '%s' otp-code-sentinel >"$E2E_TEST_OTP_CODE_FILE"

# shellcheck source=scripts/e2e/run-post-login.sh
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
[[ -z "${E2E_PRIVATE_STATE_OWNED_DIR:-}" ]] || {
  printf 'FAIL inherited private-state marker was retained\n'
  exit 1
}

fake_curl() {
  local headers_file='' body_file='' url='' endpoint
  while (($#)); do
    case "$1" in
      -D) headers_file="$2"; shift 2 ;;
      -o) body_file="$2"; shift 2 ;;
      http://*|https://*) url="$1"; shift ;;
      *) shift ;;
    esac
  done
  endpoint="${url#"$BFF_BASE_URL"}"
  printf '%s\n' "$endpoint" >>"$tmp/fake-network-calls.txt"
  case "$endpoint" in
    /api/v1/auth/login)
      printf 'Set-Cookie: auth_flow_sid=auth-flow-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"login-csrf-sentinel","destinations":[{"destinationId":"email-destination","channel":"EMAIL"}]}}}' >"$body_file"
      ;;
    /api/v1/auth/login/otp/send)
      : >"$headers_file"
      printf '%s' '{"status":"SUCCESS"}' >"$body_file"
      ;;
    /api/v1/auth/login/otp/verify)
      printf 'Set-Cookie: portal_sid=portal-cookie-sentinel; Path=/\r\n' >"$headers_file"
      printf '%s' '{"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"authenticated-csrf-sentinel","accessToken":"authentication-token-sentinel","accounts":[{"accountRef":"account-ref-sentinel"}]}}}' >"$body_file"
      ;;
    *)
      printf 'FAIL unexpected fake endpoint: %s\n' "$endpoint" >&2
      return 1
      ;;
  esac
  printf '200 0.001'
}
curl() { fake_curl "$@"; }
find() {
  if [[ "${E2E_TEST_FORCE_CLEANUP_FAILURE:-false}" == true ]]; then
    return 73
  fi
  command find "$@"
}

fail=0
assert_equal() {
  local actual="$1" expected="$2" message="$3"
  if [[ "$actual" != "$expected" ]]; then
    printf 'FAIL %s\n' "$message"
    fail=1
  fi
}
assert_not_equal() {
  local actual="$1" unexpected="$2" message="$3"
  if [[ "$actual" == "$unexpected" ]]; then
    printf 'FAIL %s\n' "$message"
    fail=1
  fi
}
assert_owned_directory() {
  local expected="$1"
  assert_equal "${E2E_PRIVATE_STATE_DIR:-}" "$expected" \
    'private directory path changed during the owned run'
  if ! private_runtime_state_is_owned; then
    printf 'FAIL private directory is not backed by a current-process ownership capability\n'
    fail=1
  fi
}
assert_private_modes() {
  local directory="$1" file mode
  mode="$(stat -c '%a' "$directory")"
  assert_equal "$mode" 700 'private runtime directory mode is not 0700'
  while IFS= read -r file; do
    mode="$(stat -c '%a' "$file")"
    assert_equal "$mode" 600 'private runtime file mode is not 0600'
  done < <(find "$directory" -type f -print)
}
assert_unrelated_directory_unchanged() {
  [[ -d "$tmp/unrelated" ]] || { printf 'FAIL unowned directory was deleted\n'; fail=1; return; }
  [[ -f "$tmp/unrelated/keep.txt" ]] || { printf 'FAIL unowned file was deleted\n'; fail=1; return; }
  assert_equal "$(cat "$tmp/unrelated/keep.txt")" unrelated-test-data \
    'unowned file contents changed'
  assert_equal "$(stat -c '%a' "$tmp/unrelated")" 755 'unowned directory permissions changed'
  assert_equal "$(stat -c '%a' "$tmp/unrelated/keep.txt")" 644 \
    'unowned file permissions changed'
}
assert_no_authentication_values_in_output_or_evidence() {
  local value
  for value in \
    login-user-sentinel \
    login-password-sentinel \
    otp-code-sentinel \
    auth-flow-cookie-sentinel \
    login-csrf-sentinel \
    portal-cookie-sentinel \
    authenticated-csrf-sentinel \
    account-ref-sentinel \
    authentication-token-sentinel; do
    if grep -R -F -q -- "$value" "$tmp/journey.out" "$E2E_RUN_EVIDENCE_DIR"; then
      printf 'FAIL authentication value appeared in output or evidence\n'
      fail=1
      return
    fi
  done
}

mkdir -m 700 "$tmp/forged"
printf 'forged-directory-data\n' >"$tmp/forged/keep.txt"
E2E_PRIVATE_STATE_DIR="$tmp/forged"
E2E_PRIVATE_STATE_OWNED_DIR="$tmp/forged"
destroy_private_runtime_state || true
[[ -d "$tmp/forged" && -f "$tmp/forged/keep.txt" ]] || {
  printf 'FAIL assigned ownership variables authorized cleanup of an unrelated directory\n'
  fail=1
}
assert_equal "$(cat "$tmp/forged/keep.txt" 2>/dev/null || true)" forged-directory-data \
  'forged ownership modified unrelated file contents'
unset E2E_PRIVATE_STATE_DIR E2E_PRIVATE_STATE_OWNED_DIR
ROOT="$ROOT" UNRELATED="$tmp/unrelated" CLEANUP_LOG="$tmp/inherited-fd-cleanup.out" \
  bash -c '
    exec 199<"$UNRELATED"
    E2E_PRIVATE_STATE_DIR="$UNRELATED"
    export E2E_PRIVATE_STATE_DIR
    source "$ROOT/scripts/e2e/lib/private-runtime-state.sh"
    if private_runtime_state_is_owned; then exit 1; fi
    if destroy_private_runtime_state >"$CLEANUP_LOG" 2>&1; then exit 1; fi
    [[ -e "/proc/$BASHPID/fd/199" ]]
  ' || {
  printf 'FAIL inherited directory descriptor was trusted or discarded\n'
  fail=1
}
assert_unrelated_directory_unchanged

mkdir -m 700 "$tmp/root-symlink-target"
printf 'root-symlink-data\n' >"$tmp/root-symlink-target/keep.txt"
ln -s "$tmp/root-symlink-target" "$tmp/root-symlink"
E2E_PRIVATE_STATE_DIR="$tmp/root-symlink"
if private_runtime_state_is_owned; then
  printf 'FAIL root symlink was accepted as owned state\n'
  fail=1
fi
destroy_private_runtime_state || true
[[ -L "$tmp/root-symlink" && -f "$tmp/root-symlink-target/keep.txt" ]] || {
  printf 'FAIL root symlink cleanup modified its external target\n'
  fail=1
}
unset E2E_PRIVATE_STATE_DIR

initialize_post_login_runtime
orchestrator_state_dir="$E2E_PRIVATE_STATE_DIR"
[[ -d "$orchestrator_state_dir" ]] || { printf 'FAIL orchestrator private state was not initialized\n'; fail=1; }
assert_not_equal "$orchestrator_state_dir" "$tmp/unrelated" \
  'orchestrator reused an inherited unowned directory'
assert_owned_directory "$orchestrator_state_dir"
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'orchestrator initialization did not create exactly one private directory'
assert_unrelated_directory_unchanged
E2E_PRIVATE_STATE_DIR="$tmp/unrelated"
if create_private_runtime_state >"$tmp/path-tamper.out" 2>&1; then
  printf 'FAIL changing the state path authorized a second private directory\n'
  fail=1
fi
E2E_PRIVATE_STATE_DIR="$orchestrator_state_dir"
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'path-tamper rejection created a second private directory'
assert_owned_directory "$orchestrator_state_dir"
assert_unrelated_directory_unchanged
ROOT="$ROOT" bash -c '
  source "$ROOT/scripts/e2e/lib/private-runtime-state.sh"
  [[ ! -e "/proc/$BASHPID/fd/199" ]] || exit 1
  destroy_private_runtime_state
' || {
  printf 'FAIL inherited orchestrator capability was trusted\n'
  fail=1
}
[[ -d "$orchestrator_state_dir" ]] || {
  printf 'FAIL child cleanup removed parent-owned state\n'
  fail=1
}
authenticated_login_journey >"$tmp/journey.out" 2>&1 || {
  printf 'FAIL fake-network authenticated login journey failed\n'
  cat "$tmp/journey.out" >&2
  fail=1
}
assert_equal "$E2E_PRIVATE_STATE_DIR" "$orchestrator_state_dir" \
  'authenticated login journey did not reuse the orchestrator private directory'
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'authenticated login journey created more than one private directory'
assert_equal "$(wc -l <"$tmp/fake-network-calls.txt")" 3 \
  'fake login did not make exactly the three expected network operations'
assert_private_modes "$orchestrator_state_dir"
assert_unrelated_directory_unchanged
assert_no_authentication_values_in_output_or_evidence
chmod 755 "$orchestrator_state_dir"
if private_runtime_state_is_owned; then
  printf 'FAIL unsafe private directory permissions were accepted\n'
  fail=1
fi
if create_private_runtime_state >"$tmp/unsafe-create.out" 2>&1; then
  printf 'FAIL unsafe owned directory was silently replaced\n'
  fail=1
fi
grep -q 'permissions are unsafe' "$tmp/unsafe-create.out" || {
  printf 'FAIL unsafe-mode rejection did not report the reason\n'
  fail=1
}
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'unsafe-mode rejection created a second private directory'
chmod 700 "$orchestrator_state_dir"
mkdir -m 700 "$tmp/external-target"
printf 'external-target-data\n' >"$tmp/external-target/keep.txt"
ln -s "$tmp/external-target/keep.txt" "$orchestrator_state_dir/nested-link"
E2E_TEST_FORCE_CLEANUP_FAILURE=true
if orchestrator_cleanup >"$tmp/cleanup-failure.out" 2>&1; then
  printf 'FAIL cleanup error was reported as success\n'
  fail=1
fi
unset E2E_TEST_FORCE_CLEANUP_FAILURE
grep -q 'ownership retained for retry' "$tmp/cleanup-failure.out" || {
  printf 'FAIL cleanup failure did not preserve retry diagnostics\n'
  fail=1
}
[[ -d "$orchestrator_state_dir" && -f "$orchestrator_state_dir/nested-link" ]] || {
  printf 'FAIL cleanup failure discarded state needed for diagnosis or retry\n'
  fail=1
}
assert_owned_directory "$orchestrator_state_dir"
[[ -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]] || {
  printf 'FAIL authentication values remained exported after failed cleanup\n'
  fail=1
}
orchestrator_cleanup || {
  printf 'FAIL cleanup retry did not succeed\n'
  fail=1
}
[[ ! -e "$orchestrator_state_dir" ]] || { printf 'FAIL orchestrator cleanup left private state behind\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 0 \
  'private directory remained after orchestrator cleanup'
[[ -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]] || {
  printf 'FAIL authenticated state remained exported after cleanup\n'
  fail=1
}
[[ -z "${E2E_PRIVATE_STATE_DIR:-}${E2E_PRIVATE_STATE_OWNED_DIR:-}" \
  && ! -e "/proc/$BASHPID/fd/199" ]] || {
  printf 'FAIL orchestrator ownership capability remained after cleanup\n'
  fail=1
}
[[ ! -e "$tmp/external-target/keep.txt" ]] && {
  printf 'FAIL nested symlink cleanup removed its external target\n'
  fail=1
}
assert_equal "$(cat "$tmp/external-target/keep.txt")" external-target-data \
  'nested symlink cleanup modified its external target'
assert_unrelated_directory_unchanged
destroy_private_runtime_state
assert_unrelated_directory_unchanged
if [[ ! -d "$tmp/unrelated" ]]; then
  mkdir -m 755 "$tmp/unrelated"
  printf 'unrelated-test-data\n' >"$tmp/unrelated/keep.txt"
  chmod 644 "$tmp/unrelated/keep.txt"
fi

E2E_SCENARIO_PACK=standalone-login
E2E_SCENARIO_ID=private-state-lifecycle
export E2E_SCENARIO_PACK E2E_SCENARIO_ID
create_evidence_dir
export E2E_PRIVATE_STATE_DIR="$tmp/unrelated"
export E2E_PRIVATE_STATE_OWNED_DIR="$tmp/unrelated"
authenticated_login_journey >"$tmp/journey.out" 2>&1 || {
  printf 'FAIL fake-network standalone login journey failed\n'
  cat "$tmp/journey.out" >&2
  fail=1
}
standalone_state_dir="$E2E_PRIVATE_STATE_DIR"
[[ -d "$standalone_state_dir" ]] || { printf 'FAIL standalone login journey did not create private state\n'; fail=1; }
assert_not_equal "$standalone_state_dir" "$tmp/unrelated" \
  'standalone login reused inherited unowned directory'
assert_owned_directory "$standalone_state_dir"
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 1 \
  'standalone login journey did not create exactly one private directory'
assert_private_modes "$standalone_state_dir"
assert_unrelated_directory_unchanged
assert_no_authentication_values_in_output_or_evidence
E2E_PRIVATE_STATE_DIR="$tmp/unrelated"
destroy_private_runtime_state
[[ ! -e "$standalone_state_dir" ]] || { printf 'FAIL standalone cleanup left private state behind\n'; fail=1; }
assert_equal "$(find "$TMPDIR" -mindepth 1 -maxdepth 1 -type d | wc -l)" 0 \
  'standalone private directory remained after cleanup'
assert_equal "$E2E_PRIVATE_STATE_DIR" "$tmp/unrelated" \
  'cleanup changed an unrelated state-path variable'
[[ -z "${E2E_PRIVATE_STATE_OWNED_DIR:-}" && ! -e "/proc/$BASHPID/fd/199" ]] || {
  printf 'FAIL standalone ownership capability remained after cleanup\n'
  fail=1
}
if private_runtime_state_has_ownership; then
  printf 'FAIL standalone cleanup retained ownership\n'
  fail=1
fi
assert_unrelated_directory_unchanged
destroy_private_runtime_state
assert_unrelated_directory_unchanged

[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS post-login private-state lifecycle and cleanup\n'
