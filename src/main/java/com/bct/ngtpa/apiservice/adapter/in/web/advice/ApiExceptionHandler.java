package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorContext;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorDescriptor;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorLogDirective;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.LogLevel;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * The single {@code @ExceptionHandler(Exception.class)} entry point for every exception thrown from
 * a controller. All per-type routing is done by {@link ExceptionMappingRegistry}
 * (nearest-superclass resolution); no other {@code @ExceptionHandler} method is on this class.
 *
 * <p>Public message resolution (pre-login path check, locale lookup, portal-context
 * account/trust/scheme fields, and the reflective {@code lang} lookup on a bound request target)
 * lives in {@link PublicMessageResolver}. Event logging uses the {@link ErrorEventLogger} bean.
 * Response rendering (including assembling each {@code ApiError}) uses the {@link
 * ErrorResponseRenderer} bean.
 *
 * <p>The empty-error-list {@code SYSTEM_UNEXPECTED} fallback in {@link
 * #fallbackDescriptor(ErrorDescriptor)} cannot be proven unreachable: most {@link
 * ExceptionMappingRegistry} mappings structurally produce at least one entry, but two (the
 * AccountBalance and AccountGainLoss mappings) build their entries from external response-policy
 * classes whose error list is not guaranteed non-empty, so this guard is kept rather than removed.
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

  private final ExceptionMappingRegistry registry;
  private final PublicMessageResolver messageResolver;
  private final ErrorEventLogger eventLogger;
  private final ErrorResponseRenderer renderer;

  @Autowired
  public ApiExceptionHandler(
      ExceptionMappingRegistry registry,
      PublicMessageResolver messageResolver,
      ErrorEventLogger eventLogger,
      ErrorResponseRenderer renderer) {
    this.registry = registry;
    this.messageResolver = messageResolver;
    this.eventLogger = eventLogger;
    this.renderer = renderer;
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<MutationResponse<Void>> handle(
      Exception exception, ServerWebExchange exchange) {
    ErrorContext context = ErrorContext.from(exchange);
    ErrorDescriptor descriptor = registry.resolve(exception, context);
    List<String> messages = messageResolver.resolve(descriptor, exception, exchange);

    if (descriptor.errors().isEmpty()) {
      descriptor = fallbackDescriptor(descriptor);
      messages = messageResolver.resolve(descriptor, exception, exchange);
    }

    eventLogger.log(
        log,
        new ErrorLogDirective(
            descriptor.httpStatus(),
            descriptor.errors().getFirst().code(),
            descriptor.logLevel() == LogLevel.ERROR,
            descriptor.includeStackTrace(),
            descriptor.extraLogFields()),
        exchange,
        exception,
        registry.diagnosticMessage(exception));

    return renderer.render(descriptor, messages, context);
  }

  private ErrorDescriptor fallbackDescriptor(ErrorDescriptor descriptor) {
    return ErrorDescriptor.single(
        descriptor.httpStatus(),
        descriptor.apiStatus(),
        "SYSTEM",
        ErrorCodes.SYSTEM_UNEXPECTED,
        List.of(),
        descriptor.logEventName(),
        descriptor.logLevel(),
        descriptor.includeStackTrace());
  }
}
