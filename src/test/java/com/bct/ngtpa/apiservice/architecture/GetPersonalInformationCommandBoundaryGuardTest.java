package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GetPersonalInformationCommandBoundaryGuardTest {
  private static final Path COMMAND =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetPersonalInformationCommand.java");
  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetPersonalInformationService.java");

  @Test
  void commandCarriesBusinessInputAndTrustedContextButNotAccountRef() throws Exception {
    String source = Files.readString(COMMAND);
    assertFalse(containsIgnoringWhitespace(source, "accountRef"));
    assertTrue(containsIgnoringWhitespace(source, "String language"));
    assertTrue(containsIgnoringWhitespace(source, "String accountEnv"));
    assertTrue(containsIgnoringWhitespace(source, "String policyNo"));
    assertTrue(containsIgnoringWhitespace(source, "String certNo"));
    assertTrue(containsIgnoringWhitespace(source, "String actorUserId"));
  }

  @Test
  void controllerCopiesTrustedContextIntoGetCommand() throws Exception {
    String source = Files.readString(CONTROLLER);
    assertFalse(containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(accountRef"));
    assertTrue(containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(language,"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.account().policyNo()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.account().certNo()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserId()"));
  }

  @Test
  void serviceUsesExplicitCommandInsteadOfAmbientResolver() throws Exception {
    String source = Files.readString(SERVICE);
    assertFalse(containsIgnoringWhitespace(source, "command.accountRef()"));
    assertFalse(containsIgnoringWhitespace(source, "PortalAccessContextPort"));
    assertFalse(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertFalse(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
    assertTrue(containsIgnoringWhitespace(source, "command.accountEnv()"));
    assertTrue(containsIgnoringWhitespace(source, "command.actorUserId()"));
  }
}
