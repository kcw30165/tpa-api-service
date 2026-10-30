package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimExecutionContractArchitectureTest {
  private static final Path ROOT =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/execution");
  private static final List<String> FILES =
      List.of(
          "ApimExchangeRequest.java",
          "ApimResubmissionPolicy.java",
          "ApimExchangeExecutor.java",
          "ApimSuccessfulExchange.java",
          "DefaultApimExchangeExecutor.java",
          "ApimEnvelopeSchemaClassification.java",
          "ApimEnvelopeSchemaClassifier.java",
          "ApimExchangeOutcomeLogger.java");

  @Test
  void contractsHaveNoInboundSessionReactorApplicationOrFeatureDependencies() throws Exception {
    String source = all();
    assertThat(source)
        .doesNotContain(
            "adapter.in.web",
            "session",
            "PortalAccessContext",
            "Reactor Context",
            "reactor.util.context",
            "application.usecase",
            "controller",
            "forgotpassword",
            "notification",
            "registration",
            "personalinformation");
  }

  @Test
  void requestMakesScopeContextAndRetryExplicitWithoutDefaultsOrInference() throws Exception {
    String source = Files.readString(ROOT.resolve("ApimExchangeRequest.java"));
    assertThat(source)
        .contains(
            "ApimDatabaseScope databaseScope",
            "ApimCredentialResolutionContext credentialContext",
            "ApimRetryEligibility retryEligibility",
            "databaseScope == ApimDatabaseScope.ACCOUNT_ENV");
    assertThat(source)
        .doesNotContain(
            "= ApimDatabaseScope.NON_ACCOUNT",
            "ActorUserRole.ROLE_USER_MBR",
            "retryEligibility == null ?",
            "hasText(accountEnv)");
  }

  @Test
  void successfulMetadataDoesNotExposeSecretsOrApplicationResults() throws Exception {
    String source = Files.readString(ROOT.resolve("ApimSuccessfulExchange.java"));
    assertThat(source)
        .contains(
            "String responseBody",
            "ApimCredentialProfile profile",
            "PublicKey responseVerificationKey");
    assertThat(source)
        .doesNotContain(
            "token", "apiKey", "clientSecret", "certificateText", "session", "businessResult");
  }

  @Test
  void stagePreservesThreeApprovedContractsAndAddsOnlyDefaultExecutor() throws Exception {
    try (var stream = Files.list(ROOT)) {
      assertThat(
              stream
                  .filter(p -> p.toString().endsWith(".java"))
                  .map(p -> p.getFileName().toString())
                  .toList())
          .containsExactlyInAnyOrderElementsOf(FILES);
    }
  }

  private String all() throws Exception {
    StringBuilder out = new StringBuilder();
    for (String file : FILES) out.append(Files.readString(ROOT.resolve(file))).append('\n');
    return out.toString();
  }
}
