#!/usr/bin/env bash
run_scenario() {
  local rc=0 cookie csrf metrics curl_rc raw_headers raw_body request_id
  local cleared_cookie csrf_count

  [[ "${E2E_ALLOW_LOGIN_CANCELLATION:-false}" == true ]] || {
    printf 'BLOCKED Login cancellation requires E2E_ALLOW_LOGIN_CANCELLATION=true\n'
    return 8
  }

  create_private_runtime_state
  login_journey_start || return 1
  cookie="$(read_private_value auth_flow_cookie)"
  csrf="$(read_private_value csrf_token)"

  raw_headers="$E2E_PRIVATE_STATE_DIR/login-cancel.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/login-cancel.json"
  request_id="${REQUEST_ID_PREFIX}-login-cancel-$(date +%s)-$$"
  if metrics="$(curl --silent --show-error \
      --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" \
      --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/cancel" \
      -H "X-Request-Id: $request_id" \
      -H "X-XSRF-TOKEN: $csrf" \
      --cookie "auth_flow_sid=$cookie" \
      -D "$raw_headers" -o "$raw_body" \
      -w '%{http_code} %{time_total}')"; then
    curl_rc=0
  else
    curl_rc=$?
  fi

  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/012-login-cancel-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/013-login-cancel-response-body-redacted.json"

  assert_eq 0 "$curl_rc" login_cancel_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" login_cancel_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/013-login-cancel-response-body-redacted.json" '.status' 'SUCCESS' login_cancel_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/013-login-cancel-response-body-redacted.json" '.result.nextStep' 'RETURN_TO_LOGIN' login_cancel_next_step || rc=1

  cleared_cookie=no
  if sed -n -E '/^[Ss]et-[Cc]ookie:[[:space:]]*auth_flow_sid=/p' "$raw_headers" \
      | grep -Eq '([Mm]ax-[Aa]ge=0|[Ee]xpires=Thu, 01 Jan 1970)'; then
    cleared_cookie=yes
  else
    printf 'FAIL login_cancel_auth_flow_cookie_cleared\n'
    rc=1
  fi

  csrf_count="$(jq '[paths(scalars) as $p | select(($p[-1] | tostring) == "csrfToken")] | length' "$raw_body")"
  [[ "$csrf_count" -eq 0 ]] || {
    printf 'FAIL login_cancel_csrf_absent\n'
    rc=1
  }

  {
    printf 'login_start_attempts=1\n'
    printf 'login_cancel_attempts=1\n'
    printf 'otp_send_attempts=0\n'
    printf 'otp_resend_attempts=0\n'
    printf 'otp_verify_attempts=0\n'
    printf 'auth_flow_cookie_cleared=%s\n' "$cleared_cookie"
    printf 'response_csrf_tokens=%s\n' "$csrf_count"
    printf 'next_step=RETURN_TO_LOGIN\n'
  } >"$E2E_RUN_EVIDENCE_DIR/014-login-cancel-summary.txt"
  return "$rc"
}
