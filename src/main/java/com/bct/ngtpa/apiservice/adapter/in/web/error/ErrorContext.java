package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Objects;
import org.springframework.web.server.ServerWebExchange;

/**
 * The per-request facts an {@link ExceptionMapping} or, later, the error response renderer and
 * error event logger (sub-task 1.3) need to describe and log a failure: the HTTP method and path of
 * the failed request, and the correlation request id (absent when the inbound request did not carry
 * one, mirroring the current {@code ApiExceptionHandler} behaviour of omitting the {@code
 * X-Request-Id} response header in that case).
 *
 * <p>Not wired into any handler yet (sub-task 1.2). {@link #from(ServerWebExchange)} is the only
 * supported way to build an instance from a live request so every caller reads the request id from
 * the same attribute key as {@code ApiExceptionHandler} today.
 *
 * <p>SonarQube fix (post-M1 quality gate, java:S2583): on the project's current spring-web version
 * (7.0.7), {@code ServerHttpRequest.getMethod()} always returns a non-null {@code HttpMethod}, so
 * the previous {@code request.getMethod() != null ? ... : ""} guard always evaluated true and was
 * dead code. Removed; behaviour is unchanged since the guard never took its false branch.
 */
public record ErrorContext(String httpMethod, String path, String requestId) {

  public ErrorContext {
    Objects.requireNonNull(httpMethod, "httpMethod");
    Objects.requireNonNull(path, "path");
  }

  public static ErrorContext from(ServerWebExchange exchange) {
    Objects.requireNonNull(exchange, "exchange");
    var request = exchange.getRequest();
    String method = request.getMethod().name();
    String path = request.getPath().value();
    String requestId = exchange.getAttribute(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
    return new ErrorContext(method, path, requestId);
  }

  public boolean hasRequestId() {
    return requestId != null && !requestId.isBlank();
  }
}
