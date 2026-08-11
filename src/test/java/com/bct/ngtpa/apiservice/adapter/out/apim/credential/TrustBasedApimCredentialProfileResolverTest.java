package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

class TrustBasedApimCredentialProfileResolverTest {

    @Test
    void resolveFailsWithUnsupportedError() {
        TrustBasedApimCredentialProfileResolver resolver = new TrustBasedApimCredentialProfileResolver();
        ApimCredentialResolutionContext context = new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER, "SIT");

        StepVerifier.create(resolver.resolve(context))
                .expectErrorMatches(ex -> ex instanceof com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException
                        && ex.getMessage().contains("unsupported"))
                .verify();
    }
}