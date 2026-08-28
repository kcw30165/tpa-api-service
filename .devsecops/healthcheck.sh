#!/usr/bin/env bash
set -euo pipefail

DEPLOY_ENV="${1:-${DEPLOY_ENV:-}}"
BASE_URL="${APPLICATION_BASE_URL:-}"
TIMEOUT_SECONDS="${HEALTHCHECK_TIMEOUT_SECONDS:-10}"

[[ -n "$DEPLOY_ENV" ]] || {
  printf 'ERROR: deployment environment argument is required.\n' >&2
  exit 1
}
[[ -n "$BASE_URL" ]] || {
  printf 'ERROR: APPLICATION_BASE_URL is required.\n' >&2
  exit 1
}
[[ "$BASE_URL" == http://* || "$BASE_URL" == https://* ]] || {
  printf 'ERROR: APPLICATION_BASE_URL must use HTTP or HTTPS.\n' >&2
  exit 1
}

URL="${BASE_URL%/}/actuator/health/readiness"
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT
STATUS="$(curl --silent --show-error --fail-with-body --max-time "$TIMEOUT_SECONDS" \
  --output "$BODY" --write-out '%{http_code}' "$URL")"
[[ "$STATUS" == "200" ]] || {
  printf 'ERROR: readiness endpoint returned HTTP %s for environment %s.\n' "$STATUS" "$DEPLOY_ENV" >&2
  exit 1
}
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$BODY" || {
  printf 'ERROR: readiness response is not UP for environment %s.\n' "$DEPLOY_ENV" >&2
  exit 1
}

printf 'ACTUATOR_READINESS PASS environment=%s http_status=200\n' "$DEPLOY_ENV"
