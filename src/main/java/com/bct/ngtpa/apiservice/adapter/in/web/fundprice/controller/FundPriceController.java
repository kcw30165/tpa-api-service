package com.bct.ngtpa.apiservice.adapter.in.web.fundprice.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.mapper.FundPriceWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceQueryResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support.FundPriceSortingSupport;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceQueryResult;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRequestInvalidException;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceQueryUseCaseResult;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.support.FundPriceWorkbookExporter;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FundPriceExportResult;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceExportUseCaseResult;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.adapter.in.web.fundprice.response.FundPriceModeResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.fundprice.FetchFundPriceModeCommand;
import com.bct.ngtpa.apiservice.application.port.out.ReferenceDatePort;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCase;
import com.bct.ngtpa.apiservice.application.usecase.fundprice.FundPriceModeUseCaseResult;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import java.util.List;

/**
 * Query endpoint only, at this stage -- the export endpoint is DEFERRED to a later stage, once
 * {@code FundPriceWorkbookExporter} exists (mirroring how {@code ContributionSummaryWorkbookExporter}
 * is a genuinely separate concern from the web mapper). {@code exportUseCase} is wired now for
 * that upcoming stage, unused within this one.
 *
 * <p>Session-id cookie resolution mirrors {@code LogoutController}'s exact, already-proven
 * pattern (confirmed, script 116's investigation): {@code exchange.getRequest().getCookies()
 * .getFirst(name)}, then {@code new AuthenticatedSessionId(value.trim())} wrapped defensively.
 *
 * <p>FLAGGED, not resolved here: exact HTTP-status exception mapping for INVALID_DATE/malformed-
 * date/missing-cookie is not wired to this codebase's real {@code ApiExceptionHandler}
 * conventions -- correct failure BEHAVIOR (throwing) is implemented without guessing the specific
 * exception class the global handler expects; that wiring is a separate, deferred stage.
 */
@RestController
@RequestMapping("/api/v1")
public class FundPriceController {

  static final String EXCEL_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String EXPORT_FILE_NAME = "fund_price_info.xlsx";

  private final FundPriceQueryUseCase queryUseCase;
  private final FundPriceExportUseCase exportUseCase;
  private final FundPriceWebMapper webMapper;
  private final FundPriceSortingSupport sortingSupport;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final AuthenticatedSessionCookieWriter sessionCookies;
  private final DateTimeFormatter dateFormatter;
  private final FundPriceWorkbookExporter workbookExporter;
  private final FundPriceModeUseCase modeUseCase;
  private final ReferenceDatePort referenceDatePort;

  public FundPriceController(
      FundPriceQueryUseCase queryUseCase,
      FundPriceExportUseCase exportUseCase,
      FundPriceWebMapper webMapper,
      FundPriceSortingSupport sortingSupport,
      CurrentPortalAccessContextResolver contextResolver,
      AuthenticatedSessionCookieWriter sessionCookies,
      DateTimeFormatter fundPriceFullDateFormatter,
      FundPriceWorkbookExporter workbookExporter,
      FundPriceModeUseCase modeUseCase,
      ReferenceDatePort referenceDatePort) {
    this.queryUseCase = Objects.requireNonNull(queryUseCase);
    this.exportUseCase = Objects.requireNonNull(exportUseCase);
    this.webMapper = Objects.requireNonNull(webMapper);
    this.sortingSupport = Objects.requireNonNull(sortingSupport);
    this.contextResolver = Objects.requireNonNull(contextResolver);
    this.sessionCookies = Objects.requireNonNull(sessionCookies);
    this.dateFormatter = Objects.requireNonNull(fundPriceFullDateFormatter);
    this.workbookExporter = Objects.requireNonNull(workbookExporter);
    this.modeUseCase = Objects.requireNonNull(modeUseCase);
    this.referenceDatePort = Objects.requireNonNull(referenceDatePort);
  }

  @GetMapping(value = "/fund-price", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<FundPriceQueryResponse> getFundPrice(
      @RequestParam("date") String date,
      @RequestHeader(value = RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER, required = false)
          String acceptLanguage,
      ServerWebExchange exchange) {
    return Mono.defer(
        () -> {
          AuthenticatedSessionId sessionId = resolveSessionId(exchange);
          LocalDate parsedDate = parseDate(date);
          String lang = RequestLanguageResolver.normalize(acceptLanguage);
          return contextResolver
              .current()
              .flatMap(
                  ctx ->
                      queryUseCase.query(
                          sessionId,
                          parsedDate,
                          ctx.account().accountEnv(),
                          ctx.account().policyNo(),
                          ctx.actor().actorUserRole()))
              .flatMap(this::toQueryResultOrError)
              .map(sortingSupport::sort)
              .map(result -> webMapper.toQueryResponse(result, lang));
        });
  }

  private Mono<FundPriceQueryResult> toQueryResultOrError(FundPriceQueryUseCaseResult result) {
    return switch (result.outcome()) {
      case APPLIED -> Mono.just(result.queryResult());
      case INVALID_DATE ->
          Mono.error(new FundPriceRequestInvalidException("date is not a valid available fund price date"));
      case SYSTEM_FAILURE -> Mono.error(FundPriceRetrievalException.systemFailure());
    };
  }

  private AuthenticatedSessionId resolveSessionId(ServerWebExchange exchange) {
    var cookie = exchange.getRequest().getCookies().getFirst(sessionCookies.cookieName());
    if (cookie == null || cookie.getValue().isBlank()) {
      throw new FundPriceRequestInvalidException("Missing authenticated session cookie");
    }
    try {
      return new AuthenticatedSessionId(cookie.getValue().trim());
    } catch (IllegalArgumentException e) {
      throw new FundPriceRequestInvalidException("Invalid authenticated session cookie");
    }
  }

  private LocalDate parseDate(String date) {
    if (date == null || date.isBlank()) {
      throw new FundPriceRequestInvalidException("date is required");
    }
    try {
      return LocalDate.parse(date.trim(), dateFormatter);
    } catch (DateTimeParseException e) {
      throw new FundPriceRequestInvalidException("date must be in dd/MM/yyyy format");
    }
  }
  @GetMapping(value = "/fund-price/export", produces = EXCEL_MEDIA_TYPE)
  public Mono<ResponseEntity<byte[]>> exportFundPrice(ServerWebExchange exchange) {
    return Mono.defer(
        () -> {
          AuthenticatedSessionId sessionId = resolveSessionId(exchange);
          return contextResolver
              .current()
              .flatMap(
                  ctx ->
                      exportUseCase.export(
                          sessionId,
                          ctx.account().accountEnv(),
                          ctx.account().policyNo(),
                          ctx.account().trustCode(),
                          ctx.actor().actorUserRole()))
              .flatMap(this::toExportResultOrError)
              .map(workbookExporter::write)
              .map(
                  body ->
                      ResponseEntity.ok()
                          .contentType(org.springframework.http.MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                          .header(
                              HttpHeaders.CONTENT_DISPOSITION,
                              ContentDisposition.attachment()
                                  .filename(EXPORT_FILE_NAME)
                                  .build()
                                  .toString())
                          .body(body));
        });
  }

  @GetMapping(value = "/fund-price/mode", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<MutationResponse<FundPriceModeResultResponse>> getFundPriceMode(
      ServerWebExchange exchange) {
    return Mono.deferContextual(
        contextView -> {
          AuthenticatedSessionId sessionId = resolveSessionId(exchange);
          RequestHeaderContext headerContext =
              contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
          String requestId = headerContext == null ? null : headerContext.requestId();
          return contextResolver
              .current()
              .flatMap(
                  ctx ->
                      referenceDatePort
                          .resolveReferenceDate(ctx.account().accountEnv())
                          .flatMap(
                              referenceDate ->
                                  modeUseCase
                                      .fetchAndCacheMode(
                                          sessionId,
                                          new FetchFundPriceModeCommand(
                                              ctx.account().policyNo(),
                                              referenceDate,
                                              ctx.account().accountEnv(),
                                              ctx.actor().actorUserRole()),
                                          requestId)
                                      .flatMap(
                                          result -> toModeResultOrError(result, referenceDate))));
        });
  }

  private Mono<MutationResponse<FundPriceModeResultResponse>> toModeResultOrError(
      FundPriceModeUseCaseResult result, LocalDate referenceDate) {
    return switch (result.outcome()) {
      case APPLIED -> {
        MutationResponse<FundPriceModeResultResponse> response =
            MutationResponse.success(
                ApiStatus.SUCCESS,
                new FundPriceModeResultResponse(
                    webMapper.toModeResponse(result.metadata(), referenceDate)),
                List.of());
        yield Mono.just(response);
      }
      case SYSTEM_FAILURE -> Mono.error(FundPriceRetrievalException.systemFailure());
    };
  }

  private Mono<FundPriceExportResult> toExportResultOrError(FundPriceExportUseCaseResult result) {
    return switch (result.outcome()) {
      case APPLIED -> Mono.just(result.exportResult());
      case SYSTEM_FAILURE -> Mono.error(FundPriceRetrievalException.systemFailure());
      // INVALID_DATE is structurally unreachable for export -- FundPriceExportUseCase never
      // validates a candidate date (there is no FE-supplied date for export). Handled
      // defensively rather than silently ignored, since Java requires exhaustive switch
      // coverage over the shared FundPriceUseCaseOutcome enum.
      case INVALID_DATE -> Mono.error(FundPriceRetrievalException.systemFailure());
    };
  }
}