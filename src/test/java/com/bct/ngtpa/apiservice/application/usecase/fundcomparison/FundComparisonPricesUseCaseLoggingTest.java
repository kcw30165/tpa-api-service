package com.bct.ngtpa.apiservice.application.usecase.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonOptionsCacheEntry;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Fund Comparison M5.6 (gap closure per docs/design/13-fund-comparison/09-options-session-cache-
 * change.md sections 9.7 and 9.8, found by script 098's investigation): confirms {@link
 * FundComparisonPricesUseCase}'s membership-matching log events actually fire at the documented
 * level with the documented fields. No prior test captured these events. Attaches a Logback {@link
 * ListAppender} directly to the use case's own logger, mirroring {@code
 * FundComparisonOptionsCacheServiceLoggingTest}'s established pattern.
 */
class FundComparisonPricesUseCaseLoggingTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");
  private static final String ACCOUNT_REF = "acc_abcdefghijklmnopqrstuvwx";
  private static final String POLICY_NO = "00000008802";
  private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 10, 15);
  private static final String ALL_RANGES = "10D,1M,3M,6M,1Y,YTD,3Y";

  private final FundComparisonOptionsCacheService cache =
      mock(FundComparisonOptionsCacheService.class);
  private Logger logger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    logger = (Logger) LoggerFactory.getLogger(FundComparisonPricesUseCase.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
    when(cache.write(any(), any(), any())).thenReturn(Mono.empty());
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
  }

  private static RawFundComparisonOptionsData options(String rawTimeRange, String... codes) {
    return new RawFundComparisonOptionsData(
        rawTimeRange,
        Arrays.stream(codes)
            .map(code -> new RawFundComparisonOption(code, code + " Fund", ""))
            .toList());
  }

  private FundComparisonPricesUseCase useCase(FundComparisonOptionsSourcePort optionsPort) {
    FundComparisonPriceSourcePort pricePort =
        portCommand -> Mono.just(new RawFundPriceData(List.of()));
    ReferenceDatePort referenceDatePort = accountEnv -> Mono.just(REFERENCE_DATE);
    return new FundComparisonPricesUseCase(
        pricePort, referenceDatePort, new FundComparisonOptionsUseCase(optionsPort, cache), cache);
  }

  private static FetchFundComparisonPricesUseCaseCommand command(
      List<String> fundCodes, String timeRange) {
    return new FetchFundComparisonPricesUseCaseCommand(
        POLICY_NO, "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, timeRange);
  }

  @Test
  void aStaleCachedEntryLogsStaleRefreshAtWarnWithReasonAndRequestId() {
    when(cache.read(any(), any(), any(), any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new FundComparisonOptionsCacheEntry(
                        ACCOUNT_REF, POLICY_NO, ALL_RANGES, List.of("FAEU")))));
    FundComparisonOptionsSourcePort optionsPort = cmd -> Mono.just(options(ALL_RANGES, "FBAU"));

    StepVerifier.create(
            useCase(optionsPort)
                .getPrices(
                    command(List.of("FBAU"), "10D"), Optional.of(SID), ACCOUNT_REF, "req-log-1"))
        .expectNextCount(1)
        .verifyComplete();

    var warnEvents =
        listAppender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    assertThat(warnEvents).hasSize(1);
    var message = warnEvents.get(0).getFormattedMessage();
    assertThat(message)
        .contains("event=fund-comparison.options-cache.stale-refresh")
        .contains("reason=fund-code-not-offered")
        .contains("requestId=req-log-1");
  }

  @Test
  void aFreshMismatchLogsMembershipRejectedAtErrorWithReasonSourceFreshAndRequestId() {
    when(cache.read(any(), any(), any(), any())).thenReturn(Mono.just(Optional.empty()));
    FundComparisonOptionsSourcePort optionsPort = cmd -> Mono.just(options(ALL_RANGES, "FAEU"));

    StepVerifier.create(
            useCase(optionsPort)
                .getPrices(
                    command(List.of("XXXX"), "10D"), Optional.of(SID), ACCOUNT_REF, "req-log-2"))
        .expectError(FundComparisonRetrievalException.class)
        .verify();

    var errorEvents =
        listAppender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(errorEvents).hasSize(1);
    var message = errorEvents.get(0).getFormattedMessage();
    assertThat(message)
        .contains("event=fund-comparison.membership.rejected")
        .contains("reason=fund-code-not-offered")
        .contains("source=fresh")
        .contains("requestId=req-log-2");
  }
}
