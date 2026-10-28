package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.passwordvalidation.PasswordValidationIssuanceCommand;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.application.port.in.IssuePasswordValidationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/password-validation")
public final class PasswordValidationController {
  private final IssuePasswordValidationUseCase useCase;
  private final CurrentPortalAccessContextResolver contexts;
  private final AuthenticatedSessionCookieReader cookies;
  private final PasswordValidationRequestParser parser;
  private final PasswordValidationWebMapper mapper;

  public PasswordValidationController(
      IssuePasswordValidationUseCase useCase,
      CurrentPortalAccessContextResolver contexts,
      AuthenticatedSessionCookieReader cookies,
      PasswordValidationRequestParser parser,
      PasswordValidationWebMapper mapper) {
    this.useCase = useCase;
    this.contexts = contexts;
    this.cookies = cookies;
    this.parser = parser;
    this.mapper = mapper;
  }

  @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MutationResponse<PasswordValidationResult>>> issue(
      @RequestBody(required = false) Mono<String> raw, ServerWebExchange exchange) {
    return contexts
        .current()
        .flatMap(
            context -> {
              var sessionId = cookies.read(exchange);
              if (sessionId.isEmpty()) return Mono.just(mapper.authenticationRequired());
              return raw.defaultIfEmpty("")
                  .flatMap(
                      body -> {
                        var contentType = exchange.getRequest().getHeaders().getContentType();
                        if (contentType == null
                            || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) {
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
            failure -> Mono.just(mapper.invalid(failure.target())))
        .onErrorResume(
            PortalAccessContextResolutionException.class,
            failure -> Mono.just(mapper.missingContext()))
        .onErrorResume(
            failure ->
                failure instanceof DecodingException
                    || failure instanceof DataBufferLimitException
                    || failure instanceof ServerWebInputException
                    || failure instanceof ContentTooLargeException,
            failure -> Mono.just(mapper.invalid("PAGE")))
        .onErrorReturn(mapper.unavailable());
  }
}
