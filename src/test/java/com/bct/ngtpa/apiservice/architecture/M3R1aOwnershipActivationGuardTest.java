package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class M3R1aOwnershipActivationGuardTest {
  @Test
  void onlyApprovedOperationsAndFacadeInfrastructureOptInAtCurrentCheckpoints() throws Exception {
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
                    // CIM adopts TERMINAL_CALLER by Nick's approval, 2026-11-10.
                    "com/bct/ngtpa/apiservice/adapter/out/apim/investmentchoice/ApimInvestmentChoiceCancelAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/investmentchoice/ApimInvestmentChoiceDetailsAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/investmentchoice/ApimInvestmentChoiceInstructionAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordUsernameLookupAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/details/ApimForgotPasswordDetailsLookupAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationCompletionAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java",
                    "com/bct/ngtpa/apiservice/adapter/out/apim/vestingscale/ApimVestingScaleAdapter.java")
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
    assertThat(lookup)
        .contains(
            "ApimIncidentOwnership.TERMINAL_CALLER",
            "@LogExecution",
            "RegistrationLookupInvocationIntent.START",
            "RegistrationLookupInvocationIntent.CONTINUATION",
            "ApimIncidentOwnership.APIM_OBSERVER");
    String vestingScale =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/vestingscale/"
                    + "ApimVestingScaleAdapter.java"));
    assertThat(vestingScale)
        .contains("ApimIncidentOwnership.TERMINAL_CALLER", "FailureEventPort", "publishFailure");
    String forgotPassword =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/"
                    + "ApimForgotPasswordUsernameLookupAdapter.java"));
    assertThat(forgotPassword).contains("ApimIncidentOwnership.TERMINAL_CALLER");
    String forgotPasswordDetails =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/details/"
                    + "ApimForgotPasswordDetailsLookupAdapter.java"));
    assertThat(forgotPasswordDetails).contains("ApimIncidentOwnership.TERMINAL_CALLER");
    String forgotPasswordCompletion =
        read(
            root.resolve(
                "com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/"
                    + "ApimForgotPasswordCompletionAdapter.java"));
    assertThat(forgotPasswordCompletion).contains("ApimIncidentOwnership.TERMINAL_CALLER");
  }

  @Test
  void registrationStartCompletionAndOtpUseTheirApprovedLookupIntents() throws Exception {
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
    assertThat(start)
        .doesNotContain(
            "RegistrationLookupInvocationIntent.CONTINUATION",
            "RegistrationLookupInvocationIntent.DEFAULT");
    assertThat(completion)
        .contains("RegistrationLookupInvocationIntent.CONTINUATION")
        .doesNotContain(
            "RegistrationLookupInvocationIntent.START",
            "RegistrationLookupInvocationIntent.DEFAULT");
    assertThat(otp)
        .contains("RegistrationLookupInvocationIntent.DEFAULT")
        .doesNotContain(
            "RegistrationLookupInvocationIntent.START",
            "RegistrationLookupInvocationIntent.CONTINUATION");
  }

  private static String read(Path p) {
    try {
      return Files.readString(p);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
