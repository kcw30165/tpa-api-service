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
    void commandContainsLanguageOnly() throws Exception {
        String source = Files.readString(COMMAND);
        assertTrue(source.contains("record GetReferenceDataCountriesCommand(String language)"));
        assertFalse(source.contains("accountRef"));
    }

    @Test
    void controllerDoesNotResolveOrPassAccountRef() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("new GetReferenceDataCountriesCommand(language)"));
        assertFalse(source.contains("new GetReferenceDataCountriesCommand(accountRef"));
        assertFalse(source.contains("resolveRequiredAccountRef"));
        assertFalse(source.contains("PortalAccessContextPort"));
        assertFalse(source.contains("CurrentPortalAccessContextProvider"));
    }

    @Test
    void serviceRemainsContextFreeBecauseCountryListIsGlobal() throws Exception {
        String source = Files.readString(SERVICE);
        assertTrue(source.contains("fetchCountryList()"));
        assertFalse(source.contains("PortalAccessContextPort"));
        assertFalse(source.contains("CurrentPortalAccessContextProvider"));
        assertFalse(source.contains("currentPortalAccessContextProvider"));
        assertFalse(source.contains("requireAccountRef"));
    }
}
