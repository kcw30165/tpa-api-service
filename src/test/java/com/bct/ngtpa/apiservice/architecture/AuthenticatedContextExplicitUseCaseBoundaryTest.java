package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AuthenticatedContextExplicitUseCaseBoundaryTest {
  private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");
  private static final List<String> SERVICES =
      List.of(
          "GetNotificationsService.java",
          "UpdateNotificationsReadStatusService.java",
          "GetContributionSummaryService.java",
          "ExportContributionSummaryService.java",
          "GetPersonalInformationService.java",
          "UpdatePersonalInformationService.java");

  @Test
  void applicationServiceImplementationsRejectAmbientResolver() throws Exception {
    for (String file : SERVICES) {
      String s = Files.readString(find(file));
      assertThat(s)
          .as(file)
          .doesNotContain(
              "CurrentPortalAccessContextResolver",
              "PortalAccessContext",
              "deferContextual",
              "reactor.util.context");
    }
  }

  @Test
  void outboundAdaptersRejectPortalIdentityContextButAllowRequestIdAndLoggingContext()
      throws Exception {
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> ps = Files.walk(MAIN.resolve("adapter/out"))) {
      for (Path p : ps.filter(x -> x.toString().endsWith(".java")).toList()) {
        String s = Files.readString(p);
        if (containsIgnoringWhitespace(s, "CurrentPortalAccessContextResolver")
            || containsIgnoringWhitespace(s, "PortalAccessContextKeys")
            || containsIgnoringWhitespace(s, "PortalAccessContext")
            || containsIgnoringWhitespace(s, "ACTOR_CONTEXT")
            || containsIgnoringWhitespace(s, "ACCOUNT_CONTEXT")) offenders.add(p.toString());
      }
    }
    assertThat(offenders).isEmpty();
    String search = Files.readString(find("TrpSearchMemberAdapter.java"));
    assertThat(search)
        .containsIgnoringWhitespaces("RequestCorrelation.REQUEST_ID_CONTEXT_KEY")
        .doesNotContain("PortalAccessContext");
  }

  @Test
  void definitionsAndInboundResolutionContractsRemainAllowed() throws Exception {
    assertThat(Files.readString(find("PortalAccessContext.java")))
        .containsIgnoringWhitespaces("record PortalAccessContext");
    assertThat(Files.readString(find("CurrentPortalAccessContextResolver.java")))
        .containsIgnoringWhitespaces("interface CurrentPortalAccessContextResolver");
    assertThat(Files.readString(find("ReactorCurrentPortalAccessContextResolver.java")))
        .containsIgnoringWhitespaces("PortalAccessContextKeys.CONTEXT_KEY");
    assertThat(Files.readString(find("PortalAccessContextResolutionException.java")))
        .containsIgnoringWhitespaces("class PortalAccessContextResolutionException");
  }

  @Test
  void controllersCopyTrustedContextIntoExplicitCommands() throws Exception {
    for (String file :
        List.of(
            "NotificationController.java",
            "ContributionController.java",
            "PersonalInformationController.java")) {
      String s = Files.readString(find(file));
      assertThat(s)
          .as(file)
          .containsIgnoringWhitespaces(
              "CurrentPortalAccessContextResolver", "currentPortalAccessContextResolver.current()")
          .containsIgnoringWhitespaces(".account()", ".actor()")
          .doesNotContain("PortalAccessContextPort", "resolvePortalAccessContext(");
    }
  }

  @Test
  void apimExecutionProductionRemainsPortalContextFree() throws Exception {
    for (String f :
        List.of(
            "ApimExchangeRequest.java",
            "ApimExchangeExecutor.java",
            "DefaultApimExchangeExecutor.java"))
      assertThat(Files.readString(find(f)))
          .doesNotContain("PortalAccessContext", "CurrentPortalAccessContextResolver");
  }

  private static Path find(String name) throws Exception {
    try (Stream<Path> ps = Files.walk(MAIN)) {
      return ps.filter(p -> p.getFileName().toString().equals(name)).findFirst().orElseThrow();
    }
  }
}
