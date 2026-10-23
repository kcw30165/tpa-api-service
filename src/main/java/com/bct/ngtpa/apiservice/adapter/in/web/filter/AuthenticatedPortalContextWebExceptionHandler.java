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
 * Routes through the shared {@link ExceptionMappingRegistry} (via {@link
 * AuthenticatedPortalContextExceptionMappings} in the {@code error} package, registered by {@code
 * ExceptionMappingRegistryConfig}) to resolve status, {@code ApiStatus}, error type/code, and
 * targets, and reuses {@link ErrorResponseRenderer} to build the response body.
 *
 * <p>This handler deliberately keeps emitting its own log statement rather than delegating to
 * {@code ErrorEventLogger}: that logger always emits a fixed JSON {@code http.api.error} event,
 * which would change this filter-path's pinned plain-text log format. The resolved {@link
 * ErrorDescriptor#logEventName()} is used as the event name within that plain-text format.
 *
 * <p>The renderer is always called with a {@code null} request id: unlike {@code
 * ApiExceptionHandler}, this handler never sets the {@code X-Request-Id} response header, even when
 * the inbound request supplied one (06-open-items.md, O-6-adjacent).
 *
 * <p>{@link ErrorDescriptor} carries a {@code List<ErrorEntry>}; this handler always resolves
 * exactly one entry (the {@link AuthenticatedPortalContextExceptionMappings} mapping is built via
 * {@code ErrorDescriptor.singleWithoutMessage(...)}), so {@code descriptor.errors().getFirst()}
 * supplies the type/code/targets this handler needs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public final class AuthenticatedPortalContextWebExceptionHandler implements WebExceptionHandler {
  private static final Logger LOG =
      LoggerFactory.getLogger(AuthenticatedPortalContextWebExceptionHandler.class);

  private final ObjectMapper objectMapper;
  private final ExceptionMappingRegistry registry;
  private final ErrorResponseRenderer renderer;

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
        renderer.render(descriptor.httpStatus(), descriptor.apiStatus(), List.of(error), null);

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
