package com.bct.ngtpa.apiservice.adapter.in.web.error;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * The mandatory catch-all fallback supplied to {@link ExceptionMappingRegistry}: returned whenever
 * no more specific mapping is registered for an exception or any of its ancestors.
 *
 * <p>The single shared {@link #INSTANCE} is intentional: {@code ExceptionMappingRegistryConfig}
 * passes the same object to every {@link ExceptionMappingRegistry} it builds, and tests compare it
 * by reference. SonarQube's generic singleton-pattern advisory (java:S6548) is suppressed here
 * because that is exactly the required contract, not an accidental singleton.
 */
@SuppressWarnings("java:S6548")
public final class CatchAllExceptionMapping implements ExceptionMapping<Throwable> {

  public static final CatchAllExceptionMapping INSTANCE = new CatchAllExceptionMapping();

  private CatchAllExceptionMapping() {}

  @Override
  public Class<Throwable> exceptionType() {
    return Throwable.class;
  }

  @Override
  public ErrorDescriptor describe(Throwable exception, ErrorContext context) {
    return ErrorDescriptor.single(
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
