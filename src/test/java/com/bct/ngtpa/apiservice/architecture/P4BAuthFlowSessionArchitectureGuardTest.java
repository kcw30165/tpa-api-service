package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class P4BAuthFlowSessionArchitectureGuardTest {

  @Test
  void p4bApplicationAuthFlowPackagesRemainFrameworkFree() {
    JavaClasses classes =
        new ClassFileImporter()
            .importPackages(
                "com.bct.ngtpa.apiservice.application.dto.session.authflow",
                "com.bct.ngtpa.apiservice.application.usecase.session.authflow",
                "com.bct.ngtpa.apiservice.application.port.out");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage(
            "..application.dto.session.authflow..",
            "..application.usecase.session.authflow..",
            "..application.port.out..")
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
  void p4bRedisAdapterDoesNotDependOnWebCookieApimSmsEmailAdapters() {
    JavaClasses classes =
        new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.adapter.out.redis.auth");

    ArchRuleDefinition.noClasses()
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
}
