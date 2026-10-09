package com.bct.ngtpa.apiservice.config.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyReservationOutcome;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionCommand;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentExecutionResult;
import com.bct.ngtpa.apiservice.application.port.in.ResendLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.NoticeTemplateRegistryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.SharedLoginOtpResendService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginOtpResendSharedRuntimeCompositionTest {
  private final AtomicReference<IdempotentExecutionCommand> captured = new AtomicReference<>();

  @Test
  void composesExactlyOneSharedResendUseCaseAndPropagatesConfiguredTtl() {
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(ResendLoginOtpUseCase.class);
              var useCase = context.getBean(ResendLoginOtpUseCase.class);
              assertThat(useCase).isExactlyInstanceOf(SharedLoginOtpResendService.class);
              StepVerifier.create(
                      useCase.resend(
                          new ResendLoginOtpCommand(
                              "afs-1", "csrf", "idem-key-0001", null, null, "en", "req-1")))
                  .assertNext(
                      result ->
                          assertThat(result.status())
                              .isEqualTo(ResendLoginOtpStatus.DUPLICATE_IN_PROGRESS))
                  .verifyComplete();
              assertThat(captured.get()).isNotNull();
              assertThat(captured.get().ttl()).isEqualTo(Duration.ofSeconds(322));
              assertThat(captured.get().operationId().capability().value()).isEqualTo("login");
              assertThat(captured.get().operationId().action().value()).isEqualTo("otp-resend");
            });
  }

  private ApplicationContextRunner runner() {
    IdempotencyExecutor executor =
        command -> {
          captured.set(command);
          return Mono.just(
              new IdempotentExecutionResult(
                  IdempotencyReservationOutcome.DUPLICATE_IN_PROGRESS, null));
        };
    return new ApplicationContextRunner()
        .withPropertyValues(
            "auth-workflows.login.idempotency.persistence-hmac-secret=dGVzdC1sb2dpbi1wZXJzaXN0ZW5jZS1obWFjLXNlY3JldC0zMi1ieXRlcw==")
        .withBean(
            com.bct.ngtpa.apiservice.application.usecase.auth.login.replay
                .RecoverCommittedLoginByHandoffUseCase.class,
            () ->
                org.mockito.Mockito.mock(
                    com.bct.ngtpa.apiservice.application.usecase.auth.login.replay
                        .RecoverCommittedLoginByHandoffUseCase.class))
        .withUserConfiguration(LoginSharedIdempotencyConfig.class)
        .withBean(
            com.fasterxml.jackson.databind.ObjectMapper.class,
            com.fasterxml.jackson.databind.ObjectMapper::new)
        .withUserConfiguration(LoginOtpResendApplicationConfig.class)
        .withBean(
            AuthWorkflowProperties.class, LoginOtpResendSharedRuntimeCompositionTest::properties)
        .withBean(
            LoginProtectedAuthFlowContextLoader.class,
            () -> mock(LoginProtectedAuthFlowContextLoader.class))
        .withBean(OtpChallengePort.class, () -> mock(OtpChallengePort.class))
        .withBean(MessageDeliveryPort.class, () -> mock(MessageDeliveryPort.class))
        .withBean(LoginOtpSessionBindingPort.class, () -> mock(LoginOtpSessionBindingPort.class))
        .withBean(NoticeTemplateRegistryPort.class, () -> mock(NoticeTemplateRegistryPort.class))
        .withBean(IdempotencyExecutor.class, () -> executor)
        .withBean(
            Clock.class, () -> Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC));
  }

  private static AuthWorkflowProperties properties() {
    var all = new AuthWorkflowProperties();
    var login = new AuthWorkflowProperties.Login();
    login.getCredential().setUsernameMinLength(4);
    login.getCredential().setUsernameMaxLength(128);
    login.getCredential().setUsernameAllowedPattern("^[A-Za-z0-9._-]+$");
    login.getCredential().setPasswordMinLength(8);
    login.getCredential().setPasswordMaxLength(128);
    login.getCredential().setPasswordTrim(false);
    login.getCredential().setPasswordCaseSensitive(true);
    login.getOtp().setLength(6);
    login.getOtp().setTtlSeconds(300);
    login.getOtp().setResendCooldownSeconds(60);
    login.getOtp().setMaxVerifyAttempts(5);
    login.getIdempotency().setStartTtlSeconds(300);
    login.getIdempotency().setOtpSendTtlSeconds(321);
    login.getIdempotency().setOtpResendTtlSeconds(322);
    login.getIdempotency().setOtpVerifyTtlSeconds(323);
    login.getSession().setAbsoluteTimeoutSeconds(1800);
    login.getSession().setIdleTimeoutSeconds(900);
    login.getSession().setTerminalRetentionSeconds(300);
    all.setLogin(login);
    return all;
  }
}
