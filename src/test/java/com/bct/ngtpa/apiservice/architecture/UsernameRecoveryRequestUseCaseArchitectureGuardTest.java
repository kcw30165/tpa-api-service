package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 020 Username Recovery request use case.
 *
 * <p>Locks the bounded application boundary of the new request orchestration:
 *
 * <ul>
 *   <li>{@code RequestUsernameRecoveryService} is the canonical application-layer implementation of
 *       {@link RequestUsernameRecoveryUseCase};</li>
 *   <li>the whole request application package stays framework-free, adapter-free,
 *       infrastructure-free, and never references web DTOs or the {@code ApiStatus} response enum;</li>
 *   <li>the replay codec persists only a session-id descriptor: it must never depend on the auth-flow
 *       CSRF package, {@code java.util.Base64}, or any web/config/adapter/Spring type.</li>
 * </ul>
 */
class UsernameRecoveryRequestUseCaseArchitectureGuardTest {

    private static final String REQUEST_PACKAGE = "..application.usecase.auth.usernamerecovery..";

    @Test
    void requestServiceIsTheCanonicalImplementationOfTheRequestPort() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = classes()
                .that().haveSimpleName("RequestUsernameRecoveryService")
                .should().implement(RequestUsernameRecoveryUseCase.class)
                .as("RequestUsernameRecoveryService must implement RequestUsernameRecoveryUseCase");
        rule.check(classes);
    }

    @Test
    void requestApplicationLayerHasNoSpringAdapterRedisOrWebDependencies() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(REQUEST_PACKAGE)
                .and().haveSimpleNameStartingWith("RequestUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryRequest")
                .and().resideInAnyPackage(REQUEST_PACKAGE)
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
                .as("Username Recovery request application classes must stay framework-free and adapter-free");
        rule.check(classes);
    }

    @Test
    void requestApplicationLayerNeverReferencesWebDtosOrApiStatus() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(REQUEST_PACKAGE)
                .and().haveSimpleNameStartingWith("RequestUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryRequest")
                .and().resideInAnyPackage(REQUEST_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter.in.web..")
                .orShould().dependOnClassesThat().haveSimpleName("ApiStatus")
                .as("Username Recovery request application layer must never reference web DTOs or ApiStatus");
        rule.check(classes);
    }

    @Test
    void requestReplayCodecNeverDependsOnAuthFlowCsrfOrBase64() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAPackage(REQUEST_PACKAGE)
                .and().haveSimpleName("UsernameRecoveryRequestReplayCodec")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecase.session.authflow..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "java.util.Base64")
                .as("Username Recovery request replay codec must never depend on CSRF material or Base64");
        rule.check(classes);
    }

    @Test
    void requestReplayCodecDependencySurfaceIsJavaIdempotencyRecordAndDomainOnly() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = classes()
                .that().resideInAPackage(REQUEST_PACKAGE)
                .and().haveSimpleName("UsernameRecoveryRequestReplayCodec")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java..",
                        REQUEST_PACKAGE,
                        "com.bct.ngtpa.apiservice.application.port.out..",
                        "com.bct.ngtpa.apiservice.domain.model..")
                .as("Username Recovery request replay codec may depend only on Java, the idempotency record, "
                        + "domain models, and its own nested result types");
        rule.check(classes);
    }
}
