package com.bct.ngtpa.apiservice.application.service.accountsummary;

import com.bct.ngtpa.apiservice.application.dto.AccountBalanceResult;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceData;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceFundDetail;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.RawAccountBalanceHolding;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossFund;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GainLossBreakdown;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GainLossBreakdownHolding;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataKey;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataName;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataPrecision;
import com.bct.ngtpa.apiservice.application.dto.metadata.GeneralMetadataType;
import com.bct.ngtpa.apiservice.application.dto.metadata.ResolvedGeneralMetadataSet;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.service.presentation.DynamicDescriptionResolver;
import com.bct.ngtpa.apiservice.application.service.presentation.ExactPrecisionRenderer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes an already-produced {@link AccountBalanceResult} and {@link AccountGainLossResult}
 * into the account summary shape. Both existing assemblers are reused verbatim for every value
 * they already compute; the only new computation here is the per-fund balance/percentage
 * aggregation (R-5), the sold-fund empty-sum rule (R-2/R-4), the null-vs-unreported distinction
 * (R-6), the Q-5 superset invariant, and the accountNumber join (R-3).
 */
public final class AccountSummaryAssembler {

  private static final GeneralMetadataKey NAV_PRECISION =
      new GeneralMetadataKey(GeneralMetadataName.NET_ASSET_VALUE, GeneralMetadataType.PRECISION);
  private static final GeneralMetadataKey AB_PERCENTAGE_PRECISION =
      new GeneralMetadataKey(GeneralMetadataName.AB_PERCENTAGE, GeneralMetadataType.PRECISION);
  private static final BigDecimal SENTINEL = BigDecimal.valueOf(-1);

  private final DynamicDescriptionResolver descriptions = new DynamicDescriptionResolver();
  private final ExactPrecisionRenderer renderer = new ExactPrecisionRenderer();

  public AccountSummaryResult assemble(
      GetAccountSummaryCommand command,
      AccountBalanceResult balanceResult,
      AccountGainLossResult gainLossResult,
      RawAccountBalanceData rawBalance,
      ResolvedGeneralMetadataSet metadata) {
    try {
      if (command == null
          || balanceResult == null
          || gainLossResult == null
          || rawBalance == null
          || metadata == null) {
        throw new IllegalArgumentException();
      }

      String accountNumber = accountNumber(command);
      BigInteger navPrecision = precision(metadata, NAV_PRECISION);
      BigInteger percentagePrecision = precision(metadata, AB_PERCENTAGE_PRECISION);

      Map<String, FundAggregate> aggregates = aggregate(rawBalance.holdings());
      Map<String, RawAccountBalanceFundDetail> fundDetails =
          fundDetailsByCode(rawBalance.fundDetails());

      Set<String> gainLossFundCodes = new LinkedHashSet<>();
      for (AccountGainLossFund fund : gainLossResult.funds()) {
        gainLossFundCodes.add(fund.fundCode());
      }
      for (String heldFundCode : aggregates.keySet()) {
        if (!gainLossFundCodes.contains(heldFundCode)) {
          // Q-5 superset invariant: every held fund must also appear in the gain/loss list.
          throw new IllegalStateException("held fund absent from gain/loss superset");
        }
      }

      List<GainLossBreakdownHolding> holdings =
          gainLossResult.funds().stream()
              .map(
                  fund ->
                      holding(
                          fund,
                          aggregates.get(fund.fundCode()),
                          fundDetails.get(fund.fundCode()),
                          command.language(),
                          navPrecision,
                          percentagePrecision))
              .toList();

      var breakdown =
          new GainLossBreakdown(
              gainLossResult.period().name(),
              gainLossResult.asOfDate(),
              balanceResult.accountOverview().accountTotals().totalBalance(),
              gainLossResult.totalGainLoss(),
              holdings);

      return new AccountSummaryResult(
          accountNumber,
          balanceResult.asOfDate(),
          balanceResult.currencySelection(),
          balanceResult.accountOverview(),
          balanceResult.investmentPortfolio(),
          breakdown);
    } catch (AccountBalanceRetrievalException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw AccountBalanceRetrievalException.systemFailure();
    }
  }

  private String accountNumber(GetAccountSummaryCommand command) {
    String policyNo = command.policyNo();
    String certNo = command.certNo();
    if (policyNo == null || policyNo.isBlank() || certNo == null || certNo.isBlank()) {
      throw new IllegalArgumentException("policyNo and certNo are required for accountNumber");
    }
    return policyNo + "-" + certNo;
  }

  private GainLossBreakdownHolding holding(
      AccountGainLossFund fund,
      FundAggregate aggregate,
      RawAccountBalanceFundDetail fundDetail,
      String language,
      BigInteger navPrecision,
      BigInteger percentagePrecision) {
    FundAggregate resolved = aggregate == null ? FundAggregate.SOLD : aggregate;
    String fundName =
        fundDetail == null
            ? fund.fundName()
            : descriptions.resolve(language, fundDetail.fundName(), fundDetail.chineseFundName());
    String balance = renderer.render(resolved.balanceSum, navPrecision);
    String portfolioPercentage =
        resolved.percentageSum == null
            ? null
            : renderer.render(resolved.percentageSum, percentagePrecision);
    return new GainLossBreakdownHolding(
        fund.fundCode(),
        fundName,
        fund.fundSequence(),
        balance,
        portfolioPercentage,
        fund.gainLoss());
  }

  private BigInteger precision(ResolvedGeneralMetadataSet metadata, GeneralMetadataKey key) {
    return GeneralMetadataPrecision.parse(metadata.find(key).orElseThrow().value()).value();
  }

  private Map<String, RawAccountBalanceFundDetail> fundDetailsByCode(
      List<RawAccountBalanceFundDetail> fundDetails) {
    Map<String, RawAccountBalanceFundDetail> byCode = new LinkedHashMap<>();
    for (RawAccountBalanceFundDetail detail : fundDetails) {
      if (detail == null || detail.fundCode() == null) {
        throw new IllegalStateException("fund detail with null fund code");
      }
      String trimmed = detail.fundCode().trim();
      if (byCode.putIfAbsent(trimmed, detail) != null) {
        throw new IllegalStateException("duplicate fund detail code");
      }
    }
    return byCode;
  }

  /**
   * Sums nav and percentage across every accBal row for a fund, keyed by trimmed fund code. nav
   * uses the same sentinel rule as the rest of Account Balance (null or -1 is a technical
   * failure). percentage is null-safe: a fund whose rows are ALL null-percentage is treated as
   * globally unreported for that fund (R-6, e.g. the pre-deployment transition state); a fund with
   * SOME null and some populated rows is a partial-sum contract violation and fails (R-5). A fund
   * with zero rows at all (sold, R-2/R-4) is not represented here; the caller substitutes the
   * empty-sum default.
   */
  private Map<String, FundAggregate> aggregate(List<RawAccountBalanceHolding> holdings) {
    Map<String, BigDecimal> balanceSums = new LinkedHashMap<>();
    Map<String, BigDecimal> percentageSums = new LinkedHashMap<>();
    Map<String, Integer> rowCounts = new LinkedHashMap<>();
    Map<String, Integer> nullPercentageCounts = new LinkedHashMap<>();

    for (RawAccountBalanceHolding holding : holdings) {
      if (holding == null || holding.fundCode() == null) {
        throw new IllegalStateException("holding with null fund code");
      }
      String code = holding.fundCode().trim();
      BigDecimal nav = participatingNav(holding.netAssetValue());
      balanceSums.merge(code, nav, BigDecimal::add);
      rowCounts.merge(code, 1, Integer::sum);
      if (holding.percentage() == null) {
        nullPercentageCounts.merge(code, 1, Integer::sum);
      } else {
        percentageSums.merge(code, holding.percentage(), BigDecimal::add);
      }
    }

    Map<String, FundAggregate> result = new LinkedHashMap<>();
    for (String code : rowCounts.keySet()) {
      int rows = rowCounts.get(code);
      int nulls = nullPercentageCounts.getOrDefault(code, 0);
      BigDecimal percentageSum;
      if (nulls == rows) {
        percentageSum = null;
      } else if (nulls > 0) {
        throw new IllegalStateException("partial percentage reporting within one fund");
      } else {
        percentageSum = percentageSums.get(code);
      }
      result.put(code, new FundAggregate(balanceSums.get(code), percentageSum));
    }
    return result;
  }

  private static BigDecimal participatingNav(BigDecimal value) {
    if (value == null || value.compareTo(SENTINEL) == 0) {
      throw new IllegalStateException("non-participating nav value");
    }
    return value;
  }

  private record FundAggregate(BigDecimal balanceSum, BigDecimal percentageSum) {
    static final FundAggregate SOLD = new FundAggregate(BigDecimal.ZERO, BigDecimal.ZERO);
  }
}
