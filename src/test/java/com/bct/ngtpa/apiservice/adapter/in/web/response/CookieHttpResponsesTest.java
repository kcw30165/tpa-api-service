package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

/**
 * Focused tests for the shared missing/unusable auth-flow cookie transport
 * response.
 *
 * <p>Contract (mirrors the approved shared missing pre-auth transport artifact
 * response used by {@link CsrfHttpResponses}):
 *
 * <ul>
 * <li>401 UNAUTHORIZED with {@code BUSINESS_REJECTED};</li>
 * <li>single error with type {@code AUTHENTICATION_REQUIRED}, code
 * {@code err.security.authentication.required}, targets {@code [PAGE]},
 * source {@code SERVER};</li>
 * <li>no feature code, no Username Recovery next step, no {@code Set-Cookie}
 * header;</li>
 * <li>generic {@code MutationResponse<T>} typing.</li>
 * </ul>
 */
class CookieHttpResponsesTest {

    @Test
    void missingCookieReturnsUnauthorizedWithAuthenticationRequired() {
        StepVerifier.create(CookieHttpResponses.missingCookie())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
                    assertThat(entity.getBody().result()).isNull();
                    assertThat(entity.getBody().messages()).isEmpty();
                    assertThat(entity.getBody().errors()).singleElement().satisfies(error -> {
                        assertThat(error.type()).isEqualTo("AUTHENTICATION_REQUIRED");
                        assertThat(error.code()).isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED);
                        assertThat(error.message()).isNull();
                        assertThat(error.targets()).containsExactly("PAGE");
                        assertThat(error.severity()).isEqualTo("ERROR");
                        assertThat(error.source()).isEqualTo("SERVER");
                    });
                })
                .verifyComplete();
    }

    @Test
    void missingCookieBodyIsGenericAcrossWorkflowPayloadTypes() {
        StepVerifier.create(CookieHttpResponses.<String>missingCookie())
                .assertNext(entity -> {
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().result()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void missingCookieNeverWritesSetCookieHeader() {
        StepVerifier.create(CookieHttpResponses.missingCookie())
                .assertNext(entity -> {
                    assertThat(entity.getHeaders().isEmpty()).isTrue();
                    assertThat(entity.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
                })
                .verifyComplete();
    }

    @Test
    void missingCookieUsesOnlyApprovedGenericErrorKeys() {
        StepVerifier.create(CookieHttpResponses.missingCookie())
                .assertNext(entity -> assertThat(entity.getBody().errors())
                        .allSatisfy(error -> assertThat(error.code())
                                .isEqualTo(ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED)))
                .verifyComplete();
    }
}
