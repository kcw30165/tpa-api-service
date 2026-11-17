# shellcheck shell=bash
e2e_result__mark() (
  local journal="${1-}"
  local code="${2-}"

  case "$code" in
    APPEND_FAILED|RECORD_REJECTED) ;;
    *) return 0 ;;
  esac

  umask 077
  { printf '%s\n' "$code"; } 2>/dev/null >>"${journal}.failed" || :
  chmod 600 -- "${journal}.failed" >/dev/null 2>&1 || :
)

e2e_result__reject() {
  e2e_result__mark "${1-}" RECORD_REJECTED
  return 2
}

e2e_result__valid_identifier() (
  local value="${1-}"
  LC_ALL=C
  [[ "$value" =~ ^[A-Za-z0-9._-]{1,64}$ ]]
)

e2e_result__valid_description() (
  local value="${1-}"
  LC_ALL=C
  (( ${#value} >= 1 && ${#value} <= 200 )) && [[ "$value" != *[^[:print:]]* ]]
)

e2e_result__valid_reason_code() {
  case "${1-}" in
    ASSERTION_FAILED|HTTP_STATUS_MISMATCH|PUBLIC_ERROR_CODE_MISMATCH|CONTRACT_MISMATCH|PRECONDITION_UNAVAILABLE|FEATURE_DISABLED|INTERRUPTED|NOT_EXECUTED|REPORT_RECORDS_MISSING|CHILD_EXIT_NONZERO)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

e2e_result__safe_string() (
  local value="${1-}"
  LC_ALL=C
  [[ "$value" =~ ^[A-Za-z0-9._:-]{1,128}$ ]]
)

e2e_result__append() (
  local journal="${1-}"
  local record_type="${2-}"
  local event="${3-}"
  local pack="${4-}"
  local identifier="${5-}"
  local payload="${6-}"
  local line_count sequence recorded_at json

  umask 077
  if [[ ! "$journal" == /* ]]; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi

  if ! { : >>"$journal"; } 2>/dev/null; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  if ! chmod 600 -- "$journal" >/dev/null 2>&1; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  if ! line_count="$(wc -l <"$journal" 2>/dev/null)"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  line_count="${line_count//[[:space:]]/}"
  if [[ ! "$line_count" =~ ^[0-9]+$ ]]; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  sequence=$((line_count + 1))

  if ! recorded_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null)"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  if [[ ! "$recorded_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]]; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  if ! json="$(jq -cn \
    --argjson payload "$payload" \
    --argjson sequence "$sequence" \
    --arg event "$event" \
    --arg record_type "$record_type" \
    --arg pack "$pack" \
    --arg identifier "$identifier" \
    --arg recorded_at "$recorded_at" \
    '{
      journal_version: 1,
      seq: $sequence,
      event: $event,
      record_type: $record_type,
      pack: (if $record_type == "lifecycle" then null else $pack end),
      recorded_at_utc: $recorded_at
    }
    | if $record_type == "lifecycle" then . + {step: $identifier}
      elif $record_type == "phase" then . + {phase_id: $identifier}
      else . + {id: $identifier}
      end
    | . + $payload' 2>/dev/null)"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  if ! { printf '%s\n' "$json"; } 2>/dev/null >>"$journal"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  chmod 600 -- "$journal" >/dev/null 2>&1 || {
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  }
  return 0
)

e2e_result_start() (
  local journal="${E2E_RESULT_JOURNAL-}"
  local record_type="${1-}"
  local pack="${2-}"
  local identifier="${3-}"
  local description="${4-}"
  local payload

  [[ -n "$journal" ]] || return 0
  if [[ ! "$journal" == /* ]]; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi

  case "$record_type" in
    lifecycle)
      [[ $# -eq 3 && "$pack" == "-" ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
      case "$identifier" in
        login|otp|account_selection|logout|cleanup) ;;
        *)
          e2e_result__reject "$journal" || :
          return 2
          ;;
      esac
      ;;
    phase|scenario)
      [[ $# -eq 3 || ( "$record_type" == scenario && $# -eq 4 ) ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
      [[ "$pack" != "-" ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
      if ! e2e_result__valid_identifier "$pack" || ! e2e_result__valid_identifier "$identifier"; then
        e2e_result__reject "$journal" || :
        return 2
      fi
      if [[ "$record_type" == scenario ]] && ! e2e_result__valid_description "$description"; then
        e2e_result__reject "$journal" || :
        return 2
      fi
      ;;
    *)
      e2e_result__reject "$journal" || :
      return 2
      ;;
  esac

  if [[ "$record_type" == lifecycle ]]; then
    pack=''
  fi
  if ! payload="$(jq -cn \
    --arg record_type "$record_type" \
    --arg description "$description" \
    'if $record_type == "scenario" then {description: $description} else {} end' 2>/dev/null)"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  e2e_result__append "$journal" "$record_type" start "$pack" "$identifier" "$payload"
)

e2e_result_finish() (
  local journal="${E2E_RESULT_JOURNAL-}"
  local record_type="${1-}"
  local pack="${2-}"
  local identifier="${3-}"
  local argument key value seen_key number
  local description='' status='' outcome=''
  local expected_http_status='null' actual_http_status='null' duration_ms='null'
  local expected_public_error_code='' actual_public_error_code=''
  local request_id='' echoed_request_id='' reason_code=''
  local evidence_refs='' safe_observations='{}'
  local description_seen=0 status_seen=0 outcome_seen=0
  local payload
  local -a seen_keys=()
  local -a refs=()

  [[ -n "$journal" ]] || return 0
  if [[ ! "$journal" == /* ]]; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi

  case "$record_type" in
    lifecycle)
      [[ $# -ge 4 && "$pack" == "-" ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
      case "$identifier" in
        login|otp|account_selection|logout|cleanup) ;;
        *)
          e2e_result__reject "$journal" || :
          return 2
          ;;
      esac
      ;;
    phase|scenario)
      [[ $# -ge 4 && "$pack" != "-" ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
      if ! e2e_result__valid_identifier "$pack" || ! e2e_result__valid_identifier "$identifier"; then
        e2e_result__reject "$journal" || :
        return 2
      fi
      ;;
    *)
      e2e_result__reject "$journal" || :
      return 2
      ;;
  esac

  for argument in "${@:4}"; do
    [[ "$argument" == *=* ]] || {
      e2e_result__reject "$journal" || :
      return 2
    }
    key="${argument%%=*}"
    value="${argument#*=}"
    [[ -n "$key" ]] || {
      e2e_result__reject "$journal" || :
      return 2
    }
    for seen_key in "${seen_keys[@]}"; do
      [[ "$seen_key" != "$key" ]] || {
        e2e_result__reject "$journal" || :
        return 2
      }
    done
    seen_keys+=("$key")

    case "$record_type:$key" in
      lifecycle:outcome|phase:outcome)
        outcome="$value"
        outcome_seen=1
        case "$outcome" in
          PASS|FAIL|SKIP|NOT_RUN|INTERRUPTED) ;;
          *)
            e2e_result__reject "$journal" || :
            return 2
            ;;
        esac
        ;;
      scenario:status)
        status="$value"
        status_seen=1
        case "$status" in
          PASS|FAIL|SKIP) ;;
          *)
            e2e_result__reject "$journal" || :
            return 2
            ;;
        esac
        ;;
      scenario:description)
        description="$value"
        description_seen=1
        if ! e2e_result__valid_description "$description"; then
          e2e_result__reject "$journal" || :
          return 2
        fi
        ;;
      lifecycle:expected_http_status|phase:expected_http_status|scenario:expected_http_status)
        if [[ -n "$value" ]]; then
          if [[ ! "$value" =~ ^[0-9]{1,3}$ ]]; then
            e2e_result__reject "$journal" || :
            return 2
          fi
          number=$((10#$value))
          if (( number < 100 || number > 599 )); then
            e2e_result__reject "$journal" || :
            return 2
          fi
          expected_http_status="$number"
        fi
        ;;
      lifecycle:actual_http_status|phase:actual_http_status|scenario:actual_http_status)
        if [[ -n "$value" ]]; then
          if [[ ! "$value" =~ ^[0-9]{1,3}$ ]]; then
            e2e_result__reject "$journal" || :
            return 2
          fi
          number=$((10#$value))
          if (( number < 100 || number > 599 )); then
            e2e_result__reject "$journal" || :
            return 2
          fi
          actual_http_status="$number"
        fi
        ;;
      lifecycle:duration_ms|phase:duration_ms|scenario:duration_ms)
        if [[ -n "$value" ]]; then
          if [[ ! "$value" =~ ^[0-9]{1,8}$ ]]; then
            e2e_result__reject "$journal" || :
            return 2
          fi
          number=$((10#$value))
          if (( number < 0 || number > 86400000 )); then
            e2e_result__reject "$journal" || :
            return 2
          fi
          duration_ms="$number"
        fi
        ;;
      lifecycle:expected_public_error_code|phase:expected_public_error_code|scenario:expected_public_error_code)
        if e2e_result__safe_string "$value"; then
          expected_public_error_code="$value"
        fi
        ;;
      lifecycle:actual_public_error_code|phase:actual_public_error_code|scenario:actual_public_error_code)
        if e2e_result__safe_string "$value"; then
          actual_public_error_code="$value"
        fi
        ;;
      lifecycle:request_id|phase:request_id|scenario:request_id)
        if e2e_result__safe_string "$value"; then
          request_id="$value"
        fi
        ;;
      lifecycle:echoed_request_id|phase:echoed_request_id|scenario:echoed_request_id)
        if e2e_result__safe_string "$value"; then
          echoed_request_id="$value"
        fi
        ;;
      lifecycle:reason_code|phase:reason_code|scenario:reason_code)
        if [[ -n "$value" ]]; then
          if ! e2e_result__valid_reason_code "$value"; then
            e2e_result__reject "$journal" || :
            return 2
          fi
          reason_code="$value"
        fi
        ;;
      lifecycle:evidence_refs|phase:evidence_refs|scenario:evidence_refs)
        if [[ -n "$value" ]]; then
          if [[ ! "$value" =~ ^[A-Za-z0-9._/-]+(,[A-Za-z0-9._/-]+)*$ || "$value" == *".."* ]]; then
            e2e_result__reject "$journal" || :
            return 2
          fi
          IFS=, read -r -a refs <<<"$value"
          for key in "${refs[@]}"; do
            if [[ "$key" == /* || "$key" == */ || "$key" == *//* || "/$key/" == *"/./"* ]]; then
              e2e_result__reject "$journal" || :
              return 2
            fi
          done
          evidence_refs="$value"
        fi
        ;;
      phase:safe_observations)
        if [[ -n "$value" && "$value" != '{}' ]]; then
          e2e_result__reject "$journal" || :
          return 2
        fi
        ;;
      *)
        e2e_result__reject "$journal" || :
        return 2
        ;;
    esac
  done

  if [[ "$record_type" == scenario ]]; then
    if (( description_seen != 1 || status_seen != 1 )); then
      e2e_result__reject "$journal" || :
      return 2
    fi
    if [[ "$status" == PASS && -n "$reason_code" ]] || \
      [[ ( "$status" == FAIL || "$status" == SKIP ) && -z "$reason_code" ]]; then
      e2e_result__reject "$journal" || :
      return 2
    fi
  elif (( outcome_seen != 1 )); then
    e2e_result__reject "$journal" || :
    return 2
  fi

  if [[ -n "$request_id" && "$request_id" == "$echoed_request_id" ]]; then
    echoed_request_id=''
  fi
  if [[ "$record_type" == lifecycle ]]; then
    pack=''
  fi

  if ! payload="$(jq -cn \
    --arg record_type "$record_type" \
    --arg description "$description" \
    --arg status "$status" \
    --arg outcome "$outcome" \
    --arg expected_public_error_code "$expected_public_error_code" \
    --arg actual_public_error_code "$actual_public_error_code" \
    --arg request_id "$request_id" \
    --arg echoed_request_id "$echoed_request_id" \
    --arg reason_code "$reason_code" \
    --arg evidence_refs "$evidence_refs" \
    --argjson expected_http_status "$expected_http_status" \
    --argjson actual_http_status "$actual_http_status" \
    --argjson duration_ms "$duration_ms" \
    --argjson safe_observations "$safe_observations" \
    '
      def nullable($value): if $value == "" then null else $value end;
      if $record_type == "scenario" then
        {
          description: $description,
          status: $status,
          expected_http_status: $expected_http_status,
          actual_http_status: $actual_http_status,
          expected_public_error_code: nullable($expected_public_error_code),
          actual_public_error_code: nullable($actual_public_error_code),
          duration_ms: $duration_ms,
          request_id: nullable($request_id),
          echoed_request_id: nullable($echoed_request_id),
          reason_code: nullable($reason_code),
          evidence_refs: (if $evidence_refs == "" then [] else ($evidence_refs | split(",")) end)
        }
      else
        {
          outcome: $outcome,
          expected_http_status: $expected_http_status,
          actual_http_status: $actual_http_status,
          expected_public_error_code: nullable($expected_public_error_code),
          actual_public_error_code: nullable($actual_public_error_code),
          duration_ms: $duration_ms,
          request_id: nullable($request_id),
          echoed_request_id: nullable($echoed_request_id),
          reason_code: nullable($reason_code),
          evidence_refs: (if $evidence_refs == "" then [] else ($evidence_refs | split(",")) end)
        }
        + (if $record_type == "phase" then {safe_observations: $safe_observations} else {} end)
      end
    ' 2>/dev/null)"; then
    e2e_result__mark "$journal" APPEND_FAILED
    return 1
  fi
  e2e_result__append "$journal" "$record_type" finish "$pack" "$identifier" "$payload"
)

e2e_result_seconds_to_ms() (
  local seconds="${1-}"
  local integer_part fractional_part fractional_milliseconds rounding_digit result

  [[ $# -eq 1 ]] || return 1
  [[ "$seconds" =~ ^[0-9]+([.][0-9]+)?$ ]] || return 1
  integer_part="${seconds%%.*}"
  [[ ${#integer_part} -le 12 ]] || return 1
  fractional_part=''
  if [[ "$seconds" == *.* ]]; then
    fractional_part="${seconds#*.}"
  fi
  while [[ ${#integer_part} -gt 1 && "${integer_part:0:1}" == 0 ]]; do
    integer_part="${integer_part:1}"
  done
  fractional_part="${fractional_part}0000"
  fractional_part="${fractional_part:0:4}"
  fractional_milliseconds="${fractional_part:0:3}"
  rounding_digit="${fractional_part:3:1}"
  result=$((10#$integer_part * 1000 + 10#$fractional_milliseconds))
  if (( 10#$rounding_digit >= 5 )); then
    result=$((result + 1))
  fi
  printf '%s\n' "$result"
)
