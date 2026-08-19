package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpResult;
import reactor.core.publisher.Mono;

public interface ResendForgotPasswordOtpUseCase {
  Mono<ResendForgotPasswordOtpResult> resend(ResendForgotPasswordOtpCommand command);
}
