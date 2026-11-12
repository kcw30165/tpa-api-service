#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/private-runtime-state.sh"
source "$ROOT/scripts/e2e/lib/authenticated-login-journey.sh"
fail=0
expect_eq(){ [[ "$1" == "$2" ]] || { printf 'FAIL %s expected=%s actual=%s\n' "$3" "$2" "$1"; fail=1; }; }
create_private_runtime_state
state_dir="$E2E_PRIVATE_STATE_DIR"
write_private_value portal_cookie 'portal-secret-value'
write_private_value authenticated_csrf 'csrf-secret-value'
write_private_value selected_account_ref 'account-secret-value'
publish_authenticated_http_state || { printf 'FAIL publish returned non-zero\n'; exit 1; }
expect_eq "${PORTAL_SESSION_COOKIE_VALUE:-}" portal-secret-value portal_cookie
expect_eq "${XSRF_TOKEN:-}" csrf-secret-value csrf
expect_eq "${ACCOUNT_REF:-}" account-secret-value account_ref
[[ -z "${AUTH_FLOW_COOKIE_VALUE:-}" ]] || { printf 'FAIL auth-flow cookie was retained\n'; fail=1; }
for file in "$state_dir"/*; do [[ "$(stat -c '%a' "$file")" == 600 ]] || { printf 'FAIL file mode %s\n' "$file"; fail=1; }; done
[[ "$(stat -c '%a' "$state_dir")" == 700 ]] || { printf 'FAIL directory mode\n'; fail=1; }
destroy_private_runtime_state
[[ ! -e "$state_dir" ]] || { printf 'FAIL state directory remains\n'; fail=1; }
[[ -z "${E2E_PRIVATE_STATE_DIR:-}" ]] || { printf 'FAIL state directory variable remains\n'; fail=1; }
unset PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF
create_private_runtime_state
write_private_value portal_cookie only-cookie
if publish_authenticated_http_state >"$E2E_PRIVATE_STATE_DIR/error.out" 2>&1; then printf 'FAIL incomplete state unexpectedly published\n'; fail=1; fi
[[ -z "${PORTAL_SESSION_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]] || { printf 'FAIL partial state leaked\n'; fail=1; }
destroy_private_runtime_state
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS authenticated state publication and cleanup\n'
