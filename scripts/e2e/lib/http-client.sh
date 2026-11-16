#!/usr/bin/env bash
bff_request() {
  local method="$1" path="$2" body="${3:-}" out="$E2E_RUN_EVIDENCE_DIR"
  local secret_name header_fd='' body_fd='' cookie_header='' rc
  for secret_name in PORTAL_SESSION_COOKIE_VALUE AUTH_FLOW_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF; do
    [[ -v "$secret_name" ]] && export -n "${secret_name?}"
  done
  unset secret_name
  local request_id="${REQUEST_ID_PREFIX}-$(date +%s)-$$" url="${BFF_BASE_URL%/}${path}"
  local -a args=(--silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X "$method" -H "Accept-Language: $ACCEPT_LANGUAGE" -D "$out/.response-headers.raw" -o "$out/.response-body.raw" -w '%{http_code} %{time_total}')
  [[ "${E2E_SEND_REQUEST_ID:-true}" == true ]] && args+=(-H "X-Request-Id: $request_id")
  [[ -n "${IDEMPOTENCY_KEY:-}" ]] && args+=(-H "Idempotency-Key: $IDEMPOTENCY_KEY")
  [[ "$E2E_TLS_VERIFY" == true ]] || args+=(--insecure)
  if [[ -n "${PORTAL_SESSION_COOKIE_VALUE:-}${AUTH_FLOW_COOKIE_VALUE:-}${XSRF_TOKEN:-}${ACCOUNT_REF:-}" ]]; then
    if [[ -n "${PORTAL_SESSION_COOKIE_VALUE:-}" ]]; then
      cookie_header="portal_sid=$PORTAL_SESSION_COOKIE_VALUE"
    fi
    if [[ -n "${AUTH_FLOW_COOKIE_VALUE:-}" ]]; then
      [[ -z "$cookie_header" ]] || cookie_header+='; '
      cookie_header+="auth_flow_sid=$AUTH_FLOW_COOKIE_VALUE"
    fi
    exec {header_fd}< <(
      [[ -z "$cookie_header" ]] || printf 'Cookie: %s\n' "$cookie_header"
      [[ -n "${XSRF_TOKEN:-}" ]] && printf 'X-XSRF-TOKEN: %s\n' "$XSRF_TOKEN"
      [[ -n "${ACCOUNT_REF:-}" ]] && printf 'Account-Ref: %s\n' "$ACCOUNT_REF"
    )
    args+=(-H "@/dev/fd/$header_fd")
  fi
  if [[ -n "$body" ]]; then
    exec {body_fd}< <(printf '%s' "$body")
    args+=(-H 'Content-Type: application/json' --data-binary "@/dev/fd/$body_fd")
  fi
  printf 'method=%s\nurl=%s\nrequest_id=%s\nportal_cookie=%s\nauth_flow_cookie=%s\nxsrf=%s\naccount_ref=%s\n' "$method" "$url" "$request_id" "$(redacted_presence "${PORTAL_SESSION_COOKIE_VALUE:-}")" "$(redacted_presence "${AUTH_FLOW_COOKIE_VALUE:-}")" "$(redacted_presence "${XSRF_TOKEN:-}")" "$(redacted_presence "${ACCOUNT_REF:-}")" >"$out/005-request-command-redacted.txt"
  local metrics
  if metrics="$(curl "${args[@]}" "$url" 2>"$out/.curl-error")"; then
    rc=0
  else
    rc=$?
  fi
  if [[ -n "$body_fd" ]]; then
    exec {body_fd}<&- || rc=1
  fi
  if [[ -n "$header_fd" ]]; then
    exec {header_fd}<&- || rc=1
  fi
  E2E_CURL_EXIT="$rc"; E2E_HTTP_STATUS="${metrics%% *}"; E2E_ELAPSED_SECONDS="${metrics#* }"
  [[ -f "$out/.curl-error" ]] && redact_stream <"$out/.curl-error" >"$out/009-curl-diagnostic-redacted.txt"
  [[ -f "$out/.response-headers.raw" ]] && redact_headers_file "$out/.response-headers.raw" "$out/006-response-headers-redacted.txt"
  [[ -f "$out/.response-body.raw" ]] && redact_json_file "$out/.response-body.raw" "$out/007-response-body-redacted.json"
  rm -f "$out/.curl-error" "$out/.response-headers.raw" "$out/.response-body.raw"
  export E2E_CURL_EXIT E2E_HTTP_STATUS E2E_ELAPSED_SECONDS E2E_REQUEST_ID="$request_id"
}
