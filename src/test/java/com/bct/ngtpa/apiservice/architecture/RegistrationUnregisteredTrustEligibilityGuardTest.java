package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationUnregisteredTrustEligibilityGuardTest {
  @Test
  void otpVerifyReturnsAndPersistsOnlyExplicitlyUnregisteredTrusts() throws Exception {
    String source =
        read("application/usecase/auth/registration/otp/VerifyRegistrationOtpService.java");
    assertThat(source)
        .contains("Boolean.FALSE.equals(account.registered())")
        .contains("store(command.authFlowSessionId(), unregisteredAccounts, clock.instant())");
  }

  @Test
  void usernameCheckAuthorizesOnlyTheServerOwnedUnregisteredTrustSet() throws Exception {
    String source =
        read(
            "application/usecase/auth/registration/username/CheckRegistrationUsernameService.java");
    assertThat(source)
        .contains("RegistrationEligibleTrustSet.contains")
        .contains("validationFailure(ERROR_SELECTED_TRUST_INVALID)");
  }

  @Test
  void completionReturnsOnlyExplicitlyUnregisteredRemainingTrusts() throws Exception {
    String source =
        read("application/usecase/auth/registration/complete/CompleteRegistrationService.java");
    assertThat(source)
        .contains("remainingUnregisteredTrusts")
        .contains("Boolean.FALSE.equals(account.registered())")
        .contains("remainingUnregisteredTrusts));");
  }

  private String read(String relativePath) throws Exception {
    return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice", relativePath));
  }
}
