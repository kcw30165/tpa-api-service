package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import java.time.Instant;
import java.util.*;

/**
 * The {@code revision} field is opaque concurrency metadata (lifecycle-tier, not sensitive), used
 * exclusively by {@link
 * com.bct.ngtpa.apiservice.application.port.out.AuthenticatedSessionCasUpdatePort}. It is never
 * persisted inside the encrypted payload -- the infrastructure-layer session protector deliberately
 * excludes it from the sensitive encrypted context, mirroring how {@code status} and the lifecycle
 * timestamps are already excluded from encryption today. This class intentionally does not name the
 * concrete protector implementation, which lives outside the application layer.
 *
 * <p>{@code fundPriceModeMetadata} is deliberately NULLABLE: a session exists from login onward,
 * long before any Fund Price interaction, and metadata is present only after the first successful
 * call to the mode endpoint, per fund-price-session-contract.md's "Compatibility" clause. Unlike
 * {@code revision}, it IS sensitive data and is persisted inside the encrypted payload.
 */
public record AuthenticatedSession(
    AuthenticatedSessionId authenticatedSessionId,
    ActorContext actorContext,
    List<AuthenticatedSessionAccount> accounts,
    AuthenticatedSessionStatus status,
    String csrfTokenHash,
    Instant createdAt,
    Instant lastAccessedAt,
    Instant idleExpiresAt,
    Instant absoluteExpiresAt,
    SessionRevision revision,
    FundPriceModeSessionMetadata fundPriceModeMetadata,
    Map<String, String> featureState) {
  public AuthenticatedSession {
    Objects.requireNonNull(authenticatedSessionId);
    Objects.requireNonNull(actorContext);
    accounts = accounts == null ? List.of() : List.copyOf(accounts);
    Objects.requireNonNull(status);
    Objects.requireNonNull(createdAt);
    Objects.requireNonNull(lastAccessedAt);
    Objects.requireNonNull(idleExpiresAt);
    Objects.requireNonNull(absoluteExpiresAt);
    Objects.requireNonNull(revision, "revision must not be null");
    featureState = featureState == null ? Map.of() : Map.copyOf(featureState);
    if (!createdAt.equals(lastAccessedAt) && createdAt.isAfter(lastAccessedAt))
      throw new IllegalArgumentException("lastAccessedAt precedes createdAt");
    if (idleExpiresAt.isBefore(lastAccessedAt) || absoluteExpiresAt.isBefore(createdAt))
      throw new IllegalArgumentException("Invalid authenticated-session expiry");
    Set<String> refs = new HashSet<>();
    for (var a : accounts)
      if (!refs.add(a.accountRef().value()))
        throw new IllegalArgumentException("Duplicate account reference");
  }

  /**
   * Pre-featureState signature, kept so existing callers compile; starts with no feature state.
   * Production code that rebuilds an existing session must use the canonical constructor and pass
   * {@code featureState()} through (Fund Comparison M5.1).
   */
  public AuthenticatedSession(
      AuthenticatedSessionId authenticatedSessionId,
      ActorContext actorContext,
      List<AuthenticatedSessionAccount> accounts,
      AuthenticatedSessionStatus status,
      String csrfTokenHash,
      Instant createdAt,
      Instant lastAccessedAt,
      Instant idleExpiresAt,
      Instant absoluteExpiresAt,
      SessionRevision revision,
      FundPriceModeSessionMetadata fundPriceModeMetadata) {
    this(
        authenticatedSessionId,
        actorContext,
        accounts,
        status,
        csrfTokenHash,
        createdAt,
        lastAccessedAt,
        idleExpiresAt,
        absoluteExpiresAt,
        revision,
        fundPriceModeMetadata,
        Map.of());
  }

  public boolean isUsable(Instant now) {
    Objects.requireNonNull(now);
    return status == AuthenticatedSessionStatus.ACTIVE
        && now.isBefore(idleExpiresAt)
        && now.isBefore(absoluteExpiresAt);
  }

  public Optional<AuthenticatedSessionAccount> findAccount(AuthenticatedAccountRef ref) {
    if (ref == null) return Optional.empty();
    return accounts.stream().filter(a -> a.accountRef().equals(ref)).findFirst();
  }

  @Override
  public String toString() {
    return "AuthenticatedSession[id=<redacted>, actor=<redacted>, accountCount="
        + accounts.size()
        + ", status="
        + status
        + ", csrfTokenHash=<redacted>, createdAt="
        + createdAt
        + ", lastAccessedAt="
        + lastAccessedAt
        + ", idleExpiresAt="
        + idleExpiresAt
        + ", absoluteExpiresAt="
        + absoluteExpiresAt
        + ", revision=<redacted>, fundPriceModeMetadata=<redacted>, featureState=<redacted>]";
  }
}
