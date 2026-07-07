package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config.AuthFlowCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationController;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class AuthRegistrationControllerCaptchaGateTest {

    @Test
    void registrationStartCaptchaFailureDoesNotSetAuthFlowCookieOrReturnCsrf() {
        WebTestClient client = client(command -> Mono.just(new StartRegistrationResult(
                StartRegistrationResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_REQUIRED, "captchaChallengeId")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null)));

        client.post()
                .uri("/api/v1/auth/registration/start")
                .header("Idempotency-Key", "idem_abc123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationStartRequest("GPFS", "1234567890", "31/01/1980", "member@example.com", null, null))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().doesNotExist("auth_flow_sid")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("registration.captcha.required")
                        .contains("captchaChallengeId")
                        .doesNotContain("csrfToken")
                        .doesNotContain("auth_flow_sid")
                        .doesNotContain("otpExpiresInSeconds")
                        .doesNotContain("imageBase64")
                        .doesNotContain("answerHash")
                        .doesNotContain("cap_"));
    }

    @Test
    void registrationStartSuccessDoesNotReturnCaptchaData() {
        WebTestClient client = client(command -> Mono.just(new StartRegistrationResult(
                StartRegistrationResultStatus.STARTED,
                WorkflowNextStep.VERIFY_OTP,
                "m****r@example.com",
                300,
                60,
                5,
                "csrf_token_safe",
                "afs_secret",
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.STARTED,
                null)));

        client.post()
                .uri("/api/v1/auth/registration/start")
                .header("Idempotency-Key", "idem_abc123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationStartRequest("GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_abcdefghijklmnop"))
                .exchange()
                .expectStatus().isOk()
                .expectCookie().exists("auth_flow_sid")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("VERIFY_OTP")
                        .contains("csrf_token_safe")
                        .doesNotContain("captchaChallengeId")
                        .doesNotContain("imageBase64")
                        .doesNotContain("answerHash")
                        .doesNotContain("captchaStatus")
                        .doesNotContain("ngtpa:auth:captcha:"));
    }

    @Test
    void registrationStartCaptchaFailureTargetsCaptchaChallengeId() {
        WebTestClient client = client(command -> Mono.just(new StartRegistrationResult(
                StartRegistrationResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(RegistrationWorkflowErrorKeys.REGISTRATION_CAPTCHA_INVALID, "captchaChallengeId")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null)));

        client.post()
                .uri("/api/v1/auth/registration/start")
                .header("Idempotency-Key", "idem_abc123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegistrationStartRequest("GPFS", "1234567890", "31/01/1980", "member@example.com", null, "cap_abcdefghijklmnop"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("registration.captcha.invalid")
                .jsonPath("$.errors[0].targets[0]").isEqualTo("captchaChallengeId");
    }

    private WebTestClient client(StartRegistrationUseCase useCase) {
        return WebTestClient.bindToController(new AuthRegistrationController(
                        useCase,
                        new RegistrationStartWebMapper(Duration.ofMinutes(5)),
                        AuthFlowCookieWriter.defaultWriter()))
                .build();
    }
}