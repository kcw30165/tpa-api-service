package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceCommand;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.IssuePasswordValidationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Map;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(PasswordValidationRouteMatcher.ISSUANCE_PATH)
public final class PasswordValidationController {
  private static final String REQUEST_INVALID = "REQUEST_INVALID";
  private final IssuePasswordValidationUseCase useCase;
  private final CurrentPortalAccessContextResolver contexts;
  private final AuthenticatedSessionCookieReader cookies;
  private final PasswordValidationRequestParser parser;
  private final PasswordValidationWebMapper mapper;
  private final FailureEventPort failures;

  public PasswordValidationController(
      IssuePasswordValidationUseCase useCase,
      CurrentPortalAccessContextResolver contexts,
      AuthenticatedSessionCookieReader cookies,
      PasswordValidationRequestParser parser,
      PasswordValidationWebMapper mapper,
      FailureEventPort failures) {
    this.useCase = useCase;
    this.contexts = contexts;
    this.cookies = cookies;
    this.parser = parser;
    this.mapper = mapper;
    this.failures = failures;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MutationResponse<PasswordValidationResult>>> issue(
      @RequestBody(required = false) Mono<String> raw, ServerWebExchange exchange) {
    return contexts
        .current()
        .flatMap(
            context -> {
              var sessionId = cookies.read(exchange);
              if (sessionId.isEmpty()) {
                publishFailure(exchange, "SESSION_NOT_USABLE", null);
                return Mono.just(mapper.authenticationRequired());
              }
              return raw.defaultIfEmpty("")
                  .flatMap(
                      body -> {
                        var contentType = exchange.getRequest().getHeaders().getContentType();
                        if (contentType == null
                            || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
                          publishFailure(exchange, REQUEST_INVALID, "PAGE");
                          return Mono.just(mapper.invalid("PAGE"));
                        }
                        var parsed = parser.parse(body);
                        String correlationId =
                            exchange.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
                        return useCase
                            .issue(
                                new PasswordValidationIssuanceCommand(
                                    sessionId.get(),
                                    context,
                                    parsed.password(),
                                    parsed.purpose(),
                                    parsed.binding(),
                                    correlationId))
                            .map(result -> mapper.toResponse(result, exchange.getResponse()));
                      });
            })
        .onErrorResume(
            PasswordValidationRequestInvalidException.class,
            failure -> {
              publishFailure(exchange, REQUEST_INVALID, failure.target());
              return Mono.just(mapper.invalid(failure.target()));
            })
        .onErrorResume(
            PortalAccessContextResolutionException.class,
            failure -> Mono.just(mapper.missingContext()))
        .onErrorResume(
            failure ->
                failure instanceof DecodingException
                    || failure instanceof DataBufferLimitException
                    || failure instanceof ServerWebInputException
                    || failure instanceof ContentTooLargeException,
            failure -> {
              publishFailure(exchange, REQUEST_INVALID, "PAGE");
              return Mono.just(mapper.invalid("PAGE"));
            })
        .onErrorReturn(mapper.unavailable());
  }

  private void publishFailure(ServerWebExchange exchange, String reason, String target) {
    var dimensions = new java.util.LinkedHashMap<String, Object>();
    dimensions.put("stage", "issuance");
    dimensions.put("reason", reason);
    if (target != null) dimensions.put("target", target);
    try {
      failures.publish(
          new FailureEvent(
              "password-validation.issue.rejected",
              exchange.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY),
              "password-validation",
              "issue",
              reason.equals(REQUEST_INVALID)
                  ? FailureOrigin.LOCAL_VALIDATION
                  : FailureOrigin.LOCAL_BUSINESS,
              reason,
              reason.equals(REQUEST_INVALID)
                  ? ErrorCodes.PASSWORD_VALIDATION_REQUEST_INVALID
                  : null,
              reason.equals(REQUEST_INVALID) ? 400 : 401,
              false,
              Map.copyOf(dimensions)));
    } catch (RuntimeException ignored) {
      // Telemetry must not change the issuance response.
    }
  }
}
