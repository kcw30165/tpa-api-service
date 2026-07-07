package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationOtpVerifyWebMapperTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegistrationOtpVerifyWebMapper mapper = new RegistrationOtpVerifyWebMapper(null);

    @Test
    void successSingleTrustMapsToUsernameCheck() throws Exception {
        Object response = mapper.toMutationResponse(new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.VERIFIED,
                WorkflowNextStep.USERNAME_CHECK,
                List.of(new RegistrationUnregisteredTrust("OI", "00000008802", "2", "JPM")),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":true", "\"nextStep\":\"USERNAME_CHECK\"", "registration-username-check");
        assertThat(json).contains("\"accountEnv\":\"OI\"", "\"policyNo\":\"00000008802\"", "\"certNo\":\"2\"", "\"trustCode\":\"JPM\"");
        assertThat(json).doesNotContain("auth_flow_sid", "csrfToken", "otpChallengeId", "activeOtpChallengeId", "123456", "member@example.com", "3024102", "04/04/1982");
    }

    @Test
    void successMultipleTrustsMapsToSelectTrust() throws Exception {
        Object response = mapper.toMutationResponse(new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.VERIFIED,
                WorkflowNextStep.SELECT_TRUST,
                List.of(
                        new RegistrationUnregisteredTrust("OI", "00000008802", "2", "JPM"),
                        new RegistrationUnregisteredTrust("OI", "00000008803", "3", "BCT")),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":true", "\"nextStep\":\"SELECT_TRUST\"", "registration-trust-selection");
        assertThat(json).contains("00000008802", "00000008803");
    }

    @Test
    void invalidOtpMapsToOtpVerificationPageError() throws Exception {
        Object response = mapper.toMutationResponse(new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_INVALID,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                3,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":false", "registration.otp.invalid");
        assertThat(json).doesNotContain("123456", "otpChallengeId", "activeOtpChallengeId");
    }

    @Test
    void duplicateInProgressMapsToWait() throws Exception {
        Object response = mapper.toMutationResponse(new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                List.of(),
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
                null));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":true", "\"nextStep\":\"WAIT\"");
    }

    @Test
    void invalidOtpMapsRemainingAttemptsToVerifyOtpPayload() throws Exception {
        Object response = mapper.toMutationResponse(new VerifyRegistrationOtpResult(
                VerifyRegistrationOtpResultStatus.OTP_INVALID,
                WorkflowNextStep.VERIFY_OTP,
                List.of(),
                2,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null));

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"success\":false", "registration.otp.invalid");
        assertThat(json).contains("\"nextStep\":\"VERIFY_OTP\"", "registration-otp-verification", "\"remainingVerifyAttempts\":2");
        assertThat(json).doesNotContain("otpChallengeId", "activeOtpChallengeId", "123456", "member@example.com");
    }

}
