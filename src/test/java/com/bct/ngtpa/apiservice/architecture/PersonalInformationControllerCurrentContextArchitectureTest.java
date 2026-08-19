package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationControllerCurrentContextArchitectureTest {

  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");

  @Test
  void controllerUsesCurrentCurrentPortalAccessContextResolverOnly() throws Exception {
    String source = Files.readString(CONTROLLER);

    assertTrue(
        containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"),
        "PersonalInformationController should depend on current CurrentPortalAccessContextResolver");
    assertTrue(
        containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"),
        "PersonalInformationController should read current PortalAccessContext from Reactor context");
    assertFalse(
        containsIgnoringWhitespace(source, "PortalAccessContextPort"),
        "PersonalInformationController must not resolve PortalAccessContext by Account-Ref port");
    assertFalse(
        containsIgnoringWhitespace(source, "resolvePortalAccessContext"),
        "PersonalInformationController must not manually resolve context by accountRef");
  }

  @Test
  void controllerValidatesSelectedAccountFromCurrentContextButKeepsCommandLanguageOnly()
      throws Exception {
    String source = Files.readString(CONTROLLER);

    assertTrue(
        containsIgnoringWhitespace(source, "requireAccountRef(portalAccessContext)"),
        "Controller should validate selected accountRef from PortalAccessContext.account()");
    assertTrue(
        containsIgnoringWhitespace(source, "context.account().accountRef()"),
        "Controller should source selected accountRef from PortalAccessContext.account()");
    assertTrue(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(language,"),
        "GET personal-information command should carry business input only");
    assertFalse(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(accountRef"),
        "GET personal-information command must not carry selected-account context");
  }

  @Test
  void controllerPassesDisplayDimensionsFromCurrentPortalAccessContextToMapper() throws Exception {
    String source = Files.readString(CONTROLLER);

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
