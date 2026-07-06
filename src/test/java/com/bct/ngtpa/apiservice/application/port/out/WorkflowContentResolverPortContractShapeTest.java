package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageResolutionCommand;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPage;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class WorkflowContentResolverPortContractShapeTest {

    @Test
    void workflowContentResolverPortExposesResolveOperation() throws Exception {
        Method resolve = WorkflowContentResolverPort.class.getMethod("resolve", WorkflowPageResolutionCommand.class);

        assertThat(resolve.getReturnType()).isEqualTo(Mono.class);
        assertThat(resolve.getGenericReturnType().getTypeName()).contains(WorkflowResolvedPage.class.getSimpleName());
    }
}
