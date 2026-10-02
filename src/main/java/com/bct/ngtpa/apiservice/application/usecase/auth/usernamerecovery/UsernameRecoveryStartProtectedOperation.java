package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.CaptchaChallengeId;
import com.bct.ngtpa.apiservice.application.dto.auth.captcha.ConsumeCaptchaChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionCreateCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.captcha.RegistrationCaptchaGateService;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenGenerator;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CsrfTokenHasher;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;

/** Idempotency-neutral acquired execution for Username Recovery START. */
public final class UsernameRecoveryStartProtectedOperation {
  private final RegistrationCaptchaGateService captchaGateService;
  private final UsernameRecoveryLookupPort lookupPort;
  private final AuthFlowSessionPort authFlowSessionPort;
  private final UsernameRecoveryAuthFlowContextBinding contextBinding;
  private final CsrfTokenGenerator csrfTokenGenerator;
  private final CsrfTokenHasher csrfTokenHasher;
  private final Clock clock;
  private final int ttlSeconds;
  private final UsernameRecoveryStartSchemeRefGenerator schemeRefGenerator =
      new UsernameRecoveryStartSchemeRefGenerator();
  private final SecureRandom secureRandom = new SecureRandom();

  public UsernameRecoveryStartProtectedOperation(
      RegistrationCaptchaGateService captchaGateService,
      UsernameRecoveryLookupPort lookupPort,
      AuthFlowSessionPort authFlowSessionPort,
      UsernameRecoveryAuthFlowContextBinding contextBinding,
      CsrfTokenGenerator csrfTokenGenerator,
      CsrfTokenHasher csrfTokenHasher,
      Clock clock,
      int ttlSeconds) {
    this.captchaGateService = Objects.requireNonNull(captchaGateService);
    this.lookupPort = Objects.requireNonNull(lookupPort);
    this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort);
    this.contextBinding = Objects.requireNonNull(contextBinding);
    this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator);
    this.csrfTokenHasher = Objects.requireNonNull(csrfTokenHasher);
    this.clock = Objects.requireNonNull(clock);
    if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
    this.ttlSeconds = ttlSeconds;
  }

  public Mono<UsernameRecoveryStartResult> execute(
      UsernameRecoveryStartCommand command, UsernameRecoveryStartValidator.Validation validation) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(validation, "validation must not be null");
    Instant now = clock.instant();
    final CaptchaChallengeId captchaChallengeId;
    try {
      captchaChallengeId = new CaptchaChallengeId(command.captchaChallengeId());
    } catch (IllegalArgumentException exception) {
      return Mono.error(UsernameRecoveryStartException.captchaInvalid());
    }
    return captchaGateService
        .consume(new ConsumeCaptchaChallengeCommand(captchaChallengeId, now))
        .onErrorMap(Exception.class, ProtectedExecutionException::new)
        .flatMap(
            consumeResult ->
                switch (consumeResult.outcome()) {
                  case CONSUMED -> lookup(command, validation, now);
                  case NOT_FOUND -> Mono.error(UsernameRecoveryStartException.captchaNotFound());
                  case NOT_VERIFIED ->
                      Mono.error(UsernameRecoveryStartException.captchaNotVerified());
                  case EXPIRED -> Mono.error(UsernameRecoveryStartException.captchaExpired());
                  case LOCKED, ALREADY_CONSUMED, REFRESHED ->
                      Mono.error(UsernameRecoveryStartException.captchaConflict());
                })
        .onErrorResume(
            ProtectedExecutionException.class,
            error -> Mono.error(UsernameRecoveryStartException.systemFailure(error)));
  }

  private Mono<UsernameRecoveryStartResult> lookup(
      UsernameRecoveryStartCommand command,
      UsernameRecoveryStartValidator.Validation validation,
      Instant now) {
    var lookupCommand =
        new UsernameRecoveryLookupCommand(
            command.identityType(),
            command.identityValue(),
            command.dateOfBirth(),
            validation.email(),
            validation.mobile(),
            ActorUserRole.ROLE_USER_MBR);
    return lookupPort
        .search(lookupCommand)
        .onErrorMap(Exception.class, ProtectedExecutionException::new)
        .flatMap(
            lookupResult ->
                switch (lookupResult.status()) {
                  case MATCHED ->
                      handleMatched(command, validation, lookupResult.candidates(), now);
                  case NOT_FOUND ->
                      Mono.just(
                          new UsernameRecoveryStartResult(
                              UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND,
                              null,
                              null,
                              List.of()));
                  case CONTRACT_INVALID, UNAVAILABLE, UNKNOWN_OUTCOME ->
                      Mono.error(UsernameRecoveryStartException.systemFailure());
                });
  }

  private Mono<UsernameRecoveryStartResult> handleMatched(
      UsernameRecoveryStartCommand command,
      UsernameRecoveryStartValidator.Validation validation,
      List<UsernameRecoveryLookupCandidate> candidates,
      Instant now) {
    if (candidates.stream().noneMatch(UsernameRecoveryLookupCandidate::registered)) {
      List<UsernameRecoverySchemeProjection> projections =
          candidates.stream()
              .map(
                  candidate ->
                      new UsernameRecoverySchemeProjection(
                          null, candidate.accountEnv(), candidate.trustCode(), false))
              .toList();
      return Mono.just(
          new UsernameRecoveryStartResult(
              UsernameRecoveryStartResult.Outcome.ZERO_REGISTERED, null, null, projections));
    }
    return createRevisioned(command, validation, candidates, now);
  }

  private Mono<UsernameRecoveryStartResult> createRevisioned(
      UsernameRecoveryStartCommand command,
      UsernameRecoveryStartValidator.Validation validation,
      List<UsernameRecoveryLookupCandidate> candidates,
      Instant now) {
    String rawToken = csrfTokenGenerator.generate();
    String csrfHash = csrfTokenHasher.hash(rawToken);
    AuthFlowSessionId sessionId = generateAuthFlowSessionId();
    Instant expiresAt = now.plusSeconds(ttlSeconds);
    List<UsernameRecoverySessionCandidate> contextCandidates =
        candidates.stream()
            .map(
                candidate ->
                    new UsernameRecoverySessionCandidate(
                        new UsernameRecoveryCandidate(
                            schemeRefGenerator.generate(),
                            candidate.accountEnv(),
                            candidate.trustCode(),
                            candidate.registered(),
                            candidate.registered() ? candidate.userName() : null,
                            UsernameRecoveryDeliveryState.AVAILABLE,
                            candidate.schemeName(),
                            candidate.schemeNameChi()),
                        null,
                        null))
            .toList();
    var context =
        new UsernameRecoveryAuthFlowContext(
            UsernameRecoveryWorkflowStatus.ACTIVE,
            validation.destination(),
            command.language(),
            contextCandidates);
    var base =
        new AuthFlowSession(
            sessionId,
            AuthFlowScenario.USERNAME_RECOVERY,
            AuthFlowStatus.STARTED,
            csrfHash,
            null,
            AuthFlowSessionData.empty(),
            now,
            now,
            expiresAt);
    var bound = contextBinding.bind(base, context, now);
    var createCommand =
        new AuthFlowSessionCreateCommand(
            sessionId,
            AuthFlowScenario.USERNAME_RECOVERY,
            AuthFlowStatus.STARTED,
            now,
            expiresAt,
            bound.data(),
            csrfHash,
            null);
    return authFlowSessionPort
        .createRevisioned(createCommand)
        .onErrorMap(Exception.class, ProtectedExecutionException::new)
        .map(snapshot -> sessionCreated(snapshot.session(), context, rawToken, now));
  }

  private UsernameRecoveryStartResult sessionCreated(
      AuthFlowSession session,
      UsernameRecoveryAuthFlowContext context,
      String rawToken,
      Instant now) {
    List<UsernameRecoverySchemeProjection> projections =
        context.candidates().stream()
            .map(
                candidate ->
                    new UsernameRecoverySchemeProjection(
                        candidate.schemeRef(),
                        candidate.accountEnv(),
                        candidate.trustCode(),
                        candidate.registered()))
            .toList();
    long remaining = Duration.between(now, session.expiresAt()).toSeconds();
    return new UsernameRecoveryStartResult(
        UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
        session.authFlowSessionId(),
        rawToken,
        (int) Math.max(1, remaining),
        projections);
  }

  private AuthFlowSessionId generateAuthFlowSessionId() {
    byte[] bytes = new byte[18];
    secureRandom.nextBytes(bytes);
    return new AuthFlowSessionId(
        "afs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }

  private static final class ProtectedExecutionException extends RuntimeException {
    private ProtectedExecutionException(Throwable cause) {
      super("Username Recovery START protected execution failed", cause);
    }
  }
}
