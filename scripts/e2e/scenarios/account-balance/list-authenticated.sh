#!/usr/bin/env bash
run_scenario() {
  local rc=0 portal_cookie csrf account_ref headers body metrics curl_rc request_id currency path
  [[ "${E2E_ALLOW_AUTHENTICATED_LOGOUT:-false}" == true ]] || { printf 'BLOCKED logout guard required\n'; return 9; }
  authenticated_login_journey || return 1
  portal_cookie="$(read_private_value portal_cookie)"; csrf="$(read_private_value authenticated_csrf)"; account_ref="$(read_private_value selected_account_ref)"
  headers="$E2E_PRIVATE_STATE_DIR/account-balance.headers"; body="$E2E_PRIVATE_STATE_DIR/account-balance.json"; request_id="${REQUEST_ID_PREFIX}-account-balance-$(date +%s)-$$"; currency="${ACCOUNT_BALANCE_CURRENCY:-}"; path="${BFF_BASE_URL%/}/api/v1/account-balance"
  [[ -z "$currency" ]] || path="$path?currency=$currency"
  if metrics="$(curl --silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" --get "$path" -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id" -H "Account-Ref: $account_ref" --cookie "portal_sid=$portal_cookie" -D "$headers" -o "$body" -w '%{http_code} %{time_total}')"; then curl_rc=0; else curl_rc=$?; fi
  redact_headers_file "$headers" "$E2E_RUN_EVIDENCE_DIR/020-account-balance-response-headers-redacted.txt"
  assert_eq 0 "$curl_rc" account_balance_curl_exit || rc=1; assert_eq 200 "${metrics%% *}" account_balance_http_status || rc=1
  jq -e '.success == true and .status == "SUCCESS" and (.result.data.asOfDate|type=="string") and (.result.data.currencySelection.availableCurrencies|type=="array") and (.result.data.currencySelection.selectedCurrency|type=="string") and (.result.data.accountOverview|type=="object") and (.result.data.investmentPortfolio|type=="object")' "$body" >/dev/null || { printf 'FAIL account balance public contract\n'; rc=1; }
  jq -e 'has("accountRef")|not' "$body" >/dev/null || { printf 'FAIL accountRef echoed\n'; rc=1; }
  printf 'account_balance_requests=1\nselected_account_index=%s\ncurrency_supplied=%s\nresponse_contract=%s\n' "${AUTHENTICATED_ACCOUNT_INDEX:-0}" "$([[ -n "$currency" ]] && printf yes || printf no)" "$([[ "$rc" -eq 0 ]] && printf PASS || printf FAIL)" >"$E2E_RUN_EVIDENCE_DIR/022-account-balance-summary.txt"
  [[ "$rc" -eq 0 ]] || return "$rc"; authenticated_logout_journey
}
