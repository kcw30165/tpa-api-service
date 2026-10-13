#!/usr/bin/env bash
# Forgot Password: after the allowed number of accepted resends, the next resend is answered with HTTP 429, and a replay of its
# Idempotency-Key gives the same answer. The scenario waits for the cooldown before every resend, so the limit, and not the
# cooldown, is the only possible reason for the final rejection. It sends one real message per accepted resend plus the journey's own.
: "${BASE:=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
source "$BASE/lib/forgot-password-journey.sh"

run_scenario() {
  local rc=0 i max margin cooldown wait_seconds key next_step remaining
  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED the journey sends real messages and requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3
  }
  [[ "${E2E_ALLOW_OTP_RESEND:-false}" == true ]] || {
    printf 'BLOCKED OTP resend requires E2E_ALLOW_OTP_RESEND=true\n'; return 7
  }
  [[ "${E2E_ALLOW_OTP_RESEND_LIMIT:-false}" == true ]] || {
    printf 'BLOCKED reaching the resend limit sends several real messages and requires E2E_ALLOW_OTP_RESEND_LIMIT=true\n'; return 8
  }
  max="${E2E_FORGOT_PASSWORD_MAX_RESENDS:-5}"
  margin="${E2E_FORGOT_PASSWORD_COOLDOWN_MARGIN_SECONDS:-2}"
  [[ "$max" =~ ^[1-9][0-9]?$ ]] || { printf 'FAIL E2E_FORGOT_PASSWORD_MAX_RESENDS must be 1 to 99\n'; return 2; }
  [[ "$margin" =~ ^[0-9]{1,2}$ ]] || { printf 'FAIL E2E_FORGOT_PASSWORD_COOLDOWN_MARGIN_SECONDS must be 0 to 99\n'; return 2; }
  fp_require_inputs || return 2
  create_private_runtime_state
  fp_note "scenario=resend-limit"
  fp_note "configured_accepted_resends=$max"
  fp_journey_to_otp_sent || return 1

  cooldown="$(fp_cooldown_seconds)" || return 1
  wait_seconds=$((cooldown + margin))
  fp_note "cooldown_seconds=$cooldown"
  fp_note "wait_before_each_resend_seconds=$wait_seconds"

  for ((i = 1; i <= max; i++)); do
    fp_progress "accepted resend $i of $max: waiting ${wait_seconds}s for the cooldown to pass"
    fp_wait "$wait_seconds"
    fp_resend "otp-resend-$i" "$(fp_new_key "resend-limit-$i")"
    fp_assert_response "fp_resend_$i" 200 SUCCESS VERIFY_OTP || return 1
    remaining="$(fp_json '.result.data.remainingResendAttempts // empty')"
    fp_note "accepted_resend_$i=HTTP $FP_HTTP_STATUS remaining_resend_attempts=${remaining:-absent}"
  done

  fp_progress "resend beyond the limit: waiting ${wait_seconds}s so the cooldown cannot be the reason"
  fp_wait "$wait_seconds"
  key="$(fp_new_key resend-over-limit)"
  fp_resend otp-resend-over-limit "$key"
  fp_assert_response fp_resend_over_limit 429 BUSINESS_REJECTED - err.forgot-password.otp.resend.limitReached || rc=1
  next_step="$(fp_json '.result.nextStep // empty')"
  fp_note "over_limit_http_status=$FP_HTTP_STATUS"
  fp_note "over_limit_error_code=$(fp_json '.errors[0].code // empty')"
  fp_note "over_limit_next_step=${next_step:-absent} (observed only; the contract for this step is not asserted)"

  fp_resend otp-resend-over-limit-replay "$key"
  fp_assert_response fp_resend_over_limit_replay 429 BUSINESS_REJECTED - err.forgot-password.otp.resend.limitReached || rc=1
  fp_note "replay_http_status=$FP_HTTP_STATUS"
  fp_note "otp_resend_requests=$((max + 2))"
  fp_note "real_messages_requested_in_total=$((max + 1))"
  fp_note "automatic_retry_attempts=0"
  return "$rc"
}
