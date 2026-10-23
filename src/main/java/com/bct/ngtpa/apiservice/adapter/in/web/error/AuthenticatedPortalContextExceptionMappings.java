package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedPortalContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.5: mapping for {@link AuthenticatedPortalContextResolutionException}, the single
 * exception type handled by {@code AuthenticatedPortalContextWebExceptionHandler} (the filter-path
 * {@code WebExceptionHandler}, distinct from the {@code ApiExceptionHandler} controller advice).
 *
 * <p>Reproduces that handler's pre-1.5 hardcoded values exactly: HTTP 401, {@link
 * ApiStatus#BUSINESS_REJECTED}, error type {@code AUTHENTICATION_REQUIRED}, {@link
 * ErrorCodes#SECURITY_AUTHENTICATION_REQUIRED}, and the single {@code PAGE} target.
 *
 * <p>{@code logEventName} carries the handler's existing plain-text log event name ({@code
 * authenticated.context.failure.mapped}). The handler consumes this field directly in its own log
 * statement rather than delegating to {@link ErrorEventLogger}, because that logger always emits a
 * fixed JSON {@code http.api.error} shape, which differs from this filter-path's pinned plain-text
 * format (confirmed by the sub-task 1.1 characterization tests, which assert the literal
 * space-separated {@code event=... status=... code=... method=... path=... requestId=...} text).
 *
 * <p>Sub-task 1.7 (M1 closure, Option A): built via {@link ErrorDescriptor#single} instead of the
 * record constructor directly (one {@link MessagePolicy#RESOLVE} entry); no behaviour change --
 * this handler reads {@code descriptor.errors().getFirst()} directly rather than going through
 * {@code ApiExceptionHandler.delegate(...)}'s per-entry message resolution, since it never resolves
 * a public message at all (O-10, confirmed unchanged).
 */
public final class AuthenticatedPortalContextExceptionMappings {

  private AuthenticatedPortalContextExceptionMappings() {}

  public static ExceptionMapping<AuthenticatedPortalContextResolutionException>
      authenticationRequired() {
    return new ExceptionMapping<>() {
      @Override
      public Class<AuthenticatedPortalContextResolutionException> exceptionType() {
        return AuthenticatedPortalContextResolutionException.class;
      }

      @Override
      public ErrorDescriptor describe(
          AuthenticatedPortalContextResolutionException exception, ErrorContext context) {
        return ErrorDescriptor.singleWithoutMessage(
            HttpStatus.UNAUTHORIZED,
            ApiStatus.BUSINESS_REJECTED,
            "AUTHENTICATION_REQUIRED",
            ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
            List.of("PAGE"),
            "authenticated.context.failure.mapped",
            LogLevel.WARN,
            false);
      }
    };
  }
}
