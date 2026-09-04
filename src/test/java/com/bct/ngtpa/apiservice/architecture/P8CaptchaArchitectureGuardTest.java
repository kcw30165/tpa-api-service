package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P8CaptchaArchitectureGuardTest {

  @Test
  void useCasesRemainFrameworkFree() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(
                "com.bct.ngtpa.apiservice.application.usecase.auth.registration",
                "com.bct.ngtpa.apiservice.application.service.auth.captcha");

    noClasses()
        .that()
        .resideInAnyPackage(
            "..application.usecase.auth.registration..", "..application.service.auth.captcha..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "com.bct.ngtpa.apiservice.adapter..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
  }

  @Test
  void controllersDoNotDependOnCaptchaRedisAdapter() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/RegistrationController.java"));

    assertThat(controller)
        .doesNotContain(
            "RedisCaptchaChallengeAdapter",
            "CaptchaChallengePort",
            "ReactiveStringRedisTemplate",
            "CaptchaAnswerHasherPort",
            "CaptchaImageGeneratorPort");
  }

  @Test
  void registrationCaptchaGateUsesExistingAuthWorkflowPolicy() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/idempotency/RegistrationStartProtectedOperation.java"));
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/registration/RegistrationStartUseCaseConfig.java"));

    assertThat(source)
        .contains("AuthWorkflowPolicyPort")
        .contains("preStartChallenge")
        .doesNotContain("AuthWorkflowProperties");
    assertThat(config)
        .contains("AuthWorkflowPolicyPort")
        .contains("RegistrationCaptchaGateService")
        .doesNotContain("AuthWorkflowProperties");
  }

  @Test
  void registrationCaptchaGateDoesNotHardcodePolicyValues() throws Exception {
    String gate =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/captcha/RegistrationCaptchaGateService.java"));
    String start =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/idempotency/RegistrationStartProtectedOperation.java"));

    assertThat(gate + start)
        .doesNotContain("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        .doesNotContain("auth-workflows.registration.captcha")
        .doesNotContain("captcha:")
        .doesNotContain("auth-captcha:");
  }

  @Test
  void registrationCaptchaGateDoesNotIntroduceSecondCaptchaRoot() throws Exception {
    Path root = Path.of("src/main/resources");
    if (!Files.exists(root)) {
      return;
    }
    try (var files = Files.walk(root)) {
      String content =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
              .map(this::readSilently)
              .reduce("", (left, right) -> left + "\n" + right);

      assertThat(content)
          .doesNotContainPattern("(?m)^captcha\\s*:")
          .doesNotContainPattern("(?m)^auth-captcha\\s*:");
    }
  }

  private String readSilently(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      return "";
    }
  }
}
