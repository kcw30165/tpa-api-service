package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameResult;
import reactor.core.publisher.Mono;

public interface CheckRegistrationUsernameUseCase {
  Mono<CheckRegistrationUsernameResult> check(CheckRegistrationUsernameCommand command);
}
