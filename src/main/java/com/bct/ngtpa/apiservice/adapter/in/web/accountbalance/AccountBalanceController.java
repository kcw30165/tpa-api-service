package com.bct.ngtpa.apiservice.adapter.in.web.accountbalance;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountBalanceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
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

  @GetMapping(value = "/account-balance", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<AccountBalanceResultResponse>> getAccountBalance(
      @RequestParam(value = "currency", required = false) String currency,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        contextView -> {
          RequestHeaderContext headers =
              contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          return currentPortalAccessContextResolver
              .current()
              .flatMap(context -> invoke(context, language, clean(currency)));
        });
  }

  private Mono<MutationResponse<AccountBalanceResultResponse>> invoke(
      PortalAccessContext context, String language, String currency) {
    AccountContext account = context.account();
    ActorContext actor = context.actor();
    GetAccountBalanceCommand command =
        new GetAccountBalanceCommand(
            language,
            currency,
            account.accountEnv(),
            account.policyNo(),
            account.certNo(),
            account.trustCode(),
            account.schemeType(),
            actor.actorUserId(),
            actor.actorUserRole());
    return getAccountBalanceUseCase.get(command).map(this::success);
  }

  private MutationResponse<AccountBalanceResultResponse> success(AccountBalanceResult result) {
    CurrencySelectionResponse currencies =
        new CurrencySelectionResponse(
            result.currencySelection().selectedCurrency(),
            result.currencySelection().availableCurrencies());
    AccountOverviewResponse overview =
        new AccountOverviewResponse(
            result.accountOverview().displayAccountLabel(),
            result.accountOverview().vestedBalance(),
            result.accountOverview().pendingSwitchIn());
    List<InvestmentPortfolioItemResponse> portfolio =
        result.investmentPortfolio().stream()
            .map(
                item ->
                    new InvestmentPortfolioItemResponse(
                        item.subDisplayAccountLabel(),
                        item.fundName(),
                        item.fxRate(),
                        item.factsheetUrl()))
            .toList();
    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new AccountBalanceResultResponse(
            new AccountBalanceDataResponse(result.asOfDate(), currencies, overview, portfolio)),
        List.of());
  }

  private static String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
