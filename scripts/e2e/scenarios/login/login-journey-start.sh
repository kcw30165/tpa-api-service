#!/usr/bin/env bash
login_journey_start() {
  local out="$E2E_RUN_EVIDENCE_DIR" raw_headers raw_body metrics rc request_id idempotency_key request_body
  [[ -n "${LOGIN_TEST_USERNAME:-}" && -n "${LOGIN_TEST_PASSWORD:-}" ]] || {
    printf 'FAIL runtime Login credentials are required\n'; return 1;
  }
  [[ -n "${E2E_PRIVATE_STATE_DIR:-}" && -d "$E2E_PRIVATE_STATE_DIR" ]] || {
    printf 'FAIL private runtime state is unavailable\n'; return 1;
  }
  raw_headers="$E2E_PRIVATE_STATE_DIR/login-start.headers"
  raw_body="$E2E_PRIVATE_STATE_DIR/login-start.json"
  request_id="${REQUEST_ID_PREFIX}-start-$(date +%s)-$$"
  idempotency_key="login-start-$(date +%s)-$$"
  request_body="$(jq -cn --arg username "$LOGIN_TEST_USERNAME" --arg password "$LOGIN_TEST_PASSWORD" '{username:$username,password:$password}')"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      -X POST "${BFF_BASE_URL%/}/api/v1/auth/login" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" \
      -H "Idempotency-Key: $idempotency_key" -H 'Content-Type: application/json' \
      --data-binary "$request_body" -D "$raw_headers" -o "$raw_body" -w '%{http_code} %{time_total}')"; then rc=0; else rc=$?; fi
  LOGIN_START_CURL_EXIT="$rc"
  LOGIN_START_HTTP_STATUS="${metrics%% *}"
  redact_headers_file "$raw_headers" "$out/006-response-headers-redacted.txt"
  redact_json_file "$raw_body" "$out/007-response-body-redacted.json"
  [[ "$rc" -eq 0 && "$LOGIN_START_HTTP_STATUS" == 200 ]] || return 1
  jq -e '.status == "SUCCESS" and .result.nextStep == "SELECT_OTP_DESTINATION"' "$raw_body" >/dev/null || return 1
  local cookie csrf destination_count index destination_id
  cookie="$(sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*auth_flow_sid=([^;]+).*/\1/p' "$raw_headers" | tail -n 1)"
  csrf="$(jq -er '.result.data.csrfToken' "$raw_body")"
  destination_count="$(jq -r '.result.data.destinations | length' "$raw_body")"
  [[ "$destination_count" -gt 0 ]] || return 1
  index="${LOGIN_OTP_DESTINATION_INDEX:-0}"
  [[ "$index" =~ ^[0-9]+$ && "$index" -lt "$destination_count" ]] || return 1
  destination_id="$(jq -er --argjson index "$index" '.result.data.destinations[$index].destinationId' "$raw_body")"
  [[ -n "$cookie" && -n "$csrf" && -n "$destination_id" ]] || return 1
  write_private_value auth_flow_cookie "$cookie"
  write_private_value csrf_token "$csrf"
  write_private_value destination_id "$destination_id"
  printf 'next_step=SELECT_OTP_DESTINATION\ndestination_count=%s\nselected_destination_index=%s\n' "$destination_count" "$index" >"$out/011-login-journey-structure.txt"
}
