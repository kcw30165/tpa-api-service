package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Username Recovery production application layer.
 *
 * <p>Verifies production classes in the bounded application package keep the repository's Clean
 * Architecture boundaries. Test fixtures are deliberately excluded because they may instantiate
 * concrete adapters to exercise encryption and other boundary behavior.
 */
class UsernameRecoveryReservationArchitectureGuardTest {

    private static final String RESERVATION_PACKAGE = "com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery";

    @Test
    void reservationApplicationLayerHasNoSpringAdapterRedisOrWebDependencies() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
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
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(RESERVATION_PACKAGE);

        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..application.usecase.auth.usernamerecovery..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.exception..",
                        "com.bct.ngtpa.apiservice.adapter.in.web..")
                .check(classes);
    }
}
