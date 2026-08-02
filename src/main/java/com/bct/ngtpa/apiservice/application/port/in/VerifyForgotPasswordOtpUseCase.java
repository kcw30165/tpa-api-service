package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.verify.VerifyForgotPasswordOtpResult;
import reactor.core.publisher.Mono;

public interface VerifyForgotPasswordOtpUseCase {
    Mono<VerifyForgotPasswordOtpResult> verify(VerifyForgotPasswordOtpCommand command);
}