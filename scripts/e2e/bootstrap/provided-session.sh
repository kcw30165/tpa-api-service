#!/usr/bin/env bash
bootstrap_context() {
  [[ -n "${PORTAL_SESSION_COOKIE_VALUE:-}" || -n "${AUTH_FLOW_COOKIE_VALUE:-}" ]] || { printf 'ERROR provided-session requires one configured cookie value\n' >&2; return 1; }
  printf 'mode=provided-session\nportal_cookie=%s\nauth_flow_cookie=%s\nxsrf=%s\n' "$(redacted_presence "${PORTAL_SESSION_COOKIE_VALUE:-}")" "$(redacted_presence "${AUTH_FLOW_COOKIE_VALUE:-}")" "$(redacted_presence "${XSRF_TOKEN:-}")" >"$E2E_RUN_EVIDENCE_DIR/003-bootstrap-summary-redacted.txt"
}
