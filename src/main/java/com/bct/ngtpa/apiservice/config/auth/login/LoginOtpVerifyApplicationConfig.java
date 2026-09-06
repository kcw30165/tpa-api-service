package com.bct.ngtpa.apiservice.config.auth.login;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.login.mapper.LoginOtpVerifyWebMapper;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.RedisLoginIdempotencyKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.handoff.RedisAuthenticatedLoginHandoffAdapter;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify.LoginOtpVerifyRetentionProperties;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.login.otp.verify.RedisLoginOtpVerifyPersistenceAdapter;
import com.bct.ngtpa.apiservice.adapter.out.session.login.LoginOtpVerifiedSessionAdapter;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevisionGenerator;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotencyExecutor;
import com.bct.ngtpa.apiservice.application.port.in.EstablishAuthenticatedLoginSessionUseCase;
import com.bct.ngtpa.apiservice.application.port.in.VerifyLoginOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthenticatedLoginHandoffPort;
import com.bct.ngtpa.apiservice.application.service.auth.login.idempotency.LoginIdempotencyPersistenceIdDeriver;
import com.bct.ngtpa.apiservice.application.service.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.service.auth.otp.OtpCodeHasher;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.account.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginOtpVerifyExecutionBridge;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginFingerprintFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.LoginIdempotencyIdentityFactory;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.verify.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.RecoverCommittedLoginUseCase;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@EnableConfigurationProperties(LoginOtpVerifyRetentionProperties.class)
public class LoginOtpVerifyApplicationConfig {
  @Bean
  LoginOtpVerifyWebMapper loginOtpVerifyWebMapper() {
    return new LoginOtpVerifyWebMapper();
  }

  @Bean
  LoginOtpVerifiedSessionPort loginOtpVerifiedSessionPort(AuthFlowSessionLifecycleService l) {
    return new LoginOtpVerifiedSessionAdapter(l);
  }

  @Bean
  AuthenticatedLoginHandoffPort authenticatedLoginHandoffPort(
      RedisLoginIdempotencyKeyFactory keyFactory,
      LoginIdempotencyPersistenceIdDeriver derivation,
      ReactiveStringRedisTemplate redis,
      ObjectMapper objectMapper) {
    return new RedisAuthenticatedLoginHandoffAdapter(keyFactory, derivation, redis, objectMapper);
  }

  @Bean
  LoginOtpVerifyPersistencePort loginOtpVerifyPersistencePort(
      OtpChallengeStatePort challenges,
      OtpCodeHasher otpCodeHasher,
      RedisLoginIdempotencyKeyFactory keyFactory,
      LoginIdempotencyPersistenceIdDeriver derivation,
      ReactiveStringRedisTemplate redis) {
    return new RedisLoginOtpVerifyPersistenceAdapter(
        challenges, otpCodeHasher, keyFactory, derivation, redis);
  }

  @Bean
  VerifyLoginOtpUseCase verifyLoginOtpUseCase(
      LoginProtectedAuthFlowContextLoader l,
      LoginOtpVerifyPersistencePort persistence,
      AuthFlowSessionMutationLockPort mutationLock,
      AuthFlowSessionPort sessions,
      SessionRevisionGenerator revisions,
      LoginOtpVerifiedSessionPort v,
      AuthFlowSessionCancellationPort c,
      GetLoginAccountsUseCase g,
      LoginAccountRecordValidationService validation,
      LoginAccountReferenceAssignmentService assignment,
      EstablishAuthenticatedLoginSessionUseCase transition,
      Clock clock,
      RecoverCommittedLoginUseCase committedReplay,
      IdempotencyExecutor executor,
      LoginOtpVerifyExecutionBridge responses,
      LoginIdempotencyIdentityFactory identities,
      LoginFingerprintFactory fingerprints,
      AuthWorkflowProperties workflows) {
    workflows.validateLoginPolicy();
    var protectedOperation =
        new LoginOtpVerifyService(
            l,
            persistence,
            mutationLock,
            sessions,
            revisions,
            new SecureLoginOtpVerifyReferenceGenerator(),
            new LoginOtpVerifyRequestHasher(),
            Duration.ofSeconds(30),
            v,
            c,
            g,
            validation,
            assignment,
            transition,
            clock,
            committedReplay);
    return new SharedLoginOtpVerifyService(
        executor,
        protectedOperation,
        responses,
        identities,
        fingerprints,
        Duration.ofSeconds(workflows.getLogin().getIdempotency().getOtpVerifyTtlSeconds()));
  }
}
