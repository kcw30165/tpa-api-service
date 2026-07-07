package com.bct.ngtpa.apiservice.adapter.in.web.workflow.config;

import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowMutationResponseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowMutationResponseMapperConfig {

    @Bean
    @ConditionalOnMissingBean(WorkflowMutationResponseMapper.class)
    public WorkflowMutationResponseMapper workflowMutationResponseMapper() {
        return new WorkflowMutationResponseMapper();
    }
}
