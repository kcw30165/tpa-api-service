package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet;

import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.mapper.FundFactSheetWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response.FundFactSheetResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.GetFundFactSheetCommand;
import com.bct.ngtpa.apiservice.application.port.in.GetFundFactSheetUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * {@code GET /api/v1/fund-fact-sheet}: the fund fact sheet of the selected account.
 *
 * <p>The endpoint has no parameters. The policy number, certificate number, scheme type, account
 * environment and actor role come only from the trusted portal context, and the language from
 * {@code Accept-Language}. Session, CSRF and {@code Account-Ref} checks stay in the existing web
 * filter.
 */
@RestController
@RequestMapping("/api/v1")
public class FundFactSheetController {

  private final GetFundFactSheetUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final FundFactSheetWebMapper webMapper;

  public FundFactSheetController(
      GetFundFactSheetUseCase useCase,
      CurrentPortalAccessContextResolver contextResolver,
      FundFactSheetWebMapper webMapper) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
  }

  @GetMapping(value = "/fund-fact-sheet", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundFactSheetResultResponse>> getFundFactSheet(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage) {
    return Mono.deferContextual(
        view -> {
          RequestHeaderContext headers =
              view.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          return contextResolver
              .current()
              .flatMap(context -> useCase.get(commandFrom(context)))
              .map(result -> success(result, language));
        });
  }

  private static GetFundFactSheetCommand commandFrom(
      com.bct.ngtpa.apiservice.application.dto.PortalAccessContext context) {
    var account = context.account();
    return new GetFundFactSheetCommand(
        account.accountEnv(),
        account.policyNo(),
        account.certNo(),
        account.schemeType(),
        context.actor().actorUserRole());
  }

  private MutationResponse<FundFactSheetResultResponse> success(
      FundFactSheetResult result, String language) {
    return MutationResponse.success(
        ApiStatus.SUCCESS, webMapper.toResultResponse(result, language), List.of());
  }
}
