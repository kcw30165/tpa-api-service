package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationControllerCurrentContextArchitectureTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");

    @Test
    void controllerUsesCurrentCurrentPortalAccessContextProviderOnly() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("CurrentPortalAccessContextProvider"),
                "PersonalInformationController should depend on current CurrentPortalAccessContextProvider");
        assertTrue(source.contains("currentPortalAccessContextProvider.current()"),
                "PersonalInformationController should read current PortalAccessContext from Reactor context");
        assertFalse(source.contains("PortalAccessContextPort"),
                "PersonalInformationController must not resolve PortalAccessContext by Account-Ref port");
        assertFalse(source.contains("resolvePortalAccessContext"),
                "PersonalInformationController must not manually resolve context by accountRef");
    }

    @Test
    void controllerValidatesSelectedAccountFromCurrentContextButKeepsCommandLanguageOnly() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("requireAccountRef(portalAccessContext)"),
                "Controller should validate selected accountRef from PortalAccessContext.account()");
        assertTrue(source.contains("context.account().accountRef()"),
                "Controller should source selected accountRef from PortalAccessContext.account()");
        assertTrue(source.contains("new GetPersonalInformationCommand(language)"),
                "GET personal-information command should carry business input only");
        assertFalse(source.contains("new GetPersonalInformationCommand(accountRef"),
                "GET personal-information command must not carry selected-account context");
    }

    @Test
    void controllerPassesDisplayDimensionsFromCurrentPortalAccessContextToMapper() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("accountEnv(portalAccessContext)"),
                "Mapper accountEnv should come from current PortalAccessContext");
        assertTrue(source.contains("trustCode(portalAccessContext)"),
                "Mapper trustCode should come from current PortalAccessContext");
        assertTrue(source.contains("schemeType(portalAccessContext)"),
                "Mapper schemeType should come from current PortalAccessContext");
    }
}
