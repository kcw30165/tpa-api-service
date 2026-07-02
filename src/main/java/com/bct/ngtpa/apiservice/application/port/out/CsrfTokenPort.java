package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.security.csrf.CreateCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.CreateCsrfTokenResult;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.security.csrf.ValidateCsrfTokenResult;
import reactor.core.publisher.Mono;

public interface CsrfTokenPort {

    Mono<CreateCsrfTokenResult> createToken(CreateCsrfTokenCommand command);

    Mono<ValidateCsrfTokenResult> validateToken(ValidateCsrfTokenCommand command);

    Mono<Void> invalidateToken(String workflowRef);
}
