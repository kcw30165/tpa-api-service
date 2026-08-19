package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ApimRegistrationUsernameAvailabilityTransport {
  Mono<TrpChkCustIdApimEnvelope> postChkCustId(String path, TrpChkCustIdApimRequest request);
}
