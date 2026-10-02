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
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import com.bct.ngtpa.apiservice.application.port.out.FundPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleResult;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionLifecycleStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.LoadAndTouchAuthenticatedSessionUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fail-first tests for {@code FundPriceQueryUseCase}: loads the session, confirms usable metadata
 * exists, validates the FE-supplied date (range + exact membership in cached
 * availableFundPriceDates), then delegates to {@link FundPriceSourcePort}.
 */
class FundPriceQueryUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");
  private static final AuthenticatedSessionId SESSION_ID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 9, 25);
  private static final LocalDate AVAILABLE_DATE = LocalDate.of(2026, 5, 5);

  private final LoadAndTouchAuthenticatedSessionUseCase lifecycle =
      mock(LoadAndTouchAuthenticatedSessionUseCase.class);
  private final FundPriceSourcePort sourcePort = mock(FundPriceSourcePort.class);
  private final ReferenceDatePort referenceDatePort = mock(ReferenceDatePort.class);

  private FundPriceQueryUseCase newUseCase() {
    return new FundPriceQueryUseCase(lifecycle, sourcePort, referenceDatePort);
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

  private FundPriceModeSessionMetadata validMetadata() {
    return new FundPriceModeSessionMetadata(
        "DAILY", "BIDOFFER PRICE", List.of(AVAILABLE_DATE), 36, "5");
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
                "DUSM", "n", "n", AVAILABLE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "5")));
  }

  // ----- happy path ----------------------------------------------------------

  @Test
  void validDateFetchesPriceAndAssemblesResult() {
    stubActiveSessionWithMetadata(validMetadata());
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED);
              assertThat(result.queryResult().rows()).hasSize(1);
              assertThat(result.queryResult().fundPriceType()).isEqualTo("BIDOFFER PRICE");
              assertThat(result.queryResult().maxRiskLevel()).isEqualTo("5");
            })
        .verifyComplete();
  }

  @Test
  void modeAndDateArePassedThroughToTheSourcePortUnmanipulated() {
    stubActiveSessionWithMetadata(validMetadata());
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(rawData()));

    newUseCase()
        .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR)
        .block();

    var captor = org.mockito.ArgumentCaptor.forClass(FetchFundPriceCommand.class);
    org.mockito.Mockito.verify(sourcePort).fetchPrice(captor.capture());
    assertThat(captor.getValue().date()).isEqualTo(AVAILABLE_DATE);
    assertThat(captor.getValue().mode()).isEqualTo("DAILY");
  }

  // ----- session-usability failures (503) -------------------------------------

  @Test
  void inactiveSessionMapsToSystemFailure() {
    when(lifecycle.loadAndTouch(any()))
        .thenReturn(
            Mono.just(
                new AuthenticatedSessionLifecycleResult(
                    AuthenticatedSessionLifecycleStatus.EXPIRED, null)));

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result ->
                assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  @Test
  void missingSessionMetadataMapsToSystemFailure() {
    stubActiveSessionWithMetadata(null);

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result ->
                assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.SYSTEM_FAILURE))
        .verifyComplete();
  }

  // ----- date validation failures (400) -----------------------------------------

  @Test
  void dateNotInAvailableListMapsToInvalidDate() {
    stubActiveSessionWithMetadata(validMetadata());

    StepVerifier.create(
            newUseCase()
                .query(
                    SESSION_ID,
                    LocalDate.of(2026, 5, 6), // NOT in availableFundPriceDates
                    "DB",
                    "policy1",
                    ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.INVALID_DATE))
        .verifyComplete();
  }

  @Test
  void dateOutsideMaximumHistoryMapsToInvalidDate() {
    // A date that IS in availableFundPriceDates but violates R.minusMonths(M)<=D<=R would be
    // a downstream-contract inconsistency in practice, but the use case must still enforce its
    // own range check independently -- confirms defense-in-depth, not solely trusting the
    // cached list.
    var metadata =
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(LocalDate.of(2020, 1, 1)), 36, "5");
    stubActiveSessionWithMetadata(metadata);

    StepVerifier.create(
            newUseCase()
                .query(
                    SESSION_ID,
                    LocalDate.of(2020, 1, 1),
                    "DB",
                    "policy1",
                    ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.INVALID_DATE))
        .verifyComplete();
  }

  @Test
  void sourcePortNeverCalledWhenDateValidationFails() {
    stubActiveSessionWithMetadata(validMetadata());

    newUseCase()
        .query(SESSION_ID, LocalDate.of(2026, 5, 6), "DB", "policy1", ActorUserRole.ROLE_USER_MBR)
        .block();

    org.mockito.Mockito.verifyNoInteractions(sourcePort);
  }

  @Test
  void dateAfterReferenceDateMapsToInvalidDateEvenIfPresentInAvailableList() {
    // Defense-in-depth: even if a future date somehow appears in the cached available list
    // (a downstream-contract violation in practice), the use case's own
    // candidate.isAfter(referenceDate) check independently rejects it -- exercises that
    // specific branch, distinct from dateOutsideMaximumHistoryMapsToInvalidDate's
    // isBefore(lowerBound) branch.
    var futureDate = REFERENCE_DATE.plusDays(1);
    var metadata =
        new FundPriceModeSessionMetadata("DAILY", "BIDOFFER PRICE", List.of(futureDate), 36, "5");
    stubActiveSessionWithMetadata(metadata);

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, futureDate, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.INVALID_DATE))
        .verifyComplete();
  }

  @Test
  void noRiskLevelInAnyRowMeansMaxRiskLevelIsNullInResult() {
    stubActiveSessionWithMetadata(validMetadata());
    var noRiskData =
        new RawFundPriceData(
            List.of(
                new RawFundPriceRow(
                    "DUSM",
                    "n",
                    "n",
                    AVAILABLE_DATE,
                    "HKD",
                    BigDecimal.ONE,
                    BigDecimal.ONE,
                    null)));
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(noRiskData));

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(
            result -> {
              assertThat(result.outcome()).isEqualTo(FundPriceUseCaseOutcome.APPLIED);
              assertThat(result.queryResult().maxRiskLevel()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void zeroRiskLevelRowsDoNotTriggerMaxRiskLevelEcho() {
    stubActiveSessionWithMetadata(validMetadata());
    var zeroRisk =
        new RawFundPriceData(
            List.of(
                new RawFundPriceRow(
                    "DUSM", "n", "n", AVAILABLE_DATE, "HKD", BigDecimal.ONE, BigDecimal.ONE, "0")));
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(zeroRisk));

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(result -> assertThat(result.queryResult().maxRiskLevel()).isNull())
        .verifyComplete();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"0", ""})
  void unassignedCachedMaxRiskLevelIsOmitted(String cachedMax) {
    stubActiveSessionWithMetadata(
        new FundPriceModeSessionMetadata(
            "DAILY", "BIDOFFER PRICE", List.of(AVAILABLE_DATE), 36, cachedMax));
    when(sourcePort.fetchPrice(any())).thenReturn(Mono.just(rawData()));

    StepVerifier.create(
            newUseCase()
                .query(SESSION_ID, AVAILABLE_DATE, "DB", "policy1", ActorUserRole.ROLE_USER_MBR))
        .assertNext(result -> assertThat(result.queryResult().maxRiskLevel()).isNull())
        .verifyComplete();
  }
}
