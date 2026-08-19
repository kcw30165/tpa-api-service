package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.*;
import reactor.core.publisher.Mono;

public interface ForgotPasswordDetailsLookupPort {
  Mono<ForgotPasswordDetailsLookupResult> verify(ForgotPasswordDetailsLookupCommand command);
}
