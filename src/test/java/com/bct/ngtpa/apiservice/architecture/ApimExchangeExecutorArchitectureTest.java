package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApimExchangeExecutorArchitectureTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/execution/DefaultApimExchangeExecutor.java");

  @Test
  void implementationUsesApprovedBoundaryAndSuccessfulExchangeFacade() throws Exception {
    String s = Files.readString(SOURCE);
    assertThat(s)
        .contains(
            "implements ApimExchangeExecutor",
            "postSuccessfulExchange(",
            "postEncryptedSuccessfulExchange(",
            "request.databaseScope()",
            "request.credentialContext()",
            "request.retryEligibility()",
            "request.requestType()",
            "request.responseDataType()",
            "exchange.responseVerificationKey()");
  }

  @Test
  void implementationHasNoDirectRoutingCertificateWebSessionOrFeatureDependencies()
      throws Exception {
    String s = Files.readString(SOURCE);
    assertThat(s.replace("ObjectMapper", "JacksonCodec"))
        .doesNotContain(
            "ApimCertificateService",
            "ApimCredentialProfileResolver",
            "PortalAccessContext",
            "adapter.in.web",
            "application.usecase",
            "reactor.util.context",
            "ROLE_USER_MBR",
            "ApimDatabaseScope.NON_ACCOUNT",
            "ApimDatabaseScope.ACCOUNT_ENV",
            "forgotpassword",
            "notification",
            "registration",
            "ResponseValidator",
            "Mapper",
            "unknownOutcome",
            "FailureEvent");
  }

  @Test
  void implementationOwnsCryptoButNoBusinessInterpretationOrSecrets() throws Exception {
    String s = Files.readString(SOURCE);
    assertThat(s).contains("encryptRequest(", "decryptResponseEnvelope(", "Mono.defer");
    assertThat(s)
        .doesNotContain(
            "clientSecret", "apiKey", "accessToken", "certificateText", "requireSuccessData");
  }
}
