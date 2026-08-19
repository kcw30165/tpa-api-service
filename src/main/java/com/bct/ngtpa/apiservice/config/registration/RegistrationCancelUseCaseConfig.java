package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.port.in.CancelRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.cancel.CancelRegistrationService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationCancelUseCaseConfig {
  @Bean
  @ConditionalOnMissingBean(CancelRegistrationUseCase.class)
  CancelRegistrationUseCase cancelRegistrationUseCase(
      AuthFlowSessionPort sessions,
      AuthFlowSessionCsrfService csrfService,
      OtpChallengePort otpChallenges,
      AuthFlowSessionCancellationPort cancellationPort,
      Clock clock) {
    return new CancelRegistrationService(
        sessions, csrfService, otpChallenges, cancellationPort, clock);
  }
}
