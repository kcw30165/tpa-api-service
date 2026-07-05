package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.mapper.RegistrationOtpVerifyWebMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationOtpVerifyWebConfig {

    @Bean
    @ConditionalOnMissingBean
    public RegistrationOtpVerifyWebMapper registrationOtpVerifyWebMapper() {
        return new RegistrationOtpVerifyWebMapper(null);
    }
}
