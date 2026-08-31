#!/usr/bin/env bash
set -euo pipefail
BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; cd "$BASE/../.."
source "$BASE/lib/runtime.sh"; source "$BASE/lib/preflight.sh"; source "$BASE/lib/redaction.sh"; source "$BASE/lib/evidence.sh"; source "$BASE/lib/assertions.sh"; source "$BASE/lib/http-client.sh"
source "$BASE/lib/private-runtime-state.sh"
source "$BASE/lib/authenticated-login-journey.sh"
: "${E2E_SCENARIO_PACK:=platform}"; : "${E2E_SCENARIO_ID:=controlled-assertion-self-test}"
case "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" in platform/readiness|platform/controlled-assertion-self-test|login/missing-request-id|login/missing-idempotency-key|login/invalid-request|login/valid-start|login/otp-send|login/otp-send-and-verify|login/otp-send-and-invalid-verify|login/otp-send-and-resend|login/start-and-cancel|login/otp-send-verify-and-logout|contributions/list-authenticated|account-balance/list-authenticated) ;; *) printf 'ERROR unknown or pending scenario\n' >&2; exit 2;; esac
[[ "$E2E_SCENARIO_ID" == controlled-assertion-self-test ]] || preflight
create_evidence_dir
printf 'environment=%s\npack=%s\nscenario=%s\nbootstrap=%s\n' "$E2E_ENVIRONMENT" "$E2E_SCENARIO_PACK" "$E2E_SCENARIO_ID" "$E2E_BOOTSTRAP_MODE" >"$E2E_RUN_EVIDENCE_DIR/001-preflight.txt"
printf 'base_url=%s\naccept_language=%s\nportal_cookie=%s\nauth_flow_cookie=%s\nxsrf=%s\naccount_ref=%s\n' "${BFF_BASE_URL:-<self-test>}" "$ACCEPT_LANGUAGE" "$(redacted_presence "${PORTAL_SESSION_COOKIE_VALUE:-}")" "$(redacted_presence "${AUTH_FLOW_COOKIE_VALUE:-}")" "$(redacted_presence "${XSRF_TOKEN:-}")" "$(redacted_presence "${ACCOUNT_REF:-}")" >"$E2E_RUN_EVIDENCE_DIR/002-runtime-input-manifest-redacted.txt"
source "$BASE/bootstrap/${E2E_BOOTSTRAP_MODE}.sh"; bootstrap_context
printf 'accept_language=%s\nrequest_id_prefix=%s\naccount_ref=%s\n' "$ACCEPT_LANGUAGE" "$REQUEST_ID_PREFIX" "$(redacted_presence "${ACCOUNT_REF:-}")" >"$E2E_RUN_EVIDENCE_DIR/004-request-context-summary-redacted.txt"
[[ "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/otp-send" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/otp-send-and-verify" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/otp-send-and-invalid-verify" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/otp-send-and-resend" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/start-and-cancel" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "login/otp-send-verify-and-logout" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "contributions/list-authenticated" || "$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID" == "account-balance/list-authenticated" ]] && source "$BASE/scenarios/login/login-journey-start.sh"
source "$BASE/scenarios/$E2E_SCENARIO_PACK/$E2E_SCENARIO_ID.sh"
set +e; run_scenario >"$E2E_RUN_EVIDENCE_DIR/008-assertions.txt" 2>&1; rc=$?; set -e
printf 'scenario_exit=%s\ncurl_exit=%s\nhttp_status=%s\nelapsed_seconds=%s\n' "$rc" "${E2E_CURL_EXIT:-NOT_RUN}" "${E2E_HTTP_STATUS:-NOT_RUN}" "${E2E_ELAPSED_SECONDS:-NOT_RUN}" >"$E2E_RUN_EVIDENCE_DIR/009-exit-codes.txt"
if [[ "$rc" -eq 0 ]]; then status=PASS; else status=FAIL; fi
printf 'Status: %s\nPack: %s\nScenario: %s\nEnvironment: %s\nEvidence: %s\n' "$status" "$E2E_SCENARIO_PACK" "$E2E_SCENARIO_ID" "$E2E_ENVIRONMENT" "$E2E_RUN_EVIDENCE_DIR" >"$E2E_RUN_EVIDENCE_DIR/010-final-status.txt"
printf 'E2E_%s %s\nEvidence: %s\n' "$E2E_SCENARIO_ID" "$status" "$E2E_RUN_EVIDENCE_DIR"
exit "$rc"
