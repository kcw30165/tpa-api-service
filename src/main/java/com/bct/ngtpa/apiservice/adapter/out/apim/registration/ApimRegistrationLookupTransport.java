package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchUnregisteredMemberApimRequest;
import reactor.core.publisher.Mono;

public interface ApimRegistrationLookupTransport {
    Mono<ApimRegistrationLookupEnvelope> postSearchUnregisteredMember(
            String path,
            TrpSearchUnregisteredMemberApimRequest request);
}
