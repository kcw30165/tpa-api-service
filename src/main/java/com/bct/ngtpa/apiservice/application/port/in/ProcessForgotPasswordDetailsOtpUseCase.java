package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.*;
import reactor.core.publisher.Mono;

public interface ProcessForgotPasswordDetailsOtpUseCase {
  Mono<ForgotPasswordDetailsOtpResult> process(ForgotPasswordDetailsOtpCommand command);
}
