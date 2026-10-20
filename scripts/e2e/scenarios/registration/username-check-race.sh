#!/usr/bin/env bash
# Scenario: registration/username-check-race
#
# Simulates the competitive scenario reported by Nick: two registration flows (A
# and B) both reach /username/check with the SAME unregistered candidate username.
# Flow A completes first and must win (real success). Flow B completes second with
# the same username and must now be rejected per commits 3c5fe448 (fix) and
# abe4382e (stale-test correction): HTTP 422, BUSINESS_REJECTED,
# nextStep=USERNAME_CHECK, errors[0].code=err.registration.complete.memberAlreadyRegistered.
#
# This scenario performs REAL, PERMANENT mutations (flow A registers a real
# member) and requires E2E_ALLOW_MUTATIONS=true and
# E2E_ALLOW_EXTERNAL_EFFECTS=true to be set explicitly, mirroring the safety gate
# used by other mutating scenarios in this pack.
run_scenario() {
  if [[ "${E2E_ALLOW_MUTATIONS:-false}" != true || "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" != true ]]; then
    printf 'FAIL this scenario performs a real, permanent registration and real OTP delivery; set E2E_ALLOW_MUTATIONS=true and E2E_ALLOW_EXTERNAL_EFFECTS=true to proceed\n'
    return 1
  fi

  create_private_runtime_state
  reg_require_inputs || return 1

  reg_progress "Flow A: start -> otp/verify -> username/check (candidate username shared with flow B)"
  reg_journey_to_username_checked A || return 1

  reg_progress "Flow B: start -> otp/verify -> username/check (same candidate username as flow A)"
  reg_journey_to_username_checked B || return 1

  reg_progress "Flow A: complete (expected to win -- first completer)"
  reg_complete A
  assert_eq 0 "$REG_CURL_EXIT" "reg_A_complete_curl_exit" || return 1
  local a_status a_next
  a_status="$(reg_json '.status // "<absent>"')"
  a_next="$(reg_json '.result.nextStep // "<absent>"')"
  if [[ "$REG_HTTP_STATUS" != 200 || "$a_status" != SUCCESS ]]; then
    printf 'FAIL flow A (first completer) did not succeed: http=%s status=%s\n' "$REG_HTTP_STATUS" "$a_status"
    return 1
  fi
  printf 'PASS reg_A_complete_http_status\n'
  printf 'PASS reg_A_complete_api_status\n'
  if [[ "$a_next" != "RETURN_TO_LOGIN" && "$a_next" != "SELECT_TRUST" ]]; then
    printf 'FAIL reg_A_complete_next_step unexpected value=%s (expected RETURN_TO_LOGIN or SELECT_TRUST depending on remaining trusts)\n' "$a_next"
    return 1
  fi
  printf 'PASS reg_A_complete_next_step=%s\n' "$a_next"
  reg_note "flow_A completed first and won the race as designed"

  reg_progress "Flow B: complete (expected to be rejected -- username now registered by flow A)"
  reg_complete B
  # The real downstream TRPRegMember can legitimately return either of these two
  # codes for the same duplicate-username-at-complete race (confirmed via a real
  # local run plus source review of TrpRegMemberApimResultMapper, which passes
  # the downstream code through verbatim with no selection logic). Both already
  # correctly resolve to the same outcome: 422 / BUSINESS_REJECTED / USERNAME_CHECK.
  reg_assert_response_any_code reg_B_complete 422 BUSINESS_REJECTED USERNAME_CHECK \
    err.registration.complete.memberAlreadyRegistered \
    err.registration.username.isRegistered || return 1
  reg_note "flow_B was correctly rejected: the first registration (flow A) wins, matching commits 3c5fe448/abe4382e"

  printf 'PASS registration_username_check_race_scenario_complete\n'
}
