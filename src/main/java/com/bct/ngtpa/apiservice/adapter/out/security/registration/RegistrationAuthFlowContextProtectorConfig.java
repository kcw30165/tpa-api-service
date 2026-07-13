package com.bct.ngtpa.apiservice.adapter.out.security.registration;

import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RegistrationAuthFlowContextProperties.class)
public class RegistrationAuthFlowContextProtectorConfig {

    @Bean
    @ConditionalOnMissingBean(RegistrationAuthFlowContextProtectorPort.class)
    public RegistrationAuthFlowContextProtectorPort registrationAuthFlowContextProtector(
            RegistrationAuthFlowContextProperties properties) {
        return new AesGcmRegistrationAuthFlowContextProtectorAdapter(properties.getKeyVersion(), properties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean(RegistrationUsernameCheckProtectorPort.class)
    public RegistrationUsernameCheckProtectorPort registrationUsernameCheckProtector(
            RegistrationAuthFlowContextProperties properties) {
        return new AesGcmRegistrationUsernameCheckProtectorAdapter(properties.getKeyVersion(), properties.getSecret());
    }

}
