#!/usr/bin/env bash
run_post_login_orchestrator() {
  local -a feature_names=("$@")
  local login_status=FAIL logout_status=NOT_RUN overall_rc=0 feature_name feature_rc
  local -a feature_statuses=()

  if orchestrator_login; then
    login_status=PASS
    for feature_name in "${feature_names[@]}"; do
      if orchestrator_run_feature "$feature_name"; then
        feature_statuses+=(PASS)
      else
        feature_rc=$?
        if [[ "$feature_rc" -eq 77 ]]; then feature_statuses+=(SKIP); else feature_statuses+=(FAIL); overall_rc=1; fi
      fi
    done
    if orchestrator_logout; then logout_status=PASS; else logout_status=FAIL; overall_rc=1; fi
  else
    overall_rc=1
    for feature_name in "${feature_names[@]}"; do feature_statuses+=(NOT_RUN); done
  fi

  orchestrator_cleanup
  printf 'LOGIN: %s\n' "$login_status"
  for feature_name in "${!feature_names[@]}"; do
    printf 'FEATURE %s: %s\n' "${feature_names[$feature_name]}" "${feature_statuses[$feature_name]}"
  done
  printf 'LOGOUT: %s\n' "$logout_status"
  if [[ "$overall_rc" -eq 0 ]]; then printf 'OVERALL: PASS\n'; else printf 'OVERALL: FAIL\n'; fi
  [[ -z "${E2E_RUN_EVIDENCE_DIR:-}" ]] || printf 'EVIDENCE: %s\n' "$E2E_RUN_EVIDENCE_DIR"
  return "$overall_rc"
}
