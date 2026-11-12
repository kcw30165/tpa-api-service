#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LIBRARY="$ROOT/scripts/e2e/lib/cim-valid-payload.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

# shellcheck source=scripts/e2e/lib/cim-valid-payload.sh
source "$LIBRARY"

make_snapshot() {
  local interval="$1" funds_json="$2" pending_json="${3:-[]}" scenario="${4:-NORMAL}" sources_json="${5:-}"
  [[ -n "$sources_json" ]] || sources_json='[{"switchSourceCode":"SRC-A","switchSource":"Display A","sequence":1,"available":true}]'
  jq -cn --arg scenario "$scenario" --argjson interval "$interval" \
    --argjson funds "$funds_json" --argjson pending "$pending_json" --argjson sources "$sources_json" \
    '{scenario:$scenario,intervalPercentage:$interval,switchSources:$sources,funds:$funds,pendingInstructions:$pending}'
}

make_funds() {
  local codes=("$@") json
  json="$(printf '%s\n' "${codes[@]}" | jq -Rsc 'split("\n")[:-1] | map({fundCode:.,fundName:("Name "+.),sequence:1,riskLevel:"1"})')"
  printf '%s' "$json"
}

assert_valid() {
  local snapshot="$1" seed="$2" expected_count="$3"
  cim_generate_valid_payload "$snapshot" "$seed" >"$tmp/generator.log"
  [[ ! -s "$tmp/generator.log" ]] || {
    printf 'FAIL generator emitted payload or sensitive data to stdout\n'
    exit 1
  }
  [[ "$CIM_GENERATION_STATUS" == GENERATED && "$CIM_GENERATED_FUND_COUNT" == "$expected_count" ]] || {
    printf 'FAIL expected %s generated funds, got status=%s count=%s reason=%s\n' \
      "$expected_count" "$CIM_GENERATION_STATUS" "$CIM_GENERATED_FUND_COUNT" "$CIM_GENERATION_REASON"
    exit 1
  }
  jq -e --argjson interval "$CIM_GENERATED_INTERVAL" --argjson count "$expected_count" \
    --argjson snapshot "$snapshot" '
    (.switchSources | length) == 1
    and (.allocations | length) == $count
    and ([.allocations[].fundCode] | unique | length) == $count
    and ([.allocations[].percentage] | all(. > 0 and (. % $interval == 0)))
    and ([.allocations[].percentage] | add) == 100
    and .switchSources[0] == "SRC-A"
    and all(.allocations[]; .fundCode as $code | any($snapshot.funds[]; .fundCode == $code))
  ' <<<"$CIM_GENERATED_PAYLOAD" >/dev/null || {
    printf 'FAIL generated operationPayload violates snapshot allocation contract\n'
    exit 1
  }
}

funds2="$(make_funds FUND-A FUND-B)"
funds3="$(make_funds FUND-A FUND-B FUND-C FUND-D)"
snapshot="$(make_snapshot 5 "$funds2")"
assert_valid "$snapshot" 123 2
[[ "$(jq -r '.switchSources[0]' <<<"$CIM_GENERATED_PAYLOAD")" != 'Display A' ]] || {
  printf 'FAIL generator used switchSource display text instead of switchSourceCode\n'
  exit 1
}

snapshot="$(make_snapshot 10 "$funds3")"
assert_valid "$snapshot" 234 3

for interval in 1 5 10 20; do
  snapshot="$(make_snapshot "$interval" "$funds3")"
  assert_valid "$snapshot" 345 3
done

snapshot="$(make_snapshot 5 "$funds2")"
cim_generate_valid_payload "$snapshot" 987 >"$tmp/first.log"
first_payload="$CIM_GENERATED_PAYLOAD"
first_seed="$CIM_GENERATED_SEED"
cim_generate_valid_payload "$snapshot" 987 >"$tmp/second.log"
[[ "$CIM_GENERATED_PAYLOAD" == "$first_payload" && "$CIM_GENERATED_SEED" == "$first_seed" ]] || {
  printf 'FAIL configured random seed is not reproducible\n'
  exit 1
}

snapshot="$(make_snapshot 20 "$funds3")"
cim_generate_valid_payload "$snapshot" 765 >"$tmp/negative-base.log"
cim_build_snapshot_negative_payload "$CIM_GENERATED_PAYLOAD" 20 "$snapshot"
[[ "$CIM_NEGATIVE_CODE" == 'err.investment-choice.percentage.interval-invalid' \
  && "$CIM_NEGATIVE_TARGET" == 'allocations[0].percentage' ]] || {
  printf 'FAIL P1 percentage-negative issuance contract changed\n'
  exit 1
}
jq -e '[.allocations[].percentage] == [1,99]' <<<"$CIM_NEGATIVE_PAYLOAD" >/dev/null || {
  printf 'FAIL P1 no longer builds the intended interval-invalid percentage case\n'
  exit 1
}

snapshot="$(make_snapshot 1 "$funds2")"
cim_generate_valid_payload "$snapshot" 876 >"$tmp/source-negative-base.log"
cim_build_snapshot_negative_payload "$CIM_GENERATED_PAYLOAD" 1 "$snapshot"
[[ "$CIM_NEGATIVE_CODE" == 'err.investment-choice.switch-source.unavailable' \
  && "$CIM_NEGATIVE_TARGET" == 'switchSources[0]' ]] || {
  printf 'FAIL P1 unavailable-source issuance case was not preserved for interval 1\n'
  exit 1
}
jq -e --arg source "$(jq -r '.switchSources[0]' <<<"$CIM_NEGATIVE_PAYLOAD")" '
  ([.switchSources[].switchSourceCode] | index($source)) == null
' <<<"$snapshot" >/dev/null || {
  printf 'FAIL P1 negative source unexpectedly matches an eligible source\n'
  exit 1
}

expect_skip() {
  local snapshot="$1" expected="$2"
  if cim_generate_valid_payload "$snapshot" 456 >"$tmp/skip.log"; then
    printf 'FAIL expected SKIP: %s\n' "$expected"
    exit 1
  else
    local result=$?
    [[ "$result" -eq 2 ]] || {
      printf 'FAIL skip returned status %s instead of 2\n' "$result"
      exit 1
    }
  fi
  [[ "$CIM_GENERATION_STATUS" == SKIP && "$CIM_GENERATION_REASON" == "$expected" ]] || {
    printf 'FAIL wrong SKIP reason: expected=%s actual=%s\n' "$expected" "$CIM_GENERATION_REASON"
    exit 1
  }
  [[ ! -s "$tmp/skip.log" ]] || {
    printf 'FAIL skipped generation emitted output\n'
    exit 1
  }
}

one_fund="$(make_funds FUND-A)"
expect_skip "$(make_snapshot 5 "$one_fund")" 'only 1 eligible fund; at least 2 are required'
no_sources='[{"switchSourceCode":"SRC-X","switchSource":"Unavailable","sequence":1,"available":false}]'
expect_skip "$(make_snapshot 5 "$funds2" '[]' NORMAL "$no_sources")" 'no eligible switch source (available=true)'
expect_skip "$(make_snapshot 60 "$funds3")" 'intervalPercentage=60 cannot divide 100 into interval-sized units'
expect_skip "$(make_snapshot 30 "$funds3")" 'intervalPercentage=30 cannot divide 100 into interval-sized units'
expect_skip "$(make_snapshot 100 "$funds3")" 'intervalPercentage=100 cannot form 2 positive allocations totaling 100'
expect_skip "$(make_snapshot 5 "$funds3" '[{"referenceNo":"pending"}]')" 'pending instructions exist; no submission-safe payload generated'
expect_skip "$(make_snapshot 5 "$funds3" '[]' FUNCTION_UNAVAILABLE)" 'snapshot scenario=FUNCTION_UNAVAILABLE does not support a normal submission'
expect_skip "$(make_snapshot 0 "$funds3")" 'intervalPercentage is missing or invalid'
expect_skip "$(make_snapshot null "$funds3")" 'intervalPercentage is missing or invalid'
expect_skip "$(jq -cn --argjson funds "$funds3" '{scenario:"NORMAL",switchSources:[],funds:$funds,pendingInstructions:[]}')" \
  'intervalPercentage is missing or invalid'
expect_skip "$(jq -cn --argjson funds "$funds3" '{scenario:"NORMAL",intervalPercentage:5,funds:$funds,pendingInstructions:[]}')" \
  'switchSources is missing or invalid'
expect_skip "$(jq -cn '{scenario:"NORMAL",intervalPercentage:5,switchSources:[{switchSourceCode:"SRC-A",available:true}],pendingInstructions:[]}')" \
  'funds is missing or invalid'

snapshot="$(make_snapshot 60 "$funds2")"
if cim_generate_valid_payload "$snapshot" 444 >"$tmp/impossible.log"; then
  printf 'FAIL impossible positive allocation interval was generated\n'
  exit 1
fi
cim_build_snapshot_negative_payload '' 60 "$snapshot" 444
[[ "$CIM_NEGATIVE_CODE" == err.investment-choice.percentage.interval-invalid ]] || {
  printf 'FAIL P1 negative interval precheck was lost when no positive split exists\n'
  exit 1
}
jq -e '[.allocations[].percentage] == [1,99]' <<<"$CIM_NEGATIVE_PAYLOAD" >/dev/null || {
  printf 'FAIL P1 fallback did not preserve the interval-negative case\n'
  exit 1
}

snapshot="$(make_snapshot 60 "$one_fund")"
if cim_generate_valid_payload "$snapshot" 444 >"$tmp/one-fund-impossible.log"; then
  printf 'FAIL one-fund interval-60 snapshot unexpectedly generated a valid payload\n'
  exit 1
fi
cim_build_snapshot_negative_payload '' 60 "$snapshot" 444
[[ "$CIM_NEGATIVE_CODE" == err.investment-choice.percentage.interval-invalid \
  && "$CIM_NEGATIVE_TARGET" == 'allocations[0].percentage' ]] || {
  printf 'FAIL one-fund interval-60 P1 did not isolate the interval precheck\n'
  exit 1
}
jq -e '.switchSources == ["SRC-A"] and .allocations == [{fundCode:"FUND-A",percentage:100}]' \
  <<<"$CIM_NEGATIVE_PAYLOAD" >/dev/null || {
  printf 'FAIL one-fund interval-60 P1 did not use a valid source and exact-total allocation\n'
  exit 1
}

no_eligible_sources='[{"switchSourceCode":"SRC-X","switchSource":"Unavailable","sequence":1,"available":false}]'
snapshot="$(make_snapshot 60 "$funds2" '[]' NORMAL "$no_eligible_sources")"
if cim_build_snapshot_negative_payload '' 60 "$snapshot" 444; then
  printf 'FAIL P1 generated a multi-rule negative when interval and source cannot be isolated\n'
  exit 1
else
  [[ "$?" -eq 1 ]] || {
    printf 'FAIL P1 isolation failure did not return a clear skip status\n'
    exit 1
  }
fi

duplicate_funds="$(make_funds FUND-A FUND-A FUND-B)"
snapshot="$(make_snapshot 20 "$duplicate_funds")"
assert_valid "$snapshot" 567 2
[[ "$(jq -r '[.allocations[].fundCode] | unique | length' <<<"$CIM_GENERATED_PAYLOAD")" == 2 ]] || {
  printf 'FAIL duplicate GET fund rows were selected more than once\n'
  exit 1
}

if cim_generate_valid_payload "$(make_snapshot 5 "$funds2")" invalid-seed >"$tmp/invalid-seed.log" 2>&1; then
  printf 'FAIL nonnumeric random seed accepted\n'
  exit 1
else
  [[ "$?" -eq 1 ]] || {
    printf 'FAIL invalid random seed did not fail clearly\n'
    exit 1
  }
fi
if cim_generate_valid_payload "$(make_snapshot 5 "$funds2")" 32768 >"$tmp/out-of-range-seed.log" 2>&1; then
  printf 'FAIL out-of-range random seed accepted\n'
  exit 1
else
  [[ "$?" -eq 1 ]] || {
    printf 'FAIL out-of-range random seed did not fail clearly\n'
    exit 1
  }
fi

for secret in \
  generator-password-sentinel \
  generator-cookie-sentinel \
  generator-csrf-sentinel \
  generator-account-ref-sentinel \
  generator-token-sentinel; do
  if grep -Fq -- "$secret" "$tmp"/*; then
    printf 'FAIL authentication secret appeared in generator output\n'
    exit 1
  fi
done

printf 'PASS snapshot-driven CIM payload generation and deterministic seed\n'
