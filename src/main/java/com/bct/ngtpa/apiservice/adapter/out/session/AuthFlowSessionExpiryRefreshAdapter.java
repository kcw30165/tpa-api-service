package com.bct.ngtpa.apiservice.adapter.out.session;

import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class AuthFlowSessionExpiryRefreshAdapter implements AuthFlowSessionExpiryRefreshPort {
  private final AuthFlowSessionPort sessions;
  private final AuthFlowSessionProperties properties;

  public AuthFlowSessionExpiryRefreshAdapter(
      AuthFlowSessionPort sessions, AuthFlowSessionProperties properties) {
    this.sessions = Objects.requireNonNull(sessions);
    this.properties = Objects.requireNonNull(properties);
  }

  @Override
  public Mono<AuthFlowSession> refresh(AuthFlowSessionId sessionId, Instant interactionAt) {
    return refresh(sessionId, interactionAt, null, false);
  }

  @Override
  public Mono<AuthFlowSession> refreshUsernameVerified(
      AuthFlowSessionId sessionId, Instant interactionAt) {
    return refresh(sessionId, interactionAt, AuthFlowStatus.USERNAME_VERIFIED, true);
  }

  private Mono<AuthFlowSession> refresh(
      AuthFlowSessionId sessionId,
      Instant interactionAt,
      AuthFlowStatus requiredStatus,
      boolean requireRevisionCas) {
    Objects.requireNonNull(sessionId, "sessionId must not be null");
    Objects.requireNonNull(interactionAt, "interactionAt must not be null");

    return sessions
        .load(sessionId)
        .flatMap(
            optionalSession -> {
              if (optionalSession.isEmpty()) {
                return Mono.<AuthFlowSession>error(
                    new IllegalStateException("Auth flow session not found"));
              }

              AuthFlowSession session = optionalSession.orElseThrow();
              if (session.isTerminal() || session.isExpired(interactionAt)) {
                return Mono.<AuthFlowSession>error(
                    new IllegalStateException("Auth flow session is not usable"));
              }
              if (requiredStatus != null && session.status() != requiredStatus) {
                return Mono.<AuthFlowSession>error(
                    new IllegalStateException("Auth flow session status is not refreshable"));
              }

              int ttlSeconds = ttl(session.scenario());
              AuthFlowSession updated =
                  new AuthFlowSession(
                      session.authFlowSessionId(),
                      session.scenario(),
                      session.status(),
                      session.csrfTokenHash(),
                      session.activeOtpChallengeId(),
                      session.data(),
                      session.createdAt(),
                      interactionAt,
                      interactionAt.plusSeconds(ttlSeconds));
              var writer =
                  new com.bct.ngtpa.apiservice.application.usecase.session.authflow
                      .RevisionAwareAuthFlowSessionWriter(sessions);
              return requireRevisionCas
                  ? writer.updateExpectedStrict(session, updated)
                  : writer.updateExpected(session, updated);
            });
  }

  private int ttl(AuthFlowScenario scenario) {
    AuthFlowSessionProperties.Scenario p =
        switch (scenario) {
          case REGISTRATION -> properties.getScenarios().getRegistration();
          case LOGIN -> properties.getScenarios().getLogin();
          case FORGOT_PASSWORD -> properties.getScenarios().getForgotPassword();
          case USERNAME_RECOVERY -> properties.getScenarios().getUsernameRecovery();
        };
    if (p.getTtlSeconds() <= 0)
      throw new IllegalStateException("Auth flow session TTL must be positive");
    return p.getTtlSeconds();
  }
}
