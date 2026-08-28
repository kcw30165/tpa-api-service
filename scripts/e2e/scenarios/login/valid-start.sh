#!/usr/bin/env bash
run_scenario() {
  local rc=0 body="$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json" request_body
  [[ -n "${LOGIN_TEST_USERNAME:-}" && -n "${LOGIN_TEST_PASSWORD:-}" ]] || {
    printf 'FAIL runtime Login credentials are required\n'
    return 1
  }
  request_body="$(jq -cn --arg username "$LOGIN_TEST_USERNAME" --arg password "$LOGIN_TEST_PASSWORD" '{username:$username,password:$password}')"
  IDEMPOTENCY_KEY="login-valid-start-$(date +%s)-$$" E2E_SEND_REQUEST_ID=true \
    bff_request POST '/api/v1/auth/login' "$request_body"
  assert_eq 0 "$E2E_CURL_EXIT" curl_exit || rc=1
  assert_eq 200 "$E2E_HTTP_STATUS" http_status || rc=1
  assert_json_scalar_eq "$body" '.status' 'SUCCESS' api_status || rc=1
  assert_json_path_exists "$body" '.result' || rc=1
  assert_file_not_matches "$body" '"password"|"otpCode"' credential_fields_absent || rc=1
  return "$rc"
}
