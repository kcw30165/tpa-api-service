package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sub-task 1.7 (M1 closure, Option A) update: the {@code mappingFor} test helper now builds its
 * {@link ErrorDescriptor} via {@link ErrorDescriptor#single}, and assertions read {@code
 * descriptor.errors().getFirst().code()} instead of the removed {@code descriptor.errorCode()}. No
 * change to resolution-order, catch-all-fallback or duplicate-mapping behaviour.
 */
class ExceptionMappingRegistryTest {

  private static final ErrorContext CONTEXT =
      new ErrorContext("GET", "/api/v1/fund-price", "req-1");

  // ───────────────────────── test exception hierarchy ─────────────────────────

  private static class GrandparentException extends RuntimeException {}

  private static class ParentException extends GrandparentException {}

  private static class ChildException extends ParentException {}

  private static class UnrelatedException extends RuntimeException {}

  // ───────────────────────── helpers ─────────────────────────

  private static <T extends Throwable> ExceptionMapping<T> mappingFor(
      Class<T> type, String errorCode) {
    return new ExceptionMapping<>() {
      @Override
      public Class<T> exceptionType() {
        return type;
      }

      @Override
      public ErrorDescriptor describe(T exception, ErrorContext context) {
        return ErrorDescriptor.single(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus.BUSINESS_REJECTED,
            "BUSINESS",
            errorCode,
            List.of(),
            "http.api.error",
            LogLevel.WARN,
            false);
      }
    };
  }

  private static ExceptionMapping<Throwable> catchAll() {
    return mappingFor(Throwable.class, "SYSTEM_UNEXPECTED");
  }

  private static String code(ErrorDescriptor descriptor) {
    return descriptor.errors().getFirst().code();
  }

  // ───────────────────────── nearest-superclass resolution ─────────────────────────

  @Test
  void resolvesExactMatchWhenTheThrownTypeItselfIsRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(
                mappingFor(GrandparentException.class, "grandparent"),
                mappingFor(ParentException.class, "parent"),
                mappingFor(ChildException.class, "child")),
            catchAll());

    ErrorDescriptor descriptor = registry.resolve(new ChildException(), CONTEXT);

    assertEquals("child", code(descriptor));
  }

  @Test
  void resolvesToNearestRegisteredAncestorWhenExactTypeIsNotRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(
                mappingFor(GrandparentException.class, "grandparent"),
                mappingFor(ParentException.class, "parent")),
            catchAll());

    // ChildException itself is not registered; ParentException is nearer than GrandparentException.
    ErrorDescriptor descriptor = registry.resolve(new ChildException(), CONTEXT);

    assertEquals("parent", code(descriptor));
  }

  @Test
  void resolvesToFurtherAncestorWhenOnlyItIsRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(mappingFor(GrandparentException.class, "grandparent")), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new ChildException(), CONTEXT);

    assertEquals("grandparent", code(descriptor));
  }

  // ───────────────────────── catch-all fallback ─────────────────────────

  @Test
  void fallsBackToCatchAllWhenNoAncestorIsRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(mappingFor(ParentException.class, "parent")), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new UnrelatedException(), CONTEXT);

    assertEquals("SYSTEM_UNEXPECTED", code(descriptor));
  }

  @Test
  void fallsBackToCatchAllWhenNoMappingsAreRegisteredAtAll() {
    var registry = new ExceptionMappingRegistry(List.of(), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new IllegalStateException("boom"), CONTEXT);

    assertEquals("SYSTEM_UNEXPECTED", code(descriptor));
    assertEquals(0, registry.mappingCount());
  }

  // ───────────────────────── duplicate mapping fails at startup ─────────────────────────

  @Test
  void duplicateMappingForTheSameExceptionTypeFailsAtConstruction() {
    var first = mappingFor(ParentException.class, "parent-first");
    var second = mappingFor(ParentException.class, "parent-second");
    var mappings = List.of(first, second);
    var catchAllMapping = catchAll();

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> new ExceptionMappingRegistry(mappings, catchAllMapping));

    assertTrue(ex.getMessage().contains(ParentException.class.getName()));
  }

  // ───────────────────────── mappingCount and null-argument guards ─────────────────────────

  @Test
  void mappingCountReflectsTheNumberOfDistinctRegisteredTypes() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(
                mappingFor(GrandparentException.class, "grandparent"),
                mappingFor(ParentException.class, "parent"),
                mappingFor(ChildException.class, "child")),
            catchAll());

    assertEquals(3, registry.mappingCount());
  }

  @Test
  void constructorRejectsNullMappingsList() {
    var catchAllMapping = catchAll();
    assertThrows(
        NullPointerException.class, () -> new ExceptionMappingRegistry(null, catchAllMapping));
  }

  @Test
  void constructorRejectsNullCatchAll() {
    var mappings = List.of(mappingFor(ParentException.class, "parent"));
    assertThrows(NullPointerException.class, () -> new ExceptionMappingRegistry(mappings, null));
  }

  @Test
  void resolveRejectsNullException() {
    var registry = new ExceptionMappingRegistry(List.of(), catchAll());
    assertThrows(NullPointerException.class, () -> registry.resolve(null, CONTEXT));
  }

  @Test
  void resolveRejectsNullContext() {
    var registry = new ExceptionMappingRegistry(List.of(), catchAll());
    var exception = new IllegalStateException();
    assertThrows(NullPointerException.class, () -> registry.resolve(exception, null));
  }
}
