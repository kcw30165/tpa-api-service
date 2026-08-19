package com.bct.ngtpa.apiservice.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisRegistrationIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class P6RegistrationStartIntegrationTest {

  @Test
  void p6RegistrationStartRequestShapeExcludesInternalFields() {
    assertThat(
            Arrays.stream(RegistrationStartRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly(
            "identityType", "identityValue", "dateOfBirth", "email", "mobile", "captchaChallengeId")
        .doesNotContain(
            "registrationSessionId",
            "authFlowSessionId",
            "otpChallengeId",
            "contactType",
            "contactValue",
            "accountRef",
            "lang");
  }

  @Test
  void p6RegistrationStartValidationCoversRequiredRules() {
    var validator = new RegistrationStartValidator();
    var invalid =
        List.of(
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "BAD", "1", "31/01/1980", "member@example.com", null, null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "ABC", "31/01/1980", "member@example.com", null, null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/02/1980", "member@example.com", null, null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", null, null, null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", "member@example.com", "91234567", null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", "bad-email", null, null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", null, "31234567", null),
            new com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartInput(
                "GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap abc"));
    invalid.forEach(input -> assertThat(validator.validate(input).valid()).isFalse());
  }

  @Test
  void p6RegistrationStartRedisIdempotencyKeyShapeIsActionScoped() {
    String key =
        new RedisRegistrationIdempotencyKeyFactory("ngtpa")
            .keyFor(
                RegistrationIdempotencyAction.REGISTRATION_START,
                new RegistrationIdempotencyKey("idem_abc123"));
    assertThat(key)
        .isEqualTo("ngtpa:auth:idempotency:registration-start:idem_abc123")
        .doesNotContain(
            "member@example.com", "91234567", "csrf", "otp", "password", "auth_flow_sid", "req-");
  }

  @Test
  void p6RegistrationStartSuccessReturnsCsrfTokenAndCookieShapeWithoutSessionIdInBody() {
    var mapper = new RegistrationStartWebMapper(Duration.ofMinutes(5));
    var result =
        new com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult(
            com.bct.ngtpa.apiservice.application.usecase.auth.registration
                .StartRegistrationResultStatus.STARTED,
            WorkflowNextStep.VERIFY_OTP,
            "m****r@example.com",
            300,
            60,
            5,
            "csrf_abc",
            "afs_abc",
            List.of(),
            List.of(),
            com.bct.ngtpa.apiservice.application.dto.auth.idempotency
                .RegistrationIdempotencyStartOutcome.STARTED,
            null);
    var response = mapper.toMutationResponse(result);
    var cookie = AuthFlowCookieWriter.defaultWriter().createAuthFlowCookie("afs_abc");

    assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
    assertThat(response.result().data().csrfToken()).isEqualTo("csrf_abc");
    assertThat(response.toString())
        .doesNotContain(
            "afs_abc", "authFlowSessionId", "otpChallengeId", "otpCode", "policyNo", "certNo");
    assertThat(cookie.getName()).isEqualTo("auth_flow_sid");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("None");
  }
}
