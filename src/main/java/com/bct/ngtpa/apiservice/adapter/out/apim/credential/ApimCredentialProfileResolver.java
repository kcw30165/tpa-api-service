package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException;

/**
 * Strict resolver contract: requires explicit database scope and minimal routing context.
 * Legacy single-arg resolver remains abstract for backwards-compatibility but must fail closed when used.
 */
public interface ApimCredentialProfileResolver {
    Mono<ApimCredentialProfile> resolve(ApimCredentialResolutionContext context);

    default Mono<ApimCredentialProfile> resolve(ApimDatabaseScope scope, ApimCredentialResolutionContext context) {
        return Mono.error(new ApimProfileResolutionException("explicit resolve(scope,context) is required; legacy single-arg resolver unsupported for strict routing"));
    }
}
