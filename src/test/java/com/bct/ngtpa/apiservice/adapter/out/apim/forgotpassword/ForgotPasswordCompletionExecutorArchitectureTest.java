package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordCompletionExecutorArchitectureTest {
  @Test
  void completionUsesExecutorAndExplicitMutationRouting() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordCompletionAdapter.java"));
    String compact = source.replaceAll("\\s+", "");
    assertThat(source)
        .contains(
            "ApimExchangeExecutor",
            "ApimDatabaseScope.NON_ACCOUNT",
            "ApimRetryEligibility.IDEMPOTENT_MUTATION",
            "postEnvelope");
    assertThat(compact)
        .contains("newApimCredentialResolutionContext(command.actorUserRole(),null)");
    assertThat(source)
        .doesNotContain(
            "ApimWebClientFacade",
            "ApimCertificateService",
            "ApimPayloadCryptoService",
            "ApimProperties",
            "getEncryption()",
            "ROLE_USER_MBR");
  }

  @Test
  void feCannotControlRoleOrRouting() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/dto/ForgotPasswordCompleteRequest.java"));
    assertThat(s)
        .doesNotContain("ActorUserRole", "actorRole", "scope", "accountEnv", "retryEligibility");
  }

  @Test
  void usernameLookupProductionRemainsUntouched() throws Exception {
    assertThat(
            Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordUsernameLookupAdapter.java")))
        .contains("ApimExchangeExecutor");
  }
}
