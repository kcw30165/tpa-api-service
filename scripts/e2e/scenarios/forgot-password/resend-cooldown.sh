#!/usr/bin/env bash
# Forgot Password: a resend inside the cooldown is answered with HTTP 429, and a replay of the same Idempotency-Key gives the same answer.
: "${BASE:=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
source "$BASE/lib/forgot-password-journey.sh"

run_scenario() {
  local rc=0 key retry_after
  [[ "${E2E_ALLOW_EXTERNAL_EFFECTS:-false}" == true ]] || {
    printf 'BLOCKED the journey sends one real message and requires E2E_ALLOW_EXTERNAL_EFFECTS=true\n'; return 3
  }
  [[ "${E2E_ALLOW_OTP_RESEND:-false}" == true ]] || {
    printf 'BLOCKED OTP resend requires E2E_ALLOW_OTP_RESEND=true\n'; return 7
  }
  fp_require_inputs || return 2
  create_private_runtime_state
  fp_note "scenario=resend-cooldown"
  fp_journey_to_otp_sent || return 1

  key="$(fp_new_key resend-cooldown)"
  fp_resend otp-resend-immediate "$key"
  fp_assert_response fp_resend_immediate 429 BUSINESS_REJECTED VERIFY_OTP err.forgot-password.otp.resend.cooldown || rc=1
  fp_note "immediate_resend_http_status=$FP_HTTP_STATUS"
  fp_note "immediate_resend_error_code=$(fp_json '.errors[0].code // empty')"
  retry_after="$(fp_json '.result.data.retryAfterSeconds // empty')"
  fp_note "retry_after_seconds_in_body=${retry_after:-absent}"

  fp_resend otp-resend-replay "$key"
  fp_assert_response fp_resend_replay 429 BUSINESS_REJECTED VERIFY_OTP err.forgot-password.otp.resend.cooldown || rc=1
  fp_note "replay_http_status=$FP_HTTP_STATUS"
  fp_note "otp_resend_requests=2"
  fp_note "real_messages_requested_in_total=1"
  fp_note "automatic_retry_attempts=0"
  return "$rc"
}
