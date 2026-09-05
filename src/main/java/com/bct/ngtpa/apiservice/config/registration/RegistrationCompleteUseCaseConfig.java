package com.bct.ngtpa.apiservice.config.registration;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.CompleteRegistrationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationUsernameCheckProtectorPort;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationPasswordValidator;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.RegistrationUsernameCheckContextLoader;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.RegistrationCompleteProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.RegistrationCompleteReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.RegistrationCompleteSharedExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.SharedCompleteRegistrationService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration
public class RegistrationCompleteUseCaseConfig {
  @Bean
  @ConditionalOnMissingBean(RegistrationPasswordValidator.class)
  RegistrationPasswordValidator registrationPasswordValidator(AuthWorkflowPolicyPort policy) {
    return new RegistrationPasswordValidator(policy);
  }

  @Bean
  @ConditionalOnMissingBean(RegistrationUsernameCheckContextLoader.class)
  RegistrationUsernameCheckContextLoader registrationUsernameCheckContextLoader(
      RegistrationUsernameCheckProtectorPort protector) {
    return new RegistrationUsernameCheckContextLoader(protector);
  }

  @Bean
  @Scope(proxyMode = ScopedProxyMode.INTERFACES)
  @ConditionalOnMissingBean(CompleteRegistrationUseCase.class)
  CompleteRegistrationUseCase completeRegistrationUseCase(
      RegistrationProtectedAuthFlowContextLoader contextLoader,
      AuthFlowSessionPort sessions,
      RegistrationUsernameCheckContextLoader usernameLoader,
      RegistrationPasswordValidator passwordValidator,
      IdempotencyExecutor idempotencyExecutor,
      RegistrationSharedIdempotencyIdentityFactory identityFactory,
      RegistrationSharedFingerprintFactory fingerprintFactory,
      AuthFlowSessionCompletionLockPort locks,
      AuthFlowSessionExpiryRefreshPort expiry,
      RegistrationCompletionPort completion,
      RegistrationLookupPort lookup,
      AuthFlowSessionCancellationPort cancellation,
      AuthFlowSessionProperties properties,
      Clock clock) {
    Duration lease = Duration.ofSeconds(properties.getCompletionLock().getLeaseSeconds());
    var protectedOperation =
        new RegistrationCompleteProtectedOperation(
            sessions, locks, completion, lookup, cancellation, lease, clock);
    var replayCodec = new RegistrationCompleteReplayCodec();
    var responseBridge = new RegistrationCompleteSharedExecutionBridge(replayCodec);
    return new SharedCompleteRegistrationService(
        contextLoader,
        expiry,
        usernameLoader,
        passwordValidator,
        idempotencyExecutor,
        protectedOperation,
        responseBridge,
        identityFactory,
        fingerprintFactory,
        clock);
  }
}
