package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompletionExecutorFailFirstTest {
  private static String read(String relative) throws Exception {
    return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/", relative));
  }

  private static String compact(String source) {
    return source.replaceAll("\\s+", "");
  }

  @Test
  void focusedCompletionCommandCarriesExplicitProtectedRoutingMetadata() throws Exception {
    String source =
        compact(
            read(
                "application/usecase/auth/registration/complete/RegistrationCompletionCommand.java"));
    assertThat(source)
        .contains("ActorUserRoleactorUserRole")
        .contains("Objects.requireNonNull(actorUserRole")
        .contains("StringaccountEnv")
        .contains("accountEnv=required(accountEnv");
  }

  @Test
  void serviceIntroducesApprovedPreAuthenticationRoleFromProtectedCompletionBoundary()
      throws Exception {
    String source =
        compact(
            read(
                "application/usecase/auth/registration/complete/CompleteRegistrationService.java"));
    assertThat(source)
        .contains("SelectedTrusttrust=uc.selectedTrust()")
        .contains("trust.accountEnv()")
        .contains("trust.trustCode()")
        .contains("ActorUserRole.ROLE_USER_MBR")
        .contains("newRegistrationCompletionCommand(");
  }

  @Test
  void adapterUsesTypedAccountEnvExchangeWithNonRetryableMutation() throws Exception {
    String source =
        compact(read("adapter/out/apim/registration/ApimRegistrationCompletionAdapter.java"));
    assertThat(source)
        .contains("ApimExchangeExecutor")
        .contains("ApimExchangeRequest")
        .contains("postEnvelope(")
        .contains("ApimDatabaseScope.ACCOUNT_ENV")
        .contains(
            "newApimCredentialResolutionContext(command.actorUserRole(),command.accountEnv())")
        .contains("ApimRetryEligibility.NON_RETRYABLE")
        .contains("TrpRegMemberApimRequest.class")
        .contains("TrpRegMemberApimDataItem.class")
        .doesNotContain("ActorUserRole.ROLE_USER_MBR")
        .doesNotContain("ApimRetryEligibility.IDEMPOTENT_MUTATION")
        .doesNotContain("ApimWebClientFacade")
        .doesNotContain("ApimCertificateService")
        .doesNotContain("ApimPayloadCryptoService")
        .doesNotContain("ApimProperties")
        .doesNotContain("resolveProfile(")
        .doesNotContain("getEncryption()")
        .doesNotContain("encryptRequest(")
        .doesNotContain("decryptResponseEnvelope(");
  }

  @Test
  void downstreamRequestKeepsExistingContractAndDoesNotSerializeRoutingRoleOrConfirmation()
      throws Exception {
    String source = compact(read("adapter/out/apim/registration/dto/TrpRegMemberApimRequest.java"));
    assertThat(source)
        .contains("@JsonProperty(\"env\")Stringenvironment")
        .contains("@JsonProperty(\"password\")Stringpassword")
        .doesNotContain("actorUserRole")
        .doesNotContain("confirmPassword")
        .doesNotContain("idempotencyKey")
        .doesNotContain("correlationId");
  }

  @Test
  void operationSpecificResultMapperRemainsOutsideExecutor() throws Exception {
    String adapter =
        compact(read("adapter/out/apim/registration/ApimRegistrationCompletionAdapter.java"));
    String mapper =
        compact(read("adapter/out/apim/registration/mapper/TrpRegMemberApimResultMapper.java"));
    assertThat(adapter).contains("TrpRegMemberApimResultMapper");
    assertThat(mapper)
        .contains("RegistrationCompletionResult.rejected(")
        .contains("RegistrationCompletionResult.registered(")
        .contains("TRPRegMemberresponseisinvalid");
  }
}
