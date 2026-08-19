package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationCurrentContextBoundaryGuardTest {
  @Test
  void updateCommandCarriesTrustedContextButNotAccountRef() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdatePersonalInformationCommand.java"));
    assertTrue(containsIgnoringWhitespace(source, "record UpdatePersonalInformationCommand("));
    assertFalse(containsIgnoringWhitespace(source, "String accountRef,"));
    assertFalse(containsIgnoringWhitespace(source, "accountRef()"));
    assertTrue(containsIgnoringWhitespace(source, "String accountEnv"));
    assertTrue(containsIgnoringWhitespace(source, "String actorUserId"));
    assertTrue(containsIgnoringWhitespace(source, "ActorUserRole actorUserRole"));
  }

  @Test
  void updateControllerCopiesCurrentContextAndDoesNotReadAccountRefFromHeaderContext()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java"));
    assertTrue(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertTrue(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
    assertFalse(containsIgnoringWhitespace(source, "context.accountRef()"));
    assertTrue(
        containsIgnoringWhitespace(
            source, "requestMapper.toCommand(body.applyToAllAccounts(), body)"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.account().policyNo()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserRole()"));
  }

  @Test
  void updateServiceUsesExplicitCommandNotAmbientProvider() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdatePersonalInformationService.java"));
    assertFalse(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertFalse(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
    assertFalse(containsIgnoringWhitespace(source, "resolvePortalAccessContext"));
    assertFalse(containsIgnoringWhitespace(source, "command.accountRef()"));
    assertTrue(containsIgnoringWhitespace(source, "command.accountEnv()"));
    assertTrue(containsIgnoringWhitespace(source, "command.actorUserRole()"));
  }
}
