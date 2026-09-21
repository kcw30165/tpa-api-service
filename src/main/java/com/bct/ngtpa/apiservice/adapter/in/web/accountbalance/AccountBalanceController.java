package com.bct.ngtpa.apiservice.adapter.in.web.accountbalance;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountBalanceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountBalanceController {
  private final GetAccountBalanceUseCase getAccountBalanceUseCase;
  private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;

  @GetMapping(value = "/account/balance", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<AccountBalanceResultResponse>> getAccountBalance(
      @RequestParam(value = "currency", required = false) String currency,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        v -> {
          RequestHeaderContext h = v.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String l = RequestLanguageResolver.resolve(h, acceptLanguage, "en");
          return currentPortalAccessContextResolver
              .current()
              .flatMap(c -> invoke(c, l, clean(currency)));
        });
  }

  private Mono<MutationResponse<AccountBalanceResultResponse>> invoke(
      PortalAccessContext c, String l, String currency) {
    var a = c.account();
    var x = c.actor();
    return getAccountBalanceUseCase
        .get(
            new GetAccountBalanceCommand(
                l,
                currency,
                a.accountEnv(),
                a.policyNo(),
                a.certNo(),
                a.trustCode(),
                a.schemeType(),
                x.actorUserId(),
                x.actorUserRole()))
        .map(this::success);
  }

  private MutationResponse<AccountBalanceResultResponse> success(AccountBalanceResult r) {
    var c =
        new CurrencySelectionResponse(
            r.currencySelection().selectedCurrency(), r.currencySelection().availableCurrencies());
    var o =
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
    var p =
        new InvestmentPortfolioResponse(
            r.investmentPortfolio().fundLevelView().stream().map(this::fundDisplay).toList(),
            r.investmentPortfolio().subDisplayAccountLevelView().stream()
                .map(this::subDisplay)
                .toList());
    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new AccountBalanceResultResponse(new AccountBalanceDataResponse(r.asOfDate(), c, o, p)),
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
