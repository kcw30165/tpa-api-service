package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.VerifyRegistrationOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationEligibleTrustPersistencePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifyProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifyReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.RegistrationOtpVerifySharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.otp.idempotency.SharedVerifyRegistrationOtpService;
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
      IdempotencyExecutor idempotencyExecutor,
      RegistrationOtpPort registrationOtpPort,
      RegistrationSessionPort registrationSessionPort,
      RegistrationLookupPort registrationLookupPort,
      RegistrationEligibleTrustPersistencePort eligibleTrustPersistencePort,
      Clock clock) {
    var operation =
        new RegistrationOtpVerifyProtectedOperation(
            registrationOtpPort,
            registrationSessionPort,
            registrationLookupPort,
            eligibleTrustPersistencePort,
            clock);
    var responses =
        new RegistrationOtpVerifySharedExecutionBridge(new RegistrationOtpVerifyReplayCodec());
    return new SharedVerifyRegistrationOtpService(
        protectedContextLoader,
        idempotencyExecutor,
        operation,
        responses,
        new RegistrationSharedIdempotencyIdentityFactory(),
        new RegistrationSharedFingerprintFactory());
  }
}
