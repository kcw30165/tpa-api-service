package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import reactor.core.publisher.Mono;

/**
 * Strict resolver contract: requires explicit database scope and minimal routing context.
 */
public interface ApimCredentialProfileResolver {
    Mono<ApimCredentialProfile> resolve(ApimDatabaseScope scope, ApimCredentialResolutionContext context);
}
