package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * {@code POST /api/v1/fund-comparison/options}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.2.
 *
 * <p>Mirrors {@code FundFactSheetController}'s exact confirmed pattern (script 042 evidence): no
 * request body; policyNo and accountEnv come from the trusted {@code
 * PortalAccessContext.AccountContext}, actorUserRole from {@code PortalAccessContext.ActorContext},
 * and the language from {@code Accept-Language}. Session, CSRF and {@code Account-Ref} checks stay
 * in the existing web filter.
 *
 * <p>Unlike {@code FundFactSheetController}, this controller has NO separate web mapper dependency:
 * {@link FundComparisonOptionsUseCase#getOptions} already returns the final public {@link
 * FundComparisonOptionsResponse} (the mapper runs inside the use case, confirmed sub-tasks 2.2/2.3)
 * -- this controller only wraps that result under {@code result.data} via {@link
 * FundComparisonOptionsResultResponse} and {@link MutationResponse}.
 */
@RestController
@RequestMapping("/api/v1")
public class FundComparisonOptionsController {

  private final FundComparisonOptionsUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;

  public FundComparisonOptionsController(
      FundComparisonOptionsUseCase useCase, CurrentPortalAccessContextResolver contextResolver) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
  }

  @PostMapping(value = "/fund-comparison/options", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundComparisonOptionsResultResponse>> getOptions(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        view -> {
          RequestHeaderContext headers =
              view.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          return contextResolver
              .current()
              .flatMap(context -> useCase.getOptions(commandFrom(context), language))
              .map(FundComparisonOptionsController::success);
        });
  }

  private static FetchFundComparisonOptionsCommand commandFrom(PortalAccessContext context) {
    var account = context.account();
    return new FetchFundComparisonOptionsCommand(
        account.policyNo(), account.accountEnv(), context.actor().actorUserRole());
  }

  private static MutationResponse<FundComparisonOptionsResultResponse> success(
      FundComparisonOptionsResponse result) {
    return MutationResponse.success(
        ApiStatus.SUCCESS, new FundComparisonOptionsResultResponse(result), List.of());
  }
}
