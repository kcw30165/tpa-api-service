package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GetReferenceDataCountriesCommandBoundaryGuardTest {

    private static final Path COMMAND = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetReferenceDataCountriesCommand.java");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataController.java");
    private static final Path SERVICE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetReferenceDataCountriesService.java");

    @Test
    void commandContainsExplicitAuthenticatedRouting() throws Exception {
        String source = Files.readString(COMMAND);
        assertTrue(source.contains("String language"));
        assertTrue(source.contains("String accountEnv"));
        assertTrue(source.contains("ActorUserRole actorUserRole"));
        assertFalse(source.contains("accountRef"));
    }

    @Test
    void controllerUsesCurrentTrustedContextWithoutPassingAccountRef() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"));
        assertTrue(source.contains("portalAccessContext.account().accountEnv()"));
        assertTrue(source.contains("portalAccessContext.actor().actorUserRole()"));
        assertFalse(source.contains("new GetReferenceDataCountriesCommand(accountRef"));
        assertFalse(source.contains("resolveRequiredAccountRef"));
        assertFalse(source.contains("PortalAccessContextPort"));
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
    }

    @Test
    void serviceRemainsAmbientContextFreeAndPassesExplicitCommand() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("fetchCountryList(command)"));
        assertFalse(source.contains("PortalAccessContextPort"));
        assertFalse(source.contains("CurrentPortalAccessContextResolver"));
        assertFalse(source.contains("currentPortalAccessContextResolver"));
        assertFalse(source.contains("PortalAccessContext"));
        assertFalse(source.contains("deferContextual"));
        assertFalse(source.contains("reactor.util.context"));
        assertFalse(source.contains("requireAccountRef"));
    }
}
