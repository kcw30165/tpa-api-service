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
    void getPersonalInformationCommandContainsBusinessInputOnly() throws Exception {
        String source = Files.readString(COMMAND);

        assertFalse(source.contains("accountRef"),
                "GetPersonalInformationCommand must not carry selected-account context");
        assertTrue(source.contains("record GetPersonalInformationCommand(String language)"),
                "GetPersonalInformationCommand should only carry language for now");
    }

    @Test
    void controllerDoesNotPassAccountRefIntoGetPersonalInformationCommand() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertFalse(source.contains("new GetPersonalInformationCommand(accountRef"),
                "Controller must not pass selected accountRef into the GET command");
        assertTrue(source.contains("new GetPersonalInformationCommand(language)"),
                "Controller should pass business input only into the GET command");
    }

    @Test
    void useCaseResolvesCurrentPortalAccessContextInsteadOfUsingCommandAccountRef() throws Exception {
        String source = Files.readString(SERVICE);

        assertFalse(source.contains("command.accountRef()"),
                "GetPersonalInformationService must not read accountRef from command");
        assertFalse(source.contains("PortalAccessContextPort"),
                "GetPersonalInformationService should not resolve PortalAccessContext by Account-Ref port");
        assertTrue(source.contains("CurrentPortalAccessContextResolver"),
                "GetPersonalInformationService should depend on current request context resolver");
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"),
                "GetPersonalInformationService should use current request PortalAccessContext");
    }
}
