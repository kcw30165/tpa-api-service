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
{"result":{"data":{"accounts":[{"accountRef":"account-first"},{"accountRef":"account-second"}]}}}
JSON
expect_eq "$(select_first_account_ref "$tmp/accounts.json")" account-first first_account
cat >"$tmp/no-accounts.json" <<'JSON'
{"result":{"data":{"accounts":[]}}}
JSON
if select_first_account_ref "$tmp/no-accounts.json" >/dev/null 2>&1; then printf 'FAIL no-account unexpectedly succeeded\n'; fail=1; fi
acquire_login_otp(){ printf 'otp-from-replaceable-provider\n'; }
expect_eq "$(acquire_login_otp)" otp-from-replaceable-provider otp_boundary
[[ "$fail" -eq 0 ]] || exit 1
printf 'PASS login selection boundaries\n'
