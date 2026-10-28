package com.bct.ngtpa.apiservice.application.usecase.passwordvalidation;

import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordCheckResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.*;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;

public final class PasswordValidationGrantService {
  public enum Status {
    ISSUED,
    CONSUMED,
    REJECTED,
    SESSION_NOT_USABLE,
    UNAVAILABLE
  }

  public record IssueResult(Status status, String token, Instant expiresAt) {
    @Override
    public String toString() {
      return "IssueResult[status=" + status + ", grant=<redacted>]";
    }
  }

  public record ConsumeResult(Status status, PayloadDigest binding) {
    @Override
    public String toString() {
      return "ConsumeResult[status=" + status + ", binding=<redacted>]";
    }
  }

  private static final IssueResult ISSUE_REJECTED = new IssueResult(Status.REJECTED, null, null);
  private static final IssueResult ISSUE_UNAVAILABLE =
      new IssueResult(Status.UNAVAILABLE, null, null);
  private static final IssueResult ISSUE_SESSION_NOT_USABLE =
      new IssueResult(Status.SESSION_NOT_USABLE, null, null);
  private static final ConsumeResult CONSUME_REJECTED = new ConsumeResult(Status.REJECTED, null);
  private static final ConsumeResult CONSUME_UNAVAILABLE =
      new ConsumeResult(Status.UNAVAILABLE, null);
  private static final int CANONICALIZER_VERSION = 1;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final SecureRandom random = new SecureRandom();

  public PasswordValidationGrantService(AuthenticatedSessionConcurrencyService concurrency) {
    this.concurrency = Objects.requireNonNull(concurrency);
  }

  public Mono<IssueResult> issue(
      AuthenticatedSessionId sessionId,
      AuthenticatedAccountRef account,
      PasswordValidationPurpose purpose,
      PayloadDigest binding,
      PasswordCheckResult passwordCheck,
      String correlationId) {
    return Mono.defer(
            () -> {
              if (sessionId == null
                  || account == null
                  || purpose == null
                  || binding == null
                  || binding.version() != CANONICALIZER_VERSION
                  || passwordCheck != PasswordCheckResult.CORRECT) {
                return Mono.just(ISSUE_REJECTED);
              }
              byte[] entropy = new byte[32];
              random.nextBytes(entropy);
              String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
              String digest = tokenDigest(token);
              String key = scopeKey(account, purpose);
              return concurrency
                  .updateIfUsableGuarded(
                      sessionId,
                      (session, now) -> session.findAccount(account).isPresent(),
                      (session, now) -> {
                        Instant issuedAt = Instant.ofEpochMilli(now.toEpochMilli());
                        return session.withFeatureState(
                            key,
                            new PasswordValidationGrant(
                                    digest, binding, issuedAt, issuedAt.plusSeconds(300))
                                .encode());
                      },
                      correlationId)
                  .map(
                      result ->
                          switch (result.outcome()) {
                            case APPLIED ->
                                new IssueResult(
                                    Status.ISSUED,
                                    token,
                                    Objects.requireNonNull(
                                            PasswordValidationGrant.decode(
                                                result.session().featureState().get(key)))
                                        .expiresAt());
                            case UNCHANGED -> ISSUE_REJECTED;
                            case SESSION_NOT_FOUND, SESSION_NOT_USABLE -> ISSUE_SESSION_NOT_USABLE;
                            default -> ISSUE_UNAVAILABLE;
                          });
            })
        .defaultIfEmpty(ISSUE_UNAVAILABLE)
        .onErrorReturn(ISSUE_UNAVAILABLE);
  }

  public Mono<ConsumeResult> consume(
      AuthenticatedSessionId sessionId,
      AuthenticatedAccountRef account,
      PasswordValidationPurpose purpose,
      String token,
      String correlationId) {
    return Mono.defer(
            () -> {
              if (sessionId == null
                  || account == null
                  || purpose == null
                  || token == null
                  || !token.matches("[A-Za-z0-9_-]{43}")) {
                return Mono.just(CONSUME_REJECTED);
              }
              String key = scopeKey(account, purpose);
              String digest = tokenDigest(token);
              AtomicReference<PayloadDigest> consumed = new AtomicReference<>();
              return concurrency
                  .updateIfUsableGuarded(
                      sessionId,
                      (session, now) -> {
                        if (session.findAccount(account).isEmpty()) return false;
                        String encoded = session.featureState().get(key);
                        var grant = PasswordValidationGrant.decode(encoded);
                        if (grant == null && encoded != null && !encoded.equals("consumed")) {
                          throw new IllegalStateException(
                              "Password-validation grant state unavailable");
                        }
                        return grant != null
                            && grant.binding().version() == CANONICALIZER_VERSION
                            && !now.isBefore(grant.issuedAt())
                            && now.isBefore(grant.expiresAt())
                            && MessageDigest.isEqual(
                                digest.getBytes(StandardCharsets.US_ASCII),
                                grant.tokenDigest().getBytes(StandardCharsets.US_ASCII));
                      },
                      (session, now) -> {
                        consumed.set(
                            Objects.requireNonNull(
                                    PasswordValidationGrant.decode(session.featureState().get(key)))
                                .binding());
                        return session.withFeatureState(key, "consumed");
                      },
                      correlationId)
                  .map(
                      result ->
                          switch (result.outcome()) {
                            case APPLIED -> new ConsumeResult(Status.CONSUMED, consumed.get());
                            case UNCHANGED -> CONSUME_REJECTED;
                            default -> CONSUME_UNAVAILABLE;
                          });
            })
        .defaultIfEmpty(CONSUME_UNAVAILABLE)
        .onErrorReturn(CONSUME_UNAVAILABLE);
  }

  private static String scopeKey(
      AuthenticatedAccountRef account, PasswordValidationPurpose purpose) {
    return "password-validation:" + account.value() + ":" + purpose.name();
  }

  private static String tokenDigest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Password-validation digest unavailable");
    }
  }
}
