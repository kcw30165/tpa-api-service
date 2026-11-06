package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.START_PATH;
import static com.bct.ngtpa.apiservice.adapter.in.web.controller.UsernameRecoveryBoundaryFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Preserved contract: downstream member-not-found and the existing empty-data mapping still end in
 * the 422 verification-failed rejection with no session, cookie or CSRF.
 */
class UsernameRecoveryMemberNotFoundBoundaryPreservationTest {
  private final UsernameRecoveryBoundaryFixture fixture = new UsernameRecoveryBoundaryFixture();

  @Test
  void downstreamMemberNotFoundStillReturnsTheVerificationFailedRejection() {
    fixture.givenApimReturns("err.tpa.member.not.found", List.of());
    assertRejectedWithoutSession();
  }

  @Test
  void downstreamEmptyDataStillReturnsTheVerificationFailedRejection() {
    fixture.givenApimReturns("", List.of());
    assertRejectedWithoutSession();
  }

  @Test
  void downstreamMissingDataStillReturnsTheVerificationFailedRejection() {
    fixture.givenApimReturns("", null);
    assertRejectedWithoutSession();
  }

  private void assertRejectedWithoutSession() {
    fixture
        .client
        .post()
        .uri(START_PATH)
        .contentType(MediaType.APPLICATION_JSON)
        .header("Accept-Language", "en")
        .header("Idempotency-Key", "idem-boundary-nf")
        .bodyValue(validRequest())
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isEqualTo(422))
        .expectHeader()
        .doesNotExist(HttpHeaders.SET_COOKIE)
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(false)
        .jsonPath("$.status")
        .isEqualTo("BUSINESS_REJECTED")
        .jsonPath("$.result.nextStep")
        .isEqualTo("RESTART_FLOW")
        .jsonPath("$.result.data")
        .isEmpty()
        .jsonPath("$.errors[0].code")
        .isEqualTo("err.usernamerecovery.verification.failed")
        .jsonPath("$.errors[0].source")
        .isEqualTo("BFF");

    verify(fixture.apim).postEnvelope(any());
    assertThat(fixture.harness.stored.get()).isNull();
    assertThat(fixture.harness.creates.get()).isZero();
  }
}
