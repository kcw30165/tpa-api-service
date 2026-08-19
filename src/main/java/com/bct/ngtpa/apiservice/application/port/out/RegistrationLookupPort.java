package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupResult;
import reactor.core.publisher.Mono;

public interface RegistrationLookupPort {
  Mono<RegistrationLookupResult> searchUnregisteredMember(RegistrationLookupCommand command);
}
