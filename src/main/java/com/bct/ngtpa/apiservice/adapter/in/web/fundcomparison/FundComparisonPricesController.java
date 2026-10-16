package com.bct.ngtpa.apiservice.adapter.in.web.fundcomparison;

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
import com.bct.ngtpa.apiservice.application.usecase.fundcomparison.FundComparisonPricesUseCase;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * {@code POST /api/v1/fund-comparison/prices}, per
 * docs/design/13-fund-comparison/02-api-contract.md section 2.3 (Milestone 3, sub-task 3.4 -- the
 * final sub-task of Milestone 3).
 *
 * <p>Mirrors {@code FundComparisonOptionsController}'s confirmed context-resolution pattern
 * (policyNo and accountEnv from the trusted {@code PortalAccessContext.AccountContext},
 * actorUserRole from {@code PortalAccessContext.ActorContext}). Unlike the options controller, this
 * one does NOT resolve {@code Accept-Language}: the prices response ({@code fundCode}, {@code
 * priceDate}, {@code unitPrice}) has no localized fields, so there is nothing for a language to
 * select.
 *
 * <p>Uses {@code @RequestBody(required = false)} rather than the Spring default ({@code required =
 * true}) so that a missing or malformed body reaches {@link FundComparisonPricesWebMapper#validate}
 * as a {@code null} request (producing the project's own {@code
 * err.fund-comparison.request.invalid} envelope, confirmed by {@code
 * FundComparisonPricesWebMapperTest#nullRequestThrowsRequestInvalid}, sub-task 3.2) rather than a
 * generic Spring-framework-level 400 that would bypass the established error-code contract.
 * Validation runs inside {@link Mono#defer}, converting a thrown {@code
 * FundComparisonRequestInvalidException} into a normal {@code onError} signal (handled by the
 * already-wired {@code ApiExceptionHandler}) -- and, since validation precedes {@code
 * contextResolver.current()} in the reactive chain, an invalid request never triggers a context
 * resolution or use-case call.
 */
@RestController
@RequestMapping("/api/v1")
public class FundComparisonPricesController {

  private final FundComparisonPricesUseCase useCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final FundComparisonPricesWebMapper webMapper;

  public FundComparisonPricesController(
      FundComparisonPricesUseCase useCase,
      CurrentPortalAccessContextResolver contextResolver,
      FundComparisonPricesWebMapper webMapper) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
  }

  @PostMapping(value = "/fund-comparison/prices", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundComparisonPricesResultResponse>> getPrices(
      @RequestBody(required = false) FundComparisonPricesRequest request) {
    return Mono.defer(() -> Mono.just(webMapper.validate(request)))
        .flatMap(
            validated ->
                contextResolver
                    .current()
                    .flatMap(context -> useCase.getPrices(commandFrom(validated, context))))
        .map(this::success);
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
