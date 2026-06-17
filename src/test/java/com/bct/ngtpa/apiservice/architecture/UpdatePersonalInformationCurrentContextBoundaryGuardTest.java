package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationCurrentContextBoundaryGuardTest {

    @Test
    void updateCommandDoesNotCarryAccountRefAsRecordComponent() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdatePersonalInformationCommand.java"));
        assertTrue(source.contains("record UpdatePersonalInformationCommand("));
        assertFalse(source.contains("String accountRef,"));
        assertFalse(source.contains("accountRef()"));
    }

    @Test
    void updateControllerUsesCurrentContextAndDoesNotReadAccountRefFromHeaderContext() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UpdatePersonalInformationController.java"));
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"));
        assertFalse(source.contains("context.accountRef()"));
        assertTrue(source.contains("requestMapper.toCommand(body.applyToAllAccounts(), body)"));
    }

    @Test
    void updateServiceUsesCurrentProviderNotPortalAccessContextPort() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdatePersonalInformationService.java"));
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"));
        assertFalse(source.contains("resolvePortalAccessContext"));
        assertFalse(source.contains("command.accountRef()"));
    }
}
