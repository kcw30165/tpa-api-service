#!/usr/bin/env bash
# Password-validation end-to-end scenarios (curl based).
#
# Run this by hand against DEV. It is NOT run by the build or by an agent.
#
# What it does
#   Issues password-validation tokens and checks the Personal Information PUT
#   gate: wrong password, missing token, changed body, consumed token, replay,
#   and the username throttle.
#
# Safety
#   * Never prints a password, token, cookie, CSRF value or response body.
#     Only scenario names, HTTP statuses and public error codes are printed.
#   * Secrets are read from environment variables and written to a private
#     temporary directory (mode 700) that is removed on exit.
#   * One wrong-password attempt counts toward the downstream account lock
#     (the downstream locks after repeated failures). It is skipped unless you
#     set CONFIRM_WRONG_PASSWORD_ATTEMPT=yes. Use a test account only.
#   * The success scenario performs a REAL Personal Information update. It is
#     skipped unless you set CONFIRM_LIVE_UPDATE=yes. Use current or easily
#     reversible values in REVIEWED_PAYLOAD_FILE.
#   * The account-locked outcome (423) is deliberately not exercised.
#
# Required environment variables (values are never printed)
#   BASE_URL                 e.g. https://host (no trailing slash)
#   SESSION_COOKIE           value of the portal_sid cookie
#   CSRF_TOKEN               value for the X-XSRF-TOKEN header
#   ACCOUNT_REF              value for the Account-Ref header
#   CORRECT_PASSWORD         the test member's real password
#   REVIEWED_PAYLOAD_FILE    JSON file: {"formVersion":...,"applyToAllAccounts":...,"fields":{...}}
#   DIFFERENT_PAYLOAD_FILE   JSON file with the same shape but a changed value
# Optional
#   WRONG_PASSWORD                       required only for the wrong-password scenario
#   CONFIRM_WRONG_PASSWORD_ATTEMPT=yes   enables the wrong-password scenario
#   CONFIRM_LIVE_UPDATE=yes              enables the real update scenario
#   RUN_THROTTLE=yes                     enables the throttle scenario
#   THROTTLE_LIMIT=10                    configured max attempts
#   THROTTLE_WAIT_SECONDS=61             wait for the window to reset first
#   CURL_EXTRA_ARGS                      e.g. "--cacert ca.pem"
#
# Throttle scenario notes
#   The server counts every well-formed issuance request for the username across all
#   sessions. All THROTTLE_LIMIT+1 requests must finish inside one window, so run it
#   from a fast link and do not use the same account at the same time. If the
#   deployment overrides PASSWORD_VALIDATION_RATE_LIMIT_MAX_ATTEMPTS or _WINDOW_SECONDS,
#   set THROTTLE_LIMIT to the maximum and THROTTLE_WAIT_SECONDS to the window plus one.
#
# Exit status: 0 all executed scenarios passed, 1 a scenario failed, 2 bad configuration.
set -uo pipefail
umask 077

ISSUE_PATH=/api/v1/password-validation
UPDATE_PATH=/api/v1/personal-information
TOKEN_HEADER=X-Password-Validation-Token

command -v curl >/dev/null 2>&1 || { echo "curl is required"; exit 2; }
command -v python >/dev/null 2>&1 || { echo "python is required"; exit 2; }

missing=()
for name in BASE_URL SESSION_COOKIE CSRF_TOKEN ACCOUNT_REF CORRECT_PASSWORD REVIEWED_PAYLOAD_FILE DIFFERENT_PAYLOAD_FILE; do
  [[ -n "${!name:-}" ]] || missing+=("$name")
done
if (( ${#missing[@]} )); then
  echo "Missing required environment variables: ${missing[*]}"
  exit 2
fi
for f in "$REVIEWED_PAYLOAD_FILE" "$DIFFERENT_PAYLOAD_FILE"; do
  [[ -r "$f" ]] || { echo "Cannot read a payload file (check the path variables)"; exit 2; }
  python -c 'import json,sys; d=json.load(open(sys.argv[1])); assert isinstance(d,dict)' "$f" 2>/dev/null \
    || { echo "A payload file is not a JSON object"; exit 2; }
done
BASE_URL="${BASE_URL%/}"
THROTTLE_LIMIT="${THROTTLE_LIMIT:-10}"
THROTTLE_WAIT_SECONDS="${THROTTLE_WAIT_SECONDS:-61}"

WORK=$(mktemp -d) || exit 2
chmod 700 "$WORK"
trap 'rm -rf "$WORK"' EXIT

# Headers (including secrets) live in a private file so they never appear in argv.
HDR="$WORK/headers.txt"
{
  printf 'Cookie: portal_sid=%s\n' "$SESSION_COOKIE"
  printf 'X-XSRF-TOKEN: %s\n' "$CSRF_TOKEN"
  printf 'Account-Ref: %s\n' "$ACCOUNT_REF"
  printf 'Content-Type: application/json\n'
  printf 'Accept: application/json\n'
} > "$HDR"

PASSED=0; FAILED=0; SKIPPED=0
LAST_STATUS=""; LAST_CODE=""; LAST_RETRY_AFTER=""; TOKEN_VALUE=""
SEQ=0

# build_issue_body <password-env-name> <payload-file>  -> JSON on stdout
build_issue_body() {
  SCN_PW_NAME="$1" SCN_PAYLOAD="$2" python - <<'PY'
import json, os
name = os.environ["SCN_PW_NAME"]
body = {
    "password": os.environ[name],
    "purpose": "PERSONAL_INFORMATION_UPDATE",
    "operationPayload": json.load(open(os.environ["SCN_PAYLOAD"])),
}
print(json.dumps(body))
PY
}

# call <METHOD> <path> <body-file|-> [token]   sets LAST_STATUS / LAST_CODE / LAST_RETRY_AFTER
call() {
  local method="$1" path="$2" bodyfile="$3" token="${4:-}"
  SEQ=$((SEQ + 1))
  local out="$WORK/resp.$SEQ" hdrs="$WORK/resp-headers.$SEQ" extra="$WORK/extra.$SEQ"
  : > "$extra"
  printf 'X-Request-Id: scn-%s-%s\n' "$$" "$SEQ" >> "$extra"
  [[ -n "$token" ]] && printf '%s: %s\n' "$TOKEN_HEADER" "$token" >> "$extra"
  local args=(-sS -o "$out" -D "$hdrs" -w '%{http_code}' -X "$method"
              -H "@$HDR" -H "@$extra" "$BASE_URL$path")
  [[ "$bodyfile" != "-" ]] && args+=(--data-binary "@$bodyfile")
  # shellcheck disable=SC2086
  LAST_STATUS=$(curl "${args[@]}" ${CURL_EXTRA_ARGS:-} 2>/dev/null) || LAST_STATUS="000"
  # One interpreter start per request: line 1 = public error code, line 2 = token (never printed).
  local parsed
  parsed=$(OUT="$out" python - <<'PY'
import json, os
code = token = ""
try:
    data = json.load(open(os.environ["OUT"]))
    code = (data.get("errors") or [{}])[0].get("code", "") or ""
    token = ((data.get("result") or {}).get("data") or {}).get("token", "") or ""
except Exception:
    pass
print(code)
print(token)
PY
)
  LAST_CODE=$(printf '%s\n' "$parsed" | sed -n 1p)
  TOKEN_VALUE=$(printf '%s\n' "$parsed" | sed -n 2p)
  LAST_RETRY_AFTER=$(grep -i '^retry-after:' "$hdrs" 2>/dev/null | head -1 | tr -d '\r' | cut -d: -f2- | tr -d ' ')
}

check() {  # check <scenario> <expected-status> [expected-code]
  local name="$1" status="$2" code="${3:-}"
  if [[ "$LAST_STATUS" == "$status" && ( -z "$code" || "$LAST_CODE" == "$code" ) ]]; then
    printf 'PASS  %-62s status=%s code=%s\n' "$name" "$LAST_STATUS" "${LAST_CODE:--}"
    PASSED=$((PASSED + 1))
  else
    printf 'FAIL  %-62s expected=%s%s got=%s code=%s\n' "$name" "$status" "${code:+/$code}" "$LAST_STATUS" "${LAST_CODE:--}"
    FAILED=$((FAILED + 1))
  fi
}
skip() { printf 'SKIP  %-62s %s\n' "$1" "$2"; SKIPPED=$((SKIPPED + 1)); }

issue() {  # issue <password-env-name> <payload-file>; token ends up in TOKEN_VALUE
  build_issue_body "$1" "$2" > "$WORK/issue-body.json"
  call POST "$ISSUE_PATH" "$WORK/issue-body.json"
  rm -f "$WORK/issue-body.json"
}

echo "Target host: $(printf '%s' "$BASE_URL" | sed -E 's#^[a-z]+://##; s#/.*$##')"
echo

# 1. Wrong password -> 401, no token. Counts toward the downstream lock.
if [[ "${CONFIRM_WRONG_PASSWORD_ATTEMPT:-}" == "yes" && -n "${WRONG_PASSWORD:-}" ]]; then
  issue WRONG_PASSWORD "$REVIEWED_PAYLOAD_FILE"
  check "1 wrong password is rejected, no token" 401 err.password-validation.password.invalid
  [[ -z "$TOKEN_VALUE" ]] || { echo "FAIL  1b a token was returned for a wrong password"; FAILED=$((FAILED + 1)); }
else
  skip "1 wrong password" "set CONFIRM_WRONG_PASSWORD_ATTEMPT=yes and WRONG_PASSWORD (counts toward account lock)"
fi

# 2. Correct password -> 200 with a token and a 300 second lifetime.
issue CORRECT_PASSWORD "$REVIEWED_PAYLOAD_FILE"
check "2 correct password issues a token" 200
TOKEN_A="$TOKEN_VALUE"
if [[ -n "$TOKEN_A" ]]; then
  printf 'PASS  %-62s token present (not printed)\n' "2b token returned"; PASSED=$((PASSED + 1))
else
  printf 'FAIL  %-62s no token in response\n' "2b token returned"; FAILED=$((FAILED + 1))
fi

# 3. PUT without a token -> 403 and nothing updated.
call PUT "$UPDATE_PATH" "$REVIEWED_PAYLOAD_FILE"
check "3 update without a token is refused" 403 err.password-validation.authorization.invalid

# 4. Token A with a CHANGED body -> 403 and the token is consumed.
if [[ -n "$TOKEN_A" ]]; then
  call PUT "$UPDATE_PATH" "$DIFFERENT_PAYLOAD_FILE" "$TOKEN_A"
  check "4 changed body is refused" 403 err.password-validation.authorization.invalid
  call PUT "$UPDATE_PATH" "$REVIEWED_PAYLOAD_FILE" "$TOKEN_A"
  check "4b that token was consumed by the refused attempt" 403 err.password-validation.authorization.invalid
else
  skip "4 changed body" "no token from scenario 2"
fi

# 5. Reissue, then PUT with the matching body -> existing success. REAL UPDATE.
if [[ "${CONFIRM_LIVE_UPDATE:-}" == "yes" ]]; then
  issue CORRECT_PASSWORD "$REVIEWED_PAYLOAD_FILE"
  check "5 reissue after consumption" 200
  TOKEN_B="$TOKEN_VALUE"
  if [[ -n "$TOKEN_B" ]]; then
    call PUT "$UPDATE_PATH" "$REVIEWED_PAYLOAD_FILE" "$TOKEN_B"
    if [[ "$LAST_STATUS" == "200" ]]; then
      printf 'PASS  %-62s status=200 (real update applied)\n' "5b matching body is accepted"; PASSED=$((PASSED + 1))
    else
      printf 'FAIL  %-62s expected=200 got=%s code=%s\n' "5b matching body is accepted" "$LAST_STATUS" "${LAST_CODE:--}"; FAILED=$((FAILED + 1))
    fi
    # 6. Replay of the used token -> 403.
    call PUT "$UPDATE_PATH" "$REVIEWED_PAYLOAD_FILE" "$TOKEN_B"
    check "6 replay of a used token is refused" 403 err.password-validation.authorization.invalid
  else
    skip "5b/6 matching body and replay" "no token from the reissue"
  fi
else
  skip "5/6 matching body update and replay" "set CONFIRM_LIVE_UPDATE=yes (performs a REAL update)"
fi

# 7. Throttle: THROTTLE_LIMIT admitted, the next one is 429 with Retry-After.
if [[ "${RUN_THROTTLE:-}" == "yes" ]]; then
  echo "Waiting ${THROTTLE_WAIT_SECONDS}s for the throttle window to reset (do not use this account meanwhile)..."
  sleep "$THROTTLE_WAIT_SECONDS"
  admitted=0; blocked=""
  for i in $(seq 1 $((THROTTLE_LIMIT + 1))); do
    issue CORRECT_PASSWORD "$REVIEWED_PAYLOAD_FILE"
    if [[ "$LAST_STATUS" == "200" ]]; then admitted=$((admitted + 1)); else blocked="$LAST_STATUS/$LAST_CODE"; break; fi
  done
  if (( admitted == THROTTLE_LIMIT )) && [[ "$blocked" == "429/err.password-validation.rate-limited" ]]; then
    printf 'PASS  %-62s admitted=%s then 429 retry-after=%s\n' "7 throttle blocks request $((THROTTLE_LIMIT + 1))" "$admitted" "${LAST_RETRY_AFTER:-absent}"
    PASSED=$((PASSED + 1))
    [[ -n "$LAST_RETRY_AFTER" ]] || { echo "FAIL  7b Retry-After header missing on 429"; FAILED=$((FAILED + 1)); }
  else
    printf 'FAIL  %-62s admitted=%s first-non-200=%s\n' "7 throttle" "$admitted" "${blocked:-none}"
    FAILED=$((FAILED + 1))
  fi
else
  skip "7 throttle" "set RUN_THROTTLE=yes (sends ${THROTTLE_LIMIT}+1 correct-password requests)"
fi

echo
echo "Passed: $PASSED  Failed: $FAILED  Skipped: $SKIPPED"
(( FAILED == 0 )) && exit 0 || exit 1
