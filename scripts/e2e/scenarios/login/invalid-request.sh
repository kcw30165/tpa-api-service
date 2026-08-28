#!/usr/bin/env bash
run_scenario() {
  local rc=0 body="$E2E_RUN_EVIDENCE_DIR/007-response-body-redacted.json"
  IDEMPOTENCY_KEY="login-invalid-request-$(date +%s)-$$" E2E_SEND_REQUEST_ID=true \
    bff_request POST '/api/v1/auth/login' '{}'
  assert_eq 0 "$E2E_CURL_EXIT" curl_exit || rc=1
  assert_eq 400 "$E2E_HTTP_STATUS" http_status || rc=1
  assert_json_scalar_eq "$body" '.status' 'VALIDATION_FAILED' api_status || rc=1
  assert_file_not_matches "$body" 'password|otpCode|portal_sid|auth_flow_sid|csrfToken|Account-Ref' sensitive_data_absent || rc=1
  return "$rc"
}
