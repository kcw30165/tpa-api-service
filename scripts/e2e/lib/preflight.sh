#!/usr/bin/env bash
set -euo pipefail
preflight() {
  for c in bash curl jq sed awk grep mktemp date; do command -v "$c" >/dev/null 2>&1 || { printf 'ERROR missing dependency: %s\n' "$c" >&2; return 1; }; done
  [[ -n "${BFF_BASE_URL:-}" ]] || { printf 'ERROR BFF_BASE_URL is required\n' >&2; return 1; }
  [[ "$BFF_BASE_URL" == http://* || "$BFF_BASE_URL" == https://* ]] || { printf 'ERROR BFF_BASE_URL must use HTTP or HTTPS\n' >&2; return 1; }
  case "${E2E_BOOTSTRAP_MODE:-}" in anonymous|provided-session) ;; *) printf 'ERROR unsupported bootstrap mode\n' >&2; return 1;; esac
}
