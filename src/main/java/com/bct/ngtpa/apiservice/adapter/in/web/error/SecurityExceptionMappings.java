package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Mappings for {@link AuthenticationException} and {@link AccessDeniedException}. Both are built
 * via {@link ErrorDescriptor#single} (one {@link MessagePolicy#RESOLVE} entry each).
 */
public final class SecurityExceptionMappings {

  private SecurityExceptionMappings() {}

  public static ExceptionMapping<AuthenticationException> authenticationRequired() {
    return new ExceptionMapping<>() {
      @Override
      public Class<AuthenticationException> exceptionType() {
        return AuthenticationException.class;
      }

      @Override
      public ErrorDescriptor describe(AuthenticationException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.UNAUTHORIZED,
            ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            ErrorCodes.SECURITY_AUTHENTICATION_REQUIRED,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }

  public static ExceptionMapping<AccessDeniedException> accessDenied() {
    return new ExceptionMapping<>() {
      @Override
      public Class<AccessDeniedException> exceptionType() {
        return AccessDeniedException.class;
      }

      @Override
      public ErrorDescriptor describe(AccessDeniedException exception, ErrorContext context) {
        return ErrorDescriptor.single(
            HttpStatus.FORBIDDEN,
            ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            ErrorCodes.SECURITY_ACCESS_DENIED,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }
}
