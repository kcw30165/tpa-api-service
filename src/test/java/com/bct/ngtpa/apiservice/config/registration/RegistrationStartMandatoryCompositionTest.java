package com.bct.ngtpa.apiservice.config.registration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.RegistrationStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.registration.idempotency.SharedStartRegistrationService;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RegistrationStartMandatoryCompositionTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(RegistrationStartUseCaseConfig.class)
          .withBean(
              IdempotencyExecutor.class, () -> org.mockito.Mockito.mock(IdempotencyExecutor.class))
          .withBean(
              RegistrationLookupPort.class,
              () -> org.mockito.Mockito.mock(RegistrationLookupPort.class))
          .withBean(
              RegistrationSessionPort.class,
              () -> org.mockito.Mockito.mock(RegistrationSessionPort.class))
          .withBean(
              AuthFlowSessionPort.class, () -> org.mockito.Mockito.mock(AuthFlowSessionPort.class))
          .withBean(
              RegistrationCsrPort.class, () -> org.mockito.Mockito.mock(RegistrationCsrPort.class))
          .withBean(
              RegistrationOtpPort.class, () -> org.mockito.Mockito.mock(RegistrationOtpPort.class))
          .withBean(
              RegistrationMessagePort.class,
              () -> org.mockito.Mockito.mock(RegistrationMessagePort.class))
          .withBean(
              AuthWorkflowPolicyPort.class,
              () -> org.mockito.Mockito.mock(AuthWorkflowPolicyPort.class))
          .withBean(
              CaptchaChallengePort.class,
              () -> org.mockito.Mockito.mock(CaptchaChallengePort.class))
          .withBean(
              RegistrationStartProtectedReplayPort.class,
              () -> org.mockito.Mockito.mock(RegistrationStartProtectedReplayPort.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withBean(MaskingService.class, () -> new MaskingService(new MaskingProperties()));

  @Test
  void composesExactlyOneSharedStartUseCaseWhenLegacyEnableFlagIsFalse() {
    context
        .withPropertyValues("shared-idempotency.enabled=false")
        .run(
            application -> {
              assertThat(application).hasSingleBean(StartRegistrationUseCase.class);
              assertThat(application.getBean(StartRegistrationUseCase.class))
                  .isExactlyInstanceOf(SharedStartRegistrationService.class);
            });
  }
}
