#!/usr/bin/env bash
# Shared helpers for the Registration E2E scenarios. Sourced by the scenario files;
# defines functions only.
#
# Member-identifying inputs (identity value, date of birth, contact, OTP code,
# selected trust, password) and the per-flow auth-flow cookie / CSRF token live
# only in the private runtime state directory and in curl arguments. The evidence
# directory receives presence flags and redacted responses only. Two concurrent
# flows (A and B) are supported by namespacing all private state and sequence
# labels with the flow identifier.

REG_PATH_PREFIX="/api/v1/auth/registration"
REG_SEQ=11
REG_CURL_EXIT=0
REG_HTTP_STATUS=000
REG_RAW_HEADERS=""
REG_RAW_BODY=""
REG_ORIGIN=""

reg_note() { printf '%s\n' "$*" >>"$E2E_RUN_EVIDENCE_DIR/030-summary.txt"; }

reg_progress() {
  printf '%s\n' "$*" >>"$E2E_RUN_EVIDENCE_DIR/040-progress.txt"
  { printf '%s\n' "$*" >/dev/tty; } 2>/dev/null || true
}

reg_new_key() { printf 'reg-%s-%s-%s' "$1" "$(date +%s)" "$$"; }

reg_require_inputs() {
  local flow name var email_var mobile_var
  local -a missing=()
  for flow in A B; do
    for name in IDENTITY_TYPE IDENTITY_VALUE DATE_OF_BIRTH PASSWORD; do
      var="REGISTRATION_RACE_${flow}_${name}"
      [[ -n "${!var:-}" ]] || missing+=("$var")
    done
    email_var="REGISTRATION_RACE_${flow}_EMAIL"
    mobile_var="REGISTRATION_RACE_${flow}_MOBILE"
    if [[ -n "${!email_var:-}" && -n "${!mobile_var:-}" ]]; then
      missing+=("only one of $email_var and $mobile_var")
    elif [[ -z "${!email_var:-}" && -z "${!mobile_var:-}" ]]; then
      missing+=("one of $email_var and $mobile_var")
    fi
  done
  [[ -n "${REGISTRATION_RACE_USERNAME:-}" ]] || missing+=("REGISTRATION_RACE_USERNAME")
  REG_ORIGIN="${REGISTRATION_E2E_ORIGIN:-http://localhost:4200}"
  if [[ ! "$REG_ORIGIN" =~ ^https?://[^/]+$ ]]; then
    missing+=("REGISTRATION_E2E_ORIGIN as scheme://host[:port] without a path")
  fi
  if ((${#missing[@]} > 0)); then
    printf 'FAIL runtime inputs are required or invalid:\n'
    for name in "${missing[@]}"; do printf '  - %s\n' "$name"; done
    return 1
  fi
}

# Remember an auth_flow_sid cookie that a response issued for a given flow.
reg_capture_cookie() {
  local flow="$1" file="$2" value
  value="$(tr -d '\r' <"$file" | sed -n -E 's/^[Ss]et-[Cc]ookie:[[:space:]]*auth_flow_sid=([^;]+).*/\1/p' | tail -n 1)"
  [[ -z "$value" ]] || write_private_value "${flow}_auth_flow_cookie" "$value"
}

# reg_call <flow A|B> <name> <path> <json body or empty> <idempotency key or -> <auth yes|no>
# Sets REG_CURL_EXIT, REG_HTTP_STATUS, REG_RAW_HEADERS and REG_RAW_BODY. Must be
# called directly, never inside $( ).
reg_call() {
  local flow="$1" name="$2" path="$3" body="$4" idem="$5" auth="$6"
  local out="$E2E_RUN_EVIDENCE_DIR" prefix request_id metrics cookie csrf
  local -a args
  REG_SEQ=$((REG_SEQ + 1))
  prefix="$(printf '%03d-%s-%s' "$REG_SEQ" "$flow" "$name")"
  REG_RAW_HEADERS="$E2E_PRIVATE_STATE_DIR/${flow}_${name}.headers"
  REG_RAW_BODY="$E2E_PRIVATE_STATE_DIR/${flow}_${name}.json"
  request_id="${REQUEST_ID_PREFIX}-${flow}-${name}-$(date +%s)-$$"
  args=(--silent --show-error --connect-timeout "$E2E_CONNECT_TIMEOUT_SECONDS" --max-time "$E2E_REQUEST_TIMEOUT_SECONDS" -X POST
    -H "Accept-Language: $ACCEPT_LANGUAGE" -H "X-Request-Id: $request_id")
  [[ "$E2E_TLS_VERIFY" == true ]] || args+=(--insecure)
  [[ "$idem" == "-" ]] || args+=(-H "Idempotency-Key: $idem")
  if [[ "$auth" == yes ]]; then
    cookie="$(read_private_value "${flow}_auth_flow_cookie")"
    csrf="$(read_private_value "${flow}_csrf_token")"
    args+=(-H "X-XSRF-TOKEN: $csrf" -H "Origin: $REG_ORIGIN" --cookie "auth_flow_sid=$cookie")
  fi
  [[ -z "$body" ]] || args+=(-H 'Content-Type: application/json' --data-binary "$body")
  {
    printf 'flow=%s\nmethod=POST\npath=%s\nrequest_id=%s\n' "$flow" "$path" "$request_id"
    printf 'idempotency_key=%s\n' "$([[ "$idem" == "-" ]] && echo not-sent || echo sent-redacted)"
    printf 'auth_flow_cookie_csrf_token_and_origin=%s\n' "$([[ "$auth" == yes ]] && echo sent-redacted || echo not-sent)"
    printf 'body=%s\n' "$([[ -n "$body" ]] && echo json-redacted || echo none)"
  } >"$out/${prefix}-request-summary.txt"
  rm -f "$REG_RAW_HEADERS" "$REG_RAW_BODY"
  if metrics="$(curl "${args[@]}" -D "$REG_RAW_HEADERS" -o "$REG_RAW_BODY" -w '%{http_code} %{time_total}' "${BFF_BASE_URL%/}${REG_PATH_PREFIX}${path}")"; then
    REG_CURL_EXIT=0
  else
    REG_CURL_EXIT=$?
  fi
  [[ -n "${metrics:-}" ]] || metrics="000 0"
  REG_HTTP_STATUS="${metrics%% *}"
  E2E_CURL_EXIT="$REG_CURL_EXIT"; E2E_HTTP_STATUS="$REG_HTTP_STATUS"; E2E_ELAPSED_SECONDS="${metrics#* }"
  [[ ! -f "$REG_RAW_HEADERS" ]] || redact_headers_file "$REG_RAW_HEADERS" "$out/${prefix}-response-headers-redacted.txt"
  [[ ! -f "$REG_RAW_BODY" ]] || redact_json_file "$REG_RAW_BODY" "$out/${prefix}-response-body-redacted.json"
  [[ ! -f "$REG_RAW_HEADERS" ]] || reg_capture_cookie "$flow" "$REG_RAW_HEADERS"
  return 0
}

reg_json() { jq -r "$1" "$REG_RAW_BODY" 2>/dev/null || true; }

# reg_assert_response <label> <http status> <api status> <next step or -> [error code]
# Asserts on the raw body: the evidence copy redacts every sensitive key.
reg_assert_response() {
  local label="$1" http="$2" api="$3" next="$4" code="${5:-}" rc=0 actual
  assert_eq 0 "$REG_CURL_EXIT" "${label}_curl_exit" || rc=1
  assert_eq "$http" "$REG_HTTP_STATUS" "${label}_http_status" || rc=1
  actual="$(reg_json '.status // "<absent>"')"
  assert_eq "$api" "${actual:-<not-json>}" "${label}_api_status" || rc=1
  if [[ "$next" != "-" ]]; then
    actual="$(reg_json '.result.nextStep // "<absent>"')"
    assert_eq "$next" "${actual:-<not-json>}" "${label}_next_step" || rc=1
  fi
  if [[ -n "$code" ]]; then
    actual="$(reg_json '.errors[0].code // "<absent>"')"
    assert_eq "$code" "${actual:-<not-json>}" "${label}_error_code" || rc=1
  fi
  return "$rc"
}

# Drives one flow (A or B) from /start through a successful /otp/verify and
# /username/check against the SAME candidate username
# (REGISTRATION_RACE_USERNAME), leaving the flow ready to call /complete. Real
# OTP delivery is used; the OTP code must be supplied via
# REGISTRATION_RACE_<FLOW>_OTP_CODE (captured by the operator from the real
# inbox/SMS before running).
reg_journey_to_username_checked() {
  local flow="$1"
  local idt_var="REGISTRATION_RACE_${flow}_IDENTITY_TYPE"
  local idv_var="REGISTRATION_RACE_${flow}_IDENTITY_VALUE"
  local dob_var="REGISTRATION_RACE_${flow}_DATE_OF_BIRTH"
  local email_var="REGISTRATION_RACE_${flow}_EMAIL"
  local mobile_var="REGISTRATION_RACE_${flow}_MOBILE"
  local otp_var="REGISTRATION_RACE_${flow}_OTP_CODE"
  local identity_type="${!idt_var}" identity_value="${!idv_var}" dob="${!dob_var}"
  local email="${!email_var:-}" mobile="${!mobile_var:-}" otp_code="${!otp_var:-}"
  local body csrf trust_json account_env trust_code

  body="$(jq -cn --arg type "$identity_type" --arg value "$identity_value" --arg dob "$dob" \
    --arg email "$email" --arg mobile "$mobile" \
    '{identityType:$type, identityValue:$value, dateOfBirth:$dob,
      email: (if $email == "" then null else $email end),
      mobile: (if $mobile == "" then null else $mobile end),
      captchaChallengeId: null}')"
  reg_call "$flow" start /start "$body" "$(reg_new_key "${flow}-start")" no
  body=''
  reg_assert_response "reg_${flow}_start" 200 SUCCESS VERIFY_OTP || return 1
  [[ -f "$E2E_PRIVATE_STATE_DIR/${flow}_auth_flow_cookie" ]] || {
    printf 'FAIL flow %s start did not issue an auth_flow_sid cookie\n' "$flow"
    return 1
  }
  csrf="$(reg_json '.result.data.csrfToken // empty')"
  [[ -n "$csrf" ]] || {
    printf 'FAIL flow %s start did not return a csrfToken\n' "$flow"
    return 1
  }
  write_private_value "${flow}_csrf_token" "$csrf"
  reg_note "flow_${flow}_start_attempts=1"

  if [[ -z "$otp_code" ]]; then
    printf 'FAIL set REGISTRATION_RACE_%s_OTP_CODE with the real OTP code delivered to the flow %s contact\n' "$flow" "$flow"
    return 1
  fi
  body="$(jq -cn --arg code "$otp_code" '{code:$code}')"
  reg_call "$flow" otp-verify /otp/verify "$body" "$(reg_new_key "${flow}-otp")" yes
  body=''
  assert_eq 0 "$REG_CURL_EXIT" "reg_${flow}_otp_verify_curl_exit" || return 1
  assert_eq 200 "$REG_HTTP_STATUS" "reg_${flow}_otp_verify_http_status" || return 1
  assert_eq SUCCESS "$(reg_json '.status // "<absent>"')" "reg_${flow}_otp_verify_api_status" || return 1
  reg_note "flow_${flow}_otp_verify_attempts=1"

  trust_json="$(reg_json '.result.data.unregisteredTrusts[0] // empty')"
  [[ -n "$trust_json" ]] || {
    printf 'FAIL flow %s otp/verify returned no unregistered trusts to select\n' "$flow"
    return 1
  }
  account_env="$(printf '%s' "$trust_json" | jq -r '.accountEnv')"
  trust_code="$(printf '%s' "$trust_json" | jq -r '.trustCode')"
  write_private_value "${flow}_account_env" "$account_env"
  write_private_value "${flow}_trust_code" "$trust_code"

  body="$(jq -cn --arg env "$account_env" --arg trust "$trust_code" --arg username "$REGISTRATION_RACE_USERNAME" \
    '{selectedTrust: {accountEnv:$env, trustCode:$trust}, username:$username}')"
  reg_call "$flow" username-check /username/check "$body" - yes
  body=''
  reg_assert_response "reg_${flow}_username_check" 200 SUCCESS USERNAME_CHECK || return 1
  reg_note "flow_${flow}_username_check_attempts=1 (candidate username shared across flow A and flow B)"
}

# Calls /complete for the given flow with its own password. Leaves REG_HTTP_STATUS
# / REG_RAW_BODY populated for the caller to assert, since flow A and flow B
# expect different outcomes (first wins, second is rejected).
reg_complete() {
  local flow="$1"
  local pw_var="REGISTRATION_RACE_${flow}_PASSWORD"
  local password="${!pw_var}"
  local body
  body="$(jq -cn --arg p "$password" '{password:$p, confirmPassword:$p}')"
  reg_call "$flow" complete /complete "$body" "$(reg_new_key "${flow}-complete")" yes
  body=''
}
