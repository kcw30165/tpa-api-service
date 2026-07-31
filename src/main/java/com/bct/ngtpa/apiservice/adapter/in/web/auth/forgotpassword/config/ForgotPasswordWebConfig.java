package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.config;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordStartWebMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForgotPasswordWebConfig {
    @Bean
    ForgotPasswordStartWebMapper forgotPasswordStartWebMapper(
            @Value("${auth-workflows.forgot-password.idempotency.start-ttl-seconds:300}") int startTtlSeconds) {
        return new ForgotPasswordStartWebMapper(Duration.ofSeconds(startTtlSeconds));
    }
}