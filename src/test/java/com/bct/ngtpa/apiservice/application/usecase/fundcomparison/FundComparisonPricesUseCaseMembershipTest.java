package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData.RawFundComparisonOption;
import com.bct.ngtpa.apiservice.application.dto.fundprice.RawFundPriceData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundComparisonRetrievalException;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonOptionsSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.FundComparisonPriceSourcePort;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fund Comparison M5.4: prices requests are validated against the session-cached options with a
 * single refresh-and-recheck (docs/design/13-fund-comparison/09-options-session-cache-change.md
 * sections 9.4 and 9.8). Uses the real options use case with a counting source port and a mocked
 * cache service.
 */
class FundComparisonPricesUseCaseMembershipTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final Optional<AuthenticatedSessionId> SESSION = Optional.of(SID);
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);
  private static final String ALL_RANGES = "10D,1M,3M,6M,1Y,YTD,3Y";

  private final AtomicInteger optionsCalls = new AtomicInteger();
  private final AtomicInteger priceCalls = new AtomicInteger();
  private final AtomicReference<FetchFundComparisonPricesCommand> priceCommand =
      new AtomicReference<>();
  private final FundComparisonOptionsCacheService cache =
      mock(FundComparisonOptionsCacheService.class);
  private Mono<RawFundComparisonOptionsData> freshOptions =
      Mono.just(options(ALL_RANGES, "FAEU", "FBAU"));

  @BeforeEach
  void cacheWritesComplete() {
    when(cache.write(any(), any(), any())).thenReturn(Mono.empty());
  }

  private static RawFundComparisonOptionsData options(String rawTimeRange, String... codes) {
    return new RawFundComparisonOptionsData(
        rawTimeRange,
        Arrays.stream(codes)
            .map(code -> new RawFundComparisonOption(code, code + " Fund", ""))
            .toList());
  }

  private FundComparisonPricesUseCase useCase() {
    FundComparisonOptionsSourcePort optionsPort =
        optionsCommand -> {
          optionsCalls.incrementAndGet();
          return freshOptions;
        };
    FundComparisonPriceSourcePort pricePort =
        portCommand -> {
          priceCalls.incrementAndGet();
          priceCommand.set(portCommand);
          return Mono.just(new RawFundPriceData(List.of()));
        };
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    return new FundComparisonPricesUseCase(
        pricePort, referenceDatePort, new FundComparisonOptionsUseCase(optionsPort, cache), cache);
  }

  private static FetchFundComparisonPricesUseCaseCommand command(
      List<String> fundCodes, String timeRange) {
    return new FetchFundComparisonPricesUseCaseCommand(
        POLICY_NO, "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, timeRange);
  }

  private void cached(String rawTimeRange, String... codes) {
    when(cache.read(any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new FundComparisonOptionsCacheEntry(
                        ACCOUNT_REF, POLICY_NO, rawTimeRange, List.of(codes)))));
  }

  private void nothingCached() {
    when(cache.read(any(), any(), any(), any())).thenReturn(Mono.just(Optional.empty()));
  }

  private Mono<FundComparisonPricesResult> run(FetchFundComparisonPricesUseCaseCommand command) {
    return useCase().getPrices(command, SESSION, ACCOUNT_REF, "req-1");
  }

  @Test
  void aMatchingCacheHitFetchesPricesWithoutCallingOptions() {
    cached(ALL_RANGES, "FAEU", "FBAU");

    StepVerifier.create(run(command(List.of("FAEU"), "10D"))).expectNextCount(1).verifyComplete();

    assertThat(optionsCalls).hasValue(0);
    assertThat(priceCalls).hasValue(1);
    assertThat(priceCommand.get().fundList()).isEqualTo("FAEU");
    verify(cache).read(SID, ACCOUNT_REF, POLICY_NO, "req-1");
    verify(cache, never()).write(any(), any(), any());
  }

  @Test
  void aCacheMissFetchesOptionsOnceStoresThemAndFetchesPrices() {
    nothingCached();

    StepVerifier.create(run(command(List.of("FBAU"), "1M"))).expectNextCount(1).verifyComplete();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(1);
    verify(cache).write(eq(SID), any(FundComparisonOptionsCacheEntry.class), eq("req-1"));
  }

  @Test
  void withoutASessionTheCacheIsNotUsedAndFreshOptionsAreChecked() {
    var useCase = useCase();

    StepVerifier.create(
            useCase.getPrices(
                command(List.of("FAEU"), "10D"), Optional.empty(), ACCOUNT_REF, "req-1"))
        .expectNextCount(1)
        .verifyComplete();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(1);
    verify(cache, never()).read(any(), any(), any(), any());
    verify(cache, never()).write(any(), any(), any());
  }

  @Test
  void aStaleCacheIsRefreshedOnceAndThenThePricesAreFetched() {
    cached(ALL_RANGES, "FAEU");

    StepVerifier.create(run(command(List.of("FBAU"), "10D"))).expectNextCount(1).verifyComplete();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(1);
    assertThat(priceCommand.get().fundList()).isEqualTo("FBAU");
    verify(cache).write(eq(SID), any(FundComparisonOptionsCacheEntry.class), eq("req-1"));
  }

  @Test
  void aSelectionStillUnmatchedAfterTheRefreshIsARetrievalFailure() {
    cached(ALL_RANGES, "FAEU");

    StepVerifier.create(run(command(List.of("XXXX"), "10D")))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(0);
  }

  @Test
  void aMissThatDoesNotMatchFreshOptionsDoesNotFetchOptionsAgain() {
    nothingCached();

    StepVerifier.create(run(command(List.of("XXXX"), "10D")))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10Y", "2D", "5M"})
  void anUnofferedTimeRangeIsARetrievalFailureAndNoPricesAreFetched(String timeRange) {
    cached(ALL_RANGES, "FAEU");

    StepVerifier.create(run(command(List.of("FAEU"), timeRange)))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(0);
  }

  @Test
  void anOptionsFailureAfterACacheMissIsARetrievalFailure() {
    nothingCached();
    freshOptions = Mono.error(new IllegalStateException("downstream down"));

    StepVerifier.create(run(command(List.of("FAEU"), "10D")))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    assertThat(priceCalls).hasValue(0);
  }

  @Test
  void anOptionsFailureDuringTheRecheckIsARetrievalFailure() {
    cached(ALL_RANGES, "FAEU");
    freshOptions = Mono.error(new IllegalStateException("downstream down"));

    StepVerifier.create(run(command(List.of("FBAU"), "10D")))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(0);
  }

  @Test
  void caseAndWhitespaceDifferencesUseTheOfferedSpellingDownstreamAndInTheResult() {
    cached(ALL_RANGES, "FAEU", "FBAU");

    StepVerifier.create(run(command(List.of("faeu"), "10d")))
        .assertNext(result -> assertThat(result.fundCodes()).containsExactly("FAEU"))
        .verifyComplete();

    assertThat(priceCommand.get().fundList()).isEqualTo("FAEU");
    assertThat(priceCommand.get().fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(optionsCalls).hasValue(0);
  }

  @Test
  void aCachedEntryWithoutValidTimeRangesIsRefreshed() {
    cached("bogus", "FAEU");

    StepVerifier.create(run(command(List.of("FAEU"), "10D"))).expectNextCount(1).verifyComplete();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(1);
  }

  @Test
  void requiresTheCommandAndTheSessionOptional() {
    var useCase = useCase();
    var command = command(List.of("FAEU"), "10D");

    assertThatThrownBy(() -> useCase.getPrices(null, SESSION, ACCOUNT_REF, "r"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("command");
    assertThatThrownBy(() -> useCase.getPrices(command, null, ACCOUNT_REF, "r"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("sessionId");
  }

  @Test
  void aStaleCacheRefreshSucceedsEvenWhenTheRefreshedCacheWriteFails() {
    // 09 section 9.8: "Refresh-and-recheck succeeds even when the refreshed cache write fails."
    cached(ALL_RANGES, "FAEU");
    when(cache.write(any(), any(), any()))
        .thenReturn(Mono.error(new IllegalStateException("simulated cache write failure")));

    StepVerifier.create(run(command(List.of("FBAU"), "10D"))).expectNextCount(1).verifyComplete();

    assertThat(optionsCalls).hasValue(1);
    assertThat(priceCalls).hasValue(1);
  }
}
