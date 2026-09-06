package com.bct.ngtpa.apiservice.config.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.idempotency.*;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoginStartSharedRuntimeCompositionTest {
  private final AtomicReference<IdempotentExecutionCommand> captured = new AtomicReference<>();

  @Test
  void contextCreatesExactlyOneSharedUseCaseAndConfiguredTtlReachesExecution() {
    runner()
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(StartLoginUseCase.class);
              assertThat(context.getBeanNamesForType(StartLoginUseCase.class))
                  .containsExactly("startLoginUseCase");
              StartLoginUseCase useCase = context.getBean(StartLoginUseCase.class);
              assertThat(useCase).isInstanceOf(SharedStartLoginService.class);
              StepVerifier.create(
                      useCase.start(
                          new StartLoginCommand(
                              " Member01 ", "ExactPass", "en", "req-1", "idem-key")))
                  .assertNext(
                      result ->
                          assertThat(result.status())
                              .isEqualTo(StartLoginStatus.DUPLICATE_IN_PROGRESS))
                  .verifyComplete();
              assertThat(captured.get()).isNotNull();
              assertThat(captured.get().ttl()).isEqualTo(Duration.ofSeconds(300));
              assertThat(captured.get().operationId().capability().value()).isEqualTo("login");
              assertThat(captured.get().operationId().action().value()).isEqualTo("credentials");
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
        .withUserConfiguration(LoginSharedIdempotencyConfig.class)
        .withBean(
            com.fasterxml.jackson.databind.ObjectMapper.class,
            com.fasterxml.jackson.databind.ObjectMapper::new)
        .withUserConfiguration(LoginApplicationConfig.class)
        .withBean(
            AuthWorkflowProperties.class,
            LoginStartSharedRuntimeCompositionTest::workflowProperties)
        .withBean(AuthFlowSessionProperties.class, AuthFlowSessionProperties::new)
        .withBean(LoginCredentialPolicyService.class, () -> policy())
        .withBean(LoginPreAuthContextFactory.class, () -> mock(LoginPreAuthContextFactory.class))
        .withBean(
            CredentialAuthenticationPort.class, () -> mock(CredentialAuthenticationPort.class))
        .withBean(
            LoginPreAuthContextProtectorPort.class,
            () -> mock(LoginPreAuthContextProtectorPort.class))
        .withBean(
            AuthFlowSessionLifecycleService.class,
            () -> mock(AuthFlowSessionLifecycleService.class))
        .withBean(
            CreateAuthFlowSessionCsrfUseCase.class,
            () -> mock(CreateAuthFlowSessionCsrfUseCase.class))
        .withBean(LoginStartReplayPort.class, () -> mock(LoginStartReplayPort.class))
        .withBean(
            LoginStartReplayProtectorPort.class, () -> mock(LoginStartReplayProtectorPort.class))
        .withBean(
            LoginStartReplayCommitRefGenerator.class,
            () -> mock(LoginStartReplayCommitRefGenerator.class))
        .withBean(IdempotencyExecutor.class, () -> executor)
        .withBean(
            Clock.class, () -> Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC));
  }

  private static LoginCredentialPolicyService policy() {
    return new LoginCredentialPolicyService(
        true,
        LoginUsernameNormalization.ASCII_LOWERCASE,
        4,
        128,
        "^[A-Za-z0-9._-]+$",
        false,
        true,
        8,
        128);
  }

  private static AuthWorkflowProperties workflowProperties() {
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
    login.getOtp().setSmsTemplate("code={code}");
    login.getOtp().getEmailTemplate().setSubject("Login code");
    login.getOtp().getEmailTemplate().setBody("code={code}");
    login.getIdempotency().setStartTtlSeconds(300);
    login.getIdempotency().setOtpSendTtlSeconds(300);
    login.getIdempotency().setOtpResendTtlSeconds(300);
    login.getIdempotency().setOtpVerifyTtlSeconds(300);
    login.getSession().setAbsoluteTimeoutSeconds(1800);
    login.getSession().setIdleTimeoutSeconds(900);
    login.getSession().setTerminalRetentionSeconds(300);
    login.getAccount().setIdentifierWidth(12);
    all.setLogin(login);
    return all;
  }
}
