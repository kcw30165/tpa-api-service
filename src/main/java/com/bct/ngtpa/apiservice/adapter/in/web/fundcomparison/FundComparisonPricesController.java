package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.mapper.FundComparisonPricesWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.request.FundComparisonPricesRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonPricesResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison.response.FundComparisonPricesResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FetchFundComparisonPricesUseCaseCommand;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesResult;
import com.bct.ngtpa.apiservice.application.dto.fundcomparison.FundComparisonPricesValidatedRequest;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonSelectedPricesUseCase;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@code POST /api/v1/fund-comparison/prices}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.3.
 *
 * <p>policyNo, accountEnv and accountRef come from the trusted {@code PortalAccessContext},
 * actorUserRole from its actor. No language is resolved: the prices response has no localized
 * fields.
 *
 * <p>Uses {@code @RequestBody(required = false)} so a missing or malformed body reaches {@link
 * FundComparisonPricesWebMapper#validate} as {@code null} and produces the project's own {@code
 * err.fund-comparison.request.invalid} envelope. Validation runs before context resolution, so an
 * invalid request never reaches the use case.
 *
 * <p>Sub-task 5.4: the validated request is checked against the session-cached options by {@link
 * FundComparisonSelectedPricesUseCase}; the session id comes from {@link
 * AuthenticatedSessionCookieReader} and the request id from {@link RequestHeaderContext}.
 */
@RestController
@RequestMapping("/api/v1")
public class FundComparisonPricesController {

  private final FundComparisonSelectedPricesUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final FundComparisonPricesWebMapper webMapper;
  private final AuthenticatedSessionCookieReader sessionCookieReader;

  public FundComparisonPricesController(
      FundComparisonSelectedPricesUseCase useCase,
      CurrentPortalAccessContextResolver contextResolver,
      FundComparisonPricesWebMapper webMapper,
      AuthenticatedSessionCookieReader sessionCookieReader) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
    this.sessionCookieReader =
        Objects.requireNonNull(sessionCookieReader, "sessionCookieReader must not be null");
  }

  @PostMapping(value = "/fund-comparison/prices", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundComparisonPricesResultResponse>> getPrices(
      @RequestBody(required = false) FundComparisonPricesRequest request,
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        view -> {
          RequestHeaderContext headers =
              view.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String requestId = headers == null ? null : headers.requestId();
          return Mono.defer(() -> Mono.just(webMapper.validate(request)))
              .flatMap(
                  validated ->
                      contextResolver
                          .current()
                          .flatMap(
                              context ->
                                  useCase.getPrices(
                                      commandFrom(validated, context),
                                      sessionCookieReader.read(exchange),
                                      context.account().accountRef(),
                                      requestId)))
              .map(this::success);
        });
  }

  private static FetchFundComparisonPricesUseCaseCommand commandFrom(
      FundComparisonPricesValidatedRequest validated, PortalAccessContext context) {
    var account = context.account();
    return new FetchFundComparisonPricesUseCaseCommand(
        account.policyNo(),
        account.accountEnv(),
        context.actor().actorUserRole(),
        validated.fundCodes(),
        validated.timeRange());
  }

  private MutationResponse<FundComparisonPricesResultResponse> success(
      FundComparisonPricesResult result) {
    FundComparisonPricesResponse response = webMapper.toResponse(result);
    return MutationResponse.success(
        ApiStatus.SUCCESS, new FundComparisonPricesResultResponse(response), List.of());
  }
}
