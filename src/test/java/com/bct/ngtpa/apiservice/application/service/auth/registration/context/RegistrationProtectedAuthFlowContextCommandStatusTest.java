package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistrationProtectedAuthFlowContextCommandStatusTest {

  @Test
  void legacyConstructorRemainsRestrictedToOtpSent() {
    var command =
        RegistrationProtectedAuthFlowContextCommand.forOtpSent(
            new AuthFlowSessionId("afs_safe"), "csrf-safe", null, null, "req-safe");

    assertThat(command.acceptedStatuses()).containsExactly(AuthFlowStatus.OTP_SENT);
  }

  @Test
  void completionCallerCanRequireUsernameVerified() {
    var command =
        new RegistrationProtectedAuthFlowContextCommand(
            new AuthFlowSessionId("afs_safe"),
            "csrf-safe",
            null,
            null,
            "req-safe",
            Set.of(AuthFlowStatus.USERNAME_VERIFIED));

    assertThat(command.acceptedStatuses()).containsExactly(AuthFlowStatus.USERNAME_VERIFIED);
    assertThat(command.toString()).doesNotContain("afs_safe", "csrf-safe", "req-safe");
  }
}
