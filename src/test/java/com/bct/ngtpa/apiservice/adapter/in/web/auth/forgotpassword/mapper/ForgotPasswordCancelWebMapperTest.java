package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorSeverity;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorType;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordCancelWebMapperTest {
    private final ForgotPasswordCancelWebMapper mapper = new ForgotPasswordCancelWebMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successfulCleanupOutcomesAlwaysReturnReturnToLoginWithEmptyData() throws Exception {
        for (CancelForgotPasswordStatus status : new CancelForgotPasswordStatus[] {
                CancelForgotPasswordStatus.CANCELLED,
                CancelForgotPasswordStatus.SESSION_NOT_FOUND,
                CancelForgotPasswordStatus.ALREADY_CANCELLED,
                CancelForgotPasswordStatus.ALREADY_TERMINAL}) {
            var response = mapper.toResponse(new CancelForgotPasswordResult(status, true, true, List.of()));
            var json = objectMapper.valueToTree(response);

            assertThat(response.success()).isTrue();
            assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
            assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
            assertThat(response.messages()).isEmpty();
            assertThat(response.errors()).isEmpty();
            assertThat(json.at("/result/data").isObject()).isTrue();
            assertThat(json.at("/result/data").size()).isZero();
            assertThat(json.toString()).doesNotContain(
                    status.name(),
                    "afs_",
                    "SessionRevision",
                    "redis",
                    "csrf",
                    "otp");
        }
    }

    @Test
    void internalStatusNamesNeverLeakIntoPublicBody() throws Exception {
        var response = mapper.toResponse(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.CANCELLED, true, true, List.of()));
        var json = objectMapper.valueToTree(response);
        assertThat(json.toString()).doesNotContain("CANCELLED", "SESSION_NOT_FOUND", "ALREADY_TERMINAL");
    }

    @Test
    void invalidAuthFlowReturnsValidationFailureWithReturnToLoginAndAuthFlowError() throws Exception {
        var response = mapper.toResponse(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.INVALID_AUTH_FLOW,
                false,
                false,
                List.of(error(WorkflowErrorType.FORM, "err.forgot-password.authFlow.invalid", "PAGE"))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("err.forgot-password.authFlow.invalid");
            assertThat(error.message()).isNull();
            assertThat(error.targets()).isEmpty();
        });
    }

    @Test
    void csrfInvalidReturnsValidationFailureWithReturnToLoginAndCsrfError() throws Exception {
        var response = mapper.toResponse(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.CSRF_INVALID,
                false,
                false,
                List.of(error(WorkflowErrorType.FORM, "err.forgot-password.csrf.invalid", "PAGE"))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("err.forgot-password.csrf.invalid");
            assertThat(error.targets()).isEmpty();
        });
    }

    @Test
    void systemFailedReturnsSystemErrorWithReturnToLoginAndSanitizedError() throws Exception {
        var response = mapper.toResponse(new CancelForgotPasswordResult(
                CancelForgotPasswordStatus.SYSTEM_FAILED,
                false,
                false,
                List.of(error(WorkflowErrorType.SYSTEM, "err.system.unavailable", "PAGE"))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("err.system.unavailable");
            assertThat(error.targets()).isEmpty();
        });
        assertThat(response.toString()).doesNotContain("PAGE");
    }

    @Test
    void missingSessionHelperReturnsSuccessfulCleanupWithReturnToLogin() throws Exception {
        var response = mapper.missingSession();
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void missingCsrfHelperReturnsValidationFailureWithCsrfError() throws Exception {
        var response = mapper.missingCsrf();
        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result().nextStep()).isEqualTo(WorkflowNextStep.RETURN_TO_LOGIN);
        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.code()).isEqualTo("err.forgot-password.csrf.invalid");
            assertThat(error.targets()).isEmpty();
        });
    }

    private static WorkflowErrorDescriptor error(WorkflowErrorType type, String code, String target) {
        return new WorkflowErrorDescriptor(
                type, code, null, target == null ? List.of() : List.of(target),
                WorkflowErrorSeverity.ERROR, "SERVER");
    }
}