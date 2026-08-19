package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Focused tests for the typed override enum used by {@link HttpStatusClassification}. */
class HttpStatusClassificationOverrideTest {

  @Test
  void shouldMapConflictToConflictHttpStatus() {
    assertThat(HttpStatusClassificationOverride.CONFLICT.httpStatus())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldMapTooManyRequestsToTooManyRequestsHttpStatus() {
    assertThat(HttpStatusClassificationOverride.TOO_MANY_REQUESTS.httpStatus())
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void shouldMapForbiddenToForbiddenHttpStatus() {
    assertThat(HttpStatusClassificationOverride.FORBIDDEN.httpStatus())
        .isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldContainExactlyTheThreeApprovedValues() {
    assertThat(HttpStatusClassificationOverride.values())
        .containsExactly(
            HttpStatusClassificationOverride.CONFLICT,
            HttpStatusClassificationOverride.TOO_MANY_REQUESTS,
            HttpStatusClassificationOverride.FORBIDDEN);
  }

  @Test
  void shouldCarryNoErrorCodeOrFeatureMetadata() {
    assertThat(Arrays.stream(HttpStatusClassificationOverride.class.getDeclaredFields()))
        .allSatisfy(
            field ->
                assertThat(field.getType())
                    .isIn(
                        HttpStatus.class,
                        HttpStatusClassificationOverride.class,
                        HttpStatusClassificationOverride[].class));
  }
}
