#!/usr/bin/env bash

# These sourced-library outputs are consumed by the calling scenario script.
# shellcheck disable=SC2034
CIM_GENERATION_STATUS=''
CIM_GENERATION_REASON=''
CIM_GENERATED_PAYLOAD=''
CIM_GENERATED_SEED=''
CIM_GENERATED_INTERVAL=''
CIM_GENERATED_FUND_COUNT=0
CIM_GENERATED_PERCENTAGES=''
CIM_RANDOM_INDEX=0

cim_resolve_random_seed() {
  local seed="${1:-}"
  if [[ -z "$seed" ]]; then
    seed="$RANDOM"
  fi
  [[ "$seed" =~ ^[0-9]{1,5}$ ]] || return 1
  seed=$((10#$seed))
  ((seed <= 32767)) || return 1
  CIM_GENERATED_SEED="$seed"
  RANDOM="$seed"
}

cim_random_index() {
  local count="$1"
  ((count > 0)) || return 1
  CIM_RANDOM_INDEX=$((RANDOM % count))
}

cim_skip_payload() {
  CIM_GENERATION_STATUS=SKIP
  CIM_GENERATION_REASON="$1"
  CIM_GENERATED_PAYLOAD=''
  CIM_GENERATED_FUND_COUNT=0
  CIM_GENERATED_PERCENTAGES=''
  return 2
}

cim_generate_valid_payload() {
  local snapshot="$1" seed="${2:-${CIM_RANDOM_SEED:-}}"
  local scenario interval source_json fund_json source_index
  local units allocation_count remaining slots part fund_code
  local -a sources=() funds=() remaining_funds=() selected_funds=() percentages=()

  CIM_GENERATION_STATUS=''
  CIM_GENERATION_REASON=''
  CIM_GENERATED_PAYLOAD=''
  CIM_GENERATED_SEED=''
  CIM_GENERATED_INTERVAL=''
  CIM_GENERATED_FUND_COUNT=0
  CIM_GENERATED_PERCENTAGES=''

  if ! jq -e 'type == "object" and (.scenario | type == "string")' \
    <<<"$snapshot" >/dev/null 2>&1; then
    cim_skip_payload 'snapshot scenario is missing or invalid'
    return $?
  fi
  scenario="$(jq -r '.scenario' <<<"$snapshot")"
  [[ "$scenario" == NORMAL ]] || {
    cim_skip_payload "snapshot scenario=$scenario does not support a normal submission"
    return $?
  }
  if ! jq -e '.pendingInstructions | type == "array"' <<<"$snapshot" >/dev/null 2>&1; then
    cim_skip_payload 'pendingInstructions is missing or invalid'
    return $?
  fi
  if ! jq -e '.intervalPercentage | type == "number" and floor == . and . >= 1 and . <= 100' \
    <<<"$snapshot" >/dev/null 2>&1; then
    cim_skip_payload 'intervalPercentage is missing or invalid'
    return $?
  fi
  if ! jq -e '.switchSources | type == "array"' <<<"$snapshot" >/dev/null 2>&1; then
    cim_skip_payload 'switchSources is missing or invalid'
    return $?
  fi
  if ! jq -e '.funds | type == "array"' <<<"$snapshot" >/dev/null 2>&1; then
    cim_skip_payload 'funds is missing or invalid'
    return $?
  fi

  interval="$(jq -r '.intervalPercentage' <<<"$snapshot")"
  source_json="$(jq -c '
    [.switchSources[]
      | select(type == "object" and .available == true)
      | .switchSourceCode
      | select(type == "string" and test("\\S") and test("^[^\\r\\n]+$"))]
    | unique
  ' <<<"$snapshot")"
  mapfile -t sources < <(jq -r '.[]' <<<"$source_json")
  ((${#sources[@]} > 0)) || {
    cim_skip_payload 'no eligible switch source (available=true)'
    return $?
  }

  # The normal GET contract contains only available funds and exposes no availability flag.
  fund_json="$(jq -c '
    [.funds[]
      | select(type == "object")
      | .fundCode
      | select(type == "string" and test("\\S") and test("^[^\\r\\n]+$"))]
    | unique
  ' <<<"$snapshot")"
  mapfile -t funds < <(jq -r '.[]' <<<"$fund_json")
  ((${#funds[@]} >= 2)) || {
    cim_skip_payload "only ${#funds[@]} eligible fund; at least 2 are required"
    return $?
  }

  units=$((100 / interval))
  if ((100 % interval != 0)); then
    cim_skip_payload "intervalPercentage=$interval cannot divide 100 into interval-sized units"
    return $?
  fi
  if ((units < 2)); then
    cim_skip_payload "intervalPercentage=$interval cannot form 2 positive allocations totaling 100"
    return $?
  fi
  if ((${#funds[@]} >= 3 && units >= 3)); then
    allocation_count=3
  else
    allocation_count=2
  fi

  cim_resolve_random_seed "$seed" || {
    CIM_GENERATION_STATUS=ERROR
    CIM_GENERATION_REASON='CIM_RANDOM_SEED must be an integer from 0 through 32767'
    return 1
  }
  CIM_GENERATED_INTERVAL="$interval"

  cim_random_index "${#sources[@]}"
  source_index="$CIM_RANDOM_INDEX"
  remaining_funds=("${funds[@]}")
  for ((index = 0; index < allocation_count; index++)); do
    cim_random_index "${#remaining_funds[@]}"
    fund_code="${remaining_funds[$CIM_RANDOM_INDEX]}"
    selected_funds+=("$fund_code")
    local -a next_funds=()
    for candidate in "${remaining_funds[@]}"; do
      [[ "$candidate" == "$fund_code" ]] || next_funds+=("$candidate")
    done
    remaining_funds=("${next_funds[@]}")
  done

  remaining="$units"
  for ((index = 0; index < allocation_count - 1; index++)); do
    slots=$((allocation_count - index - 1))
    cim_random_index "$((remaining - slots))"
    part=$((CIM_RANDOM_INDEX + 1))
    percentages+=("$((part * interval))")
    remaining=$((remaining - part))
  done
  percentages+=("$((remaining * interval))")

  fund_json="$(printf '%s\n' "${selected_funds[@]}" | jq -Rsc 'split("\n")[:-1]')"
  local percentages_json
  percentages_json="$(printf '%s\n' "${percentages[@]}" | jq -Rsc 'split("\n")[:-1] | map(tonumber)')"
  CIM_GENERATED_PAYLOAD="$(
    jq -cn --arg source "${sources[$source_index]}" \
      --argjson funds "$fund_json" --argjson percentages "$percentages_json" \
      '{
        switchSources: [$source],
        allocations: [$funds | to_entries[] | {fundCode: .value, percentage: $percentages[.key]}]
      }'
  )" || {
    CIM_GENERATION_STATUS=ERROR
    CIM_GENERATION_REASON='failed to encode generated operationPayload'
    return 1
  }

  CIM_GENERATION_STATUS=GENERATED
  CIM_GENERATED_FUND_COUNT="$allocation_count"
  CIM_GENERATED_PERCENTAGES="$(jq -r '[.allocations[].percentage] | join(",")' <<<"$CIM_GENERATED_PAYLOAD")"
}

cim_build_snapshot_negative_payload() {
  local valid_payload="$1" interval="$2" snapshot="$3" seed="${4:-${CIM_RANDOM_SEED:-}}"
  local source fund first second unavailable suffix source_json fund_json
  local -a valid_funds=() sources=() funds=() remaining_funds=()

  CIM_NEGATIVE_PAYLOAD=''
  CIM_NEGATIVE_CODE=''
  CIM_NEGATIVE_TARGET=''
  CIM_NEGATIVE_SEED=''

  if [[ -n "$valid_payload" ]]; then
    source="$(jq -er '.switchSources[0] | strings | select(length > 0)' <<<"$valid_payload")" || return 1
    mapfile -t valid_funds < <(jq -r '.allocations[].fundCode' <<<"$valid_payload")
  else
    source_json="$(jq -c '
      [.switchSources[]
        | select(type == "object" and .available == true)
        | .switchSourceCode
        | select(type == "string" and test("\\S") and test("^[^\\r\\n]+$"))]
      | unique
    ' <<<"$snapshot")" || return 1
    fund_json="$(jq -c '
      [.funds[]
        | select(type == "object")
        | .fundCode
        | select(type == "string" and test("\\S") and test("^[^\\r\\n]+$"))]
      | unique
    ' <<<"$snapshot")" || return 1
    mapfile -t sources < <(jq -r '.[]' <<<"$source_json")
    mapfile -t funds < <(jq -r '.[]' <<<"$fund_json")
    ((${#funds[@]} > 0)) || return 1

    if ((interval > 1 && ${#sources[@]} > 0 && ${#funds[@]} >= 2)); then
      cim_resolve_random_seed "$seed" || return 1
      CIM_NEGATIVE_SEED="$CIM_GENERATED_SEED"
      cim_random_index "${#sources[@]}"
      source="${sources[$CIM_RANDOM_INDEX]}"
      remaining_funds=("${funds[@]}")
      for ((index = 0; index < 2; index++)); do
        cim_random_index "${#remaining_funds[@]}"
        fund="${remaining_funds[$CIM_RANDOM_INDEX]}"
        valid_funds+=("$fund")
        local -a next_funds=()
        for candidate in "${remaining_funds[@]}"; do
          [[ "$candidate" == "$fund" ]] || next_funds+=("$candidate")
        done
        remaining_funds=("${next_funds[@]}")
      done
      first="${valid_funds[0]}"
      second="${valid_funds[1]}"
      CIM_NEGATIVE_PAYLOAD="$(
        jq -cn --arg source "$source" --arg first "$first" --arg second "$second" \
          '{switchSources:[$source],allocations:[{fundCode:$first,percentage:1},{fundCode:$second,percentage:99}]}'
      )"
      CIM_NEGATIVE_CODE=err.investment-choice.percentage.interval-invalid
      CIM_NEGATIVE_TARGET='allocations[0].percentage'
      return 0
    fi

    if ((interval > 1 && 100 % interval != 0)); then
      ((${#sources[@]} > 0)) || return 1
      cim_resolve_random_seed "$seed" || return 1
      CIM_NEGATIVE_SEED="$CIM_GENERATED_SEED"
      cim_random_index "${#sources[@]}"
      source="${sources[$CIM_RANDOM_INDEX]}"
      cim_random_index "${#funds[@]}"
      fund="${funds[$CIM_RANDOM_INDEX]}"
      CIM_NEGATIVE_PAYLOAD="$(
        jq -cn --arg source "$source" --arg fund "$fund" \
          '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}'
      )"
      CIM_NEGATIVE_CODE=err.investment-choice.percentage.interval-invalid
      CIM_NEGATIVE_TARGET='allocations[0].percentage'
      return 0
    fi
  fi

  if ((interval > 1 && ${#valid_funds[@]} >= 2)); then
    first="${valid_funds[0]}"
    second="${valid_funds[1]}"
    [[ "$first" != "$second" ]] || return 1
    CIM_NEGATIVE_PAYLOAD="$(
      jq -cn --arg source "$source" --arg first "$first" --arg second "$second" \
        '{switchSources:[$source],allocations:[{fundCode:$first,percentage:1},{fundCode:$second,percentage:99}]}'
    )"
    CIM_NEGATIVE_CODE=err.investment-choice.percentage.interval-invalid
    CIM_NEGATIVE_TARGET='allocations[0].percentage'
    return 0
  fi

  if [[ -z "$valid_payload" ]]; then
    ((100 % interval == 0)) || return 1
    cim_resolve_random_seed "$seed" || return 1
    CIM_NEGATIVE_SEED="$CIM_GENERATED_SEED"
    cim_random_index "${#funds[@]}"
    fund="${funds[$CIM_RANDOM_INDEX]}"
  else
    fund="${valid_funds[0]}"
    ((100 % interval == 0)) || {
      CIM_NEGATIVE_PAYLOAD="$(
        jq -cn --arg source "$source" --arg fund "$fund" \
          '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}'
      )"
      CIM_NEGATIVE_CODE=err.investment-choice.percentage.interval-invalid
      CIM_NEGATIVE_TARGET='allocations[0].percentage'
      return 0
    }
  fi

  unavailable=CIM-UNAVAILABLE-SOURCE
  suffix=0
  while jq -e --arg candidate "$unavailable" \
    '.switchSources | any(.[]; .switchSourceCode == $candidate)' <<<"$snapshot" >/dev/null 2>&1; do
    suffix=$((suffix + 1))
    unavailable="CIM-UNAVAILABLE-SOURCE-$suffix"
  done
  CIM_NEGATIVE_PAYLOAD="$(
    jq -cn --arg source "$unavailable" --arg fund "$fund" \
      '{switchSources:[$source],allocations:[{fundCode:$fund,percentage:100}]}'
  )"
  CIM_NEGATIVE_CODE=err.investment-choice.switch-source.unavailable
  CIM_NEGATIVE_TARGET='switchSources[0]'
}

cim_unavailable_reference() {
  local snapshot="$1" candidate=CIM-INVALID-REFERENCE suffix=0
  while jq -e --arg candidate "$candidate" \
    '.pendingInstructions | any(.[]; .referenceNo == $candidate)' <<<"$snapshot" >/dev/null 2>&1; do
    suffix=$((suffix + 1))
    candidate="CIM-INVALID-REFERENCE-$suffix"
  done
  printf '%s' "$candidate"
}
