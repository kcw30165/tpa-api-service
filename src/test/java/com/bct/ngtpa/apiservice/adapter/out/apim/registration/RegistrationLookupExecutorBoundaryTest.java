package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationLookupExecutorBoundaryTest {
  private String source(String path) throws Exception {
    return Files.readString(Path.of(path));
  }

  @Test
  void focusedCommandCarriesExplicitRegistrationRoutingRole() throws Exception {
    String command =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/registration/RegistrationLookupCommand.java");
    assertThat(command).contains("ActorUserRole actorUserRole");
    assertThat(command).doesNotContain("accountEnv", "PortalAccessContext", "accountRef");
  }

  @Test
  void adapterUsesTypedNonAccountExecutorWithoutLocalRoleSubstitution() throws Exception {
    String adapter =
        source(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationLookupAdapter.java");
    assertThat(adapter)
        .contains(
            "ApimExchangeExecutor",
            "ApimExchangeRequest",
            "postEnvelope(",
            "ApimDatabaseScope.NON_ACCOUNT",
            "command.actorUserRole()",
            "new ApimCredentialResolutionContext(command.actorUserRole(), null)",
            "ApimRetryEligibility.SAFE_BUSINESS_CALL",
            "TrpSearchUnregMemberApimRequest.class",
            "TrpSearchUnregMemberApimDataItem.class");
    assertThat(adapter)
        .doesNotContain(
            "ROLE_USER_MBR",
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "resolveProfile(",
            "getPublicKey(",
            "encryptRequest(",
            "decryptResponseEnvelope(",
            "PortalAccessContext",
            "accountRef");
  }

  @Test
  void allThreeRegistrationCallersIntroduceTheEstablishedCompatibilityRole() throws Exception {
    for (String file :
        new String[] {
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationService.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/otp/VerifyRegistrationOtpService.java",
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/CompleteRegistrationService.java"
        }) {
      assertThat(source(file)).as(file).contains("ActorUserRole.ROLE_USER_MBR");
    }
  }
}
