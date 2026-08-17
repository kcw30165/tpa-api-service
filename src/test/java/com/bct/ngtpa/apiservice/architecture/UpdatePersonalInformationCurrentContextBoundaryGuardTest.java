package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationCurrentContextBoundaryGuardTest {
    @Test
    void updateCommandCarriesTrustedContextButNotAccountRef() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdatePersonalInformationCommand.java"));
        assertTrue(source.contains("record UpdatePersonalInformationCommand("));
        assertFalse(source.contains("String accountRef,"));
        assertFalse(source.contains("accountRef()"));
        assertTrue(source.contains("String accountEnv"));
        assertTrue(source.contains("String actorUserId"));
        assertTrue(source.contains("ActorUserRole actorUserRole"));
    }

    @Test
    void updateControllerCopiesCurrentContextAndDoesNotReadAccountRefFromHeaderContext() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java"));
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"));
        assertFalse(source.contains("context.accountRef()"));
        assertTrue(source.contains("requestMapper.toCommand(body.applyToAllAccounts(), body)"));
        assertTrue(source.contains("portalAccessContext.account().policyNo()"));
        assertTrue(source.contains("portalAccessContext.actor().actorUserRole()"));
    }

    @Test
    void updateServiceUsesExplicitCommandNotAmbientProvider() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdatePersonalInformationService.java"));
        assertFalse(source.contains("CurrentPortalAccessContextResolver"));
        assertFalse(source.contains("currentPortalAccessContextResolver.current()"));
        assertFalse(source.contains("resolvePortalAccessContext"));
        assertFalse(source.contains("command.accountRef()"));
        assertTrue(source.contains("command.accountEnv()"));
        assertTrue(source.contains("command.actorUserRole()"));
    }
}
