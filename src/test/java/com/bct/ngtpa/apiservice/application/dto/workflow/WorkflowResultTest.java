package com.bct.ngtpa.apiservice.application.dto.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowResultTest {

    @Test
    void workflowResultSuccessCarriesNextStepAndPayload() {
        var result = WorkflowResult.success(WorkflowNextStep.VERIFY_OTP, new SamplePayload("m****r@example.com", 300));

        assertThat(result.status()).isEqualTo(WorkflowResultStatus.SUCCESS);
        assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(result.payload()).isEqualTo(new SamplePayload("m****r@example.com", 300));
        assertThat(result.errors()).isEmpty();
        assertThat(result.isSuccessLike()).isTrue();
    }

    @Test
    void workflowResultDefaultsCollectionsToEmpty() {
        var result = new WorkflowResult<String>(
                WorkflowResultStatus.SUCCESS,
                WorkflowNextStep.NONE,
                "payload",
                null,
                null);

        assertThat(result.messages()).isEmpty();
        assertThat(result.errors()).isEmpty();
        assertThatThrownBy(() -> result.messages()
                .add(new WorkflowMessageDescriptor(WorkflowMessageType.INFO, "code", "message", "PAGE")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.errors()
                .add(new WorkflowErrorDescriptor(WorkflowErrorType.FORM, "code", "message", List.of(),
                        WorkflowErrorSeverity.ERROR, "SERVER")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void workflowResultFailureCarriesErrors() {
        var error = new WorkflowErrorDescriptor(
                WorkflowErrorType.FORM,
                "err.registration.start.invalid",
                "Invalid registration start request.",
                List.of(),
                WorkflowErrorSeverity.ERROR,
                "SERVER");

        var result = WorkflowResult.failure(WorkflowResultStatus.VALIDATION_FAILED, WorkflowNextStep.RESTART_FLOW,
                List.of(error));

        assertThat(result.isSuccessLike()).isFalse();
        assertThat(result.status()).isEqualTo(WorkflowResultStatus.VALIDATION_FAILED);
        assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
        assertThat(result.errors()).containsExactly(error);
        assertThat(result.messages()).isEmpty();
    }

    @Test
    void workflowResultWaitingSupportsDuplicateInProgress() {
        var result = WorkflowResult.duplicateInProgress(WorkflowNextStep.WAIT);

        assertThat(result.status()).isEqualTo(WorkflowResultStatus.WAITING);
        assertThat(result.nextStep()).isEqualTo(WorkflowNextStep.WAIT);
        assertThat(result.isSuccessLike()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void workflowResultToStringDoesNotExposePayload() {
        var result = WorkflowResult.success(WorkflowNextStep.VERIFY_OTP, "secret_payload_value");

        assertThat(result.toString())
                .contains("status=SUCCESS")
                .contains("nextStep=VERIFY_OTP")
                .contains("messageCount=0")
                .contains("errorCount=0")
                .doesNotContain("secret_payload_value");
    }

    private static java.util.List<String> recordComponentNames(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private record SamplePayload(String maskedDestination, int expiresInSeconds) {
    }
}
