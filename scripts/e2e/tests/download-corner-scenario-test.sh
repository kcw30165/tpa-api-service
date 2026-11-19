#!/usr/bin/env bash
unset BASH_ENV ENV
while IFS= read -r _name; do unset "$_name"; done < <(
  compgen -e | grep -E '^(E2E_|BFF_|DC_|LOGIN_|APIM_|OTP_|ACCOUNT_|SESSION_|FAKE_)' || true
)
unset _name
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
PACK="$ROOT/scripts/e2e/download-corner-scenarios.sh"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT

fail_test() {
  printf 'FAIL download-corner scenario: %s\n' "$1" >&2
  exit 1
}

[[ -f "$PACK" ]] || fail_test "pack is missing"
mkdir -p "$tmp/bin" "$tmp/headers" "$tmp/args"
cat >"$tmp/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -u
args=("$@")
method=GET
headers_file=''
body_file=''
url=''
language=''
request_id=''
request_id_count=0
while (($#)); do
  case "$1" in
    -X) method="$2"; shift 2 ;;
    -H)
      case "$2" in
        @*) headers_file="${2#@}" ;;
        'Accept-Language: '*) language="${2#Accept-Language: }" ;;
      esac
      shift 2
      ;;
    -o) body_file="$2"; shift 2 ;;
    -w) shift 2 ;;
    http://*|https://*) url="$1"; shift ;;
    *) shift ;;
  esac
done
call_number="$(($(wc -l <"$CALLS_FILE") + 1))"
printf '%s %s %s\n' "$method" "${url#"$BASE_URL"}" "$language" >>"$CALLS_FILE"
printf '%s\n' "${args[@]}" >"$ARGS_DIR/args-$call_number"
cat "$headers_file" >"$HEADERS_DIR/headers-$call_number"
printf 'Accept-Language: %s\n' "$language" >>"$HEADERS_DIR/headers-$call_number"
while IFS= read -r header; do
  case "$header" in
    X-Request-Id:*)
      request_id_count=$((request_id_count + 1))
      request_id="${header#X-Request-Id:}"
      request_id="${request_id# }"
      ;;
  esac
done <"$HEADERS_DIR/headers-$call_number"
if [[ "$request_id_count" -ne 1 || -z "${request_id//[[:space:]]/}" ]]; then
  : >"$ID_FAILURE_FILE"
  printf '{"success":false,"status":"VALIDATION_FAILED","errors":[{"code":"err.request.id.missing","targets":["X-Request-Id"]}]}\n' \
    >"$body_file"
  printf '400'
  exit 0
fi
jq --arg scenario "$FAKE_SCENARIO" --arg language "$language" '
  if $scenario == "categories-not-array" then
    .result.data.categories = {}
  elif $scenario == "blank-category" then
    .result.data.categories[0].categoryName = "  "
  elif $scenario == "missing-items" then
    del(.result.data.categories[0].items)
  elif $scenario == "blank-item-link" then
    .result.data.categories[0].items[0].itemLink = " "
  elif $scenario == "blank-item-name" then
    .result.data.categories[0].items[0].itemName = " "
  elif $scenario == "blank-file-name" then
    .result.data.categories[0].items[0].fileName = ""
  elif $scenario == "leaked-seq" then
    .result.data.categories[0].seq = 1
  elif $scenario == "leaked-categorySeq" then
    .result.data.categories[0].categorySeq = 1
  elif $scenario == "leaked-itemSeq" then
    .result.data.categories[0].items[0].itemSeq = 1
  elif $scenario == "leaked-env" then
    .result.data.categories[0].items[0].env = "PROD"
  elif $scenario == "category-count-mismatch" and $language == "zh-HK" then
    .result.data.categories += [{"categoryName":"多","items":[]}]
  elif $scenario == "item-count-mismatch" and $language == "zh-HK" then
    .result.data.categories[0].items |= .[0:1]
  else .
  end
' "$FIXTURE_FILE" >"$body_file"
printf '%s' "$FAKE_HTTP_STATUS"
FAKE_CURL
chmod 700 "$tmp/bin/curl"
export PATH="$tmp/bin:$PATH"

cat >"$tmp/fixture.json" <<'FIXTURE'
{
  "success": true,
  "status": "SUCCESS",
  "result": {
    "data": {
      "categories": [
        {
          "categoryName": "Forms",
          "items": [
            {"itemName":"Form A","itemLink":"https://files.test/a.pdf","fileName":"a.pdf"},
            {"itemName":"Form B","itemLink":"https://files.test/b.pdf","fileName":"b.pdf"}
          ]
        },
        {"categoryName":"Other","items":[]}
      ]
    }
  }
}
FIXTURE

run_case() {
  local scenario="$1" http_status="$2" expected_rc="$3" expected_failure="$4" rc
  local calls="$tmp/calls-$scenario" args="$tmp/args/$scenario" headers="$tmp/headers/$scenario"
  local id_failure="$tmp/id-failure-$scenario" request_id='' request_id_count=0
  local request_id_en='' request_id_zh=''
  mkdir -p "$args" "$headers"
  : >"$calls"
  if BASE_URL=http://mock SESSION_COOKIE=session-secret CSRF_TOKEN=csrf-secret \
    ACCOUNT_REF=account-secret CALLS_FILE="$calls" ARGS_DIR="$args" \
    HEADERS_DIR="$headers" FIXTURE_FILE="$tmp/fixture.json" \
    ID_FAILURE_FILE="$id_failure" \
    FAKE_SCENARIO="$scenario" FAKE_HTTP_STATUS="$http_status" \
    bash "$PACK" >"$tmp/output-$scenario" 2>&1; then
    rc=0
  else
    rc=$?
  fi
  [[ ! -e "$id_failure" ]] \
    || fail_test "$scenario fake curl rejected a missing, blank, or duplicate X-Request-Id"
  [[ "$rc" -eq "$expected_rc" ]] || {
    cat "$tmp/output-$scenario" >&2
    fail_test "$scenario returned $rc, expected $expected_rc"
  }
  if [[ -n "$expected_failure" ]] \
    && ! grep -Fq "$expected_failure" "$tmp/output-$scenario"; then
    cat "$tmp/output-$scenario" >&2
    fail_test "$scenario did not fail its expected check"
  fi
  [[ "$(wc -l <"$calls")" -eq 2 ]] || fail_test "$scenario did not issue exactly two requests"
  [[ "$(cat "$calls")" == $'GET /api/v1/download-corner en\nGET /api/v1/download-corner zh-HK' ]] \
    || fail_test "$scenario changed the read-only request order or language"
  for index in 1 2; do
    grep -Fxq 'Cookie: portal_sid=session-secret' "$headers/headers-$index" \
      || fail_test "$scenario omitted the authenticated cookie"
    grep -Fxq 'X-XSRF-TOKEN: csrf-secret' "$headers/headers-$index" \
      || fail_test "$scenario omitted the CSRF header"
    grep -Fxq 'Account-Ref: account-secret' "$headers/headers-$index" \
      || fail_test "$scenario omitted the selected Account-Ref"
    request_id_count="$(grep -c '^X-Request-Id:' "$headers/headers-$index" || true)"
    [[ "$request_id_count" -eq 1 ]] \
      || fail_test "$scenario did not send exactly one X-Request-Id"
    request_id="$(sed -n 's/^X-Request-Id: //p' "$headers/headers-$index")"
    [[ -n "${request_id//[[:space:]]/}" ]] \
      || fail_test "$scenario sent a blank X-Request-Id"
    if [[ "$index" -eq 1 ]]; then request_id_en="$request_id"; else request_id_zh="$request_id"; fi
    grep -Fxq 'GET' "$args/args-$index" || fail_test "$scenario used a non-GET method"
    if grep -Eq '^(POST|PUT|PATCH|DELETE)$' "$args/args-$index"; then
      fail_test "$scenario issued a mutating HTTP method"
    fi
  done
  [[ "$request_id_en" != "$request_id_zh" ]] \
    || fail_test "$scenario reused the same X-Request-Id for both languages"
}

run_case valid 200 0 ''
run_case non-200 503 1 'check=http-status'
run_case categories-not-array 200 1 'check=envelope'
run_case blank-category 200 1 'check=category-shape'
run_case missing-items 200 1 'check=category-shape'
run_case blank-item-link 200 1 'check=item-shape'
run_case blank-item-name 200 1 'check=item-shape'
run_case blank-file-name 200 1 'check=item-shape'
run_case leaked-seq 200 1 'check=no-seq-env'
run_case leaked-categorySeq 200 1 'check=no-seq-env'
run_case leaked-itemSeq 200 1 'check=no-seq-env'
run_case leaked-env 200 1 'check=no-seq-env'
run_case category-count-mismatch 200 1 'check=language-category-count'
run_case item-count-mismatch 200 1 'check=language-item-count'

printf 'PASS Download Corner read-only scenario contract\n'
