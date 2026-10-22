package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * SonarQube fix (post-M1 quality gate, java:S5778): every {@code assertThrows} lambda that
 * previously contained two or more method invocations that could each throw (for example a {@code
 * catchAll()} or {@code List.of(...)} argument expression evaluated alongside the
 * constructor/method call under test) now has every "safe" invocation extracted into a local
 * variable before the {@code assertThrows} call, leaving only the single invocation actually
 * expected to throw inside the lambda. No assertions or test intent changed.
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
        return new ErrorDescriptor(
            HttpStatus.BAD_REQUEST,
            ApiStatus.BUSINESS_REJECTED,
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

    assertEquals("child", descriptor.errorCode());
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

    assertEquals("parent", descriptor.errorCode());
  }

  @Test
  void resolvesToFurtherAncestorWhenOnlyItIsRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(mappingFor(GrandparentException.class, "grandparent")), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new ChildException(), CONTEXT);

    assertEquals("grandparent", descriptor.errorCode());
  }

  // ───────────────────────── catch-all fallback ─────────────────────────

  @Test
  void fallsBackToCatchAllWhenNoAncestorIsRegistered() {
    var registry =
        new ExceptionMappingRegistry(
            List.of(mappingFor(ParentException.class, "parent")), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new UnrelatedException(), CONTEXT);

    assertEquals("SYSTEM_UNEXPECTED", descriptor.errorCode());
  }

  @Test
  void fallsBackToCatchAllWhenNoMappingsAreRegisteredAtAll() {
    var registry = new ExceptionMappingRegistry(List.of(), catchAll());

    ErrorDescriptor descriptor = registry.resolve(new IllegalStateException("boom"), CONTEXT);

    assertEquals("SYSTEM_UNEXPECTED", descriptor.errorCode());
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
