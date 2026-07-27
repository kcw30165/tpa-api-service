package com.bct.ngtpa.apiservice.adapter.in.web.error;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkflowErrorContractProperties.class)
public class WorkflowErrorContractConfig {
    @Bean
    WorkflowErrorContractResolver workflowErrorContractResolver(WorkflowErrorContractProperties properties) {
        return new WorkflowErrorContractResolver(properties);
    }

    @Bean
    SmartInitializingSingleton workflowErrorContractValidation(WorkflowErrorContractProperties properties) {
        return () -> WorkflowErrorContractValidator.validate(properties);
    }
}
