package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TrpChkCustIdTransport {
  Mono<TrpChkCustIdApimEnvelope> post(TrpChkCustIdTransportCommand command);
}
