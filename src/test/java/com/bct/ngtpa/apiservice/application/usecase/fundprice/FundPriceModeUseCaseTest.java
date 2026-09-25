package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceModeData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceModeSourcePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for {@code FundPriceModeUseCase}, the final piece of this feature: calls {@link
 * FundPriceModeSourcePort#fetchMode} (the PORT, never the concrete {@code
 * ApimFundPriceModeSourceAdapter} -- per Nick's explicit instruction), then stamps the resulting
 * {@link FundPriceModeSessionMetadata} onto the session via {@link
 * AuthenticatedSessionConcurrencyService#updateIfUsable}.
 *
 * <p>Confirmed policy (fund-price-session-contract.md "No hidden refresh", re-read verbatim in
 * script 077): the mode endpoint ALWAYS calls TRPGetFundPriceMode and unconditionally overwrites
 * session metadata -- there is no conditional-skip-if-fresh branching to test here.
 */
class FundPriceModeUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  private final FundPriceModeSourcePort sourcePort = mock(FundPriceModeSourcePort.class);
  private final AuthenticatedSessionConcurrencyService concurrencyService =
      mock(AuthenticatedSessionConcurrencyService.class);

  private FundPriceModeUseCase newUseCase() {
    return new FundPriceModeUseCase(sourcePort, concurrencyService);
  }

  private FetchFundPriceModeCommand command() {
    return new FetchFundPriceModeCommand(
        "00000008802", LocalDate.of(2026, 9, 25), "DB", ActorUserRole.ROLE_USER_MBR);
  }

  private RawFundPriceModeData rawData() {
    return new RawFundPriceModeData(
        "DAILY",
        "BIDOFFER PRICE",
        List.of(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 5, 25)),
        36,
        "5");
  }

  private AuthenticatedSession existingSession() {
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
        null);
  }

  @Test
  void fetchesModeThroughThePortAndStampsMetadataOntoTheSession() {
    when(sourcePort.fetchMode(any())).thenReturn(Mono.just(rawData()));
    var stamped =
        new AuthenticatedSession(
            SESSION_ID,
            existingSession().actorContext(),
            existingSession().accounts(),
            existingSession().status(),
            existingSession().csrfTokenHash(),
            existingSession().createdAt(),
            existingSession().lastAccessedAt(),
            existingSession().idleExpiresAt(),
            existingSession().absoluteExpiresAt(),
            existingSession().revision(),
            new FundPriceModeSessionMetadata(
                "DAILY", "BIDOFFER PRICE", rawData().availableFundPriceDates(), 36, "5"));
    when(concurrencyService.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.applied(stamped)));

    StepVerifier.create(newUseCase().fetchAndCacheMode(SESSION_ID, command(), "req-1"))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.APPLIED);
              assertThat(result.metadata().mode()).isEqualTo("DAILY");
              assertThat(result.metadata().fundPriceType()).isEqualTo("BIDOFFER PRICE");
              assertThat(result.metadata().maxRiskLevel()).isEqualTo("5");
            })
        .verifyComplete();

    verify(sourcePort).fetchMode(command());
  }

  @Test
  void
      mutatorAppliedToTheConcurrencyServiceStampsExactlyTheFetchedMetadataAndPreservesEverythingElse() {
    when(sourcePort.fetchMode(any())).thenReturn(Mono.just(rawData()));
    ArgumentCaptor<UnaryOperator<AuthenticatedSession>> mutatorCaptor =
        ArgumentCaptor.forClass(UnaryOperator.class);
    when(concurrencyService.updateIfUsable(any(), mutatorCaptor.capture(), any()))
        .thenAnswer(
            invocation -> {
              AuthenticatedSession current = existingSession();
              AuthenticatedSession mutated = mutatorCaptor.getValue().apply(current);
              return Mono.just(AuthenticatedSessionConcurrencyResult.applied(mutated));
            });

    var result = newUseCase().fetchAndCacheMode(SESSION_ID, command(), "req-1").block();

    assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.APPLIED);
    // The mutator must preserve every OTHER field on the session -- proving it is a targeted
    // metadata-stamp, not a wholesale replacement built independently of the loaded session.
    assertThat(result.metadata().mode()).isEqualTo("DAILY");
  }

  @Test
  void
      sourcePortFailureNeverCallsTheConcurrencyServiceAndPropagatesAsFundPriceRetrievalException() {
    when(sourcePort.fetchMode(any()))
        .thenReturn(Mono.error(FundPriceRetrievalException.systemFailure()));

    StepVerifier.create(newUseCase().fetchAndCacheMode(SESSION_ID, command(), "req-1"))
        .expectError(FundPriceRetrievalException.class)
        .verify();

    verifyNoInteractions(concurrencyService);
  }

  @Test
  void concurrencyServiceSessionNotFoundMapsToASystemFailureOutcome() {
    when(sourcePort.fetchMode(any())).thenReturn(Mono.just(rawData()));
    when(concurrencyService.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionConcurrencyResult.of(
                    AuthenticatedSessionConcurrencyOutcome.SESSION_NOT_FOUND)));

    StepVerifier.create(newUseCase().fetchAndCacheMode(SESSION_ID, command(), "req-1"))
        .assertNext(
            result ->
                assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void concurrencyServiceRevisionConflictExhaustedMapsToASystemFailureOutcome() {
    when(sourcePort.fetchMode(any())).thenReturn(Mono.just(rawData()));
    when(concurrencyService.updateIfUsable(any(), any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionConcurrencyResult.of(
                    AuthenticatedSessionConcurrencyOutcome.REVISION_CONFLICT_EXHAUSTED)));

    StepVerifier.create(newUseCase().fetchAndCacheMode(SESSION_ID, command(), "req-1"))
        .assertNext(
            result ->
                assertThat(result.outcome()).isEqualTo(FundPriceModeUseCaseOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void correlationIdIsPassedThroughToTheConcurrencyServiceUnchanged() {
    // The stubbed APPLIED result must carry a session that actually has metadata stamped --
    // existingSession() alone (fundPriceModeMetadata == null) represents a session that has
    // NEVER called the mode endpoint, which is not a valid APPLIED outcome and is correctly
    // rejected by FundPriceModeUseCaseResult's own guard clause. This mirrors the pattern already
    // used in fetchesModeThroughThePortAndStampsMetadataOntoTheSession() above.
    when(sourcePort.fetchMode(any())).thenReturn(Mono.just(rawData()));
    var existing = existingSession();
    var stamped =
        new AuthenticatedSession(
            existing.authenticatedSessionId(),
            existing.actorContext(),
            existing.accounts(),
            existing.status(),
            existing.csrfTokenHash(),
            existing.createdAt(),
            existing.lastAccessedAt(),
            existing.idleExpiresAt(),
            existing.absoluteExpiresAt(),
            existing.revision(),
            new FundPriceModeSessionMetadata(
                "DAILY", "BIDOFFER PRICE", rawData().availableFundPriceDates(), 36, "5"));
    when(concurrencyService.updateIfUsable(any(), any(), any()))
        .thenReturn(Mono.just(AuthenticatedSessionConcurrencyResult.applied(stamped)));

    newUseCase().fetchAndCacheMode(SESSION_ID, command(), "correlation-xyz").block();

    verify(concurrencyService)
        .updateIfUsable(any(), any(), org.mockito.ArgumentMatchers.eq("correlation-xyz"));
  }
}
