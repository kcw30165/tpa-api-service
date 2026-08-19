package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GetReferenceDataCountriesCommandBoundaryGuardTest {

  private static final Path COMMAND =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetReferenceDataCountriesCommand.java");
  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataController.java");
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetReferenceDataCountriesService.java");

  @Test
  void commandContainsExplicitAuthenticatedRouting() throws Exception {
    String source = Files.readString(COMMAND);
    assertTrue(containsIgnoringWhitespace(source, "String language"));
    assertTrue(containsIgnoringWhitespace(source, "String accountEnv"));
    assertTrue(containsIgnoringWhitespace(source, "ActorUserRole actorUserRole"));
    assertFalse(containsIgnoringWhitespace(source, "accountRef"));
  }

  @Test
  void controllerUsesCurrentTrustedContextWithoutPassingAccountRef() throws Exception {
    String source = Files.readString(CONTROLLER);
    assertTrue(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertTrue(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.account().accountEnv()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserRole()"));
    assertFalse(
        containsIgnoringWhitespace(source, "new GetReferenceDataCountriesCommand(accountRef"));
    assertFalse(containsIgnoringWhitespace(source, "resolveRequiredAccountRef"));
    assertFalse(containsIgnoringWhitespace(source, "PortalAccessContextPort"));
    assertTrue(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
  }

  @Test
  void serviceRemainsAmbientContextFreeAndPassesExplicitCommand() throws Exception {
    String source = Files.readString(SERVICE);
    assertTrue(containsIgnoringWhitespace(source, "fetchCountryList(command)"));
    assertFalse(containsIgnoringWhitespace(source, "PortalAccessContextPort"));
    assertFalse(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertFalse(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver"));
    assertFalse(containsIgnoringWhitespace(source, "PortalAccessContext"));
    assertFalse(containsIgnoringWhitespace(source, "deferContextual"));
    assertFalse(containsIgnoringWhitespace(source, "reactor.util.context"));
    assertFalse(containsIgnoringWhitespace(source, "requireAccountRef"));
  }
}
