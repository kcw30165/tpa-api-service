#!/usr/bin/env bash
run_scenario() {
  local rc=0 cookie csrf destination_id request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key invalid_code

  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED invalid-OTP journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3;
  }
  [[ "${E2E_ALLOW_OTP_VERIFICATION:-false}" == true ]] || {
    printf 'BLOCKED invalid-OTP verification requires E2E_ALLOW_OTP_VERIFICATION=true\n'; return 4;
  }
  [[ "${E2E_EXPECT_INVALID_OTP:-false}" == true ]] || {
    printf 'BLOCKED invalid-OTP intent requires E2E_EXPECT_INVALID_OTP=true\n'; return 6;
  }

  create_private_runtime_state
  login_journey_start || return 1
  cookie="$(read_private_value auth_flow_cookie)"
  csrf="$(read_private_value csrf_token)"
  destination_id="$(read_private_value destination_id)"

  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-send.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-send.json"
  request_id="${REQUEST_ID_PREFIX}-otp-send-$(date +%s)-$$"
  idempotency_key="login-invalid-otp-send-$(date +%s)-$$"
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
  [[ "$rc" -eq 0 ]] || return "$rc"

  if [[ "${E2E_OFFLINE_TEST_MODE:-false}" == true && -n "${E2E_TEST_INVALID_OTP_CODE_FILE:-}" ]]; then
    invalid_code="$(cat "$E2E_TEST_INVALID_OTP_CODE_FILE")"
  else
    local input_attempt=0 normalized_code
    received_code=""
    while [[ "$input_attempt" -lt 3 ]]; do
      input_attempt=$((input_attempt+1))
      read -r -s -p 'Enter the received Login OTP to derive a different test code: ' received_code </dev/tty 2>/dev/tty
      printf '\n' >/dev/tty
      normalized_code="$(printf '%s' "$received_code" | tr -d '\r' | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
      if [[ "$normalized_code" =~ ^[0-9]+$ ]]; then
        received_code="$normalized_code"
        break
      fi
      received_code=""
      if [[ "$input_attempt" -lt 3 ]]; then
        printf 'Input must contain digits only. Please try again; no OTP verification request was sent.\n' >/dev/tty
      fi
    done
    [[ -n "$received_code" ]] || { printf 'FAIL received OTP was not numeric after 3 local attempts\n'; return 1; }
    local last_digit replacement
    last_digit="${received_code: -1}"
    if [[ "$last_digit" == 9 ]]; then
      replacement=0
    else
      replacement=$((last_digit + 1))
    fi
    invalid_code="${received_code:0:${#received_code}-1}${replacement}"
    [[ "$invalid_code" != "$received_code" && "${#invalid_code}" -eq "${#received_code}" ]] || {
      received_code=''; invalid_code=''; printf 'FAIL invalid OTP derivation failed\n'; return 1;
    }
    printf 'Received OTP accepted securely: %s digits\n' "${#received_code}" >/dev/tty
    printf 'Submitting one guaranteed-different OTP verification attempt...\n' >/dev/tty
    received_code=''
  fi
  [[ -n "$invalid_code" ]] || { printf 'FAIL invalid OTP is required\n'; return 1; }

  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-invalid.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-invalid.json"
  request_id="${REQUEST_ID_PREFIX}-otp-invalid-$(date +%s)-$$"
  idempotency_key="login-invalid-otp-verify-$(date +%s)-$$"
  request_body="$(jq -cn --arg code "$invalid_code" '{code:$code}')"
  invalid_code=''
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/verify" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" \
      -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" \
      --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  request_body=''
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/015-invalid-otp-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/016-invalid-otp-response-body-redacted.json"
  assert_eq 0 "$curl_rc" invalid_otp_curl_exit || rc=1
  assert_eq 422 "${metrics%% *}" invalid_otp_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-invalid-otp-response-body-redacted.json" '.status' 'BUSINESS_REJECTED' invalid_otp_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-invalid-otp-response-body-redacted.json" '.result.nextStep' 'VERIFY_OTP' invalid_otp_next_step || rc=1
  jq -e '.result.data.remainingVerifyAttempts | numbers' "$E2E_RUN_EVIDENCE_DIR/016-invalid-otp-response-body-redacted.json" >/dev/null \
    || { printf 'FAIL remaining_verify_attempts\n'; rc=1; }
  if grep -Eqi '^set-cookie:[[:space:]]*portal_sid=' "$raw_headers"; then printf 'FAIL portal_cookie_absent\n'; rc=1; else printf 'PASS portal_cookie_absent\n'; fi
  jq -e '((.result.data.accounts? // []) | length) == 0' "$raw_body" >/dev/null \
    || { printf 'FAIL accounts_absent\n'; rc=1; }
  local response_status response_next_step response_account_count portal_issued
  response_status="$(jq -r '.status // "<absent>"' "$raw_body")"
  response_next_step="$(jq -r '.result.nextStep // "<absent>"' "$raw_body")"
  response_account_count="$(jq -r '(.result.data.accounts? // []) | length' "$raw_body")"
  if grep -Eqi '^set-cookie:[[:space:]]*portal_sid=[^;]+' "$raw_headers"; then portal_issued=yes; else portal_issued=no; fi
  printf 'otp_send_attempts=1\ninvalid_otp_verify_attempts=1\notp_resend_attempts=0\nportal_cookie_issued=%s\naccount_count=%s\nresponse_status=%s\nresponse_next_step=%s\n' \
    "$portal_issued" "$response_account_count" "$response_status" "$response_next_step" \
    >"$E2E_RUN_EVIDENCE_DIR/017-invalid-otp-summary.txt"
  return "$rc"
}
