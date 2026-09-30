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
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataResolutionSource;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadata;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.port.in.GeneralMetadataResolver;
import java.math.BigInteger;
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
  private final GeneralMetadataResolver metadataResolver =
      query ->
          reactor.core.publisher.Mono.just(
              new ResolvedGeneralMetadataSet(
                  query.scope(),
                  GeneralMetadataResolutionSource.POLICY_SPECIFIC,
                  query.requiredKeys().stream()
                      .map(key -> new ResolvedGeneralMetadata(key, "2"))
                      .toList()));

  private FundPriceExportUseCase newUseCase() {
    return new FundPriceExportUseCase(lifecycle, sourcePort, referenceDatePort, metadataResolver);
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
    // CONFIRMED (Nick): export has NO mode. It passes policyNo, fromDate, toDate directly to
    // APIM via the NEW fetchPriceRange port method -- NOT fetchPrice/FetchFundPriceCommand's
    // mode-dependent single-date expansion, which remains query-only. This replaces the
    // deliberately loose assertion from script 101 now that the design is confirmed.
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR).block();

    var captor =
        org.mockito.ArgumentCaptor.forClass(
            com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceRangeCommand.class);
    org.mockito.Mockito.verify(sourcePort).fetchPriceRange(captor.capture());
    assertThat(captor.getValue().policyNo()).isEqualTo("policy1");
    assertThat(captor.getValue().fromDate()).isEqualTo(REFERENCE_DATE.minusMonths(36));
    assertThat(captor.getValue().toDate()).isEqualTo(REFERENCE_DATE);
  }

  @Test
  void exportNeverCallsFetchPriceOnlyFetchPriceRange() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR).block();

    org.mockito.Mockito.verify(sourcePort, org.mockito.Mockito.never()).fetchPrice(any());
  }

  @Test
  void exportNeverValidatesAgainstAvailableFundPriceDatesUnlikeQuery() {
    // Confirms the key behavioral difference: export has no FE date to validate, so a
    // metadata whose availableFundPriceDates does NOT include referenceDate must still succeed.
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2020, 1, 1)), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED))
        .verifyComplete();
  }

  @Test
  void missingSessionMetadataMapsToSystemFailure() {
    stubActiveSessionWithMetadata(null);

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
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
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(noRiskData));

    StepVerifier.create(newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED);
              assertThat(result.exportResult().maxRiskLevel()).isNull();
            })
        .verifyComplete();
  }
  @Test
  void resolvesUnitPricePrecisionViaGeneralMetadataResolver() {
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5");
    stubActiveSessionWithMetadata(metadata);
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase()
                .export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result ->
                assertThat(result.exportResult().unitPricePrecision())
                    .isEqualTo(BigInteger.valueOf(2)))
        .verifyComplete();
  }

  @Test
  void zeroRiskLevelRowsDoNotTriggerMaxRiskLevelEcho() {
    stubActiveSessionWithMetadata(
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, "5"));
    var zeroRisk =
        new RawFundPriceData(
            List.of(
                new RawFundPriceRow(
                    "DUSM", "n", "n", REFERENCE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "0")));
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(zeroRisk));

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(result -> assertThat(result.exportResult().maxRiskLevel()).isNull())
        .verifyComplete();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"0", ""})
  void unassignedCachedMaxRiskLevelIsOmitted(String cachedMax) {
    stubActiveSessionWithMetadata(
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(REFERENCE_DATE), 36, cachedMax));
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(result -> assertThat(result.exportResult().maxRiskLevel()).isNull())
        .verifyComplete();
  }

  @Test
  void exportResultCarriesCachedMode() {
    stubActiveSessionWithMetadata(
        new FundPriceModeSessionMetadata(
            "MONTHLY", "UNIT PRICE", List.of(REFERENCE_DATE), 36, "5"));
    when(sourcePort.fetchPriceRange(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase().export(SESSION_ID, "DB", "policy1", "DBA", ActorUserRole.ROLE_USER_MBR))
        .assertNext(result -> assertThat(result.exportResult().mode()).isEqualTo("MONTHLY"))
        .verifyComplete();
  }
}
