#!/usr/bin/env bash
set -euo pipefail
redact_stream() {
  sed -E \
    -e 's/(portal_sid|auth_flow_sid|X-XSRF-TOKEN|Authorization|Cookie|Set-Cookie)([=:][[:space:]]*)[^;[:space:]\"]+/\1\2<redacted>/Ig' \
    -e 's/(Account-Ref)([=:][[:space:]]*)[^;[:space:]\"]+/\1\2<redacted>/Ig' \
    -e 's/(password|otpCode|captchaAnswer|csrfToken)(["=: ]+)[^, }\"]+/\1\2<redacted>/Ig'
}
redacted_presence() { [[ -n "${1:-}" ]] && printf '<provided-redacted>' || printf '<not-provided>'; }
