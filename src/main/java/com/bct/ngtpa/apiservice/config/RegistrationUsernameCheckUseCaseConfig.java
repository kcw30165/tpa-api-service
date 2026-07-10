package com.bct.ngtpa.apiservice.config;

import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.username.CheckRegistrationUsernameService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationUsernameCheckUseCaseConfig {
    @Bean
    @ConditionalOnMissingBean
    public CheckRegistrationUsernameUseCase checkRegistrationUsernameUseCase(
            AuthFlowSessionPort authFlowSessionPort,
            RegistrationUsernameAvailabilityPort registrationUsernameAvailabilityPort,
            AuthWorkflowPolicyPort authWorkflowPolicyPort,
            DownstreamErrorClassificationPort downstreamErrorClassificationPort) {
        return new CheckRegistrationUsernameService(
                authFlowSessionPort,
                registrationUsernameAvailabilityPort,
                authWorkflowPolicyPort,
                downstreamErrorClassificationPort,
                Clock.systemUTC());
    }
}
