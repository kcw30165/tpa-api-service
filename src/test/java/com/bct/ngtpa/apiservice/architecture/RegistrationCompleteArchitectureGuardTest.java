package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegistrationCompleteArchitectureGuardTest {

  private static final Path APPLICATION_SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/SharedCompleteRegistrationService.java");
  private static final Path REGISTRATION_CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/"
              + "RegistrationController.java");

  @Test
  void completionUseCaseRemainsFrameworkAndAdapterFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(
                "com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete");

    noClasses()
        .that()
        .resideInAPackage("..application.usecase.auth.registration.complete..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter..",
            "..config..",
            "org.springframework..",
            "jakarta.servlet..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
  }

  @Test
  void existingRegistrationControllerSolelyOwnsCompleteEndpoint() throws Exception {
    String controller = Files.readString(REGISTRATION_CONTROLLER);

    assertThat(controller)
        .containsIgnoringWhitespaces("@PostMapping(path = \"/complete\"")
        .containsIgnoringWhitespaces("CompleteRegistrationUseCase")
        .containsIgnoringWhitespaces("CompleteRegistrationWebMapper")
        .containsIgnoringWhitespaces("cookieWriter.clearAuthFlowCookie(response)");

    long constructorCount =
        Pattern.compile("public\\s+AuthRegistrationController\\s*\\(")
            .matcher(controller)
            .results()
            .count();
    assertThat(constructorCount).isEqualTo(0);
  }

  @Test
  void applicationOrchestrationUsesProtectedContextAndTypedCapabilities() throws Exception {
    String source = Files.readString(APPLICATION_SERVICE);

    assertThat(source)
        .containsIgnoringWhitespaces("RegistrationProtectedAuthFlowContextLoader")
        .containsIgnoringWhitespaces("RegistrationUsernameCheckContextLoader")
        .containsIgnoringWhitespaces("AuthFlowSessionCompletionLockPort")
        .containsIgnoringWhitespaces("AuthFlowSessionExpiryRefreshPort")
        .containsIgnoringWhitespaces("AuthFlowSessionCancellationPort")
        .containsIgnoringWhitespaces("case UNKNOWN_OUTCOME_EXISTING")
        .containsIgnoringWhitespaces("AuthFlowStatus.COMPLETING")
        .doesNotContain("AuthFlowSessionProperties")
        .doesNotContain("adapter.in.web")
        .doesNotContain("adapter.out.redis")
        .doesNotContain("adapter.out.apim");
  }

  @Test
  void passwordAndProtectedStateAreNotWrittenToLogsOrIdempotencySnapshots() throws Exception {
    String service = Files.readString(APPLICATION_SERVICE);
    String request =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/dto/"
                    + "CompleteRegistrationRequest.java"));
    String command =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
                    + "CompleteRegistrationCommand.java"));

    assertThat(request)
        .containsIgnoringWhitespaces("password=<redacted>", "confirmPassword=<redacted>");
    assertThat(command)
        .containsIgnoringWhitespaces("password=<redacted>")
        .containsIgnoringWhitespaces("confirmPassword=<redacted>")
        .containsIgnoringWhitespaces("authFlowSessionId=<redacted>")
        .containsIgnoringWhitespaces("csrfToken=<redacted>")
        .containsIgnoringWhitespaces("idempotencyKey=<redacted>");
    assertThat(service)
        .containsIgnoringWhitespaces("sha256(c.password())")
        .doesNotContain("responseSnapshot(), c.password()")
        .doesNotContain("log.info")
        .doesNotContain("log.debug")
        .doesNotContain("log.error");
  }
}
