package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ExecutionLoggingAspectInvocationContextTest {

  private java.lang.invoke.MethodHandle findCanonicalConstructorHandle(Class<?> nested) throws Exception {
    var lookup = java.lang.invoke.MethodHandles.privateLookupIn(ExecutionLoggingAspect.class, java.lang.invoke.MethodHandles.lookup());
    var mt = java.lang.invoke.MethodType.methodType(void.class, String.class, String.class, String.class, boolean.class, boolean.class, Object[].class);
    return lookup.findConstructor(nested, mt);
  }

  private Object newInvocationContext(java.lang.invoke.MethodHandle ctorHandle, Object[] args) throws Exception {
    // canonical ctor: (String className, String methodName, String label, boolean logArgs, boolean logResult, Object[] args)
    try {
      return ctorHandle.invokeWithArguments("C", "m", "C.m", true, false, args);
    } catch (Throwable t) {
      throw new Exception(t);
    }
  }

  @Test
  void equalsAndHashCode_contentAware_simpleArgs() throws Exception {
    Class<?>[] inner = ExecutionLoggingAspect.class.getDeclaredClasses();
    Class<?> ic = null;
    for (Class<?> c : inner) if (c.getSimpleName().equals("InvocationContext")) ic = c;
    if (ic == null) throw new IllegalStateException("InvocationContext class not found");

    var ctorHandle = findCanonicalConstructorHandle(ic);

    Object[] a1 = new Object[] {"x", 123};
    Object[] a2 = new Object[] {"x", 123};

    Object inv1 = newInvocationContext(ctorHandle, a1);
    Object inv2 = newInvocationContext(ctorHandle, a2);

    assertTrue(inv1.equals(inv2));
    assertTrue(inv2.equals(inv1));
    assertEquals(inv1.hashCode(), inv2.hashCode());
  }

  @Test
  void equalsAndHashCode_contentAware_nestedPrimitiveArray() throws Exception {
    Class<?> ic = Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("InvocationContext"))
        .findFirst()
        .orElseThrow();
    var ctorHandle = findCanonicalConstructorHandle(ic);

    Object[] a1 = new Object[] { new int[] {1,2} };
    Object[] a2 = new Object[] { new int[] {1,2} };

    Object inv1 = newInvocationContext(ctorHandle, a1);
    Object inv2 = newInvocationContext(ctorHandle, a2);

    assertTrue(inv1.equals(inv2));
    assertEquals(inv1.hashCode(), inv2.hashCode());
  }

  @Test
  void equalsFalse_whenScalarDiffers() throws Exception {
    Class<?> ic = Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("InvocationContext"))
        .findFirst()
        .orElseThrow();
    var ctorHandle = findCanonicalConstructorHandle(ic);

    Object inv1 = newInvocationContext(ctorHandle, new Object[] {"x"});

    // change scalar method name to differ (reuse existing ctorHandle)
    Object inv3;
    try {
      inv3 = ctorHandle.invokeWithArguments("C", "m2", "C.m2", true, false, new Object[] {"x"});
    } catch (Throwable t) {
      throw new Exception(t);
    }

    assertFalse(inv1.equals(inv3));
  }

  @Test
  void equalsFalse_whenNestedArrayDiffers() throws Exception {
    Class<?> ic = Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("InvocationContext"))
        .findFirst()
        .orElseThrow();
    var ctorHandle = findCanonicalConstructorHandle(ic);

    Object[] a1 = new Object[] { new int[] {1,2} };
    Object[] a2 = new Object[] { new int[] {2,3} };

    Object inv1 = newInvocationContext(ctorHandle, a1);
    Object inv2 = newInvocationContext(ctorHandle, a2);

    assertFalse(inv1.equals(inv2));
  }

  @Test
  void equalsFalse_withNullOrOtherType() throws Exception {
    Class<?> ic = Arrays.stream(ExecutionLoggingAspect.class.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("InvocationContext"))
        .findFirst()
        .orElseThrow();
    var ctorHandle = findCanonicalConstructorHandle(ic);

    Object inv = newInvocationContext(ctorHandle, new Object[] {"x"});

    assertFalse(inv.equals(null));
    assertFalse(inv.equals("some string"));
  }
}
