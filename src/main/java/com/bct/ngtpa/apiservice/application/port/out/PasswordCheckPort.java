package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckCommand;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import reactor.core.publisher.Mono;

public interface PasswordCheckPort {
  Mono<PasswordCheckResult> check(PasswordCheckCommand command);
}
