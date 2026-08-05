package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class CsrfHttpResponsesTest {

    @Test
    void missingTokenReturnsUnauthorizedWithAuthenticationRequired() {
        StepVerifier.create(CsrfHttpResponses.missingToken())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
                    assertThat(entity.getBody().result()).isNull();
                    assertThat(entity.getBody().errors()).singleElement().satisfies(error -> {
                        assertThat(error.type()).isEqualTo("AUTHENTICATION_REQUIRED");
                        assertThat(error.code()).isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
                        assertThat(error.targets()).containsExactly("PAGE");
                        assertThat(error.source()).isEqualTo("SERVER");
                    });
                })
                .verifyComplete();
    }

    @Test
    void missingTokenBodyIsGenericAcrossWorkflowPayloadTypes() {
        StepVerifier.create(CsrfHttpResponses.<String>missingToken())
                .assertNext(entity -> assertThat(entity.getBody().result()).isNull())
                .verifyComplete();
    }
}