package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code AuthenticatedSession} does not yet carry a {@code fundPriceModeMetadata}
 * field.
 *
 * <p>Deliberately NULLABLE, confirmed by script 073's investigation and Nick's explicit
 * confirmation: per fund-price-session-contract.md's "Compatibility" clause, a session exists from
 * login onward, long before any Fund Price interaction -- metadata is present only after the first
 * successful mode fetch and absent for every other session's entire lifetime. Unlike {@code
 * revision} (always stamped, never null), this field must tolerate null both at construction and
 * round-trip through the protector.
 *
 * <p>This field is confirmed (script 073) to require a home on the domain record itself: the
 * protector's private SensitiveContext has no independent storage and cannot carry a value that has
 * no AuthenticatedSession counterpart to read from or write into.
 */
class AuthenticatedSessionFundPriceModeMetadataFieldTest {

  private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

  private AuthenticatedSession session(FundPriceModeSessionMetadata metadata) {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)),
        metadata);
  }

  @Test
  void nullFundPriceModeMetadataIsAcceptedAndNotDefaulted() {
    // Confirms the nullable design decision directly: a fresh login session, which has never
    // called the Fund Price mode endpoint, must construct successfully with a null value here.
    var built = session(null);
    assertThat(built.fundPriceModeMetadata()).isNull();
  }

  @Test
  void presentFundPriceModeMetadataIsCarriedVerbatim() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 9, 18)), 36, "5");
    var built = session(metadata);
    assertThat(built.fundPriceModeMetadata()).isEqualTo(metadata);
  }

  @Test
  void toStringDoesNotLeakFundPriceModeMetadataContents() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 9, 18)), 36, "5");
    var built = session(metadata);
    assertThat(built.toString()).doesNotContain("BIDOFFER PRICE").doesNotContain("18/09/2026");
  }
}
