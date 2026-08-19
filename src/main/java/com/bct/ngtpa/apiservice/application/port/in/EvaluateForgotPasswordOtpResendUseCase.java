package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.*;
import reactor.core.publisher.Mono;

public interface EvaluateForgotPasswordOtpResendUseCase {
  Mono<ForgotPasswordOtpResendResult> evaluate(ForgotPasswordOtpResendCommand command);
}
