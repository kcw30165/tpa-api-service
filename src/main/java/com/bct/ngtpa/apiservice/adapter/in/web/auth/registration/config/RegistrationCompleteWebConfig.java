package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.CompleteRegistrationWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.shared.error.ErrorMessageResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationCompleteWebConfig {
    @Bean
    @ConditionalOnMissingBean
    CompleteRegistrationWebMapper completeRegistrationWebMapper(
            RegistrationIdempotencyProperties idempotency,
            RegistrationSuccessPageMapper successPageMapper,
            ErrorMessageResolver messages) {
        return new CompleteRegistrationWebMapper(
                idempotency.completeTtl(), successPageMapper, messages);
    }
}
