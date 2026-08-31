#!/usr/bin/env bash
run_scenario() {
  local rc=0 portal_cookie authenticated_csrf account_ref
  local request_id from_date to_date headers body metrics curl_rc
  local item_count page page_size total_records has_next

  [[ "${E2E_ALLOW_AUTHENTICATED_LOGOUT:-false}" == true ]] || {
    printf 'BLOCKED authenticated logout requires E2E_ALLOW_AUTHENTICATED_LOGOUT=true\n'
    return 9
  }

  AUTHENTICATED_ACCOUNT_INDEX="${CONTRIBUTIONS_ACCOUNT_INDEX:-0}"
  export AUTHENTICATED_ACCOUNT_INDEX
  authenticated_login_journey || return 1

  portal_cookie="$(read_private_value portal_cookie)"
  authenticated_csrf="$(read_private_value authenticated_csrf)"
  account_ref="$(read_private_value selected_account_ref)"
  from_date="${CONTRIBUTIONS_FROM_DATE:-20/03/2026}"
  to_date="${CONTRIBUTIONS_TO_DATE:-29/06/2026}"
  headers="$E2E_PRIVATE_STATE_DIR/contributions.headers"
  body="$E2E_PRIVATE_STATE_DIR/contributions.json"
  request_id="${REQUEST_ID_PREFIX}-contributions-$(date +%s)-$$"

  if metrics="$(curl --silent --show-error --get \
      --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" \
      --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" \
      "${BFF_BASE_URL%/}/api/v1/contributions" \
      --data-urlencode "fromDate=$from_date" \
      --data-urlencode "toDate=$to_date" \
      -H "Accept-Language: $ACCEPT_LANGUAGE" \
      -H "X-Request-Id: $request_id" \
      -H "Account-Ref: $account_ref" \
      -H "X-XSRF-TOKEN: $authenticated_csrf" \
      --cookie "portal_sid=$portal_cookie" \
      -D "$headers" -o "$body" \
      -w '%{http_code} %{time_total}')"; then
    curl_rc=0
  else
    curl_rc=$?
  fi

  redact_headers_file "$headers" "$E2E_RUN_EVIDENCE_DIR/020-contributions-response-headers-redacted.txt"
  redact_json_file "$body" "$E2E_RUN_EVIDENCE_DIR/021-contributions-response-body-redacted.json"
  assert_eq 0 "$curl_rc" contributions_curl_exit || rc=1
  assert_eq 200 "${metrics%% *}" contributions_http_status || rc=1
  jq -e '.actions.export.enabled | type == "boolean"' "$body" >/dev/null || {
    printf 'FAIL contributions export action contract\n'; rc=1;
  }
  jq -e '.items | type == "array"' "$body" >/dev/null || {
    printf 'FAIL contributions items contract\n'; rc=1;
  }
  jq -e '.pagination | type == "object"' "$body" >/dev/null || {
    printf 'FAIL contributions pagination contract\n'; rc=1;
  }

  item_count="$(jq -r '.items | length' "$body")"
  page="$(jq -r '.pagination.page' "$body")"
  page_size="$(jq -r '.pagination.pageSize' "$body")"
  total_records="$(jq -r '.pagination.totalRecords' "$body")"
  has_next="$(jq -r '.pagination.hasNextPage' "$body")"
  {
    printf 'from_date=%s\nto_date=%s\n' "$from_date" "$to_date"
    printf 'selected_account_index=%s\naccount_ref_present=yes\n' "$AUTHENTICATED_ACCOUNT_INDEX"
    printf 'portal_cookie_present=yes\nauthenticated_xsrf_present=yes\n'
    printf 'item_count=%s\npage=%s\npage_size=%s\ntotal_records=%s\nhas_next_page=%s\n' \
      "$item_count" "$page" "$page_size" "$total_records" "$has_next"
    printf 'contributions_requests=1\nautomatic_retries=0\n'
  } > "$E2E_RUN_EVIDENCE_DIR/022-contributions-summary.txt"

  [[ "$rc" -eq 0 ]] || return "$rc"
  authenticated_logout_journey
}
