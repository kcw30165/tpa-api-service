package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorDescriptor;
import java.util.List;
import java.util.Objects;

public record CancelForgotPasswordResult(
        CancelForgotPasswordStatus status,
        boolean clearAuthFlowCookie,
        boolean clearXsrfCookie,
        List<WorkflowErrorDescriptor> errors) {

    public CancelForgotPasswordResult {
        Objects.requireNonNull(status, "status must not be null");
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean successfulCleanup() {
        return switch (status) {
            case CANCELLED, SESSION_NOT_FOUND, ALREADY_CANCELLED, ALREADY_TERMINAL -> true;
            default -> false;
        };
    }
}