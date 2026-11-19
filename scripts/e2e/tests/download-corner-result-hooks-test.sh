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
  printf 'FAIL Download Corner result hooks: %s\n' "$1" >&2
  exit 1
}

[[ -f "$PACK" ]] || fail_test "pack is missing"
mkdir -p "$tmp/bin" "$tmp/headers" "$tmp/args" "$tmp/journals" "$tmp/output"
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
  printf '{"success":false,"status":"VALIDATION_FAILED","errors":[{"code":"err.request.id.missing","targets":["X-Request-Id"]}]}\n' \
    >"$body_file"
  printf '400'
  exit 0
fi
jq --arg language "$language" '
  if $language == "zh-HK" then
    .result.data.categories[0].categoryName = "表格"
    | .result.data.categories[0].items[0].itemName = "表格 A"
    | .result.data.categories[1].categoryName = "其他"
  else . end
' "$FIXTURE_FILE" >"$body_file"
if [[ "$language" == en && "${FAKE_CURL_FAIL_EN:-false}" == true ]]; then
  printf '000'
  exit 7
fi
status="$FAKE_HTTP_STATUS"
if [[ "$language" == en && -n "${FAKE_HTTP_STATUS_EN:-}" ]]; then
  status="$FAKE_HTTP_STATUS_EN"
fi
printf '%s' "$status"
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

run_pack() {
  local label="$1" journal_path="$2" curl_fail_en="$3" expected_rc="$4" rc
  local calls="$tmp/calls-$label" args="$tmp/args/$label" headers="$tmp/headers/$label"
  mkdir -p "$args" "$headers"
  : >"$calls"
  if [[ "$journal_path" == unset ]]; then
    if BASE_URL=http://mock SESSION_COOKIE=session-secret CSRF_TOKEN=csrf-secret \
      ACCOUNT_REF=account-secret CALLS_FILE="$calls" ARGS_DIR="$args" \
      HEADERS_DIR="$headers" FIXTURE_FILE="$tmp/fixture.json" \
      FAKE_HTTP_STATUS=200 FAKE_CURL_FAIL_EN="$curl_fail_en" \
      bash "$PACK" >"$tmp/output/$label" 2>&1; then
      rc=0
    else
      rc=$?
    fi
  else
    if E2E_RESULT_JOURNAL="$journal_path" BASE_URL=http://mock \
      SESSION_COOKIE=session-secret CSRF_TOKEN=csrf-secret ACCOUNT_REF=account-secret \
      CALLS_FILE="$calls" ARGS_DIR="$args" HEADERS_DIR="$headers" \
      FIXTURE_FILE="$tmp/fixture.json" FAKE_HTTP_STATUS=200 \
      FAKE_CURL_FAIL_EN="$curl_fail_en" bash "$PACK" >"$tmp/output/$label" 2>&1; then
      rc=0
    else
      rc=$?
    fi
  fi
  [[ "$rc" -eq "$expected_rc" ]] || {
    cat "$tmp/output/$label" >&2
    fail_test "$label returned $rc, expected $expected_rc"
  }
  [[ "$(wc -l <"$calls")" -eq 2 ]] || fail_test "$label did not issue exactly two requests"
  [[ "$(cat "$calls")" == $'GET /api/v1/download-corner en\nGET /api/v1/download-corner zh-HK' ]] \
    || fail_test "$label changed request order or languages"
}

expected_ids="$tmp/expected-ids"
cat >"$expected_ids" <<'IDS'
en.http-status
en.envelope
en.category-shape
en.item-shape
en.no-seq-env
zh-HK.http-status
zh-HK.envelope
zh-HK.category-shape
zh-HK.item-shape
zh-HK.no-seq-env
language-category-count
language-item-count
IDS
sort -o "$expected_ids" "$expected_ids"

assert_balanced_journal() {
  local journal="$1"
  jq -s -e '
    ([.[] | select(.event == "start") | .id] | sort)
      == ([.[] | select(.event == "finish") | .id] | sort)
    and ([.[] | .seq] == [range(1; length + 1)])
  ' "$journal" >/dev/null || fail_test "unbalanced start/finish records or sequence"
  jq -r 'select(.event == "start") | .id' "$journal" | sort >"$tmp/actual-ids"
  cmp -s "$expected_ids" "$tmp/actual-ids" || fail_test "journal ID set changed"
}

run_pack no-journal unset false 0
run_pack success-journal "$tmp/journals/success.jsonl" false 0
cmp -s "$tmp/output/no-journal" "$tmp/output/success-journal" \
  || fail_test "journal hooks changed terminal output"
[[ ! -e "$tmp/journals/success.jsonl.failed" ]] || fail_test "success wrote a failed marker"
[[ "$(stat -c '%a' "$tmp/journals/success.jsonl")" == 600 ]] || fail_test "journal mode is not 0600"
assert_balanced_journal "$tmp/journals/success.jsonl"
jq -s -e '
  all(.[] | select(.event == "finish");
    .request_id == null
    and .echoed_request_id == null
    and .duration_ms == null
    and .expected_public_error_code == null
    and .actual_public_error_code == null)
  and all(.[] | select(.event == "finish");
    if (.id | startswith("language-")) then
      .actual_http_status == null and .expected_http_status == null
    else
      .actual_http_status == 200
      and .expected_http_status == (if (.id | endswith(".http-status")) then 200 else null end)
    end)
' "$tmp/journals/success.jsonl" >/dev/null || fail_test "success journal status or null fields changed"

run_pack failed-no-journal unset true 1
run_pack failed-journal "$tmp/journals/failed.jsonl" true 1
cmp -s "$tmp/output/failed-no-journal" "$tmp/output/failed-journal" \
  || fail_test "journal hooks changed failed terminal output"
assert_balanced_journal "$tmp/journals/failed.jsonl"
jq -s -e '
  all(.[] | select(.event == "finish" and (.id | startswith("en.")));
    .actual_http_status == null)
  and all(.[] | select(.event == "finish" and (.id | startswith("zh-HK.")));
    .actual_http_status == 200)
  and ([.[] | select(.event == "finish" and .id == "en.http-status")][0]
    | .status == "FAIL" and .expected_http_status == 200)
  and all(.[] | select(.event == "finish" and (.id | startswith("en."))
      and .id != "en.http-status"); .status == "SKIP")
  and all(.[] | select(.event == "finish" and (.id | startswith("language-")));
    .status == "SKIP" and .actual_http_status == null and .expected_http_status == null)
' "$tmp/journals/failed.jsonl" >/dev/null || fail_test "HTTP 000 did not omit status and skip dependent checks"
[[ ! -e "$tmp/journals/failed.jsonl.failed" ]] || fail_test "HTTP 000 wrote a failed marker"

printf 'PASS Download Corner result journal hooks\n'
