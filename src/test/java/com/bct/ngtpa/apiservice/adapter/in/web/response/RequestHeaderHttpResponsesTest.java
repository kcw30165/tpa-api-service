package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

/**
 * Focused tests for the shared missing required syntactic header transport response.
 *
 * <p>Contract:
 *
 * <ul>
 *   <li>400 BAD_REQUEST with {@code VALIDATION_FAILED};
 *   <li>single FIELD error with the approved generic code {@code err.request.validation.failed},
 *       the caller-supplied canonical header target, and source {@code SERVER};
 *   <li>no {@code missingRequestId()} — {@code RequiredRequestIdWebFilter} owns missing/blank
 *       {@code X-Request-Id};
 *   <li>no {@code missingSchemeRef()} — Scheme-Ref validity/ownership is feature behavior;
 *   <li>generic {@code MutationResponse<T>} typing.
 * </ul>
 */
class RequestHeaderHttpResponsesTest {

  @Test
  void missingRequiredHeaderReturns400ValidationFailedWithHeaderTarget() {
    StepVerifier.create(RequestHeaderHttpResponses.missingRequiredHeader("Scheme-Ref"))
        .assertNext(
            entity -> {
              assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(entity.getBody()).isNotNull();
              assertThat(entity.getBody().success()).isFalse();
              assertThat(entity.getBody().status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
              assertThat(entity.getBody().result()).isNull();
              assertThat(entity.getBody().messages()).isEmpty();
              assertThat(entity.getBody().errors())
                  .singleElement()
                  .satisfies(
                      error -> {
                        assertThat(error.type()).isEqualTo("FIELD");
                        assertThat(error.code()).isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED);
                        assertThat(error.message()).isNull();
                        assertThat(error.targets()).containsExactly("Scheme-Ref");
                        assertThat(error.severity()).isEqualTo("ERROR");
                        assertThat(error.source()).isEqualTo("SERVER");
                      });
            })
        .verifyComplete();
  }

  @Test
  void missingRequiredHeaderBodyIsGenericAcrossWorkflowPayloadTypes() {
    StepVerifier.create(RequestHeaderHttpResponses.<String>missingRequiredHeader("Scheme-Ref"))
        .assertNext(
            entity -> {
              assertThat(entity.getBody()).isNotNull();
              assertThat(entity.getBody().result()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void missingRequiredHeaderUsesCanonicalApprovedGenericCodeOnly() {
    StepVerifier.create(RequestHeaderHttpResponses.missingRequiredHeader("Idempotency-Key"))
        .assertNext(
            entity ->
                assertThat(entity.getBody().errors())
                    .isNotEmpty()
                    .allSatisfy(
                        error ->
                            assertThat(error.code())
                                .isEqualTo(ErrorCodes.REQUEST_VALIDATION_FAILED)))
        .verifyComplete();
  }

  @Test
  void missingRequiredHeaderRejectsNullTarget() {
    assertThatThrownBy(() -> RequestHeaderHttpResponses.missingRequiredHeader(null))
        .isInstanceOf(NullPointerException.class);
  }
}
