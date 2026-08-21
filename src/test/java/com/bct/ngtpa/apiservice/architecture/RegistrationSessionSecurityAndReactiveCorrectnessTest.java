package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationSessionSecurityAndReactiveCorrectnessTest {

  @Test
  void apiExceptionHandlerNeverBlocksForPortalAccessContext() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java"));
    assertThat(source).doesNotContain(".block(");
    assertThat(source).doesNotContain("currentOrEmpty()");
    assertThat(source)
        .containsIgnoringWhitespaces("isPreLoginAuthRequest")
        .containsIgnoringWhitespaces("PortalAccessContextKeys.ATTRIBUTE_KEY");
  }

  @Test
  void registrationErrorResolutionIsPreLoginAndLanguageOnly() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java"));
    assertThat(source)
        .containsIgnoringWhitespaces("/api/v1/auth/registration")
        .containsIgnoringWhitespaces("return null;");
  }

  @Test
  void cancellationValidatesCsrfBeforeOtpOrSessionMutation() throws Exception {
    String source = Files.readString(find("CancelRegistrationService.java"));
    int csrfValidation = indexOfIgnoringWhitespace(source, "csrfService.validateForSession");
    int otpInvalidation =
        indexOfIgnoringWhitespace(source, "invalidateOtpIfPresent(command, session, now)");
    int sessionCancellation = indexOfIgnoringWhitespace(source, "cancellationPort.cancel(");
    assertThat(csrfValidation).isGreaterThanOrEqualTo(0);
    assertThat(otpInvalidation).isGreaterThan(csrfValidation);
    assertThat(sessionCancellation).isGreaterThan(otpInvalidation);
  }

  @Test
  void cancellationDependencyFailureCannotReportSuccessfulCleanup() throws Exception {
    String source = Files.readString(find("CancelRegistrationService.java"));
    assertThat(source)
        .containsIgnoringWhitespaces(".then(cancellationPort.cancel(")
        .containsIgnoringWhitespaces(
            ".onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()))");
  }

  private Path find(String fileName) throws Exception {
    try (var paths = Files.walk(Path.of("src/main/java"))) {
      return paths
          .filter(path -> path.getFileName().toString().equals(fileName))
          .findFirst()
          .orElseThrow();
    }
  }
}
