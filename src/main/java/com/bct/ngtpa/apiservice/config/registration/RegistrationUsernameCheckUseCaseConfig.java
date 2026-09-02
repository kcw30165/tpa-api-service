package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.port.in.CheckRegistrationUsernameUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.DownstreamErrorClassificationPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameAvailabilityPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
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
      AuthFlowSessionExpiryRefreshPort expiryRefreshPort,
      RegistrationUsernameAvailabilityPort registrationUsernameAvailabilityPort,
      AuthWorkflowPolicyPort authWorkflowPolicyPort,
      DownstreamErrorClassificationPort downstreamErrorClassificationPort,
      RegistrationUsernameCheckProtectorPort registrationUsernameCheckProtectorPort,
      Clock clock) {
    return new CheckRegistrationUsernameService(
        authFlowSessionPort,
        expiryRefreshPort,
        registrationUsernameAvailabilityPort,
        authWorkflowPolicyPort,
        downstreamErrorClassificationPort,
        registrationUsernameCheckProtectorPort,
        clock);
  }
}
