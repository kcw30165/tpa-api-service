package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import java.util.Objects;

public record VerifyForgotPasswordOtpResult(
        VerifyForgotPasswordOtpStatus status,
        WorkflowNextStep nextStep) {

    public VerifyForgotPasswordOtpResult {
        Objects.requireNonNull(status, "status must not be null");
        nextStep = nextStep == null ? WorkflowNextStep.NONE : nextStep;
    }
}