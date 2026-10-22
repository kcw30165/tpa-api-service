package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Sub-task 1.4: verbatim move of {@code ApiExceptionHandler.handleUnexpectedException}'s mapping
 * (the catch-all fallback for {@code Exception.class} previously registered directly on {@code
 * ApiExceptionHandler}). This is the mandatory catch-all supplied to {@link
 * ExceptionMappingRegistry}; it is returned whenever no more specific mapping is registered for an
 * exception or any of its ancestors.
 */
public final class CatchAllExceptionMapping implements ExceptionMapping<Throwable> {

  public static final CatchAllExceptionMapping INSTANCE = new CatchAllExceptionMapping();

  private CatchAllExceptionMapping() {}

  @Override
  public Class<Throwable> exceptionType() {
    return Throwable.class;
  }

  @Override
  public ErrorDescriptor describe(Throwable exception, ErrorContext context) {
    return new ErrorDescriptor(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiStatus.SYSTEM_ERROR,
        "SYSTEM",
        ErrorCodes.SYSTEM_UNEXPECTED,
        List.of(),
        "http.api.error",
        LogLevel.ERROR,
        true);
  }
}
