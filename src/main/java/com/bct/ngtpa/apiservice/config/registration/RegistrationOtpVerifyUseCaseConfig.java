package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.VerifyRegistrationOtpService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistrationOtpVerifyUseCaseConfig {

  @Bean
  @ConditionalOnMissingBean(VerifyRegistrationOtpUseCase.class)
  public VerifyRegistrationOtpUseCase verifyRegistrationOtpUseCase(
      RegistrationProtectedAuthFlowContextLoader protectedContextLoader,
      RegistrationRequestHashGenerator requestHashGenerator,
      RegistrationIdempotencyPort registrationIdempotencyPort,
      RegistrationOtpPort registrationOtpPort,
      RegistrationSessionPort registrationSessionPort,
      RegistrationLookupPort registrationLookupPort,
      Clock clock) {
    return new VerifyRegistrationOtpService(
        protectedContextLoader,
        requestHashGenerator,
        registrationIdempotencyPort,
        registrationOtpPort,
        registrationSessionPort,
        registrationLookupPort,
        clock);
  }
}
