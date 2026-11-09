package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class M3R1aOwnershipActivationGuardTest {
  @Test
  void onlyApprovedRegistrationOperationsAndFacadeInfrastructureOptInAtM3bR1() throws Exception {
    var root = Path.of("src/main/java");
    var hits =
        Files.walk(root)
            .filter(p -> p.toString().endsWith(".java"))
            .filter(p -> read(p).contains("ApimIncidentOwnership.TERMINAL_CALLER"))
            .map(p -> root.relativize(p).toString().replace('\\', '/'))
            .sorted()
            .toList();
    assertThat(hits)
        .containsExactlyElementsOf(
            List.of(
                    "com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/execution/ApimExchangeOutcomeLogger.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java")
                .stream()
                .sorted()
                .toList());
    String reg =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java"));
    assertThat(reg).contains("TERMINAL_CALLER").doesNotContain("@LogExecution");
    String lookup =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java"));
    assertThat(lookup).contains("TERMINAL_CALLER", "@LogExecution");
    String forgotPassword =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/"
                    + "ApimForgotPasswordUsernameLookupAdapter.java"));
    assertThat(forgotPassword).doesNotContain("TERMINAL_CALLER");
  }

  @Test
  void onlyRegistrationStartSetsTheLookupInvocationIntent() throws Exception {
    String start =
        read(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/idempotency/RegistrationStartProtectedOperation.java"));
    String completion =
        read(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/idempotency/RegistrationCompleteProtectedOperation.java"));
    String otp =
        read(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/idempotency/RegistrationOtpVerifyProtectedOperation.java"));

    assertThat(start).contains("RegistrationLookupInvocationIntent.START");
    assertThat(completion).doesNotContain("RegistrationLookupInvocationIntent");
    assertThat(otp).doesNotContain("RegistrationLookupInvocationIntent");
  }

  private static String read(Path p) {
    try {
      return Files.readString(p);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
