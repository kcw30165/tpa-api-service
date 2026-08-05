package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimRequest;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TrpChkCustIdTransport {
    Mono<TrpChkCustIdApimEnvelope> post(TrpChkCustIdApimRequest request, String correlationId);
}