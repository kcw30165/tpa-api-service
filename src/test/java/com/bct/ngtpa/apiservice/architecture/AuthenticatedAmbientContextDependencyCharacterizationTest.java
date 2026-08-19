package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthenticatedAmbientContextDependencyCharacterizationTest {
  private static final List<String> APPLICATION_SERVICES =
      List.of(
          "GetNotificationsService.java", "UpdateNotificationsReadStatusService.java",
          "GetContributionSummaryService.java", "ExportContributionSummaryService.java",
          "GetPersonalInformationService.java", "UpdatePersonalInformationService.java");

  @Test
  void sixKnownApplicationServicesNoLongerResolveAmbientPortalContext() throws Exception {
    Path usecases = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase");
    for (String file : APPLICATION_SERVICES) {
      assertThat(Files.readString(usecases.resolve(file)))
          .as(file)
          .doesNotContain(
              "CurrentPortalAccessContextResolver",
              "currentPortalAccessContextResolver.current()",
              "PortalAccessContext",
              "Mono.deferContextual",
              "reactor.util.context");
    }
  }

  @Test
  void usernameRecoveryAdapterRetainsRequestIdReactorContextOnly() throws Exception {
    Path adapter =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/usernamerecovery/TrpSearchMemberAdapter.java");
    assertThat(Files.readString(adapter))
        .contains("Mono.deferContextual", "RequestCorrelation.REQUEST_ID_CONTEXT_KEY")
        .doesNotContain(
            "PortalAccessContext", "PortalAccessContextKeys", "ACTOR_CONTEXT", "ACCOUNT_CONTEXT");
  }

  @Test
  void inboundFilterOwnsSessionScopedResolutionAndContextPublication() throws Exception {
    Path filter =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java");
    String source = Files.readString(filter).replaceAll("\\s+", " ");
    assertThat(source)
        .contains(
            "resolvePortalAccessContext(accountRef, sessionId)",
            "PortalAccessContextKeys.ATTRIBUTE_KEY",
            "PortalAccessContextKeys.CONTEXT_KEY");
  }
}
