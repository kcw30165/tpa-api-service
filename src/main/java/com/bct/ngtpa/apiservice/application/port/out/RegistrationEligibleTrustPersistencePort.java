package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupAccount;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Mono;

public interface RegistrationEligibleTrustPersistencePort {
  Mono<Void> store(
      AuthFlowSessionId sessionId, List<RegistrationLookupAccount> accounts, Instant now);
}
