package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.Objects;
import org.springframework.web.server.ServerWebExchange;

/**
 * The per-request facts an {@link ExceptionMapping}, {@link ErrorResponseRenderer} and {@link
 * ErrorEventLogger} need to describe and log a failure: the HTTP method and path of the failed
 * request, and the correlation request id (absent when the inbound request did not carry one, so
 * the response omits the {@code X-Request-Id} header in that case).
 *
 * <p>{@link #from(ServerWebExchange)} is the only supported way to build an instance from a live
 * request, so every caller reads the request id from the same attribute key.
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
