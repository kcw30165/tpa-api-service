package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1")
public final class InvestmentChoiceController {

  private final GetInvestmentChoiceUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final AuthenticatedSessionCookieReader sessionCookieReader;
  private final InvestmentChoiceWebMapper webMapper;

  public InvestmentChoiceController(
      GetInvestmentChoiceUseCase useCase,
      CurrentPortalAccessContextResolver contextResolver,
      AuthenticatedSessionCookieReader sessionCookieReader,
      InvestmentChoiceWebMapper webMapper) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.sessionCookieReader =
        Objects.requireNonNull(sessionCookieReader, "sessionCookieReader must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
  }

  @GetMapping(value = "/investment-choice", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MutationResponse<InvestmentChoiceResultResponse>>> getInvestmentChoice(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage,
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        contextView -> {
          RequestHeaderContext headers =
              contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          String requestId = headers == null ? null : headers.requestId();
          Optional<AuthenticatedSessionId> sessionId = sessionCookieReader.read(exchange);
          return contextResolver
              .current()
              .flatMap(
                  context -> {
                    String accountRef = requireAccountRef(context);
                    if (sessionId.isEmpty()) {
                      return Mono.error(new AuthenticatedPortalContextResolutionException());
                    }
                    return useCase
                        .execute(commandFrom(context, accountRef, sessionId.get(), requestId))
                        .map(
                            result -> {
                              MutationResponse<InvestmentChoiceResultResponse> response =
                                  MutationResponse.success(
                                      ApiStatus.SUCCESS,
                                      new InvestmentChoiceResultResponse(
                                          webMapper.toDataResponse(result, language)),
                                      List.of());
                              return ResponseEntity.status(
                                      HttpStatusClassification.classify(response.status()))
                                  .body(response);
                            });
                  });
        });
  }

  private static GetInvestmentChoiceCommand commandFrom(
      PortalAccessContext context,
      String accountRef,
      AuthenticatedSessionId sessionId,
      String requestId) {
    var account = context.account();
    return new GetInvestmentChoiceCommand(
        context.actor().actorUserRole(),
        account.accountEnv(),
        account.policyNo(),
        account.certNo(),
        accountRef,
        sessionId,
        requestId);
  }

  private static String requireAccountRef(PortalAccessContext context) {
    if (context == null
        || context.account() == null
        || !StringUtils.hasText(context.account().accountRef())) {
      throw new PortalAccessContextResolutionException(
          ErrorCodes.MEMBER_CONTEXT_INVALID, "Missing Account-Ref header for selected-account API");
    }
    return context.account().accountRef();
  }
}
