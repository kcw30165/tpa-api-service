package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameRecoveryLookupResultTest {

  private static UsernameRecoveryLookupCandidate candidate() {
    return new UsernameRecoveryLookupCandidate("DB", "DBA", true, "john.doe", null, null);
  }

  @Test
  void matchedCarriesCandidates() {
    var result =
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.MATCHED, List.of(candidate()));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    assertThat(result.candidates()).hasSize(1);
  }

  @Test
  void matchedRequiresAtLeastOneCandidate() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupResult(
                    UsernameRecoveryLookupResult.Status.MATCHED, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matched lookup requires at least one candidate");
  }

  @Test
  void nonMatchedOutcomesCarryNoCandidates() {
    for (var status :
        new UsernameRecoveryLookupResult.Status[] {
          UsernameRecoveryLookupResult.Status.NOT_FOUND,
          UsernameRecoveryLookupResult.Status.CONTRACT_INVALID,
          UsernameRecoveryLookupResult.Status.UNAVAILABLE,
          UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME
        }) {
      var result = new UsernameRecoveryLookupResult(status, List.of());
      assertThat(result.candidates()).isEmpty();
    }
  }

  @Test
  void nonMatchedOutcomeRejectsCandidates() {
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupResult(
                    UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of(candidate())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("non-matched lookup must not contain candidates");
  }

  @Test
  void requiresStatusAndCopiesCandidateList() {
    assertThatThrownBy(() -> new UsernameRecoveryLookupResult(null, List.of()))
        .isInstanceOf(NullPointerException.class);

    var result =
        new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.NOT_FOUND, null);
    assertThat(result.candidates()).isEmpty();
    assertThatThrownBy(() -> result.candidates().add(candidate()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void toStringDoesNotDiscloseCandidateUsernames() {
    var result =
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.MATCHED, List.of(candidate()));

    assertThat(result.toString())
        .doesNotContain("john.doe")
        .contains("status=MATCHED", "candidateCount=1");
  }
}
