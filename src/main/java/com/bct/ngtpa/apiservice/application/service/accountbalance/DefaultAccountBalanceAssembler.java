package com.bct.ngtpa.apiservice.application.service.accountbalance;

import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.dto.metadata.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import com.bct.ngtpa.apiservice.application.port.in.*;
import com.bct.ngtpa.apiservice.application.port.out.AccountBalancePresentationEventPort;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import reactor.core.publisher.Mono;

public final class DefaultAccountBalanceAssembler implements AccountBalanceAssembler {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private final AccountBalanceNormalizer normalizer;
  private final AccountOverviewAggregator overview;
  private final FundLevelPortfolioBuilder fundBuilder;
  private final SubDisplayLevelPortfolioBuilder subBuilder;
  private final AccountBalancePresentationTransformer presentation;
  private final AccountBalanceCurrencySelectionMapper currencies;
  private final AccountBalanceFactsheetResolver factsheets;
  private final AccountBalanceDynamicDescriptionResolver descriptions =
      new AccountBalanceDynamicDescriptionResolver();
  private final AccountBalancePrecisionRenderer renderer = new AccountBalancePrecisionRenderer();

  public DefaultAccountBalanceAssembler(
      AccountBalanceNormalizer n,
      AccountOverviewAggregator o,
      FundLevelPortfolioBuilder f,
      SubDisplayLevelPortfolioBuilder s,
      AccountBalancePresentationTransformer p,
      AccountBalanceCurrencySelectionMapper c,
      AccountBalanceFactsheetResolver u) {
    normalizer = n;
    overview = o;
    fundBuilder = f;
    subBuilder = s;
    presentation = p;
    currencies = c;
    factsheets = u;
  }

  public static DefaultAccountBalanceAssembler createForTests(
      AccountBalancePresentationEventPort e) {
    return new DefaultAccountBalanceAssembler(
        new DefaultAccountBalanceNormalizer(),
        new DefaultAccountOverviewAggregator(),
        new DefaultFundLevelPortfolioBuilder(),
        new DefaultSubDisplayLevelPortfolioBuilder(),
        new DefaultAccountBalancePresentationTransformer(),
        new AccountBalanceCurrencySelectionMapper(),
        new AccountBalanceFactsheetResolver(e));
  }

  @Override
  public Mono<AccountBalanceResult> assemble(
      GetAccountBalanceCommand command,
      LocalDate date,
      ResolvedGeneralMetadataSet metadata,
      RawAccountBalanceData raw) {
    return Mono.fromCallable(() -> build(command, date, metadata, raw))
        .onErrorMap(
            x ->
                x instanceof AccountBalanceRetrievalException
                    ? x
                    : AccountBalanceRetrievalException.systemFailure());
  }

  private AccountBalanceResult build(
      GetAccountBalanceCommand c,
      LocalDate d,
      ResolvedGeneralMetadataSet m,
      RawAccountBalanceData r) {
    if (c == null || d == null || m == null || r == null)
      throw AccountBalanceRetrievalException.systemFailure();
    NormalizedAccountBalanceData n = normalizer.normalize(r);
    var o = overview.aggregate(n);
    var fv = fundBuilder.build(n);
    var sv = subBuilder.build(n);
    presentation.transform(c.language(), m);
    Map<GeneralMetadataName, BigInteger> p =
        ((DefaultAccountBalancePresentationTransformer) presentation).precisions(m);
    var ao =
        new AccountOverview(
            new AccountOverviewDisplayOptions(o.vestedBalanceVisible(), o.pendingSwitchInVisible()),
            o.displayAccounts().stream()
                .map(
                    x ->
                        new AccountOverviewDisplayAccount(
                            x.displayAccountId(),
                            displayName(n, x.displayAccountId(), c.language()),
                            x.displaySequence(),
                            render(x.accountBalance(), p, GeneralMetadataName.NET_ASSET_VALUE),
                            o.vestedBalanceVisible()
                                ? render(x.vestedBalance(), p, GeneralMetadataName.NET_ASSET_VALUE)
                                : null))
                .toList(),
            new AccountOverviewTotals(
                render(o.accountTotals().totalBalance(), p, GeneralMetadataName.NET_ASSET_VALUE),
                o.pendingSwitchInVisible()
                    ? render(
                        o.accountTotals().pendingSwitchIn(), p, GeneralMetadataName.NET_ASSET_VALUE)
                    : null));
    return new AccountBalanceResult(
        r.asOfDate().format(DATE),
        currencies.map(n.currency()),
        ao,
        new InvestmentPortfolio(mapFund(fv, n, c.language(), p), mapSub(sv, n, c.language(), p)));
  }

  private List<FundLevelDisplayAccount> mapFund(
      RawFundLevelView v,
      NormalizedAccountBalanceData n,
      String l,
      Map<GeneralMetadataName, BigInteger> p) {
    return v.displayAccounts().stream()
        .map(
            d ->
                new FundLevelDisplayAccount(
                    displayName(n, d.displayAccountId(), l),
                    d.displayAccountId(),
                    d.displaySequence(),
                    d.funds().stream()
                        .map(
                            f -> {
                              var raw = n.funds().get(new FundKey(f.fundCode()));
                              String fs = factsheets.resolve(raw.fundFactsheet()).orElse(null),
                                  fx =
                                      n.displayOptions().fxRateVisible()
                                          ? render(
                                              raw.exchangeRate(), p, GeneralMetadataName.FX_RATE)
                                          : null;
                              return new FundLevelFund(
                                  f.fundCode(),
                                  descriptions.resolve(l, raw.fundName(), raw.chineseFundName()),
                                  render(raw.fundPrice(), p, GeneralMetadataName.FUND_PRICE),
                                  render(f.unitBalance(), p, GeneralMetadataName.UNIT_BALANCE),
                                  render(
                                      f.accountBalance(), p, GeneralMetadataName.NET_ASSET_VALUE),
                                  fs,
                                  fx,
                                  f.subDisplayAccounts().stream()
                                      .map(
                                          s ->
                                              new FundLevelSubDisplayAccount(
                                                  subName(
                                                      n,
                                                      d.displayAccountId(),
                                                      s.subDisplayAccountId(),
                                                      l),
                                                  s.subDisplayAccountId(),
                                                  render(
                                                      s.unitBalance(),
                                                      p,
                                                      GeneralMetadataName.UNIT_BALANCE),
                                                  render(
                                                      s.accountBalance(),
                                                      p,
                                                      GeneralMetadataName.NET_ASSET_VALUE),
                                                  render(
                                                      raw.fundPrice(),
                                                      p,
                                                      GeneralMetadataName.FUND_PRICE),
                                                  fx))
                                      .toList());
                            })
                        .toList()))
        .toList();
  }

  private List<SubDisplayLevelDisplayAccount> mapSub(
      RawSubDisplayLevelView v,
      NormalizedAccountBalanceData n,
      String l,
      Map<GeneralMetadataName, BigInteger> p) {
    return v.displayAccounts().stream()
        .map(
            d ->
                new SubDisplayLevelDisplayAccount(
                    displayName(n, d.displayAccountId(), l),
                    d.displayAccountId(),
                    d.displaySequence(),
                    d.subDisplayAccounts().stream()
                        .map(
                            s ->
                                new SubDisplayLevelSubDisplayAccount(
                                    subName(n, d.displayAccountId(), s.subDisplayAccountId(), l),
                                    s.subDisplayAccountId(),
                                    s.funds().stream()
                                        .map(
                                            f -> {
                                              var raw = n.funds().get(new FundKey(f.fundCode()));
                                              return new SubDisplayLevelFund(
                                                  f.fundCode(),
                                                  descriptions.resolve(
                                                      l, raw.fundName(), raw.chineseFundName()),
                                                  render(
                                                      raw.fundPrice(),
                                                      p,
                                                      GeneralMetadataName.FUND_PRICE),
                                                  render(
                                                      f.unitBalance(),
                                                      p,
                                                      GeneralMetadataName.UNIT_BALANCE),
                                                  render(
                                                      f.accountBalance(),
                                                      p,
                                                      GeneralMetadataName.NET_ASSET_VALUE),
                                                  factsheets
                                                      .resolve(raw.fundFactsheet())
                                                      .orElse(null),
                                                  n.displayOptions().fxRateVisible()
                                                      ? render(
                                                          raw.exchangeRate(),
                                                          p,
                                                          GeneralMetadataName.FX_RATE)
                                                      : null);
                                            })
                                        .toList()))
                        .toList()))
        .toList();
  }

  private String displayName(NormalizedAccountBalanceData n, String id, String l) {
    return n.displaySources().entrySet().stream()
        .filter(e -> e.getKey().displaySource().equals(id))
        .findFirst()
        .map(
            e ->
                descriptions.resolve(
                    l, e.getValue().sourceDescription(), e.getValue().sourceChineseDescription()))
        .orElseThrow(AccountBalanceRetrievalException::systemFailure);
  }

  private String subName(NormalizedAccountBalanceData n, String d, String s, String l) {
    var x = n.displaySources().get(new DisplaySourceKey(d, s));
    if (x == null) throw AccountBalanceRetrievalException.systemFailure();
    return descriptions.resolve(l, x.subSourceDescription(), x.subSourceChineseDescription());
  }

  private String render(
      java.math.BigDecimal x, Map<GeneralMetadataName, BigInteger> p, GeneralMetadataName k) {
    return renderer.render(x, p.get(k));
  }
}
