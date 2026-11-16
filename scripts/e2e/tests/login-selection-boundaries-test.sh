#!/usr/bin/env bash
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/e2e/lib/authenticated-login-journey.sh"
fail=0
expect_eq(){ [[ "$1" == "$2" ]] || { printf 'FAIL %s expected=%s actual=%s\n' "$3" "$2" "$1"; fail=1; }; }
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cat >"$tmp/destinations.json" <<'JSON'
{"result":{"data":{"destinations":[{"destinationId":"sms-first","type":"SMS"},{"destinationId":"email-first","type":"EMAIL"},{"destinationId":"email-second","type":"EMAIL"}]}}}
JSON
expect_eq "$(select_first_email_destination_id "$tmp/destinations.json")" email-first first_email
cat >"$tmp/channel-destinations.json" <<'JSON'
{"result":{"data":{"destinations":[{"destinationId":"sms-channel","channel":"SMS"},{"destinationId":"email-channel","channel":"EMAIL"}]}}}
JSON
expect_eq "$(select_first_email_destination_id "$tmp/channel-destinations.json")" email-channel first_email_channel_contract
cat >"$tmp/no-email.json" <<'JSON'
{"result":{"data":{"destinations":[{"destinationId":"sms-only","type":"SMS"}]}}}
JSON
if select_first_email_destination_id "$tmp/no-email.json" >"$tmp/no-email.out" 2>&1; then printf 'FAIL no-email unexpectedly succeeded\n'; fail=1; fi
grep -Fq 'FAIL no email OTP destination is available' "$tmp/no-email.out" || { printf 'FAIL no-email message\n'; fail=1; }
cat >"$tmp/accounts.json" <<'JSON'
{"result":{"data":{"accounts":[{"accountRef":"account-first"},{"accountRef":"account-second"},{"accountRef":"account-third"}]}}}
JSON
select_account_ref "$tmp/accounts.json" false '' || { printf 'FAIL default account selection\n'; fail=1; }
expect_eq "$SELECTED_ACCOUNT_REF" account-first first_account
expect_eq "$SELECTED_ACCOUNT_INDEX" 0 first_account_index
expect_eq "$SELECTED_ACCOUNT_COUNT" 3 account_count
select_account_ref "$tmp/accounts.json" true account-second || {
  printf 'FAIL explicit account selection\n'
  fail=1
}
expect_eq "$SELECTED_ACCOUNT_REF" account-second explicit_account
expect_eq "$SELECTED_ACCOUNT_INDEX" 1 explicit_account_index
expect_eq "$SELECTED_ACCOUNT_COUNT" 3 explicit_account_count
[[ "$(jq -r '.result.data.accounts[0].accountRef' "$tmp/accounts.json")" == account-first ]] || {
  printf 'FAIL account response order changed\n'
  fail=1
}
if select_account_ref "$tmp/accounts.json" true account-absent >"$tmp/no-match.out" 2>&1; then
  printf 'FAIL unmatched selector unexpectedly succeeded\n'
  fail=1
fi
grep -Fq 'exactly one login response account' "$tmp/no-match.out" || {
  printf 'FAIL unmatched-selector message\n'
  fail=1
}
if grep -Fq account-absent "$tmp/no-match.out"; then
  printf 'FAIL unmatched selector leaked to output\n'
  fail=1
fi
cat >"$tmp/duplicate-accounts.json" <<'JSON'
{"result":{"data":{"accounts":[{"accountRef":"duplicate-ref"},{"accountRef":"duplicate-ref"}]}}}
JSON
if select_account_ref "$tmp/duplicate-accounts.json" true duplicate-ref >"$tmp/duplicate.out" 2>&1; then
  printf 'FAIL duplicate selector unexpectedly succeeded\n'
  fail=1
fi
if grep -Fq duplicate-ref "$tmp/duplicate.out"; then
  printf 'FAIL duplicate selector leaked to output\n'
  fail=1
fi
cat >"$tmp/malformed-accounts.json" <<'JSON'
{"result":{"data":{"accounts":[{"accountRef":"account-first"},{"policyNo":"missing-reference"}]}}}
JSON
if select_account_ref "$tmp/malformed-accounts.json" true account-first >"$tmp/malformed.out" 2>&1; then
  printf 'FAIL malformed account list unexpectedly succeeded\n'
  fail=1
fi
if select_account_ref "$tmp/malformed-accounts.json" false '' >"$tmp/malformed-default.out" 2>&1; then
  printf 'FAIL malformed default account list unexpectedly succeeded\n'
  fail=1
fi
cat >"$tmp/missing-accounts.json" <<'JSON'
{"result":{"data":{}}}
JSON
if select_account_ref "$tmp/missing-accounts.json" false '' >"$tmp/missing.out" 2>&1; then
  printf 'FAIL missing account list unexpectedly succeeded\n'
  fail=1
fi
cat >"$tmp/no-accounts.json" <<'JSON'
{"result":{"data":{"accounts":[]}}}
JSON
if select_account_ref "$tmp/no-accounts.json" false '' >/dev/null 2>&1; then printf 'FAIL no-account unexpectedly succeeded\n'; fail=1; fi
if select_account_ref "$tmp/accounts.json" true '' >"$tmp/empty-selector.out" 2>&1; then
  printf 'FAIL empty explicit selector unexpectedly succeeded\n'
  fail=1
fi
acquire_login_otp(){ printf 'otp-from-replaceable-provider\n'; }
expect_eq "$(acquire_login_otp)" otp-from-replaceable-provider otp_boundary
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS login selection boundaries\n'
