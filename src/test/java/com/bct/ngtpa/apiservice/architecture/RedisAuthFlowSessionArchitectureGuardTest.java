package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

class RedisAuthFlowSessionArchitectureGuardTest {

  @Test
  void redisAuthFlowSessionAdapterDoesNotDependOnWebCookieOrApim() {
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

  @Test
  void applicationAuthFlowSessionModelStillHasNoRedisOrSpringDependency() {
    JavaClasses classes =
        new ClassFileImporter()
            .importPackages(
                "com.bct.ngtpa.apiservice.application.dto.session.authflow",
                "com.bct.ngtpa.apiservice.application.port.out");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage("..application.dto.session.authflow..", "..application.port.out..")
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
  void p4b2DoesNotIntroduceAuthenticatedSessionImplementation() {
    assertNotEquals(
        com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession.class,
        com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession.class);
    assertNotEquals(
        com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthFlowSessionAdapter.class,
        com.bct.ngtpa.apiservice.adapter.out.redis.auth.RedisAuthenticatedSessionAdapter.class);
  }
}
