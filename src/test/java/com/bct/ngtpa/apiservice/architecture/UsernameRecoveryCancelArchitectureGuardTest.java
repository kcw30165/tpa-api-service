package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Task 021 Username Recovery cancel use case.
 *
 * <p>Locks the bounded application boundary of the new cancel orchestration:
 *
 * <ul>
 *   <li>{@code CancelUsernameRecoveryService} is the canonical application-layer implementation of
 *       {@link CancelUsernameRecoveryUseCase};</li>
 *   <li>the whole cancel application package stays framework-free, adapter-free,
 *       infrastructure-free, and never references web DTOs or the {@code ApiStatus} response
 *       enum;</li>
 *   <li>the cancel replay codec persists only a constant descriptor: it must never depend on the
 *       auth-flow CSRF package, the domain session model, {@code java.util.Base64}, or any
 *       web/config/adapter/Spring type.</li>
 * </ul>
 */
class UsernameRecoveryCancelArchitectureGuardTest {

    private static final String CANCEL_PACKAGE = "..application.usecase.auth.usernamerecovery..";

    @Test
    void cancelServiceIsTheCanonicalImplementationOfTheCancelPort() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = classes()
                .that().haveSimpleName("CancelUsernameRecoveryService")
                .should().implement(CancelUsernameRecoveryUseCase.class)
                .as("CancelUsernameRecoveryService must implement CancelUsernameRecoveryUseCase");
        rule.check(classes);
    }

    @Test
    void cancelServiceUsesSingleEnvelopeProtectedContextBoundary() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/CancelUsernameRecoveryService.java"));
        org.assertj.core.api.Assertions.assertThat(source)
                .doesNotContain(
                        "UsernameRecoverySessionPort",
                        "UsernameRecoverySession",
                        "UsernameRecoverySessionId",
                        "UsernameRecoveryWorkflowStatePort",
                        "workflowStatePort",
                        "deleteRevisioned")
                .contains(
                        "AuthFlowSessionPort",
                        "UsernameRecoveryAuthFlowContextBinding",
                        "cancelRevisioned",
                        "validateRetainedContext");
    }

    @Test
    void cancelApplicationLayerHasNoSpringAdapterRedisOrWebDependencies() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(CANCEL_PACKAGE)
                .and().haveSimpleNameStartingWith("CancelUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryCancel")
                .and().resideInAnyPackage(CANCEL_PACKAGE)
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
                .as("Username Recovery cancel application classes must stay framework-free and adapter-free");
        rule.check(classes);
    }

    @Test
    void cancelApplicationLayerNeverReferencesWebDtosOrApiStatus() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAnyPackage(CANCEL_PACKAGE)
                .and().haveSimpleNameStartingWith("CancelUsernameRecovery")
                .or().haveSimpleNameStartingWith("UsernameRecoveryCancel")
                .and().resideInAnyPackage(CANCEL_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter.in.web..")
                .orShould().dependOnClassesThat().haveSimpleName("ApiStatus")
                .as("Username Recovery cancel application layer must never reference web DTOs or ApiStatus");
        rule.check(classes);
    }

    @Test
    void cancelReplayCodecNeverDependsOnAuthFlowCsrfDomainSessionOrBase64() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = noClasses()
                .that().resideInAPackage(CANCEL_PACKAGE)
                .and().haveSimpleName("UsernameRecoveryCancelReplayCodec")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..usecase.session.authflow..",
                        "com.bct.ngtpa.apiservice.domain.model..")
                .orShould().dependOnClassesThat().haveFullyQualifiedName(
                        "java.util.Base64")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoveryCsrfHash")
                .as("Username Recovery cancel replay codec must never depend on CSRF material, "
                        + "domain session models, or Base64");
        rule.check(classes);
    }

    @Test
    void cancelReplayCodecDependencySurfaceIsJavaIdempotencyRecordAndOwnTypesOnly() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");

        ArchRule rule = classes()
                .that().resideInAPackage(CANCEL_PACKAGE)
                .and().haveSimpleName("UsernameRecoveryCancelReplayCodec")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java..",
                        CANCEL_PACKAGE,
                        "com.bct.ngtpa.apiservice.application.port.out..")
                .as("Username Recovery cancel replay codec may depend only on Java, the idempotency "
                        + "record, and its own nested result types");
        rule.check(classes);
    }
}
