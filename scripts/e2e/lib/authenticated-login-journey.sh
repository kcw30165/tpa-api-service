#!/usr/bin/env bash
authenticated_login_journey() {
  local rc=0 cookie csrf destination_id otp_code request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key portal_cookie authenticated_csrf account_count account_index account_ref
  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || { printf 'BLOCKED authenticated journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3; }
  [[ "${E2E_ALLOW_OTP_VERIFICATION:-false}" == true ]] || { printf 'BLOCKED OTP verification requires E2E_ALLOW_OTP_VERIFICATION=true\n'; return 4; }
  create_private_runtime_state
  login_journey_start || return 1
  cookie="$(read_private_value auth_flow_cookie)"; csrf="$(read_private_value csrf_token)"; destination_id="$(read_private_value destination_id)"
  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-send.headers"; raw_body="$E2E_PRIVATE_STATE_DIR/otp-send.json"
  request_id="${REQUEST_ID_PREFIX}-otp-send-$(date +%s)-$$"; idempotency_key="login-otp-send-$(date +%s)-$$"
  request_body="$(jq -cn --arg destinationId "$destination_id" '{destinationId:$destinationId}')"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/send" -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/012-otp-send-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_send_curl_exit || rc=1; assert_eq 200 "${metrics%% *}" otp_send_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/013-otp-send-response-body-redacted.json" '.status' SUCCESS otp_send_api_status || rc=1
  [[ "$rc" -eq 0 ]] || return "$rc"
  if [[ -n "${E2E_TEST_OTP_CODE_FILE:-}" ]]; then [[ "${E2E_OFFLINE_TEST_MODE:-false}" == true ]] || return 1; otp_code="$(cat "$E2E_TEST_OTP_CODE_FILE")"; else printf 'Login OTP: ' >/dev/tty; IFS= read -r -s otp_code </dev/tty; printf '\n' >/dev/tty; fi
  [[ -n "$otp_code" ]] || { printf 'FAIL OTP is required\n'; return 1; }
  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-verify.headers"; raw_body="$E2E_PRIVATE_STATE_DIR/otp-verify.json"
  request_id="${REQUEST_ID_PREFIX}-otp-verify-$(date +%s)-$$"; idempotency_key="login-otp-verify-$(date +%s)-$$"; request_body="$(jq -cn --arg code "$otp_code" '{code:$code}')"; otp_code=''
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/verify" -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  request_body=''; redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/015-otp-verify-response-headers-redacted.txt"; redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_verify_curl_exit || rc=1; assert_eq 200 "${metrics%% *}" otp_verify_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.status' SUCCESS otp_verify_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.result.nextStep' SELECT_ACCOUNT otp_verify_next_step || rc=1
  [[ "$rc" -eq 0 ]] || return "$rc"
  portal_cookie="$(sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=([^;]+).*/\1/p' "$raw_headers" | tail -n 1)"; authenticated_csrf="$(jq -er '.result.data.csrfToken' "$raw_body")"; account_count="$(jq -r '.result.data.accounts|length' "$raw_body")"
  account_index="${AUTHENTICATED_ACCOUNT_INDEX:-0}"; [[ "$account_index" =~ ^[0-9]+$ && "$account_index" -lt "$account_count" ]] || { printf 'FAIL authenticated account index is invalid\n'; return 1; }
  account_ref="$(jq -er --argjson index "$account_index" '.result.data.accounts[$index].accountRef' "$raw_body")"
  [[ -n "$portal_cookie" && -n "$authenticated_csrf" && -n "$account_ref" ]] || return 1
  write_private_value portal_cookie "$portal_cookie"; write_private_value authenticated_csrf "$authenticated_csrf"; write_private_value selected_account_ref "$account_ref"
  printf 'next_step=SELECT_ACCOUNT\naccount_count=%s\nselected_account_index=%s\nportal_cookie_issued=yes\n' "$account_count" "$account_index" >"$E2E_RUN_EVIDENCE_DIR/017-authenticated-transition-summary.txt"
  printf 'login_start_attempts=1\notp_send_attempts=1\notp_verify_attempts=1\nautomatic_retries=0\n' >"$E2E_RUN_EVIDENCE_DIR/018-external-effect-summary.txt"
}

authenticated_logout_journey() {
  local portal_cookie csrf headers body metrics rc=0 request_id curl_rc cleared=no
  portal_cookie="$(read_private_value portal_cookie)"; csrf="$(read_private_value authenticated_csrf)"; headers="$E2E_PRIVATE_STATE_DIR/logout.headers"; body="$E2E_PRIVATE_STATE_DIR/logout.json"; request_id="${REQUEST_ID_PREFIX}-logout-$(date +%s)-$$"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST "${BFF_BASE_URL%/}/api/v1/auth/logout" -H "X-Request-Id: $request_id" -H "X-XSRF-TOKEN: $csrf" --cookie "portal_sid=$portal_cookie" -D "$headers" -o "$body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$headers" "$E2E_RUN_EVIDENCE_DIR/030-logout-response-headers-redacted.txt"; redact_json_file "$body" "$E2E_RUN_EVIDENCE_DIR/031-logout-response-body-redacted.json"
  assert_eq 0 "$curl_rc" logout_curl_exit || rc=1; assert_eq 200 "${metrics%% *}" logout_http_status || rc=1; assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/031-logout-response-body-redacted.json" '.status' SUCCESS logout_api_status || rc=1
  sed -n -E '/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=/p' "$headers" | grep -Eq '([Mm]ax-[Aa]ge=0|[Ee]xpires=Thu, 01 Jan 1970)' && cleared=yes || rc=1
  printf 'logout_attempts=1\nportal_cookie_cleared=%s\n' "$cleared" >"$E2E_RUN_EVIDENCE_DIR/032-logout-summary.txt"; return "$rc"
}
