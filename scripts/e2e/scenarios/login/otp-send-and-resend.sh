#!/usr/bin/env bash
run_scenario() {
  local rc=0 cookie csrf destination_id request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key retry_header retry_body

  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED resend journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3;
  }
  [[ "${E2E_ALLOW_OTP_RESEND:-false}" == true ]] || {
    printf 'BLOCKED OTP resend requires E2E_ALLOW_OTP_RESEND=true\n'; return 7;
  }

  create_private_runtime_state
  login_journey_start || return 1
  cookie="$(read_private_value auth_flow_cookie)"
  csrf="$(read_private_value csrf_token)"
  destination_id="$(read_private_value destination_id)"

  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-send.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-send.json"
  request_id="${REQUEST_ID_PREFIX}-otp-send-$(date +%s)-$$"
  idempotency_key="login-resend-initial-send-$(date +%s)-$$"
  request_body="$(jq -cn --arg destinationId "$destination_id" '{destinationId:$destinationId}')"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/send" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" \
      -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" \
      --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  request_body=''
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/012-otp-send-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_send_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" otp_send_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json" '.status' 'SUCCESS' otp_send_api_status || rc=1
  [[ "$rc" -eq 0 ]] || return "$rc"

  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-resend.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-resend.json"
  request_id="${REQUEST_ID_PREFIX}-otp-resend-$(date +%s)-$$"
  idempotency_key="login-otp-resend-$(date +%s)-$$"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/resend" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" \
      --cookie "auth_flow_sid=$cookie" -D "$raw_headers" -o "$raw_body" \
      -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/015-otp-resend-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/016-otp-resend-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_resend_curl_exit || rc=1
  assert_eq 429 "${metrics%% *}" otp_resend_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-resend-response-body-redacted.json" '.status' 'BUSINESS_REJECTED' otp_resend_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-resend-response-body-redacted.json" '.result.nextStep' 'VERIFY_OTP' otp_resend_next_step || rc=1
  retry_body="$(jq -r '.result.data.retryAfterSeconds // empty' "$raw_body")"
  retry_header="$(sed -n -E 's/^[Rr]etry-[Aa]fter:[[:space:]]*([0-9]+).*/\1/p' "$raw_headers" | tail -n 1)"
  [[ "$retry_body" =~ ^[1-9][0-9]*$ ]] || { printf 'FAIL resend_retry_after_body\n'; rc=1; }
  [[ "$retry_header" =~ ^[1-9][0-9]*$ ]] || { printf 'FAIL resend_retry_after_header\n'; rc=1; }
  [[ -n "$retry_body" && "$retry_body" == "$retry_header" ]] || { printf 'FAIL resend_retry_after_consistency\n'; rc=1; }
  {
    printf 'otp_send_attempts=1\n'
    printf 'otp_resend_attempts=1\n'
    printf 'otp_verify_attempts=0\n'
    printf 'resend_outcome=COOLDOWN_ACTIVE\n'
    printf 'retry_after_seconds=%s\n' "$retry_body"
    printf 'automatic_retry_attempts=0\n'
  } >"$E2E_RUN_EVIDENCE_DIR/017-otp-resend-summary.txt"
  return "$rc"
}
