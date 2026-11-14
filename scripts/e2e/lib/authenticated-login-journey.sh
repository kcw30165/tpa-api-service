#!/usr/bin/env bash
select_first_email_destination_id() {
  local response_file="$1" destination_id
  destination_id="$(jq -er '[.result.data.destinations[]? | select((.channel // .type // .destinationType // "") | ascii_downcase == "email")][0].destinationId // empty' "$response_file")" || true
  [[ -n "$destination_id" ]] || { printf 'FAIL no email OTP destination is available\n' >&2; return 1; }
  printf '%s\n' "$destination_id"
}

select_account_ref() {
  local response_file="$1" selector_provided="$2" selector="$3"
  local match_indices match_count selected_index

  SELECTED_ACCOUNT_REF=''
  SELECTED_ACCOUNT_INDEX=''
  SELECTED_ACCOUNT_COUNT=unknown

  if ! jq -e '(.result.data.accounts | type) == "array"' "$response_file" \
    >/dev/null 2>&1; then
    printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
    return 1
  fi
  SELECTED_ACCOUNT_COUNT="$(jq -r '.result.data.accounts | length' "$response_file" 2>/dev/null)" \
    || SELECTED_ACCOUNT_COUNT=unknown
  if ! jq -e '
    all(.result.data.accounts[];
      type == "object"
      and (.accountRef | type) == "string"
      and (.accountRef | length) > 0)
  ' "$response_file" >/dev/null 2>&1; then
    printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
    return 1
  fi

  if [[ "$selector_provided" != true ]]; then
    selected_index=0
    SELECTED_ACCOUNT_REF="$(jq -er \
      '.result.data.accounts[0].accountRef | select(type == "string" and length > 0)' \
      "$response_file" 2>/dev/null)" || {
      printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
      return 1
    }
  else
    match_indices="$(jq -c --arg selector "$selector" '
      [.result.data.accounts
        | to_entries[]
        | select(.value.accountRef == $selector)
        | .key]
    ' "$response_file" 2>/dev/null)" || {
      printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
      return 1
    }
    match_count="$(jq -r 'length' <<<"$match_indices" 2>/dev/null)" || match_count=0
    [[ "$match_count" == 1 ]] || {
      printf 'FAIL account selection failed: explicit selector did not match exactly one login response account\n' >&2
      return 1
    }
    selected_index="$(jq -r '.[0]' <<<"$match_indices")" || {
      printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
      return 1
    }
    SELECTED_ACCOUNT_REF="$(jq -er --argjson index "$selected_index" \
      '.result.data.accounts[$index].accountRef | select(type == "string" and length > 0)' \
      "$response_file" 2>/dev/null)" || {
      printf 'FAIL account selection failed: login response account list is unavailable or malformed\n' >&2
      return 1
    }
  fi

  SELECTED_ACCOUNT_INDEX="$selected_index"
}

capture_post_login_account_selector() {
  [[ "${POST_LOGIN_ACCOUNT_SELECTOR_CAPTURED:-false}" == true ]] && return 0
  POST_LOGIN_ACCOUNT_SELECTOR_PROVIDED=false
  if [[ "${E2E_ACCOUNT_REF+x}" == x ]]; then
    POST_LOGIN_ACCOUNT_SELECTOR_PROVIDED=true
    POST_LOGIN_ACCOUNT_SELECTOR="$E2E_ACCOUNT_REF"
  fi
  unset E2E_ACCOUNT_REF
  POST_LOGIN_ACCOUNT_SELECTOR_CAPTURED=true
}

acquire_login_otp() {
  local otp_code
  printf 'Login OTP: ' >/dev/tty
  IFS= read -r -s otp_code </dev/tty
  printf '\n' >/dev/tty
  [[ -n "$otp_code" ]] || { printf 'FAIL OTP is required\n' >&2; return 1; }
  printf '%s\n' "$otp_code"
}

publish_authenticated_http_state() {
  local portal_cookie authenticated_csrf account_ref
  portal_cookie="$(read_private_value portal_cookie 2>/dev/null)" || return 1
  authenticated_csrf="$(read_private_value authenticated_csrf 2>/dev/null)" || return 1
  account_ref="$(read_private_value selected_account_ref 2>/dev/null)" || return 1
  [[ -n "$portal_cookie" && -n "$authenticated_csrf" && -n "$account_ref" ]] || {
    printf 'FAIL authenticated state is incomplete\n' >&2
    return 1
  }
  PORTAL_SESSION_COOKIE_VALUE="$portal_cookie"
  XSRF_TOKEN="$authenticated_csrf"
  ACCOUNT_REF="$account_ref"
  unset AUTH_FLOW_COOKIE_VALUE
  export PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF
}

authenticated_login_journey() {
  local rc=0 cookie csrf destination_id otp_code request_body metrics curl_rc
  local raw_headers raw_body request_id idempotency_key portal_cookie authenticated_csrf account_ref
  local account_selector='' account_selector_provided=false selection_mode
  capture_post_login_account_selector
  account_selector_provided="$POST_LOGIN_ACCOUNT_SELECTOR_PROVIDED"
  account_selector="${POST_LOGIN_ACCOUNT_SELECTOR:-}"
  unset POST_LOGIN_ACCOUNT_SELECTOR POST_LOGIN_ACCOUNT_SELECTOR_PROVIDED POST_LOGIN_ACCOUNT_SELECTOR_CAPTURED
  # shellcheck disable=SC2034 # Read by post-login-orchestrator.sh after the journey returns.
  POST_LOGIN_ACCOUNT_SELECTION_STATUS=NOT_RUN
  # shellcheck disable=SC2034 # Read by post-login-orchestrator.sh to close an established session.
  POST_LOGIN_AUTHENTICATED_SESSION_ESTABLISHED=false
  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || { printf 'BLOCKED authenticated journey requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3; }
  [[ "${E2E_ALLOW_OTP_VERIFICATION:-false}" == true ]] || { printf 'BLOCKED OTP verification requires E2E_ALLOW_OTP_VERIFICATION=true\n'; return 4; }
  if ! private_runtime_state_is_owned; then
    create_private_runtime_state
  fi
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
  if [[ -n "${E2E_TEST_OTP_CODE_FILE:-}" ]]; then
    [[ "${E2E_OFFLINE_TEST_MODE:-false}" == true ]] || return 1
    otp_code="$(cat "$E2E_TEST_OTP_CODE_FILE")"
  else
    otp_code="$(acquire_login_otp)" || return 1
  fi
  [[ -n "$otp_code" ]] || { printf 'FAIL OTP is required\n'; return 1; }
  raw_headers="$E2E_PRIVATE_STATE_DIR/otp-verify.headers"; raw_body="$E2E_PRIVATE_STATE_DIR/otp-verify.json"
  request_id="${REQUEST_ID_PREFIX}-otp-verify-$(date +%s)-$$"; idempotency_key="login-otp-verify-$(date +%s)-$$"; request_body="$(jq -cn --arg code "$otp_code" '{code:$code}')"; otp_code=''
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST "${BFF_BASE_URL%/}/api/v1/auth/login/otp/verify" -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" -H "Idempotency-Key: $idempotency_key" -H "X-XSRF-TOKEN: $csrf" -H 'Content-Type: application/json' --cookie "auth_flow_sid=$cookie" --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  request_body=''; redact_headers_file "$raw_headers" "$E2E_RUN_EVIDENCE_DIR/015-otp-verify-response-headers-redacted.txt"; redact_json_file "$raw_body" "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json"
  assert_eq 0 "$curl_rc" otp_verify_curl_exit || rc=1; assert_eq 200 "${metrics%% *}" otp_verify_http_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.status' SUCCESS otp_verify_api_status || rc=1
  assert_json_scalar_eq "$E2E_RUN_EVIDENCE_DIR/016-otp-verify-response-body-redacted.json" '.result.nextStep' SELECT_ACCOUNT otp_verify_next_step || rc=1
  [[ "$rc" -eq 0 ]] || return "$rc"
  portal_cookie="$(sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=([^;]+).*/\1/p' "$raw_headers" | tail -n 1)"
  authenticated_csrf="$(jq -er '.result.data.csrfToken' "$raw_body" 2>/dev/null)" || authenticated_csrf=''
  [[ -n "$portal_cookie" && -n "$authenticated_csrf" ]] || return 1
  write_private_value portal_cookie "$portal_cookie" || return 1
  write_private_value authenticated_csrf "$authenticated_csrf" || return 1
  # shellcheck disable=SC2034 # Read by post-login-orchestrator.sh when login later fails.
  POST_LOGIN_AUTHENTICATED_SESSION_ESTABLISHED=true

  if [[ "$account_selector_provided" == true ]]; then
    selection_mode=explicit
  else
    selection_mode=first
  fi
  if ! select_account_ref "$raw_body" "$account_selector_provided" "$account_selector"; then
    POST_LOGIN_ACCOUNT_SELECTION_STATUS=FAIL
    printf 'next_step=SELECT_ACCOUNT\naccount_count=%s\naccount_selection=FAIL\nselection_mode=%s\nselected_account_index=none\nportal_cookie_issued=yes\n' \
      "$SELECTED_ACCOUNT_COUNT" "$selection_mode" \
      >"$E2E_RUN_EVIDENCE_DIR/017-authenticated-transition-summary.txt"
    account_selector=''
    return 1
  fi
  # shellcheck disable=SC2034 # Printed by post-login-orchestrator.sh in the consolidated summary.
  POST_LOGIN_ACCOUNT_SELECTION_STATUS=PASS
  account_ref="$SELECTED_ACCOUNT_REF"
  write_private_value selected_account_ref "$account_ref" || return 1
  account_selector=''
  unset SELECTED_ACCOUNT_REF
  publish_authenticated_http_state || return 1
  printf 'next_step=SELECT_ACCOUNT\naccount_count=%s\naccount_selection=PASS\nselection_mode=%s\nselected_account_index=%s\nportal_cookie_issued=yes\n' \
    "$SELECTED_ACCOUNT_COUNT" "$selection_mode" "$SELECTED_ACCOUNT_INDEX" \
    >"$E2E_RUN_EVIDENCE_DIR/017-authenticated-transition-summary.txt"
  unset SELECTED_ACCOUNT_INDEX SELECTED_ACCOUNT_COUNT
  printf 'login_start_attempts=1\notp_send_attempts=1\notp_verify_attempts=1\nautomatic_retries=0\n' >"$E2E_RUN_EVIDENCE_DIR/018-external-effect-summary.txt"
}

validate_logout_contract() {
  local curl_rc="$1" http_status="$2" headers="$3" body="$4" rc=0
  [[ "$curl_rc" -eq 0 ]] || rc=1
  [[ "$http_status" == 200 ]] || rc=1
  [[ "$(jq -r '.status // empty' "$body")" == SUCCESS ]] || rc=1
  sed -n -E '/^[Ss]et-[Cc]ookie:[[:space:]]*portal_sid=/p' "$headers" | grep -Eq '([Mm]ax-[Aa]ge=0|[Ee]xpires=Thu, 01 Jan 1970)' || rc=1
  return "$rc"
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
