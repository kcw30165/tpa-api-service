package com.bct.ngtpa.apiservice.adapter.in.web.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Holds every {@link ExceptionMapping} and resolves the {@link ErrorDescriptor} for a thrown
 * exception by nearest-superclass resolution: the exception's own class is checked first (an exact
 * match), then each superclass in turn, until a registered mapping is found or the walk reaches the
 * top of the hierarchy, at which point the mandatory catch-all mapping is used.
 *
 * <p>Registering two mappings for the same {@link ExceptionMapping#exceptionType()} fails fast at
 * construction time (startup), since the registry is built once as a singleton bean. Not wired into
 * any handler yet (sub-task 1.2); {@code ApiExceptionHandler} keeps its own
 * {@code @ExceptionHandler} methods until sub-task 1.4.
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

  @SuppressWarnings("unchecked")
  public <T extends Throwable> ErrorDescriptor resolve(T exception, ErrorContext context) {
    Objects.requireNonNull(exception, "exception");
    Objects.requireNonNull(context, "context");

    Class<?> candidate = exception.getClass();
    while (candidate != null && Throwable.class.isAssignableFrom(candidate)) {
      ExceptionMapping<? extends Throwable> mapping = mappingsByType.get(candidate);
      if (mapping != null) {
        return ((ExceptionMapping<T>) mapping).describe(exception, context);
      }
      candidate = candidate.getSuperclass();
    }
    return catchAll.describe(exception, context);
  }

  public int mappingCount() {
    return mappingsByType.size();
  }
}
