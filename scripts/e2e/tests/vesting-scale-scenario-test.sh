#!/usr/bin/env bash
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PACK="$ROOT/scripts/e2e/vesting-scale-scenarios.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
umask 077

[[ -f "$PACK" ]] || {
  printf 'FAIL registered vesting-scale scenario script is missing\n'
  exit 1
}

mkdir -p "$tmp/bin" "$tmp/headers" "$tmp/private" "$tmp/evidence"
REAL_JQ="$(command -v jq)"
export REAL_JQ
export JQ_ARGV_LOG="$tmp/jq-argv"
export JQ_ENV_LOG="$tmp/jq-env"
cat >"$tmp/bin/jq" <<'FAKE_JQ'
#!/usr/bin/env bash
printf '%s\n' "$@" >>"$JQ_ARGV_LOG"
env -0 >>"$JQ_ENV_LOG"
exec "$REAL_JQ" "$@"
FAKE_JQ

cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -u
local_args=("$@")
method=GET
headers_file=''
body_file=''
url=''
language=''
while (($#)); do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -H)
      case "$2" in
        @*) headers_file="${2#@}" ;;
        'Accept-Language: '*) language="${2#Accept-Language: }" ;;
      esac
      shift 2
      ;;
    -D) shift 2 ;;
    -o) body_file="$2"; shift 2 ;;
    -w) shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
call_number="$(($(wc -l <"$CALLS_FILE") + 1))"
printf '%s %s %s\n' "$method" "${url#"$BASE_URL"}" "$language" >>"$CALLS_FILE"
printf '%s\n' "${local_args[@]}" >"$ARGS_DIR/args-$call_number"
env -0 >"$ARGS_DIR/env-$call_number"
printf '%s\n' "$FAKE_SCENARIO" >"$ARGS_DIR/scenario-$call_number"
cat "$headers_file" >"$ARGS_DIR/headers-$call_number"
printf 'Accept-Language: %s\n' "$language" >>"$ARGS_DIR/headers-$call_number"
jq --arg scenario "$FAKE_SCENARIO" --arg language "$language" '
  if $scenario == "arrays-empty" then
    .result.data.planTypes = [] | .result.data.overview.plans = [] | .result.data.vestingScale.plans = []
  elif $scenario == "missing-section" then del(.result.data.vestingScale)
  elif $scenario == "null-scales" then .result.data.vestingScale.plans[0].scales = null
  elif $scenario == "empty-scales" then .result.data.vestingScale.plans[0].scales = []
  elif $scenario == "success-false" then .success = false
  elif $scenario == "planTypes-not-array" then .result.data.planTypes = {}
  elif $scenario == "orphan-overview-plan" then .result.data.planTypes[0].planNo = "ZZZ"
  elif $scenario == "orphan-vesting-plan" then .result.data.vestingScale.plans[0].planNo = "ZZZ"
  elif $scenario == "descending-years" then
    .result.data.vestingScale.plans[0].scales = [
      {"completedYears":2,"vestingPercentage":20},
      {"completedYears":1,"vestingPercentage":10}
    ]
  elif $scenario == "bad-date" then .result.data.overview.member.dateJoinedCompany = "1995-10-01"
  elif $scenario == "null-date" then .result.data.overview.member.dateJoinedCompany = null
  elif $scenario == "language-mismatch" and $language == "zh-HK" then
    .result.data.planTypes[0].planNo = "OOX"
  elif $scenario == "member-missing" then del(.result.data.overview.member.memberCategory)
  elif $scenario == "member-null" then .result.data.overview.member.memberCategory = null
  elif $scenario == "plan-service-present" then
    .result.data.overview.plans[0].planService = {"years":1,"months":0}
  elif $scenario == "vesting-scale-null" then .result.data.vestingScale = null
  elif $scenario == "plans-empty" then
    .result.data.planTypes = [] | .result.data.overview.plans = [] | .result.data.vestingScale.plans = []
  else .
  end
' "$FIXTURE_FILE" >"$body_file"
printf '%s' "$FAKE_HTTP_STATUS"
FAKE_CURL
chmod 700 "$tmp/bin/jq" "$tmp/bin/curl"
export PATH="$tmp/bin:$PATH"

cat >"$tmp/fixture.json" <<'FIXTURE'
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "planTypes": [
        {"planNo":"OOI","planType":"Synthetic plan OOI"},
        {"planNo":"OOX","planType":"Synthetic plan OOX"},
        {"planNo":"PPI","planType":"Synthetic plan PPI"},
        {"planNo":"PPX","planType":"Synthetic plan PPX"}
      ],
      "overview": {
        "member": {
          "memberCategory":"ACTIVE",
          "dateJoinedCompany":"01/10/1995",
          "dateJoinedScheme":"01/04/2000"
        },
        "plans": [
          {"planNo":"OOI","planEffectiveDate":"01/02/2019","dateOfJoiningPlan":"01/02/2019","memberContributionBasis":null,"employerContributionBasis":null},
          {"planNo":"OOX","planEffectiveDate":"01/03/2019","dateOfJoiningPlan":"01/03/2019","memberContributionBasis":null,"employerContributionBasis":null},
          {"planNo":"PPI","planEffectiveDate":"01/04/2019","dateOfJoiningPlan":"01/04/2019","memberContributionBasis":null,"employerContributionBasis":null},
          {"planNo":"PPX","planEffectiveDate":"01/05/2019","dateOfJoiningPlan":"01/05/2019","memberContributionBasis":null,"employerContributionBasis":null}
        ]
      },
      "vestingScale": {
        "plans": [
          {"planNo":"OOI","scales":[{"completedYears":0,"vestingPercentage":0},{"completedYears":1,"vestingPercentage":10}]},
          {"planNo":"OOX","scales":null},
          {"planNo":"PPI","scales":[]},
          {"planNo":"PPX","scales":[{"completedYears":0,"vestingPercentage":0}]}
        ]
      }
    }
  },
  "messages": [],
  "errors": []
}
FIXTURE

export FIXTURE_FILE="$tmp/fixture.json"
export CALLS_FILE="$tmp/calls"
export ARGS_DIR="$tmp/headers"
export BASE_URL=http://fake-e2e.invalid
export SESSION_COOKIE=session-cookie-sentinel
export CSRF_TOKEN=csrf-token-sentinel
export ACCOUNT_REF=account-reference-sentinel
export ACCEPT_LANGUAGE=fr
export E2E_CONNECT_TIMEOUT_SECONDS=1
export E2E_REQUEST_TIMEOUT_SECONDS=1

fail=0
assert_private_transport() {
  local index value
  [[ "$(wc -l <"$CALLS_FILE")" == 2 ]] || {
    printf 'FAIL expected exactly two HTTP calls\n'
    fail=1
  }
  [[ "$(sed -n '1p' "$CALLS_FILE")" == 'GET /api/v1/vesting-scale en' \
    && "$(sed -n '2p' "$CALLS_FILE")" == 'GET /api/v1/vesting-scale zh-HK' ]] || {
    printf 'FAIL request method, path, language, or ordering changed\n'
    fail=1
  }
  for index in 1 2; do
    if ! grep -Fxq 'Cookie: portal_sid=session-cookie-sentinel' "$ARGS_DIR/headers-$index" \
      || ! grep -Fxq 'X-XSRF-TOKEN: csrf-token-sentinel' "$ARGS_DIR/headers-$index" \
      || ! grep -Fxq 'Account-Ref: account-reference-sentinel' "$ARGS_DIR/headers-$index"; then
      printf 'FAIL authenticated headers missing\n'
      fail=1
    fi
    if [[ "$index" == 1 ]]; then
      grep -Fxq 'Accept-Language: en' "$ARGS_DIR/headers-$index" || {
        printf 'FAIL English language header missing\n'
        fail=1
      }
    else
      grep -Fxq 'Accept-Language: zh-HK' "$ARGS_DIR/headers-$index" || {
        printf 'FAIL Chinese language header missing\n'
        fail=1
      }
    fi
    for value in "$SESSION_COOKIE" "$CSRF_TOKEN" "$ACCOUNT_REF"; do
      if grep -Fq -- "$value" "$ARGS_DIR/args-$index" "$ARGS_DIR/env-$index"; then
        printf 'FAIL authenticated value appeared in curl arguments or environment\n'
        fail=1
        break
      fi
    done
  done
  if grep -Eq 'POST|/auth/logout' "$CALLS_FILE"; then
    printf 'FAIL mutation or logout request was made\n'
    fail=1
  fi
}

run_case() {
  local name="$1" scenario="$2" http_status="$3" absent="$4" expected="$5" rc
  local state_dir="$tmp/private/$name" evidence_dir="$tmp/evidence/$name"
  mkdir -m 700 "$state_dir" "$evidence_dir" "$tmp/headers/$name"
  export E2E_PRIVATE_STATE_DIR="$state_dir"
  export E2E_RUN_EVIDENCE_DIR="$evidence_dir"
  export FAKE_SCENARIO="$scenario"
  export FAKE_HTTP_STATUS="$http_status"
  export VESTING_SCALE_EXPECT_ABSENT="$absent"
  export ARGS_DIR="$tmp/headers/$name"
  : >"$CALLS_FILE"
  : >"$JQ_ARGV_LOG"
  : >"$JQ_ENV_LOG"
  set +e
  timeout 120 bash "$PACK" \
    >"$tmp/$name.log" 2>&1
  rc=$?
  set -e
  if [[ "$expected" == PASS ]]; then
    if [[ "$rc" -ne 0 ]]; then
      printf 'FAIL %s scenario did not pass\n' "$name"
      sed -E \
        -e 's/session-cookie-sentinel/<redacted>/g' \
        -e 's/csrf-token-sentinel/<redacted>/g' \
        -e 's/account-reference-sentinel/<redacted>/g' \
        "$tmp/$name.log"
      fail=1
    fi
  else
    if [[ "$rc" -eq 0 ]]; then
      printf 'FAIL %s invalid scenario passed\n' "$name"
      cat "$tmp/$name.log"
      fail=1
    fi
    grep -Fq "$expected" "$tmp/$name.log" || {
      printf 'FAIL %s did not report the expected assertion\n' "$name"
      fail=1
    }
  fi
  if [[ "$scenario" == unknown-absent || "$scenario" == duplicate-absent ]]; then
    [[ ! -s "$CALLS_FILE" ]] || { printf 'FAIL invalid absent list made requests\n'; fail=1; }
  else
    assert_private_transport
    [[ "$(cat "$ARGS_DIR/scenario-1")" == "$scenario" \
      && "$(cat "$ARGS_DIR/scenario-2")" == "$scenario" ]] || {
      printf 'FAIL fake response scenario was not applied\n'
      fail=1
    }
  fi
  if [[ "$expected" == PASS ]]; then
    grep -Fq 'language=en http=200' "$evidence_dir/040-vesting-scale-summary.txt" || {
      printf 'FAIL English summary evidence missing\n'
      fail=1
    }
    grep -Fq 'language=zh-HK http=200' "$evidence_dir/040-vesting-scale-summary.txt" || {
      printf 'FAIL Chinese summary evidence missing\n'
      fail=1
    }
  fi
  for value in "$SESSION_COOKIE" "$CSRF_TOKEN" "$ACCOUNT_REF" \
    'Synthetic plan OOI' '01/10/1995'; do
    if grep -R -F -q -- "$value" "$tmp/$name.log" "$evidence_dir" "$JQ_ARGV_LOG" "$JQ_ENV_LOG"; then
      printf 'FAIL raw response or authenticated value appeared in output or evidence\n'
      fail=1
      break
    fi
  done
}

export FAKE_HTTP_STATUS=200
run_case full-payload full 200 '' PASS
run_case empty-arrays arrays-empty 200 '' PASS
run_case missing-vesting-scale missing-section 200 '' PASS
run_case null-scales null-scales 200 '' PASS
run_case empty-scales empty-scales 200 '' PASS
run_case non-200 full 503 '' 'FAIL vesting-scale language=en check=http-status'
run_case success-false success-false 200 '' 'FAIL vesting-scale language=en check=envelope'
run_case plan-types-not-array planTypes-not-array 200 '' 'FAIL vesting-scale language=en check=planTypes-array'
run_case orphan-overview-plan orphan-overview-plan 200 '' 'FAIL vesting-scale language=en check=plan-cross-reference'
run_case orphan-vesting-plan orphan-vesting-plan 200 '' 'FAIL vesting-scale language=en check=vesting-plan-reference'
run_case descending-years descending-years 200 '' 'FAIL vesting-scale language=en check=completed-years-order'
run_case bad-date bad-date 200 '' 'FAIL vesting-scale language=en check=member-dates'
run_case null-date null-date 200 '' PASS
run_case language-mismatch language-mismatch 200 '' 'FAIL vesting-scale check=language-plan-sequence'
run_case absent-key-missing member-missing 200 memberCategory PASS
run_case absent-key-null member-null 200 memberCategory 'FAIL vesting-scale language=en check=absent-memberCategory'
run_case absent-key-present full 200 memberCategory 'FAIL vesting-scale language=en check=absent-memberCategory'
run_case absent-plan-field-missing full 200 planService PASS
run_case absent-plan-field-present plan-service-present 200 planService 'FAIL vesting-scale language=en check=absent-planService'
run_case absent-plans-vacuous plans-empty 200 planService PASS
run_case absent-section missing-section 200 vestingScale PASS
run_case absent-section-null vesting-scale-null 200 vestingScale 'FAIL vesting-scale language=en check=vesting-scale-shape'
grep -Fq 'check=absent-vestingScale' "$tmp/absent-section-null.log" || {
  printf 'FAIL explicit-null vestingScale did not fail the strict missing-key assertion\n'
  fail=1
}
run_case unknown-absent unknown-absent 200 notAField 'ERROR VESTING_SCALE_EXPECT_ABSENT'
run_case duplicate-absent duplicate-absent 200 'planService, planService' 'ERROR VESTING_SCALE_EXPECT_ABSENT'

unset E2E_PRIVATE_STATE_DIR E2E_RUN_EVIDENCE_DIR VESTING_SCALE_EXPECT_ABSENT
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
preflight_events="$tmp/preflight-events"
orchestrator_login() { printf 'login\n' >>"$preflight_events"; }
orchestrator_run_feature() { printf 'feature\n' >>"$preflight_events"; }
orchestrator_logout() { printf 'logout\n' >>"$preflight_events"; }
run_preflight_case() {
  local name="$1" absent="$2" rc
  unset E2E_PRIVATE_STATE_DIR E2E_RUN_EVIDENCE_DIR
  export E2E_FEATURE_PACKS=investment-choice
  export E2E_EVIDENCE_ROOT="$tmp/preflight-evidence-$name"
  export VESTING_SCALE_EXPECT_ABSENT="$absent"
  : >"$preflight_events"
  set +e
  post_login_main >"$tmp/preflight-$name.log" 2>&1
  rc=$?
  set -e
  [[ "$rc" -ne 0 && ! -s "$preflight_events" \
    && -z "${E2E_PRIVATE_STATE_DIR:-}" ]] || {
    printf 'FAIL invalid absent-list configuration reached login or created state\n'
    fail=1
  }
  grep -Fq 'ERROR VESTING_SCALE_EXPECT_ABSENT' "$tmp/preflight-$name.log" || {
    printf 'FAIL invalid absent-list configuration was not rejected in preflight\n'
    fail=1
  }
  [[ ! -d "$E2E_EVIDENCE_ROOT" ]] || {
    printf 'FAIL invalid absent-list configuration created evidence before login\n'
    fail=1
  }
}
run_preflight_case unsupported-field notAField
run_preflight_case duplicate-field 'memberCategory, memberCategory'

[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS vesting-scale scenario contract, privacy, and preflight\n'
