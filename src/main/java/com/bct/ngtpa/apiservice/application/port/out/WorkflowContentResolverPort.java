package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowPageResolutionCommand;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowResolvedPage;
import reactor.core.publisher.Mono;

public interface WorkflowContentResolverPort {
    Mono<WorkflowResolvedPage> resolve(WorkflowPageResolutionCommand command);
}
