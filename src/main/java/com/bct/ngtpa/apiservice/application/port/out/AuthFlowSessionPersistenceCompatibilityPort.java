package com.bct.ngtpa.apiservice.application.port.out;

import reactor.core.publisher.Mono;

public interface AuthFlowSessionPersistenceCompatibilityPort {
  Mono<AuthFlowSessionPersistenceCompatibilityResult> establish(int configuredVersion);
}
