package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimRequest;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface TrpMemberLoginTransport {
    Mono<ApimResponseEnvelope<TrpMemberLoginApimDataItem>> post(TrpMemberLoginApimRequest request);
}
