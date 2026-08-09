package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.in.StartUsernameRecoveryUseCase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 019 Username Recovery start use case.
 *
 * <p>Locks the bounded application boundary of the new start orchestration:
 *
 * <ul>
 *   <li>{@code StartUsernameRecoveryService} is the canonical application-layer implementation
 *       of {@link StartUsernameRecoveryUseCase};</li>
 *   <li>the whole start application package stays framework-free, adapter-free,
 *       infrastructure-free, and never references web DTOs or the {@code ApiStatus} response
 *       enum (so downstream/system outcomes never become public through the application);</li>
 *   <li>the start package never depends on the auth-flow session CSRF package (raw CSRF
 *       generation stays an explicit dependency of the service only via the committed
 *       {@code CsrfTokenGenerator}/{@code CsrfTokenHasher} abstractions).</li>
 * </ul>
 */
class UsernameRecoveryStartUseCaseArchitectureGuardTest {

    private static final String START_PACKAGE = "..application.usecase.auth.usernamerecovery..";

    @Test
    void startServiceIsTheCanonicalImplementationOfTheStartPort() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = classes()
                .that().haveSimpleName("StartUsernameRecoveryService")
                .should().implement(StartUsernameRecoveryUseCase.class)
                .as("StartUsernameRecoveryService must implement StartUsernameRecoveryUseCase");
        rule.check(classes);
    }

    @Test
    void startApplicationLayerHasNoSpringAdapterRedisOrWebDependencies() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(START_PACKAGE)
                .and().haveSimpleNameStartingWith("StartUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryStart")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.springframework.http..",
                        "org.springframework.web..",
                        "jakarta.servlet..",
                        "com.bct.ngtpa.apiservice.adapter..",
                        "com.bct.ngtpa.apiservice.config..",
                        "com.bct.ngtpa.apiservice.infrastructure..",
                        "io.lettuce..",
                        "org.springframework.data.redis..")
                .as("Username Recovery start application classes must stay framework-free and adapter-free");
        rule.check(classes);
    }

    @Test
    void startApplicationLayerNeverReferencesWebDtosOrApiStatus() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(START_PACKAGE)
                .and().haveSimpleNameStartingWith("StartUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryStart")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter.in.web..")
                .orShould().dependOnClassesThat().haveSimpleName("ApiStatus")
                .as("Username Recovery start application layer must never reference web DTOs or ApiStatus");
        rule.check(classes);
    }

    @Test
    void startHelperClassesNeverDependOnTheAuthFlowCsrfPackage() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(START_PACKAGE)
                .and().haveSimpleNameStartingWith("UsernameRecoveryStart")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecase.session.authflow..")
                .as("Username Recovery start helper classes must not depend on the auth-flow CSRF package;"
                        + " only StartUsernameRecoveryService consumes the committed CsrfTokenGenerator/CsrfTokenHasher");
        rule.check(classes);
    }
}
