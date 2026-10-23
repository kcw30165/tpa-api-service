package com.bct.ngtpa.apiservice.adapter.in.web.error;

/**
 * Describes how one exception type (or one of its subclasses, via {@link ExceptionMappingRegistry}
 * nearest-superclass resolution) is rendered and logged. A mapping is registered once per {@link
 * #exceptionType()}; {@link ExceptionMappingRegistry} rejects a second mapping for the same type at
 * construction time (startup).
 *
 * <p>{@link #describe(Throwable, ErrorContext)} receives the concrete exception instance so a
 * mapping can read instance state (for example {@code ApimException.getStatusCode()}).
 *
 * <p>{@link #diagnosticMessage(Throwable)} is a default method so a mapping can supply its own
 * non-exception diagnostic text (for example {@code RegistrationLookupDownstreamException}'s
 * reason-qualified message), while every mapping that does not override it keeps the universal
 * default of {@code exception.getMessage()}.
 *
 * @param <T> the exception type this mapping describes
 */
public interface ExceptionMapping<T extends Throwable> {

  Class<T> exceptionType();

  ErrorDescriptor describe(T exception, ErrorContext context);

  default String diagnosticMessage(T exception) {
    return exception.getMessage();
  }
}
