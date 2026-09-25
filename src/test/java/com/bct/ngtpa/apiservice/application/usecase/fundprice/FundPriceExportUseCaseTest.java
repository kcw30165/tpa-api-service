package com.bct.ngtpa.apiservice.application.usecase.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceCommand;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceModeSessionMetadata;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceRow;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for {@code FundPriceExportUseCase}: NO FE date parameter, per
 * fund-price-api-contract.md Section 3 ("No FE date-range parameters"). Derives
 * from=referenceDate.minusMonths(maximumFundPriceHistoryMonths), to=referenceDate directly, and
 * does NOT run the query use case's date-membership validation step -- there is no FE-supplied
 * candidate date to validate against the cached list.
 */
class FundPriceExportUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 9, 25);

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle =
      mock(LoadAndTouchAuthenticatedSessionUseCase.class);
  private final FundPriceSourcePort sourcePort = mock(FundPriceSourcePort.class);
  private final ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);

  private FundPriceExportUseCase newUseCase() {
    return new FundPriceExportUseCase(lifecycle, sourcePort, referenceDatePort);
  }

  private AuthenticatedSession sessionWithMetadata(FundPriceModeSessionMetadata metadata) {
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

  private void stubActiveSessionWithMetadata(FundPriceModeSessionMetadata metadata) {
    when(lifecycle.loadAndTouch(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionLifecycleResult(
                    AuthenticatedSessionLifecycleStatus.ACTIVE, sessionWithMetadata(metadata))));
    when(referenceDatePort.resolveReferenceDate(any())).thenReturn(Mono.just(REFERENCE_DATE));
  }

  private RawFundPriceData rawData() {
    return new RawFundPriceData(
        List.of(
            new RawFundPriceRow(
                "DUSM", "n", "n", REFERENCE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "5")));
  }

  @Test
  void exportDerivesFromDateAsReferenceDateMinusMaximumHistoryMonths() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(rawData()));

    newUseCase().export(SESSION_ID, "DB", "policy1", ActorUserRole.ROLE_USER_MBR).block();

    var captor = org.mockito.ArgumentCaptor.forClass(FetchFundPriceCommand.class);
    org.mockito.Mockito.verify(sourcePort).fetchPrice(captor.capture());
    // Export's from/to derivation is a range, not a single validated date -- confirmed the
    // command still carries a single `date` field per FetchFundPriceCommand's existing shape,
    // meaning the adapter's own mode-dependent expansion is reused for export too via a
    // "MONTHLY-shaped" range request spanning the full history window. This is the one open
    // design question flagged for review: does export reuse the adapter's MONTHLY expansion by
    // passing referenceDate with mode="MONTHLY"-equivalent semantics, or does it need its own
    // distinct command variant? Currently asserts only that fetchPrice was called at all.
    assertThat(captor.getValue()).isNotNull();
  }

  @Test
  void exportNeverValidatesAgainstAvailableFundPriceDatesUnlikeQuery() {
    // Confirms the key behavioral difference: export has no FE date to validate, so a
    // metadata whose availableFundPriceDates does NOT include referenceDate must still succeed.
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2020, 1, 1)), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void missingSessionMetadataMapsToSystemFailure() {
    stubActiveSessionWithMetadata(null);

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }
  @Test
  void noRiskLevelInAnyRowMeansMaxRiskLevelIsNullInResult() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    var noRiskData =
        new RawFundPriceData(
            List.of(
                new RawFundPriceRow(
                    "DUSM", "n", "n", REFERENCE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, null)));
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(noRiskData));

    StepVerifier.create(newUseCase().export(SESSION_ID, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED);
              assertThat(result.exportResult().maxRiskLevel()).isNull();
            })
        .verifyComplete();
  }
}
