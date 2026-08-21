package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class AuthFlowSessionCsrfService implements CreateAuthFlowSessionCsrfUseCase {
  private static final Logger LOG = LoggerFactory.getLogger(AuthFlowSessionCsrfService.class);
  private final AuthFlowSessionPort authFlowSessionPort;
  private final CsrfTokenGenerator csrfTokenGenerator;
  private final CsrfTokenValidationService csrfTokenValidationService;
  private final CsrfTokenHasher tokenBindingHasher;
  private final RevisionAwareAuthFlowSessionWriter revisionWriter;

  public AuthFlowSessionCsrfService(AuthFlowSessionPort authFlowSessionPort) {
    this(authFlowSessionPort, new SecureRandomCsrfTokenGenerator(), new Sha256CsrfTokenHasher());
  }

  public AuthFlowSessionCsrfService(
      AuthFlowSessionPort authFlowSessionPort,
      CsrfTokenGenerator csrfTokenGenerator,
      CsrfTokenHasher csrfTokenHasher) {
    this.authFlowSessionPort =
        Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
    this.csrfTokenGenerator =
        Objects.requireNonNull(csrfTokenGenerator, "csrfTokenGenerator must not be null");
    this.tokenBindingHasher =
        Objects.requireNonNull(csrfTokenHasher, "csrfTokenHasher must not be null");
    this.csrfTokenValidationService = new CsrfTokenValidationService(tokenBindingHasher);
    this.revisionWriter = new RevisionAwareAuthFlowSessionWriter(authFlowSessionPort);
  }

  @Override
  public Mono<CreateAuthFlowSessionCsrfResult> createForSession(
      CreateAuthFlowSessionCsrfCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return bindToken(command.authFlowSessionId(), command.now(), command.ttlSeconds());
  }

  public Mono<CreateAuthFlowSessionCsrfResult> rotateForSession(
      RotateAuthFlowSessionCsrfCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return bindToken(command.authFlowSessionId(), command.now(), command.ttlSeconds());
  }

  public Mono<ValidateAuthFlowSessionCsrfResult> validateForSession(
      ValidateAuthFlowSessionCsrfCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return authFlowSessionPort
        .load(command.authFlowSessionId())
        .map(
            optional -> {
              if (optional.isEmpty()) {
                return result(AuthFlowSessionCsrfValidationStatus.SESSION_MISSING);
              }
              AuthFlowSession session = optional.orElseThrow();
              Objects.requireNonNull(session, "session must not be null");
              Objects.requireNonNull(command, "command must not be null");
              if (!session.authFlowSessionId().equals(command.authFlowSessionId())) {
                return result(AuthFlowSessionCsrfValidationStatus.SESSION_MISSING);
              }
              if (session.isExpired(command.now())) {
                return result(AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED);
              }
              if (session.isTerminal()) {
                return result(AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE);
              }
              CsrfTokenValidationResult token =
                  csrfTokenValidationService.validate(
                      command.submittedToken(), session.csrfTokenHash());
              LOG.info(
                  "event=auth.flow.csrf.validation sessionFingerprint={} submittedFingerprint={} "
                      + "storedHashFingerprint={} scenario={} flowStatus={} updatedAt={} result={}",
                  fingerprint(session.authFlowSessionId().value()),
                  fingerprint(command.submittedToken()),
                  fingerprint(session.csrfTokenHash()),
                  session.scenario(),
                  session.status(),
                  session.updatedAt(),
                  token.status());
              return result(
                  switch (token.status()) {
                    case VALID -> AuthFlowSessionCsrfValidationStatus.VALID;
                    case TOKEN_MISSING -> AuthFlowSessionCsrfValidationStatus.TOKEN_MISSING;
                    case TOKEN_HASH_MISSING ->
                        AuthFlowSessionCsrfValidationStatus.TOKEN_HASH_MISSING;
                    case TOKEN_MISMATCH -> AuthFlowSessionCsrfValidationStatus.TOKEN_MISMATCH;
                  });
            });
  }

  private Mono<CreateAuthFlowSessionCsrfResult> bindToken(
      AuthFlowSessionId authFlowSessionId, Instant now, int ttlSeconds) {
    return authFlowSessionPort
        .load(authFlowSessionId)
        .flatMap(
            optional ->
                optional
                    .map(Mono::just)
                    .orElseGet(
                        () ->
                            Mono.error(
                                new IllegalStateException("Auth flow session not available"))))
        .flatMap(
            session -> {
              if (session.isExpired(now) || session.isTerminal()) {
                return Mono.error(new IllegalStateException("Auth flow session is not usable"));
              }
              String rawToken = csrfTokenGenerator.generate();
              String tokenHash = tokenBindingHasher.hash(rawToken);
              AuthFlowSession updated =
                  new AuthFlowSession(
                      session.authFlowSessionId(),
                      session.scenario(),
                      session.status(),
                      tokenHash,
                      session.activeOtpChallengeId(),
                      session.data(),
                      session.createdAt(),
                      now,
                      session.expiresAt());
              Duration remaining = Duration.between(now, session.expiresAt());
              long remainingSeconds = remaining.getSeconds();
              if (remainingSeconds <= 0) {
                return Mono.error(new IllegalStateException("Auth flow session is not usable"));
              }
              int expiresInSeconds =
                  remainingSeconds > Integer.MAX_VALUE
                      ? Integer.MAX_VALUE
                      : Math.toIntExact(remainingSeconds);
              return revisionWriter
                  .updateExpected(session, updated)
                  .thenReturn(
                      new CreateAuthFlowSessionCsrfResult(
                          rawToken, session.expiresAt(), expiresInSeconds));
            });
  }

  private static String fingerprint(String value) {
    if (value == null) {
      return "<null>";
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 8);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static ValidateAuthFlowSessionCsrfResult result(
      AuthFlowSessionCsrfValidationStatus status) {
    return new ValidateAuthFlowSessionCsrfResult(
        status, status == AuthFlowSessionCsrfValidationStatus.VALID);
  }
}
