#!/usr/bin/env bash
: "${E2E_REPOSITORY_ROOT:=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"

feature_pack_path() {
  local feature_name="$1"
  case "$feature_name" in
    investment-choice) printf '%s/scripts/e2e/investment-choice-scenarios.sh\n' "$E2E_REPOSITORY_ROOT" ;;
    vesting-scale) printf '%s/scripts/e2e/vesting-scale-scenarios.sh\n' "$E2E_REPOSITORY_ROOT" ;;
    *) printf 'ERROR unknown feature pack: %s\n' "$feature_name" >&2; return 1 ;;
  esac
}

resolve_vesting_scale_absent_fields() {
  local configured="${VESTING_SCALE_EXPECT_ABSENT:-}" field
  local -A seen=()
  local -a fields=()
  IFS=, read -r -a fields <<<"$configured"
  for field in "${fields[@]}"; do
    field="$(printf '%s' "$field" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
    [[ -z "$field" ]] && continue
    case "$field" in
      memberCategory|dateJoinedCompany|dateJoinedScheme|employmentDate|\
      planEffectiveDate|dateOfJoiningPlan|planService|\
      memberContributionBasis|employerContributionBasis|vestingScale) ;;
      *)
        printf 'ERROR VESTING_SCALE_EXPECT_ABSENT must contain only unique documented fields\n' >&2
        return 1
        ;;
    esac
    [[ -z "${seen[$field]+x}" ]] || {
      printf 'ERROR VESTING_SCALE_EXPECT_ABSENT must contain only unique documented fields\n' >&2
      return 1
    }
    seen["$field"]=1
    printf '%s\n' "$field"
  done
}

resolve_feature_packs() {
  local configured="${1:-}" feature_name feature_path
  local -A seen=()
  local -a names=()
  [[ -n "${configured//[[:space:]]/}" ]] || {
    printf 'ERROR E2E_FEATURE_PACKS must not be empty\n' >&2
    return 1
  }
  read -r -a names <<<"$configured"
  [[ "${#names[@]}" -gt 0 ]] || {
    printf 'ERROR E2E_FEATURE_PACKS must not be empty\n' >&2
    return 1
  }
  for feature_name in "${names[@]}"; do
    [[ -z "${seen[$feature_name]+x}" ]] || {
      printf 'ERROR duplicate feature pack: %s\n' "$feature_name" >&2
      return 1
    }
    feature_path="$(feature_pack_path "$feature_name")" || return 1
    [[ -f "$feature_path" ]] || {
      printf 'ERROR registered feature pack is unavailable: %s\n' "$feature_name" >&2
      return 1
    }
    seen["$feature_name"]=1
    printf '%s\n' "$feature_path"
  done
}
