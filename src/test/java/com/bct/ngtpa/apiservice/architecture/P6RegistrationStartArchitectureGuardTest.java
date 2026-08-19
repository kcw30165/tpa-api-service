package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class P6RegistrationStartArchitectureGuardTest {
  @Test
  void p6ApplicationUseCaseRemainsFrameworkFree() {
    var classes =
        new ClassFileImporter()
            .importPackages("com.bct.ngtpa.apiservice.application.usecase.auth.registration");
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("..application.usecase.auth.registration..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "org.springframework.http..",
            "org.springframework.web..",
            "jakarta.servlet..",
            "..adapter..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
  }

  @Test
  void p6ControllerDoesNotDependOnOutboundAdapters() {
    var classes =
        new ClassFileImporter()
            .importPackages("com.bct.ngtpa.apiservice.adapter.in.web.auth.registration");
    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("..adapter.in.web.auth.registration..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.out.apim..",
            "..adapter.out.redis..",
            "..adapter.out.sms..",
            "..adapter.out.email..",
            "org.springframework.data.redis..",
            "org.springframework.web.reactive.function.client..")
        .check(classes);
  }
}
