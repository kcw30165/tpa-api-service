package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationOtpVerifyWebMapperTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RegistrationOtpVerifyWebMapper mapper =
      new RegistrationOtpVerifyWebMapper(
          new com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper());

  @Test
  void successSingleTrustMapsToUsernameCheck() throws Exception {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.VERIFIED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(new RegistrationUnregisteredTrust("OI", "JPM")),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.USERNAME_CHECK);
    assertThat(response.result().data().unregisteredTrusts())
        .singleElement()
        .satisfies(
            trust -> {
              assertThat(trust.accountEnv()).isEqualTo("OI");
              assertThat(trust.trustCode()).isEqualTo("JPM");
            });
    assertThat(json).contains("\"success\":true", "\"nextStep\":\"USERNAME_CHECK\"");
    assertThat(json).doesNotContain("registration-username-check", "pageRef");
    assertThat(json)
        .doesNotContain(
            "auth_flow_sid",
            "csrfToken",
            "otpChallengeId",
            "activeOtpChallengeId",
            "123456",
            "member@example.com",
            "3024102",
            "04/04/1982");
  }

  @Test
  void successMultipleTrustsMapsToSelectTrust() throws Exception {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.VERIFIED,
                WorkflowNextStep.SELECT_TRUST,
                List.of(
                    new RegistrationUnregisteredTrust("OI", "JPM"),
                    new RegistrationUnregisteredTrust("OI", "BCT")),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.SELECT_TRUST);
    assertThat(response.result().data().unregisteredTrusts()).hasSize(2);
    assertThat(json).contains("\"success\":true", "\"nextStep\":\"SELECT_TRUST\"");
    assertThat(json).doesNotContain("registration-trust-selection", "pageRef");
  }

  @Test
  void invalidOtpMapsToOtpVerificationPageError() throws Exception {
    Object response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_INVALID,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                3,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(json).contains("\"success\":false", "err.registration.otp.invalid");
    assertThat(json).doesNotContain("123456", "otpChallengeId", "activeOtpChallengeId");
  }

  @Test
  void duplicateInProgressMapsToWait() throws Exception {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
                null));

    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
    assertThat(response.result().data()).isNull();
  }

  @Test
  void invalidOtpMapsRemainingAttemptsToVerifyOtpPayload() throws Exception {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_INVALID,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                2,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

    String json = objectMapper.writeValueAsString(response);
    assertThat(json).contains("\"success\":false", "err.registration.otp.invalid");
    assertThat(json).contains("\"nextStep\":\"VERIFY_OTP\"", "\"remainingVerifyAttempts\":2");
    assertThat(json).doesNotContain("registration-otp-verification", "pageRef");
    assertThat(json)
        .doesNotContain("otpChallengeId", "activeOtpChallengeId", "123456", "member@example.com");
  }

  @Test
  void otpExpiredMapsToBusinessFailedWithDistinctErrorCode() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_EXPIRED,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_EXPIRED));
  }

  @Test
  void otpLockedMapsToBusinessFailedWithDistinctErrorCode() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_LOCKED,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(
                        RegistrationOtpVerifyErrorKeys.REGISTRATION_OTP_MAX_ATTEMPTS_REACHED));
  }

  @Test
  void noEligibleTrustsMapsToBusinessFailedWithDistinctErrorCode() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.NO_ELIGIBLE_TRUSTS,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(RegistrationOtpVerifyErrorKeys.REGISTRATION_NO_ELIGIBLE_TRUSTS));
  }

  @Test
  void trustLookupFailedForcesSystemErrorOverride() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.TRUST_LOOKUP_FAILED,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> {
              assertThat(error.code())
                  .isEqualTo(RegistrationOtpVerifyErrorKeys.REGISTRATION_TRUST_LOOKUP_FAILED);
              // Item 4 addition (2026-10-28 decision): RegistrationOtpVerifyWebMapper.java:144
              // maps TRUST_LOOKUP_FAILED -> WorkflowErrorType.DOWNSTREAM_SYSTEM. Confirmed real
              // source: the "system error override" (lines 40-44) only rewrites the top-level
              // success/status fields - it reuses the SAME errors() list from the real
              // WorkflowMutationResponseMapper.toMutationResponse(...) call unchanged, so
              // error.type() still reflects the real toApiError() rendering
              // (descriptor.type().name()), unaffected by the override.
              assertThat(error.type()).isEqualTo("SYSTEM");
            });
  }

  @Test
  void systemFailedMapsToSystemErrorWithoutOverride() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void invalidAuthFlowFallsThroughToDefaultFormErrorType() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.INVALID_AUTH_FLOW,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(RegistrationOtpVerifyErrorKeys.REGISTRATION_AUTH_FLOW_INVALID));
  }

  @Test
  void csrfInvalidMapsToDistinctErrorCode() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.CSRF_INVALID,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(RegistrationOtpVerifyErrorKeys.REGISTRATION_CSRF_INVALID));
  }

  @Test
  void idempotencyKeyReusedMapsToDistinctErrorCode() {
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.IDEMPOTENCY_KEY_REUSED,
                WorkflowNextStep.NONE,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error ->
                assertThat(error.code())
                    .isEqualTo(RegistrationOtpVerifyErrorKeys.WORKFLOW_IDEMPOTENCY_KEY_REUSED));
  }

  @Test
  void suppliedErrorsListIsPassedThroughInsteadOfSynthesizedDefault() {
    var suppliedError =
        new WorkflowErrorDescriptor(
            WorkflowErrorType.FIELD,
            "err.registration.otp.customSuppliedCode",
            null,
            List.of("otpCode"),
            WorkflowErrorSeverity.ERROR,
            "SERVER");
    var response =
        mapper.toMutationResponse(
            new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_INVALID,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                1,
                List.of(),
                List.of(suppliedError),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));
    assertThat(response.errors())
        .singleElement()
        .satisfies(
            error -> assertThat(error.code()).isEqualTo("err.registration.otp.customSuppliedCode"));
  }
}
