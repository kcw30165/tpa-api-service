#!/usr/bin/env bash
# Shared helpers for the Forgot Password E2E scenarios. Sourced by the scenario files; defines functions only.
#
# Member-identifying inputs (username, identity value, date of birth, e-mail, mobile), the auth-flow cookie and the CSRF token live only in the
# private runtime state directory and in curl arguments. The evidence directory receives presence flags and redacted responses only.

FP_PATH_PREFIX="/api/v1/auth/forgot-password"
FP_SEQ=11
FP_CURL_EXIT=0
FP_HTTP_STATUS=000
FP_RAW_HEADERS=""
FP_RAW_BODY=""
FP_ORIGIN=""

fp_note() { printf '%s\n' "$*" >>"$E2E_RUN_EVIDENCE_DIR/030-summary.txt"; }

fp_progress() {
  printf '%s\n' "$*" >>"$E2E_RUN_EVIDENCE_DIR/040-progress.txt"
  { printf '%s\n' "$*" >/dev/tty; } 2>/dev/null || true
}

fp_wait() { sleep "$1"; }

fp_new_key() { printf 'fp-%s-%s-%s' "$1" "$(date +%s)" "$$"; }

fp_require_inputs() {
  local name dob
  local -a missing=()
  for name in FORGOT_PASSWORD_TEST_USERNAME FORGOT_PASSWORD_TEST_IDENTITY_VALUE FORGOT_PASSWORD_TEST_DATE_OF_BIRTH; do
    [[ -n "${!name:-}" ]] || missing+=("$name")
  done
  if [[ -n "${FORGOT_PASSWORD_TEST_EMAIL:-}" && -n "${FORGOT_PASSWORD_TEST_MOBILE:-}" ]]; then
    missing+=("only one of FORGOT_PASSWORD_TEST_EMAIL and FORGOT_PASSWORD_TEST_MOBILE")
  elif [[ -z "${FORGOT_PASSWORD_TEST_EMAIL:-}" && -z "${FORGOT_PASSWORD_TEST_MOBILE:-}" ]]; then
    missing+=("one of FORGOT_PASSWORD_TEST_EMAIL and FORGOT_PASSWORD_TEST_MOBILE")
  fi
  dob="${FORGOT_PASSWORD_TEST_DATE_OF_BIRTH:-}"
  if [[ -n "$dob" && ! "$dob" =~ ^[0-9]{2}/[0-9]{2}/[0-9]{4}$ ]]; then
    missing+=("FORGOT_PASSWORD_TEST_DATE_OF_BIRTH in the format dd/MM/yyyy")
  fi
  FP_ORIGIN="${FORGOT_PASSWORD_E2E_ORIGIN:-http://localhost:4200}"
  if [[ ! "$FP_ORIGIN" =~ ^https?://[^/]+$ ]]; then
    missing+=("FORGOT_PASSWORD_E2E_ORIGIN as scheme://host[:port] without a path")
  fi
  if ((${#missing[@]} > 0)); then
    printf 'FAIL runtime inputs are required or invalid:\n'
    for name in "${missing[@]}"; do printf '  - %s\n' "$name"; done
    return 1
  fi
}

# Remember an auth_flow_sid cookie that a response issued or rotated (a cleared cookie has no value and is ignored).
fp_capture_cookie() {
  local value
  value="$(tr -d '\r' <"$1" | sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*auth_flow_sid=([^;]+).*/\1/p' | tail -n 1)"
  [[ -z "$value" ]] || write_private_value auth_flow_cookie "$value"
}

# fp_call <name> <path> <json body or empty> <idempotency key or -> <auth yes|no>
# Sets FP_CURL_EXIT, FP_HTTP_STATUS, FP_RAW_HEADERS and FP_RAW_BODY. Must be called directly, never inside $( ).
fp_call() {
  local name="$1" path="$2" body="$3" idem="$4" auth="$5"
  local out="$E2E_RUN_EVIDENCE_DIR" prefix request_id metrics cookie csrf
  local -a args
  FP_SEQ=$((FP_SEQ + 1))
  prefix="$(printf '%03d-%s' "$FP_SEQ" "$name")"
  FP_RAW_HEADERS="$E2E_PRIVATE_STATE_DIR/$name.headers"
  FP_RAW_BODY="$E2E_PRIVATE_STATE_DIR/$name.json"
  request_id="${REQUEST_ID_PREFIX}-${name}-$(date +%s)-$$"
  args=(--silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST
    -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id")
  [[ "$E2E_TLS_VERIFY" == true ]] || args+=(--insecure)
  [[ "$idem" == "-" ]] || args+=(-H "Idempotency-Key: $idem")
  if [[ "$auth" == yes ]]; then
    cookie="$(read_private_value auth_flow_cookie)"
    csrf="$(read_private_value csrf_token)"
    args+=(-H "X-XSRF-TOKEN: $csrf" -H "Origin: $FP_ORIGIN" --cookie "auth_flow_sid=$cookie")
  fi
  [[ -z "$body" ]] || args+=(-H 'Content-Type: application/json' --data-binary "$body")
  {
    printf 'method=POST\npath=%s\nrequest_id=%s\n' "$path" "$request_id"
    printf 'idempotency_key=%s\n' "$([[ "$idem" == "-" ]] && echo not-sent || echo sent-redacted)"
    printf 'auth_flow_cookie_csrf_token_and_origin=%s\n' "$([[ "$auth" == yes ]] && echo sent-redacted || echo not-sent)"
    printf 'body=%s\n' "$([[ -n "$body" ]] && echo json-redacted || echo none)"
  } >"$out/${prefix}-request-summary.txt"
  rm -f "$FP_RAW_HEADERS" "$FP_RAW_BODY"
  if metrics="$(curl "${args[@]}" -D "$FP_RAW_HEADERS" -o "$FP_RAW_BODY" -w '%{http_code} %{time_total}' "${BFF_BASE_URL%/}${FP_PATH_PREFIX}${path}")"; then
    FP_CURL_EXIT=0
  else
    FP_CURL_EXIT=$?
  fi
  [[ -n "${metrics:-}" ]] || metrics="000 0"
  FP_HTTP_STATUS="${metrics%% *}"
  E2E_CURL_EXIT="$FP_CURL_EXIT"; E2E_HTTP_STATUS="$FP_HTTP_STATUS"; E2E_ELAPSED_SECONDS="${metrics#* }"
  [[ ! -f "$FP_RAW_HEADERS" ]] || redact_headers_file "$FP_RAW_HEADERS" "$out/${prefix}-response-headers-redacted.txt"
  [[ ! -f "$FP_RAW_BODY" ]] || redact_json_file "$FP_RAW_BODY" "$out/${prefix}-response-body-redacted.json"
  [[ ! -f "$FP_RAW_HEADERS" ]] || fp_capture_cookie "$FP_RAW_HEADERS"
  return 0
}

fp_json() { jq -r "$1" "$FP_RAW_BODY" 2>/dev/null || true; }

# fp_assert_response <label> <http status> <api status> <next step or -> [error code]
# Asserts on the raw body: the evidence copy redacts every key named code, which includes the error code.
fp_assert_response() {
  local label="$1" http="$2" api="$3" next="$4" code="${5:-}" rc=0 actual
  assert_eq 0 "$FP_CURL_EXIT" "${label}_curl_exit" || rc=1
  assert_eq "$http" "$FP_HTTP_STATUS" "${label}_http_status" || rc=1
  actual="$(fp_json '.status // "<absent>"')"
  assert_eq "$api" "${actual:-<not-json>}" "${label}_api_status" || rc=1
  if [[ "$next" != "-" ]]; then
    actual="$(fp_json '.result.nextStep // "<absent>"')"
    assert_eq "$next" "${actual:-<not-json>}" "${label}_next_step" || rc=1
  fi
  if [[ -n "$code" ]]; then
    actual="$(fp_json '.errors[0].code // "<absent>"')"
    assert_eq "$code" "${actual:-<not-json>}" "${label}_error_code" || rc=1
  fi
  return "$rc"
}

# Start, identity check and details check: the details check sends the first real OTP message.
fp_journey_to_otp_sent() {
  local body identity_type csrf cooldown
  body="$(jq -cn --arg username "$FORGOT_PASSWORD_TEST_USERNAME" '{username:$username}')"
  fp_call start /start "$body" "$(fp_new_key start)" no
  fp_assert_response fp_start 200 SUCCESS ENTER_ID || return 1
  [[ -f "$E2E_PRIVATE_STATE_DIR/auth_flow_cookie" ]] || { printf 'FAIL start did not issue an auth_flow_sid cookie\n'; return 1; }
  csrf="$(fp_json '.result.data.csrfToken // empty')"
  [[ -n "$csrf" ]] || { printf 'FAIL start did not return a csrfToken\n'; return 1; }
  write_private_value csrf_token "$csrf"
  identity_type="${FORGOT_PASSWORD_TEST_IDENTITY_TYPE:-$(fp_json '.result.data.identityType // empty')}"
  [[ -n "$identity_type" ]] || { printf 'FAIL no identity type: set FORGOT_PASSWORD_TEST_IDENTITY_TYPE (GPN, GPFS, STAFF_NO or ID)\n'; return 1; }
  printf 'PASS fp_start_cookie_and_csrf_issued\n'

  body="$(jq -cn --arg type "$identity_type" --arg value "$FORGOT_PASSWORD_TEST_IDENTITY_VALUE" '{identityType:$type,identityValue:$value}')"
  fp_call identity-check /identity-check "$body" - yes
  fp_assert_response fp_identity 200 SUCCESS ENTER_DETAILS || return 1

  if [[ -n "${FORGOT_PASSWORD_TEST_EMAIL:-}" ]]; then
    body="$(jq -cn --arg dob "$FORGOT_PASSWORD_TEST_DATE_OF_BIRTH" --arg email "$FORGOT_PASSWORD_TEST_EMAIL" '{dateOfBirth:$dob,email:$email}')"
  else
    body="$(jq -cn --arg dob "$FORGOT_PASSWORD_TEST_DATE_OF_BIRTH" --arg mobile "$FORGOT_PASSWORD_TEST_MOBILE" '{dateOfBirth:$dob,mobile:$mobile}')"
  fi
  fp_call details-check /details-check "$body" "$(fp_new_key details)" yes
  body=''
  fp_assert_response fp_details 200 SUCCESS VERIFY_OTP || return 1
  cooldown="$(fp_json '.result.data.resendCooldownSeconds // empty')"
  if [[ "$cooldown" =~ ^[1-9][0-9]*$ ]]; then write_private_value cooldown_seconds "$cooldown"; fi
  fp_note "start_attempts=1"
  fp_note "identity_check_attempts=1"
  fp_note "details_check_attempts=1"
  fp_note "real_messages_requested_by_the_journey=1"
}

fp_resend() { fp_call "$1" /otp/resend '' "$2" yes; }

# The cooldown to wait for between accepted resends: the value the details check returned, else a runtime value; never a guess.
fp_cooldown_seconds() {
  local value=""
  [[ ! -f "$E2E_PRIVATE_STATE_DIR/cooldown_seconds" ]] || value="$(read_private_value cooldown_seconds)"
  [[ -n "$value" ]] || value="${FORGOT_PASSWORD_RESEND_COOLDOWN_SECONDS:-}"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    printf 'FAIL the details check returned no resend cooldown; set FORGOT_PASSWORD_RESEND_COOLDOWN_SECONDS\n' >&2
    return 1
  fi
  printf '%s' "$value"
}
