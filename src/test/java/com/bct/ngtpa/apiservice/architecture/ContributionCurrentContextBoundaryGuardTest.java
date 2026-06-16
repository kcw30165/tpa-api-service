package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContributionCurrentContextBoundaryGuardTest {

    @Test
    void contributionControllerDoesNotResolveAccountRef() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionController.java"));

        assertFalse(source.contains("resolveRequiredAccountRef"));
        assertFalse(source.contains("PortalAccessContextResolutionException"));
        assertFalse(source.contains("ErrorCodes"));
        assertTrue(source.contains("new GetContributionSummaryCommand("));
        assertTrue(source.contains("new ExportContributionSummaryCommand()"));
    }

    @Test
    void contributionServicesUseCurrentProviderNotPortalAccessContextPort() throws Exception {
        String getService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetContributionSummaryService.java"));
        String exportService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/ExportContributionSummaryService.java"));

        assertTrue(getService.contains("CurrentPortalAccessContextResolver"));
        assertTrue(exportService.contains("CurrentPortalAccessContextResolver"));
        assertTrue(getService.contains("currentPortalAccessContextResolver.current()"));
        assertTrue(exportService.contains("currentPortalAccessContextResolver.current()"));
        assertFalse(getService.contains("PortalAccessContextPort"));
        assertFalse(exportService.contains("PortalAccessContextPort"));
        assertFalse(getService.contains("resolvePortalAccessContext"));
        assertFalse(exportService.contains("resolvePortalAccessContext"));
        assertFalse(getService.contains("command.accountRef()"));
        assertFalse(exportService.contains("command.accountRef()"));
    }
}
