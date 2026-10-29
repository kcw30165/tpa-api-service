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

  public record ConsumeResult(Status status, PayloadDigest binding, String reason) {
    public ConsumeResult(Status status, PayloadDigest binding) {
      this(status, binding, null);
    }

    @Override
    public String toString() {
      return "ConsumeResult[status=" + status + ", binding=<redacted>, reason=" + reason + "]";
    }
  }

  private static final IssueResult ISSUE_REJECTED = new IssueResult(Status.REJECTED, null, null);
  private static final IssueResult ISSUE_UNAVAILABLE =
      new IssueResult(Status.UNAVAILABLE, null, null);
  private static final IssueResult ISSUE_SESSION_NOT_USABLE =
      new IssueResult(Status.SESSION_NOT_USABLE, null, null);
  private static final int CANONICALIZER_VERSION = 1;
  public static final int DEFAULT_TTL_SECONDS = 300;
  private static final int MIN_TTL_SECONDS = 30;
  private static final int MAX_TTL_SECONDS = 900;
  private final AuthenticatedSessionConcurrencyService concurrency;
  private final int ttlSeconds;
  private final SecureRandom random = new SecureRandom();

  public PasswordValidationGrantService(AuthenticatedSessionConcurrencyService concurrency) {
    this(concurrency, DEFAULT_TTL_SECONDS);
  }

  public PasswordValidationGrantService(
      AuthenticatedSessionConcurrencyService concurrency, int ttlSeconds) {
    this.concurrency = Objects.requireNonNull(concurrency);
    if (ttlSeconds < MIN_TTL_SECONDS || ttlSeconds > MAX_TTL_SECONDS) {
      throw new IllegalArgumentException(
          "password-validation.grant.ttl-seconds must be between 30 and 900");
    }
    this.ttlSeconds = ttlSeconds;
  }

  public int ttlSeconds() {
    return ttlSeconds;
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
                                    digest, binding, issuedAt, issuedAt.plusSeconds(ttlSeconds))
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
              if (sessionId == null || account == null || purpose == null) {
                return Mono.just(rejected("ACCOUNT_NOT_IN_SESSION"));
              }
              if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
                return Mono.just(rejected("TOKEN_MALFORMED"));
              }
              String key = scopeKey(account, purpose);
              String digest = tokenDigest(token);
              AtomicReference<PayloadDigest> consumed = new AtomicReference<>();
              AtomicReference<String> rejectionReason = new AtomicReference<>();
              return concurrency
                  .updateIfUsableGuarded(
                      sessionId,
                      (session, now) -> {
                        rejectionReason.set(null);
                        if (session.findAccount(account).isEmpty()) {
                          rejectionReason.set("ACCOUNT_NOT_IN_SESSION");
                          return false;
                        }
                        String encoded = session.featureState().get(key);
                        if (encoded == null) {
                          rejectionReason.set("NO_GRANT");
                          return false;
                        }
                        if (encoded.equals("consumed")) {
                          rejectionReason.set("ALREADY_CONSUMED");
                          return false;
                        }
                        var grant = PasswordValidationGrant.decode(encoded);
                        if (grant == null) {
                          rejectionReason.set("STATE_UNREADABLE");
                          throw new IllegalStateException(
                              "Password-validation grant state unavailable");
                        }
                        if (grant.binding().version() != CANONICALIZER_VERSION) {
                          rejectionReason.set("VERSION_UNSUPPORTED");
                          return false;
                        }
                        if (now.isBefore(grant.issuedAt())) {
                          rejectionReason.set("NOT_YET_VALID");
                          return false;
                        }
                        if (!now.isBefore(grant.expiresAt())) {
                          rejectionReason.set("EXPIRED");
                          return false;
                        }
                        if (!MessageDigest.isEqual(
                            digest.getBytes(StandardCharsets.US_ASCII),
                            grant.tokenDigest().getBytes(StandardCharsets.US_ASCII))) {
                          rejectionReason.set("TOKEN_MISMATCH");
                          return false;
                        }
                        return true;
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
                            case APPLIED ->
                                new ConsumeResult(Status.CONSUMED, consumed.get(), null);
                            case UNCHANGED ->
                                rejected(
                                    Objects.requireNonNullElse(
                                        rejectionReason.get(), "SYSTEM_FAILURE"));
                            default -> unavailable(result.outcome().name());
                          });
            })
        .defaultIfEmpty(unavailable("SYSTEM_FAILURE"))
        .onErrorResume(
            failure ->
                Mono.just(
                    unavailable(
                        failure instanceof IllegalStateException
                                && "Password-validation grant state unavailable"
                                    .equals(failure.getMessage())
                            ? "STATE_UNREADABLE"
                            : failure.getClass().getSimpleName())));
  }

  private static ConsumeResult rejected(String reason) {
    return new ConsumeResult(Status.REJECTED, null, reason);
  }

  private static ConsumeResult unavailable(String reason) {
    return new ConsumeResult(Status.UNAVAILABLE, null, reason);
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
