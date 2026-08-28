package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.overview.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.AccountOverviewAggregator;
import java.math.BigDecimal;
import java.util.*;

public final class DefaultAccountOverviewAggregator implements AccountOverviewAggregator {
  private static final BigDecimal SENTINEL = BigDecimal.valueOf(-1);

  @Override
  public RawAccountOverviewAggregation aggregate(NormalizedAccountBalanceData data) {
    if (data == null || data.displayOptions() == null) throw systemFailure();
    boolean vestedVisible = data.displayOptions().vestedBalanceVisible();
    boolean pendingVisible = data.displayOptions().pendingSwitchInVisible();
    Map<String, Accumulator> grouped = new HashMap<>();
    BigDecimal totalBalance = BigDecimal.ZERO;
    BigDecimal pendingSwitchIn = pendingVisible ? BigDecimal.ZERO : null;
    for (Map.Entry<HoldingKey, RawAccountBalanceHolding> entry : data.holdings().entrySet()) {
      HoldingKey key = entry.getKey();
      RawAccountBalanceHolding holding = entry.getValue();
      BigDecimal nav = participating(holding.netAssetValue());
      BigDecimal vested = vestedVisible ? participating(holding.vestedBalance()) : null;
      BigDecimal transit = pendingVisible ? participating(holding.transitAmount()) : null;
      RawAccountBalanceDisplaySource source =
          data.displaySources()
              .get(new DisplaySourceKey(key.displaySource(), key.subDisplaySource()));
      if (source == null || source.sequence() == null) throw systemFailure();
      Accumulator accumulator =
          grouped.computeIfAbsent(key.displaySource(), ignored -> new Accumulator());
      accumulator.sequence = Math.min(accumulator.sequence, source.sequence());
      accumulator.balance = accumulator.balance.add(nav);
      if (vestedVisible) accumulator.vested = accumulator.vested.add(vested);
      totalBalance = totalBalance.add(nav);
      if (pendingVisible) pendingSwitchIn = pendingSwitchIn.add(transit);
    }
    List<RawDisplayAccountOverview> displayAccounts =
        grouped.entrySet().stream()
            .map(
                entry ->
                    new RawDisplayAccountOverview(
                        entry.getKey(),
                        entry.getValue().sequence,
                        entry.getValue().balance,
                        vestedVisible ? entry.getValue().vested : null))
            .sorted(
                Comparator.comparing(RawDisplayAccountOverview::displaySequence)
                    .thenComparing(RawDisplayAccountOverview::displayAccountId))
            .toList();
    return new RawAccountOverviewAggregation(
        vestedVisible,
        pendingVisible,
        displayAccounts,
        new RawAccountTotals(totalBalance, pendingSwitchIn));
  }

  private static BigDecimal participating(BigDecimal value) {
    if (value == null || value.compareTo(SENTINEL) == 0) throw systemFailure();
    return value;
  }

  private static AccountBalanceRetrievalException systemFailure() {
    return AccountBalanceRetrievalException.systemFailure();
  }

  private static final class Accumulator {
    private int sequence = Integer.MAX_VALUE;
    private BigDecimal balance = BigDecimal.ZERO;
    private BigDecimal vested = BigDecimal.ZERO;
  }
}
