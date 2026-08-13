package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 022 Username Recovery web DTOs and mappers.
 *
 * <p>Locks the bounded web mapping boundary:
 *
 * <ul>
 *   <li>the web DTO/mapper package stays free of outbound adapters (APIM,
 *       Redis, message), config, infrastructure, Spring, servlet, Reactor, and
 *       controller types;</li>
 *   <li>application and domain layers never depend on the new web package
 *       (no web-to-application reverse dependency);</li>
 *   <li>no all-purpose {@code UsernameRecoveryHttpResponses} type exists in the
 *       feature web package (it is explicitly forbidden by Task 022 and owned
 *       later by Task 023).</li>
 * </ul>
 */
class UsernameRecoveryWebDtoArchitectureGuardTest {

    private static final String WEB_PACKAGE = "..adapter.in.web.auth.usernamerecovery..";

    @Test
    void webDtosAndMappersNeverDependOnAdaptersConfigInfrastructureOrFrameworkTypes() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAPackage(WEB_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter.out..",
                        "com.bct.ngtpa.apiservice.config..",
                        "com.bct.ngtpa.apiservice.infrastructure..",
                        "org.springframework..",
                        "org.springframework.http..",
                        "org.springframework.web..",
                        "org.springframework.web.reactive..",
                        "reactor.core..",
                        "jakarta.servlet..",
                        "io.lettuce..",
                        "org.springframework.data.redis..")
                .as("Username Recovery web DTOs and mappers must stay free of outbound adapters, "
                        + "config, infrastructure, Spring, servlet, and Redis types");
        rule.check(classes);
    }

    @Test
    void applicationAndDomainNeverDependOnUsernameRecoveryWebPackage() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..application..", "..domain..")
                .should().dependOnClassesThat().resideInAPackage(WEB_PACKAGE)
                .as("Application and domain layers must never depend on Username Recovery web DTOs or mappers");
        rule.check(classes);
    }

    @Test
    void noAllPurposeUsernameRecoveryHttpResponsesTypeExistsInTheWebPackage() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAPackage(WEB_PACKAGE)
                .should().haveSimpleName("UsernameRecoveryHttpResponses")
                .as("Task 022 must not create an all-purpose UsernameRecoveryHttpResponses type");
        rule.check(classes);
    }
}
