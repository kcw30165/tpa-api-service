#!/usr/bin/env bash
run_scenario() {
  local rc=0 raw_headers raw_body metrics curl_rc request_id idempotency_key request_body cookie csrf destination_id
  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED OTP send requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'
    return 3
  }
  create_private_runtime_state
  login_journey_start || return 1
  cookie="$(read_private_value auth_flow_cookie)"
  csrf="$(read_private_value csrf_token)"
  destination_id="$(read_private_value destination_id)"
  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-send.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-send.json"
  request_id="${REQUEST_ID_PREFIX}-otp-send-$(date +%s)-$$"
  idempotency_key="login-otp-send-$(date +%s)-$$"
  request_body="$(jq -cn --arg destinationId "$destination_id" '{destinationId:$destinationId}')"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/send" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" \
      -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" \
      --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/012-otp-send-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_send_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" otp_send_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json" '.status' 'SUCCESS' otp_send_api_status || rc=1
  printf 'otp_send_attempts=1\nresend_attempts=0\n' >"$E2E_RUN_EVIDENCE_DIR/014-external-effect-summary.txt"
  return "$rc"
}
