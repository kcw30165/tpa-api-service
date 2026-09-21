package com.bct.ngtpa.apiservice.adapter.in.web.accountgainloss;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountgainloss.*;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.*;
import com.bct.ngtpa.apiservice.application.port.in.GetAccountGainLossUseCase;
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
public class AccountGainLossController {
  private final GetAccountGainLossUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;

  @GetMapping(value = "/account/gain-loss", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<AccountGainLossResultResponse>> getAccountGainLoss(
      @RequestParam(value = "period", required = false, defaultValue = "SINCE_SCHEME_INCEPTION")
          AccountGainLossPeriod period,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        view -> {
          RequestHeaderContext headers =
              view.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          return contextResolver.current().flatMap(c -> invoke(c, language, period));
        });
  }

  private Mono<MutationResponse<AccountGainLossResultResponse>> invoke(
      PortalAccessContext context, String language, AccountGainLossPeriod period) {
    var account = context.account();
    var actor = context.actor();
    return useCase
        .get(
            new GetAccountGainLossCommand(
                language,
                period,
                account.accountEnv(),
                account.policyNo(),
                account.certNo(),
                account.trustCode(),
                account.schemeType(),
                actor.actorUserId(),
                actor.actorUserRole()))
        .map(this::success);
  }

  private MutationResponse<AccountGainLossResultResponse> success(AccountGainLossResult result) {
    var funds =
        result.funds().stream()
            .map(
                f ->
                    new AccountGainLossFundResponse(
                        f.fundCode(), f.fundName(), f.fundSequence(), f.gainLoss()))
            .toList();
    var data =
        new AccountGainLossDataResponse(
            result.period(),
            result.asOfDate(),
            new AccountGainLossOverviewResponse(result.totalGainLoss()),
            new AccountGainLossFundBreakdownResponse(funds.size(), funds));
    return MutationResponse.success(
        ApiStatus.SUCCESS, new AccountGainLossResultResponse(data), List.of());
  }
}
