package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginCredentialIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginPreAuthContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginCredentialIdempotencyCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginCredentialRequestHasher;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginCredentialPolicyService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginPreAuthContextFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartReplayCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.StartLoginService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Outer-layer composition for login application services. */
@Configuration
public class LoginApplicationConfig {

  @Bean
  LoginCredentialRequestHasher loginCredentialRequestHasher() {
    return new LoginCredentialRequestHasher();
  }

  @Bean
  LoginCredentialIdempotencyCoordinator loginCredentialIdempotencyCoordinator(
      LoginCredentialIdempotencyPort port, Clock clock, AuthWorkflowProperties properties) {
    properties.validateLoginPolicy();
    return new LoginCredentialIdempotencyCoordinator(
        port,
        clock,
        Duration.ofSeconds(properties.getLogin().getIdempotency().getStartTtlSeconds()));
  }

  @Bean
  LoginStartReplayCoordinator loginStartReplayCoordinator(
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      LoginStartReplayCommitRefGenerator refs,
      LoginCredentialIdempotencyCoordinator idempotency,
      Clock clock) {
    return new LoginStartReplayCoordinator(replayPort, protector, refs, idempotency, clock);
  }

  @Bean
  StartLoginUseCase startLoginUseCase(
      LoginCredentialPolicyService policy,
      LoginCredentialRequestHasher hasher,
      LoginCredentialIdempotencyCoordinator idempotency,
      CredentialAuthenticationPort credentials,
      LoginPreAuthContextFactory contexts,
      LoginPreAuthContextProtectorPort protector,
      AuthFlowSessionLifecycleService sessions,
      CreateAuthFlowSessionCsrfUseCase csrf,
      LoginStartReplayCoordinator replayCoordinator,
      Clock clock,
      AuthWorkflowProperties workflows,
      AuthFlowSessionProperties sessionProperties) {
    workflows.validateLoginPolicy();
    var loginScenario = sessionProperties.getScenarios().getLogin();
    return new StartLoginService(
        policy,
        hasher,
        idempotency,
        credentials,
        contexts,
        protector,
        sessions,
        csrf,
        replayCoordinator,
        clock,
        loginScenario.getTtlSeconds(),
        loginScenario.isRequired(),
        sessionProperties.getSessionId().getPrefix(),
        sessionProperties.getSessionId().getEntropyBytes());
  }
}
