package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.idempotency.ForgotPasswordIdempotencyStartOutcome;

public final class ForgotPasswordOtpResendIdempotencyClassifier {
    public ForgotPasswordOtpResendEligibilityStatus classify(ForgotPasswordIdempotencyStartOutcome o) {
        return switch (o) {
            case STARTED -> ForgotPasswordOtpResendEligibilityStatus.ELIGIBLE;
            case DUPLICATE_IN_PROGRESS -> ForgotPasswordOtpResendEligibilityStatus.IDEMPOTENCY_IN_PROGRESS;
            case COMPLETED_REPLAY -> ForgotPasswordOtpResendEligibilityStatus.IDEMPOTENCY_COMPLETED_REPLAY;
            case KEY_REUSED_WITH_DIFFERENT_REQUEST -> ForgotPasswordOtpResendEligibilityStatus.IDEMPOTENCY_KEY_REUSED;
            case FAILED_EXISTING -> ForgotPasswordOtpResendEligibilityStatus.PREVIOUS_FAILED;
            case UNKNOWN_OUTCOME_EXISTING -> ForgotPasswordOtpResendEligibilityStatus.UNKNOWN_OUTCOME_BLOCKED;
        };
    }
}
