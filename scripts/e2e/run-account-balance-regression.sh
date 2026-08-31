#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
MODE="${1:-}"
[[ "$MODE" == offline ]] || { printf 'Usage: bash scripts/e2e/run-account-balance-regression.sh offline\n' >&2; exit 2; }
for c in bash jq mktemp date zip; do command -v "$c" >/dev/null 2>&1 || { printf 'ERROR missing dependency: %s\n' "$c" >&2; exit 2; }; done
TS="$(date +%Y%m%d-%H%M%S)"
PARENT="${E2E_EVIDENCE_ROOT:-$ROOT/target/evidence}/${TS}-bff-e2e-account-balance-regression-offline"
CHILD_ROOT="$PARENT/children"
UPLOAD_ROOT="${E2E_UPLOAD_ROOT:-/mnt/d/Projects/ORSO/ngtpa}"
UPLOAD_FILE="$UPLOAD_ROOT/$(basename "$PARENT").txt"
mkdir -p "$CHILD_ROOT" "$UPLOAD_ROOT"; chmod 700 "$PARENT" "$CHILD_ROOT"
cleanup() { local rc=$?; rm -f "$UPLOAD_FILE"; (cd "$(dirname "$PARENT")" && zip -qr "$UPLOAD_FILE" "$(basename "$PARENT")"); printf 'Upload artifact: %s\n' "$UPLOAD_FILE"; exit "$rc"; }
trap cleanup EXIT
TMP="$(mktemp -d)"; mkdir -p "$TMP/bin" "$TMP/runtime"; trap 'rm -rf "$TMP"; cleanup' EXIT
printf '246810' > "$TMP/runtime/otp"; chmod 600 "$TMP/runtime/otp"
cat > "$TMP/bin/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
count_file="${MOCK_COUNT_FILE:?}"; args_file="${MOCK_ARGS_FILE:?}"
count=0; [[ -f "$count_file" ]] && count="$(cat "$count_file")"; count=$((count+1)); printf '%s' "$count" > "$count_file"
printf '%q ' "$@" >> "$args_file"; printf '\n' >> "$args_file"
out=''; headers=''
while [[ $# -gt 0 ]]; do case "$1" in -o) out="$2"; shift 2;; -D) headers="$2"; shift 2;; -w) shift 2;; *) shift;; esac; done
case "$count" in
  1) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: auth_flow_sid=SYNTHETIC_COOKIE; Path=/api/v1/auth; Secure; HttpOnly\r\n\r\n' > "$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_OTP_DESTINATION","data":{"csrfToken":"SYNTHETIC_CSRF","destinations":[{"destinationId":"SYNTHETIC_DEST","channel":"email","maskedDestination":"SYNTHETIC_MASK"}]}}}' > "$out";;
  2) printf 'HTTP/1.1 200 OK\r\n\r\n' > "$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"VERIFY_OTP","data":{"resendCooldownSeconds":60}}}' > "$out";;
  3) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=SYNTHETIC_PORTAL; Path=/; Secure; HttpOnly\r\n\r\n' > "$headers"; printf '{"success":true,"status":"SUCCESS","result":{"nextStep":"SELECT_ACCOUNT","data":{"csrfToken":"SYNTHETIC_AUTH_CSRF","accounts":[{"accountRef":"SYNTHETIC_ACCOUNT","accountEnv":"SYNTHETIC_ENV"}]}}}' > "$out";;
  4) printf 'HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n' > "$headers"; printf '{"success":true,"status":"SUCCESS","result":{"data":{"asOfDate":"31/08/2026","currencySelection":{"availableCurrencies":["HKD"],"selectedCurrency":"HKD"},"accountOverview":{"displayOptions":{"vestedBalanceVisible":true,"pendingSwitchInVisible":false},"displayAccounts":[],"accountTotals":{"totalBalance":"0.00","pendingSwitchIn":null}},"investmentPortfolio":{"displayOptions":{"fxRateVisible":false},"fundLevelView":{"displayAccounts":[]},"subDisplayAccountLevelView":{"displayAccounts":[]}}}},"messages":[],"errors":[]}' > "$out";;
  5) printf 'HTTP/1.1 200 OK\r\nSet-Cookie: portal_sid=; Max-Age=0; Path=/\r\n\r\n' > "$headers"; printf '{"success":true,"status":"SUCCESS","result":null,"messages":[],"errors":[]}' > "$out";;
  *) exit 98;;
esac
printf '200 0.001000'
MOCK
chmod 755 "$TMP/bin/curl"
OUTPUT="$PARENT/010-child-output.txt"
set +e
MOCK_COUNT_FILE="$TMP/count" MOCK_ARGS_FILE="$TMP/requests" PATH="$TMP/bin:$PATH" \
BFF_BASE_URL=http://mock E2E_EVIDENCE_ROOT="$CHILD_ROOT" E2E_ENVIRONMENT=offline \
E2E_SCENARIO_PACK=account-balance E2E_SCENARIO_ID=list-authenticated E2E_BOOTSTRAP_MODE=anonymous \
E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_VERIFICATION=true E2E_ALLOW_AUTHENTICATED_LOGOUT=true \
E2E_OFFLINE_TEST_MODE=true E2E_TEST_OTP_CODE_FILE="$TMP/runtime/otp" AUTHENTICATED_ACCOUNT_INDEX=0 \
LOGIN_TEST_USERNAME=synthetic-user LOGIN_TEST_PASSWORD=synthetic-password LOGIN_OTP_DESTINATION_INDEX=0 \
bash scripts/e2e/run.sh > "$OUTPUT" 2>&1
rc=$?
set -e
printf 'child_exit=%s\n' "$rc" > "$PARENT/020-exit-codes.txt"
[[ "$rc" -eq 0 ]] || { printf 'status=FAIL\n' > "$PARENT/090-final-status.txt"; exit "$rc"; }
[[ "$(cat "$TMP/count")" == 5 ]] || { printf 'ERROR request count mismatch\n' >&2; exit 1; }
for marker in '/api/v1/auth/login' '/api/v1/auth/login/otp/send' '/api/v1/auth/login/otp/verify' '/api/v1/account-balance' '/api/v1/auth/logout' 'Account-Ref:' 'portal_sid='; do
  grep -Fq -- "$marker" "$TMP/requests" || { printf 'ERROR missing transport marker: %s\n' "$marker" >&2; exit 1; }
done
if grep -R -E -q 'SYNTHETIC_(COOKIE|CSRF|DEST|MASK|PORTAL|AUTH_CSRF|ACCOUNT|ENV)|synthetic-password|246810' "$CHILD_ROOT"; then
  printf 'security_audit=FAIL\n' > "$PARENT/030-security-audit.txt"; exit 1
fi
printf 'security_audit=PASS\n' > "$PARENT/030-security-audit.txt"
printf 'requests=5\nreal_network_requests=0\nhuman_prompts=0\nautomatic_retries=0\n' > "$PARENT/040-summary.txt"
printf 'status=PASS\nmode=offline\nevidence=%s\n' "$PARENT" > "$PARENT/090-final-status.txt"
printf 'ACCOUNT_BALANCE_REGRESSION_OFFLINE PASS\nEvidence: %s\n' "$PARENT"
