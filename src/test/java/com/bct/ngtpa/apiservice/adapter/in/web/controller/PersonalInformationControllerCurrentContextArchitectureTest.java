package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationControllerCurrentContextArchitectureTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");

    @Test
    void controllerUsesCurrentCurrentPortalAccessContextResolverInsteadOfPortalAccessContextPort() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("PortalAccessContextPort"),
                "PersonalInformationController must not directly resolve account context from PortalAccessContextPort");
        assertTrue(source.contains("CurrentPortalAccessContextResolver"),
                "PersonalInformationController should depend on the current request context resolver");
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"),
                "PersonalInformationController should read the already-populated per-request PortalAccessContext");
    }

    @Test
    void controllerGetsAccountRefAndDisplayDimensionsFromCurrentPortalAccessContext() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("resolveRequiredAccountRef(ContextView"),
                "PersonalInformationController should not manually resolve Account-Ref from RequestHeaderContext");
        assertTrue(source.contains("new GetPersonalInformationCommand(language)"),
                "Get command should not use accountRef");
        assertTrue(source.contains("accountEnv(portalAccessContext)"),
                "Mapper accountEnv should come from current PortalAccessContext");
        assertTrue(source.contains("trustCode(portalAccessContext)"),
                "Mapper trustCode should come from current PortalAccessContext");
        assertTrue(source.contains("schemeType(portalAccessContext)"),
                "Mapper schemeType should come from current PortalAccessContext");
    }
}
