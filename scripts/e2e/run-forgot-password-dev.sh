#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

usage() {
  cat <<'TXT'
Usage: bash scripts/e2e/run-forgot-password-dev.sh <scenario>

Scenarios:
  resend-cooldown  Start, identity check and details check (one real message), then an immediate resend and a replay of
                   the same Idempotency-Key. Both must be answered with HTTP 429 (cooldown). No further message is sent.
  resend-limit     As above, then the allowed number of accepted resends, each after the cooldown (five more real
                   messages), then one resend beyond the limit and a replay of it. Both must be answered with HTTP 429
                   (limit). Takes about seven minutes and asks for confirmation first.

Runtime inputs (prompted when missing; hidden where personal): FORGOT_PASSWORD_TEST_USERNAME,
FORGOT_PASSWORD_TEST_IDENTITY_VALUE, FORGOT_PASSWORD_TEST_DATE_OF_BIRTH (dd/MM/yyyy) and one of
FORGOT_PASSWORD_TEST_EMAIL or FORGOT_PASSWORD_TEST_MOBILE. Optional: FORGOT_PASSWORD_TEST_IDENTITY_TYPE,
FORGOT_PASSWORD_E2E_ORIGIN (default http://localhost:4200), BFF_BASE_URL (default http://localhost:<SERVER_PORT from .env, else 8888>).
TXT
}

[[ $# -eq 1 ]] || { usage >&2; exit 2; }
scenario="$1"

case "$scenario" in
  resend-cooldown)
    effect_description="one Forgot Password start, identity check and details check (one real message), then an immediate resend and a same-key replay, both expected to be rejected with 429"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_RESEND=true)
    messages=1
    ;;
  resend-limit)
    max_resends="${E2E_FORGOT_PASSWORD_MAX_RESENDS:-5}"
    effect_description="one Forgot Password start, identity check and details check, ${max_resends} accepted resends after the cooldown, then one resend beyond the limit and a same-key replay"
    guards=(E2E_ALLOW_EXTERNAL_EFFECTS=true E2E_ALLOW_OTP_RESEND=true E2E_ALLOW_OTP_RESEND_LIMIT=true)
    messages=$((max_resends + 1))
    ;;
  -h | --help | help) usage; exit 0 ;;
  *) printf 'ERROR: Unknown Forgot Password scenario: %s\n\n' "$scenario" >&2; usage >&2; exit 2 ;;
esac

printf 'Forgot Password retest scenario: %s\n' "$scenario"
printf 'External-effect boundary: %s\n' "$effect_description"
printf 'Real messages requested: %s (sent to the destination configured for the environment)\n' "$messages"

if [[ -z "${FORGOT_PASSWORD_TEST_USERNAME:-}" ]]; then
  read -r -p 'Test username: ' FORGOT_PASSWORD_TEST_USERNAME
fi
if [[ -z "${FORGOT_PASSWORD_TEST_IDENTITY_VALUE:-}" ]]; then
  read -r -s -p 'Identity value (hidden): ' FORGOT_PASSWORD_TEST_IDENTITY_VALUE
  printf '\n'
fi
if [[ -z "${FORGOT_PASSWORD_TEST_DATE_OF_BIRTH:-}" ]]; then
  read -r -s -p 'Date of birth dd/MM/yyyy (hidden): ' FORGOT_PASSWORD_TEST_DATE_OF_BIRTH
  printf '\n'
fi
FORGOT_PASSWORD_TEST_EMAIL="${FORGOT_PASSWORD_TEST_EMAIL:-}"
FORGOT_PASSWORD_TEST_MOBILE="${FORGOT_PASSWORD_TEST_MOBILE:-}"
if [[ -z "$FORGOT_PASSWORD_TEST_EMAIL" && -z "$FORGOT_PASSWORD_TEST_MOBILE" ]]; then
  read -r -p 'Contact for the OTP: [e]mail or [m]obile: ' contact_kind
  case "$contact_kind" in
    e | E) read -r -s -p 'E-mail (hidden): ' FORGOT_PASSWORD_TEST_EMAIL; printf '\n' ;;
    m | M) read -r -s -p 'Mobile (hidden): ' FORGOT_PASSWORD_TEST_MOBILE; printf '\n' ;;
    *) printf 'ERROR: answer e or m.\n' >&2; exit 2 ;;
  esac
fi
[[ -n "$FORGOT_PASSWORD_TEST_USERNAME" && -n "$FORGOT_PASSWORD_TEST_IDENTITY_VALUE" && -n "$FORGOT_PASSWORD_TEST_DATE_OF_BIRTH" ]] || {
  printf 'ERROR: username, identity value and date of birth are required.\n' >&2
  exit 2
}

if [[ "$scenario" == resend-limit && "${FORGOT_PASSWORD_E2E_CONFIRM:-}" != yes ]]; then
  printf 'This sends %s real messages and takes about %s minutes.\n' "$messages" "$(((messages * 62 + 59) / 60))"
  read -r -p 'Type SEND to continue: ' answer
  [[ "$answer" == SEND ]] || { printf 'Cancelled; nothing was sent.\n' >&2; exit 2; }
fi

default_port="$(grep -h -E '^SERVER_PORT=' .env 2>/dev/null | tail -n 1 | cut -d= -f2- | tr -d '[:space:]' || true)"
export BFF_BASE_URL="${BFF_BASE_URL:-http://localhost:${default_port:-8888}}"
export E2E_ENVIRONMENT="${E2E_ENVIRONMENT:-local}"
export E2E_EVIDENCE_ROOT="${E2E_EVIDENCE_ROOT:-$ROOT/target/evidence}"
export E2E_BOOTSTRAP_MODE=anonymous
export E2E_SCENARIO_PACK=forgot-password
export E2E_SCENARIO_ID="$scenario"
export ACCEPT_LANGUAGE="${ACCEPT_LANGUAGE:-en}"
export REQUEST_ID_PREFIX="${REQUEST_ID_PREFIX:-fp-retest-$scenario}"
export FORGOT_PASSWORD_TEST_USERNAME FORGOT_PASSWORD_TEST_IDENTITY_VALUE FORGOT_PASSWORD_TEST_DATE_OF_BIRTH
export FORGOT_PASSWORD_TEST_EMAIL FORGOT_PASSWORD_TEST_MOBILE
for assignment in "${guards[@]}"; do export "$assignment"; done

set +e
bash scripts/e2e/run.sh
rc=$?
set -e
unset FORGOT_PASSWORD_TEST_USERNAME FORGOT_PASSWORD_TEST_IDENTITY_VALUE FORGOT_PASSWORD_TEST_DATE_OF_BIRTH
unset FORGOT_PASSWORD_TEST_EMAIL FORGOT_PASSWORD_TEST_MOBILE
exit "$rc"
