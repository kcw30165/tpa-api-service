package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationSessionSecurityAndReactiveCorrectnessTest {

    @Test
    void apiExceptionHandlerNeverBlocksForPortalAccessContext() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java"));
        assertThat(source).doesNotContain(".block(");
        assertThat(source).doesNotContain("currentOrEmpty()");
        assertThat(source).contains("isPreLoginAuthRequest")
                .contains("PortalAccessContextKeys.ATTRIBUTE_KEY");
    }

    @Test
    void registrationErrorResolutionIsPreLoginAndLanguageOnly() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java"));
        assertThat(source).contains("/api/v1/auth/registration")
                .contains("return null;");
    }

    @Test
    void cancellationValidatesCsrfBeforeOtpOrSessionMutation() throws Exception {
        String source = Files.readString(find("CancelRegistrationService.java"));
        int csrfValidation = source.indexOf("csrfService.validateForSession");
        int otpInvalidation = source.indexOf("invalidateOtpIfPresent(command, session, now)");
        int sessionCancellation = source.indexOf("cancellationPort.cancel(");
        assertThat(csrfValidation).isGreaterThanOrEqualTo(0);
        assertThat(otpInvalidation).isGreaterThan(csrfValidation);
        assertThat(sessionCancellation).isGreaterThan(otpInvalidation);
    }

    @Test
    void cancellationDependencyFailureCannotReportSuccessfulCleanup() throws Exception {
        String source = Files.readString(find("CancelRegistrationService.java"));
        assertThat(source).contains(".then(cancellationPort.cancel(")
                .contains(".onErrorResume(RuntimeException.class, ignored -> Mono.just(systemFailure()))");
    }

    private Path find(String fileName) throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            return paths.filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst().orElseThrow();
        }
    }
}
