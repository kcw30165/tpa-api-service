package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowErrorKeys;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationStartWebMapperTest {
    private final RegistrationStartWebMapper mapper = new RegistrationStartWebMapper(Duration.ofMinutes(5));

    @Test
    void registrationStartMapsRequestToUseCaseCommand() {
        var request = new RegistrationStartRequest("GPFS", "1234567890", "31/01/1980", "member@example.com", null,
                "cap_secret");
        StartRegistrationCommand command = mapper.toCommand(request, "idem_abc123", "req-123", "en-HK");

        assertThat(command.idempotencyKey()).isEqualTo(new RegistrationIdempotencyKey("idem_abc123"));
        assertThat(command.identityType()).isEqualTo("GPFS");
        assertThat(command.identityValue()).isEqualTo("1234567890");
        assertThat(command.dateOfBirth()).isEqualTo("31/01/1980");
        assertThat(command.email()).isEqualTo("member@example.com");
        assertThat(command.mobile()).isNull();
        assertThat(command.acceptLanguage()).isEqualTo("en-HK");
        assertThat(command.correlationId()).isEqualTo("req-123");
        assertThat(command.toString()).doesNotContain("1234567890", "member@example.com", "cap_secret", "req-123",
                "idem_abc123");
    }

    @Test
    void responseToStringDoesNotExposeCsrfTokenOrSessionId() {
        var result = new StartRegistrationResult(StartRegistrationResultStatus.STARTED, WorkflowNextStep.VERIFY_OTP,
                "m****r@example.com", 300, 60, 5, "csrf_secret", "afs_secret", List.of(), List.of(),
                RegistrationIdempotencyStartOutcome.STARTED, null);
        var response = mapper.toResponse(result);

        assertThat(response.csrfToken()).isEqualTo("csrf_secret");
        assertThat(response.toString()).doesNotContain("csrf_secret", "afs_secret", "authFlowSessionId",
                "otpChallengeId", "otpCode");
    }

    @Test
    void mapperIncludesLocalizedErrorMessage() {
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.VERIFICATION_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED,
                        "identityValue", "We are unable to verify your details. Please review and try again.")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        var response = mapper.toMutationResponse(result);

        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("registration.start.verificationFailed");
            assertThat(error.message()).isEqualTo("We are unable to verify your details. Please review and try again.");
        });
    }

    @Test
    void mapperResolvesRegistrationErrorMessageThroughResolver() {
        ErrorMessageResolver resolver = (code, locale, accountEnv, trustCode, schemeType) -> "resolved-message";
        var localMapper = new RegistrationStartWebMapper(Duration.ofMinutes(5), resolver);
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.VERIFICATION_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(RegistrationWorkflowErrorKeys.REGISTRATION_START_VERIFICATION_FAILED,
                        "identityValue")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        var response = localMapper.toMutationResponse(result, "zh-HK");

        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("registration.start.verificationFailed");
            assertThat(error.message()).isEqualTo("resolved-message");
        });
    }

    @Test
    void messageDeliveryFailureMapsToDownstreamErrorStatus() {
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.MESSAGE_DELIVERY_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(
                        RegistrationWorkflowErrorKeys.REGISTRATION_MESSAGE_DELIVERY_FAILED,
                        "email")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        var response = mapper.toMutationResponse(result, "en");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.DOWNSTREAM_ERROR);
        assertThat(response.result()).isNotNull();
        assertThat(response.errors()).singleElement()
                .satisfies(error -> assertThat(error.code()).isEqualTo("registration.messageDelivery.failed"));
    }

    @Test
    void systemFailureMapsToSystemErrorStatus() {
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.SYSTEM_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(new StartRegistrationError(
                        RegistrationWorkflowErrorKeys.WORKFLOW_SYSTEM_UNAVAILABLE,
                        "PAGE")),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        var response = mapper.toMutationResponse(result, "en");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
        assertThat(response.result()).isNotNull();
    }

}
