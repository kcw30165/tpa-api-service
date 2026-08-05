package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.TrpSearchRegedMemberRequest;
import reactor.core.publisher.Mono;
public interface TrpSearchRegedMemberTransport { Mono<TrpSearchRegedMemberEnvelope> post(TrpSearchRegedMemberRequest request, String requestId); }
