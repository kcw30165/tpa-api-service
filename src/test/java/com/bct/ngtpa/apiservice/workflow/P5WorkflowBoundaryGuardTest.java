package com.bct.ngtpa.apiservice.workflow;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class P5WorkflowBoundaryGuardTest {

    @Test
    void p5DoesNotIntroduceApimRegistrationAdapters() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationStartAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationVerificationAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.ApimRegistrationCredentialAdapter");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.apim.TRPSearchUnregisteredMemberAdapter");
    }

    @Test
    void p5DoesNotIntroduceCaptchaEndpointRuntime() {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.in.web.filter.CaptchaWebFilter");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.usecase.auth.CaptchaVerificationUseCase");
    }

    @Test
    void p5DoesNotIntroduceFundSwitchingBusinessLogic() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.port.in.FundSwitchingUseCase");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.FundSwitchingWorkflowState");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.usecase.workflow.ExecuteFundSwitchingWorkflow");
    }

    @Test
    void p5DoesNotImplementLocalizedWorkflowResolverOrGenericErrorConverter() {
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowLocalizedMessageResolver");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorMessageConverter");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowErrorResponseMapper");
        assertClassAbsent("com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowMessageResolutionService");
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
