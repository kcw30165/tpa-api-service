package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies the value semantics of the private {@code InvocationContext} record without widening its
 * visibility and without comparing classes by name.
 */
class ExecutionLoggingAspectInvocationContextTest {

  private static Class<?> invocationContextClass() {
    List<Class<?>> records =
        Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
            .filter(Class::isRecord)
            .toList();
    assertEquals(1, records.size(), "expected exactly one nested record");
    return records.get(0);
  }

  private static MethodHandle canonicalConstructor() throws ReflectiveOperationException {
    MethodHandles.Lookup lookup =
        MethodHandles.privateLookupIn(ExecutionLoggingAspect.class, MethodHandles.lookup());
    MethodType type =
        MethodType.methodType(
            void.class,
            String.class,
            String.class,
            String.class,
            boolean.class,
            boolean.class,
            boolean.class,
            Object[].class);
    return lookup.findConstructor(invocationContextClass(), type);
  }

  private static Object newContext(String methodName, Object[] args, boolean warningOnlyOnFailure)
      throws Throwable {
    return canonicalConstructor()
        .invokeWithArguments(
            "C", methodName, "C." + methodName, true, false, warningOnlyOnFailure, args);
  }

  @Test
  void equalsAndHashCode_contentAware_simpleArgs() throws Throwable {
    Object first = newContext("m", new Object[] {"x", 123}, false);
    Object second = newContext("m", new Object[] {"x", 123}, false);

    assertEquals(first, second);
    assertEquals(second, first);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void equalsAndHashCode_contentAware_nestedPrimitiveArray() throws Throwable {
    Object first = newContext("m", new Object[] {new int[] {1, 2}}, false);
    Object second = newContext("m", new Object[] {new int[] {1, 2}}, false);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }

  @Test
  void equalsFalse_whenScalarDiffers() throws Throwable {
    Object first = newContext("m", new Object[] {"x"}, false);
    Object second = newContext("m2", new Object[] {"x"}, false);

    assertNotEquals(first, second);
  }

  @Test
  void equalsFalse_whenNestedArrayDiffers() throws Throwable {
    Object first = newContext("m", new Object[] {new int[] {1, 2}}, false);
    Object second = newContext("m", new Object[] {new int[] {2, 3}}, false);

    assertNotEquals(first, second);
  }

  @Test
  void equalsFalse_withNullOrOtherType() throws Throwable {
    Object context = newContext("m", new Object[] {"x"}, false);

    assertNotEquals(context, null);
    assertNotEquals(context, "some string");
  }

  @Test
  void equalsFalse_whenWarningOnlyOnFailureDiffers() throws Throwable {
    Object legacy = newContext("m", new Object[] {"x"}, false);
    Object warningOnly = newContext("m", new Object[] {"x"}, true);

    assertNotEquals(legacy, warningOnly);
  }
}
