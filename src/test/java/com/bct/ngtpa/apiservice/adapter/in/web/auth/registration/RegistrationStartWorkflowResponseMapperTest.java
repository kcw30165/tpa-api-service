package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto.RegistrationStartWorkflowDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyStartOutcome;
import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationError;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationResultStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegistrationStartWorkflowResponseMapperTest {

    private final RegistrationStartWebMapper mapper = new RegistrationStartWebMapper(Duration.ofMinutes(5));

    @Test
    void startRegistrationResultMapsToWorkflowResultSuccess() {
        var response = mapper.toMutationResponse(started(), "en");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(response.result().data()).isInstanceOf(RegistrationStartWorkflowDataResponse.class);
        assertThat(response.result().data().maskedDestination()).isEqualTo("m****r@example.com");
        assertThat(response.result().data().csrfToken()).isEqualTo("csrf_token_safe");
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void startRegistrationDuplicateInProgressMapsToWait() {
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.DUPLICATE_IN_PROGRESS,
                WorkflowNextStep.WAIT,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(),
                RegistrationIdempotencyStartOutcome.DUPLICATE_IN_PROGRESS,
                null);

        var response = mapper.toMutationResponse(result, "en");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.WAIT);
        assertThat(response.result().data().csrfToken()).isNull();
    }

    @Test
    void startRegistrationValidationFailureMapsToApiErrors() {
        var error = new StartRegistrationError(
                SharedWorkflowErrorKey.IDEMPOTENCY_MISSING,
                "Idempotency-Key",
                null);
        var result = new StartRegistrationResult(
                StartRegistrationResultStatus.VALIDATION_FAILED,
                WorkflowNextStep.NONE,
                null,
                0,
                0,
                0,
                null,
                null,
                List.of(),
                List.of(error),
                RegistrationIdempotencyStartOutcome.STARTED,
                null);

        var response = mapper.toMutationResponse(result, "en");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNotNull();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().code()).isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value());
        assertThat(response.errors().getFirst().targets()).containsExactly("Idempotency-Key");
    }

    @Test
    void missingIdempotencyKeyUsesGenericMutationResponse() {
        var response = mapper.missingIdempotencyKeyResponse("en");

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNotNull();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().code()).isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value());
        assertThat(response.errors().getFirst().targets()).containsExactly("Idempotency-Key");
    }

    @Test
    void workflowAlignedResponseDoesNotExposeAuthFlowSessionId() {
        var response = mapper.toMutationResponse(started(), "en");

        assertThat(response.toString())
                .doesNotContain("afs_secret", "authFlowSessionId", "auth_flow_sid", "sessionId");
    }

    @Test
    void workflowAlignedResponsePreservesCsrfTokenInBodyButToStringRedactsIt() {
        var response = mapper.toMutationResponse(started(), "en");

        assertThat(response.result().data().csrfToken()).isEqualTo("csrf_token_safe");
        assertThat(response.result().data().toString()).doesNotContain("csrf_token_safe");
    }


    @Test
    void workflowDataDoesNotDuplicateNextStep() {
        var response = mapper.toMutationResponse(started(), "en");

        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(response.result().data().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("nextStep");
    }


    @Test
    void workflowDataDoesNotDuplicateNextStepAndUsesCentralizedOtpPageRef() {
        var response = mapper.toMutationResponse(started(), "en");

        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(response.result().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("nextStep", "data");
    }

    private static StartRegistrationResult started() {
        return new StartRegistrationResult(
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
                null);
    }
}
