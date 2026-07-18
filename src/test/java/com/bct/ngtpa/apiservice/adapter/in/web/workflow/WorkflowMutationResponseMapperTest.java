package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageRef;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResult;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultPayload;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResultStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowMutationResponseMapperTest {

    private final WorkflowMutationResponseMapper mapper = new WorkflowMutationResponseMapper();

    @Test
    void workflowMessageDescriptorMapsToApiMessage() {
        var result = new WorkflowResult<String>(
                WorkflowResultStatus.SUCCESS,
                WorkflowNextStep.VERIFY_OTP,
                "payload",
                null,
                List.of(new WorkflowMessageDescriptor(
                        WorkflowMessageType.INFO,
                        "registration.otp.resent",
                        "A new verification code has been sent.",
                        "PAGE")),
                List.of());

        var response = mapper.toMutationResponse(result);

        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().type()).isEqualTo("INFO");
        assertThat(response.messages().getFirst().code()).isEqualTo("registration.otp.resent");
        assertThat(response.messages().getFirst().target()).isEqualTo("PAGE");
    }

    @Test
    void workflowErrorDescriptorMapsToApiError() {
        var result = WorkflowResult.failure(WorkflowResultStatus.VALIDATION_FAILED, WorkflowNextStep.RESTART_FLOW, List.of(
                new WorkflowErrorDescriptor(
                        WorkflowErrorType.FIELD,
                        "registration.start.email.invalid",
                        "Invalid email address.",
                        List.of("email"),
                        WorkflowErrorSeverity.ERROR,
                        "SERVER")));

        var response = mapper.toMutationResponse(result);

        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().type()).isEqualTo("FIELD");
        assertThat(response.errors().getFirst().code()).isEqualTo("registration.start.email.invalid");
        assertThat(response.errors().getFirst().targets()).containsExactly("email");
        assertThat(response.errors().getFirst().severity()).isEqualTo("ERROR");
        assertThat(response.errors().getFirst().source()).isEqualTo("SERVER");
    }

    @Test
    void successWorkflowResultMapsToMutationResponse() {
        var payload = new RegistrationStartPayload("m****r@example.com", 300);
        var result = WorkflowResult.success(WorkflowNextStep.VERIFY_OTP, payload);

        var response = mapper.toMutationResponse(result);

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.VERIFY_OTP);
        assertThat(response.result().data()).isEqualTo(payload);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void validationFailureWorkflowResultMapsToMutationResponse() {
        var result = WorkflowResult.failure(WorkflowResultStatus.VALIDATION_FAILED, WorkflowNextStep.RESTART_FLOW, List.of(
                new WorkflowErrorDescriptor(WorkflowErrorType.FORM, "registration.start.invalid", "Invalid request.", List.of(), WorkflowErrorSeverity.ERROR, "SERVER")));

        var response = mapper.toMutationResponse(result);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNotNull();
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RESTART_FLOW);
        assertThat(response.errors()).hasSize(1);
    }

    @Test
    void systemFailureWorkflowResultMapsToSystemErrorStatus() {
        var result = WorkflowResult.failure(WorkflowResultStatus.SYSTEM_FAILED, WorkflowNextStep.RESTART_FLOW, List.of(
                new WorkflowErrorDescriptor(WorkflowErrorType.SYSTEM, "registration.system.unavailable", "Service unavailable.", List.of(), WorkflowErrorSeverity.ERROR, "SERVER")));

        var response = mapper.toMutationResponse(result);

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    }

    @Test
    void workflowResultMapperDoesNotPutRequestIdInBody() {
        var result = WorkflowResult.success(
                WorkflowNextStep.REGISTRATION_SUCCESS,
                new RegistrationStartPayload("m****r@example.com", 300),
                new WorkflowPageRef("registration-success", "hasRemainingTrusts"));

        var response = mapper.toMutationResponse(result);

        assertThat(response.result().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("requestId", "xRequestId", "X-Request-Id");
        assertThat(response.messages()).allSatisfy(message -> assertThat(message.toString()).doesNotContain("requestId", "xRequestId", "X-Request-Id"));
        assertThat(response.errors()).allSatisfy(error -> assertThat(error.toString()).doesNotContain("requestId", "xRequestId", "X-Request-Id"));
    }

    @Test
    void mappedPayloadCarriesPageRefWithoutLocalizedContent() {
        var pageRef = new WorkflowPageRef("registration-success", "hasRemainingTrusts");
        var response = mapper.toMutationResponse(WorkflowResult.success(WorkflowNextStep.REGISTRATION_SUCCESS, "ok", pageRef));
        WorkflowResultPayload<String> payload = response.result();

        assertThat(payload.pageRef()).isEqualTo(pageRef);
        assertThat(payload.pageRef().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("pageId", "variant");
    }

    private record RegistrationStartPayload(String maskedDestination, int expiresInSeconds) {
    }
}
