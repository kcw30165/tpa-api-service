package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimRequest;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface ApimRegistrationCompletionTransport {
    Mono<TrpRegMemberApimEnvelope> postRegMember(String path, TrpRegMemberApimRequest request);
}
