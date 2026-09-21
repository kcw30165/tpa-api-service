#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/scenarios/account-balance/local-stubbed/assertions.sh"
: "${ACCOUNT_BALANCE_BASE_URL:?required}"
: "${ACCOUNT_BALANCE_PORTAL_SID:?required}"
: "${ACCOUNT_BALANCE_ACCOUNT_REF:?required}"
ACCOUNT_BALANCE_SCENARIOS="${ACCOUNT_BALANCE_SCENARIOS:-initial-load,explicit-currency,missing-request-id,missing-language}"
ACCOUNT_BALANCE_EXPLICIT_CURRENCY="${ACCOUNT_BALANCE_EXPLICIT_CURRENCY:-USD}"
ACCOUNT_BALANCE_ENGLISH_LANGUAGE="${ACCOUNT_BALANCE_ENGLISH_LANGUAGE:-en}"
ACCOUNT_BALANCE_EVIDENCE_DIR="${ACCOUNT_BALANCE_EVIDENCE_DIR:-target/evidence/$(date +%Y%m%d-%H%M%S)-bff-e2e-account-balance-local-stubbed}"
mkdir -p "$ACCOUNT_BALANCE_EVIDENCE_DIR"
chmod 700 "$ACCOUNT_BALANCE_EVIDENCE_DIR"
BASE="${ACCOUNT_BALANCE_BASE_URL%/}/api/v1/account/balance"
COOKIE_HEADER="portal_sid=${ACCOUNT_BALANCE_PORTAL_SID}"
SCENARIO=""; RESPONSE_FILE=""; HTTP_STATUS=""

contains_scenario(){ [[ ",${ACCOUNT_BALANCE_SCENARIOS}," == *",$1,"* ]]; }
require_hook(){ local name="$1"; [[ -n "${!name:-}" ]] || { printf 'SKIPPED scenario=%s reason=missing_%s\n' "$SCENARIO" "$name"; return 1; }; }
run_hook(){ local name="$1"; require_hook "$name" || return 1; bash -lc "${!name}" >/dev/null; }
request(){
  local url="$1"; shift
  RESPONSE_FILE="$ACCOUNT_BALANCE_EVIDENCE_DIR/${SCENARIO}.json"
  HTTP_STATUS="$(curl --silent --show-error --output "$RESPONSE_FILE" --write-out '%{http_code}' "$url" "$@")"
  chmod 600 "$RESPONSE_FILE"
}
common_headers(){ printf '%s\n' -H "X-Request-Id:account-balance-e2e-${SCENARIO}" -H "Account-Ref:${ACCOUNT_BALANCE_ACCOUNT_REF}" -H "Cookie:${COOKIE_HEADER}"; }
run_standard(){
  local name="$1"; SCENARIO="$name"; contains_scenario "$name" || return 0
  case "$name" in
    initial-load)
      mapfile -t h < <(common_headers); request "$BASE" "${h[@]}" -H "Accept-Language:${ACCOUNT_BALANCE_ENGLISH_LANGUAGE}"; assert_status 200; assert_json result.data.currencySelection.selectedCurrency ;;
    explicit-currency)
      mapfile -t h < <(common_headers); request "$BASE?currency=${ACCOUNT_BALANCE_EXPLICIT_CURRENCY}" "${h[@]}" -H "Accept-Language:${ACCOUNT_BALANCE_ENGLISH_LANGUAGE}"; assert_status 200; assert_json result.data.currencySelection.selectedCurrency ;;
    missing-request-id)
      request "$BASE" -H "Account-Ref:${ACCOUNT_BALANCE_ACCOUNT_REF}" -H "Cookie:${COOKIE_HEADER}"; assert_status 400; assert_json_equals status VALIDATION_FAILED; assert_json_equals errors.0.targets.0 X-Request-Id ;;
    malformed-request-id)
      request "$BASE" -H 'X-Request-Id: ' -H "Account-Ref:${ACCOUNT_BALANCE_ACCOUNT_REF}" -H "Cookie:${COOKIE_HEADER}"; assert_status 400 ;;
    missing-language)
      mapfile -t h < <(common_headers); request "$BASE" "${h[@]}"; assert_status 200; assert_json result.data ;;
    missing-account-context)
      request "$BASE" -H "X-Request-Id:account-balance-e2e-${SCENARIO}" -H "Cookie:${COOKIE_HEADER}"; [[ "$HTTP_STATUS" == 400 || "$HTTP_STATUS" == 401 || "$HTTP_STATUS" == 403 ]] || fail_assertion shared_context_error ;;
    invalid-account-context)
      request "$BASE" -H "X-Request-Id:account-balance-e2e-${SCENARIO}" -H 'Account-Ref:SYNTHETIC_INVALID_REFERENCE' -H "Cookie:${COOKIE_HEADER}"; [[ "$HTTP_STATUS" == 400 || "$HTTP_STATUS" == 401 || "$HTTP_STATUS" == 403 ]] || fail_assertion shared_context_error ;;
  esac
  printf 'PASS scenario=%s status=%s\n' "$SCENARIO" "$HTTP_STATUS"
}
run_stubbed(){
  local name="$1" hook="$2"; SCENARIO="$name"; contains_scenario "$name" || return 0
  run_hook "$hook" || return 0
  mapfile -t h < <(common_headers); request "$BASE" "${h[@]}" -H "Accept-Language:${ACCOUNT_BALANCE_ENGLISH_LANGUAGE}"
  case "$name" in
    account-invalid) assert_status 422; assert_json_equals status BUSINESS_REJECTED; assert_json_equals errors.0.code err.ab.account.not.exist; assert_json_equals errors.0.type BUSINESS ;;
    member-not-found) assert_status 422; assert_json_equals status BUSINESS_REJECTED; assert_json_equals errors.0.code err.ab.account.mismatch; assert_json_equals errors.0.type BUSINESS ;;
    malformed-cardinality) assert_status 503; assert_json_equals status SYSTEM_ERROR; assert_json_equals errors.0.code err.system.unavailable; assert_json_equals errors.0.type SYSTEM ;;
    malformed-factsheet) assert_status 200; assert_json_absent_recursive factsheetUrl ;;
    metadata-cache-miss-hit|metadata-default-all|metadata-policy-specific|metadata-targeted-fallback) assert_status 200; assert_json result.data ;;
  esac
  printf 'PASS scenario=%s status=%s\n' "$SCENARIO" "$HTTP_STATUS"
}
for scenario in initial-load explicit-currency missing-request-id malformed-request-id missing-language missing-account-context invalid-account-context; do run_standard "$scenario"; done
run_stubbed account-invalid ACCOUNT_BALANCE_SETUP_ACCOUNT_INVALID
run_stubbed member-not-found ACCOUNT_BALANCE_SETUP_MEMBER_NOT_FOUND
run_stubbed metadata-cache-miss-hit ACCOUNT_BALANCE_SETUP_METADATA_CACHE_MISS_HIT
run_stubbed metadata-default-all ACCOUNT_BALANCE_SETUP_METADATA_DEFAULT_ALL
run_stubbed metadata-policy-specific ACCOUNT_BALANCE_SETUP_METADATA_POLICY_SPECIFIC
run_stubbed metadata-targeted-fallback ACCOUNT_BALANCE_SETUP_METADATA_TARGETED_FALLBACK
run_stubbed malformed-cardinality ACCOUNT_BALANCE_SETUP_MALFORMED_CARDINALITY
run_stubbed malformed-factsheet ACCOUNT_BALANCE_SETUP_MALFORMED_FACTSHEET
printf 'E2E_ACCOUNT_BALANCE_LOCAL_STUBBED_PASS evidence_dir=%s\n' "$ACCOUNT_BALANCE_EVIDENCE_DIR"
