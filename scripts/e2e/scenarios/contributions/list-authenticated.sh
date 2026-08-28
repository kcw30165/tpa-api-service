#!/usr/bin/env bash
run_scenario() {
  local rc=0 cookie csrf destination_id otp_code request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key portal_cookie authenticated_csrf account_count next_step
  local account_index account_ref contributions_headers contributions_body contributions_metrics contributions_rc
  local from_date to_date item_count page page_size total_records has_next

  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED integrated journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3;
  }
  [[ "${E2E_ALLOW_OTP_VERIFICATION:-false}" == true ]] || {
    printf 'BLOCKED OTP verification requires E2E_ALLOW_OTP_VERIFICATION=true\n'; return 4;
  }
  [[ "${E2E_ALLOW_ACCOUNT_SELECTION:-false}" != true ]] || {
    printf 'BLOCKED account selection is outside this scenario\n'; return 5;
  }
  [[ "${E2E_ALLOW_AUTHENTICATED_LOGOUT:-false}" == true ]] || {
    printf 'BLOCKED authenticated logout requires E2E_ALLOW_AUTHENTICATED_LOGOUT=true\n'; return 9;
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
  printf 'Login OTP: ' >/dev/tty
  IFS= read -r -s otp_code </dev/tty
  printf '
' >/dev/tty
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

  account_index="${CONTRIBUTIONS_ACCOUNT_INDEX:-0}"
  [[ "$account_index" =~ ^[0-9]+$ ]] || { printf 'FAIL contributions account index must be nonnegative\n'; return 1; }
  [[ "$account_index" -lt "$account_count" ]] || { printf 'FAIL contributions account index is outside returned accounts\n'; return 1; }
  account_ref="$(jq -er --argjson index "$account_index" '.result.data.accounts[$index].accountRef' "$raw_body")"
  [[ -n "$account_ref" ]] || { printf 'FAIL selected Account-Ref is missing\n'; return 1; }
  write_private_value contributions_account_ref "$account_ref"

  from_date="${CONTRIBUTIONS_FROM_DATE:-20/03/2026}"
  to_date="${CONTRIBUTIONS_TO_DATE:-29/06/2026}"
  contributions_headers="$E2E_PRIVATE_STATE_DIR/contributions.headers"
  contributions_body="$E2E_PRIVATE_STATE_DIR/contributions.json"
  request_id="${REQUEST_ID_PREFIX}-contributions-$(date +%s)-$$"
  if contributions_metrics="$(curl --silent --show-error --get \
      --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      "${BFF_BASE_URL%/}/api/v1/contributions" \
      --data-urlencode "fromDate=$from_date" --data-urlencode "toDate=$to_date" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Account-Ref: $account_ref" -H "X-XSRF-TOKEN: $authenticated_csrf" \
      --cookie "portal_sid=$portal_cookie" -D "$contributions_headers" -o "$contributions_body" \
      -w '%{http_code} %{time_total}')"; then contributions_rc=0; else contributions_rc=$?; fi
  redact_headers_file "$contributions_headers" "$E2E_RUN_EVIDENCE_DIR/020-contributions-response-headers-redacted.txt"
  redact_json_file "$contributions_body" "$E2E_RUN_EVIDENCE_DIR/021-contributions-response-body-redacted.json"
  assert_eq 0 "$contributions_rc" contributions_curl_exit || rc=1
  assert_eq 200 "${contributions_metrics%% *}" contributions_http_status || rc=1
  jq -e '.actions.export.enabled | type == "boolean"' "$contributions_body" >/dev/null || { printf 'FAIL contributions export action contract\n'; rc=1; }
  jq -e '.items | type == "array"' "$contributions_body" >/dev/null || { printf 'FAIL contributions items contract\n'; rc=1; }
  jq -e '.pagination | type == "object"' "$contributions_body" >/dev/null || { printf 'FAIL contributions pagination contract\n'; rc=1; }
  item_count="$(jq -r '.items | length' "$contributions_body")"
  page="$(jq -r '.pagination.page' "$contributions_body")"
  page_size="$(jq -r '.pagination.pageSize' "$contributions_body")"
  total_records="$(jq -r '.pagination.totalRecords' "$contributions_body")"
  has_next="$(jq -r '.pagination.hasNextPage' "$contributions_body")"
  {
    printf 'from_date=%s\nto_date=%s\n' "$from_date" "$to_date"
    printf 'selected_account_index=%s\naccount_ref_present=yes\n' "$account_index"
    printf 'portal_cookie_present=yes\nauthenticated_xsrf_present=yes\n'
    printf 'item_count=%s\npage=%s\npage_size=%s\ntotal_records=%s\nhas_next_page=%s\n' "$item_count" "$page" "$page_size" "$total_records" "$has_next"
    printf 'contributions_requests=1\nautomatic_retries=0\n'
  } >"$E2E_RUN_EVIDENCE_DIR/022-contributions-summary.txt"
  [[ "$rc" -eq 0 ]] || return "$rc"

  raw_headers="$E2E_PRIVATE_STATE_DIR/logout.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/logout.json"
  request_id="${REQUEST_ID_PREFIX}-logout-$(date +%s)-$$"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/logout" \
      -H "X-Request-Id: $request_id" -H "X-XSRF-TOKEN: $authenticated_csrf" \
      --cookie "portal_sid=$portal_cookie" -D "$raw_headers" -o "$raw_body" \
      -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/023-logout-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/024-logout-response-body-redacted.json"
  assert_eq 0 "$curl_rc" authenticated_logout_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" authenticated_logout_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/024-logout-response-body-redacted.json" '.status' 'SUCCESS' authenticated_logout_api_status || rc=1
  local portal_cookie_cleared logout_csrf_count
  portal_cookie_cleared=no
  if sed -n -E '/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=/p' "$raw_headers" \
      | grep -Eq '([Mm]ax-[Aa]ge=0|[Ee]xpires=Thu, 01 Jan 1970)'; then
    portal_cookie_cleared=yes
  else
    printf 'FAIL authenticated_logout_portal_cookie_cleared\n'; rc=1
  fi
  logout_csrf_count="$(jq '[paths(scalars) as $p | select(($p[-1] | tostring) == "csrfToken")] | length' "$raw_body")"
  [[ "$logout_csrf_count" -eq 0 ]] || { printf 'FAIL authenticated_logout_csrf_absent\n'; rc=1; }
  {
    printf 'login_start_attempts=1\n'
    printf 'otp_send_attempts=1\n'
    printf 'otp_verify_attempts=1\n'
    printf 'contributions_requests=1\n'
    printf 'logout_attempts=1\n'
    printf 'otp_resend_attempts=0\n'
    printf 'account_selection_attempts=0\n'
    printf 'repeated_logout_attempts=0\n'
    printf 'portal_cookie_issued_before_logout=yes\n'
    printf 'portal_cookie_cleared_after_logout=%s\n' "$portal_cookie_cleared"
    printf 'logout_response_csrf_tokens=%s\n' "$logout_csrf_count"
  } >"$E2E_RUN_EVIDENCE_DIR/025-authenticated-logout-summary.txt"
  return "$rc"
}
