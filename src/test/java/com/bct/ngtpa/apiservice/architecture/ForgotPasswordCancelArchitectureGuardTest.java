package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel.CancelForgotPasswordStatus;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordCancelArchitectureGuardTest {
  @Test
  void cancelApplicationPackageRemainsFrameworkAndAdapterFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(
                "com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.cancel");
    noClasses()
        .that()
        .resideInAPackage("..application.usecase.auth.forgotpassword.cancel..")
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
  void cancelServiceReusesSharedCancellationCapabilitiesWithoutLockOrIdempotencyKey()
      throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/cancel/CancelForgotPasswordService.java"));
    assertThat(source)
        .contains("AuthFlowSessionPort")
        .contains("AuthFlowSessionCsrfService")
        .contains("OtpChallengePort")
        .contains("AuthFlowSessionCancellationPort")
        .contains("AuthFlowScenario.FORGOT_PASSWORD")
        .contains("AuthFlowSessionCancellationReason.USER_CANCELLED")
        .contains("OtpChallengeInvalidationReason.USER_CANCELLED_FORGOT_PASSWORD")
        .doesNotContain("AuthFlowSessionMutationLockPort")
        .doesNotContain("AuthFlowSessionCompletionLockPort")
        .doesNotContain("ForgotPasswordIdempotencyPort")
        .doesNotContain("Idempotency-Key")
        .doesNotContain(
            ".block(", ".blockOptional(", ".subscribe(", "toFuture().get(", "Thread.sleep(");
  }

  @Test
  void commandRedactsTransportSecrets() {
    var command =
        new CancelForgotPasswordCommand(
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
        new CancelForgotPasswordStatus[] {
          CancelForgotPasswordStatus.CANCELLED,
          CancelForgotPasswordStatus.SESSION_NOT_FOUND,
          CancelForgotPasswordStatus.ALREADY_CANCELLED,
          CancelForgotPasswordStatus.ALREADY_TERMINAL
        }) {
      var result = new CancelForgotPasswordResult(status, true, true, java.util.List.of());
      assertThat(result.successfulCleanup()).isTrue();
      assertThat(result.clearAuthFlowCookie()).isTrue();
      assertThat(result.clearXsrfCookie()).isTrue();
    }
  }

  @Test
  void webMapperOwnsPublicShapingAndNeverExposesInternalData() throws Exception {
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordCancelWebMapper.java"));
    assertThat(mapper)
        .contains("WorkflowNextStep.RETURN_TO_LOGIN")
        .contains("ApiError")
        .contains("MutationResponse")
        .doesNotContain("RedisTemplate")
        .doesNotContain("SessionRevision")
        .doesNotContain("OtpChallenge")
        .doesNotContain("resetAuthorization")
        .doesNotContain("ObjectMapper");
    int toResponseStart = mapper.indexOf("toResponse(");
    int missingSessionStart = mapper.indexOf("missingSession()");
    assertThat(toResponseStart).isGreaterThanOrEqualTo(0);
    assertThat(missingSessionStart).isGreaterThan(toResponseStart);
    String responseRegion = mapper.substring(toResponseStart, missingSessionStart);
    assertThat(responseRegion)
        .doesNotContain("AuthFlowSessionId")
        .doesNotContain("csrf")
        .doesNotContain("session");
    String responseMapping =
        mapper.substring(
            mapper.indexOf("MutationResponse<ForgotPasswordWorkflowResultResponse"),
            Math.min(
                mapper.length(),
                mapper.indexOf("MutationResponse<ForgotPasswordWorkflowResultResponse") + 800));
    assertThat(responseMapping).doesNotContain("AuthFlowSessionId");
  }

  @Test
  void controllerCancelKeepsThinBoundaryWithoutRedisAdapterOrIdempotencyKey() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthForgotPasswordController.java"));
    var cancelMappingPattern =
        java.util.regex.Pattern.compile(
            "@PostMapping\\s*\\([^)]*path\\s*=\\s*\"/cancel\"", java.util.regex.Pattern.DOTALL);

    var cancelMappingMatcher = cancelMappingPattern.matcher(controller);

    assertThat(cancelMappingMatcher.find()).isTrue();

    int cancelMapping = cancelMappingMatcher.start();
    String cancelRegion =
        cancelMapping >= 0
            ? controller.substring(
                cancelMapping, Math.min(controller.length(), cancelMapping + 2200))
            : "";
    assertThat(controller)
        .contains("CancelForgotPasswordUseCase")
        .contains("ForgotPasswordCancelWebMapper")
        .doesNotContain("Account-Ref");
    assertThat(cancelRegion)
        .doesNotContain("Idempotency-Key")
        .doesNotContain("Account-Ref")
        .doesNotContain("RedisTemplate");
  }
}
