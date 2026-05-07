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
        log.warn("TrustBasedApimCredentialProfileResolver is a placeholder and should not be used in production.");
        return Mono.empty();
    }
}
