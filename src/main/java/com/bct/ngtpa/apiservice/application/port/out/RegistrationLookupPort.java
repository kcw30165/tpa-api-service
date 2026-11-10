package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import reactor.core.publisher.Mono;

public interface RegistrationLookupPort {
  Mono<RegistrationLookupResult> searchMember(RegistrationLookupCommand command);

  /** Classifies an original failure from {@link #searchMember} without repeating the lookup. */
  FailureOrigin classifySearchMemberFailure(Throwable failure);
}
