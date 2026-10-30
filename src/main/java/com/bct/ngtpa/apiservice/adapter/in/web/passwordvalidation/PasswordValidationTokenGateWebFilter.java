package com.bct.ngtpa.apiservice.adapter.in.web.passwordvalidation;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.session.AuthenticatedSessionCookieReader;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedAccountRef;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import com.bct.ngtpa.apiservice.application.port.out.OperationPayloadBindingPort;
import com.bct.ngtpa.apiservice.application.usecase.passwordvalidation.PasswordValidationGrantService;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PasswordValidationPurpose;
import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public final class PasswordValidationTokenGateWebFilter implements WebFilter {
  static final String TOKEN_HEADER = "X-Password-Validation-Token";
  static final String BINDING_ATTRIBUTE =
      PasswordValidationTokenGateWebFilter.class.getName() + ".binding";
  private static final PasswordValidationGrantService.ConsumeResult UNAVAILABLE =
      new PasswordValidationGrantService.ConsumeResult(
          PasswordValidationGrantService.Status.UNAVAILABLE, null, "SYSTEM_FAILURE");
  private final PasswordValidationGrantService grants;
  private final AuthenticatedSessionCookieReader cookies;
  private final OperationPayloadBindingPort bindings;
  private final ServerCodecConfigurer codecs;
  private final ErrorResponseRenderer renderer;
  private final ObjectMapper json;
  private final FailureEventPort failures;

  public PasswordValidationTokenGateWebFilter(
      PasswordValidationGrantService grants,
      AuthenticatedSessionCookieReader cookies,
      OperationPayloadBindingPort bindings,
      ServerCodecConfigurer codecs,
      ErrorResponseRenderer renderer,
      ObjectMapper json,
      FailureEventPort failures) {
    this.grants = grants;
    this.cookies = cookies;
    this.bindings = bindings;
    this.codecs = codecs;
    this.renderer = renderer;
    this.json = json;
    this.failures = failures;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!HttpMethod.PUT.equals(exchange.getRequest().getMethod())
        || !PasswordValidationRouteMatcher.matches(exchange.getRequest())) {
      return chain.filter(exchange);
    }
    exchange.getResponse().getHeaders().setCacheControl("no-store");
    String accountRef =
        exchange.getRequest().getHeaders().getFirst(RequestHeaderContextKeys.ACCOUNT_REF_HEADER);
    if (!StringUtils.hasText(accountRef)) {
      return missingAccountRef(exchange);
    }
    PortalAccessContext context = exchange.getAttribute(PortalAccessContextKeys.ATTRIBUTE_KEY);
    var sessionId = cookies.read(exchange);
    if (!hasAuthenticatedContext(context, accountRef, sessionId.isPresent())) {
      return missingAuthenticatedContext(exchange);
    }
    var tokens = exchange.getRequest().getHeaders().get(TOKEN_HEADER);
    String tokenHeaderRejection = tokenHeaderRejectionReason(tokens);
    if (tokenHeaderRejection != null) {
      return rejected(exchange, tokenHeaderRejection);
    }
    return consumeAndDispatch(exchange, chain, context, sessionId.get(), tokens.getFirst());
  }

  private Mono<Void> missingAccountRef(ServerWebExchange exchange) {
    return failure(
        exchange,
        HttpStatus.BAD_REQUEST,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.MEMBER_CONTEXT_INVALID,
        List.of(),
        "ACCOUNT_REF_MISSING");
  }

  private Mono<Void> missingAuthenticatedContext(ServerWebExchange exchange) {
    return failure(
        exchange,
        HttpStatus.UNAUTHORIZED,
        ApiStatus.BUSINESS_REJECTED,
        "AUTHENTICATION_REQUIRED",
        ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
        List.of("PAGE"),
        "CONTEXT_MISSING_OR_MISMATCHED");
  }

  private static boolean hasAuthenticatedContext(
      PortalAccessContext context, String accountRef, boolean hasSessionId) {
    return context != null
        && context.actor() != null
        && StringUtils.hasText(context.actor().actorUserId())
        && context.actor().actorUserRole() != null
        && context.account() != null
        && accountRef.trim().equals(context.account().accountRef())
        && hasSessionId;
  }

  private static String tokenHeaderRejectionReason(List<String> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      return "TOKEN_HEADER_MISSING";
    }
    if (tokens.size() != 1) {
      return "TOKEN_HEADER_DUPLICATE";
    }
    if (!StringUtils.hasText(tokens.getFirst())) {
      return "TOKEN_HEADER_BLANK";
    }
    return null;
  }

  private Mono<Void> consumeAndDispatch(
      ServerWebExchange exchange,
      WebFilterChain chain,
      PortalAccessContext context,
      AuthenticatedSessionId sessionId,
      String token) {
    String requestId = exchange.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    // Keep technical conversion on consume only, never on controller/update work.
    return Mono.defer(
            () ->
                grants.consume(
                    sessionId,
                    new AuthenticatedAccountRef(context.account().accountRef()),
                    PasswordValidationPurpose.PERSONAL_INFORMATION_UPDATE,
                    token,
                    requestId))
        .defaultIfEmpty(UNAVAILABLE)
        .onErrorReturn(UNAVAILABLE)
        .flatMap(consumed -> dispatchConsumedGrant(exchange, chain, consumed));
  }

  private Mono<Void> dispatchConsumedGrant(
      ServerWebExchange exchange,
      WebFilterChain chain,
      PasswordValidationGrantService.ConsumeResult consumed) {
    if (consumed.status() == PasswordValidationGrantService.Status.REJECTED) {
      return rejected(exchange, consumed.reason() == null ? "TOKEN_MALFORMED" : consumed.reason());
    }
    if (consumed.status() != PasswordValidationGrantService.Status.CONSUMED
        || consumed.binding() == null) {
      return failure(
          exchange,
          HttpStatus.SERVICE_UNAVAILABLE,
          ApiStatus.SYSTEM_ERROR,
          "SYSTEM",
          ErrorCodes.PASSWORD_VALIDATION_UNAVAILABLE,
          List.of("PAGE"),
          consumed.reason() == null ? "SYSTEM_FAILURE" : consumed.reason());
    }
    exchange.getAttributes().put(BINDING_ATTRIBUTE, consumed.binding());
    return bindBody(exchange, chain, consumed.binding())
        .doFinally(ignored -> exchange.getAttributes().remove(BINDING_ATTRIBUTE));
  }

  private Mono<Void> bindBody(
      ServerWebExchange exchange, WebFilterChain chain, PayloadDigest expected) {
    return Mono.defer(
            () -> ServerRequest.create(exchange, codecs.getReaders()).bodyToMono(byte[].class))
        .defaultIfEmpty(new byte[0])
        .map(
            bytes -> {
              var contentType = exchange.getRequest().getHeaders().getContentType();
              var charset =
                  contentType == null || contentType.getCharset() == null
                      ? StandardCharsets.UTF_8
                      : contentType.getCharset();
              // M1 parses the raw JSON strictly, before the lossy Jackson DTO decoder.
              return new BoundBody(bytes, bindings.digest(new String(bytes, charset)));
            })
        .onErrorResume(
            ignored ->
                failure(
                        exchange,
                        HttpStatus.BAD_REQUEST,
                        ApiStatus.VALIDATION_FAILED,
                        "FIELD",
                        ErrorCodes.REQUEST_INVALID,
                        List.of("PAGE"),
                        "BODY_INVALID")
                    .then(Mono.empty()))
        .flatMap(
            body -> {
              if (!expected.matches(body.binding)) return rejected(exchange, "PAYLOAD_MISMATCH");
              var replay =
                  new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                      return Flux.defer(
                          () -> Flux.just(exchange.getResponse().bufferFactory().wrap(body.bytes)));
                    }
                  };
              return chain.filter(exchange.mutate().request(replay).build());
            });
  }

  private Mono<Void> rejected(ServerWebExchange exchange, String reason) {
    return failure(
        exchange,
        HttpStatus.FORBIDDEN,
        ApiStatus.BUSINESS_REJECTED,
        "BUSINESS",
        ErrorCodes.PASSWORD_VALIDATION_AUTHORIZATION_INVALID,
        List.of(TOKEN_HEADER),
        reason);
  }

  private Mono<Void> failure(
      ServerWebExchange exchange,
      HttpStatus http,
      ApiStatus status,
      String type,
      String code,
      List<String> targets,
      String reason) {
    String requestId = exchange.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    publishFailure(requestId, reason, http.value(), code);
    var response =
        renderer.render(
            http,
            status,
            List.of(new ApiError(type, code, null, targets, "ERROR", "SERVER")),
            requestId);
    exchange.getResponse().setStatusCode(response.getStatusCode());
    exchange.getResponse().getHeaders().putAll(response.getHeaders());
    try {
      return exchange
          .getResponse()
          .writeWith(
              Mono.just(
                  exchange
                      .getResponse()
                      .bufferFactory()
                      .wrap(json.writeValueAsBytes(response.getBody()))));
    } catch (JsonProcessingException ignored) {
      return exchange.getResponse().setComplete();
    }
  }

  private void publishFailure(String requestId, String reason, int httpStatus, String publicCode) {
    try {
      failures.publish(
          new FailureEvent(
              "password-validation.authorization.rejected",
              requestId,
              "password-validation",
              "token-gate",
              httpStatus >= 500 ? FailureOrigin.LOCAL_SYSTEM : FailureOrigin.LOCAL_BUSINESS,
              reason,
              publicCode,
              httpStatus,
              false,
              Map.of(
                  "stage",
                  "token-gate",
                  "reason",
                  reason,
                  "route",
                  PasswordValidationRouteMatcher.PERSONAL_INFORMATION_PATH,
                  "method",
                  HttpMethod.PUT.name())));
    } catch (RuntimeException ignored) {
      // Telemetry must not alter the token-gate response.
    }
  }

  private static final class BoundBody {
    private final byte[] bytes;
    private final PayloadDigest binding;

    private BoundBody(byte[] bytes, PayloadDigest binding) {
      this.bytes = bytes;
      this.binding = binding;
    }

    @Override
    public String toString() {
      return "BoundBody[<redacted>]";
    }
  }
}
