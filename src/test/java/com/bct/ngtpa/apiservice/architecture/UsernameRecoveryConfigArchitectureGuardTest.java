package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 016 Username Recovery configuration extension.
 *
 * <p>Keeps the delivery-scenario application DTO framework-free and confines the shared message
 * delivery runtime configuration away from outbound transports, web, and Redis infrastructure.
 * {@code MessageDeliveryRuntimeConfig} legitimately depends on the web-adapter {@code
 * AuthWorkflowProperties} binding class, so outbound adapters are the guarded direction.
 */
class UsernameRecoveryConfigArchitectureGuardTest {

  @Test
  void messageDeliveryScenarioStaysFrameworkFreeInApplicationDto() {
    JavaClasses classes =
        new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.application.dto.message");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage("..application.dto.message..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "com.bct.ngtpa.apiservice.adapter..",
            "com.bct.ngtpa.apiservice.config..",
            "com.bct.ngtpa.apiservice.infrastructure..")
        .check(classes);
  }

  @Test
  void messageDeliveryRuntimeConfigStaysClearOfOutboundTransportAndWebLayers() {
    JavaClasses classes =
        new ClassFileImporter().importPackages("com.bct.ngtpa.apiservice.config.message");

    ArchRuleDefinition.noClasses()
        .that()
        .resideInAnyPackage("..config.message..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.bct.ngtpa.apiservice.adapter.out..",
            "org.springframework.web..",
            "io.lettuce..",
            "org.springframework.data.redis..")
        .check(classes);
  }
}
