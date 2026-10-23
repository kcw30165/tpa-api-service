package com.bct.ngtpa.apiservice.adapter.in.web.advice;

import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorContext;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorDescriptor;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEntry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorLogDirective;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorResponseRenderer;
import com.bct.ngtpa.apiservice.adapter.in.web.error.ExceptionMappingRegistry;
import com.bct.ngtpa.apiservice.adapter.in.web.error.LogLevel;
import com.bct.ngtpa.apiservice.adapter.in.web.error.PublicMessageResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * Sub-task 1.8.3 (M1 closure, Option A, A1 approved 2026-10-22): collapses to a single
 * {@code @ExceptionHandler(Exception.class) handle(Exception, ServerWebExchange)} method. All
 * per-type routing is done by {@link ExceptionMappingRegistry} (nearest-superclass resolution); no
 * other {@code @ExceptionHandler} method remains on this class.
 *
 * <p>Public message resolution now lives entirely in {@link PublicMessageResolver} (extracted
 * verbatim in sub-task 1.8.1 -- the pre-login path check, locale lookup, portal-context
 * account/trust/scheme fields, and the reflective {@code lang} lookup on a bound request target are
 * all there now; none of that logic is duplicated here). Event logging uses the {@link
 * ErrorEventLogger} bean (sub-task 1.8.2, instance method). Response rendering uses the {@link
 * ErrorResponseRenderer} bean (converted to a bean-only class in this sub-task, since its pure
 * 4-argument signature could not coexist with a static method of the same signature -- unlike
 * {@code ErrorEventLogger}, whose instance method's arity naturally differs by one parameter and so
 * could keep both forms).
 *
 * <p>Every test that previously called a named {@code handleXxx(...)} method now calls {@code
 * handle(...)} instead -- only the call site changed, never the assertion (per A1). The
 * empty-error-list {@code SYSTEM_UNEXPECTED} fallback in {@link #fallbackErrors} is kept exactly as
 * it behaved before this sub-task (same resolved message, same code), even though it is unreachable
 * in practice since every {@link ExceptionMappingRegistry} resolution -- including the mandatory
 * catch-all -- always produces at least one {@link ErrorEntry}.
 */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
  private static final String SOURCE_SERVER = "SERVER";

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
    List<ErrorEntry> entries = descriptor.errors();

    List<ApiError> errors = new ArrayList<>(entries.size());
    for (int i = 0; i < entries.size(); i++) {
      ErrorEntry entry = entries.get(i);
      errors.add(
          new ApiError(
              entry.type(),
              entry.code(),
              messages.get(i),
              entry.targets(),
              "ERROR",
              SOURCE_SERVER));
    }

    List<ApiError> safeErrors =
        errors.isEmpty() ? fallbackErrors(descriptor, exception, exchange) : List.copyOf(errors);

    eventLogger.log(
        log,
        new ErrorLogDirective(
            descriptor.httpStatus(),
            safeErrors.getFirst().code(),
            descriptor.logLevel() == LogLevel.ERROR,
            descriptor.includeStackTrace(),
            descriptor.extraLogFields()),
        exchange,
        exception,
        registry.diagnosticMessage(exception));

    String requestId = getRequestId(exchange);
    return renderer.render(descriptor.httpStatus(), descriptor.apiStatus(), safeErrors, requestId);
  }

  private List<ApiError> fallbackErrors(
      ErrorDescriptor descriptor, Exception exception, ServerWebExchange exchange) {
    ErrorDescriptor fallbackDescriptor =
        ErrorDescriptor.single(
            descriptor.httpStatus(),
            descriptor.apiStatus(),
            "SYSTEM",
            ErrorCodes.SYSTEM_UNEXPECTED,
            List.of(),
            descriptor.logEventName(),
            descriptor.logLevel(),
            descriptor.includeStackTrace());
    String fallbackMessage =
        messageResolver.resolve(fallbackDescriptor, exception, exchange).getFirst();
    return List.of(ApiError.system(ErrorCodes.SYSTEM_UNEXPECTED, fallbackMessage, SOURCE_SERVER));
  }

  private String getRequestId(ServerWebExchange exchange) {
    return (String) exchange.getAttributes().get(RequestCorrelation.REQUEST_ID_ATTRIBUTE_KEY);
  }
}
