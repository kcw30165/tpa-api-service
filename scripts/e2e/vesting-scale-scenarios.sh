#!/usr/bin/env bash
# shellcheck disable=SC2016
set +x
set -Eeuo pipefail
umask 077

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/e2e/lib/feature-pack-registry.sh
source "$ROOT/lib/feature-pack-registry.sh"

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
  printf 'ERROR vesting-scale requires the authenticated post-login request contract\n' >&2
  exit 2
}

absent_fields_text="$(resolve_vesting_scale_absent_fields)" || exit 2
declare -a absent_fields=()
if [[ -n "$absent_fields_text" ]]; then
  mapfile -t absent_fields <<<"$absent_fields_text"
fi

evidence_dir="${E2E_RUN_EVIDENCE_DIR:-}"
if [[ -n "$evidence_dir" && ( ! -d "$evidence_dir" || -L "$evidence_dir" ) ]]; then
  printf 'ERROR vesting-scale evidence directory is unavailable\n' >&2
  exit 2
fi

response_dir="${E2E_PRIVATE_STATE_DIR:-}"
owns_response_dir=false
if [[ -n "$response_dir" ]]; then
  if [[ ! -d "$response_dir" || -L "$response_dir" \
    || "$(stat -c '%a' "$response_dir" 2>/dev/null)" != 700 ]]; then
    printf 'ERROR vesting-scale private runtime state is unavailable\n' >&2
    exit 2
  fi
else
  response_dir="$(mktemp -d "${TMPDIR:-/tmp}/vesting-scale-e2e.XXXXXX")" || {
    printf 'ERROR could not create private vesting-scale response state\n' >&2
    exit 2
  }
  chmod 700 "$response_dir" || {
    rmdir -- "$response_dir" 2>/dev/null || true
    printf 'ERROR could not protect private vesting-scale response state\n' >&2
    exit 2
  }
  owns_response_dir=true
fi

response_prefix="$response_dir/vesting-scale-${BASHPID}"
english_body="${response_prefix}-en.json"
chinese_body="${response_prefix}-zh-HK.json"
english_error="${response_prefix}-en-curl-error"
chinese_error="${response_prefix}-zh-HK-curl-error"
summary_file="${evidence_dir:+$evidence_dir/040-vesting-scale-summary.txt}"

# shellcheck disable=SC2329 # Invoked by the EXIT trap below.
cleanup_owned_response_state() {
  if [[ "$owns_response_dir" == true ]]; then
    rm -f -- "$english_body" "$chinese_body" "$english_error" "$chinese_error"
    rmdir -- "$response_dir" 2>/dev/null || true
  fi
}
trap cleanup_owned_response_state EXIT

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
  if status="$(curl "${curl_args[@]}" "${BASE_URL_VALUE%/}/api/v1/vesting-scale" 2>"$error_file")"; then
    curl_rc=0
  else
    curl_rc=$?
  fi
  exec {headers_fd}<&- || curl_rc=1
  [[ "$curl_rc" -eq 0 && "$status" =~ ^[0-9]{3}$ ]] || status=000
  printf '%s\n' "$status"
}

record_check() {
  local language="$1" body_file="$2" name="$3" expression="$4"
  if jq -e "$expression" "$body_file" >/dev/null 2>&1; then
    checks_passed+=("$name")
  else
    checks_failed+=("$name")
    printf 'FAIL vesting-scale language=%s check=%s\n' "$language" "$name"
    validation_failed=true
  fi
}

check_absent_field() {
  local body_file="$1" field="$2"
  jq -e --arg field "$field" '
    if $field == "vestingScale" then
      (.result.data | has($field) | not)
    elif any([
      "memberCategory", "dateJoinedCompany", "dateJoinedScheme", "employmentDate"
    ][]; . == $field) then
      (.result.data.overview.member | has($field) | not)
    else
      all(.result.data.overview.plans[]; has($field) | not)
    end
  ' "$body_file" >/dev/null 2>&1
}

validate_response() {
  local language="$1" status="$2" body_file="$3"
  local types_count plans_count vesting_count passed absent_result field
  local validation_failed=false absent_failed=false
  local -a checks_passed=() checks_failed=()

  if [[ "$status" == 200 ]]; then
    checks_passed+=(http-status)
  else
    checks_failed+=(http-status)
    printf 'FAIL vesting-scale language=%s check=http-status\n' "$language"
    validation_failed=true
  fi

  record_check "$language" "$body_file" envelope \
    '(.success == true) and (.status == "SUCCESS")'
  record_check "$language" "$body_file" planTypes-array \
    '(.result.data.planTypes | type) == "array"'
  record_check "$language" "$body_file" overview-member-object \
    '(.result.data.overview.member | type) == "object"'
  record_check "$language" "$body_file" overview-plans-array \
    '(.result.data.overview.plans | type) == "array"'
  record_check "$language" "$body_file" plan-cross-reference '
    . as $root
    | [$root.result.data.overview.plans[].planNo] as $overview_plan_nos
    | ($root.result.data.planTypes
      | map(.planNo as $plan_no
        | ($plan_no | type == "string" and length > 0)
          and ($overview_plan_nos | index($plan_no) != null))
      | all)
  '
  record_check "$language" "$body_file" vesting-scale-shape '
    (.result.data | has("vestingScale") | not)
    or ((.result.data.vestingScale | type) == "object"
      and (.result.data.vestingScale.plans | type) == "array")
  '
  record_check "$language" "$body_file" vesting-plan-reference '
    . as $root
    | if ($root.result.data | has("vestingScale") | not) then true
      else
        [$root.result.data.planTypes[].planNo] as $plan_type_nos
        | ($root.result.data.vestingScale.plans
          | map(. as $plan
            | ($plan.planNo | type == "string" and length > 0)
              and ($plan_type_nos | index($plan.planNo) != null)
              and ($plan | has("scales"))
              and ($plan.scales == null or ($plan.scales | type) == "array"))
          | all)
      end
  '
  record_check "$language" "$body_file" completed-years-order '
    . as $root
    | if ($root.result.data | has("vestingScale") | not) then true
      else
        ($root.result.data.vestingScale.plans
          | map(.scales as $scales
            | if $scales == null then true
              elif ($scales | type) == "array" then
                ($scales | map((.completedYears | type) == "number") | all)
                and ([$scales | to_entries[] | select(.key > 0)
                  | .value.completedYears >= $scales[.key - 1].completedYears] | all)
              else false end)
          | all)
      end
  '
  record_check "$language" "$body_file" member-dates '
    def valid_optional_date($object; $key):
      if ($object | has($key)) then
        ($object[$key] == null
          or (($object[$key] | type) == "string"
            and ($object[$key] | test("^[0-9]{2}/[0-9]{2}/[0-9]{4}$"))))
      else true end;
    . as $root
    | (["dateJoinedCompany", "dateJoinedScheme", "employmentDate"]
      | map(valid_optional_date($root.result.data.overview.member; .))
      | all)
  '
  record_check "$language" "$body_file" plan-dates '
    def valid_optional_date($object; $key):
      if ($object | has($key)) then
        ($object[$key] == null
          or (($object[$key] | type) == "string"
            and ($object[$key] | test("^[0-9]{2}/[0-9]{2}/[0-9]{4}$"))))
      else true end;
    . as $root
    | ($root.result.data.overview.plans
      | map(. as $plan
        | (["planEffectiveDate", "dateOfJoiningPlan"]
          | map(valid_optional_date($plan; .))
          | all))
      | all)
  '

  if ((${#absent_fields[@]})); then
    for field in "${absent_fields[@]}"; do
      if check_absent_field "$body_file" "$field"; then
        checks_passed+=("absent-$field")
      else
        checks_failed+=("absent-$field")
        printf 'FAIL vesting-scale language=%s check=absent-%s\n' "$language" "$field"
        validation_failed=true
        absent_failed=true
      fi
    done
    if [[ "$absent_failed" == true ]]; then absent_result=failed; else absent_result=passed; fi
  else
    absent_result=none
  fi

  types_count="$(jq -r 'if (.result.data.planTypes | type) == "array" then (.result.data.planTypes | length) else 0 end' "$body_file" 2>/dev/null)" || types_count=0
  plans_count="$(jq -r 'if (.result.data.overview.plans | type) == "array" then (.result.data.overview.plans | length) else 0 end' "$body_file" 2>/dev/null)" || plans_count=0
  vesting_count="$(jq -r 'if (.result.data.vestingScale.plans | type) == "array" then (.result.data.vestingScale.plans | length) else 0 end' "$body_file" 2>/dev/null)" || vesting_count=0
  ((${#checks_passed[@]})) && passed="$(IFS=,; printf '%s' "${checks_passed[*]}")" || passed=none

  local summary="language=$language http=$status planTypes=$types_count plans=$plans_count vestingScalePlans=$vesting_count checks_passed=$passed absent=$absent_result"
  if ((${#checks_failed[@]})); then
    local failed
    failed="$(IFS=,; printf '%s' "${checks_failed[*]}")"
    summary+=" checks_failed=$failed"
  fi
  printf '%s\n' "$summary"
  if [[ -n "$summary_file" ]]; then
    printf '%s\n' "$summary" >>"$summary_file"
  fi
  [[ "$validation_failed" == false ]]
}

english_status="$(fetch_language en "$english_body" "$english_error")"
chinese_status="$(fetch_language zh-HK "$chinese_body" "$chinese_error")"
overall_rc=0
validate_response en "$english_status" "$english_body" || overall_rc=1
validate_response zh-HK "$chinese_status" "$chinese_body" || overall_rc=1

if jq -e -s '
  length == 2
  and (.[0] as $en | .[1] as $zh
    | ($en.result.data.planTypes | map(.planNo))
        == ($zh.result.data.planTypes | map(.planNo)))
' "$english_body" "$chinese_body" >/dev/null 2>&1; then
  printf 'PASS vesting-scale check=language-plan-sequence\n'
  [[ -z "$summary_file" ]] || printf 'cross_language_plan_sequence=passed\n' >>"$summary_file"
else
  printf 'FAIL vesting-scale check=language-plan-sequence\n'
  [[ -z "$summary_file" ]] || printf 'cross_language_plan_sequence=failed\n' >>"$summary_file"
  overall_rc=1
fi

exit "$overall_rc"
