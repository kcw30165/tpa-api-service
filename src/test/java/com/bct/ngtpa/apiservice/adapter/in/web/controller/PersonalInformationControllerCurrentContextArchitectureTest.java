package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationControllerCurrentContextArchitectureTest {

  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java");

  @Test
  void controllerUsesCurrentCurrentPortalAccessContextResolverInsteadOfPortalAccessContextPort()
      throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(
        containsIgnoringWhitespace(source, "PortalAccessContextPort"),
        "PersonalInformationController must not directly resolve account context from PortalAccessContextPort");
    assertTrue(
        containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"),
        "PersonalInformationController should depend on the current request context resolver");
    assertTrue(
        containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"),
        "PersonalInformationController should read the already-populated per-request PortalAccessContext");
  }

  @Test
  void controllerGetsAccountRefAndDisplayDimensionsFromCurrentPortalAccessContext()
      throws Exception {
    String source = Files.readString(SOURCE);

    assertFalse(
        containsIgnoringWhitespace(source, "resolveRequiredAccountRef(ContextView"),
        "PersonalInformationController should not manually resolve Account-Ref from RequestHeaderContext");
    assertTrue(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(language,"),
        "Get command should carry trusted authenticated retrieval inputs");
    assertFalse(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(accountRef"),
        "Get command must not carry Account-Ref as frontend routing authority");
    assertTrue(
        containsIgnoringWhitespace(source, "portalAccessContext.account().accountEnv()"),
        "Get command accountEnv should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "portalAccessContext.account().policyNo()"),
        "Get command policyNo should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "portalAccessContext.account().certNo()"),
        "Get command certNo should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserId()"),
        "Get command actorUserId should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserRole()"),
        "Get command actorUserRole should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "accountEnv(portalAccessContext)"),
        "Mapper accountEnv should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "trustCode(portalAccessContext)"),
        "Mapper trustCode should come from current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "schemeType(portalAccessContext)"),
        "Mapper schemeType should come from current PortalAccessContext");
  }
}
