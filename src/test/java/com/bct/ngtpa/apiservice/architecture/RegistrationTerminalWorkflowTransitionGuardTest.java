package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationTerminalWorkflowTransitionGuardTest {
  @Test
  void startCompletesWithoutOtpWhenNoUnregisteredTrustRemains() throws Exception {
    String source = read("application/usecase/auth/registration/StartRegistrationService.java");
    assertThat(source)
        .contains("Boolean.FALSE.equals(account.registered())")
        .contains("if (unregisteredAccounts.isEmpty())")
        .contains("completeAlreadyRegisteredStart")
        .contains("completeRevisioned")
        .contains("StartRegistrationResultStatus.REGISTRATION_COMPLETED")
        .contains("WorkflowNextStep.RETURN_TO_LOGIN");
    String terminalMethod =
        source.substring(
            source.indexOf("private Mono<StartRegistrationResult> completeAlreadyRegisteredStart"),
            source.indexOf("private RegistrationRequestHash requestHash"));
    assertThat(terminalMethod)
        .doesNotContain("createOtp(")
        .doesNotContain("deliverRegistrationOtp(");
  }

  @Test
  void startTerminalMapperReturnsCompletionMessageAndReturnToLogin() throws Exception {
    String source = read("adapter/in/web/auth/registration/mapper/RegistrationStartWebMapper.java");
    assertThat(source)
        .contains("StartRegistrationResultStatus.REGISTRATION_COMPLETED")
        .contains("WorkflowNextStep.RETURN_TO_LOGIN")
        .contains("ApiMessage.info(RegistrationMessageKey.COMPLETION.value(), null, TARGET_PAGE)");
  }

  @Test
  void completionUsesDistinctNavigationForRemainingAndFinalTrusts() throws Exception {
    String source =
        read("application/usecase/auth/registration/complete/CompleteRegistrationService.java");
    assertThat(source)
        .contains("CompleteRegistrationResultStatus.REGISTERED_WITH_REMAINING_TRUSTS")
        .contains("WorkflowNextStep.SELECT_TRUST")
        .contains("AuthFlowStatus.TRUST_SELECTION_READY")
        .contains("CompleteRegistrationResultStatus.REGISTERED")
        .contains("WorkflowNextStep.RETURN_TO_LOGIN")
        .contains("AuthFlowStatus.COMPLETED");
  }

  private String read(String relativePath) throws Exception {
    return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice", relativePath));
  }
}
