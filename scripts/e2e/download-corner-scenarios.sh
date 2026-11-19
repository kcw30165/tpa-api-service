#!/usr/bin/env bash
# shellcheck disable=SC2016
set +x
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091 # The library is sourced relative to ROOT.
source "$ROOT/lib/result-records.sh"

BASE_URL_VALUE="${BASE_URL:-}"
SESSION_COOKIE_VALUE="${SESSION_COOKIE:-}"
CSRF_TOKEN_VALUE="${CSRF_TOKEN:-}"
ACCOUNT_REF_VALUE="${ACCOUNT_REF:-}"
for secret_name in SESSION_COOKIE CSRF_TOKEN ACCOUNT_REF; do
  [[ -v "$secret_name" ]] && export -n "${secret_name?}"
done
unset SESSION_COOKIE CSRF_TOKEN ACCOUNT_REF secret_name

[[ -n "$BASE_URL_VALUE" && -n "$SESSION_COOKIE_VALUE" \
  && -n "$CSRF_TOKEN_VALUE" && -n "$ACCOUNT_REF_VALUE" ]] || {
  printf 'ERROR download-corner requires the authenticated post-login request contract\n' >&2
  exit 2
}

evidence_dir="${E2E_RUN_EVIDENCE_DIR:-}"
if [[ -n "$evidence_dir" && ( ! -d "$evidence_dir" || -L "$evidence_dir" ) ]]; then
  printf 'ERROR download-corner evidence directory is unavailable\n' >&2
  exit 2
fi

response_dir="${E2E_PRIVATE_STATE_DIR:-}"
owns_response_dir=false
if [[ -n "$response_dir" ]]; then
  if [[ ! -d "$response_dir" || -L "$response_dir" \
    || "$(stat -c '%a' "$response_dir" 2>/dev/null)" != 700 ]]; then
    printf 'ERROR download-corner private runtime state is unavailable\n' >&2
    exit 2
  fi
else
  response_dir="$(mktemp -d "${TMPDIR:-/tmp}/download-corner-e2e.XXXXXX")" || {
    printf 'ERROR could not create private Download Corner response state\n' >&2
    exit 2
  }
  chmod 700 "$response_dir" || {
    rmdir -- "$response_dir" 2>/dev/null || true
    printf 'ERROR could not protect private Download Corner response state\n' >&2
    exit 2
  }
  owns_response_dir=true
fi

response_prefix="$response_dir/download-corner-${BASHPID}"
english_body="${response_prefix}-en.json"
chinese_body="${response_prefix}-zh-HK.json"
english_error="${response_prefix}-en-curl-error"
chinese_error="${response_prefix}-zh-HK-curl-error"
summary_file="${evidence_dir:+$evidence_dir/040-download-corner-summary.txt}"

# shellcheck disable=SC2329 # Invoked by the EXIT trap below.
cleanup_owned_response_state() {
  if [[ "$owns_response_dir" == true ]]; then
    rm -f -- "$english_body" "$chinese_body" "$english_error" "$chinese_error"
    rmdir -- "$response_dir" 2>/dev/null || true
  fi
}
trap cleanup_owned_response_state EXIT

DC_HOOK_HTTP_STATUS=''
ENGLISH_HTTP_STATUS=''
CHINESE_HTTP_STATUS=''

dc_hook_description() {
  case "${1-}" in
    en.http-status|zh-HK.http-status)
      printf '%s' 'Download Corner response returns HTTP status 200.'
      ;;
    en.envelope|zh-HK.envelope)
      printf '%s' 'result.data.categories is an array.'
      ;;
    en.category-shape|zh-HK.category-shape)
      printf '%s' 'Every category has a non-blank categoryName and an items array.'
      ;;
    en.item-shape|zh-HK.item-shape)
      printf '%s' 'Every item has non-blank itemName, itemLink, and fileName values.'
      ;;
    en.no-seq-env|zh-HK.no-seq-env)
      printf '%s' 'result.data contains no seq, categorySeq, itemSeq, or env keys.'
      ;;
    language-category-count)
      printf '%s' 'English and zh-HK responses contain the same number of categories.'
      ;;
    language-item-count)
      printf '%s' 'English and zh-HK responses contain the same item count at each category position.'
      ;;
    *) return 1 ;;
  esac
}

dc_result_start() {
  local identifier="${1-}" description
  [[ "$#" -eq 1 ]] || return 0
  description="$(dc_hook_description "$identifier")" || return 0
  e2e_result_start scenario download-corner "$identifier" "$description" || :
}

dc_result_finish() {
  local identifier="${1-}" status="${2-}" reason_code="${3-}"
  local description
  local -a fields=()

  [[ "$#" -ge 2 && "$#" -le 3 ]] || return 0
  description="$(dc_hook_description "$identifier")" || return 0
  fields+=("description=$description" "status=$status")
  if [[ -n "$reason_code" ]]; then fields+=("reason_code=$reason_code"); fi
  if [[ "$identifier" == en.* || "$identifier" == zh-HK.* ]]; then
    if [[ "$DC_HOOK_HTTP_STATUS" =~ ^[1-5][0-9]{2}$ ]]; then
      fields+=("actual_http_status=$DC_HOOK_HTTP_STATUS")
    fi
    if [[ "$identifier" == *.http-status ]]; then fields+=(expected_http_status=200); fi
  fi
  e2e_result_finish scenario download-corner "$identifier" "${fields[@]}" || :
}

fetch_language() {
  local language="$1" body_file="$2" error_file="$3" headers_fd status curl_rc
  exec {headers_fd}< <(
    printf 'Cookie: portal_sid=%s\n' "$SESSION_COOKIE_VALUE"
    printf 'X-XSRF-TOKEN: %s\n' "$CSRF_TOKEN_VALUE"
    printf 'Account-Ref: %s\n' "$ACCOUNT_REF_VALUE"
  )
  local -a curl_args=(
    --silent --show-error
    --connect-timeout "${E2E_CONNECT_TIMEOUT_SECONDS:-5}"
    --max-time "${E2E_REQUEST_TIMEOUT_SECONDS:-20}"
    -X GET
    -H "Accept-Language: $language"
    -H "@/dev/fd/$headers_fd"
    -o "$body_file"
    -w '%{http_code}'
  )
  [[ "${E2E_TLS_VERIFY:-true}" == true ]] || curl_args+=(--insecure)
  if status="$(curl "${curl_args[@]}" "${BASE_URL_VALUE%/}/api/v1/download-corner" 2>"$error_file")"; then
    curl_rc=0
  else
    curl_rc=$?
  fi
  exec {headers_fd}<&- || curl_rc=1
  [[ "$curl_rc" -eq 0 && "$status" =~ ^[0-9]{3}$ ]] || status=000
  printf '%s\n' "$status"
}

dc_skip_language_checks() {
  local language="$1" check identifier
  for check in envelope category-shape item-shape no-seq-env; do
    identifier="$language.$check"
    dc_result_start "$identifier" || :
    dc_result_finish "$identifier" SKIP HTTP_STATUS_MISMATCH || :
  done
}

dc_record_check() {
  local language="$1" check="$2" body_file="$3" expression="$4"
  local identifier="$language.$check"
  dc_result_start "$identifier" || :
  if jq -e "$expression" "$body_file" >/dev/null 2>&1; then
    checks_passed+=("$check")
    dc_result_finish "$identifier" PASS || :
  else
    checks_failed+=("$check")
    printf 'FAIL download-corner language=%s check=%s\n' "$language" "$check"
    validation_failed=true
    dc_result_finish "$identifier" FAIL CONTRACT_MISMATCH || :
  fi
}

validate_language() {
  local language="$1" status="$2" body_file="$3"
  local passed
  local validation_failed=false
  local -a checks_passed=() checks_failed=()

  DC_HOOK_HTTP_STATUS="$status"
  dc_result_start "$language.http-status" || :
  if [[ "$status" == 200 ]]; then
    checks_passed+=(http-status)
    dc_result_finish "$language.http-status" PASS || :
  else
    checks_failed+=(http-status)
    printf 'FAIL download-corner language=%s check=http-status\n' "$language"
    validation_failed=true
    dc_result_finish "$language.http-status" FAIL HTTP_STATUS_MISMATCH || :
    dc_skip_language_checks "$language"
  fi

  if [[ "$status" == 200 ]]; then
    dc_record_check "$language" envelope "$body_file" '
      (.success == true)
      and (.status == "SUCCESS")
      and ((.result.data.categories | type) == "array")
    '
    dc_record_check "$language" category-shape "$body_file" '
      (.result.data.categories | type) == "array"
      and all(.result.data.categories[];
        ((.categoryName | type) == "string")
        and (.categoryName | test("\\S"))
        and ((.items | type) == "array"))
    '
    dc_record_check "$language" item-shape "$body_file" '
      (.result.data.categories | type) == "array"
      and all(.result.data.categories[];
        ((.items | type) == "array")
        and all(.items[];
          ((.itemName | type) == "string") and (.itemName | test("\\S"))
          and ((.itemLink | type) == "string") and (.itemLink | test("\\S"))
          and ((.fileName | type) == "string") and (.fileName | test("\\S"))))
    '
    dc_record_check "$language" no-seq-env "$body_file" '
      (.result.data | type) == "object"
      and ([.result.data | .. | objects | keys[]]
        | all(. != "seq" and . != "categorySeq" and . != "itemSeq" and . != "env"))
    '
  fi

  if ((${#checks_passed[@]})); then
    passed="$(IFS=,; printf '%s' "${checks_passed[*]}")"
  else
    passed=none
  fi
  local summary="language=$language http=$status checks_passed=$passed"
  if ((${#checks_failed[@]})); then
    local failed
    failed="$(IFS=,; printf '%s' "${checks_failed[*]}")"
    summary+=" checks_failed=$failed"
  fi
  if [[ "$status" != 200 ]]; then summary+=" checks_skipped=envelope,category-shape,item-shape,no-seq-env"; fi
  printf '%s\n' "$summary"
  [[ -z "$summary_file" ]] || printf '%s\n' "$summary" >>"$summary_file"
  [[ "$validation_failed" == false ]]
}

dc_cross_check() {
  local identifier="$1" expression="$2"
  dc_result_start "$identifier" || :
  if [[ "$ENGLISH_HTTP_STATUS" != 200 || "$CHINESE_HTTP_STATUS" != 200 ]]; then
    dc_result_finish "$identifier" SKIP HTTP_STATUS_MISMATCH || :
    printf 'SKIP download-corner check=%s\n' "$identifier"
    return 0
  fi
  if jq -e -s "$expression" "$english_body" "$chinese_body" >/dev/null 2>&1; then
    dc_result_finish "$identifier" PASS || :
    printf 'PASS download-corner check=%s\n' "$identifier"
  else
    overall_rc=1
    dc_result_finish "$identifier" FAIL CONTRACT_MISMATCH || :
    printf 'FAIL download-corner check=%s\n' "$identifier"
  fi
}

ENGLISH_HTTP_STATUS="$(fetch_language en "$english_body" "$english_error")"
CHINESE_HTTP_STATUS="$(fetch_language zh-HK "$chinese_body" "$chinese_error")"
overall_rc=0
validate_language en "$ENGLISH_HTTP_STATUS" "$english_body" || overall_rc=1
validate_language zh-HK "$CHINESE_HTTP_STATUS" "$chinese_body" || overall_rc=1

DC_HOOK_HTTP_STATUS=''
dc_cross_check language-category-count '
  length == 2
  and (.[0].result.data.categories | type) == "array"
  and (.[1].result.data.categories | type) == "array"
  and (.[0].result.data.categories | length)
    == (.[1].result.data.categories | length)
'
dc_cross_check language-item-count '
  length == 2
  and all(.[].result.data.categories[]; (.items | type) == "array")
  and (.[0].result.data.categories | map(.items | length))
    == (.[1].result.data.categories | map(.items | length))
'

exit "$overall_rc"
