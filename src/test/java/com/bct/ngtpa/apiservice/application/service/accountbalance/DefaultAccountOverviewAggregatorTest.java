package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.overview.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAccountOverviewAggregatorTest {
  private final DefaultAccountOverviewAggregator aggregator = new DefaultAccountOverviewAggregator();

  @Test
  void aggregatesMultipleDisplayAccountsFundsAndSubDisplayAccountsInDeterministicOrder() {
    var normalized = normalized(true, true,
        List.of(source("ZZ", "2", 20), source("AA", "1", 20), source("ZZ", "1", 10)),
        List.of(holding("ZZ", "2", "F1", "10", "4", "1"), holding("ZZ", "1", "F2", "20", "6", "2"), holding("AA", "1", "F3", "30", "8", "3")));
    RawAccountOverviewAggregation result = aggregator.aggregate(normalized);
    assertThat(result.displayAccounts()).extracting(RawDisplayAccountOverview::displayAccountId).containsExactly("ZZ", "AA");
    assertThat(result.displayAccounts().get(0).displaySequence()).isEqualTo(10);
    assertThat(result.displayAccounts().get(0).accountBalance()).isEqualByComparingTo("30");
    assertThat(result.displayAccounts().get(0).vestedBalance()).isEqualByComparingTo("10");
    assertThat(result.accountTotals().totalBalance()).isEqualByComparingTo("60");
    assertThat(result.accountTotals().pendingSwitchIn()).isEqualByComparingTo("6");
  }

  @Test
  void technicalIdentifierBreaksEqualSequenceTies() {
    var normalized = normalized(false, false, List.of(source("ZZ", "1", 10), source("AA", "1", 10)),
        List.of(holding("ZZ", "1", "F1", "1", null, null), holding("AA", "1", "F2", "1", null, null)));
    assertThat(aggregator.aggregate(normalized).displayAccounts()).extracting(RawDisplayAccountOverview::displayAccountId).containsExactly("AA", "ZZ");
  }

  @Test
  void hiddenValuesRemainNullAndAreNotInspected() {
    var result = aggregator.aggregate(normalized(false, false, List.of(source("A", "1", 1)),
        List.of(holding("A", "1", "F", "0", "-1", null))));
    assertThat(result.displayAccounts().getFirst().accountBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.displayAccounts().getFirst().vestedBalance()).isNull();
    assertThat(result.accountTotals().pendingSwitchIn()).isNull();
  }

  @Test
  void visibleZeroIsRetained() {
    var result = aggregator.aggregate(normalized(true, true, List.of(source("A", "1", 1)),
        List.of(holding("A", "1", "F", "0", "0", "0"))));
    assertThat(result.displayAccounts().getFirst().vestedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.accountTotals().pendingSwitchIn()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void visibleNullAndMinusOneParticipatingValuesFailTechnically() {
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", null, "1", "1"))));
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", "-1.0", "1", "1"))));
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", "1", null, "1"))));
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", "1", "-1", "1"))));
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", "1", "1", null))));
    assertFailure(normalized(true, true, List.of(source("A", "1", 1)), List.of(holding("A", "1", "F", "1", "1", "-1"))));
  }

  @Test
  void nullParticipatingSequenceFailsTechnically() {
    assertFailure(normalized(false, false, List.of(source("A", "1", null)), List.of(holding("A", "1", "F", "1", null, null))));
  }

  @Test
  void emptyHoldingsProduceNoDisplayAccountsAndZeroVisibleTotals() {
    var result = aggregator.aggregate(normalized(true, true, List.of(source("A", "1", 1)), List.of()));
    assertThat(result.displayAccounts()).isEmpty();
    assertThat(result.accountTotals().totalBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.accountTotals().pendingSwitchIn()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  private void assertFailure(NormalizedAccountBalanceData data) {
    assertThatThrownBy(() -> aggregator.aggregate(data)).isInstanceOf(AccountBalanceRetrievalException.class)
        .extracting("reason").isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE);
  }

  private static NormalizedAccountBalanceData normalized(boolean vestedVisible, boolean pendingVisible,
      List<RawAccountBalanceDisplaySource> sources, List<RawAccountBalanceHolding> holdings) {
    var sourceMap = new LinkedHashMap<DisplaySourceKey, RawAccountBalanceDisplaySource>();
    sources.forEach(s -> sourceMap.put(new DisplaySourceKey(s.displaySource(), s.subDisplaySource()), s));
    var holdingMap = new LinkedHashMap<HoldingKey, RawAccountBalanceHolding>();
    holdings.forEach(h -> holdingMap.put(new HoldingKey(h.displaySource(), h.subDisplaySource(), h.fundCode()), h));
    return new NormalizedAccountBalanceData(new RawAccountBalanceDisplayOptions(vestedVisible, pendingVisible, false),
        new RawAccountBalanceCurrency(List.of("HKD"), "HKD"), sourceMap, new LinkedHashMap<>(), holdingMap);
  }

  private static RawAccountBalanceDisplaySource source(String display, String sub, Integer seq) {
    return new RawAccountBalanceDisplaySource(display, "English", "中文", sub, "Sub", "子", seq);
  }

  private static RawAccountBalanceHolding holding(String display, String sub, String fund, String nav, String vested, String transit) {
    return new RawAccountBalanceHolding(display, sub, fund, BigDecimal.ONE, decimal(nav), decimal(vested), decimal(transit));
  }

  private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
