#!/usr/bin/env bash
bff_request() {
  local method="$1" path="$2" body="${3:-}" out="$E2E_RUN_EVIDENCE_DIR"
  local request_id="${REQUEST_ID_PREFIX}-$(date +%s)-$$" url="${BFF_BASE_URL%/}${path}"
  local -a args=(--silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X "$method" -H "Accept-Language: $ACCEPT_LANGUAGE" -D "$out/006-response-headers-redacted.txt" -o "$out/007-response-body-redacted.json" -w '%{http_code} %{time_total}')
  [[ "${E2E_SEND_REQUEST_ID:-true}" == true ]] && args+=(-H "X-Request-Id: $request_id")
  [[ -n "${IDEMPOTENCY_KEY:-}" ]] && args+=(-H "Idempotency-Key: $IDEMPOTENCY_KEY")
  [[ "$E2E_TLS_VERIFY" == true ]] || args+=(--insecure)
  [[ -n "${PORTAL_SESSION_COOKIE_VALUE:-}" ]] && args+=(--cookie "portal_sid=$PORTAL_SESSION_COOKIE_VALUE")
  [[ -n "${AUTH_FLOW_COOKIE_VALUE:-}" ]] && args+=(--cookie "auth_flow_sid=$AUTH_FLOW_COOKIE_VALUE")
  [[ -n "${XSRF_TOKEN:-}" ]] && args+=(-H "X-XSRF-TOKEN: $XSRF_TOKEN")
  [[ -n "${ACCOUNT_REF:-}" ]] && args+=(-H "Account-Ref: $ACCOUNT_REF")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' --data-binary "$body")
  printf 'method=%s\nurl=%s\nrequest_id=%s\nportal_cookie=%s\nauth_flow_cookie=%s\nxsrf=%s\naccount_ref=%s\n' "$method" "$url" "$request_id" "$(redacted_presence "${PORTAL_SESSION_COOKIE_VALUE:-}")" "$(redacted_presence "${AUTH_FLOW_COOKIE_VALUE:-}")" "$(redacted_presence "${XSRF_TOKEN:-}")" "$(redacted_presence "${ACCOUNT_REF:-}")" >"$out/005-request-command-redacted.txt"
  local metrics rc
  if metrics="$(curl "${args[@]}" "$url" 2>"$out/.curl-error")"; then
    rc=0
  else
    rc=$?
  fi
  E2E_CURL_EXIT="$rc"; E2E_HTTP_STATUS="${metrics%% *}"; E2E_ELAPSED_SECONDS="${metrics#* }"
  [[ -f "$out/.curl-error" ]] && redact_stream <"$out/.curl-error" >"$out/009-curl-diagnostic-redacted.txt"
  rm -f "$out/.curl-error"
  export E2E_CURL_EXIT E2E_HTTP_STATUS E2E_ELAPSED_SECONDS E2E_REQUEST_ID="$request_id"
}
