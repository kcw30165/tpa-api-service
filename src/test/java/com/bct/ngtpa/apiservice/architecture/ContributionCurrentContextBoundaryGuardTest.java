package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContributionCurrentContextBoundaryGuardTest {
  @Test
  void contributionControllerCopiesTrustedContextWithoutResolvingAccountRef() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionController.java"));
    assertFalse(containsIgnoringWhitespace(source, "resolveRequiredAccountRef"));
    assertFalse(containsIgnoringWhitespace(source, "PortalAccessContextResolutionException"));
    assertFalse(containsIgnoringWhitespace(source, "ErrorCodes"));
    assertTrue(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
    assertTrue(containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
    assertTrue(containsIgnoringWhitespace(source, "new GetContributionSummaryCommand("));
    assertTrue(containsIgnoringWhitespace(source, "new ExportContributionSummaryCommand("));
    assertTrue(containsIgnoringWhitespace(source, ".account()"));
    assertTrue(containsIgnoringWhitespace(source, ".actor()"));
  }

  @Test
  void contributionServicesUseExplicitCommandsNotAmbientContext() throws Exception {
    String getService =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetContributionSummaryService.java"));
    String exportService =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/ExportContributionSummaryService.java"));
    for (String source : new String[] {getService, exportService}) {
      assertFalse(containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"));
      assertFalse(
          containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"));
      assertFalse(containsIgnoringWhitespace(source, "PortalAccessContextPort"));
      assertFalse(containsIgnoringWhitespace(source, "resolvePortalAccessContext"));
      assertFalse(containsIgnoringWhitespace(source, "command.accountRef()"));
      assertTrue(containsIgnoringWhitespace(source, "command.accountEnv()"));
      assertTrue(containsIgnoringWhitespace(source, "command.actorUserId()"));
    }
  }
}
