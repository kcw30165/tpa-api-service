package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.usecase.auth.registration.StartRegistrationService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartRegistrationUseCaseArchitectureGuardTest {
  @Test
  void startRegistrationUseCaseHasNoWebRedisApimAdapterOrCookieDependencies() {
    var classes =
        new ClassFileImporter()
            .importPackages("com.bct.ngtpa.apiservice.application.usecase.auth.registration");
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("..application.usecase.auth.registration..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.in.web..",
            "..adapter.out.redis..",
            "..adapter.out.apim..",
            "org.springframework..",
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
    assertThat(StartRegistrationService.class).isNotNull();
  }

  @Test
  void useCaseDoesNotHardcodeUserFacingMessages() throws IOException {
    Path source =
        Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/StartRegistrationUseCase.java");
    if (Files.exists(source)) {
      String text = Files.readString(source);
      assertThat(text)
          .doesNotContain(
              "We are unable to verify your details",
              "A new verification code has been sent",
              "我們未能核實您的資料");
    }
  }
}
