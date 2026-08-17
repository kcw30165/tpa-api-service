package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApimProperties.class)
public class ApimRoutingValidationConfig {

    public ApimRoutingValidationConfig(ApimProperties apimProperties) {
        // validate at startup and fail fast if routing is invalid
        apimProperties.validateRouting();
    }
}
