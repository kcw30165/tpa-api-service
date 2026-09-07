package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimRequest;
import reactor.core.publisher.Mono;

public interface ApimRegistrationLookupTransport {
  Mono<ApimRegistrationLookupEnvelope> postSearchMember(
      String path, TrpSearchMemberApimRequest request);
}
