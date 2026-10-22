package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorContext;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorDescriptor;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEntry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/**
 * Sub-task 1.5: routes through the shared {@link ExceptionMappingRegistry} (via {@link
 * AuthenticatedPortalContextExceptionMappings} in the {@code error} package, registered by {@code
 * ExceptionMappingRegistryConfig}) to resolve status, {@code ApiStatus}, error type/code, and
 * targets, and reuses {@link ErrorResponseRenderer} to build the response body -- instead of
 * hardcoding those values inline as before sub-task 1.5.
 *
 * <p>This handler deliberately keeps emitting its own log statement rather than delegating to
 * {@code ErrorEventLogger}: that logger always emits a fixed JSON {@code http.api.error} event,
 * which would change this filter-path's pinned plain-text log format (asserted literally by the
 * sub-task 1.1 characterization tests). The resolved {@link ErrorDescriptor#logEventName()} is used
 * as the event name within the unchanged plain-text format instead.
 *
 * <p>The renderer is always called with a {@code null} request id, preserving the confirmed
 * pre-existing gap (06-open-items.md, O-6-adjacent): unlike {@code ApiExceptionHandler}, this
 * handler never sets the {@code X-Request-Id} response header, even when the inbound request
 * supplied one. Changing that remains Nick's decision and is out of scope for this sub-task.
 *
 * <p>Sub-task 1.7 (M1 closure, Option A) compile-break fix: {@link ErrorDescriptor} no longer
 * exposes {@code errorCode()}/{@code errorType()}/{@code targets()} directly -- it carries a {@code
 * List<ErrorEntry>}. This handler always resolves exactly one {@link ErrorEntry} (the {@link
 * AuthenticatedPortalContextExceptionMappings} mapping is built via {@code
 * ErrorDescriptor.single(...)}), so {@code descriptor.errors().getFirst()} reproduces the exact
 * same type/code/targets values this handler read before sub-task 1.7. No behaviour change: same
 * log line format, same response body, same absence of the {@code X-Request-Id} header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public final class AuthenticatedPortalContextWebExceptionHandler implements WebExceptionHandler {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedPortalContextWebExceptionHandler.class);

  private final ObjectMapper objectMapper;
  private final ExceptionMappingRegistry registry;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
    if (!(failure instanceof AuthenticatedPortalContextResolutionException resolutionException)) {
      return Mono.error(failure);
    }

    ErrorContext context = ErrorContext.from(exchange);
    ErrorDescriptor descriptor = registry.resolve(resolutionException, context);
    ErrorEntry entry = descriptor.errors().getFirst();

    LOG.warn(
        "event={} status={} code={} method={} path={} requestId={}",
        descriptor.logEventName(),
        descriptor.httpStatus().value(),
        entry.code(),
        exchange.getRequest().getMethod(),
        exchange.getRequest().getPath().value(),
        context.requestId());

    ApiError error =
        new ApiError(entry.type(), entry.code(), null, entry.targets(), "ERROR", "SERVER");
    ResponseEntity<MutationResponse<Void>> response =
        ErrorResponseRenderer.render(
            descriptor.httpStatus(), descriptor.apiStatus(), List.of(error), null);

    exchange.getResponse().setStatusCode(response.getStatusCode());
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      DataBuffer buffer =
          exchange
              .getResponse()
              .bufferFactory()
              .wrap(objectMapper.writeValueAsBytes(response.getBody()));
      return exchange.getResponse().writeWith(Mono.just(buffer));
    } catch (Exception ignored) {
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      return exchange.getResponse().setComplete();
    }
  }
}
