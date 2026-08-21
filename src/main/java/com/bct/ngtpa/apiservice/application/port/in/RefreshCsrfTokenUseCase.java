package com.bct.ngtpa.apiservice.application.port.in;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenResult;
import reactor.core.publisher.Mono;

public interface RefreshCsrfTokenUseCase {
  Mono<RefreshCsrfTokenResult> refresh(RefreshCsrfTokenCommand command);
}
