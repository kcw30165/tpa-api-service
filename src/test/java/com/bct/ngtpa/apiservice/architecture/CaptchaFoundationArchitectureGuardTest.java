package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class CaptchaFoundationArchitectureGuardTest {

  @Test
  void captchaApplicationModelsHaveNoSpringRedisWebOrAdapterDependencies() {
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
            "com.bct.ngtpa.apiservice.adapter..",
            "io.lettuce..",
            "org.springframework.data.redis..",
            "org.slf4j..")
        .check(classes);
  }
}
