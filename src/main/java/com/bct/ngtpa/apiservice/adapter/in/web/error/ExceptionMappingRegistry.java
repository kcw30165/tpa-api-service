package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds every {@link ExceptionMapping} and resolves the {@link ErrorDescriptor} (or the diagnostic
 * log message) for a thrown exception by nearest-superclass resolution: the exception's own class
 * is checked first (an exact match), then each superclass in turn, until a registered mapping is
 * found or the walk reaches the top of the hierarchy, at which point the mandatory catch-all
 * mapping is used.
 *
 * <p>Registering two mappings for the same {@link ExceptionMapping#exceptionType()} fails fast at
 * construction time (startup), since the registry is built once as a singleton bean.
 *
 * <p>{@link #diagnosticMessage(Throwable)} shares the same nearest-superclass walk as {@link
 * #resolve(Throwable, ErrorContext)} through a common private {@code resolveMapping} method, so
 * both the response descriptor and the log's diagnostic text come from the same resolved mapping.
 */
public final class ExceptionMappingRegistry {

  private final Map<Class<? extends Throwable>, ExceptionMapping<? extends Throwable>>
      mappingsByType;
  private final ExceptionMapping<Throwable> catchAll;

  public ExceptionMappingRegistry(
      List<? extends ExceptionMapping<? extends Throwable>> mappings,
      ExceptionMapping<Throwable> catchAll) {
    Objects.requireNonNull(mappings, "mappings");
    this.catchAll = Objects.requireNonNull(catchAll, "catchAll");

    Map<Class<? extends Throwable>, ExceptionMapping<? extends Throwable>> byType =
        new LinkedHashMap<>();
    for (ExceptionMapping<? extends Throwable> mapping : mappings) {
      Objects.requireNonNull(mapping, "mapping");
      Class<? extends Throwable> type = mapping.exceptionType();
      Objects.requireNonNull(type, "mapping.exceptionType()");
      ExceptionMapping<? extends Throwable> existing = byType.putIfAbsent(type, mapping);
      if (existing != null) {
        throw new IllegalStateException(
            "Duplicate ExceptionMapping registered for " + type.getName());
      }
    }
    this.mappingsByType = Map.copyOf(byType);
  }

  public <T extends Throwable> ErrorDescriptor resolve(T exception, ErrorContext context) {
    Objects.requireNonNull(exception, "exception");
    Objects.requireNonNull(context, "context");
    return resolveMapping(exception).describe(exception, context);
  }

  public <T extends Throwable> String diagnosticMessage(T exception) {
    Objects.requireNonNull(exception, "exception");
    return resolveMapping(exception).diagnosticMessage(exception);
  }

  @SuppressWarnings("unchecked")
  private <T extends Throwable> ExceptionMapping<T> resolveMapping(T exception) {
    Class<?> candidate = exception.getClass();
    while (Throwable.class.isAssignableFrom(candidate)) {
      ExceptionMapping<? extends Throwable> mapping = mappingsByType.get(candidate);
      if (mapping != null) {
        return (ExceptionMapping<T>) mapping;
      }
      candidate = candidate.getSuperclass();
    }
    return (ExceptionMapping<T>) catchAll;
  }

  public int mappingCount() {
    return mappingsByType.size();
  }
}
