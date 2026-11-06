package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameRecoveryStartResultWithoutSessionTest {

  @Test
  void memberNotFoundCarriesNoSessionDataAndNoSchemes() {
    UsernameRecoveryStartResult result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, List.of());

    assertThat(result.outcome()).isEqualTo(UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
    assertThat(result.authFlowSessionId()).isNull();
    assertThat(result.csrfToken()).isNull();
    assertThat(result.expiresInSeconds()).isNull();
    assertThat(result.schemes()).isEmpty();
  }

  @Test
  void aSessionCreatedOutcomeIsRejectedBecauseItNeedsSessionData() {
    assertThatThrownBy(
            () ->
                UsernameRecoveryStartResult.withoutSession(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
