package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for the SIMPLIFIED {@code FundPriceRequestSupport.loadUsableSession(...)} shape:
 * returns {@code Mono<Optional<FundPriceModeSessionMetadata>>} directly, replacing the previously
 * redundant {@code SessionCheckResult}/{@code UsableSession}/{@code SessionCheckOutcome} wrapper
 * types, which merely re-wrapped data {@link AuthenticatedSessionLifecycleResult} and {@link
 * FundPriceModeSessionMetadata} already carry adequately (confirmed, Nick: the redundancy was the
 * extra wrapper types, not the session-load call itself -- {@code FundPriceModeUseCase}'s own
 * established pattern confirms use cases loading their own session via a directly-supplied {@link
 * AuthenticatedSessionId} is correct, script 111's evidence).
 */
class FundPriceRequestSupportTest {

  private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle =
      mock(LoadAndTouchAuthenticatedSessionUseCase.class);

  private FundPriceRequestSupport newSupport() {
    return new FundPriceRequestSupport(lifecycle);
  }

  private AuthenticatedSession session(FundPriceModeSessionMetadata metadata) {
    return new AuthenticatedSession(
        SESSION_ID,
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

  private FundPriceModeSessionMetadata validMetadata() {
    return new FundPriceModeSessionMetadata(
        "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2026, 5, 5)), 36, "5");
  }

  @Test
  void activeSessionWithMetadataReturnsPresentOptional() {
    when(lifecycle.loadAndTouch(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionLifecycleResult(
                    AuthenticatedSessionLifecycleStatus.ACTIVE, session(validMetadata()))));

    StepVerifier.create(newSupport().loadUsableSession(SESSION_ID))
        .assertNext(result -> assertThat(result).contains(validMetadata()))
        .verifyComplete();
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionLifecycleStatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = "ACTIVE")
  void inactiveSessionReturnsEmptyOptional(AuthenticatedSessionLifecycleStatus status) {
    when(lifecycle.loadAndTouch(any()))
        .thenReturn(Mono.just(new AuthenticatedSessionLifecycleResult(status, null)));

    StepVerifier.create(newSupport().loadUsableSession(SESSION_ID))
        .assertNext(result -> assertThat(result).isEqualTo(Optional.empty()))
        .verifyComplete();
  }

  @Test
  void activeSessionWithNullMetadataReturnsEmptyOptional() {
    when(lifecycle.loadAndTouch(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionLifecycleResult(
                    AuthenticatedSessionLifecycleStatus.ACTIVE, session(null))));

    StepVerifier.create(newSupport().loadUsableSession(SESSION_ID))
        .assertNext(result -> assertThat(result).isEqualTo(Optional.empty()))
        .verifyComplete();
  }

  @Test
  void dateValidationBehaviorIsUnchanged() {
    var metadata = validMetadata();
    var referenceDate = LocalDate.of(2026, 9, 25);

    assertThat(newSupport().validateDate(metadata, referenceDate, LocalDate.of(2026, 5, 5)))
        .isEqualTo(FundPriceRequestSupport.DateCheckOutcome.VALID);
    assertThat(newSupport().validateDate(metadata, referenceDate, LocalDate.of(2020, 1, 1)))
        .isEqualTo(FundPriceRequestSupport.DateCheckOutcome.INVALID);
  }
}
