package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionDomainArchitectureTest {
  private static final Path DOMAIN =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authenticated");

  @Test
  void modelIsFrameworkAndInfrastructureNeutral() throws Exception {
    String source;
    try (var paths = Files.walk(DOMAIN)) {
      source =
          paths
              .filter(path -> path.toString().endsWith(".java"))
              // The hybrid protected envelope is a persistence boundary DTO,
              // not the authenticated-session domain model guarded here.
              .filter(
                  path ->
                      !path.getFileName().toString().equals("ProtectedAuthenticatedSession.java"))
              .map(
                  path -> {
                    try {
                      return Files.readString(path);
                    } catch (Exception exception) {
                      throw new IllegalStateException(exception);
                    }
                  })
              .collect(Collectors.joining("\n"));
    }

    assertThat(source)
        .doesNotContain(
            "org.springframework",
            "io.lettuce",
            "WebClient",
            "ObjectMapper",
            "javax.crypto",
            "ServerHttp",
            "ResponseCookie");
  }

  @Test
  void domainRetainsRequiredLifecycleAndAccountSemantics() throws Exception {
    String session = Files.readString(DOMAIN.resolve("AuthenticatedSession.java"));
    String status = Files.readString(DOMAIN.resolve("AuthenticatedSessionStatus.java"));
    String account = Files.readString(DOMAIN.resolve("AuthenticatedSessionAccount.java"));

    assertThat(session)
        .contains(
            "ActorContext actorContext",
            "List<AuthenticatedSessionAccount> accounts",
            "String csrfTokenHash",
            "Instant lastAccessedAt",
            "Instant idleExpiresAt",
            "Instant absoluteExpiresAt");
    String compactSession = session.replaceAll("\\s+", "");
    assertThat(compactSession)
        .contains(
            "status==AuthenticatedSessionStatus.ACTIVE",
            "now.isBefore(idleExpiresAt)",
            "now.isBefore(absoluteExpiresAt)");
    assertThat(status).contains("ACTIVE", "LOGGED_OUT", "REVOKED", "EXPIRED", "terminal()");
    assertThat(account)
        .contains(
            "AuthenticatedAccountRef accountRef",
            "String accountEnv",
            "String policyNo",
            "String certNo",
            "String trustCode");
    assertThat(sourceOf(session, account)).doesNotContain("actorType");
  }

  private static String sourceOf(String... values) {
    return String.join("\n", values);
  }
}
