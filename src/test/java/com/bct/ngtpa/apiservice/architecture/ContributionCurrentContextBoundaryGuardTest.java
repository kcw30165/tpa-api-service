package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContributionCurrentContextBoundaryGuardTest {
    @Test
    void contributionControllerCopiesTrustedContextWithoutResolvingAccountRef() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionController.java"));
        assertFalse(source.contains("resolveRequiredAccountRef"));
        assertFalse(source.contains("PortalAccessContextResolutionException"));
        assertFalse(source.contains("ErrorCodes"));
        assertTrue(source.contains("CurrentPortalAccessContextResolver"));
        assertTrue(source.contains("currentPortalAccessContextResolver.current()"));
        assertTrue(source.contains("new GetContributionSummaryCommand("));
        assertTrue(source.contains("new ExportContributionSummaryCommand("));
        assertTrue(source.contains(".account()"));
        assertTrue(source.contains(".actor()"));
    }

    @Test
    void contributionServicesUseExplicitCommandsNotAmbientContext() throws Exception {
        String getService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetContributionSummaryService.java"));
        String exportService = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/ExportContributionSummaryService.java"));
        for (String source : new String[] {getService, exportService}) {
            assertFalse(source.contains("CurrentPortalAccessContextResolver"));
            assertFalse(source.contains("currentPortalAccessContextResolver.current()"));
            assertFalse(source.contains("PortalAccessContextPort"));
            assertFalse(source.contains("resolvePortalAccessContext"));
            assertFalse(source.contains("command.accountRef()"));
            assertTrue(source.contains("command.accountEnv()"));
            assertTrue(source.contains("command.actorUserId()"));
        }
    }
}
