package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowPageSelections;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageResolutionCommand;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageSelection;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPage;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPageAction;
import com.bct.ngtpa.apiservice.application.port.out.WorkflowContentResolverPort;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WorkflowPageArchitectureGuardTest {

    @Test
    void workflowPageModelsDoNotDependOnAuthFlowSession() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                WorkflowPageSelection.class,
                WorkflowPageResolutionCommand.class,
                WorkflowResolvedPage.class,
                WorkflowResolvedPageAction.class,
                RegistrationWorkflowPageSelections.class,
                WorkflowContentResolverPort.class);

        ArchRuleDefinition.noClasses()
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..application.dto.session.authflow..")
                .check(classes);
    }

    @Test
    void workflowPageModelsDoNotIntroduceNewPageDsl() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageDsl");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.NewBffPagesDsl");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageDefinitionRepository");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageDatabase");
    }

    @Test
    void workflowPageModelsDoNotIntroduceRuntimeEngine() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowEngine");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowGraph");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowRuntime");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowTransitionExecutor");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowDefinitionRepository");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowDatabaseState");
    }

    @Test
    void workflowPageModelsDoNotIntroduceAuthFlowResume() throws IOException {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.request.AuthFlowResumeRequest");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.ResumeAuthFlowUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.out.AuthFlowResumePort");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowResumeService");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthFlowResumeController");
        assertThat(sourceContains("/api/v1/auth/flow/resume")).isFalse();
    }

    @Test
    void workflowPageApplicationModelsHaveNoSpringAdapterRedisOrWebDependencies() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                WorkflowPageSelection.class,
                WorkflowPageResolutionCommand.class,
                WorkflowResolvedPage.class,
                WorkflowResolvedPageAction.class,
                RegistrationWorkflowPageSelections.class,
                WorkflowContentResolverPort.class);

        ArchRuleDefinition.noClasses()
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
    void p5_4DoesNotIntroduceRegistrationEndpointsOrApimAdapters() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthRegistrationOtpController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationStartAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationVerificationAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationCredentialAdapter");
    }

    private static void assertClassAbsent(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    private static boolean sourceContains(String text) throws IOException {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) {
            return false;
        }
        try (var paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java")
                            || path.toString().endsWith(".yml")
                            || path.toString().endsWith(".yaml"))
                    .anyMatch(path -> contains(path, text));
        }
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            return false;
        }
    }
}
