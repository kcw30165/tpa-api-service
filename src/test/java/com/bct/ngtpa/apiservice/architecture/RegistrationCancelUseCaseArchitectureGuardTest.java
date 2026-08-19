package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationResultStatus;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class RegistrationCancelUseCaseArchitectureGuardTest {
  @Test
  void cancelApplicationPackageRemainsFrameworkAndAdapterFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(
                "com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel");
    noClasses()
        .that()
        .resideInAPackage("..application.usecase.auth.registration.cancel..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter..",
            "..config..",
            "org.springframework..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
  }

  @Test
  void commandRedactsTransportSecrets() {
    var command =
        new CancelRegistrationCommand(
            new AuthFlowSessionId("afs_secret"),
            "csrf_secret",
            "https://origin.secret",
            "https://referer.secret",
            "request-secret");
    assertThat(command.toString())
        .doesNotContain(
            "afs_secret", "csrf_secret", "origin.secret", "referer.secret", "request-secret");
  }

  @Test
  void missingAndTerminalSessionsArePubliclySuccessfulCleanupOutcomes() {
    for (var status :
        new CancelRegistrationResultStatus[] {
          CancelRegistrationResultStatus.CANCELLED,
          CancelRegistrationResultStatus.SESSION_NOT_FOUND,
          CancelRegistrationResultStatus.ALREADY_CANCELLED,
          CancelRegistrationResultStatus.ALREADY_TERMINAL
        }) {
      var result = new CancelRegistrationResult(status, true, true, java.util.List.of());
      assertThat(result.successfulCleanup()).isTrue();
      assertThat(result.clearAuthFlowCookie()).isTrue();
      assertThat(result.clearXsrfCookie()).isTrue();
    }
  }
}
