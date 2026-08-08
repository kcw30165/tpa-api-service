package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 015 Username Recovery reservation application layer.
 *
 * <p>Verifies the bounded application integration keeps the repository's Clean Architecture
 * boundaries: no Spring, HTTP/web, servlet, adapter, Redis, or Lettuce dependency may cross into
 * the new application package. The integration must depend only on the committed session port,
 * domain models, the shared application CSRF verification abstraction, and Reactor.
 */
class UsernameRecoveryReservationArchitectureGuardTest {

    private static final String RESERVATION_PACKAGE = "com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery";

    @Test
    void reservationApplicationLayerHasNoSpringAdapterRedisOrWebDependencies() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages(RESERVATION_PACKAGE);

        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..application.usecase.auth.usernamerecovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
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
    void reservationApplicationLayerDoesNotDependOnInfrastructureExceptionsOrWebDtos() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages(RESERVATION_PACKAGE);

        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..application.usecase.auth.usernamerecovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.exception..",
                        "com.bct.ngtpa.apiservice.adapter.in.web..")
                .check(classes);
    }
}
