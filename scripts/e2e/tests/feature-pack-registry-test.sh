#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/feature-pack-registry.sh"
fail=0
expect_failure(){ local label="$1" value="$2" expected="$3" out; if out="$(resolve_feature_packs "$value" 2>&1)"; then printf 'FAIL %s unexpectedly passed\n' "$label"; fail=1; elif [[ "$out" != *"$expected"* ]]; then printf 'FAIL %s message=%s\n' "$label" "$out"; fail=1; fi; }
expect_resolved(){ local label="$1" value="$2" expected="$3" out; if ! out="$(resolve_feature_packs "$value")"; then printf 'FAIL %s unexpectedly failed\n' "$label"; fail=1; elif [[ "$out" != "$expected" ]]; then printf 'FAIL %s output changed\n' "$label"; fail=1; fi; }
expect_failure_exact(){ local label="$1" expected="$2" out rc; shift 2; out="$(resolve_feature_packs "$@" 2>&1)"; rc=$?; if [[ "$rc" -eq 0 || "$out" != "$expected" ]]; then printf 'FAIL %s exit=%s output=%s\n' "$label" "$rc" "$out"; fail=1; fi; }
resolved="$(resolve_feature_packs 'investment-choice')" || { printf 'FAIL known pack\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/investment-choice-scenarios.sh" ]] || { printf 'FAIL mapping=%s\n' "$resolved"; fail=1; }
resolved="$(resolve_feature_packs 'download-corner')" || { printf 'FAIL download-corner pack\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/download-corner-scenarios.sh" ]] || { printf 'FAIL download-corner mapping=%s\n' "$resolved"; fail=1; }
resolved="$(resolve_feature_packs 'vesting-scale')" || { printf 'FAIL vesting-scale pack\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/vesting-scale-scenarios.sh" ]] || { printf 'FAIL vesting-scale mapping=%s\n' "$resolved"; fail=1; }
resolved="$(resolve_feature_packs 'investment-choice vesting-scale')" || { printf 'FAIL ordered packs\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/investment-choice-scenarios.sh"$'\n'"$ROOT/scripts/e2e/vesting-scale-scenarios.sh" ]] || {
  printf 'FAIL ordered pack paths changed\n'
  fail=1
}
resolved="$(resolve_feature_packs 'download-corner vesting-scale')" || { printf 'FAIL ordered Download Corner packs\n'; exit 1; }
[[ "$resolved" == "$ROOT/scripts/e2e/download-corner-scenarios.sh"$'\n'"$ROOT/scripts/e2e/vesting-scale-scenarios.sh" ]] || {
  printf 'FAIL Download Corner pack order changed\n'
  fail=1
}
resolved="$(resolve_feature_packs $'investment-choice\n')" || { printf 'FAIL ordered singleton\n'; exit 1; }
[[ "$(printf '%s\n' "$resolved" | wc -l)" -eq 1 ]] || { printf 'FAIL order output\n'; fail=1; }
ordered="$ROOT/scripts/e2e/vesting-scale-scenarios.sh"$'\n'"$ROOT/scripts/e2e/investment-choice-scenarios.sh"
expect_resolved comma-tight 'vesting-scale,investment-choice' "$ordered"
expect_resolved comma-space-after 'vesting-scale, investment-choice' "$ordered"
expect_resolved comma-space-before 'vesting-scale ,investment-choice' "$ordered"
expect_resolved whitespace-separator 'vesting-scale investment-choice' "$ordered"
expect_resolved tab-separator $'vesting-scale\tinvestment-choice' "$ordered"
expect_resolved comma-tab-separator $'vesting-scale,\tinvestment-choice' "$ordered"
expect_failure_exact mixed-duplicate "$ROOT/scripts/e2e/vesting-scale-scenarios.sh"$'\n'"$ROOT/scripts/e2e/investment-choice-scenarios.sh"$'\n''ERROR duplicate feature pack: vesting-scale' 'vesting-scale,investment-choice vesting-scale'
expect_failure_exact leading-comma 'ERROR E2E_FEATURE_PACKS contains an empty element' ',vesting-scale'
expect_failure_exact trailing-comma 'ERROR E2E_FEATURE_PACKS contains an empty element' 'vesting-scale,'
expect_failure_exact doubled-comma 'ERROR E2E_FEATURE_PACKS contains an empty element' 'vesting-scale,,investment-choice'
expect_failure_exact comma-whitespace-comma 'ERROR E2E_FEATURE_PACKS contains an empty element' 'vesting-scale, ,investment-choice'
expect_failure_exact comma-only 'ERROR E2E_FEATURE_PACKS contains an empty element' ','
expect_resolved comma-single 'vesting-scale' "$ROOT/scripts/e2e/vesting-scale-scenarios.sh"
expect_failure_exact unknown-in-comma-list "$ROOT/scripts/e2e/investment-choice-scenarios.sh"$'\n''ERROR unknown feature pack: unknown-pack' 'investment-choice,unknown-pack'
expect_failure empty '' 'E2E_FEATURE_PACKS must not be empty'
expect_failure whitespace '   ' 'E2E_FEATURE_PACKS must not be empty'
expect_failure unknown 'unknown-pack' 'unknown feature pack: unknown-pack'
expect_failure injection 'investment-choice;touch /tmp/e2e-injected' 'unknown feature pack:'
expect_failure duplicate 'investment-choice investment-choice' 'duplicate feature pack: investment-choice'
expect_failure_exact duplicate-output "$ROOT/scripts/e2e/investment-choice-scenarios.sh"$'\n''ERROR duplicate feature pack: investment-choice' 'investment-choice investment-choice'
expect_failure_exact empty-output 'ERROR E2E_FEATURE_PACKS must not be empty' ''
expect_failure_exact whitespace-only-output 'ERROR E2E_FEATURE_PACKS must not be empty' $' \t\n '

# shellcheck disable=SC1091
source "$ROOT/scripts/e2e/run-post-login.sh" --library-only
unset E2E_FEATURE_PACKS
out="$(collect_feature_names "${E2E_FEATURE_PACKS:-}" 2>&1)"
[[ "$?" -ne 0 && "$out" == 'ERROR E2E_FEATURE_PACKS must not be empty' ]] || { printf 'FAIL unset input behavior changed\n'; fail=1; }
login_marker_dir="$(mktemp -d)" || { printf 'FAIL unable to create login marker directory\n'; exit 1; }
login_marker="$login_marker_dir/login-calls"
trap 'rm -f "$login_marker"; rmdir "$login_marker_dir"' EXIT
orchestrator_login(){ printf 'called\n' >>"$login_marker"; return 0; }
run_fake_login_positive_control(){ orchestrator_login; }
run_fake_login_positive_control
if [[ -s "$login_marker" ]]; then
  printf 'PASS login marker positive control\n'
else
  printf 'FAIL login marker positive control\n'
  fail=1
fi
: > "$login_marker"
out="$(E2E_FEATURE_PACKS=',vesting-scale' post_login_main 2>&1)"
[[ "$?" -ne 0 && "$out" == *'E2E_FEATURE_PACKS contains an empty element'* && ! -s "$login_marker" ]] || {
  printf 'FAIL empty-element rejection did not stop before login\n'
  fail=1
}
[[ ! -e /tmp/e2e-injected ]] || { printf 'FAIL arbitrary command executed\n'; fail=1; rm -f /tmp/e2e-injected; }
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS feature pack registry validation\n'
