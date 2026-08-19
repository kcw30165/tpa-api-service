package com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Username Recovery effective destination command.
 *
 * <p>Verifies the command carries the session-resident verified destination without mutation and
 * that {@code toString()} never discloses the destination value.
 */
class UsernameRecoveryEffectiveDestinationCommandTest {

  @Test
  void carriesVerifiedDestinationWithoutMutation() {
    var verified =
        new UsernameRecoveryVerifiedDestination(
            "member@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL);

    var command = new UsernameRecoveryEffectiveDestinationCommand(verified);

    assertThat(command.verifiedDestination()).isSameAs(verified);
  }

  @Test
  void rejectsNullVerifiedDestination() {
    assertThatThrownBy(() -> new UsernameRecoveryEffectiveDestinationCommand(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("verifiedDestination");
  }

  @Test
  void toStringNeverDisclosesDestinationValue() {
    var command =
        new UsernameRecoveryEffectiveDestinationCommand(
            new UsernameRecoveryVerifiedDestination(
                "member@example.test", UsernameRecoveryVerifiedDestination.Channel.EMAIL));

    assertThat(command.toString())
        .contains("<redacted>")
        .contains("EMAIL")
        .doesNotContain("member@example.test");
  }
}
