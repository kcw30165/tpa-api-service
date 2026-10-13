package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.DisplaySourceKey;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.FundKey;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.HoldingKey;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultAccountBalanceNormalizerTest {
  private final DefaultAccountBalanceNormalizer normalizer = new DefaultAccountBalanceNormalizer();

  @Test
  void buildsImmutableInsertionOrderedIndexesAndPreservesRawValues() {
    var source = source("EE", "OE", 20);
    var fund = fund("DBLF", "535.9379", "1.0", "not a url");
    var holding = holding("EE", "OE", "DBLF", "10.123", "-1", null, "2.50");
    var normalized = normalizer.normalize(raw(List.of(source), List.of(fund), List.of(holding)));

    assertThat(normalized.displaySources())
        .containsExactlyEntriesOf(java.util.Map.of(new DisplaySourceKey("EE", "OE"), source));
    assertThat(normalized.funds())
        .containsExactlyEntriesOf(java.util.Map.of(new FundKey("DBLF"), fund));
    assertThat(normalized.holdings())
        .containsExactlyEntriesOf(java.util.Map.of(new HoldingKey("EE", "OE", "DBLF"), holding));
    assertThat(normalized.funds().get(new FundKey("DBLF")).fundPrice())
        .isEqualByComparingTo("535.9379");
    assertThat(normalized.holdings().values().iterator().next().netAssetValue())
        .isEqualByComparingTo("-1");
    assertThat(normalized.holdings().values().iterator().next().vestedBalance()).isNull();
    assertThat(normalized.funds().values().iterator().next().fundFactsheet())
        .isEqualTo("not a url");
    assertThat(normalized.displaySources().values().iterator().next().sourceChineseDescription())
        .isEqualTo("來源");
    assertThatThrownBy(() -> normalized.funds().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void preservesInsertionOrder() {
    var first = source("A", "1", 20);
    var second = source("B", "2", 10);
    var normalized = normalizer.normalize(raw(List.of(first, second), List.of(), List.of()));
    assertThat(normalized.displaySources().keySet())
        .containsExactly(new DisplaySourceKey("A", "1"), new DisplaySourceKey("B", "2"));
  }

  @Test
  void acceptsThreeEmptyCollectionsAndUnreferencedParents() {
    var empty = normalizer.normalize(raw(List.of(), List.of(), List.of()));
    assertThat(empty.displaySources()).isEmpty();
    assertThat(empty.funds()).isEmpty();
    assertThat(empty.holdings()).isEmpty();
    var parents =
        normalizer.normalize(
            raw(List.of(source("EE", "OE", 1)), List.of(fund("F", "1", "1", "x")), List.of()));
    assertThat(parents.displaySources()).hasSize(1);
    assertThat(parents.funds()).hasSize(1);
  }

  @Test
  void rejectsEveryDuplicateKeyEvenWhenPayloadsAreEqual() {
    var source = source("EE", "OE", 1);
    var fund = fund("F", "1", "1", "x");
    var holding = holding("EE", "OE", "F", "1", "1", "1", "1");
    assertSystemFailure(raw(List.of(source, source), List.of(), List.of()));
    assertSystemFailure(raw(List.of(), List.of(fund, fund), List.of()));
    assertSystemFailure(raw(List.of(source), List.of(fund), List.of(holding, holding)));
  }

  @Test
  void rejectsBlankOrNullIdentifiers() {
    assertSystemFailure(raw(List.of(source(" ", "OE", 1)), List.of(), List.of()));
    assertSystemFailure(raw(List.of(source("EE", null, 1)), List.of(), List.of()));
    assertSystemFailure(raw(List.of(), List.of(fund(" ", "1", "1", "x")), List.of()));
    assertSystemFailure(
        raw(List.of(), List.of(), List.of(holding("EE", "OE", null, "1", "1", "1", "1"))));
  }

  @Test
  void rejectsDisplaySourceAndFundOrphans() {
    var holding = holding("EE", "OE", "F", "1", "1", "1", "1");
    assertSystemFailure(raw(List.of(), List.of(fund("F", "1", "1", "x")), List.of(holding)));
    assertSystemFailure(raw(List.of(source("EE", "OE", 1)), List.of(), List.of(holding)));
  }

  private void assertSystemFailure(RawAccountBalanceData raw) {
    assertThatThrownBy(() -> normalizer.normalize(raw))
        .isInstanceOf(AccountBalanceRetrievalException.class)
        .extracting("reason")
        .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE);
  }

  private static RawAccountBalanceData raw(
      List<RawAccountBalanceDisplaySource> sources,
      List<RawAccountBalanceFundDetail> funds,
      List<RawAccountBalanceHolding> holdings) {
    return new RawAccountBalanceData(
        null,
        new RawAccountBalanceDisplayOptions(true, false, true),
        new RawAccountBalanceCurrency(List.of("HKD"), "HKD"),
        sources,
        funds,
        holdings);
  }

  private static RawAccountBalanceDisplaySource source(
      String display, String sub, Integer sequence) {
    return new RawAccountBalanceDisplaySource(display, "Source", "來源", sub, "Sub", "子來源", sequence);
  }

  private static RawAccountBalanceFundDetail fund(
      String code, String price, String rate, String factsheet) {
    return new RawAccountBalanceFundDetail(
        code, "Fund", "基金", 1, new BigDecimal(price), "HKD", new BigDecimal(rate), factsheet, null);
  }

  private static RawAccountBalanceHolding holding(
      String display,
      String sub,
      String fund,
      String unit,
      String nav,
      String vested,
      String transit) {
    return new RawAccountBalanceHolding(
        display, sub, fund, decimal(unit), decimal(nav), decimal(vested), decimal(transit));
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
