package com.bct.ngtpa.apiservice.application.service.accountbalance;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultSubDisplayLevelPortfolioBuilderTest {
  private final DefaultSubDisplayLevelPortfolioBuilder builder =
      new DefaultSubDisplayLevelPortfolioBuilder();

  @Test
  void buildsHeldHierarchyWithDeterministicOrdering() {
    var r = builder.build(TestData.data());
    assertThat(r.displayAccounts())
        .extracting(RawSubDisplayLevelDisplayAccount::displayAccountId)
        .containsExactly("AA", "ZZ");
    var zz = r.displayAccounts().get(1);
    assertThat(zz.displaySequence()).isEqualTo(10);
    assertThat(zz.subDisplayAccounts())
        .extracting(RawSubDisplayLevelSubDisplayAccount::subDisplayAccountId)
        .containsExactly("A", "B");
    assertThat(zz.subDisplayAccounts().getFirst().funds())
        .extracting(RawSubDisplayLevelFund::fundCode)
        .containsExactly("F1", "F2");
  }

  @Test
  void preservesUniqueHoldingValuesZeroScaleAndImmutability() {
    var r = builder.build(TestData.single("0.00", "0.000"));
    var f = r.displayAccounts().getFirst().subDisplayAccounts().getFirst().funds().getFirst();
    assertThat(f.unitBalance().scale()).isEqualTo(2);
    assertThat(f.accountBalance().scale()).isEqualTo(3);
    assertThatThrownBy(() -> r.displayAccounts().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void emptyHoldingsDoNotFabricateParents() {
    assertThat(builder.build(TestData.emptyHeld()).displayAccounts()).isEmpty();
  }

  @Test
  void rejectsInvalidParticipatingValuesAndSequences() {
    for (var d :
        List.of(
            TestData.single(null, "1"),
            TestData.single("-1.0", "1"),
            TestData.single("1", null),
            TestData.single("1", "-1"),
            TestData.nullSourceSequence(),
            TestData.nullFundSequence()))
      assertThatThrownBy(() -> builder.build(d))
          .isInstanceOf(AccountBalanceRetrievalException.class)
          .extracting("reason")
          .isEqualTo(AccountBalanceRetrievalException.Reason.SYSTEM_FAILURE);
  }

  static final class TestData {
    static NormalizedAccountBalanceData data() {
      return normalized(
          List.of(src("ZZ", "B", 20), src("ZZ", "A", 10), src("AA", "C", 10)),
          List.of(fund("F2", 20), fund("F1", 10)),
          List.of(
              hold("ZZ", "A", "F2", "2", "20"),
              hold("ZZ", "A", "F1", "1", "10"),
              hold("ZZ", "B", "F1", "3", "30"),
              hold("AA", "C", "F2", "4", "40")));
    }

    static NormalizedAccountBalanceData single(String u, String n) {
      return normalized(
          List.of(src("A", "1", 1)), List.of(fund("F", 1)), List.of(hold("A", "1", "F", u, n)));
    }

    static NormalizedAccountBalanceData emptyHeld() {
      return normalized(List.of(src("A", "1", 1)), List.of(fund("F", 1)), List.of());
    }

    static NormalizedAccountBalanceData nullSourceSequence() {
      return normalized(
          List.of(src("A", "1", null)),
          List.of(fund("F", 1)),
          List.of(hold("A", "1", "F", "1", "1")));
    }

    static NormalizedAccountBalanceData nullFundSequence() {
      return normalized(
          List.of(src("A", "1", 1)),
          List.of(fund("F", null)),
          List.of(hold("A", "1", "F", "1", "1")));
    }

    static NormalizedAccountBalanceData normalized(
        List<RawAccountBalanceDisplaySource> s,
        List<RawAccountBalanceFundDetail> f,
        List<RawAccountBalanceHolding> h) {
      Map<DisplaySourceKey, RawAccountBalanceDisplaySource> sm = new LinkedHashMap<>();
      s.forEach(x -> sm.put(new DisplaySourceKey(x.displaySource(), x.subDisplaySource()), x));
      Map<FundKey, RawAccountBalanceFundDetail> fm = new LinkedHashMap<>();
      f.forEach(x -> fm.put(new FundKey(x.fundCode()), x));
      Map<HoldingKey, RawAccountBalanceHolding> hm = new LinkedHashMap<>();
      h.forEach(
          x -> hm.put(new HoldingKey(x.displaySource(), x.subDisplaySource(), x.fundCode()), x));
      return new NormalizedAccountBalanceData(null, null, sm, fm, hm);
    }

    static RawAccountBalanceDisplaySource src(String d, String s, Integer q) {
      return new RawAccountBalanceDisplaySource(d, "E", "中", s, "SE", "中", q);
    }

    static RawAccountBalanceFundDetail fund(String c, Integer q) {
      return new RawAccountBalanceFundDetail(
          c, "N", "中", q, BigDecimal.ONE, "HKD", BigDecimal.ONE, "raw");
    }

    static RawAccountBalanceHolding hold(String d, String s, String f, String u, String n) {
      return new RawAccountBalanceHolding(
          d,
          s,
          f,
          u == null ? null : new BigDecimal(u),
          n == null ? null : new BigDecimal(n),
          null,
          null);
    }
  }
}
