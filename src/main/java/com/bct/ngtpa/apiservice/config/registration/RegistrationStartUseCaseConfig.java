package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.StartRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.CaptchaChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCsrPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.registration.RegistrationStartValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.SharedStartRegistrationService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RegistrationStartUseCaseConfig {
  @Bean
  @ConditionalOnMissingBean
  RegistrationStartValidator registrationStartValidator() {
    return new RegistrationStartValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  RegistrationCaptchaGateService registrationCaptchaGateService(
      CaptchaChallengePort captchaChallengePort, Clock clock) {
    return new RegistrationCaptchaGateService(captchaChallengePort, clock);
  }

  @Bean
  @ConditionalOnMissingBean(RegistrationStartProtectedOperation.class)
  RegistrationStartProtectedOperation registrationStartProtectedOperation(
      RegistrationStartValidator validator,
      RegistrationLookupPort lookup,
      RegistrationSessionPort sessions,
      AuthFlowSessionPort authFlowSessions,
      RegistrationCsrPort csrf,
      RegistrationOtpPort otp,
      RegistrationMessagePort messages,
      AuthWorkflowPolicyPort policy,
      RegistrationCaptchaGateService captcha,
      Clock clock) {
    return new RegistrationStartProtectedOperation(
        validator, lookup, sessions, authFlowSessions, csrf, otp, messages, policy, captcha, clock);
  }

  @Bean
  @ConditionalOnMissingBean(RegistrationStartSharedExecutionBridge.class)
  RegistrationStartSharedExecutionBridge registrationStartSharedExecutionBridge(
      RegistrationStartProtectedReplayPort protectedReplay) {
    return new RegistrationStartSharedExecutionBridge(
        new RegistrationStartReplayCodec(), protectedReplay);
  }

  @Bean
  @ConditionalOnMissingBean(StartRegistrationUseCase.class)
  StartRegistrationUseCase startRegistrationUseCase(
      IdempotencyExecutor executor,
      RegistrationStartProtectedOperation operation,
      RegistrationStartSharedExecutionBridge responses) {
    return new SharedStartRegistrationService(
        executor,
        operation,
        responses,
        new RegistrationSharedIdempotencyIdentityFactory(),
        new RegistrationSharedFingerprintFactory());
  }
}
