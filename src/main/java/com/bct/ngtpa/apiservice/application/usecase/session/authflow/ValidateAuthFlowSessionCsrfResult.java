package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import java.util.Objects;

public record ValidateAuthFlowSessionCsrfResult(AuthFlowSessionCsrfValidationStatus status, boolean valid) {
    public ValidateAuthFlowSessionCsrfResult {
        Objects.requireNonNull(status, "status must not be null");
    }
}
