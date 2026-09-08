package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordDetailsExecutorFailFirstTest {
  private static String source(String relative) throws Exception {
    return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/", relative));
  }

  private static String compact(String value) {
    return value.replaceAll("\\s+", "");
  }

  @Test
  void focusedLookupCommandCarriesExplicitPreAuthenticationRole() throws Exception {
    String command =
        compact(
            source(
                "application/usecase/auth/forgotpassword/details/ForgotPasswordDetailsLookupCommand.java"));
    assertThat(command)
        .contains("ActorUserRoleactorUserRole")
        .contains("Objects.requireNonNull(actorUserRole")
        .doesNotContain("PortalAccessContext")
        .doesNotContain("Account-Ref");
  }

  @Test
  void everyApplicationCallerSuppliesCompatibilityRole() throws Exception {
    String direct =
        compact(
            source(
                "application/usecase/auth/forgotpassword/details/VerifyForgotPasswordDetailsService.java"));
    String locked =
        compact(
            source(
                "application/usecase/auth/forgotpassword/detailsotp/ForgotPasswordUnderLockDetailsVerifier.java"));
    assertThat(direct)
        .contains("ActorUserRole.ROLE_USER_MBR")
        .contains("newForgotPasswordDetailsLookupCommand(");
    assertThat(locked)
        .contains("ActorUserRole.ROLE_USER_MBR")
        .contains("newForgotPasswordDetailsLookupCommand(");
  }

  @Test
  void adapterUsesTypedNonAccountSafeBusinessExchange() throws Exception {
    String adapter =
        compact(
            source(
                "adapter/out/apim/forgotpassword/details/ApimForgotPasswordDetailsLookupAdapter.java"));
    assertThat(adapter)
        .contains("ApimExchangeExecutor")
        .contains("ApimExchangeRequest")
        .contains("postEnvelope(")
        .contains("/ws/NGTPA/v1/TRPSearchMember")
        .contains("ForgotPasswordTrpSearchMemberRequest.class")
        .contains("ForgotPasswordTrpSearchMemberDataItem.class")
        .contains("ApimDatabaseScope.NON_ACCOUNT")
        .contains("newApimCredentialResolutionContext(command.actorUserRole(),null)")
        .contains("ApimRetryEligibility.SAFE_BUSINESS_CALL")
        .doesNotContain("ActorUserRole.ROLE_USER_MBR")
        .doesNotContain("LegacyForgotPasswordTransport");
  }

  @Test
  void migratedPathOwnsNoManualCryptoProfileOrEncryptionBranch() throws Exception {
    String adapter =
        source(
            "adapter/out/apim/forgotpassword/details/ApimForgotPasswordDetailsLookupAdapter.java");
    Path legacyTransport =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/details/LegacyForgotPasswordTransport.java");
    assertThat(Files.exists(legacyTransport)).isFalse();
    assertThat(adapter)
        .doesNotContain("ApimWebClientFacade")
        .doesNotContain("ApimCertificateService")
        .doesNotContain("ApimPayloadCryptoService")
        .doesNotContain("ApimProperties")
        .doesNotContain("resolveProfile(")
        .doesNotContain("encryptRequest(")
        .doesNotContain("decryptResponseEnvelope(");
  }

  @Test
  void routingMetadataIsNotSerializedAndResponseEnvironmentCannotRouteRequest() throws Exception {
    String request =
        source(
            "adapter/out/apim/forgotpassword/details/dto/ForgotPasswordTrpSearchMemberRequest.java");
    String adapter =
        source(
            "adapter/out/apim/forgotpassword/details/ApimForgotPasswordDetailsLookupAdapter.java");
    assertThat(request)
        .doesNotContain("actorUserRole")
        .doesNotContain("accountEnv")
        .doesNotContain("csrfToken")
        .doesNotContain("newPassword");
    assertThat(adapter).doesNotContain("result.accountEnv()").doesNotContain("data.accountEnv()");
  }
}
