package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonOptionsMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonOptionsResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonOptionsCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.RawFundComparisonOptionsData;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonOptionsUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@code POST /api/v1/fund-comparison/options}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.2.
 *
 * <p>No request body; policyNo, accountEnv and accountRef come from the trusted {@code
 * PortalAccessContext}, actorUserRole from its actor, and the language from {@code
 * Accept-Language}. Session, CSRF and {@code Account-Ref} checks stay in the existing web filter.
 *
 * <p>Sub-task 5.3: the session id is read through {@link AuthenticatedSessionCookieReader} and the
 * request id from {@link RequestHeaderContext}, and both are handed to the use case so the fetched
 * options are cached in the session (09 section 9.5.3). A missing cookie only skips caching. The
 * controller still owns the mapping to the public response (script 047).
 */
@RestController
@RequestMapping("/api/v1")
public class FundComparisonOptionsController {

  private final FundComparisonOptionsUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final FundComparisonOptionsMapper webMapper;
  private final AuthenticatedSessionCookieReader sessionCookieReader;

  public FundComparisonOptionsController(
      FundComparisonOptionsUseCase useCase,
      CurrentPortalAccessContextResolver contextResolver,
      FundComparisonOptionsMapper webMapper,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
    this.sessionCookieReader =
        Objects.requireNonNull(sessionCookieReader, "sessionCookieReader must not be null");
  }

  @PostMapping(value = "/fund-comparison/options", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundComparisonOptionsResultResponse>> getOptions(
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage,
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        view -> {
          RequestHeaderContext headers =
              view.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String language = RequestLanguageResolver.resolve(headers, acceptLanguage, "en");
          String requestId = headers == null ? null : headers.requestId();
          Optional<AuthenticatedSessionId> sessionId = sessionCookieReader.read(exchange);
          return contextResolver
              .current()
              .flatMap(
                  context ->
                      useCase.getOptionsAndCache(
                          commandFrom(context),
                          sessionId,
                          context.account().accountRef(),
                          requestId))
              .map(raw -> success(raw, language));
        });
  }

  private static FetchFundComparisonOptionsCommand commandFrom(PortalAccessContext context) {
    var account = context.account();
    return new FetchFundComparisonOptionsCommand(
        account.policyNo(), account.accountEnv(), context.actor().actorUserRole());
  }

  private MutationResponse<FundComparisonOptionsResultResponse> success(
      RawFundComparisonOptionsData raw, String language) {
    FundComparisonOptionsResponse result = webMapper.toResponse(raw, language);
    return MutationResponse.success(
        ApiStatus.SUCCESS, new FundComparisonOptionsResultResponse(result), List.of());
  }
}
