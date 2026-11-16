#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/post-login-orchestrator.sh"
fail=0; tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
expect_contains(){ grep -Fq "$2" "$1" || { printf 'FAIL missing %s\n' "$2"; fail=1; }; }
run_case(){
 local name="$1" login_rc="$2" logout_rc="$3" expected_rc="$4" authenticated="${5:-false}"; shift 5
 : >"$tmp/$name.events"
 orchestrator_login(){ printf 'login\n' >>"$tmp/$name.events"; POST_LOGIN_ACCOUNT_SELECTION_STATUS="${SELECTION_STATUS:-NOT_RUN}"; POST_LOGIN_AUTHENTICATED_SESSION_ESTABLISHED="$authenticated"; return "$login_rc"; }
 orchestrator_run_feature(){ printf 'feature:%s\n' "$1" >>"$tmp/$name.events"; case "$1" in first) return "${FIRST_RC:-0}";; second) return "${SECOND_RC:-0}";; *) return 0;; esac; }
 orchestrator_logout(){ printf 'logout\n' >>"$tmp/$name.events"; return "$logout_rc"; }
 orchestrator_cleanup(){ printf 'cleanup\n' >>"$tmp/$name.events"; return "${CLEANUP_RC:-0}"; }
 set +e; run_post_login_orchestrator "$@" >"$tmp/$name.out" 2>&1; actual=$?; set -e
 [[ "$actual" -eq "$expected_rc" ]] || { printf 'FAIL %s rc=%s\n' "$name" "$actual"; fail=1; }
}
FIRST_RC=1 SECOND_RC=0 run_case continue 0 0 1 false first second
[[ "$(cat "$tmp/continue.events")" == $'login\nfeature:first\nfeature:second\nlogout\ncleanup' ]] || { printf 'FAIL continuation order\n'; fail=1; }
expect_contains "$tmp/continue.out" 'LOGIN: PASS'; expect_contains "$tmp/continue.out" 'ACCOUNT_SELECTION: NOT_RUN'; expect_contains "$tmp/continue.out" 'FEATURE first: FAIL'; expect_contains "$tmp/continue.out" 'FEATURE second: PASS'; expect_contains "$tmp/continue.out" 'LOGOUT: PASS'; expect_contains "$tmp/continue.out" 'OVERALL: FAIL'
FIRST_RC=0 SECOND_RC=0 run_case success 0 0 0 false first second
expect_contains "$tmp/success.out" 'OVERALL: PASS'
FIRST_RC=0 SECOND_RC=0 run_case logout 0 1 1 false first
expect_contains "$tmp/logout.out" 'LOGOUT: FAIL'; expect_contains "$tmp/logout.out" 'OVERALL: FAIL'
CLEANUP_RC=1 run_case cleanup-failure 0 0 1 false first
expect_contains "$tmp/cleanup-failure.out" 'OVERALL: FAIL'
FIRST_RC=0 SECOND_RC=0 run_case login 1 0 1 false first second
[[ "$(cat "$tmp/login.events")" == $'login\ncleanup' ]] || { printf 'FAIL login short-circuit\n'; fail=1; }
expect_contains "$tmp/login.out" 'LOGIN: FAIL'; expect_contains "$tmp/login.out" 'ACCOUNT_SELECTION: NOT_RUN'; expect_contains "$tmp/login.out" 'FEATURE first: NOT_RUN'; expect_contains "$tmp/login.out" 'FEATURE second: NOT_RUN'; expect_contains "$tmp/login.out" 'LOGOUT: NOT_RUN'; expect_contains "$tmp/login.out" 'OVERALL: FAIL'
SELECTION_STATUS=FAIL FIRST_RC=0 SECOND_RC=0 run_case selection-failure 1 0 1 true first second
[[ "$(cat "$tmp/selection-failure.events")" == $'login\nlogout\ncleanup' ]] || { printf 'FAIL failed account selection lifecycle order\n'; fail=1; }
expect_contains "$tmp/selection-failure.out" 'ACCOUNT_SELECTION: FAIL'; expect_contains "$tmp/selection-failure.out" 'FEATURE first: NOT_RUN'; expect_contains "$tmp/selection-failure.out" 'FEATURE second: NOT_RUN'; expect_contains "$tmp/selection-failure.out" 'LOGOUT: PASS'; expect_contains "$tmp/selection-failure.out" 'OVERALL: FAIL'
SELECTION_STATUS=FAIL FIRST_RC=0 SECOND_RC=0 run_case selection-logout-failure 1 1 1 true first
[[ "$(cat "$tmp/selection-logout-failure.events")" == $'login\nlogout\ncleanup' ]] || { printf 'FAIL failed account selection logout was not attempted once\n'; fail=1; }
expect_contains "$tmp/selection-logout-failure.out" 'ACCOUNT_SELECTION: FAIL'; expect_contains "$tmp/selection-logout-failure.out" 'LOGOUT: FAIL'; expect_contains "$tmp/selection-logout-failure.out" 'OVERALL: FAIL'
secret='do-not-print-secret'; FIRST_RC=0 SECOND_RC=0 run_case secret 0 0 0 false first
if grep -Fq "$secret" "$tmp/secret.out"; then printf 'FAIL secret in summary\n'; fail=1; fi
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS post-login orchestrator lifecycle aggregation\n'
