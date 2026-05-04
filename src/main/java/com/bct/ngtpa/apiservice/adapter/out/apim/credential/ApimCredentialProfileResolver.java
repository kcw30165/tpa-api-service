package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import reactor.core.publisher.Mono;

public interface ApimCredentialProfileResolver {
    Mono<ApimCredentialProfile> resolve(ApimCredentialResolutionContext context);
}
