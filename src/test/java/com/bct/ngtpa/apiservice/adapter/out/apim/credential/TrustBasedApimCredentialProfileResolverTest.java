package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class TrustBasedApimCredentialProfileResolverTest {

    @Test
    void resolveCompletesWithoutReturningAProfile() {
        TrustBasedApimCredentialProfileResolver resolver = new TrustBasedApimCredentialProfileResolver();
        ApimCredentialResolutionContext context = new ApimCredentialResolutionContext(
                "trust-1", "scheme-1", "policy-1", "member-1", "user-1", "SIT", "EMPLOYEE", "op", null);

        StepVerifier.create(resolver.resolve(context))
                .verifyComplete();
    }
}