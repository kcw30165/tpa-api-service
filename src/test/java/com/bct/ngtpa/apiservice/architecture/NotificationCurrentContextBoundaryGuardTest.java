package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationCurrentContextBoundaryGuardTest {

    @Test
    void notificationCommandsDoNotCarryAccountRef() throws Exception {
        String getCommand = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/GetNotificationsCommand.java"));
        String updateCommand = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdateNotificationsReadStatusCommand.java"));

        assertFalse(getCommand.contains("accountRef"));
        assertFalse(updateCommand.contains("accountRef"));
    }

    @Test
    void notificationControllerDoesNotResolveAccountRef() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationController.java"));

        assertFalse(source.contains("resolveRequiredAccountRef"));
        assertFalse(source.contains("RequestHeaderContext"));
        assertFalse(source.contains("Account-Ref"));
    }

    @Test
    void notificationServicesUseCurrentProviderNotPortalAccessContextPort() throws Exception {
        String getService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetNotificationsService.java"));
        String updateService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/UpdateNotificationsReadStatusService.java"));

        assertTrue(getService.contains("CurrentPortalAccessContextProvider"));
        assertTrue(updateService.contains("CurrentPortalAccessContextProvider"));
        assertTrue(getService.contains("currentPortalAccessContextProvider.current()"));
        assertTrue(updateService.contains("currentPortalAccessContextProvider.current()"));
        assertFalse(getService.contains("PortalAccessContextPort"));
        assertFalse(updateService.contains("PortalAccessContextPort"));
        assertFalse(getService.contains("resolvePortalAccessContext"));
        assertFalse(updateService.contains("resolvePortalAccessContext"));
    }
}
