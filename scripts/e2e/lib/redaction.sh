#!/usr/bin/env bash
redact_stream() {
  sed -E \
    -e 's/(portal_sid|auth_flow_sid|X-XSRF-TOKEN|Authorization|Cookie|Set-Cookie)([=:][[:space:]]*)[^;[:space:]\"]+/\1\2<redacted>/Ig' \
    -e 's/(Account-Ref|Idempotency-Key)([=:][[:space:]]*)[^;[:space:]\"]+/\1\2<redacted>/Ig' \
    -e 's/(password|otpCode|captchaAnswer|csrfToken|destinationId|maskedDestination)(["=: ]+)[^, }\"]+/\1\2<redacted>/Ig'
}
redacted_presence() { [[ -n "${1:-}" ]] && printf '<provided-redacted>' || printf '<not-provided>'; }
redact_headers_file() {
  local source="$1" destination="$2"
  sed -E \
    -e 's/^([Ss]et-[Cc]ookie:[[:space:]]*[^=;[:space:]]+)=([^;]*)(.*)$/\1=<redacted>\3/' \
    -e 's/^([Aa]uthorization|[Xx]-[Xx][Ss][Rr][Ff]-[Tt][Oo][Kk][Ee][Nn]|[Aa]ccount-[Rr]ef|[Ii]dempotency-[Kk]ey):.*/\1: <redacted>/' \
    "$source" >"$destination"
}
redact_json_file() {
  local source="$1" destination="$2"
  if jq -e . "$source" >/dev/null 2>&1; then
    jq 'walk(if type == "object" then with_entries(if (.key | test("^(password|confirmPassword|otpCode|code|captchaAnswer|csrfToken|destinationId|maskedDestination|sessionId|accountRef|accountEnv|policyNo|certNo|trustCode|actorUserId|actorUserName|actorUserRole|actorUserType)$"; "i")) then .value = "<redacted>" else . end) else . end)' "$source" >"$destination"
  else
    redact_stream <"$source" >"$destination"
  fi
}
