package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 017 Username Recovery inbound use-case ports.
 *
 * <p>Verifies that the three new ports stay framework-free, expose interfaces only, and have no
 * implementation classes in this task. The ports may depend only on the committed application
 * DTO package and Reactor; Spring, servlet, web, adapter, configuration, infrastructure, and Redis
 * types must never cross the inbound boundary.
 */
class UsernameRecoveryInboundPortArchitectureGuardTest {

    @Test
    void usernameRecoveryInboundPortsStayFrameworkAndAdapterFree() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.application.port.in");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..application.port.in..")
                .and().haveSimpleNameEndingWith("UsernameRecoveryUseCase")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "com.bct.ngtpa.apiservice.adapter..",
                        "com.bct.ngtpa.apiservice.config..",
                        "com.bct.ngtpa.apiservice.infrastructure..",
                        "io.lettuce..",
                        "org.springframework.data.redis..")
                .as("Username Recovery inbound ports must stay framework-free and adapter-free");
        rule.check(classes);
    }

    @Test
    void usernameRecoveryInboundPortsAreInterfacesOnly() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.application.port.in");

        ArchRule rule = classes()
                .that().resideInAPackage("..application.port.in..")
                .and().haveSimpleNameEndingWith("UsernameRecoveryUseCase")
                .should().beInterfaces()
                .as("Username Recovery inbound ports must be interfaces, not concrete classes");
        rule.check(classes);
    }

    @Test
    void usernameRecoveryInboundPortsHaveNoImplementationClassesInThisTask() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage("com.bct.ngtpa.apiservice..")
                .and().areNotInterfaces()
                .should().implement(StartUsernameRecoveryUseCase.class)
                .orShould().implement(RequestUsernameRecoveryUseCase.class)
                .orShould().implement(CancelUsernameRecoveryUseCase.class)
                .as("Task 017 must not introduce Username Recovery inbound-port implementations");
        rule.check(classes);
    }
}
