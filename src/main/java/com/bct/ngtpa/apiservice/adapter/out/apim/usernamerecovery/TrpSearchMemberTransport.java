package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import reactor.core.publisher.Mono;

/**
 * Transport boundary owning the raw TRPSearchMember APIM exchange and strict
 * routing.
 */
public interface TrpSearchMemberTransport {
    Mono<ApimResponseEnvelope<com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem>> post(
            com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest request,
            String correlationId);
}
