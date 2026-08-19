package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class XsrfCookieSupportArchitectureGuardTest {

  @Test
  void applicationXsrfCookieSupportHasNoSpringWebDependencies() {
    JavaClasses classes =
        new ClassFileImporter()
            .importPackages(
                "com.bct.ngtpa.apiservice.application.dto.session",
                "com.bct.ngtpa.apiservice.application.service.session");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage("..application.dto.session..", "..application.service.session..")
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
  void configMapperMayDependOnAuthFlowSessionPropertiesOnly() {
    JavaClasses classes =
        new ClassFileImporter()
            .importPackages("com.bct.ngtpa.apiservice.adapter.in.web.session.config");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAPackage("..adapter.in.web.session.config..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..adapter.out.redis..",
            "..adapter.out.apim..",
            "..adapter.out.sms..",
            "..adapter.out.email..",
            "..adapter.in.web.controller..",
            "..application.usecase..")
        .check(classes);
  }
}
