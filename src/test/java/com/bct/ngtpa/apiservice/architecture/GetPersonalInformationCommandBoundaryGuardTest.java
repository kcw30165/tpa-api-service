package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GetPersonalInformationCommandBoundaryGuardTest {
    private static final Path COMMAND = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetPersonalInformationCommand.java");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetPersonalInformationService.java");

    @Test
    void commandCarriesBusinessInputAndTrustedContextButNotAccountRef() throws Exception {
        String source = Files.readString(COMMAND);
        assertFalse(source.contains("accountRef"));
        assertTrue(source.contains("String language"));
        assertTrue(source.contains("String accountEnv"));
        assertTrue(source.contains("String policyNo"));
        assertTrue(source.contains("String certNo"));
        assertTrue(source.contains("String actorUserId"));
    }

    @Test
    void controllerCopiesTrustedContextIntoGetCommand() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertFalse(source.contains("new GetPersonalInformationCommand(accountRef"));
        assertTrue(source.contains("new GetPersonalInformationCommand(language,"));
        assertTrue(source.contains("portalAccessContext.account().policyNo()"));
        assertTrue(source.contains("portalAccessContext.account().certNo()"));
        assertTrue(source.contains("portalAccessContext.actor().actorUserId()"));
    }

    @Test
    void serviceUsesExplicitCommandInsteadOfAmbientResolver() throws Exception {
        String source = Files.readString(SERVICE);
        assertFalse(source.contains("command.accountRef()"));
        assertFalse(source.contains("PortalAccessContextPort"));
        assertFalse(source.contains("CurrentPortalAccessContextResolver"));
        assertFalse(source.contains("currentPortalAccessContextResolver.current()"));
        assertTrue(source.contains("command.accountEnv()"));
        assertTrue(source.contains("command.actorUserId()"));
    }
}
