package com.bct.ngtpa.apiservice.application.usecase.auth.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import org.junit.jupiter.api.Test;

class AuthenticatedLogoutContractTest {
  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void commandRequiresOpaqueSessionAndNormalizesCorrelationId() {
    var command = new AuthenticatedLogoutCommand(SID, "  req-1  ");
    assertThat(command.authenticatedSessionId()).isEqualTo(SID);
    assertThat(command.correlationId()).isEqualTo("req-1");
    assertThat(command.toString()).doesNotContain(SID.value(), "req-1");
  }

  @Test
  void commandRejectsMissingSession() {
    assertThatThrownBy(() -> new AuthenticatedLogoutCommand(null, "req-1"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void resultAllowsOnlyTerminalPublicOutcomes() {
    assertThat(AuthenticatedLogoutResult.loggedOut().status())
        .isEqualTo(AuthenticatedLogoutStatus.LOGGED_OUT);
    assertThat(AuthenticatedLogoutResult.alreadyLoggedOut().status())
        .isEqualTo(AuthenticatedLogoutStatus.ALREADY_LOGGED_OUT);
    assertThat(AuthenticatedLogoutResult.failed().status())
        .isEqualTo(AuthenticatedLogoutStatus.FAILED);
    assertThat(AuthenticatedLogoutResult.loggedOut().successful()).isTrue();
    assertThat(AuthenticatedLogoutResult.alreadyLoggedOut().successful()).isTrue();
    assertThat(AuthenticatedLogoutResult.failed().successful()).isFalse();
  }
}
