package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.*;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface LoginProtectedAuthFlowContextLoader {
  Mono<LoginProtectedAuthFlowContextResult> load(LoginProtectedAuthFlowContextCommand command);
}
