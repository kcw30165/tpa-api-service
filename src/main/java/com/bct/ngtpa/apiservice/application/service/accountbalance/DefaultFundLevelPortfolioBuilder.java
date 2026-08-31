package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.FundLevelPortfolioBuilder;
import java.math.BigDecimal;
import java.util.*;

public final class DefaultFundLevelPortfolioBuilder implements FundLevelPortfolioBuilder {
  private static final BigDecimal SENTINEL = BigDecimal.valueOf(-1);

  @Override
  public RawFundLevelView build(NormalizedAccountBalanceData data) {
    try {
      if (data == null) fail();
      Map<String, DisplayAcc> displays = new LinkedHashMap<>();
      for (var e : data.holdings().entrySet()) {
        HoldingKey k = e.getKey();
        RawAccountBalanceHolding h = e.getValue();
        var src =
            data.displaySources()
                .get(new DisplaySourceKey(k.displaySource(), k.subDisplaySource()));
        var fund = data.funds().get(new FundKey(k.fundCode()));
        if (src == null || fund == null || src.sequence() == null || fund.fundSequence() == null)
          fail();
        BigDecimal unit = participating(h.unit()), nav = participating(h.netAssetValue());
        var d = displays.computeIfAbsent(k.displaySource(), x -> new DisplayAcc(x));
        d.sequence = Math.min(d.sequence, src.sequence());
        var f =
            d.funds.computeIfAbsent(
                k.fundCode(), x -> new FundAcc(fund.fundCode(), fund.fundSequence()));
        f.unit = f.unit.add(unit);
        f.nav = f.nav.add(nav);
        f.leaves.add(
            new RawFundLevelSubDisplayAccount(k.subDisplaySource(), src.sequence(), unit, nav));
      }
      return new RawFundLevelView(
          displays.values().stream()
              .map(DisplayAcc::toRecord)
              .sorted(
                  Comparator.comparing(RawFundLevelDisplayAccount::displaySequence)
                      .thenComparing(RawFundLevelDisplayAccount::displayAccountId))
              .toList());
    } catch (AccountBalanceRetrievalException x) {
      throw x;
    } catch (RuntimeException x) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }

  private static BigDecimal participating(BigDecimal v) {
    if (v == null || v.compareTo(SENTINEL) == 0) fail();
    return v;
  }

  private static void fail() {
    throw AccountBalanceRetrievalException.systemFailure();
  }

  private static final class DisplayAcc {
    final String id;
    int sequence = Integer.MAX_VALUE;
    final Map<String, FundAcc> funds = new LinkedHashMap<>();

    DisplayAcc(String id) {
      this.id = id;
    }

    RawFundLevelDisplayAccount toRecord() {
      return new RawFundLevelDisplayAccount(
          id,
          sequence,
          funds.values().stream()
              .map(FundAcc::toRecord)
              .sorted(
                  Comparator.comparing(RawFundLevelFund::fundSequence)
                      .thenComparing(RawFundLevelFund::fundCode))
              .toList());
    }
  }

  private static final class FundAcc {
    final String code;
    final Integer sequence;
    BigDecimal unit = BigDecimal.ZERO, nav = BigDecimal.ZERO;
    final List<RawFundLevelSubDisplayAccount> leaves = new ArrayList<>();

    FundAcc(String c, Integer s) {
      code = c;
      sequence = s;
    }

    RawFundLevelFund toRecord() {
      return new RawFundLevelFund(
          code,
          sequence,
          unit,
          nav,
          leaves.stream()
              .sorted(
                  Comparator.comparing(RawFundLevelSubDisplayAccount::displaySequence)
                      .thenComparing(RawFundLevelSubDisplayAccount::subDisplayAccountId))
              .toList());
    }
  }
}
