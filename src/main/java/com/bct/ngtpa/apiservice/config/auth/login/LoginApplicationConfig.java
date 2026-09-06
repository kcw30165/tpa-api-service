package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginPreAuthContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayCommitRefGenerator;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginStartReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.TransientJsonPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginStartReplayCodec;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginStartExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginCredentialPolicyService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.LoginPreAuthContextFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartProtectedReplayCommitter;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.LoginStartProtectedOperation;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.start.SharedStartLoginService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Outer-layer composition for login application services. */
@Configuration
public class LoginApplicationConfig {

  @Bean
  LoginStartProtectedOperation loginStartProtectedOperation(
      CredentialAuthenticationPort credentials,
      LoginPreAuthContextFactory contexts,
      LoginPreAuthContextProtectorPort protector,
      AuthFlowSessionLifecycleService sessions,
      CreateAuthFlowSessionCsrfUseCase csrf,
      Clock clock,
      AuthFlowSessionProperties sessionProperties) {
    var loginScenario = sessionProperties.getScenarios().getLogin();
    return new LoginStartProtectedOperation(
        credentials,
        contexts,
        protector,
        sessions,
        csrf,
        clock,
        loginScenario.getTtlSeconds(),
        loginScenario.isRequired(),
        sessionProperties.getSessionId().getPrefix(),
        sessionProperties.getSessionId().getEntropyBytes());
  }

  @Bean
  LoginStartProtectedReplayCommitter loginStartProtectedReplayCommitter(
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      LoginStartReplayCommitRefGenerator refs,
      Clock clock) {
    return new LoginStartProtectedReplayCommitter(replayPort, protector, refs, clock);
  }

  @Bean
  LoginStartExecutionBridge loginStartSharedExecutionBridge(
      LoginStartReplayPort replayPort,
      LoginStartReplayProtectorPort protector,
      Clock clock,
      TransientJsonPort json) {
    return new LoginStartExecutionBridge(
        json,
        new LoginStartReplayCodec(
            new LoginResponseEnvelope(json), replayPort, protector, clock));
  }

  @Bean
  StartLoginUseCase startLoginUseCase(
      LoginCredentialPolicyService policy,
      IdempotencyExecutor executor,
      LoginStartProtectedOperation protectedOperation,
      LoginStartProtectedReplayCommitter committer,
      LoginStartExecutionBridge responses,
      AuthWorkflowProperties workflows) {
    workflows.validateLoginPolicy();
    return new SharedStartLoginService(
        policy,
        executor,
        protectedOperation,
        committer,
        responses,
        new LoginIdempotencyIdentityFactory(),
        new LoginFingerprintFactory(),
        Duration.ofSeconds(workflows.getLogin().getIdempotency().getStartTtlSeconds()));
  }
}
