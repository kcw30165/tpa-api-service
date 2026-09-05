package com.bct.ngtpa.apiservice.config.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency.SharedCompleteRegistrationService;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationSharedIdempotencyIdentityFactory;
import java.lang.reflect.Proxy;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RegistrationCompleteInterfaceProxyCompositionTest {

  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(
              RegistrationCompleteUseCaseConfig.class,
              RegistrationCompleteInterfaceProxyCompositionTest.Collaborators.class,
              RegistrationCompleteInterfaceProxyCompositionTest.AopInfrastructure.class);

  @Test
  void activeAopInfrastructureExposesCompleteUseCaseThroughInterfaceProxy() {
    context.run(
        application -> {
          assertThat(application).hasNotFailed();
          assertThat(application).hasBean("completeRegistrationUseCase");
          assertThat(application).hasBean("scopedTarget.completeRegistrationUseCase");
          CompleteRegistrationUseCase useCase =
              application.getBean("completeRegistrationUseCase", CompleteRegistrationUseCase.class);
          assertThat(Proxy.isProxyClass(useCase.getClass())).isTrue();
          assertThat(useCase).isInstanceOf(CompleteRegistrationUseCase.class);
          assertThat(useCase).isNotInstanceOf(SharedCompleteRegistrationService.class);
        });
  }

  @Test
  void finalApplicationServiceAndInterfaceBeanBoundaryRemainExplicit() {
    assertThat(java.lang.reflect.Modifier.isFinal(SharedCompleteRegistrationService.class.getModifiers()))
        .isTrue();
    assertThat(CompleteRegistrationUseCase.class.isAssignableFrom(SharedCompleteRegistrationService.class))
        .isTrue();
  }

  @Configuration(proxyBeanMethods = false)
  static class AopInfrastructure {
    @Bean
    static DefaultAdvisorAutoProxyCreator autoProxyCreator() {
      var creator = new DefaultAdvisorAutoProxyCreator();
      creator.setProxyTargetClass(true);
      return creator;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Collaborators {
    @Bean RegistrationProtectedAuthFlowContextLoader contextLoader() { return mock(RegistrationProtectedAuthFlowContextLoader.class); }
    @Bean AuthFlowSessionPort sessions() { return mock(AuthFlowSessionPort.class); }
    @Bean RegistrationUsernameCheckProtectorPort usernameProtector() { return mock(RegistrationUsernameCheckProtectorPort.class); }
    @Bean AuthWorkflowPolicyPort policy() { return mock(AuthWorkflowPolicyPort.class); }
    @Bean IdempotencyExecutor executor() { return mock(IdempotencyExecutor.class); }
    @Bean RegistrationSharedIdempotencyIdentityFactory identities() { return new RegistrationSharedIdempotencyIdentityFactory(); }
    @Bean RegistrationSharedFingerprintFactory fingerprints() { return new RegistrationSharedFingerprintFactory(); }
    @Bean AuthFlowSessionCompletionLockPort locks() { return mock(AuthFlowSessionCompletionLockPort.class); }
    @Bean AuthFlowSessionExpiryRefreshPort expiry() { return mock(AuthFlowSessionExpiryRefreshPort.class); }
    @Bean RegistrationCompletionPort completion() { return mock(RegistrationCompletionPort.class); }
    @Bean RegistrationLookupPort lookup() { return mock(RegistrationLookupPort.class); }
    @Bean AuthFlowSessionCancellationPort cancellation() { return mock(AuthFlowSessionCancellationPort.class); }
    @Bean AuthFlowSessionProperties properties() {
      var properties = new AuthFlowSessionProperties();
      properties.getCompletionLock().setLeaseSeconds(30);
      return properties;
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
  }
}
