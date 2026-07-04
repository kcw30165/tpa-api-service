package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationStartWebMapper;
import java.time.Duration;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationStartWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationStartWebMapper registrationStartWebMapper(
            @Value("${registration-start.idempotency.ttl-seconds:300}") int idempotencyTtlSeconds, ErrorMessageResolver errorMessageResolver) {
        return new RegistrationStartWebMapper(Duration.ofSeconds(Math.max(1, idempotencyTtlSeconds)), errorMessageResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthFlowCookieWriter authFlowCookieWriter() {
        return AuthFlowCookieWriter.defaultWriter();
    }
}
