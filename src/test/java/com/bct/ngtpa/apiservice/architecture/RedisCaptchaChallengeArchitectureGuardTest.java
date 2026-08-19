package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class RedisCaptchaChallengeArchitectureGuardTest {

  @Test
  void redisCaptchaAdapterDoesNotReadAuthWorkflowPolicyDirectly() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

    noClasses()
        .that()
        .resideInAPackage("..adapter.out.redis.auth..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName(
            "com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName(
            "com.bct.ngtpa.apiservice.application.port.in.AuthWorkflowPolicyPort")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName(
            "com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy")
        .check(classes);
  }

  @Test
  void redisCaptchaAdapterDoesNotDependOnWebApimSmsEmailAdapters() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

    noClasses()
        .that()
        .resideInAPackage("..adapter.out.redis.auth..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.in.web..",
            "..adapter.out.apim..",
            "..adapter.out.sms..",
            "..adapter.out.email..")
        .check(classes);
  }

  @Test
  void captchaApplicationModelStillHasNoRedisSpringOrWebDependency() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(
                "com.bct.ngtpa.apiservice.application.dto.auth.captcha",
                "com.bct.ngtpa.apiservice.application.port.out");

    noClasses()
        .that()
        .resideInAPackage("..application.dto.auth.captcha..")
        .or()
        .resideInAPackage("..application.port.out..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "io.lettuce..",
            "org.springframework.data.redis..",
            "com.bct.ngtpa.apiservice.adapter..")
        .check(classes);
  }

  @Test
  void p8_5AllowsControllerEndpointsWithoutChangingRedisBoundary() throws Exception {
    assertClassPresent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
  }

  private static void assertClassPresent(String className) throws Exception {
    assertThat(Class.forName(className)).isNotNull();
  }
}
