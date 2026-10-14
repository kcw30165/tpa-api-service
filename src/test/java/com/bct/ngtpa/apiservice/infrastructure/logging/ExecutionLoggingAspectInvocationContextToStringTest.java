package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

/**
 * Covers java:S6218 — InvocationContext (a private record with an {@code Object[] args} field) must
 * not rely on the auto-generated {@code toString()}, which prints the array's identity hash instead
 * of its content.
 */
class ExecutionLoggingAspectInvocationContextToStringTest {

  @Test
  void toStringRendersArrayContentsInsteadOfDefaultIdentity() throws Exception {
    // Avoid S1872 (classes should not be compared by name): InvocationContext is a
    // private member class that cannot be referenced via a class literal from this
    // test, so its Class<?> is constructed directly from the binary name instead of
    // scanning getDeclaredClasses() with a getSimpleName().equals(...) check.
    Class<?> invocationContextClass =
        Class.forName(ExecutionLoggingAspect.class.getName() + "$InvocationContext");

    Constructor<?> constructor =
        invocationContextClass.getDeclaredConstructor(
            String.class, String.class, String.class, boolean.class, boolean.class, Object[].class);
    constructor.setAccessible(true);

    Object context =
        constructor.newInstance(
            "SomeClass", "someMethod", "label", true, true, new Object[] {"alpha", 42});

    String rendered = context.toString();

    assertThat(rendered).doesNotContain("[Ljava.lang.Object;@");
    assertThat(rendered).contains("alpha");
    assertThat(rendered).contains("42");
  }
}
