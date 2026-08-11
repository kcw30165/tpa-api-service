package com.bct.ngtpa.apiservice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * Architecture guard for the Model B Username Recovery workflow-state sidecar contract (Repair
 * 025A review fix).
 *
 * <p>The sidecar is a protected application/session persistence contract in the application DTO
 * session namespace ({@code application.dto.session.usernamerecovery}), parallel to the generic
 * {@code AuthFlowSession} application DTO; the outbound port/result/command types live in
 * {@code application.port.out}. This guard verifies the corrected production types keep the Clean
 * Architecture boundaries (test classes are excluded from the analysis):
 *
 * <ul>
 *   <li>no Spring, HTTP/web, servlet, adapter, Redis, or Lettuce dependency may cross into the
 *       sidecar DTO/port types;</li>
 *   <li>the sidecar DTO/port types must not reintroduce the legacy session vocabulary (the old
 *       aggregate, identifier, revision, CSRF hash, or port stack);</li>
 *   <li>the sidecar DTO may depend only on approved feature domain candidate/value types, on
 *       {@code AuthFlowSessionId} from the same application layer, and on its same-package
 *       workflow-state revision.</li>
 * </ul>
 *
 * <p>Domain-layer independence from the application layer is enforced separately by the unchanged
 * {@code CleanArchitectureDependencyTest}.
 */
class UsernameRecoveryWorkflowStateArchitectureGuardTest {

    @Test
    void workflowStateDtoAndPortTypesHaveNoSpringAdapterRedisOrWebDependencies() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery",
                        "com.bct.ngtpa.apiservice.application.port.out");

        ArchRuleDefinition.noClasses()
                .that().haveSimpleNameStartingWith("UsernameRecoveryWorkflowState")
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
    void workflowStateSidecarDoesNotDependOnLegacySessionAggregateVocabulary() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery",
                        "com.bct.ngtpa.apiservice.application.port.out");

        ArchRuleDefinition.noClasses()
                .that().haveSimpleNameStartingWith("UsernameRecoveryWorkflowState")
                .should().dependOnClassesThat().haveSimpleName("UsernameRecoverySession")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionId")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionRevision")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoveryCsrfHash")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionPort")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionMutationCommand")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionMutationResult")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionMutationOutcome")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionLoadResult")
                .orShould().dependOnClassesThat().haveSimpleName("UsernameRecoverySessionDeleteResult")
                .check(classes);
    }

    @Test
    void workflowStateSidecarDtoDependsOnlyOnApprovedDomainAndCanonicalSessionTypes() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(
                        "com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery",
                        "com.bct.ngtpa.apiservice.application.port.out");

        ArchRuleDefinition.classes()
                .that().resideInAPackage("..application.dto.session.usernamerecovery..")
                .and().haveSimpleNameStartingWith("UsernameRecoveryWorkflowState")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "java..",
                        "com.bct.ngtpa.apiservice.domain.model..",
                        "com.bct.ngtpa.apiservice.application.dto.session.authflow..",
                        "com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery..")
                .check(classes);
    }
}
