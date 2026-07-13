package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationStartWebConfig {
    @Bean
    @ConditionalOnMissingBean
    public RegistrationStartWebMapper registrationStartWebMapper(
            RegistrationIdempotencyProperties idempotencyProperties,
            ErrorMessageResolver errorMessageResolver) {
        return new RegistrationStartWebMapper(idempotencyProperties.startTtl(), errorMessageResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthFlowCookieWriter authFlowCookieWriter(AuthFlowSessionProperties authFlowSessionProperties) {
        return AuthFlowCookieWriter.from(authFlowSessionProperties);
    }
}
