#!/usr/bin/env bash
set -uo pipefail
E2E_REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.."&&pwd)"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/runtime.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/assertions.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/evidence.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/private-runtime-state.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/scenarios/login/login-journey-start.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/authenticated-login-journey.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/feature-pack-registry.sh"
source "$E2E_REPOSITORY_ROOT/scripts/e2e/lib/post-login-orchestrator.sh"
orchestrator_login(){ authenticated_login_journey; }
orchestrator_logout(){ authenticated_logout_journey; }
orchestrator_cleanup(){ destroy_private_runtime_state; unset PORTAL_SESSION_COOKIE_VALUE XSRF_TOKEN ACCOUNT_REF; }
defensive_post_login_cleanup(){ orchestrator_cleanup; }
collect_feature_names(){
 local configured="$1" resolved name
 resolved="$(resolve_feature_packs "$configured")" || return 1
 while IFS= read -r name; do basename "$name" .sh | sed 's/-scenarios$//'; done <<<"$resolved"
}
orchestrator_run_feature(){
 local name="$1" path
 path="$(feature_pack_path "$name")"||return 1
 [[ -n "${BFF_BASE_URL:-}" && -n "${PORTAL_SESSION_COOKIE_VALUE:-}" && -n "${XSRF_TOKEN:-}" && -n "${ACCOUNT_REF:-}" ]]||return 1
 BASE_URL="$BFF_BASE_URL" SESSION_COOKIE="$PORTAL_SESSION_COOKIE_VALUE" CSRF_TOKEN="$XSRF_TOKEN" ACCOUNT_REF="$ACCOUNT_REF" bash "$path"
}
post_login_main(){
 local resolved name
 local -a names=()
 resolved="$(collect_feature_names "${E2E_FEATURE_PACKS:-}")" || return 1
 while IFS= read -r name; do names+=("$name"); done <<<"$resolved"
 trap defensive_post_login_cleanup EXIT INT TERM
 run_post_login_orchestrator "${names[@]}"
 local rc=$?
 trap - EXIT INT TERM
 return "$rc"
}
[[ "${1:-}" == --library-only ]]||post_login_main
