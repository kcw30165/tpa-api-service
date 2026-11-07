package com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.investmentchoice.response.InvestmentChoiceSubmissionResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.HttpStatusClassification;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.support.RequestLanguageResolver;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.GetInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceInstructionRequest;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceSubmitOutcome;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.InvestmentChoiceViolation;
import com.bct.ngtpa.apiservice.application.dto.investmentchoice.SubmitInvestmentChoiceCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.GetInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.in.SubmitInvestmentChoiceUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public final class InvestmentChoiceController {

  private static final String ERROR_SOURCE = "SERVER";

  private final GetInvestmentChoiceUseCase useCase;
  private final SubmitInvestmentChoiceUseCase submitUseCase;
  private final CurrentPortalAccessContextResolver contextResolver;
  private final AuthenticatedSessionCookieReader sessionCookieReader;
  private final InvestmentChoiceWebMapper webMapper;
  private final ObjectMapper objectMapper;
  private final InvestmentChoiceInstructionBodyValidator bodyValidator =
      new InvestmentChoiceInstructionBodyValidator();

  public InvestmentChoiceController(
      GetInvestmentChoiceUseCase useCase,
      SubmitInvestmentChoiceUseCase submitUseCase,
      CurrentPortalAccessContextResolver contextResolver,
      AuthenticatedSessionCookieReader sessionCookieReader,
      InvestmentChoiceWebMapper webMapper,
      ObjectMapper objectMapper) {
    this.useCase = Objects.requireNonNull(useCase, "useCase must not be null");
    this.submitUseCase = Objects.requireNonNull(submitUseCase, "submitUseCase must not be null");
    this.contextResolver =
        Objects.requireNonNull(contextResolver, "contextResolver must not be null");
    this.sessionCookieReader =
        Objects.requireNonNull(sessionCookieReader, "sessionCookieReader must not be null");
    this.webMapper = Objects.requireNonNull(webMapper, "webMapper must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
                        .get(commandFrom(context, accountRef, sessionId.get(), requestId))
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

  @PostMapping(
      value = "/investment-choice/instructions",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MutationResponse<InvestmentChoiceSubmissionResultResponse>>>
      submitInvestmentChoice(
          @RequestBody(required = false) Mono<JsonNode> requestBody, ServerWebExchange exchange) {
    return Mono.deferContextual(
        contextView -> {
          RequestHeaderContext headers =
              contextView.getOrDefault(RequestHeaderContextKeys.CONTEXT_KEY, null);
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
                    return requestBody
                        .map(this::toValidationTree)
                        .defaultIfEmpty(NullNode.getInstance())
                        .flatMap(
                            body -> {
                              var validation = bodyValidator.validate(body);
                              if (!validation.isValid()) {
                                return Mono.just(
                                    submitResponse(
                                        exchange,
                                        MutationResponse.failure(
                                            ApiStatus.VALIDATION_FAILED,
                                            validationErrors(validation.violations()))));
                              }
                              SubmitInvestmentChoiceCommand command =
                                  submitCommandFrom(
                                      context,
                                      accountRef,
                                      sessionId.get(),
                                      requestId,
                                      validation.request());
                              return submitUseCase
                                  .submit(command)
                                  .map(
                                      outcome ->
                                          submitResponse(exchange, submissionResponse(outcome)));
                            });
                  });
        });
  }

  private com.fasterxml.jackson.databind.JsonNode toValidationTree(JsonNode body) {
    try {
      return objectMapper.readTree(body.toString());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to convert decoded investment choice request", exception);
    }
  }

  private MutationResponse<InvestmentChoiceSubmissionResultResponse> submissionResponse(
      InvestmentChoiceSubmitOutcome outcome) {
    if (outcome instanceof InvestmentChoiceSubmitOutcome.Submitted(var submission)) {
      return MutationResponse.success(
          ApiStatus.SUBMITTED, webMapper.toSubmissionResultResponse(submission), List.of());
    }
    if (outcome instanceof InvestmentChoiceSubmitOutcome.Invalid(var violations)) {
      return MutationResponse.failure(ApiStatus.VALIDATION_FAILED, validationErrors(violations));
    }
    throw new IllegalStateException("Unsupported investment choice submit outcome");
  }

  private static List<ApiError> validationErrors(List<InvestmentChoiceViolation> violations) {
    return violations.stream()
        .map(
            violation ->
                switch (violation.kind()) {
                  case FIELD ->
                      ApiError.field(violation.code(), null, violation.targets(), ERROR_SOURCE);
                  case CROSS_FIELD ->
                      ApiError.crossField(
                          violation.code(), null, violation.targets(), ERROR_SOURCE);
                  case FORM ->
                      ApiError.form(violation.code(), null, violation.targets(), ERROR_SOURCE);
                })
        .toList();
  }

  private static ResponseEntity<MutationResponse<InvestmentChoiceSubmissionResultResponse>>
      submitResponse(
          ServerWebExchange exchange,
          MutationResponse<InvestmentChoiceSubmissionResultResponse> response) {
    var status =
        response.status() == ApiStatus.VALIDATION_FAILED
            ? HttpStatus.UNPROCESSABLE_CONTENT
            : HttpStatusClassification.classify(response.status());
    exchange.getResponse().setStatusCode(status);
    return ResponseEntity.status(status).body(response);
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

  private static SubmitInvestmentChoiceCommand submitCommandFrom(
      PortalAccessContext context,
      String snapshotAccountRef,
      AuthenticatedSessionId sessionId,
      String requestId,
      InvestmentChoiceInstructionRequest request) {
    var account = context.account();
    return new SubmitInvestmentChoiceCommand(
        context.actor().actorUserId(),
        context.actor().actorUserRole(),
        account.accountEnv(),
        account.policyNo(),
        account.certNo(),
        snapshotAccountRef,
        sessionId,
        requestId,
        request);
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
