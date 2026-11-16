#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/feature-pack-registry.sh"
fail=0
expect_failure(){ local label="$1" value="$2" expected="$3" out; if out="$(resolve_feature_packs "$value" 2>&1)"; then printf 'FAIL %s unexpectedly passed\n' "$label"; fail=1; elif [[ "$out" != *"$expected"* ]]; then printf 'FAIL %s message=%s\n' "$label" "$out"; fail=1; fi; }
resolved="$(resolve_feature_packs 'investment-choice')" || { printf 'FAIL known pack\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/investment-choice-scenarios.sh" ]] || { printf 'FAIL mapping=%s\n' "$resolved"; fail=1; }
resolved="$(resolve_feature_packs 'vesting-scale')" || { printf 'FAIL vesting-scale pack\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/vesting-scale-scenarios.sh" ]] || { printf 'FAIL vesting-scale mapping=%s\n' "$resolved"; fail=1; }
resolved="$(resolve_feature_packs 'investment-choice vesting-scale')" || { printf 'FAIL ordered packs\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/investment-choice-scenarios.sh"$'\n'"$ROOT/scripts/e2e/vesting-scale-scenarios.sh" ]] || {
  printf 'FAIL ordered pack paths changed\n'
  fail=1
}
resolved="$(resolve_feature_packs $'investment-choice\n')" || { printf 'FAIL ordered singleton\n'; exit 1; }
[[ "$(printf '%s\n' "$resolved" | wc -l)" -eq 1 ]] || { printf 'FAIL order output\n'; fail=1; }
expect_failure empty '' 'E2E_FEATURE_PACKS must not be empty'
expect_failure whitespace '   ' 'E2E_FEATURE_PACKS must not be empty'
expect_failure unknown 'unknown-pack' 'unknown feature pack: unknown-pack'
expect_failure injection 'investment-choice;touch /tmp/e2e-injected' 'unknown feature pack:'
expect_failure duplicate 'investment-choice investment-choice' 'duplicate feature pack: investment-choice'
[[ ! -e /tmp/e2e-injected ]] || { printf 'FAIL arbitrary command executed\n'; fail=1; rm -f /tmp/e2e-injected; }
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS feature pack registry validation\n'
