package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Placeholder for a future trust-based resolver.
 * TBC: mapping rules not yet confirmed; do not enable by default.
 */
@Service
@Slf4j
public class TrustBasedApimCredentialProfileResolver implements ApimCredentialProfileResolver {

    @Override
    public Mono<ApimCredentialProfile> resolve(ApimCredentialResolutionContext context) {
        // Strict resolver requires explicit scope; this placeholder must not be used.
        return Mono.error(new com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException(
                "TrustBasedApimCredentialProfileResolver unsupported in strict routing"));
    }

    // Keep legacy signature for source-compat but do not enable it.
    public Mono<ApimCredentialProfile> resolve(ApimDatabaseScope scope, ApimCredentialResolutionContext context) {
        log.warn("TrustBasedApimCredentialProfileResolver (legacy signature) should not be used in production.");
        return Mono.empty();
    }
}
