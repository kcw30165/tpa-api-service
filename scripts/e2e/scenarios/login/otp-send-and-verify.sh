#!/usr/bin/env bash
run_scenario() {
  local rc=0 cookie csrf destination_id otp_code request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key portal_cookie authenticated_csrf account_count next_step

  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED integrated journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3;
  }
  [[ "${E2E_ALLOW_OTP_VERIFICATION:-false}" == true ]] || {
    printf 'BLOCKED OTP verification requires E2E_ALLOW_OTP_VERIFICATION=true\n'; return 4;
  }
  [[ "${E2E_ALLOW_ACCOUNT_SELECTION:-false}" != true ]] || {
    printf 'BLOCKED account selection is outside this scenario\n'; return 5;
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
  [[ "$rc" -eq 0 ]] || return "$rc"

  if [[ -n "${E2E_TEST_OTP_CODE_FILE:-}" ]]; then
    [[ "${E2E_OFFLINE_TEST_MODE:-false}" == true ]] || { printf 'FAIL OTP file input is restricted to offline tests\n'; return 1; }
    otp_code="$(cat "$E2E_TEST_OTP_CODE_FILE")"
  else
    read -r -s -p 'Login OTP: ' otp_code </dev/tty >/dev/tty
    printf '\n' >/dev/tty
  fi
  [[ -n "$otp_code" ]] || { printf 'FAIL OTP is required\n'; return 1; }

  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-verify.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/otp-verify.json"
  request_id="${REQUEST_ID_PREFIX}-otp-verify-$(date +%s)-$$"
  idempotency_key="login-otp-verify-$(date +%s)-$$"
  request_body="$(jq -cn --arg code "$otp_code" '{code:$code}')"
  otp_code=''
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/verify" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" \
      -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" \
      --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  request_body=''
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/015-otp-verify-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_verify_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" otp_verify_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.status' 'SUCCESS' otp_verify_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.result.nextStep' 'SELECT_ACCOUNT' otp_verify_next_step || rc=1
  [[ "$rc" -eq 0 ]] || return "$rc"

  portal_cookie="$(sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=([^;]+).*/\1/p' "$raw_headers" | tail -n 1)"
  authenticated_csrf="$(jq -er '.result.data.csrfToken' "$raw_body")"
  account_count="$(jq -r '.result.data.accounts | length' "$raw_body")"
  next_step="$(jq -r '.result.nextStep' "$raw_body")"
  [[ -n "$portal_cookie" && -n "$authenticated_csrf" && "$account_count" -gt 0 ]] || return 1
  write_private_value portal_cookie "$portal_cookie"
  write_private_value authenticated_csrf "$authenticated_csrf"
  printf 'next_step=%s\naccount_count=%s\nportal_cookie_issued=yes\naccount_selection_attempts=0\n' \
    "$next_step" "$account_count" >"$E2E_RUN_EVIDENCE_DIR/017-authenticated-transition-summary.txt"
  printf 'otp_send_attempts=1\notp_verify_attempts=1\notp_resend_attempts=0\naccount_selection_attempts=0\n' \
    >"$E2E_RUN_EVIDENCE_DIR/018-external-effect-summary.txt"
  return 0
}
