package com.bct.ngtpa.apiservice.adapter.in.web.accountsummary;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.*;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.AccountSummaryResult;
import com.bct.ngtpa.apiservice.application.dto.accountsummary.GetAccountSummaryCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountSummaryUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Same header profile as {@code /api/v1/account/balance}: shared X-Request-Id enforcement (via the
 * request filter, not here), optional Accept-Language with English fallback, and selected-account
 * delegation through {@link CurrentPortalAccessContextResolver}.
 *
 * <p>Adds {@code period}, matching {@code /api/v1/account/gain-loss} exactly (optional, default
 * {@code SINCE_SCHEME_INCEPTION}). The response is the full Account Balance payload plus {@code
 * accountNumber} and {@code gainLossBreakdown}; every other field name, nullability, and ordering
 * matches {@code /api/v1/account/balance} because this controller reuses the same application-level
 * {@link CurrencySelection}, {@link AccountOverview}, and {@link InvestmentPortfolio} types that
 * {@code AccountBalanceController} maps, via {@link AccountSummaryResult} (S3).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountSummaryController {

  private final GetAccountSummaryUseCase getAccountSummaryUseCase;
  private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

  @GetMapping(value = "/account/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<AccountSummaryResultResponse>> getAccountSummary(
      @RequestParam(value = "currency", required = false) String currency,
      @RequestParam(value = "period", required = false, defaultValue = "SINCE_SCHEME_INCEPTION")
          AccountGainLossPeriod period,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        v -> {
          RequestHeaderContext h = v.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String l = RequestLanguageResolver.resolve(h, acceptLanguage, "en");
          return currentPortalAccessContextResolver
              .current()
              .flatMap(c -> invoke(c, l, clean(currency), period));
        });
  }

  private Mono<MutationResponse<AccountSummaryResultResponse>> invoke(
      PortalAccessContext c, String l, String currency, AccountGainLossPeriod period) {
    var a = c.account();
    var x = c.actor();
    return getAccountSummaryUseCase
        .get(
            new GetAccountSummaryCommand(
                l,
                currency,
                period,
                a.accountEnv(),
                a.policyNo(),
                a.certNo(),
                a.trustCode(),
                a.schemeType(),
                x.actorUserId(),
                x.actorUserRole()))
        .map(this::success);
  }

  private MutationResponse<AccountSummaryResultResponse> success(AccountSummaryResult r) {
    var currencySelection =
        new CurrencySelectionResponse(
            r.currencySelection().selectedCurrency(), r.currencySelection().availableCurrencies());
    var accountOverview =
        new AccountOverviewResponse(
            new AccountOverviewDisplayOptionsResponse(
                r.accountOverview().displayOptions().vestedBalanceVisible(),
                r.accountOverview().displayOptions().pendingSwitchInVisible()),
            r.accountOverview().displayAccounts().stream()
                .map(
                    a ->
                        new AccountOverviewDisplayAccountResponse(
                            a.displayAccountId(),
                            a.displayAccountLabel(),
                            a.displaySequence(),
                            a.accountBalance(),
                            a.vestedBalance()))
                .toList(),
            new AccountOverviewTotalsResponse(
                r.accountOverview().accountTotals().totalBalance(),
                r.accountOverview().accountTotals().pendingSwitchIn()));
    var investmentPortfolio =
        new InvestmentPortfolioResponse(
            r.investmentPortfolio().fundLevelView().stream().map(this::fundDisplay).toList(),
            r.investmentPortfolio().subDisplayAccountLevelView().stream()
                .map(this::subDisplay)
                .toList());
    var gainLossBreakdown =
        new GainLossBreakdownResponse(
            r.gainLossBreakdown().period(),
            r.gainLossBreakdown().gainLossAsOfDate(),
            r.gainLossBreakdown().totalValue(),
            r.gainLossBreakdown().totalGainLoss(),
            r.gainLossBreakdown().holdings().stream()
                .map(
                    h ->
                        new GainLossBreakdownHoldingResponse(
                            h.fundCode(),
                            h.fundName(),
                            h.fundSequence(),
                            h.balance(),
                            h.portfolioPercentage(),
                            h.gainLoss()))
                .toList());
    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new AccountSummaryResultResponse(
            new AccountSummaryDataResponse(
                r.accountNumber(),
                r.asOfDate(),
                currencySelection,
                accountOverview,
                investmentPortfolio,
                gainLossBreakdown)),
        List.of());
  }

  private FundLevelDisplayAccountResponse fundDisplay(FundLevelDisplayAccount d) {
    return new FundLevelDisplayAccountResponse(
        d.displayAccountName(),
        d.displayAccountId(),
        d.displaySequence(),
        d.funds().stream()
            .map(
                f ->
                    new FundLevelFundResponse(
                        f.fundCode(),
                        f.fundName(),
                        f.fundPrice(),
                        f.unitBalance(),
                        f.accountBalance(),
                        f.factsheetUrl(),
                        f.fxRate(),
                        f.subDisplayAccounts().stream()
                            .map(
                                s ->
                                    new FundLevelSubDisplayAccountResponse(
                                        s.subDisplayAccountName(),
                                        s.subDisplayAccountId(),
                                        s.unitBalance(),
                                        s.accountBalance(),
                                        s.fundPrice(),
                                        s.fxRate()))
                            .toList()))
            .toList());
  }

  private SubDisplayLevelDisplayAccountResponse subDisplay(SubDisplayLevelDisplayAccount d) {
    return new SubDisplayLevelDisplayAccountResponse(
        d.displayAccountName(),
        d.displayAccountId(),
        d.displaySequence(),
        d.subDisplayAccounts().stream()
            .map(
                s ->
                    new SubDisplayLevelSubDisplayAccountResponse(
                        s.subDisplayAccountName(),
                        s.subDisplayAccountId(),
                        s.funds().stream()
                            .map(
                                f ->
                                    new SubDisplayLevelFundResponse(
                                        f.fundCode(),
                                        f.fundName(),
                                        f.fundPrice(),
                                        f.unitBalance(),
                                        f.accountBalance(),
                                        f.factsheetUrl(),
                                        f.fxRate()))
                            .toList()))
            .toList());
  }

  private static String clean(String v) {
    return v == null || v.isBlank() ? null : v.trim();
  }
}
